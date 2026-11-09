import { DurableObject } from 'cloudflare:workers';
import type { Env } from './index';

const MAX_DEVICES = 10;
const MAX_MESSAGE_BYTES = 64 * 1024; // a state carries up to ~50 queued songs
const MSGS_PER_WINDOW = 40;          // per socket, per 10 s
const WINDOW_MS = 10_000;
const OPEN = 1;

interface Device { deviceId: string; name: string; kind: string }
/** What the playing device last reported. Position is anchored: posMs at server time [at]. */
interface State { activeId: string; activeName: string; track: unknown; queue: unknown[]; index: number; posMs: number; playing: boolean; at: number }

/**
 * Spotify Connect for one account: every logged-in device of that account holds a socket here.
 * The device playing audio ("active") reports its state; the others show it and send commands,
 * which are forwarded to the active device only. "Play here" sends a take to the new device, which
 * starts the queue and reports state; the old one sees it is no longer active and stops.
 * The Worker authenticates the login token before forwarding the upgrade, so everything here is one account.
 */
export class ConnectHub extends DurableObject<Env> {
    private state: State | null | undefined;
    private buckets = new Map<WebSocket, { start: number; count: number }>();

    private async load(): Promise<State | null> {
        if (this.state === undefined) this.state = (await this.ctx.storage.get<State>('s')) ?? null;
        return this.state;
    }

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
            this.send(ws, { t: 'welcome', now, state: await this.load() });
            this.broadcast({ t: 'devices', devices: this.devices() });
            return;
        }
        if (!me) return ws.close(1008, 'hello first');

        if (msg.t === 'ping') return this.send(ws, { t: 'pong', t0: msg.t0, now });

        if (msg.t === 'synced') { // the sender saved the library: the others fetch it now
            for (const w of this.ctx.getWebSockets()) if (w !== ws && w.readyState === OPEN && w.deserializeAttachment()) this.send(w, { t: 'synced' });
            return;
        }

        if (msg.t === 'state') { // the sender is playing (or was told to): it is the active device now
            const state: State = {
                activeId: me.deviceId, activeName: me.name, track: msg.track ?? null,
                queue: Array.isArray(msg.queue) ? msg.queue.slice(0, 60) : [], index: Number.isInteger(msg.index) ? msg.index : 0,
                posMs: Number.isFinite(msg.posMs) ? Math.max(0, msg.posMs) : 0, playing: !!msg.playing, at: now,
            };
            this.state = state;
            await this.ctx.storage.put('s', state);
            this.broadcast({ t: 'state', now, state });
            return;
        }

        if (msg.t === 'cmd') { // a remote control press, for the active device
            const s = await this.load();
            const op = String(msg.op ?? '');
            if (op === 'take') { // "play on <target>": target defaults to the sender ("play here")
                const target = typeof msg.target === 'string' ? msg.target : me.deviceId;
                const w = this.socketOf(target);
                if (!s || !w) return this.send(ws, { t: 'error', code: s ? 'device_gone' : 'nothing_playing' });
                const posMs = s.playing ? s.posMs + (now - s.at) : s.posMs;
                return this.send(w, { t: 'take', state: { ...s, posMs, playing: true }, from: me.name });
            }
            if (!s) return this.send(ws, { t: 'error', code: 'nothing_playing' });
            const w = this.socketOf(s.activeId);
            if (!w) return this.send(ws, { t: 'error', code: 'device_gone' });
            return this.send(w, { t: 'cmd', op, posMs: Number.isFinite(msg.posMs) ? msg.posMs : undefined, index: Number.isInteger(msg.index) ? msg.index : undefined, by: me.name });
        }
    }

    async webSocketClose(ws: WebSocket): Promise<void> {
        this.buckets.delete(ws);
        const gone = ws.deserializeAttachment() as Device | null;
        try { ws.close(); } catch { /* already closed */ }
        if (!gone) return;
        const s = await this.load();
        if (s?.activeId === gone.deviceId && s.playing) { // the player went away: freeze its clock
            const now = Date.now();
            this.state = { ...s, playing: false, posMs: s.posMs + (now - s.at), at: now };
            await this.ctx.storage.put('s', this.state);
            this.broadcast({ t: 'state', now, state: this.state });
        }
        this.broadcast({ t: 'devices', devices: this.devices() });
    }
}
