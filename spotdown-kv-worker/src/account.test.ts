import { expect, test } from 'bun:test';
import { Database } from 'bun:sqlite';
import { readFileSync } from 'fs';
import { account } from './account';

// D1 and KV stand-ins: enough of their API for account.ts
function fakeEnv() {
    const db = new Database(':memory:');
    db.exec(readFileSync(new URL('../migrations/0004_accounts.sql', import.meta.url), 'utf8'));
    const stmt = (sql: string, args: unknown[] = []) => ({
        bind: (...a: unknown[]) => stmt(sql, a),
        first: async () => db.query(sql).get(...(args as any[])) ?? null,
        run: async () => ({ meta: { changes: db.query(sql).run(...(args as any[])).changes } }),
    });
    const kv = new Map<string, string>();
    return {
        APP_SECRET: 's',
        DB: { prepare: (sql: string) => stmt(sql), batch: async (list: any[]) => { for (const s of list) await s.run(); } },
        KV: { get: async (k: string) => kv.get(k) ?? null, put: async (k: string, v: string) => void kv.set(k, v), delete: async (k: string) => void kv.delete(k) },
    } as any;
}

const K1 = '1'.repeat(64), K2 = '2'.repeat(64);
const call = async (env: any, action: string, body?: unknown, token?: string, method = 'POST') => {
    const headers: Record<string, string> = { 'X-App-Secret': 's', 'Content-Type': 'application/json' };
    if (token) headers.Authorization = `Bearer ${token}`;
    const res = await account(new Request(`https://x/account/${action}`, { method, headers, body: body === undefined ? undefined : typeof body === 'string' ? body : JSON.stringify(body) }), env, action.split('?')[0]);
    return { status: res.status, json: await res.json() as any };
};

test('register, login, library, password change, logout, delete', async () => {
    const env = fakeEnv();
    const reg = await call(env, 'register', { email: ' A@b.co ', key: K1, device: 'Mac' });
    expect(reg.status).toBe(200);
    expect(reg.json.email).toBe('a@b.co');
    expect(reg.json.hand).toMatch(/^[A-Z2-9]{6}$/);
    expect((await call(env, 'register', { email: 'a@b.co', key: K1 })).status).toBe(409);
    expect((await call(env, 'login', { email: 'a@b.co', key: K2 })).status).toBe(401);
    expect((await call(env, 'login', { email: 'nobody@b.co', key: K1 })).status).toBe(401);

    const t = (await call(env, 'login', { email: 'a@b.co', key: K1, device: 'Pixel' })).json.token;
    expect((await call(env, 'library', undefined, t, 'GET')).json).toEqual({ rev: 0, doc: null });
    expect((await call(env, 'library?rev=0', '{"v":1}', t, 'PUT')).json).toEqual({ rev: 1 });
    expect((await call(env, 'library?rev=0', '{"v":2}', t, 'PUT')).status).toBe(409); // someone else saved first
    expect((await call(env, 'library?rev=1', '{"v":3,"tracks":[1]}', t, 'PUT')).json).toEqual({ rev: 2 });
    expect((await call(env, 'library?rev=1', '{"v":4}', t, 'PUT')).status).toBe(409);
    expect((await call(env, 'library', undefined, t, 'GET')).json).toEqual({ rev: 2, doc: { v: 3, tracks: [1] } });
    expect((await call(env, 'library', undefined, undefined, 'GET')).status).toBe(401);

    expect((await call(env, 'password', { key: K2, newKey: K1 }, t)).status).toBe(401);
    expect((await call(env, 'password', { key: K1, newKey: K2 }, t)).status).toBe(200);
    expect((await call(env, 'login', { email: 'a@b.co', key: K1 })).status).toBe(401);
    expect((await call(env, 'login', { email: 'a@b.co', key: K2 })).status).toBe(200);

    expect((await call(env, 'logout', undefined, t)).status).toBe(200);
    expect((await call(env, 'library', undefined, t, 'GET')).status).toBe(401);

    const t2 = (await call(env, 'login', { email: 'a@b.co', key: K2 })).json.token;
    expect((await call(env, 'delete', { key: K2 }, t2)).status).toBe(200);
    expect((await call(env, 'login', { email: 'a@b.co', key: K2 })).status).toBe(401);
    expect((await call(env, 'register', { email: 'a@b.co', key: K1 })).status).toBe(200);
});

test('locks the account after 10 wrong passwords', async () => {
    const env = fakeEnv();
    await call(env, 'register', { email: 'a@b.co', key: K1 });
    for (let i = 0; i < 10; i++) await call(env, 'login', { email: 'a@b.co', key: K2 });
    expect((await call(env, 'login', { email: 'a@b.co', key: K1 })).status).toBe(429);
});

test('rejects bad input and a missing app secret', async () => {
    const env = fakeEnv();
    expect((await call(env, 'register', { email: 'nope', key: K1 })).status).toBe(400);
    expect((await call(env, 'register', { email: 'a@b.co', key: 'short' })).status).toBe(400);
    const res = await account(new Request('https://x/account/login', { method: 'POST', body: '{}' }), env, 'login');
    expect(res.status).toBe(401);
});
