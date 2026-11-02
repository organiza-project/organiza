-- Organiza local schema v5. See docs/schema.md and ADR-0016.
-- One row per intervention: whether the person stayed focused or overrode, and why.
-- Screen contents and URLs are never stored; a site is the block-list entry it matched.

CREATE TABLE intervention_event (
    id           INTEGER PRIMARY KEY,
    session_id   TEXT REFERENCES session (id) ON DELETE SET NULL,
    kind         TEXT NOT NULL CHECK (kind IN ('app', 'site', 'short_form', 'guard')),
    app          TEXT NOT NULL,
    site         TEXT CHECK ((kind = 'site') = (site IS NOT NULL)),
    outcome      TEXT NOT NULL CHECK (outcome IN ('stayed', 'overrode')),
    reason       TEXT CHECK (reason IN ('WORK', 'COMMUNICATION', 'ERRAND', 'BOREDOM', 'HABIT', 'OTHER')),
    note         TEXT CHECK (note IS NULL OR length(note) <= 200),
    phrase_id    TEXT,
    pause_s      INTEGER NOT NULL DEFAULT 0 CHECK (pause_s >= 0),
    pass_minutes INTEGER CHECK (pass_minutes IS NULL OR pass_minutes BETWEEN 1 AND 15),
    strict       INTEGER NOT NULL CHECK (strict IN (0, 1)),
    reflected    INTEGER NOT NULL DEFAULT 0 CHECK (reflected IN (0, 1)),
    at_ms        INTEGER NOT NULL,
    CHECK ((outcome = 'overrode') = (reason IS NOT NULL AND pass_minutes IS NOT NULL))
) STRICT;

CREATE INDEX intervention_event_at ON intervention_event (at_ms);

PRAGMA user_version = 5;
