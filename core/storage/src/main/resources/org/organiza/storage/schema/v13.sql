-- Organiza local schema v13. See docs/schema.md and ADR-0036.
-- A note's title is typed by hand; dictation fills the note's text.

ALTER TABLE note ADD COLUMN title TEXT CHECK (title IS NULL OR length(title) BETWEEN 1 AND 80);

PRAGMA user_version = 13;
