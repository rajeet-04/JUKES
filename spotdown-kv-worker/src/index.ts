export interface Env {
    KV: KVNamespace;
    DB: D1Database;
    SPOTIFY_CLIENT_ID: string;
    SPOTIFY_CLIENT_SECRET: string;
    APP_SECRET: string;
    CERT_SHA256?: string; // comma-separated app signing cert fingerprints for assetlinks.json
}

const REFRESH_MARGIN_MS = 300_000; // app-side buffer is the same; never hand out a nearly-dead token
const KV_TTL_S = 3300;             // Spotify tokens live 3600s

const SHARE_TTL_S = 7 * 86400;               // sliding: reads push expiry out again
const SHARE_MAX_AGE_MS = 30 * 86400 * 1000;  // ...but never past this from creation
const TOUCH_EVERY_MS = 86400 * 1000;         // at most one expiry push per share per day
const SHARE_MAX_BYTES = 256 * 1024;
const SHARE_MAX_TRACKS = 500;
const ID_CHARS = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz';

const newId = () => Array.from(crypto.getRandomValues(new Uint8Array(10)), b => ID_CHARS[b % 62]).join('');
const esc = (s: string) => s.replace(/[&<>"']/g, c => `&#${c.charCodeAt(0)};`);

interface Share { v: number; kind: string; name: string; tracks: unknown[] }
interface ShareRow { body: string; created_at: number; touched_at: number }

// Expiry slides forward on reads, but at most once a day per share (each push is a D1 write), and never from the landing page.
async function readShare(env: Env, id: string, touch = true): Promise<Share | null> {
    const now = Date.now();
    const row = await env.DB.prepare('SELECT body, created_at, touched_at FROM shares WHERE id = ? AND expires_at > ?')
        .bind(id, now).first<ShareRow>();
    if (!row) return null;
    if (touch && now - row.touched_at > TOUCH_EVERY_MS) {
        const expires = Math.min(now + SHARE_TTL_S * 1000, row.created_at + SHARE_MAX_AGE_MS);
        await env.DB.prepare('UPDATE shares SET touched_at = ?, expires_at = ? WHERE id = ?').bind(now, expires, id).run();
    }
    return JSON.parse(row.body);
}

async function createShare(request: Request, env: Env): Promise<Response> {
    if (request.headers.get('X-App-Secret') !== env.APP_SECRET) return Response.json({ error: 'Unauthorized' }, { status: 401 });
    const raw = await request.text();
    if (raw.length > SHARE_MAX_BYTES) return Response.json({ error: 'Too large' }, { status: 413 });
    let body: Share;
    try { body = JSON.parse(raw); } catch { return Response.json({ error: 'Bad JSON' }, { status: 400 }); }
    if (body.v !== 1 || body.kind !== 'playlist' || typeof body.name !== 'string' || !Array.isArray(body.tracks) || body.tracks.length > SHARE_MAX_TRACKS)
        return Response.json({ error: 'Invalid share' }, { status: 400 });
    const id = newId();
    const now = Date.now();
    await env.DB.prepare('INSERT INTO shares (id, body, created_at, touched_at, expires_at) VALUES (?, ?, ?, ?, ?)')
        .bind(id, JSON.stringify(body), now, now, now + SHARE_TTL_S * 1000).run();
    return Response.json({ id });
}

async function landing(env: Env, id: string): Promise<Response> {
    const share = await readShare(env, id, false);
    const title = share ? esc(share.name) : 'Link expired';
    const sub = share ? `${share.tracks.length} songs` : 'This shared playlist is no longer available.';
    const open = share ? `<a class="b" href="juke://p/${id}">Open in JUKES</a>` : '';
    return new Response(
        `<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${title} · JUKES</title>` +
        `<style>body{font:16px system-ui;background:#111;color:#eee;display:grid;place-items:center;min-height:100vh;margin:0;text-align:center}` +
        `.b{display:inline-block;margin-top:20px;padding:12px 24px;border-radius:24px;background:#1db954;color:#000;text-decoration:none;font-weight:600}</style>` +
        `<div><h1>${title}</h1><p>${sub}</p>${open}</div>`,
        { status: share ? 200 : 404, headers: { 'Content-Type': 'text/html; charset=utf-8' } });
}

// ponytail: concurrent cold requests may each refresh once; harmless, add a Durable Object if it ever matters
export default {
    async scheduled(_event: ScheduledController, env: Env): Promise<void> {
        await env.DB.prepare('DELETE FROM shares WHERE expires_at < ?').bind(Date.now()).run();
    },

    async fetch(request: Request, env: Env): Promise<Response> {
        const { pathname } = new URL(request.url);
        const m = pathname.match(/^\/(share|p)\/([0-9A-Za-z]{10})$/);
        if (pathname === '/share' && request.method === 'POST') return createShare(request, env);
        if (m?.[1] === 'share') {
            const share = await readShare(env, m[2]);
            return share ? Response.json(share) : Response.json({ error: 'Not Found' }, { status: 404 });
        }
        if (m?.[1] === 'p') return landing(env, m[2]);
        if (pathname === '/.well-known/assetlinks.json') return Response.json([{
            relation: ['delegate_permission/common.handle_all_urls'],
            target: { namespace: 'android_app', package_name: 'com.example.juke', sha256_cert_fingerprints: (env.CERT_SHA256 ?? '').split(',').filter(Boolean) },
        }]);
        if (pathname !== '/spotify-token') return new Response('Not Found', { status: 404 });
        if (request.headers.get('X-App-Secret') !== env.APP_SECRET) return Response.json({ error: 'Unauthorized' }, { status: 401 });

        let hit = await env.KV.get<{ token: string; exp: number }>('spotify', 'json');
        if (!hit || hit.exp - Date.now() < REFRESH_MARGIN_MS) {
            const res = await fetch('https://accounts.spotify.com/api/token', {
                method: 'POST',
                headers: {
                    Authorization: 'Basic ' + btoa(`${env.SPOTIFY_CLIENT_ID}:${env.SPOTIFY_CLIENT_SECRET}`),
                    'Content-Type': 'application/x-www-form-urlencoded',
                },
                body: 'grant_type=client_credentials',
            });
            if (!res.ok) return Response.json({ error: 'spotify ' + res.status }, { status: 502 });
            const { access_token, expires_in } = await res.json<{ access_token: string; expires_in: number }>();
            hit = { token: access_token, exp: Date.now() + expires_in * 1000 };
            await env.KV.put('spotify', JSON.stringify(hit), { expirationTtl: KV_TTL_S });
        }
        return Response.json({ access_token: hit.token, expires_in: Math.floor((hit.exp - Date.now()) / 1000) });
    },
};
