-- Organiza local schema v3. See docs/schema.md and ADR-0011.

ALTER TABLE challenge ADD COLUMN max_failures INTEGER NOT NULL DEFAULT 0 CHECK (max_failures >= 0);
ALTER TABLE challenge ADD COLUMN strict INTEGER NOT NULL DEFAULT 0 CHECK (strict IN (0, 1));
ALTER TABLE challenge ADD COLUMN member_id TEXT;
ALTER TABLE challenge ADD COLUMN display_name TEXT;
ALTER TABLE challenge ADD COLUMN relay_url TEXT;
ALTER TABLE challenge ADD COLUMN sync_cursor INTEGER NOT NULL DEFAULT 0;
ALTER TABLE challenge ADD COLUMN join_sent INTEGER NOT NULL DEFAULT 0 CHECK (join_sent IN (0, 1));
ALTER TABLE challenge ADD COLUMN reveal_sent INTEGER NOT NULL DEFAULT 0 CHECK (reveal_sent IN (0, 1));

CREATE TABLE arena_member (
    challenge_id TEXT NOT NULL REFERENCES challenge (id) ON DELETE CASCADE,
    member_id    TEXT NOT NULL,
    display_name TEXT NOT NULL,
    PRIMARY KEY (challenge_id, member_id)
) STRICT, WITHOUT ROWID;

-- What other people sent. Never add usage data here: see ADR-0011 and MigrationsTest.
CREATE TABLE arena_entry (
    challenge_id        TEXT NOT NULL REFERENCES challenge (id) ON DELETE CASCADE,
    member_id           TEXT NOT NULL,
    local_date          TEXT NOT NULL,
    outcome             TEXT NOT NULL CHECK (outcome IN ('success', 'failure', 'frozen')),
    streak              INTEGER NOT NULL CHECK (streak >= 0),
    commitment          BLOB NOT NULL CHECK (length(commitment) = 32),
    previous_commitment BLOB CHECK (length(previous_commitment) = 32),
    signature           BLOB NOT NULL,
    PRIMARY KEY (challenge_id, member_id, local_date)
) STRICT, WITHOUT ROWID;

-- Set when a member signed two different answers for one day.
CREATE TABLE arena_conflict (
    challenge_id TEXT NOT NULL REFERENCES challenge (id) ON DELETE CASCADE,
    member_id    TEXT NOT NULL,
    PRIMARY KEY (challenge_id, member_id)
) STRICT, WITHOUT ROWID;

CREATE TABLE arena_reveal (
    challenge_id TEXT NOT NULL REFERENCES challenge (id) ON DELETE CASCADE,
    member_id    TEXT NOT NULL,
    message      BLOB NOT NULL,
    PRIMARY KEY (challenge_id, member_id)
) STRICT, WITHOUT ROWID;

PRAGMA user_version = 3;
