# Local schema v1

The source of truth is
[`v1.sql`](../core/storage/src/main/resources/org/organiza/storage/schema/v1.sql),
tested by `SchemaV1Test`. Decisions are in ADR-0004 and ADR-0006.

| Table | Purpose | Notes |
|-------|---------|-------|
| `rule` | A named set of blocks, optionally bound to a calendar tag | `calendar_tag` is `#focus`, `#deepwork` and so on |
| `rule_target` | What a rule blocks | `package` or `domain`; full URLs are not stored |
| `session` | A planned or running block period | History survives rule deletion; calendar occurrences are unique per UID and start |
| `habit` | A daily criterion | `manual`, `screen_time_under` or `zero_launches` |
| `habit_log` | One result per habit per local day | Holds the result only, never the underlying usage |
| `challenge` | A peer challenge over a habit | A habit in a challenge cannot be deleted |
| `challenge_log` | One daily commitment, and the only table that feeds sync | Columns are pinned by an allow-list test |

## Conventions

- All tables are `STRICT`, and `PRAGMA foreign_keys = ON` is required on every connection.
- Instants are `*_at_ms`, UTC epoch milliseconds. Days are `local_date`, ISO-8601.
- The date `GLOB` checks catch format errors only, so `2026-13-45` passes. The domain layer
  validates real dates.
- Primary keys are app-generated text IDs.
- A released `vN.sql` is never edited. Changes ship as a new version with tests.
- The Phase 2 engine runs this SQL under SQLCipher (ADR-0004).
