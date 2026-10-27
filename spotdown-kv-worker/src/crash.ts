import type { Env } from './index';
import { clip, fileIssue, github, scrub, table } from './report';

const NEW_ISSUES_PER_DAY = 20;
const MAX_BODY = 32 * 1024;
const MAX_TRACE = 16 * 1024;
const FRAMES = 6;

interface CrashIn {
    installId?: string;
    type?: string;
    message?: string;
    trace?: string;
    thread?: string;
    at?: number;
    app?: Record<string, unknown>;
}

/**
 * The same crash from every version and device: exception types plus the top frames of each "Caused by",
 * without line numbers (they move between builds) or lambda/synthetic counters.
 */
export function signature(trace: string): string {
    const parts: string[] = [];
    let frames = 0;
    for (const raw of trace.split('\n')) {
        const line = raw.trim();
        if (line.startsWith('at ')) {
            if (frames++ < FRAMES) parts.push(line.replace(/\(.*\)$/, '').replace(/\$\d+/g, '$'));
        } else if (parts.length === 0 || line.startsWith('Caused by: ')) {
            parts.push(line.replace(/^Caused by: /, '').split(':')[0]);
            frames = 0;
        }
    }
    return parts.join('\n');
}

export async function fingerprint(trace: string): Promise<string> {
    const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(signature(trace)));
    return Array.from(new Uint8Array(digest).slice(0, 12), b => b.toString(16).padStart(2, '0')).join('');
}

// One issue per distinct crash; repeats only bump the count, plus a comment the first time a new version hits it.
export async function createCrash(request: Request, env: Env): Promise<Response> {
    if (request.headers.get('X-App-Secret') !== env.APP_SECRET) return Response.json({ error: 'Unauthorized' }, { status: 401 });
    const raw = await request.text();
    if (raw.length > MAX_BODY) return Response.json({ error: 'Too large' }, { status: 413 });
    let body: CrashIn;
    try { body = JSON.parse(raw); } catch { return Response.json({ error: 'Bad JSON' }, { status: 400 }); }
    const trace = scrub(clip(body.trace, MAX_TRACE));
    const type = clip(body.type, 200);
    if (!type || !/\n\s*at /.test(trace)) return Response.json({ error: 'Invalid crash' }, { status: 400 });
    if (!env.GITHUB_TOKEN) return Response.json({ error: 'Crash reports are not set up' }, { status: 503 });

    const fp = await fingerprint(trace);
    const now = Date.now();
    const version = clip(body.app?.version, 40);
    const existing = await env.DB.prepare('SELECT issue, last_version FROM crashes WHERE fingerprint = ?').bind(fp).first<{ issue: number | null; last_version: string }>();
    if (existing) {
        await env.DB.prepare('UPDATE crashes SET count = count + 1, last_seen = ?, last_version = ? WHERE fingerprint = ?').bind(now, version, fp).run();
        if (existing.issue && version !== existing.last_version)
            await github(env, `issues/${existing.issue}/comments`, { body: `Seen again in **${version}**\n\n${table(body.app)}` });
        return Response.json({ issue: existing.issue, fingerprint: fp });
    }

    const today = await env.DB.prepare('SELECT COUNT(*) AS n FROM crashes WHERE first_seen > ?').bind(now - 86400_000).first<{ n: number }>();
    if ((today?.n ?? 0) >= NEW_ISSUES_PER_DAY) return Response.json({ error: 'Too many new crashes today' }, { status: 429 });
    // Claim the fingerprint first so two devices sending the same new crash at once file one issue.
    const claimed = await env.DB.prepare('INSERT OR IGNORE INTO crashes (fingerprint, issue, count, first_seen, last_seen, last_version) VALUES (?, NULL, 1, ?, ?, ?)')
        .bind(fp, now, now, version).run();
    if (!claimed.meta.changes) return Response.json({ issue: null, fingerprint: fp });

    const message = scrub(clip(body.message, 300)).replace(/\s+/g, ' ');
    const title = `[Crash] ${type.split('.').pop()}${message ? `: ${message}` : ''}`.slice(0, 120);
    const issueBody = [
        `\`${type}\` on thread \`${clip(body.thread, 60).replace(/`/g, '')}\``,
        '',
        '```',
        trace.replace(/```/g, "'''"),
        '```',
        '',
        '### App',
        table(body.app),
        '',
        `Crash \`${fp}\` (install \`${clip(body.installId, 8)}\`). Later occurrences are counted in D1: ` +
            `\`bunx wrangler d1 execute jukes-shares --remote --command "SELECT count, last_seen, last_version FROM crashes WHERE fingerprint='${fp}'"\``,
    ].join('\n');
    const issue = await fileIssue(env, title, issueBody, 'crash');
    if (issue === null) {
        await env.DB.prepare('DELETE FROM crashes WHERE fingerprint = ?').bind(fp).run(); // let the next report try again
        return Response.json({ error: 'Could not file the crash' }, { status: 502 });
    }
    await env.DB.prepare('UPDATE crashes SET issue = ? WHERE fingerprint = ?').bind(issue, fp).run();
    return Response.json({ issue, fingerprint: fp });
}
