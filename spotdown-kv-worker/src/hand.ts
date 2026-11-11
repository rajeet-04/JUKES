import { DurableObject } from 'cloudflare:workers';
import type { Env } from './index';

const MAX_BYTES = 64 * 1024; // up to ~30 songs
const KEEP_MS = 24 * 3600 * 1000;

/**
 * Mailbox for playback handoff between a user's own devices (one per pairing code). The latest message wins:
 * an offer ("play this here"), its ack, or a pull request. A Durable Object, not KV, because the other device
 * has to see a write at once. Reads return the server clock so devices can age an offer without agreeing on time.
 */
export class HandBox extends DurableObject<Env> {
    async fetch(request: Request): Promise<Response> {
        if (request.method === 'PUT') {
            const body = await request.text();
            if (body.length > MAX_BYTES) return new Response('too big', { status: 413 });
            let box: unknown;
            try { box = JSON.parse(body); } catch { return new Response('bad json', { status: 400 }); }
            await this.ctx.storage.put('s', { box, at: Date.now() });
            await this.ctx.storage.setAlarm(Date.now() + KEEP_MS);
            return new Response('ok');
        }
        const s = await this.ctx.storage.get<{ box: unknown; at: number }>('s');
        return Response.json({ now: Date.now(), at: s?.at ?? 0, box: s?.box ?? null });
    }

    async alarm() { await this.ctx.storage.deleteAll(); }
}
