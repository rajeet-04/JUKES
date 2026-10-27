import { expect, test } from 'bun:test';
import { fingerprint, signature } from './crash';

const trace = (line: number, version = '') => [
    'java.lang.RuntimeException: Unable to start service: boom 42' + version,
    `\tat android.app.ActivityThread.handleServiceArgs(ActivityThread.java:${line})`,
    `\tat com.example.juke.services.PlaybackService$foregroundGuard$1.run(PlaybackService.kt:${line + 3})`,
    'Caused by: java.lang.IllegalStateException: no player',
    `\tat com.example.juke.services.PlaybackService.onStartCommand(PlaybackService.kt:${line})`,
].join('\n');

test('the same crash in another build gets the same fingerprint', async () => {
    expect(await fingerprint(trace(100))).toBe(await fingerprint(trace(250, ' v2')));
});

test('a different crash gets a different fingerprint', async () => {
    expect(await fingerprint(trace(100))).not.toBe(await fingerprint(trace(100).replace('onStartCommand', 'onCreate')));
});

test('signature keeps exception types and frames, not messages or line numbers', () => {
    const sig = signature(trace(100));
    expect(sig).toContain('java.lang.IllegalStateException');
    expect(sig).toContain('PlaybackService$foregroundGuard$.run');
    expect(sig).not.toContain('boom');
    expect(sig).not.toContain('100');
});
