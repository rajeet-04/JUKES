import type { Env } from './index';

/** A HogQL string literal: backslashes and quotes escaped, so an email can never end the string. */
export const lit = (s: string) => `'${s.replace(/\\/g, '\\\\').replace(/'/g, "\\'")}'`;

export const posthogReady = (env: Env) =>
    !!(env.POSTHOG_PERSONAL_API_KEY && env.POSTHOG_PROJECT_ID && env.POSTHOG_API_HOST);

const call = (env: Env, path: string, init: RequestInit) =>
    fetch(`${env.POSTHOG_API_HOST}/api/projects/${env.POSTHOG_PROJECT_ID}${path}`, {
        ...init,
        headers: { Authorization: `Bearer ${env.POSTHOG_PERSONAL_API_KEY}`, 'Content-Type': 'application/json', ...init.headers },
        signal: AbortSignal.timeout(15_000),
    });

const queryBackoff = new Map<string, number>();

/**
 * Runs one HogQL query and returns its rows, or null when PostHog fails.
 * ponytail: PostHog suggests saved Endpoints for backend code; this one function is the only caller of /query/,
 * so switching is a one-function change.
 */
export async function hogql(env: Env, query: string): Promise<unknown[][] | null> {
    const project = `${env.POSTHOG_API_HOST}/${env.POSTHOG_PROJECT_ID}`;
    if ((queryBackoff.get(project) ?? 0) > Date.now()) return null;
    queryBackoff.delete(project);
    try {
        const res = await call(env, '/query/', { method: 'POST', body: JSON.stringify({ query: { kind: 'HogQLQuery', query } }) });
        if (res.status === 429) {
            const value = res.headers.get('Retry-After');
            const now = Date.now();
            const delay = value == null ? NaN : /^\d+$/.test(value) ? Number(value) * 1000 : Date.parse(value) - now;
            const until = now + (Number.isFinite(delay) ? Math.max(1000, delay) : 60_000);
            queryBackoff.set(project, Math.max(queryBackoff.get(project) ?? 0, until));
        }
        if (!res.ok) return null;
        const { results } = await res.json<{ results?: unknown[][] }>();
        return results ?? [];
    } catch { return null; }
}

/** Deletes the person with this distinct id and queues deletion of their events. Best effort, never throws. */
export async function deletePerson(env: Env, email: string): Promise<boolean> {
    if (!posthogReady(env)) return false;
    try {
        const found = await call(env, `/persons/?distinct_id=${encodeURIComponent(email)}`, { method: 'GET' });
        if (!found.ok) return false;
        const { results } = await found.json<{ results?: { id: number | string }[] }>();
        let ok = true;
        for (const p of results ?? []) {
            const res = await call(env, `/persons/${p.id}/?delete_events=true`, { method: 'DELETE' });
            ok = ok && res.ok;
        }
        return ok;
    } catch { return false; }
}
