import { DurableObject } from 'cloudflare:workers';
import type { Env } from './index';
import { applyOp, parseOp, type RoomState } from './jam-logic';

const MAX_MEMBERS = 10;
const MAX_MESSAGE_BYTES = 8 * 1024;
const IDLE_MS = 24 * 3600 * 1000;
const OPS_PER_WINDOW = 30; // per socket, per 10 s: a stuck client must not burn the daily write budget
const WINDOW_MS = 10_000;
const OPEN = 1;

interface Member { clientId: string; name: string }

/**
 * One Jam room. All ops run one at a time in this object, so concurrent edits get a single order.
 * Storage is one row (the whole room), rewritten once per state-changing op; presence, pings and
 * everything else live in memory or on the sockets.
 */
export class JamRoom extends DurableObject<Env> {
    private room: RoomState | null | undefined;
    private buckets = new Map<WebSocket, { start: number; count: number }>(); // memory only; a hibernation reset is harmless

    private async load(): Promise<RoomState | null> {
        if (this.room === undefined) this.room = (await this.ctx.storage.get<RoomState>('room')) ?? null;
        return this.room;
    }

    private members(): Member[] {
        return this.ctx.getWebSockets().filter(w => w.readyState === OPEN)
            .map(w => w.deserializeAttachment() as Member | null).filter((m): m is Member => !!m);
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
        const room = await this.load();
        if (!room) return ws.close(1011, 'room gone');
        const me = ws.deserializeAttachment() as Member | null;

        if (msg.t === 'hello') {
            const name = typeof msg.name === 'string' ? msg.name.trim().slice(0, 24) : '';
            if (typeof msg.clientId !== 'string' || !/^[0-9A-Za-z_-]{8,40}$/.test(msg.clientId) || !name) return ws.close(1008, 'bad hello');
            for (const w of this.ctx.getWebSockets()) { // a reconnect replaces the stale socket of the same client
                const m = w.deserializeAttachment() as Member | null;
                if (w !== ws && m?.clientId === msg.clientId) { w.serializeAttachment(null); w.close(1000, 'replaced'); }
            }
            ws.serializeAttachment({ clientId: msg.clientId, name } satisfies Member);
            ws.send(JSON.stringify({ t: 'welcome', you: msg.clientId, serverTime: Date.now(), state: { rev: room.rev, queue: room.queue }, members: this.members() }));
            this.broadcast({ t: 'members', members: this.members() }, ws);
            return;
        }
        if (!me) return ws.close(1008, 'hello first');

        if (msg.t === 'ping') { // clock sync for release 2; costs no storage
            ws.send(JSON.stringify({ t: 'pong', t0: msg.t0, serverTime: Date.now() }));
            return;
        }

        const op = parseOp(msg);
        if (!op) return void ws.send(JSON.stringify({ t: 'error', code: 'bad_op' }));
        const now = Date.now();
        const b = this.buckets.get(ws);
        if (!b || now - b.start > WINDOW_MS) this.buckets.set(ws, { start: now, count: 1 });
        else if (++b.count > OPS_PER_WINDOW) return void ws.send(JSON.stringify({ t: 'error', code: 'rate_limited' }));

        const { state, changed, error } = applyOp(room, op, me.name);
        if (error) return void ws.send(JSON.stringify({ t: 'error', code: error }));
        if (!changed) return; // duplicate or no-op: nothing to store or tell anyone
        this.room = { ...state, lastActive: now };
        await this.ctx.storage.put('room', this.room); // committed before anyone hears about it
        this.broadcast({ ...op, rev: state.rev, by: me.name });
    }

    async webSocketClose(ws: WebSocket): Promise<void> {
        this.buckets.delete(ws);
        try { ws.close(); } catch { /* already closed */ }
        this.broadcast({ t: 'members', members: this.members() });
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
