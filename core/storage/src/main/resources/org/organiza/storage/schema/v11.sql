-- Organiza local schema v11. See docs/schema.md and ADR-0030.
-- Notes: a journal entry or a reminder, typed or dictated. Nothing here leaves the device unless
-- the person asks the AI about one note.

CREATE TABLE note (
    id            TEXT PRIMARY KEY,
    body          TEXT NOT NULL CHECK (length(body) > 0),
    subject       TEXT CHECK (subject IS NULL OR length(subject) BETWEEN 1 AND 40),
    created_at_ms INTEGER NOT NULL,
    remind_at_ms  INTEGER
) STRICT;

CREATE INDEX note_created ON note (created_at_ms);

PRAGMA user_version = 11;
