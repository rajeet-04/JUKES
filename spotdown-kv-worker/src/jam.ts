import { DurableObject } from 'cloudflare:workers';
import type { Env } from './index';
import { advance, applyOp, nextAfter, parseOp, parseSeed, positionAt, radioAfter, type Playback, type RoomState } from './jam-logic';

const MAX_MEMBERS = 10;
const MAX_MESSAGE_BYTES = 128 * 1024; // a seed carries up to 200 songs
const IDLE_MS = 24 * 3600 * 1000;
const OPS_PER_WINDOW = 30; // per socket, per 10 s: a stuck client must not burn the daily write budget
const WINDOW_MS = 10_000;
const END_SLACK_MS = 5000; // a song counts as over this far before its listed length (lengths are rounded)
const OPEN = 1;

interface Member { clientId: string; name: string; joinedAt: number }
type PublicMember = Omit<Member, 'joinedAt'>;

/**
 * One Jam room. All ops run one at a time in this object, so concurrent edits get a single order.
 * Storage is one row (the whole room), rewritten once per state-changing op; presence, pings and
 * everything else live in memory or on the sockets. Everyone in the room has the same powers; the
 * "host" is only whoever has been here longest (it seeds the room and passes on when they leave).
 */
export class JamRoom extends DurableObject<Env> {
    private room: RoomState | null | undefined;
    private buckets = new Map<WebSocket, { start: number; count: number }>(); // memory only; a hibernation reset is harmless

    private async load(): Promise<RoomState | null> {
        if (this.room === undefined) this.room = (await this.ctx.storage.get<RoomState>('room')) ?? null;
        return this.room;
    }

    private async save(room: RoomState) {
        this.room = room;
        await this.ctx.storage.put('room', room); // committed before anyone hears about it
    }

    /** Connected members, longest-connected first. */
    private members(): Member[] {
        return this.ctx.getWebSockets().filter(w => w.readyState === OPEN)
            .map(w => w.deserializeAttachment() as Member | null).filter((m): m is Member => !!m)
            .sort((a, b) => a.joinedAt - b.joinedAt);
    }

    private membersMsg(room: RoomState) {
        return { t: 'members', hostId: room.hostId ?? null, members: this.members().map(({ clientId, name }): PublicMember => ({ clientId, name })) };
    }

    private broadcast(msg: object, except?: WebSocket) {
        const text = JSON.stringify(msg);
        for (const w of this.ctx.getWebSockets()) if (w !== except && w.readyState === OPEN && w.deserializeAttachment()) w.send(text);
    }

    async fetch(request: Request): Promise<Response> {
        const path = new URL(request.url).pathname;
        if (path === '/init') {
            if (await this.load()) return new Response('exists', { status: 409 });
            const now = Date.now();
            this.room = { rev: 0, queue: [], lastActive: now };
            await this.ctx.storage.put('room', this.room);
            await this.ctx.storage.setAlarm(now + IDLE_MS);
            return new Response('ok');
        }
        const room = await this.load();
        if (path === '/info') return Response.json({ exists: !!room, members: this.members().length, queue: room?.queue.length ?? 0 });
        if (!path.endsWith('/ws') || request.headers.get('Upgrade') !== 'websocket') return new Response('Not Found', { status: 404 });
        if (!room) return new Response('No such jam', { status: 404 });
        // also cap raw sockets: connections that never say hello must not pile up
        if (this.members().length >= MAX_MEMBERS || this.ctx.getWebSockets().length >= MAX_MEMBERS * 2) return new Response('Jam is full', { status: 409 });
        const [client, server] = Object.values(new WebSocketPair());
        this.ctx.acceptWebSocket(server);
        return new Response(null, { status: 101, webSocket: client });
    }

    async webSocketMessage(ws: WebSocket, message: string | ArrayBuffer): Promise<void> {
        if (typeof message !== 'string' || message.length > MAX_MESSAGE_BYTES) return ws.close(1009, 'bad message');
        let msg: any;
        try { msg = JSON.parse(message); } catch { return ws.close(1003, 'bad json'); }
        let room = await this.load();
        if (!room) return ws.close(1011, 'room gone');
        const me = ws.deserializeAttachment() as Member | null;

        if (msg.t === 'hello') {
            const name = typeof msg.name === 'string' ? msg.name.trim().slice(0, 24) : '';
            if (typeof msg.clientId !== 'string' || !/^[0-9A-Za-z_-]{8,40}$/.test(msg.clientId) || !name) return ws.close(1008, 'bad hello');
            for (const w of this.ctx.getWebSockets()) { // a reconnect replaces the stale socket of the same client
                const m = w.deserializeAttachment() as Member | null;
                if (w !== ws && m?.clientId === msg.clientId) { w.serializeAttachment(null); w.close(1000, 'replaced'); }
            }
            ws.serializeAttachment({ clientId: msg.clientId, name, joinedAt: Date.now() } satisfies Member);
            if (!room.hostId || !this.members().some(m => m.clientId === room!.hostId)) { // nobody is host: the longest-connected is
                await this.save({ ...room, hostId: this.members()[0].clientId });
                room = this.room!;
            }
            ws.send(JSON.stringify({
                t: 'welcome', you: msg.clientId, serverTime: Date.now(), state: { rev: room.rev, queue: room.queue },
                playback: room.playback ?? null, hostId: room.hostId ?? null, members: this.membersMsg(room).members,
            }));
            this.broadcast(this.membersMsg(room), ws);
            return;
        }
        if (!me) return ws.close(1008, 'hello first');

        if (msg.t === 'ping') { // clock sync; costs no storage
            ws.send(JSON.stringify({ t: 'pong', t0: msg.t0, serverTime: Date.now() }));
            return;
        }

        const now = Date.now();
        const b = this.buckets.get(ws);
        if (!b || now - b.start > WINDOW_MS) this.buckets.set(ws, { start: now, count: 1 });
        else if (++b.count > OPS_PER_WINDOW) return void ws.send(JSON.stringify({ t: 'error', code: 'rate_limited' }));

        if (msg.t === 'seed') { // the host's queue and current song, once, into a fresh room
            const items = parseSeed(msg, me.name);
            if (room.hostId !== me.clientId || room.rev !== 0 || room.queue.length > 0 || items.length === 0)
                return void ws.send(JSON.stringify({ t: 'error', code: 'seed_rejected' }));
            const current = Number.isInteger(msg.current) ? items[msg.current] : undefined;
            const posMs = Number.isFinite(msg.posMs) ? Math.max(0, Number(msg.posMs)) : 0;
            const playback: Playback | undefined = current ? { itemId: current.id, playing: !!msg.playing, anchorPosMs: posMs, anchorServerMs: now } : undefined;
            await this.save({ ...room, rev: 1, queue: items, playback, lastActive: now });
            this.broadcast({ t: 'snapshot', state: { rev: 1, queue: items }, playback: playback ?? null });
            return;
        }

        const pb = room.playback;
        if (msg.t === 'play' || msg.t === 'pause' || msg.t === 'seek') {
            const posNow = pb ? positionAt(pb, now) : 0;
            const posMs = Number.isFinite(msg.posMs) ? Math.max(0, Number(msg.posMs)) : null;
            let next: Playback;
            if (msg.t === 'play') {
                const itemId = typeof msg.itemId === 'string' && room.queue.some(i => i.id === msg.itemId) ? msg.itemId : pb?.itemId;
                if (!itemId) return;
                next = { itemId, playing: true, anchorPosMs: posMs ?? (pb?.itemId === itemId ? posNow : 0), anchorServerMs: now };
            } else if (!pb) {
                return;
            } else if (msg.t === 'pause') {
                next = { ...pb, playing: false, anchorPosMs: posNow, anchorServerMs: now };
            } else {
                next = { ...pb, anchorPosMs: posMs ?? posNow, anchorServerMs: now };
            }
            // A new song started on purpose replaces the radio songs queued after it (the host then builds a fresh radio from it).
            // Songs people added themselves stay. Next, a tap on a queued song and a song ending don't ask for this.
            let queue = room.queue, rev = room.rev, removed: string[] = [];
            if (msg.t === 'play' && msg.regen === true) {
                removed = radioAfter(room.queue, next.itemId);
                if (removed.length) { const r = applyOp(room, { t: 'removeMany', ids: removed }, me.name); queue = r.state.queue; rev = r.state.rev; }
            }
            await this.save({ ...room, queue, rev, playback: next, lastActive: now });
            if (removed.length) this.broadcast({ t: 'removeMany', ids: removed, rev, by: me.name });
            this.broadcast({ t: 'pb', playback: next });
            return;
        }

        if (msg.t === 'mix') { // "Mix": a fresh radio from the current song
            const ids = radioAfter(room.queue, pb?.itemId);
            this.broadcast({ t: 'mix' }); // first, so the host restarts its radio before the queue shrinks and it refills
            if (ids.length) {
                const r = applyOp(room, { t: 'removeMany', ids }, me.name);
                await this.save({ ...r.state, lastActive: now });
                this.broadcast({ t: 'removeMany', ids, rev: r.state.rev, by: me.name });
            }
            return;
        }

        if (msg.t === 'ended') { // every phone reports the end of the song; only the first (and only when it's true) moves on
            if (!pb || !pb.playing || pb.itemId !== msg.itemId) return;
            const item = room.queue.find(i => i.id === pb.itemId);
            if (item && item.durationSec > 0 && positionAt(pb, now) < item.durationSec * 1000 - END_SLACK_MS) return;
            const next = advance(pb, nextAfter(room.queue, pb.itemId), now);
            await this.save({ ...room, playback: next, lastActive: now });
            this.broadcast({ t: 'pb', playback: next });
            return;
        }

        const op = parseOp(msg);
        if (!op) return void ws.send(JSON.stringify({ t: 'error', code: 'bad_op' }));
        const { state, changed, error } = applyOp(room, op, me.name);
        if (error) return void ws.send(JSON.stringify({ t: 'error', code: error }));
        if (!changed) return; // duplicate or no-op: nothing to store or tell anyone
        let playback = state.playback;
        if (op.t === 'remove' && pb?.itemId === op.id) { // the playing song was removed: carry on with the one after it, or stop
            const following = nextAfter(room.queue, op.id);
            playback = following ? advance(pb, following, now) : undefined;
        }
        await this.save({ ...state, playback, lastActive: now });
        this.broadcast({ ...op, rev: state.rev, by: me.name });
        if (playback !== state.playback) this.broadcast({ t: 'pb', playback: playback ?? null });
    }

    async webSocketClose(ws: WebSocket): Promise<void> {
        this.buckets.delete(ws);
        const gone = ws.deserializeAttachment() as Member | null;
        try { ws.close(); } catch { /* already closed */ }
        const room = await this.load();
        if (!room || !gone) return;
        const left = this.members();
        let next = room;
        if (room.hostId === gone.clientId && left.length > 0) next = { ...next, hostId: left[0].clientId }; // pass the host on
        if (left.length === 0 && next.playback?.playing) { // nobody is listening: stop the clock so a later joiner doesn't land at some far-off position
            const now = Date.now();
            next = { ...next, playback: { ...next.playback, playing: false, anchorPosMs: positionAt(next.playback, now), anchorServerMs: now } };
        }
        if (next !== room) await this.save(next);
        this.broadcast(this.membersMsg(next));
    }

    async webSocketError(ws: WebSocket): Promise<void> {
        await this.webSocketClose(ws);
    }

    /** Fires 24 h after creation, then re-arms while the room is in use; an idle, empty room is deleted. */
    async alarm(): Promise<void> {
        const room = await this.load();
        if (!room) return;
        const idleFor = Date.now() - room.lastActive;
        if (this.members().length > 0 || idleFor < IDLE_MS) {
            await this.ctx.storage.setAlarm(Date.now() + (this.members().length > 0 ? IDLE_MS : IDLE_MS - idleFor));
            return;
        }
        for (const w of this.ctx.getWebSockets()) w.close(1001, 'jam ended');
        await this.ctx.storage.deleteAll();
        this.room = null;
    }
}
