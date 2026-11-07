-- Organiza local schema v8. See docs/schema.md and ADR-0020.
-- Habits gain a minute goal and an optional linked app whose foreground time counts towards it.
-- Minutes logged by hand are kept per day; app minutes are read from the OS and never stored.
-- Sessions gain a strictness tier: lockdown allows only listed apps, monitor blocks nothing.

ALTER TABLE habit ADD COLUMN goal_minutes INTEGER CHECK (goal_minutes IS NULL OR goal_minutes BETWEEN 1 AND 1440);
ALTER TABLE habit ADD COLUMN linked_package TEXT;

CREATE TABLE habit_minutes (
    habit_id   TEXT NOT NULL REFERENCES habit (id) ON DELETE CASCADE,
    local_date TEXT NOT NULL CHECK (local_date GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]'),
    minutes    INTEGER NOT NULL CHECK (minutes BETWEEN 0 AND 1440),
    PRIMARY KEY (habit_id, local_date)
) STRICT, WITHOUT ROWID;

ALTER TABLE session ADD COLUMN tier TEXT NOT NULL DEFAULT 'standard' CHECK (tier IN ('lockdown', 'standard', 'monitor'));

PRAGMA user_version = 8;
