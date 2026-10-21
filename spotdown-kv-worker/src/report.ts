import type { Env } from './index';

const GITHUB_REPO = 'rajeet-04/JUKES';
const PER_INSTALL_PER_DAY = 5;
const PER_DAY = 100;
const MAX_LOG_BYTES = 200 * 1024;
const MAX_DESC = 4000;
export const REPORT_KEEP_MS = 30 * 86400 * 1000;

interface ReportIn {
    installId?: string;
    description?: string;
    app?: Record<string, unknown>;
    jam?: Record<string, unknown> | null;
    logs?: string;
}

// The app scrubs first; this is a second pass so a missed pattern never lands in a public issue or the log store.
export const scrub = (s: string) => s
    .replace(/(authorization|bearer|token|secret|api[_-]?key|password|key)(["'\s:=]+)[A-Za-z0-9._~+/=-]{12,}/gi, '$1$2[redacted]')
    .replace(/([?&](?:access_token|token|key|secret|code)=)[^&\s"']+/gi, '$1[redacted]')
    .replace(/[\w.+-]+@[\w-]+\.[\w.-]+/g, '[email]');

const clip = (v: unknown, n: number) => String(v ?? '').slice(0, n);
const id = () => Array.from(crypto.getRandomValues(new Uint8Array(8)), b => b.toString(16).padStart(2, '0')).join('');

function table(obj: Record<string, unknown> | null | undefined): string {
    const rows = Object.entries(obj ?? {}).slice(0, 25).map(([k, v]) => `| ${clip(k, 40).replace(/\|/g, '')} | ${clip(scrub(String(v)), 120).replace(/[|\n]/g, ' ')} |`);
    return rows.length ? `| | |\n|---|---|\n${rows.join('\n')}` : '_none_';
}

export async function createReport(request: Request, env: Env): Promise<Response> {
    if (request.headers.get('X-App-Secret') !== env.APP_SECRET) return Response.json({ error: 'Unauthorized' }, { status: 401 });
    const raw = await request.text();
    if (raw.length > MAX_LOG_BYTES + 32 * 1024) return Response.json({ error: 'Too large' }, { status: 413 });
    let body: ReportIn;
    try { body = JSON.parse(raw); } catch { return Response.json({ error: 'Bad JSON' }, { status: 400 }); }
    const description = scrub(clip(body.description, MAX_DESC)).trim();
    const installId = clip(body.installId, 64);
    if (!description || !installId) return Response.json({ error: 'Invalid report' }, { status: 400 });
    if (!env.GITHUB_TOKEN) return Response.json({ error: 'Reports are not set up' }, { status: 503 });

    const now = Date.now();
    const since = now - 86400_000;
    const mine = await env.DB.prepare('SELECT COUNT(*) AS n FROM reports WHERE install_id = ? AND created_at > ?').bind(installId, since).first<{ n: number }>();
    const all = await env.DB.prepare('SELECT COUNT(*) AS n FROM reports WHERE created_at > ?').bind(since).first<{ n: number }>();
    if ((mine?.n ?? 0) >= PER_INSTALL_PER_DAY || (all?.n ?? 0) >= PER_DAY) return Response.json({ error: 'Too many reports today' }, { status: 429 });

    const reportId = id();
    const logs = scrub(clip(body.logs, MAX_LOG_BYTES));
    const title = `[Report] ${description.split('\n')[0].slice(0, 80)}`;
    const issueBody = [
        description,
        '',
        '### App',
        table(body.app),
        '',
        '### Jam',
        table(body.jam),
        '',
        `Report \`${reportId}\` (install \`${installId.slice(0, 8)}\`). Logs are stored privately: ` +
            `\`bunx wrangler d1 execute jukes-shares --remote --command "SELECT logs FROM reports WHERE id='${reportId}'"\``,
    ].join('\n');

    const gh = (payload: object) => fetch(`https://api.github.com/repos/${GITHUB_REPO}/issues`, {
        method: 'POST',
        headers: {
            Authorization: `Bearer ${env.GITHUB_TOKEN}`,
            Accept: 'application/vnd.github+json',
            'User-Agent': 'jukes-reports',
            'X-GitHub-Api-Version': '2022-11-28',
        },
        body: JSON.stringify(payload),
    });
    let res = await gh({ title, body: issueBody, labels: ['user-report'] });
    if (res.status === 422) res = await gh({ title, body: issueBody }); // label couldn't be applied
    if (!res.ok) return Response.json({ error: 'Could not file the report' }, { status: 502 });
    const issue = ((await res.json()) as { number: number }).number;

    await env.DB.prepare('INSERT INTO reports (id, install_id, created_at, issue, logs) VALUES (?, ?, ?, ?, ?)')
        .bind(reportId, installId, now, issue, logs).run();
    return Response.json({ issue });
}
