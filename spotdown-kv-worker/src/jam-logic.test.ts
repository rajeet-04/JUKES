import { expect, test } from 'bun:test';
import { advance, applyOp, cleanTrack, MAX_QUEUE, nextAfter, parseOp, parseSeed, positionAt, radioAfter, type JamOp, type RoomState } from './jam-logic';

const track = (n: number) => ({ spotifyId: 'a'.repeat(21) + (n % 10), title: `t${n}`, artist: 'x', durationSec: 100 });
const empty = (): RoomState => ({ rev: 0, queue: [], lastActive: 0 });
const add = (id: string, afterId?: string): JamOp => ({ t: 'add', id, track: track(1), afterId });
const ids = (s: RoomState) => s.queue.map(i => i.id);

test('add appends, or inserts after a given item', () => {
    let s = applyOp(empty(), add('item-0001'), 'ann').state;
    s = applyOp(s, add('item-0002'), 'bob').state;
    s = applyOp(s, add('item-0003', 'item-0001'), 'ann').state;
    expect(ids(s)).toEqual(['item-0001', 'item-0003', 'item-0002']);
    expect(s.rev).toBe(3);
    expect(s.queue[1].addedBy).toBe('ann');
});

test('a repeated add is a no-op and does not bump rev', () => {
    const s = applyOp(empty(), add('item-0001'), 'ann').state;
    const again = applyOp(s, add('item-0001'), 'ann');
    expect(again.changed).toBe(false);
    expect(again.state.rev).toBe(1);
});

test('remove drops the item; removing a missing one is a no-op', () => {
    let s = applyOp(empty(), add('item-0001'), 'a').state;
    s = applyOp(s, add('item-0002'), 'a').state;
    s = applyOp(s, { t: 'remove', id: 'item-0001' }, 'a').state;
    expect(ids(s)).toEqual(['item-0002']);
    expect(applyOp(s, { t: 'remove', id: 'item-9999' }, 'a').changed).toBe(false);
});

test('move places an item before another, or at the end', () => {
    let s = empty();
    for (const id of ['item-0001', 'item-0002', 'item-0003']) s = applyOp(s, add(id), 'a').state;
    s = applyOp(s, { t: 'move', id: 'item-0003', beforeId: 'item-0001' }, 'a').state;
    expect(ids(s)).toEqual(['item-0003', 'item-0001', 'item-0002']);
    s = applyOp(s, { t: 'move', id: 'item-0003' }, 'a').state;
    expect(ids(s)).toEqual(['item-0001', 'item-0002', 'item-0003']);
    expect(applyOp(s, { t: 'move', id: 'item-0003' }, 'a').changed).toBe(false);
});

test('queue is capped', () => {
    let s = empty();
    for (let i = 0; i < MAX_QUEUE; i++) s = applyOp(s, add(`item-${String(i).padStart(4, '0')}`), 'a').state;
    const over = applyOp(s, add('item-extra01'), 'a');
    expect(over.error).toBe('queue_full');
    expect(over.state.queue.length).toBe(MAX_QUEUE);
});

test('parseOp rejects malformed ops and cleanTrack bad tracks', () => {
    expect(parseOp({ t: 'add', id: 'short', track: track(1) })).toBeNull();
    expect(parseOp({ t: 'add', id: 'item-0001', track: { title: 'x' } })).toBeNull();
    expect(parseOp({ t: 'nuke', id: 'item-0001' })).toBeNull();
    expect(parseOp({ t: 'remove', id: 'item-0001' })).toEqual({ t: 'remove', id: 'item-0001' });
    expect(cleanTrack({ ...track(1), thumb: 'file:///x.jpg' })?.thumb).toBeUndefined();
    expect(cleanTrack({ ...track(1), thumb: 'https://i.scdn.co/x.jpg' })?.thumb).toBe('https://i.scdn.co/x.jpg');
});

test('seed keeps good entries in order and drops bad or repeated ones', () => {
    const items = parseSeed({ items: [
        { id: 'seed-0001', track: track(1) },
        { id: 'seed-0001', track: track(2) },
        { id: 'bad', track: track(3) },
        { id: 'seed-0004', track: { title: 'no id' } },
        { id: 'seed-0005', track: track(5) },
    ] }, 'ann');
    expect(items.map(i => i.id)).toEqual(['seed-0001', 'seed-0005']);
    expect(items[0].addedBy).toBe('ann');
    expect(parseSeed({}, 'ann')).toEqual([]);
    const many = Array.from({ length: MAX_QUEUE + 20 }, (_, i) => ({ id: `seed-${String(i).padStart(4, '0')}`, track: track(i) }));
    expect(parseSeed({ items: many }, 'ann').length).toBe(MAX_QUEUE);
});

test('nextAfter, positionAt and advance', () => {
    let s = empty();
    for (const id of ['item-0001', 'item-0002']) s = applyOp(s, add(id), 'a').state;
    expect(nextAfter(s.queue, 'item-0001')?.id).toBe('item-0002');
    expect(nextAfter(s.queue, 'item-0002')).toBeNull();
    expect(nextAfter(s.queue, 'missing')).toBeNull();
    const pb = { itemId: 'item-0001', playing: true, anchorPosMs: 1000, anchorServerMs: 5000 };
    expect(positionAt(pb, 7500)).toBe(3500);
    expect(positionAt({ ...pb, playing: false }, 7500)).toBe(1000);
    expect(advance(pb, s.queue[1], 9000)).toEqual({ itemId: 'item-0002', playing: true, anchorPosMs: 0, anchorServerMs: 9000 });
    expect(advance({ ...pb, itemId: 'item-0002' }, null, 9000)).toEqual({ itemId: 'item-0002', playing: false, anchorPosMs: 0, anchorServerMs: 9000 });
});

test('addMany inserts a run after an item, in order, as one change', () => {
    let s = empty();
    for (const id of ['item-0001', 'item-0002']) s = applyOp(s, add(id), 'a').state;
    const op = parseOp({ t: 'addMany', afterId: 'item-0001', items: [
        { id: 'many-0001', track: track(1) }, { id: 'many-0002', track: track(2) }, { id: 'many-0001', track: track(3) }, { id: 'bad', track: track(4) },
    ] })!;
    const r = applyOp(s, op, 'bob');
    expect(r.changed).toBe(true);
    expect(ids(r.state)).toEqual(['item-0001', 'many-0001', 'many-0002', 'item-0002']);
    expect(r.state.rev).toBe(s.rev + 1);
    expect(applyOp(r.state, op, 'bob').changed).toBe(false); // a retry adds nothing
    expect(parseOp({ t: 'addMany', items: [] })).toBeNull();
});

test('addMany adds as many as fit when the queue is nearly full', () => {
    let s = empty();
    for (let i = 0; i < MAX_QUEUE - 1; i++) s = applyOp(s, add(`item-${String(i).padStart(4, '0')}`), 'a').state;
    const op = parseOp({ t: 'addMany', items: [{ id: 'many-0001', track: track(1) }, { id: 'many-0002', track: track(2) }] })!;
    const r = applyOp(s, op, 'a');
    expect(r.state.queue.length).toBe(MAX_QUEUE);
    expect(applyOp(r.state, parseOp({ t: 'addMany', items: [{ id: 'many-0009', track: track(1) }] })!, 'a').error).toBe('queue_full');
});

test('addMany labelled Radio is credited to the radio, not the sender', () => {
    const op = parseOp({ t: 'addMany', label: 'Radio', items: [{ id: 'radio-item-01', track: track(1) }] })!;
    expect(applyOp(empty(), op, 'host').state.queue[0].addedBy).toBe('Radio');
    const other = parseOp({ t: 'addMany', label: 'Someone', items: [{ id: 'radio-item-02', track: track(1) }] })!;
    expect(applyOp(empty(), other, 'host').state.queue[0].addedBy).toBe('host');
});

test('removeMany drops the listed songs in one change', () => {
    let s = empty();
    for (const id of ['item-0001', 'item-0002', 'item-0003']) s = applyOp(s, add(id), 'a').state;
    const r = applyOp(s, parseOp({ t: 'removeMany', ids: ['item-0001', 'item-0003', 'missing-id-1'] })!, 'a');
    expect(ids(r.state)).toEqual(['item-0002']);
    expect(r.state.rev).toBe(s.rev + 1);
    expect(applyOp(r.state, parseOp({ t: 'removeMany', ids: ['item-0001'] })!, 'a').changed).toBe(false);
    expect(parseOp({ t: 'removeMany', ids: [] })).toBeNull();
});

test('radioAfter finds only the radio songs after the current one', () => {
    let s = empty();
    const radio = (id: string) => parseOp({ t: 'addMany', label: 'Radio', items: [{ id, track: track(1) }] })!;
    s = applyOp(s, radio('radio-before-1'), 'host').state;
    s = applyOp(s, add('item-current-1'), 'ann').state;
    s = applyOp(s, radio('radio-after-01'), 'host').state;
    s = applyOp(s, add('item-after-001'), 'bob').state;
    s = applyOp(s, radio('radio-after-02'), 'host').state;
    expect(radioAfter(s.queue, 'item-current-1')).toEqual(['radio-after-01', 'radio-after-02']);
    expect(radioAfter(s.queue, undefined)).toEqual([]);
    expect(radioAfter(s.queue, 'not-there')).toEqual([]);
});

test('seed songs marked as radio are credited to the radio, so a new song or Mix can replace them', () => {
    const items = parseSeed({ items: [
        { id: 'seed-hand-001', track: track(1) },
        { id: 'seed-radio-01', track: track(2), radio: true },
        { id: 'seed-radio-02', track: track(3), radio: 'yes' },
    ] }, 'ann');
    expect(items.map(i => i.addedBy)).toEqual(['ann', 'Radio', 'ann']);
    expect(radioAfter(items, 'seed-hand-001')).toEqual(['seed-radio-01']);
});
