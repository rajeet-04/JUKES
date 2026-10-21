// Pure room logic for a Jam (no Durable Object types), so it can be unit-tested and mirrored by the app.

export const MAX_QUEUE = 200;

export interface JamTrack { spotifyId: string; title: string; artist: string; durationSec: number; thumb?: string }
export interface JamItem extends JamTrack { id: string; addedBy: string }
export interface RoomState { rev: number; queue: JamItem[]; lastActive: number }

export type JamOp =
    | { t: 'add'; id: string; track: JamTrack; afterId?: string | null }
    | { t: 'remove'; id: string }
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
    if (!raw || typeof raw.id !== 'string' || !ID_RE.test(raw.id)) return null;
    const ref = (v: unknown) => (typeof v === 'string' && ID_RE.test(v) ? v : null);
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
