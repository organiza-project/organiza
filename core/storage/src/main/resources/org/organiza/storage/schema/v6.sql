-- Organiza local schema v6. See docs/schema.md and ADR-0018.
-- Habits gain a frequency, a target per period and an optional reminder time. Daily app limits,
-- a small settings table and received or sent nudges are new. The intervention log gains the
-- 'limit' kind, and SQLite cannot change a CHECK in place, so that table is rebuilt.

ALTER TABLE habit ADD COLUMN frequency TEXT NOT NULL DEFAULT 'daily' CHECK (frequency IN ('daily', 'weekly', 'monthly'));
ALTER TABLE habit ADD COLUMN target INTEGER NOT NULL DEFAULT 1 CHECK (target BETWEEN 1 AND 31);
ALTER TABLE habit ADD COLUMN reminder_minute INTEGER CHECK (reminder_minute IS NULL OR reminder_minute BETWEEN 0 AND 1439);

-- Minutes an app may be in the foreground per local day, outside and inside sessions.
CREATE TABLE usage_limit (
    package TEXT PRIMARY KEY,
    minutes INTEGER NOT NULL CHECK (minutes BETWEEN 5 AND 720)
) STRICT, WITHOUT ROWID;

CREATE TABLE setting (
    key   TEXT PRIMARY KEY,
    value TEXT NOT NULL
) STRICT, WITHOUT ROWID;

-- One nudge per sender, receiver and day. Member IDs are public keys inside the group.
CREATE TABLE arena_nudge (
    challenge_id TEXT NOT NULL REFERENCES challenge (id) ON DELETE CASCADE,
    from_member  TEXT NOT NULL,
    to_member    TEXT NOT NULL,
    local_date   TEXT NOT NULL CHECK (local_date GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]'),
    PRIMARY KEY (challenge_id, from_member, to_member, local_date)
) STRICT, WITHOUT ROWID;

CREATE TABLE intervention_event_v6 (
    id           INTEGER PRIMARY KEY,
    session_id   TEXT REFERENCES session (id) ON DELETE SET NULL,
    kind         TEXT NOT NULL CHECK (kind IN ('app', 'site', 'short_form', 'guard', 'limit')),
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
INSERT INTO intervention_event_v6 SELECT * FROM intervention_event;
DROP TABLE intervention_event;
ALTER TABLE intervention_event_v6 RENAME TO intervention_event;
CREATE INDEX intervention_event_at ON intervention_event (at_ms);

PRAGMA user_version = 6;
