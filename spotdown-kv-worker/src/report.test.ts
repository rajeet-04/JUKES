import { expect, test } from 'bun:test';
import { scrub } from './report';

test('scrub hides tokens, keys in urls and emails', () => {
    expect(scrub('Authorization: Bearer abcdefghijklmnop12345')).not.toContain('abcdefghijklmnop12345');
    expect(scrub('GET /x?access_token=abc123secret&a=1')).toBe('GET /x?access_token=[redacted]&a=1');
    expect(scrub('mail me at a.b@example.com')).toBe('mail me at [email]');
    expect(scrub('plain line with nothing secret')).toBe('plain line with nothing secret');
});

// createReport with a stub DB and a captured GitHub call
async function file(payload: object) {
    const issues: any[] = [];
    const inserts: any[][] = [];
    const realFetch = globalThis.fetch;
    globalThis.fetch = (async (_url: any, init: any) => {
        issues.push(JSON.parse(init.body));
        return Response.json({ number: 7 });
    }) as any;
    const db = { prepare: (sql: string) => ({
        bind: (...args: any[]) => ({
            first: async () => ({ n: 0 }),
            run: async () => { if (sql.startsWith('INSERT')) inserts.push(args); },
        }),
    }) };
    try {
        const { createReport } = await import('./report');
        const res = await createReport(new Request('https://x/report', { method: 'POST', headers: { 'X-App-Secret': 's' }, body: JSON.stringify(payload) }), { APP_SECRET: 's', GITHUB_TOKEN: 't', DB: db } as any);
        return { res, issues, inserts };
    } finally { globalThis.fetch = realFetch; }
}

test('a feature request is titled and labelled as one and keeps no logs', async () => {
    const { res, issues, inserts } = await file({ installId: 'abcdef123456', description: 'Add a sleep timer', app: { version: '1' }, logs: 'secret log', kind: 'feature' });
    expect(await res.json()).toEqual({ issue: 7 });
    expect(issues[0].title).toBe('[Feature] Add a sleep timer');
    expect(issues[0].labels).toEqual(['feature-request']);
    expect(issues[0].body).not.toContain('### Jam');
    expect(inserts[0][4]).toBe(''); // logs column
});

test('anything else is still a bug report with its logs', async () => {
    const { issues, inserts } = await file({ installId: 'abcdef123456', description: 'It crashed', app: {}, logs: 'line one' });
    expect(issues[0].title).toBe('[Report] It crashed');
    expect(issues[0].labels).toEqual(['user-report']);
    expect(inserts[0][4]).toBe('line one');
});
