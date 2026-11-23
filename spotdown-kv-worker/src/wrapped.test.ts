import { expect, test } from 'bun:test';
import { lit } from './posthog';
import { filterFor, longestStreak, queries, shape, weekday } from './wrapped';

test('an email with quotes or backslashes cannot end the HogQL string', () => {
    expect(lit("o'brien@x.com")).toBe("'o\\'brien@x.com'");
    expect(lit('a\\b@x.com')).toBe("'a\\\\b@x.com'");
    expect(filterFor("x'; DROP TABLE events; --@y.com", 2026)).toContain("distinct_id = 'x\\'; DROP TABLE events; --@y.com'");
});

test('every query reads only that account, that event, and that year', () => {
    const q = queries('me@x.com', 2026);
    for (const sql of Object.values(q)) {
        expect(sql).toContain("event = 'daily_listening_report'");
        expect(sql).toContain("distinct_id = 'me@x.com'");
        expect(sql).toContain("properties.date >= '2026-01-01'");
        expect(sql).toContain("properties.date <= '2026-12-31'");
    }
});

test('longest streak counts consecutive days, across month ends', () => {
    expect(longestStreak([])).toBe(0);
    expect(longestStreak(['2026-12-01'])).toBe(1);
    expect(longestStreak(['2026-01-30', '2026-01-31', '2026-02-01', '2026-02-03', '2026-02-04'])).toBe(3);
    expect(longestStreak(['2026-12-30', '2026-12-31'])).toBe(2);
});

test('weekday is Monday-first from the local date', () => {
    expect(weekday('2026-11-23')).toBe(0); // a Monday
    expect(weekday('2026-11-29')).toBe(6); // a Sunday
});

test('shape folds the query results into the numbers', () => {
    const w = shape(2026, 1, {
        days: [['2026-12-01', 600000, 10, 2], ['2026-12-02', 1200000, 20, 4], ['2026-12-04', 60000, 1, 0]],
        small: [
            [JSON.stringify([...new Array(22).fill(0), 600000, 0]), JSON.stringify({ playlist: 500000, radio: 100000 })],
            [JSON.stringify([...new Array(22).fill(0), 1200000, 60000]), JSON.stringify({ playlist: 1000000 })],
        ],
        songs: [['x - a', 'x', 'a', 'sp1', 'al1', 1800000, 25]],
        artists: [['ar1', 1800000]],
        albums: [['al1', 1800000]],
        distinct: [[7]],
    });
    expect(w.plays).toBe(31);
    expect(w.skips).toBe(6);
    expect(w.skip_rate).toBe(0.194);
    expect(w.minutes).toBe(31);
    expect(w.active_days).toBe(3);
    expect(w.longest_streak).toBe(2);
    expect(w.first_day).toBe('2026-12-01');
    expect(w.last_day).toBe('2026-12-04');
    expect(w.distinct_songs).toBe(7);
    expect(w.by_hour_minutes[22]).toBe(30);
    expect(w.by_hour_minutes[23]).toBe(1);
    expect(w.by_source_minutes).toEqual({ playlist: 25, radio: 2 });
    expect(w.by_weekday_minutes.reduce((a, b) => a + b, 0)).toBe(31);
    expect(w.top_songs[0]).toMatchObject({ title: 'x', minutes: 30, plays: 25 });
    expect(w.top_artists[0]).toEqual({ id: 'ar1', name: null, minutes: 30 });
});

test('shape of an empty year is all zeros, not an error', () => {
    const w = shape(2026, 1, { days: [], small: [], songs: [], artists: [], albums: [], distinct: [[0]] });
    expect(w.plays).toBe(0);
    expect(w.skip_rate).toBe(0);
    expect(w.first_day).toBeNull();
    expect(w.longest_streak).toBe(0);
});
