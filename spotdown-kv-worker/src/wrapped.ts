import type { Env } from './index';
import { authed } from './account';
import { hogql, lit, posthogReady } from './posthog';
import { spotifyToken } from './spotify';

/**
 * Wrapped: the year in listening for one account, read back from the daily_listening_report events the app
 * posts straight to PostHog. The aggregation runs inside PostHog (HogQL), because a free-plan Worker has about
 * 10 ms of CPU and a year of reports is far more JSON than that. Names and genres come from Spotify by id.
 * One row per account and year is cached in D1; a closed year never changes, so it is never recomputed.
 */

const EVENT = 'daily_listening_report';
const MIN_YEAR = 2026;
const FRESH_MS = 6 * 3600_000;
const EMPTY_FRESH_MS = 3600_000;
/** Days of a closed year still arrive after it ends (the app uploads a day only once it is over, and on its next open). */
const YEAR_SETTLE_MS = 14 * 86_400_000;

/** A past year's result is final only if it was computed after late uploads had a chance to land. */
export const isFinal = (year: number, computedAt: number) => computedAt >= Date.UTC(year + 1, 0, 1) + YEAR_SETTLE_MS;

/**
 * Every query reads only this account's reports for the year, by the local date each report carries.
 * It matches the person, not just the email: reports uploaded anonymously before the login were merged into
 * the account's person and keep their old distinct id.
 */
export function filterFor(email: string, year: number): string {
    return `event = '${EVENT}'
        AND person_id IN (SELECT person_id FROM events WHERE distinct_id = ${lit(email)}
            AND timestamp >= toDateTime('${MIN_YEAR}-01-01 00:00:00')
            AND timestamp < toDateTime('${year + 1}-01-01 00:00:00') + INTERVAL 2 DAY)
        AND timestamp >= toDateTime('${year}-01-01 00:00:00') - INTERVAL 2 DAY
        AND timestamp < toDateTime('${year + 1}-01-01 00:00:00') + INTERVAL 2 DAY
        AND properties.date >= '${year}-01-01' AND properties.date <= '${year}-12-31'`;
}

const tracksOf = (f: string) => `(SELECT arrayJoin(JSONExtractArrayRaw(properties, 'tracks')) AS t FROM events WHERE ${f})`;

export const queries = (email: string, year: number) => {
    const f = filterFor(email, year);
    return {
        days: `SELECT properties.date AS day, sum(toFloat(properties.total_listened_ms)) AS ms,
                 sum(toInt(properties.plays)) AS plays, sum(toInt(properties.skips)) AS skips
               FROM events WHERE ${f} GROUP BY day ORDER BY day LIMIT 400`,
        // Summed in PostHog over every report (reports are per device and per late batch, so their number is unbounded).
        // Rows are [kind, key, ms]: kind 'h' is an hour of day 0-23, kind 's' a listening source.
        small: `SELECT kind, k, sum(v) AS v FROM (
                  SELECT 'h' AS kind, toString(tupleElement(p, 1)) AS k, tupleElement(p, 2) AS v FROM (
                    SELECT arrayJoin(arrayMap(i -> tuple(i, arrayElement(JSONExtract(properties, 'listened_ms_by_hour', 'Array(Float64)'), i + 1)), range(24))) AS p
                    FROM events WHERE ${f}
                  )
                  UNION ALL
                  SELECT 's', tupleElement(p, 1), tupleElement(p, 2) FROM (
                    SELECT arrayJoin(JSONExtractKeysAndValues(properties, 'listened_ms_by_source', 'Float64')) AS p
                    FROM events WHERE ${f}
                  )
                ) GROUP BY kind, k LIMIT 100`,
        songs: `SELECT song_key, any(t_title) AS title, any(t_artist) AS artist, any(t_spotify) AS spotify_id,
                  any(t_album) AS album_id, sum(ms) AS ms, sum(plays) AS plays, sum(skips) AS skips
                FROM (
                  SELECT JSONExtractString(t, 'song_key') AS song_key, JSONExtractString(t, 'title') AS t_title,
                    JSONExtractString(t, 'artist') AS t_artist, JSONExtractString(t, 'spotify_id') AS t_spotify,
                    JSONExtractString(t, 'album_spotify_id') AS t_album, JSONExtractFloat(t, 'listened_ms') AS ms,
                    JSONExtractInt(t, 'plays') AS plays, JSONExtractInt(t, 'skips') AS skips
                  FROM ${tracksOf(f)}
                ) GROUP BY song_key ORDER BY ms DESC LIMIT 5`,
        artists: `SELECT aid, sum(ms) AS ms FROM (
                    SELECT arrayJoin(JSONExtract(t, 'artist_spotify_ids', 'Array(String)')) AS aid,
                      JSONExtractFloat(t, 'listened_ms') AS ms
                    FROM ${tracksOf(f)}
                  ) WHERE aid != '' GROUP BY aid ORDER BY ms DESC LIMIT 10`,
        albums: `SELECT album, sum(ms) AS ms FROM (
                   SELECT JSONExtractString(t, 'album_spotify_id') AS album, JSONExtractFloat(t, 'listened_ms') AS ms
                   FROM ${tracksOf(f)}
                 ) WHERE album != '' GROUP BY album ORDER BY ms DESC LIMIT 5`,
        distinct: `SELECT uniq(JSONExtractString(t, 'song_key')) AS songs FROM ${tracksOf(f)}`,
    };
};

const num = (v: unknown) => { const n = Number(v); return Number.isFinite(n) ? n : 0; };
const minutes = (ms: number) => Math.round(ms / 60000);

/** Longest run of consecutive calendar days in a sorted list of yyyy-mm-dd dates. */
export function longestStreak(dates: string[]): number {
    let best = 0, run = 0, prev = NaN;
    for (const d of dates) {
        const t = Date.parse(d + 'T00:00:00Z');
        if (Number.isNaN(t)) continue;
        run = t - prev === 86400_000 ? run + 1 : 1;
        prev = t;
        best = Math.max(best, run);
    }
    return best;
}

/** Monday = 0 ... Sunday = 6, for a local yyyy-mm-dd date. */
export const weekday = (date: string) => (new Date(date + 'T12:00:00Z').getUTCDay() + 6) % 7;


export interface Ranked { id?: string; name: string | null; minutes: number }
export interface Wrapped {
    year: number; generated_at: number;
    plays: number; skips: number; skip_rate: number; minutes: number; active_days: number; longest_streak: number;
    first_day: string | null; last_day: string | null; distinct_songs: number;
    top_songs: { song_key: string; title: string; artist: string; spotify_id: string; album_id: string; minutes: number; plays: number }[];
    top_artists: Ranked[]; top_albums: Ranked[]; top_genres: { genre: string; minutes: number }[];
    by_hour_minutes: number[]; by_weekday_minutes: number[]; by_source_minutes: Record<string, number>;
}

/** The query results folded into the numbers; names and genres are added afterwards. */
export function shape(year: number, now: number, r: {
    days: unknown[][]; small: unknown[][]; songs: unknown[][]; artists: unknown[][]; albums: unknown[][]; distinct: unknown[][];
}): Wrapped {
    const byWeekday = new Array(7).fill(0), byHour = new Array(24).fill(0), bySource: Record<string, number> = {};
    let plays = 0, skips = 0, ms = 0;
    const dates: string[] = [];
    for (const [day, m, p, s] of r.days) {
        const d = String(day);
        dates.push(d);
        ms += num(m); plays += num(p); skips += num(s);
        byWeekday[weekday(d)] += num(m);
    }
    for (const [kind, key, v] of r.small) {
        if (kind === 'h') { const i = num(key); if (i >= 0 && i < 24) byHour[i] += num(v); }
        else if (kind === 's') bySource[String(key)] = (bySource[String(key)] ?? 0) + num(v);
    }
    return {
        year, generated_at: now, plays, skips, skip_rate: plays ? Math.round((skips / plays) * 1000) / 1000 : 0,
        minutes: minutes(ms), active_days: dates.length, longest_streak: longestStreak(dates),
        first_day: dates[0] ?? null, last_day: dates[dates.length - 1] ?? null, distinct_songs: num(r.distinct[0]?.[0]),
        top_songs: r.songs.map(([song_key, title, artist, spotify_id, album_id, m, p]) => ({
            song_key: String(song_key), title: String(title), artist: String(artist),
            spotify_id: String(spotify_id), album_id: String(album_id), minutes: minutes(num(m)), plays: num(p),
        })),
        top_artists: r.artists.map(([id, m]) => ({ id: String(id), name: null, minutes: minutes(num(m)) })),
        top_albums: r.albums.map(([id, m]) => ({ id: String(id), name: null, minutes: minutes(num(m)) })),
        top_genres: [],
        by_hour_minutes: byHour.map(minutes), by_weekday_minutes: byWeekday.map(minutes),
        by_source_minutes: Object.fromEntries(Object.entries(bySource).map(([k, v]) => [k, minutes(v)])),
    };
}

/** Artist and album names, and the genres of the top artists weighted by listening, from Spotify. Quietly skipped on failure. */
async function addNames(env: Env, w: Wrapped): Promise<void> {
    let tok: Awaited<ReturnType<typeof spotifyToken>> = null;
    try { tok = await spotifyToken(env); } catch { return; } // the names are optional: a failed token fetch must not fail the route
    if (!tok) return;
    const get = async <T>(path: string): Promise<T | null> => {
        try {
            const res = await fetch('https://api.spotify.com/v1' + path, { headers: { Authorization: 'Bearer ' + tok.token } });
            return res.ok ? await res.json<T>() : null;
        } catch { return null; }
    };
    const artistIds = w.top_artists.map(a => a.id).filter(Boolean) as string[];
    const albumIds = w.top_albums.map(a => a.id).filter(Boolean) as string[];
    const [artists, albums] = await Promise.all([
        artistIds.length ? get<{ artists: ({ id: string; name: string; genres?: string[] } | null)[] }>(`/artists?ids=${artistIds.join(',')}`) : null,
        albumIds.length ? get<{ albums: ({ id: string; name: string } | null)[] }>(`/albums?ids=${albumIds.join(',')}`) : null,
    ]);
    const genreMinutes: Record<string, number> = {};
    for (const a of artists?.artists ?? []) {
        const row = a && w.top_artists.find(x => x.id === a.id);
        if (!a || !row) continue;
        row.name = a.name;
        for (const g of a.genres ?? []) genreMinutes[g] = (genreMinutes[g] ?? 0) + row.minutes;
    }
    for (const a of albums?.albums ?? []) {
        const row = a && w.top_albums.find(x => x.id === a.id);
        if (a && row) row.name = a.name;
    }
    w.top_genres = Object.entries(genreMinutes).sort((a, b) => b[1] - a[1]).slice(0, 5).map(([genre, m]) => ({ genre, minutes: m }));
    w.top_artists = w.top_artists.slice(0, 5);
}

async function compute(env: Env, email: string, year: number): Promise<Wrapped | null> {
    const q = queries(email, year);
    // One at a time: PostHog limits how many queries a personal key may run at once, and this is cached.
    const run: (unknown[][] | null)[] = [];
    for (const sql of [q.days, q.small, q.songs, q.artists, q.albums, q.distinct]) run.push(await hogql(env, sql));
    const [days, small, songs, artists, albums, distinct] = run;
    if (!days || !small || !songs || !artists || !albums || !distinct) return null;
    const w = shape(year, Date.now(), { days, small, songs, artists, albums, distinct });
    if (w.plays) await addNames(env, w);
    return w;
}

const err = (error: string, status: number) => Response.json({ error }, { status });

export async function wrapped(request: Request, env: Env): Promise<Response> {
    if (request.headers.get('X-App-Secret') !== env.APP_SECRET) return err('Unauthorized', 401);
    const me = await authed(request, env);
    if (!me) return err('Logged out. Log in again.', 401);
    if (!posthogReady(env)) return err('Wrapped is not set up yet', 503);

    const thisYear = new Date().getUTCFullYear();
    const asked = new URL(request.url).searchParams.get('year');
    const year = asked === null ? thisYear : Number(asked);
    if (!Number.isInteger(year) || year < MIN_YEAR || year > thisYear) return err('Bad year', 400);

    const user = await env.DB.prepare('SELECT email FROM users WHERE id = ?').bind(me.userId).first<{ email: string }>();
    if (!user) return err('Logged out. Log in again.', 401);

    const row = await env.DB.prepare('SELECT body, computed_at FROM wrapped_cache WHERE user_id = ? AND year = ?')
        .bind(me.userId, year).first<{ body: string; computed_at: number }>();
    const cached = row ? JSON.parse(row.body) as Wrapped : null;
    const age = row ? Date.now() - row.computed_at : Infinity;
    const fresh = row && (year < thisYear && isFinal(year, row.computed_at) ? true : age < (cached?.plays ? FRESH_MS : EMPTY_FRESH_MS));
    if (cached && fresh) return Response.json(cached);

    const w = await compute(env, user.email, year);
    if (!w) return cached ? Response.json(cached) : err('Could not read your listening right now. Try again later.', 502);
    await env.DB.prepare('INSERT INTO wrapped_cache (user_id, year, body, computed_at) VALUES (?, ?, ?, ?) ON CONFLICT (user_id, year) DO UPDATE SET body = excluded.body, computed_at = excluded.computed_at')
        .bind(me.userId, year, JSON.stringify(w), Date.now()).run();
    return Response.json(w);
}
