-- Organiza local schema v2. See docs/schema.md and ADR-0008.

ALTER TABLE rule ADD COLUMN block_short_form INTEGER NOT NULL DEFAULT 0 CHECK (block_short_form IN (0, 1));

-- What the OS had granted at the last check, so tampering during a process kill is noticed.
ALTER TABLE session ADD COLUMN granted_capabilities TEXT;

CREATE TABLE tamper_event (
    id         INTEGER PRIMARY KEY,
    session_id TEXT REFERENCES session (id) ON DELETE SET NULL,
    capability TEXT NOT NULL CHECK (capability IN ('ACCESSIBILITY', 'OVERLAY', 'VPN', 'DEVICE_ADMIN', 'USAGE_STATS')),
    at_ms      INTEGER NOT NULL
) STRICT;

PRAGMA user_version = 2;
