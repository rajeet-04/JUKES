CREATE TABLE crashes (
    fingerprint  TEXT PRIMARY KEY,
    issue        INTEGER,
    count        INTEGER NOT NULL,
    first_seen   INTEGER NOT NULL,
    last_seen    INTEGER NOT NULL,
    last_version TEXT NOT NULL
);
CREATE INDEX crashes_first_seen ON crashes (first_seen);
