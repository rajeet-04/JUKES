import { expect, test } from 'bun:test';
import { applyOp, cleanTrack, MAX_QUEUE, parseOp, type JamOp, type RoomState } from './jam-logic';

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
