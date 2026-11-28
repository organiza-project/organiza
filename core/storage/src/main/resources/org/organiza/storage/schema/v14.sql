-- Organiza local schema v14. See docs/schema.md and ADR-0040.
-- Calendar events: a title at a time, with an optional reminder before it. Nothing here leaves the device.

CREATE TABLE calendar_event (
    id            TEXT PRIMARY KEY,
    title         TEXT NOT NULL CHECK (length(title) BETWEEN 1 AND 120),
    start_ms      INTEGER NOT NULL,
    minutes       INTEGER NOT NULL CHECK (minutes BETWEEN 1 AND 1440),
    remind_before INTEGER CHECK (remind_before IS NULL OR remind_before BETWEEN 0 AND 1440)
) STRICT;

CREATE INDEX calendar_event_start ON calendar_event (start_ms);

PRAGMA user_version = 14;
