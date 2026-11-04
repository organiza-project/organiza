-- Organiza local schema v7. See docs/schema.md and ADR-0019.
-- Habitica-style tasks and the log of what they earned or cost. Titles stay on this phone and are
-- never synced; challenge_log is still the only table that feeds sync.

CREATE TABLE rpg_task (
    id            TEXT PRIMARY KEY,
    kind          TEXT NOT NULL CHECK (kind IN ('habit', 'daily', 'todo')),
    title         TEXT NOT NULL CHECK (length(title) BETWEEN 1 AND 80),
    created_at_ms INTEGER NOT NULL,
    done_at_ms    INTEGER CHECK (done_at_ms IS NULL OR kind = 'todo')
) STRICT;

-- XP and HP changes, applied in id order. A daily check is one 'daily' row per task and day.
CREATE TABLE rpg_event (
    id         INTEGER PRIMARY KEY,
    task_id    TEXT REFERENCES rpg_task (id) ON DELETE SET NULL,
    reason     TEXT NOT NULL CHECK (reason IN ('habit_up', 'habit_down', 'daily', 'todo', 'missed_daily', 'broke_block', 'fainted')),
    local_date TEXT NOT NULL CHECK (local_date GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]'),
    xp         INTEGER NOT NULL,
    hp         INTEGER NOT NULL,
    at_ms      INTEGER NOT NULL
) STRICT;
CREATE UNIQUE INDEX rpg_daily_once ON rpg_event (task_id, local_date) WHERE reason = 'daily';

PRAGMA user_version = 7;
