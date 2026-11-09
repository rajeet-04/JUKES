import type { Env } from './index';

/**
 * Accounts: email + password, no email sending (free plan). The app stretches the password with PBKDF2
 * (salt = the email) before sending it, so this side only adds a random salt and one SHA-256: the
 * expensive part stays off the Worker's 10 ms CPU budget, and a leaked table still costs a full PBKDF2
 * per guess. Each device gets its own login token; only its hash is stored.
 *
 * The library (playlists, liked songs, settings) is one JSON document per account in D1 (strongly consistent,
 * unlike KV) with a revision number: a device that saves over a revision it hasn't seen gets 409 and merges again.
 * ponytail: one document, merged on the devices; split it per playlist if a library nears D1's 2 MB row limit.
 */

const LIB_MAX_BYTES = 1_900_000; // D1 rows top out at 2 MB
const MAX_FAILS = 10;
const LOCK_MS = 15 * 60 * 1000;
const SEEN_EVERY_MS = 86400 * 1000;
const HAND_CHARS = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const KEY = /^[0-9a-f]{64}$/;

interface User { id: string; email: string; salt: string; hash: string; hand: string; fails: number; locked_until: number }

const hex = (b: ArrayBuffer | Uint8Array) => Array.from(new Uint8Array(b), x => x.toString(16).padStart(2, '0')).join('');
const sha = async (s: string) => hex(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(s)));
const rand = (n: number) => hex(crypto.getRandomValues(new Uint8Array(n)));
const err = (error: string, status: number) => Response.json({ error }, { status });

/** Constant-time compare of two hex strings. */
function same(a: string, b: string): boolean {
    if (a.length !== b.length) return false;
    let d = 0;
    for (let i = 0; i < a.length; i++) d |= a.charCodeAt(i) ^ b.charCodeAt(i);
    return d === 0;
}

async function body(request: Request): Promise<Record<string, unknown> | null> {
    try { const b = await request.json(); return b && typeof b === 'object' ? b as Record<string, unknown> : null; } catch { return null; }
}

async function newSession(env: Env, user: User, device: unknown): Promise<Response> {
    const token = rand(32), now = Date.now();
    await env.DB.prepare('INSERT INTO sessions (token_hash, user_id, device, created_at, seen_at) VALUES (?, ?, ?, ?, ?)')
        .bind(await sha(token), user.id, String(device || 'Device').slice(0, 60), now, now).run();
    return Response.json({ token, email: user.email, hand: user.hand });
}

/** The account behind the request's login token, or null. */
export async function authed(request: Request, env: Env): Promise<{ userId: string; tokenHash: string } | null> {
    const token = request.headers.get('Authorization')?.replace(/^Bearer /, '') ?? '';
    if (!KEY.test(token)) return null;
    const tokenHash = await sha(token);
    const s = await env.DB.prepare('SELECT user_id, seen_at FROM sessions WHERE token_hash = ?').bind(tokenHash).first<{ user_id: string; seen_at: number }>();
    if (!s) return null;
    if (Date.now() - s.seen_at > SEEN_EVERY_MS) await env.DB.prepare('UPDATE sessions SET seen_at = ? WHERE token_hash = ?').bind(Date.now(), tokenHash).run();
    return { userId: s.user_id, tokenHash };
}

/** Checks the password, counting failures; after too many the account waits [LOCK_MS] before trying again. */
async function checkPassword(env: Env, user: User, key: string): Promise<Response | null> {
    const now = Date.now();
    if (user.locked_until > now) return err('Too many tries. Wait 15 minutes and try again.', 429);
    if (same(await sha(user.salt + key), user.hash)) {
        if (user.fails) await env.DB.prepare('UPDATE users SET fails = 0 WHERE id = ?').bind(user.id).run();
        return null;
    }
    const fails = user.fails + 1;
    await env.DB.prepare('UPDATE users SET fails = ?, locked_until = ? WHERE id = ?')
        .bind(fails >= MAX_FAILS ? 0 : fails, fails >= MAX_FAILS ? now + LOCK_MS : 0, user.id).run();
    return err('Wrong email or password', 401);
}

export async function account(request: Request, env: Env, action: string): Promise<Response> {
    if (request.headers.get('X-App-Secret') !== env.APP_SECRET) return err('Unauthorized', 401);
    const method = request.method;

    if ((action === 'register' || action === 'login') && method === 'POST') {
        const b = await body(request);
        const email = String(b?.email ?? '').trim().toLowerCase(), key = String(b?.key ?? '');
        if (!EMAIL.test(email) || email.length > 254 || !KEY.test(key)) return err('Enter a valid email and password', 400);
        const user = await env.DB.prepare('SELECT * FROM users WHERE email = ?').bind(email).first<User>();
        if (action === 'login') {
            if (!user) return err('Wrong email or password', 401);
            return (await checkPassword(env, user, key)) ?? newSession(env, user, b?.device);
        }
        if (user) return err('An account with this email already exists. Log in instead.', 409);
        const salt = rand(16);
        const created: User = {
            id: rand(16), email, salt, hash: await sha(salt + key), fails: 0, locked_until: 0,
            hand: Array.from(crypto.getRandomValues(new Uint8Array(6)), x => HAND_CHARS[x % HAND_CHARS.length]).join(''),
        };
        await env.DB.prepare('INSERT INTO users (id, email, salt, hash, hand, created_at) VALUES (?, ?, ?, ?, ?, ?)')
            .bind(created.id, email, salt, created.hash, created.hand, Date.now()).run();
        return newSession(env, created, b?.device);
    }

    const me = await authed(request, env);
    if (!me) return err('Logged out. Log in again.', 401);

    if (action === 'library' && method === 'GET') {
        const row = await env.DB.prepare('SELECT rev, body FROM libraries WHERE user_id = ?').bind(me.userId).first<{ rev: number; body: string }>();
        return new Response(`{"rev":${row?.rev ?? 0},"doc":${row?.body ?? 'null'}}`, { headers: { 'Content-Type': 'application/json' } });
    }
    if (action === 'library' && method === 'PUT') { // ?rev= the revision this device merged with
        const rev = Number(new URL(request.url).searchParams.get('rev'));
        const raw = await request.text();
        if (!Number.isInteger(rev) || rev < 0) return err('Missing rev', 400);
        if (raw.length > LIB_MAX_BYTES) return err('Library too large to sync', 413);
        try { JSON.parse(raw); } catch { return err('Bad JSON', 400); }
        const now = Date.now();
        const done = rev === 0
            ? await env.DB.prepare('INSERT INTO libraries (user_id, rev, body, updated_at) VALUES (?, 1, ?, ?) ON CONFLICT (user_id) DO NOTHING').bind(me.userId, raw, now).run()
            : await env.DB.prepare('UPDATE libraries SET rev = rev + 1, body = ?, updated_at = ? WHERE user_id = ? AND rev = ?').bind(raw, now, me.userId, rev).run();
        if (!done.meta.changes) return err('Changed on another device', 409);
        return Response.json({ rev: rev + 1 });
    }
    if (action === 'logout' && method === 'POST') {
        await env.DB.prepare('DELETE FROM sessions WHERE token_hash = ?').bind(me.tokenHash).run();
        return Response.json({ ok: true });
    }
    if ((action === 'password' || action === 'delete') && method === 'POST') {
        const b = await body(request);
        const key = String(b?.key ?? ''), newKey = String(b?.newKey ?? '');
        const user = await env.DB.prepare('SELECT * FROM users WHERE id = ?').bind(me.userId).first<User>();
        if (!user || !KEY.test(key)) return err('Wrong password', 401);
        const bad = await checkPassword(env, user, key);
        if (bad) return bad.status === 401 ? err('Wrong password', 401) : bad;
        if (action === 'delete') {
            await env.DB.batch([
                env.DB.prepare('DELETE FROM sessions WHERE user_id = ?').bind(user.id),
                env.DB.prepare('DELETE FROM libraries WHERE user_id = ?').bind(user.id),
                env.DB.prepare('DELETE FROM users WHERE id = ?').bind(user.id),
            ]);
            return Response.json({ ok: true });
        }
        if (!KEY.test(newKey)) return err('Enter a new password', 400);
        const salt = rand(16);
        // other devices stay logged in: changing the password from a device you still have is the only way back in
        await env.DB.prepare('UPDATE users SET salt = ?, hash = ? WHERE id = ?').bind(salt, await sha(salt + newKey), user.id).run();
        return Response.json({ ok: true });
    }
    return err('Not Found', 404);
}
