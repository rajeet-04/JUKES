// Run against a local Wrangler instance with the account migrations applied.
import assert from 'node:assert/strict';

const base = 'http://127.0.0.1:' + (process.env.PORT ?? '8801');
const credentials = { email: crypto.randomUUID() + '@example.test', key: 'a'.repeat(64) };

async function device(id: string, action: string) {
    const response = await fetch(base + '/account/' + action, {
        method: 'POST', headers: { 'X-App-Secret': 'test' }, body: JSON.stringify(credentials),
    });
    assert.equal(response.status, 200);
    const { token } = await response.json();
    const ws = new WebSocket(base.replace('http:', 'ws:') + '/connect/ws', {
        headers: { Authorization: 'Bearer ' + token, 'X-App-Secret': 'test' },
    });
    const messages: any[] = [];
    const send = (message: object) => ws.send(JSON.stringify(message));
    ws.onmessage = event => messages.push(JSON.parse(event.data));
    ws.onopen = () => send({ t: 'hello', deviceId: id, name: id });
    async function next(type: string) {
        const until = Date.now() + 5000;
        while (Date.now() < until) {
            const index = messages.findIndex(message => message.t === type);
            if (index >= 0) return messages.splice(index, 1)[0];
            await Bun.sleep(10);
        }
        throw Error('Timed out waiting for ' + type);
    }
    await next('welcome');
    return { ws, send, next };
}

const source = await device('source-device', 'register');
const target = await device('target-device', 'login');
try {
    const track = { title: 'Paused song', artist: 'Artist' };
    source.send({ t: 'state', playing: false, track, queue: [track], index: 0, posMs: 17000 });
    const snapshot = await target.next('state');
    await Bun.sleep(80);
    source.send({ t: 'cmd', op: 'take', target: 'target-device' });
    const handoff = await target.next('take');
    assert.equal(handoff.state.posMs, 17000);
    assert.equal(handoff.state.playing, true);
    assert.ok(handoff.state.at > snapshot.state.at);
    source.send({ t: 'synced' });
    await target.next('synced');
    console.log('Passed signed-in handoff and library-sync relay');
} finally {
    source.ws.close();
    target.ws.close();
}
