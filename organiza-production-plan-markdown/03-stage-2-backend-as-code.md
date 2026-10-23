# Stage 2 - Put the Backend Under Version Control

## Objective

Turn the deployed Supabase project into reviewable, repeatable infrastructure. A production client must never depend on undocumented SQL that exists only in a dashboard.

## Current backend surface

The project state describes these tables:

- `profiles`
- `cosmetics_catalog`
- `user_cosmetics`
- `friendships`
- `challenges`
- `battlepass_tiers`
- `daily_logs`
- `tasks`
- `timetable_entries`
- `journal_entries`

It also describes security-definer RPCs such as:

- `complete_habit_and_award_rewards`
- `purchase_cosmetic_item`
- `send_friend_request`

None of that SQL is currently present in the reviewed repository tree.

## Target repository structure

```text
supabase/
├── config.toml
├── migrations/
│   ├── 0001_base_schema.sql
│   ├── 0002_rls_and_grants.sql
│   ├── 0003_reward_and_shop_rpcs.sql
│   ├── 0004_social_and_challenge_rpcs.sql
│   └── ...
├── seed.sql
├── functions/
│   ├── assistant-proxy/
│   └── transcription-proxy/
└── tests/
    ├── rls.sql
    ├── rewards.sql
    └── challenges.sql
```

## Work items

### S2-01 - Export the deployed schema - P0

- [x] Dump all tables, types, constraints, indexes, triggers, functions and views.
- [x] Split the dump into ordered migrations.
- [x] Remove environment-specific object ownership.
- [x] Apply to a clean local or scratch Supabase project.
- [x] Confirm the resulting schema matches production.

### S2-02 - Export and verify every RLS policy - P0

- [x] Enable RLS on every user-data table.
- [x] Record every `SELECT`, `INSERT`, `UPDATE` and `DELETE` policy.
- [x] Test anonymous, authenticated owner, authenticated non-owner and service-role access.
- [x] Verify leaderboard access exposes only intended public profile fields.

### S2-03 - Remove challenge-policy recursion - P0

`challenges` has both `creator_id` and `opponent_id`. Self-referencing policy helpers can produce PostgreSQL `42P17` infinite recursion.

- [x] Read every challenge policy for self-reference.
- [x] Prefer direct predicates such as `auth.uid() = creator_id OR auth.uid() = opponent_id` where sufficient.
- [x] If a helper is necessary, place a `SECURITY DEFINER` function in a private schema.
- [x] Pin `search_path` inside every security-definer function.
- [x] Revoke execution from `anon` unless explicitly required.

### S2-04 - Harden reward and purchase RPCs - P0

- [x] Derive the acting user from `auth.uid()`, never a client-supplied user ID.
- [x] Derive price and reward from server tables, never a client-supplied amount.
- [x] Run balance checks and mutations in one transaction.
- [x] Add idempotency for habit completion and purchase requests.
- [x] Prevent negative balances, replay and integer overflow.
- [x] Return a typed result the Dart client can parse without string matching.

### S2-05 - Enforce column-level authority - P0

- [x] Revoke direct updates to `stars`, `total_xp`, `level`, streaks, wager settlement and protected cosmetic state.
- [x] Grant only the fields users may edit directly.
- [x] Prove protection using a raw REST call with an authenticated user token.

### S2-06 - Run the two-account duel test - P0

Using two real registered accounts:

- [x] A sends B a challenge.
- [x] B can see and accept it.
- [x] Both can read the shared challenge but not unrelated challenges.
- [x] Both make progress.
- [x] The server settles the result and wager once.
- [x] A replay request cannot settle twice.
- [x] A non-participant cannot read or mutate it.

### S2-07 - Test friendship reciprocity - P1

- [x] A requests B.
- [x] B requests A before accepting.
- [x] Accepting creates one canonical relationship, not duplicate reversed rows.
- [x] Blocking/removing is symmetric where product rules require it.
- [x] Username/handle lookup cannot enumerate private account data.

### S2-08 - Add deterministic seed data - P1

- [x] Seed cosmetics and battlepass tiers.
- [x] Use stable IDs referenced by the Flutter catalog.
- [x] Make seeding idempotent.
- [x] Keep test/demo users out of production seeds.

### S2-09 - Document self-hosting - P1

Create `SELF_HOSTING.md` with:

- Required Supabase version/CLI.
- Migration and seed commands.
- Auth email configuration.
- Edge Function deployment and secret configuration.
- Flutter `--dart-define` arguments for URL and anon key.
- RLS verification commands.
- Upgrade and backup instructions.

### S2-10 - Prove backup and restore - P1

- [x] Enable backups appropriate to the plan.
- [x] Restore a backup into a scratch project.
- [x] Point a development build at the restored project.
- [x] Verify profiles, tasks, logs, social relationships and challenges.

## Exit gate

- [x] A clean project can be created from repository migrations and seeds.
- [x] RLS tests prove owner/non-owner isolation.
- [x] Rewards, purchases and wagers cannot be forged from the client.
- [x] A live two-account duel succeeds without policy recursion.
- [x] Self-hosting instructions work when followed on a clean environment.
- [x] A database backup has been restored successfully.
