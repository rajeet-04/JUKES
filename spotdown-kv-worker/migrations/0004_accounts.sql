CREATE TABLE users (
    id           TEXT PRIMARY KEY,
    email        TEXT NOT NULL UNIQUE,
    salt         TEXT NOT NULL,
    hash         TEXT NOT NULL,
    hand         TEXT NOT NULL,
    created_at   INTEGER NOT NULL,
    fails        INTEGER NOT NULL DEFAULT 0,
    locked_until INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE sessions (
    token_hash TEXT PRIMARY KEY,
    user_id    TEXT NOT NULL,
    device     TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    seen_at    INTEGER NOT NULL
);
CREATE INDEX sessions_user ON sessions (user_id);
CREATE TABLE libraries (
    user_id    TEXT PRIMARY KEY,
    rev        INTEGER NOT NULL,
    body       TEXT NOT NULL,
    updated_at INTEGER NOT NULL
);
