import { afterEach, expect, setSystemTime, test } from 'bun:test';
import { Database } from 'bun:sqlite';
import { readFileSync } from 'fs';
import { account } from './account';
import { wrapped } from './wrapped';

function fakeEnv(extra: Record<string, unknown> = {}) {
    const db = new Database(':memory:');
    for (const f of ['0004_accounts.sql', '0005_wrapped_cache.sql']) db.exec(readFileSync(new URL(`../migrations/${f}`, import.meta.url), 'utf8'));
    const stmt = (sql: string, args: unknown[] = []) => ({
        bind: (...a: unknown[]) => stmt(sql, a),
        first: async () => db.query(sql).get(...(args as any[])) ?? null,
        run: async () => ({ meta: { changes: db.query(sql).run(...(args as any[])).changes } }),
    });
    const kv = new Map<string, string>([['spotify', JSON.stringify({ token: 't', exp: Date.now() + 3_000_000 })]]);
    return {
        APP_SECRET: 's',
        DB: { prepare: (sql: string) => stmt(sql), batch: async (list: any[]) => { for (const s of list) await s.run(); } },
        KV: { get: async (k: string, t?: string) => (kv.has(k) ? (t === 'json' ? JSON.parse(kv.get(k)!) : kv.get(k)) : null), put: async (k: string, v: string) => void kv.set(k, v) },
        ...extra,
    } as any;
}
const configured = { POSTHOG_API_HOST: 'https://ph', POSTHOG_PROJECT_ID: '1', POSTHOG_PERSONAL_API_KEY: 'k' };
const K = '1'.repeat(64);

async function login(env: any) {
    const res = await account(new Request('https://x/account/register', { method: 'POST', headers: { 'X-App-Secret': 's' }, body: JSON.stringify({ email: 'Me@X.com', key: K }) }), env, 'register');
    return (await res.json() as any).token as string;
}
const get = (env: any, token: string | null, q = '') =>
    wrapped(new Request('https://x/account/wrapped' + q, { headers: { 'X-App-Secret': 's', ...(token ? { Authorization: `Bearer ${token}` } : {}) } }), env);

const realFetch = globalThis.fetch;
afterEach(() => { globalThis.fetch = realFetch; setSystemTime(); });

/** PostHog stand-in: answers each query by the first words of its SQL; counts the calls. */
function stubPostHog(opts: { fail?: boolean; days?: unknown[][] } = {}) {
    const sent: string[] = [];
    globalThis.fetch = (async (url: any, init: any) => {
        const u = String(url);
        if (u.startsWith('https://ph/')) {
            if (opts.fail) return new Response('nope', { status: 500 });
            const sql: string = JSON.parse(init.body).query.query;
            sent.push(sql);
            const rows =
                sql.includes('GROUP BY day') ? (opts.days ?? [['2026-12-01', 600000, 5, 1]]) :
                sql.includes('listened_ms_by_hour') ? [['h', '20', 600000], ['s', 'playlist', 600000]] :
                sql.includes('GROUP BY song_key') ? [['x - a', 'x', 'a', 'sp', 'al', 600000, 5]] :
                sql.includes('GROUP BY aid') ? [['ar1', 600000]] :
                sql.includes('GROUP BY album') ? [['al1', 600000]] : [[3]];
            return Response.json({ results: rows });
        }
        if (u.includes('/artists')) return Response.json({ artists: [{ id: 'ar1', name: 'Artist One', genres: ['pop', 'indie'] }] });
        if (u.includes('/albums')) return Response.json({ albums: [{ id: 'al1', name: 'Album One' }] });
        throw new Error('unexpected fetch ' + u);
    }) as any;
    return sent;
}

test('needs the app secret and a login', async () => {
    const env = fakeEnv(configured);
    expect((await wrapped(new Request('https://x/account/wrapped'), env)).status).toBe(401);
    expect((await get(env, null)).status).toBe(401);
});

test('answers 503 until PostHog is configured', async () => {
    const env = fakeEnv();
    expect((await get(env, await login(env))).status).toBe(503);
});

test('rejects years outside the feature', async () => {
    const env = fakeEnv(configured);
    const t = await login(env);
    expect((await get(env, t, '?year=2025')).status).toBe(400);
    expect((await get(env, t, '?year=3000')).status).toBe(400);
    expect((await get(env, t, '?year=abc')).status).toBe(400);
});

test('computes from PostHog, adds Spotify names, then serves the cache', async () => {
    const env = fakeEnv(configured);
    const t = await login(env);
    const year = new Date().getUTCFullYear();
    const sent = stubPostHog();
    const res = await get(env, t, `?year=${year}`);
    expect(res.status).toBe(200);
    const w = await res.json() as any;
    expect(w.plays).toBe(5);
    expect(w.top_artists[0].name).toBe('Artist One');
    expect(w.top_albums[0].name).toBe('Album One');
    expect(w.top_genres.map((g: any) => g.genre)).toEqual(['pop', 'indie']);
    expect(sent.length).toBe(6);
    expect(sent.every(s => s.includes("distinct_id = 'me@x.com'"))).toBe(true); // the account's own lowercased email
    await get(env, t, `?year=${year}`);
    expect(sent.length).toBe(6); // second request came from the cache
});

test('a failing Spotify token fetch only skips the names', async () => {
    // no cached token, and the stub throws on the token request: the numbers must still come back
    const env = fakeEnv({ ...configured, KV: { get: async () => null, put: async () => {} } });
    const t = await login(env);
    stubPostHog();
    const res = await get(env, t, `?year=${new Date().getUTCFullYear()}`);
    expect(res.status).toBe(200);
    const w = await res.json() as any;
    expect(w.plays).toBe(5);
    expect(w.top_artists[0].name).toBe(null);
});

test('an account deleted while its Wrapped is computing does not get its cache back', async () => {
    const env = fakeEnv(configured);
    const t = await login(env);
    stubPostHog();
    const inner = globalThis.fetch;
    let deleted = false;
    globalThis.fetch = (async (url: any, init: any) => {
        if (!deleted && String(url).startsWith('https://ph/')) {
            deleted = true; // the account is deleted while the first PostHog query is in flight
            await env.DB.prepare('DELETE FROM users').run();
            await env.DB.prepare('DELETE FROM wrapped_cache').run();
        }
        return inner(url, init);
    }) as any;
    const res = await get(env, t, `?year=${new Date().getUTCFullYear()}`);
    expect(res.status).toBe(401);
    expect(await env.DB.prepare('SELECT COUNT(*) AS n FROM wrapped_cache').first()).toEqual({ n: 0 });
});

test('a PostHog failure is a 502, or the old answer when there is one', async () => {
    const env = fakeEnv(configured);
    const t = await login(env);
    stubPostHog({ fail: true });
    expect((await get(env, t)).status).toBe(502);
    const year = new Date().getUTCFullYear();
    await env.DB.prepare('INSERT INTO wrapped_cache (user_id, year, body, computed_at) SELECT id, ?, ?, 0 FROM users').bind(year, JSON.stringify({ plays: 9 })).run();
    const res = await get(env, t);
    expect(res.status).toBe(200);
    expect((await res.json() as any).plays).toBe(9);
});

test('a year closed only recently is refreshed, then frozen once late uploads have settled', async () => {
    const { isFinal } = await import('./wrapped');
    const jan1 = Date.UTC(2027, 0, 1);
    expect(isFinal(2026, Date.UTC(2026, 11, 20))).toBe(false); // computed in December: partial
    expect(isFinal(2026, jan1 + 3 * 86_400_000)).toBe(false); // days 31 Dec may still be uploading
    expect(isFinal(2026, jan1 + 15 * 86_400_000)).toBe(true);
});

test('a closed year is never recomputed', async () => {
    setSystemTime(new Date('2027-02-02T00:00:00Z'));
    const env = fakeEnv(configured);
    const t = await login(env);
    const sent = stubPostHog();
    await env.DB.prepare('INSERT INTO wrapped_cache (user_id, year, body, computed_at) SELECT id, 2026, ?, ?  FROM users').bind(JSON.stringify({ plays: 4 }), Date.UTC(2027, 1, 1)).run();
    expect((await (await get(env, t, '?year=2026')).json() as any).plays).toBe(4);
    expect(sent.length).toBe(0);
});

test('a December snapshot refreshes after rollover and retains January if it disappears', async () => {
    setSystemTime(new Date('2026-12-31T23:00:00Z'));
    const env = fakeEnv(configured);
    const token = await login(env);
    const january = ['2026-01-01', 120000, 2, 0];
    const december = ['2026-12-31', 180000, 3, 0];
    stubPostHog({ days: [january] });
    expect((await (await get(env, token)).json() as any).plays).toBe(2);

    setSystemTime(new Date('2027-01-01T00:30:00Z'));
    stubPostHog({ days: [january, december] });
    const closed = await (await get(env, token, '?year=2026')).json() as any;
    expect(closed.plays).toBe(5);
    expect(closed.coverage).toBeUndefined();

    setSystemTime(new Date('2027-01-16T00:00:00Z'));
    // The higher total includes late December reports but is missing the previously observed January day.
    stubPostHog({ days: [['2026-12-31', 600000, 10, 0]] });
    expect(await (await get(env, token, '?year=2026')).json()).toEqual(closed);

    setSystemTime(new Date('2027-02-01T00:00:00Z'));
    const sent = stubPostHog();
    expect(await (await get(env, token, '?year=2026')).json()).toEqual(closed);
    expect(sent).toHaveLength(0);
});

test('an old year without a snapshot does not present expired data as a year in review', async () => {
    setSystemTime(new Date('2027-02-01T00:00:00Z'));
    const env = fakeEnv(configured);
    const token = await login(env);
    const sent = stubPostHog();
    expect((await get(env, token, '?year=2026')).status).toBe(410);
    expect(sent).toHaveLength(0);
});

test('a rate limit stops the query sequence and honors Retry-After', async () => {
    const now = new Date('2026-12-14T00:00:00Z');
    setSystemTime(now);
    const env = fakeEnv({ ...configured, POSTHOG_PROJECT_ID: 'throttled' });
    const token = await login(env);
    let requests = 0;
    globalThis.fetch = (async () => {
        requests++;
        return new Response(null, { status: 429, headers: { 'Retry-After': '15' } });
    }) as typeof fetch;
    expect((await get(env, token)).status).toBe(502);
    expect((await get(env, token)).status).toBe(502);
    expect(requests).toBe(1);

    setSystemTime(new Date(now.getTime() + 15_000));
    const sent = stubPostHog();
    expect((await get(env, token)).status).toBe(200);
    expect(sent).toHaveLength(6);
});
