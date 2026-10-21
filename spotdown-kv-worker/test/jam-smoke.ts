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

console.log('all good');
process.exit(0);
