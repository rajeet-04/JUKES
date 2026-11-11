// Two clients in one Jam. Usage: BASE=http://localhost:8799 SECRET=test bun test/jam-smoke.ts
const BASE = process.env.BASE ?? 'http://localhost:8799';
const SECRET = process.env.SECRET ?? 'test';

const track = (n: number) => ({ spotifyId: 'a'.repeat(21) + n, title: `Song ${n}`, artist: 'Artist', durationSec: 180 });

function client(code: string, clientId: string, name: string) {
    const ws = new WebSocket(`${BASE.replace('http', 'ws')}/jam/${code}/ws`);
    const inbox: any[] = [];
    const waiters: (() => void)[] = [];
    ws.onmessage = e => { inbox.push(JSON.parse(String(e.data))); waiters.splice(0).forEach(w => w()); };
    const next = async (pred: (m: any) => boolean, ms = 3000) => {
        const end = Date.now() + ms;
        for (;;) {
            const i = inbox.findIndex(pred);
            if (i >= 0) return inbox.splice(i, 1)[0];
            if (Date.now() > end) throw new Error(`timeout; inbox=${JSON.stringify(inbox)}`);
            await new Promise<void>(r => { waiters.push(r); setTimeout(r, 100); });
        }
    };
    const opened = new Promise<void>((res, rej) => { ws.onopen = () => res(); ws.onerror = () => rej(new Error('ws error')); });
    return { ws, next, send: (m: object) => ws.send(JSON.stringify(m)), hello: async () => { await opened; ws.send(JSON.stringify({ t: 'hello', clientId, name })); return next(m => m.t === 'welcome'); } };
}

const check = (cond: boolean, msg: string) => { if (!cond) { console.error('FAIL:', msg); process.exit(1); } console.log('ok  ', msg); };

const created = await fetch(`${BASE}/jam`, { method: 'POST', headers: { 'X-App-Secret': SECRET } });
check(created.status === 200, 'create jam');
const { code } = await created.json() as { code: string };
check(/^[A-HJKMNP-Z2-9]{6}$/.test(code), `code looks right (${code})`);
check((await fetch(`${BASE}/jam`, { method: 'POST' })).status === 401, 'create without secret is rejected');
check((await (await fetch(`${BASE}/jam/${code}`)).json() as any).exists === true, 'info says the room exists');
check((await fetch(`${BASE}/j/${code}`)).status === 200, 'landing page for a live jam');
check((await fetch(`${BASE}/j/ZZZZZZ`)).status === 404, 'landing page for an unknown jam is 404');

const a = client(code, 'client-aaaa1', 'Ann');
const w = await a.hello();
check(w.state.rev === 0 && w.state.queue.length === 0, 'Ann joins an empty queue');
const b = client(code, 'client-bbbb2', 'Bob');
await b.hello();
const members = await a.next(m => m.t === 'members' && m.members.length === 2);
check(members.members.length === 2, 'Ann is told Bob joined');

a.send({ t: 'add', id: 'item-0001', track: track(1) });
const seenByB = await b.next(m => m.t === 'add');
check(seenByB.rev === 1 && seenByB.by === 'Ann' && seenByB.id === 'item-0001', 'Bob sees Ann\'s add with rev 1');
await a.next(m => m.t === 'add'); // the sender hears its own op as the ack

a.send({ t: 'add', id: 'item-0001', track: track(1) });
b.send({ t: 'add', id: 'item-0002', track: track(2) });
const second = await a.next(m => m.t === 'add');
check(second.id === 'item-0002' && second.rev === 2, 'duplicate add is ignored; next add gets rev 2');

b.send({ t: 'move', id: 'item-0002', beforeId: 'item-0001' });
const moved = await a.next(m => m.t === 'move');
check(moved.rev === 3, 'move is broadcast with rev 3');
a.send({ t: 'remove', id: 'item-0001' });
await b.next(m => m.t === 'remove' && m.rev === 4);
check(true, 'remove is broadcast with rev 4');

b.ws.close();
const late = client(code, 'client-cccc3', 'Cat');
const snap = await late.hello();
check(snap.state.rev === 4 && snap.state.queue.map((i: any) => i.id).join() === 'item-0002', 'a late joiner gets the current snapshot');

a.send({ t: 'add', id: 'bad', track: track(3) });
check((await a.next(m => m.t === 'error')).code === 'bad_op', 'malformed op gets an error');

a.send({ t: 'ping', t0: 123 });
const pong = await a.next(m => m.t === 'pong');
check(pong.t0 === 123 && Math.abs(pong.serverTime - Date.now()) < 5000, 'ping/pong returns server time');

// playback anchor (spike)
a.send({ t: 'play', itemId: 'item-0002' });
const pbPlay = await late.next(m => m.t === 'pb');
check(pbPlay.playback.playing && pbPlay.playback.itemId === 'item-0002' && pbPlay.playback.anchorPosMs === 0, 'play starts the item at 0');
await a.next(m => m.t === 'pb'); // the sender's own echo
await new Promise(r => setTimeout(r, 400));
late.send({ t: 'pause' });
const pbPause = await a.next(m => m.t === 'pb');
check(!pbPause.playback.playing && pbPause.playback.anchorPosMs >= 300 && pbPause.playback.anchorPosMs < 2000, `pause freezes the position (${pbPause.playback.anchorPosMs} ms)`);
a.send({ t: 'seek', posMs: 60000 });
const pbSeek = await late.next(m => m.t === 'pb' && m.playback.anchorPosMs === 60000);
check(!pbSeek.playback.playing, 'seek keeps the paused state');
a.send({ t: 'play', itemId: 'not-in-queue' });
a.send({ t: 'play' });
const pbResume = await late.next(m => m.t === 'pb' && m.playback.playing);
check(pbResume.playback.itemId === 'item-0002' && pbResume.playback.anchorPosMs === 60000, 'play with no item resumes where it was');
const joiner = client(code, 'client-dddd4444', 'Dee');
const w2 = await joiner.hello();
check(w2.playback?.itemId === 'item-0002' && w2.playback.playing, 'a joiner receives the current playback');

// host role, seed, song end, handoff, pause-when-empty
const code2 = ((await (await fetch(`${BASE}/jam`, { method: 'POST', headers: { 'X-App-Secret': SECRET } })).json()) as { code: string }).code;
const h = client(code2, 'client-host0001', 'Hana');
const hw = await h.hello();
check(hw.hostId === 'client-host0001', 'the first member is host');
const g = client(code2, 'client-gest0002', 'Gus');
const gw = await g.hello();
check(gw.hostId === 'client-host0001', 'a joiner sees the host');

const seedItems = [1, 2, 3].map(n => ({ id: `seed-item-000${n}`, track: { ...track(n), durationSec: 2 } }));
g.send({ t: 'seed', items: seedItems, current: 0 });
check((await g.next(m => m.t === 'error')).code === 'seed_rejected', 'a non-host cannot seed');
h.send({ t: 'seed', items: seedItems, current: 1, posMs: 1500, playing: true });
const snap2 = await g.next(m => m.t === 'snapshot');
check(snap2.state.queue.length === 3 && snap2.playback.itemId === 'seed-item-0002' && snap2.playback.playing && snap2.playback.anchorPosMs === 1500, 'host seeds queue and current song for everyone');
await h.next(m => m.t === 'snapshot');
h.send({ t: 'seed', items: seedItems, current: 0 });
check((await h.next(m => m.t === 'error')).code === 'seed_rejected', 'a second seed is refused');

g.send({ t: 'ended', itemId: 'seed-item-0001' });
g.send({ t: 'ended', itemId: 'seed-item-0002' });
await new Promise(r => setTimeout(r, 600));
g.send({ t: 'ended', itemId: 'seed-item-0002' }); // 2 s song, ~2.1 s in: counts as over (slack)
const adv = await h.next(m => m.t === 'pb');
check(adv.playback.itemId === 'seed-item-0003' && adv.playback.anchorPosMs === 0 && adv.playback.playing, 'ended moves everyone on to the next song');
h.send({ t: 'ended', itemId: 'seed-item-0002' });
g.send({ t: 'remove', id: 'seed-item-0003' });
const rm = await h.next(m => m.t === 'pb' && m.playback === null);
check(rm.playback === null, 'removing the last playing song clears playback');

h.ws.close();
const handoff = await g.next(m => m.t === 'members' && m.hostId === 'client-gest0002');
check(handoff.members.length === 1, 'host leaves: the next member becomes host');
g.send({ t: 'add', id: 'late-item-0001', track: track(4) });
g.send({ t: 'play', itemId: 'late-item-0001' });
await g.next(m => m.t === 'pb' && m.playback.playing);
g.ws.close();
await new Promise(r => setTimeout(r, 800));
const back = client(code2, 'client-back0003', 'Bea');
const bw = await back.hello();
check(bw.hostId === 'client-back0003' && bw.playback && bw.playback.playing === false, 'empty room paused; the next joiner becomes host');

// addMany: one op, one rev, ordered after an item
const code3 = ((await (await fetch(`${BASE}/jam`, { method: 'POST', headers: { 'X-App-Secret': SECRET } })).json()) as { code: string }).code;
const p1 = client(code3, 'client-pppp0001', 'Pia');
await p1.hello();
const p2 = client(code3, 'client-pppp0002', 'Pat');
await p2.hello();
p1.send({ t: 'add', id: 'first-item-01', track: track(1) });
await p2.next(m => m.t === 'add');
p1.send({ t: 'addMany', afterId: 'first-item-01', items: [2, 3, 4].map(n => ({ id: `many-item-0${n}`, track: track(n) })) });
const many = await p2.next(m => m.t === 'addMany');
check(many.rev === 2 && many.items.length === 3 && many.by === 'Pia', 'addMany arrives as one op with one rev');
const check2 = client(code3, 'client-pppp0003', 'Pip');
const ws3 = await check2.hello();
check(ws3.state.queue.map((i: any) => i.id).join() === 'first-item-01,many-item-02,many-item-03,many-item-04', 'the run is stored in order after the item');

// new song and Mix replace the radio songs after the current one; songs added by hand stay
const code4 = ((await (await fetch(`${BASE}/jam`, { method: 'POST', headers: { 'X-App-Secret': SECRET } })).json()) as { code: string }).code;
const r1 = client(code4, 'client-rrrr0001', 'Rex');
await r1.hello();
const r2 = client(code4, 'client-rrrr0002', 'Rio');
await r2.hello();
const radioOp = (id: string) => ({ t: 'addMany', label: 'Radio', items: [{ id, track: track(5) }] });
r1.send({ t: 'add', id: 'user-song-0001', track: track(1) });
r1.send(radioOp('radio-song-001'));
r1.send({ t: 'add', id: 'user-song-0002', track: track(2) });
r1.send(radioOp('radio-song-002'));
r1.send({ t: 'play', itemId: 'user-song-0001' });
for (let i = 0; i < 4; i++) await r2.next(m => m.t === 'add' || m.t === 'addMany');
await r2.next(m => m.t === 'pb');
const radioMsg = (await r2.next(m => m.t === 'addMany' && m.by === 'Radio' || false, 200).catch(() => null));
r1.send({ t: 'play', itemId: 'user-song-0001', regen: true });
const regen = await r2.next(m => m.t === 'removeMany');
check(regen.ids.join() === 'radio-song-001,radio-song-002' && regen.by !== undefined, 'a new song removes the radio songs after it');
await r2.next(m => m.t === 'pb');
r1.send(radioOp('radio-song-003'));
await r2.next(m => m.t === 'addMany');
r1.send({ t: 'mix' });
const mixed = await r2.next(m => m.t === 'mix');
check(mixed.t === 'mix', 'Mix is announced to everyone');
const mixRemoved = await r2.next(m => m.t === 'removeMany');
check(mixRemoved.ids.join() === 'radio-song-003', 'Mix removes the radio songs after the current one');
const last = client(code4, 'client-rrrr0003', 'Ray');
const lw = await last.hello();
check(lw.state.queue.map((i: any) => i.id).join() === 'user-song-0001,user-song-0002', 'songs added by hand are kept');

console.log('all good');
process.exit(0);
