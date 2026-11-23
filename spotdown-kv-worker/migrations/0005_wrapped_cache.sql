-- One computed Wrapped per account and year, built from PostHog daily_listening_report events
-- and recomputed when computed_at is old enough.
CREATE TABLE wrapped_cache (
    user_id     TEXT NOT NULL,
    year        INTEGER NOT NULL,
    body        TEXT NOT NULL,
    computed_at INTEGER NOT NULL,
    PRIMARY KEY (user_id, year)
);
