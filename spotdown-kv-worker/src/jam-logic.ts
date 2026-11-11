// Pure room logic for a Jam (no Durable Object types), so it can be unit-tested and mirrored by the app.

export const MAX_QUEUE = 200;

export interface JamTrack { spotifyId: string; title: string; artist: string; durationSec: number; thumb?: string }
export interface JamItem extends JamTrack { id: string; addedBy: string }
/** What is playing, as an anchor: the position at `anchorServerMs`. While playing, "now" is anchorPosMs + (serverNow - anchorServerMs). */
export interface Playback { itemId: string; playing: boolean; anchorPosMs: number; anchorServerMs: number }
export interface RoomState { rev: number; queue: JamItem[]; lastActive: number; playback?: Playback; hostId?: string }

export type JamOp =
    | { t: 'add'; id: string; track: JamTrack; afterId?: string | null }
    | { t: 'addMany'; items: { id: string; track: JamTrack }[]; afterId?: string | null; label?: 'Radio' }
    | { t: 'remove'; id: string }
    | { t: 'removeMany'; ids: string[] }
    | { t: 'move'; id: string; beforeId?: string | null };

const ID_RE = /^[0-9A-Za-z_-]{8,40}$/;
const SPOTIFY_RE = /^[0-9A-Za-z]{10,32}$/;

const str = (v: unknown, max: number): string | null => typeof v === 'string' && v.length > 0 ? v.slice(0, max) : null;

export function cleanTrack(raw: any): JamTrack | null {
    const title = str(raw?.title, 200);
    const artist = str(raw?.artist, 200);
    if (!title || !artist || typeof raw?.spotifyId !== 'string' || !SPOTIFY_RE.test(raw.spotifyId)) return null;
    const dur = Number(raw.durationSec);
    const thumb = typeof raw.thumb === 'string' && /^https?:\/\//.test(raw.thumb) && raw.thumb.length <= 512 ? raw.thumb : undefined;
    return { spotifyId: raw.spotifyId, title, artist, durationSec: Number.isFinite(dur) && dur >= 0 && dur < 36000 ? Math.floor(dur) : 0, ...(thumb && { thumb }) };
}

/** Validates an op from the wire; null if it is not a well-formed add/remove/move. */
export function parseOp(raw: any): JamOp | null {
    const ref = (v: unknown) => (typeof v === 'string' && ID_RE.test(v) ? v : null);
    if (raw?.t === 'addMany') { // a whole album or playlist in one op: one write, one rev
        if (!Array.isArray(raw.items)) return null;
        const seen = new Set<string>();
        const items: { id: string; track: JamTrack }[] = [];
        for (const r of raw.items) {
            const track = cleanTrack(r?.track);
            if (items.length >= MAX_QUEUE || typeof r?.id !== 'string' || !ID_RE.test(r.id) || seen.has(r.id) || !track) continue;
            seen.add(r.id);
            items.push({ id: r.id, track });
        }
        return items.length ? { t: 'addMany', items, afterId: ref(raw.afterId), ...(raw.label === 'Radio' && { label: 'Radio' as const }) } : null;
    }
    if (raw?.t === 'removeMany') {
        const ids = Array.isArray(raw.ids) ? raw.ids.filter((v: unknown): v is string => typeof v === 'string' && ID_RE.test(v)).slice(0, MAX_QUEUE) : [];
        return ids.length ? { t: 'removeMany', ids } : null;
    }
    if (!raw || typeof raw.id !== 'string' || !ID_RE.test(raw.id)) return null;
    switch (raw.t) {
        case 'add': {
            const track = cleanTrack(raw.track);
            return track ? { t: 'add', id: raw.id, track, afterId: ref(raw.afterId) } : null;
        }
        case 'remove': return { t: 'remove', id: raw.id };
        case 'move': return { t: 'move', id: raw.id, beforeId: ref(raw.beforeId) };
        default: return null;
    }
}

/**
 * Applies one op. Idempotent: re-adding an existing id, removing a missing one, or a move that changes
 * nothing leaves the state (and rev) untouched, so a retry after a dropped socket can't duplicate a song.
 */
export function applyOp(state: RoomState, op: JamOp, by: string): { state: RoomState; changed: boolean; error?: 'queue_full' } {
    const same = { state, changed: false };
    const bump = (queue: JamItem[]): { state: RoomState; changed: boolean } =>
        ({ state: { ...state, rev: state.rev + 1, queue }, changed: true });
    const q = state.queue;
    switch (op.t) {
        case 'add': {
            if (q.some(i => i.id === op.id)) return same;
            if (q.length >= MAX_QUEUE) return { ...same, error: 'queue_full' };
            const item: JamItem = { ...op.track, id: op.id, addedBy: by };
            const at = op.afterId ? q.findIndex(i => i.id === op.afterId) : -1;
            return bump(at < 0 ? [...q, item] : [...q.slice(0, at + 1), item, ...q.slice(at + 1)]);
        }
        case 'addMany': {
            const fresh = op.items.filter(i => !q.some(x => x.id === i.id));
            if (fresh.length === 0) return same;
            const room = MAX_QUEUE - q.length;
            if (room <= 0) return { ...same, error: 'queue_full' };
            const who = op.label === 'Radio' ? 'Radio' : by; // the host's radio top-up is shown as the radio's, not the host's
            const added: JamItem[] = fresh.slice(0, room).map(i => ({ ...i.track, id: i.id, addedBy: who })); // as many as fit
            const at = op.afterId ? q.findIndex(i => i.id === op.afterId) : -1;
            return bump(at < 0 ? [...q, ...added] : [...q.slice(0, at + 1), ...added, ...q.slice(at + 1)]);
        }
        case 'removeMany': {
            const gone = new Set(op.ids);
            return q.some(i => gone.has(i.id)) ? bump(q.filter(i => !gone.has(i.id))) : same;
        }
        case 'remove':
            return q.some(i => i.id === op.id) ? bump(q.filter(i => i.id !== op.id)) : same;
        case 'move': {
            const from = q.findIndex(i => i.id === op.id);
            if (from < 0) return same;
            const rest = q.filter(i => i.id !== op.id);
            const before = op.beforeId ? rest.findIndex(i => i.id === op.beforeId) : -1;
            const next = before < 0 ? [...rest, q[from]] : [...rest.slice(0, before), q[from], ...rest.slice(before)];
            return next.every((item, i) => item.id === q[i].id) ? same : bump(next);
        }
    }
}

/** The host's starting queue (current song + upcoming), accepted once on a fresh room. Bad entries are skipped. */
export function parseSeed(raw: any, by: string): JamItem[] {
    const items: JamItem[] = [];
    const seen = new Set<string>();
    if (!Array.isArray(raw?.items)) return items;
    for (const r of raw.items) {
        if (items.length >= MAX_QUEUE) break;
        const track = cleanTrack(r?.track);
        if (typeof r?.id !== 'string' || !ID_RE.test(r.id) || seen.has(r.id) || !track) continue;
        seen.add(r.id);
        items.push({ ...track, id: r.id, addedBy: r.radio === true ? 'Radio' : by }); // the host's radio songs are radio songs in the room too
    }
    return items;
}

/** What follows `id` in the queue, or null if it is last (or not there). */
export function nextAfter(queue: JamItem[], id: string): JamItem | null {
    const i = queue.findIndex(item => item.id === id);
    return i >= 0 ? queue[i + 1] ?? null : null;
}

/** Where playback stands at `now`. */
export function positionAt(pb: Playback, now: number): number {
    return pb.playing ? pb.anchorPosMs + (now - pb.anchorServerMs) : pb.anchorPosMs;
}

/** Playback moves on from `from` to `next`, or stops (rewound) when there is nothing next. */
export function advance(from: Playback, next: JamItem | null, now: number): Playback {
    return next
        ? { itemId: next.id, playing: true, anchorPosMs: 0, anchorServerMs: now }
        : { itemId: from.itemId, playing: false, anchorPosMs: 0, anchorServerMs: now };
}

/** Radio-tagged songs queued after [currentId]: what a new song or a Mix replaces. Songs people added by hand stay. */
export function radioAfter(queue: JamItem[], currentId: string | undefined): string[] {
    const at = currentId ? queue.findIndex(i => i.id === currentId) : -1;
    return at < 0 ? [] : queue.slice(at + 1).filter(i => i.addedBy === 'Radio').map(i => i.id);
}
