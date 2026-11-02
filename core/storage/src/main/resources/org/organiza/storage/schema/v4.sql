-- Organiza local schema v4. See docs/schema.md and ADR-0013.
-- SQLite cannot change a CHECK constraint in place, so both tables are rebuilt and their rows copied.
-- Nothing references either table, so they can be replaced with foreign keys on.

CREATE TABLE rule_target_v4 (
    rule_id TEXT NOT NULL REFERENCES rule (id) ON DELETE CASCADE,
    kind    TEXT NOT NULL CHECK (kind IN ('package', 'domain', 'process')),
    value   TEXT NOT NULL,
    PRIMARY KEY (rule_id, kind, value)
) STRICT, WITHOUT ROWID;
INSERT INTO rule_target_v4 (rule_id, kind, value) SELECT rule_id, kind, value FROM rule_target;
DROP TABLE rule_target;
ALTER TABLE rule_target_v4 RENAME TO rule_target;

CREATE TABLE tamper_event_v4 (
    id         INTEGER PRIMARY KEY,
    session_id TEXT REFERENCES session (id) ON DELETE SET NULL,
    capability TEXT NOT NULL CHECK (capability IN ('ACCESSIBILITY', 'OVERLAY', 'VPN', 'DEVICE_ADMIN', 'USAGE_STATS', 'DAEMON')),
    at_ms      INTEGER NOT NULL
) STRICT;
INSERT INTO tamper_event_v4 (id, session_id, capability, at_ms) SELECT id, session_id, capability, at_ms FROM tamper_event;
DROP TABLE tamper_event;
ALTER TABLE tamper_event_v4 RENAME TO tamper_event;

PRAGMA user_version = 4;
