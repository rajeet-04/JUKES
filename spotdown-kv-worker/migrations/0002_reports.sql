CREATE TABLE reports (
    id         TEXT PRIMARY KEY,
    install_id TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    issue      INTEGER,
    logs       TEXT NOT NULL
);
CREATE INDEX reports_install ON reports (install_id, created_at);
