import { expect, test } from 'bun:test';
import { scrub } from './report';

test('scrub hides tokens, keys in urls and emails', () => {
    expect(scrub('Authorization: Bearer abcdefghijklmnop12345')).not.toContain('abcdefghijklmnop12345');
    expect(scrub('GET /x?access_token=abc123secret&a=1')).toBe('GET /x?access_token=[redacted]&a=1');
    expect(scrub('mail me at a.b@example.com')).toBe('mail me at [email]');
    expect(scrub('plain line with nothing secret')).toBe('plain line with nothing secret');
});
