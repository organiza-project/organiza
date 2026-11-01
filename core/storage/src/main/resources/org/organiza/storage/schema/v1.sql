-- Organiza local schema v1. See docs/schema.md and ADR-0004.
-- Instants are UTC epoch milliseconds. Local days are ISO-8601 dates.
-- Connections must run PRAGMA foreign_keys = ON.

CREATE TABLE rule (
    id            TEXT PRIMARY KEY,
    name          TEXT NOT NULL CHECK (length(name) > 0),
    calendar_tag  TEXT CHECK (calendar_tag GLOB '#?*'),
    strict        INTEGER NOT NULL DEFAULT 0 CHECK (strict IN (0, 1)),
    enabled       INTEGER NOT NULL DEFAULT 1 CHECK (enabled IN (0, 1)),
    created_at_ms INTEGER NOT NULL,
    updated_at_ms INTEGER NOT NULL
) STRICT;

CREATE INDEX rule_calendar_tag ON rule (calendar_tag) WHERE calendar_tag IS NOT NULL;

CREATE TABLE rule_target (
    rule_id TEXT NOT NULL REFERENCES rule (id) ON DELETE CASCADE,
    kind    TEXT NOT NULL CHECK (kind IN ('package', 'domain')),
    value   TEXT NOT NULL,
    PRIMARY KEY (rule_id, kind, value)
) STRICT, WITHOUT ROWID;

CREATE TABLE session (
    id                 TEXT PRIMARY KEY,
    rule_id            TEXT REFERENCES rule (id) ON DELETE SET NULL,
    source             TEXT NOT NULL CHECK (source IN ('manual', 'calendar')),
    state              TEXT NOT NULL CHECK (state IN ('scheduled', 'active', 'completed', 'aborted')),
    calendar_event_uid TEXT,
    starts_at_ms       INTEGER NOT NULL,
    ends_at_ms         INTEGER NOT NULL,
    finished_at_ms     INTEGER,
    CHECK (ends_at_ms > starts_at_ms),
    CHECK (source <> 'calendar' OR calendar_event_uid IS NOT NULL)
) STRICT;

CREATE UNIQUE INDEX session_calendar_occurrence
    ON session (calendar_event_uid, starts_at_ms) WHERE calendar_event_uid IS NOT NULL;
CREATE INDEX session_state_start ON session (state, starts_at_ms);

CREATE TABLE habit (
    id                 TEXT PRIMARY KEY,
    name               TEXT NOT NULL CHECK (length(name) > 0),
    criterion_kind     TEXT NOT NULL CHECK (criterion_kind IN ('manual', 'screen_time_under', 'zero_launches')),
    criterion_target   TEXT,
    criterion_limit_ms INTEGER CHECK (criterion_limit_ms > 0),
    created_at_ms      INTEGER NOT NULL,
    archived_at_ms     INTEGER
) STRICT;

CREATE TABLE habit_log (
    habit_id   TEXT NOT NULL REFERENCES habit (id) ON DELETE CASCADE,
    local_date TEXT NOT NULL CHECK (local_date GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]'),
    success    INTEGER NOT NULL CHECK (success IN (0, 1)),
    PRIMARY KEY (habit_id, local_date)
) STRICT, WITHOUT ROWID;

CREATE TABLE challenge (
    id               TEXT PRIMARY KEY,
    habit_id         TEXT NOT NULL REFERENCES habit (id) ON DELETE RESTRICT,
    title            TEXT NOT NULL CHECK (length(title) > 0),
    starts_on        TEXT NOT NULL CHECK (starts_on GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]'),
    length_days      INTEGER NOT NULL CHECK (length_days > 0),
    freeze_allowance INTEGER NOT NULL DEFAULT 0 CHECK (freeze_allowance >= 0),
    state            TEXT NOT NULL CHECK (state IN ('active', 'completed', 'forfeited'))
) STRICT;

-- The only table that feeds sync. Never add usage data here: see ADR-0006 and the
-- allow-list test in SchemaV1Test. The salt stays on the device until the reveal.
CREATE TABLE challenge_log (
    challenge_id        TEXT NOT NULL REFERENCES challenge (id) ON DELETE CASCADE,
    local_date          TEXT NOT NULL CHECK (local_date GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]'),
    outcome             TEXT NOT NULL CHECK (outcome IN ('success', 'failure', 'frozen')),
    streak              INTEGER NOT NULL CHECK (streak >= 0),
    salt                BLOB NOT NULL CHECK (length(salt) = 32),
    commitment          BLOB NOT NULL CHECK (length(commitment) = 32),
    previous_commitment BLOB CHECK (length(previous_commitment) = 32),
    signature           BLOB,
    synced_at_ms        INTEGER,
    PRIMARY KEY (challenge_id, local_date)
) STRICT, WITHOUT ROWID;

PRAGMA user_version = 1;
