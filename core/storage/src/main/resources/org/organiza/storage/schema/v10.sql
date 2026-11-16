-- Organiza local schema v10. See docs/schema.md and ADR-0029.
-- Arena is removed: its challenges, results, members and nudges go, children before parents.

DROP TABLE arena_nudge;
DROP TABLE arena_reveal;
DROP TABLE arena_conflict;
DROP TABLE arena_entry;
DROP TABLE arena_member;
DROP TABLE challenge_log;
DROP TABLE challenge;

PRAGMA user_version = 10;
