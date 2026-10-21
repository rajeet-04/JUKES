CREATE TABLE shares (
    id         TEXT PRIMARY KEY,
    body       TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    touched_at INTEGER NOT NULL,
    expires_at INTEGER NOT NULL
);
