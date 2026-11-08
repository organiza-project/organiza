-- Organiza local schema v9. See docs/schema.md and ADR-0024.
-- The Linux desktop keeps its own record of the app in front, since Linux has no usage log.
-- A row is a change: the app now in front, or no app when nothing is (locked, idle, app closed).

CREATE TABLE focus_change (
    at_ms   INTEGER NOT NULL,
    package TEXT
) STRICT;

CREATE INDEX focus_change_at ON focus_change (at_ms);

PRAGMA user_version = 9;
