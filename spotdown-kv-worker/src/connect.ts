import { DurableObject } from 'cloudflare:workers';
import type { Env } from './index';

const MAX_DEVICES = 10;
const MAX_MESSAGE_BYTES = 64 * 1024; // a state carries up to ~50 queued songs
const MSGS_PER_WINDOW = 40;          // per socket, per 10 s
const WINDOW_MS = 10_000;
const OPEN = 1;
const SYNCED_RELAY_MS = 60_000;

interface Device { deviceId: string; name: string; kind: string }
/** What one device last reported about its own playback. Position is anchored: posMs at server time [at]. */
interface State { activeId: string; activeName: string; track: unknown; queue: unknown[]; index: number; posMs: number; playing: boolean; at: number }

/**
 * Spotify Connect for one account: every logged-in device of that account holds a socket here.
 * Each device plays on its own and reports its own state; every device sees all of them. A remote control
 * press names the device it is for and goes to that device only. "Play on <target>" sends the sender's state
 * to the target, which starts the same queue at the same spot; nobody else stops.
 * The Worker authenticates the login token before forwarding the upgrade, so everything here is one account.
 */
export class ConnectHub extends DurableObject<Env> {
    private states: Map<string, State> | undefined;
    private buckets = new Map<WebSocket, { start: number; count: number }>();
    private lastSyncedRelay = 0;

    /** One storage key per device: a state with its queue is ~20 KB and a value may hold 128 KB. */
    private async load(): Promise<Map<string, State>> {
        if (!this.states) {
            const stored = await this.ctx.storage.list<State>({ prefix: 's:' });
            this.states = new Map([...stored.values()].map(st => [st.activeId, st]));
        }
        return this.states;
    }

    private async save(st: State) {
        const states = await this.load();
        states.set(st.activeId, st);
        await this.ctx.storage.put('s:' + st.activeId, st);
        if (states.size > MAX_DEVICES) { // forget the device heard from longest ago
            const oldest = [...states.values()].sort((a, b) => a.at - b.at)[0];
            states.delete(oldest.activeId);
            await this.ctx.storage.delete('s:' + oldest.activeId);
        }
    }

    private live(st: State, now: number): State { return st.playing ? { ...st, posMs: st.posMs + (now - st.at), at: now } : st; }

    private devices(): Device[] {
        return this.ctx.getWebSockets().filter(w => w.readyState === OPEN)
            .map(w => w.deserializeAttachment() as Device | null).filter((d): d is Device => !!d);
    }

    private send(ws: WebSocket, msg: object) { try { ws.send(JSON.stringify(msg)); } catch { /* closing */ } }

    private broadcast(msg: object) {
        const text = JSON.stringify(msg);
        for (const w of this.ctx.getWebSockets()) if (w.readyState === OPEN && w.deserializeAttachment()) try { w.send(text); } catch { /* closing */ }
    }

    private socketOf(deviceId: string): WebSocket | undefined {
        return this.ctx.getWebSockets().find(w => w.readyState === OPEN && (w.deserializeAttachment() as Device | null)?.deviceId === deviceId);
    }

    async fetch(request: Request): Promise<Response> {
        if (request.headers.get('Upgrade') !== 'websocket') return new Response('Expected a websocket', { status: 426 });
        if (this.ctx.getWebSockets().length >= MAX_DEVICES * 2) return new Response('Too many devices', { status: 409 });
        const [client, server] = Object.values(new WebSocketPair());
        this.ctx.acceptWebSocket(server);
        return new Response(null, { status: 101, webSocket: client });
    }

    async webSocketMessage(ws: WebSocket, message: string | ArrayBuffer): Promise<void> {
        if (typeof message !== 'string' || message.length > MAX_MESSAGE_BYTES) return ws.close(1009, 'bad message');
        let msg: any;
        try { msg = JSON.parse(message); } catch { return ws.close(1003, 'bad json'); }
        const now = Date.now();
        const b = this.buckets.get(ws);
        if (!b || now - b.start > WINDOW_MS) this.buckets.set(ws, { start: now, count: 1 });
        else if (++b.count > MSGS_PER_WINDOW) return;
        const me = ws.deserializeAttachment() as Device | null;

        if (msg.t === 'hello') {
            const name = typeof msg.name === 'string' ? msg.name.trim().slice(0, 40) : '';
            if (typeof msg.deviceId !== 'string' || !/^[0-9A-Za-z_-]{8,40}$/.test(msg.deviceId) || !name) return ws.close(1008, 'bad hello');
            for (const w of this.ctx.getWebSockets()) { // a reconnect replaces the stale socket of the same device
                const d = w.deserializeAttachment() as Device | null;
                if (w !== ws && d?.deviceId === msg.deviceId) { w.serializeAttachment(null); try { w.close(1000, 'replaced'); } catch { /* gone */ } }
            }
            ws.serializeAttachment({ deviceId: msg.deviceId, name, kind: msg.kind === 'desktop' ? 'desktop' : 'phone' } satisfies Device);
            this.send(ws, { t: 'welcome', now, states: [...(await this.load()).values()] });
            this.broadcast({ t: 'devices', devices: this.devices() });
            return;
        }
        if (!me) return ws.close(1008, 'hello first');

        if (msg.t === 'ping') return this.send(ws, { t: 'pong', t0: msg.t0, now });

        if (msg.t === 'synced') { // the sender saved the library: the others fetch it now
            // at most once a minute: two devices that keep disagreeing must not bounce saves back and forth
            if (now - this.lastSyncedRelay < SYNCED_RELAY_MS) return;
            this.lastSyncedRelay = now;
            for (const w of this.ctx.getWebSockets()) if (w !== ws && w.readyState === OPEN && w.deserializeAttachment()) this.send(w, { t: 'synced' });
            return;
        }

        if (msg.t === 'state') { // the sender's own playback
            const state: State = {
                activeId: me.deviceId, activeName: me.name, track: msg.track ?? null,
                queue: Array.isArray(msg.queue) ? msg.queue.slice(0, 60) : [], index: Number.isInteger(msg.index) ? msg.index : 0,
                posMs: Number.isFinite(msg.posMs) ? Math.max(0, msg.posMs) : 0, playing: !!msg.playing, at: now,
            };
            await this.save(state);
            this.broadcast({ t: 'state', now, state });
            return;
        }

        if (msg.t === 'cmd') { // a remote control press, for the device it names
            const op = String(msg.op ?? '');
            const target = typeof msg.target === 'string' ? msg.target : '';
            const w = this.socketOf(target);
            if (!w) return this.send(ws, { t: 'error', code: 'device_gone' });
            if (op === 'take') { // "play on <target>": the sender's playback, started there
                const mine = (await this.load()).get(me.deviceId);
                if (!mine?.track) return this.send(ws, { t: 'error', code: 'nothing_playing' });
                return this.send(w, { t: 'take', state: { ...this.live(mine, now), playing: true }, from: me.name });
            }
            return this.send(w, { t: 'cmd', op, posMs: Number.isFinite(msg.posMs) ? msg.posMs : undefined, index: Number.isInteger(msg.index) ? msg.index : undefined, by: me.name });
        }
    }

    async webSocketClose(ws: WebSocket): Promise<void> {
        this.buckets.delete(ws);
        const gone = ws.deserializeAttachment() as Device | null;
        try { ws.close(); } catch { /* already closed */ }
        if (!gone) return;
        const st = (await this.load()).get(gone.deviceId);
        if (st?.playing) { // it went away mid-song: freeze its clock
            const now = Date.now();
            const frozen = { ...this.live(st, now), playing: false };
            await this.save(frozen);
            this.broadcast({ t: 'state', now, state: frozen });
        }
        this.broadcast({ t: 'devices', devices: this.devices() });
    }
}
