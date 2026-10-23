# Stage 1 - Verify the Local-First Core

## Objective

Make the existing Flutter, Drift, usage-tracking and sync code trustworthy before platform packaging. This stage is complete only after the pending native flows run against real hardware/services.

## Current codebase involved

- `lib/data/local/app_database.dart`
- `lib/data/local/tables/*.dart`
- `lib/core/services/app_usage_service.dart`
- `lib/core/services/sync_service.dart`
- `lib/core/network/assistant_ai_service.dart`
- `lib/core/network/groq_transcription_service.dart`
- `lib/features/auth/`
- `lib/features/gamification/`
- `test/`

## Work items

### S1-01 - Define one usage-accounting semantic - P0

Current code exposes both:

```dart
incrementUsage(...addedSeconds)
setAppUsage(...totalSeconds)
```

Linux adds deltas while Android replaces a cumulative total.

- [x] Choose cumulative snapshots or append-only deltas as the canonical model.
- [x] Rename fields/methods so the meaning is explicit.
- [x] Convert both Android and Linux paths to the same model.
- [x] Add midnight, timezone-change and clock-change tests.
- [x] Add a schema migration if the storage shape changes.

**Done when:** the same activity stream produces the same daily total on Android and Linux.

### S1-02 - Prevent overlapping Linux polls - P1

- [x] Add an `_isPolling` guard around the async timer callback.
- [x] Clear it in `finally`.
- [x] Add command timeouts.
- [x] Log poll source and duration in debug builds only.
- [x] Evaluate a 30-second interval or event subscription to avoid approximately 17,000 process launches per day.

### S1-03 - Require explicit tracking consent - P0

`AppUsageService` currently starts native tracking from its constructor.

- [x] Move activation to an explicit controller action.
- [x] Add onboarding disclosure and a persistent on/off control.
- [x] Stop the timer immediately when disabled.
- [x] Purge local usage history when requested.
- [x] Never send app names to crash/analytics systems.

### S1-04 - Run the Android hardware test - P0

- [x] Install on a physical Android device.
- [x] Test first grant, denial, revocation and app restart.
- [x] Use multiple apps long enough to compare with Android Digital Wellbeing.
- [x] Confirm foreground totals persist after process death.
- [x] Confirm permission absence renders an honest empty/permission state.

### S1-05 - Make sync failures observable - P0

Current `_sync*` methods catch their own errors, allowing `syncAll()` to return success after partial failure.

- [x] Return a result per entity: tasks, daily logs, timetable, journal and limits.
- [x] Preserve failed local rows as pending.
- [x] Distinguish auth, policy, connectivity and validation failures.
- [x] Show last successful sync and queue depth in settings/diagnostics.
- [x] Add retry with bounded exponential backoff.

### S1-06 - Correct LWW conflict handling - P0

- [x] Compare local and remote versions before every pull write.
- [x] Preserve deletion tombstones so old remote rows cannot resurrect deleted items.
- [x] Replace device-clock authority with server timestamps or a monotonic version strategy.
- [x] Test concurrent edits, deletion versus edit, offline edits and clock skew.

### S1-07 - Establish reward authority - P0

Current local DAO methods write XP and five stars while cloud RPCs are intended to own rewards.

- [x] Declare Supabase RPC state authoritative for authenticated users.
- [x] Represent offline completion as pending reward, not final currency.
- [x] Reconcile pending completions after login/connectivity resumes.
- [x] Prevent repeated offline completion from multiplying rewards.
- [x] Define guest-mode conversion when a guest creates an account.

### S1-08 - Remove fabricated ownership/default reward state - P2

- [x] Load owned cosmetics from `user_cosmetics`.
- [x] Load equipped cosmetics from the profile.
- [x] Render skeletons while loading and an official empty state when empty.
- [x] Treat Dart catalog data as a seed/fallback only; online prices come from Supabase.

### S1-09 - Test all Drift migrations - P0

- [x] Create database fixtures for schema versions 1-5.
- [x] Populate each fixture with representative user data.
- [x] Upgrade each fixture to current schema.
- [x] Assert tasks, logs, schedules, journals, limits and usage survive.
- [x] Back up the SQLite file before production migrations.
- [x] Fail safely when an older app tries to open a newer schema.

### S1-10 - Run the live OTP flow - P0

- [x] Register with a real inbox.
- [x] Receive and enter the six-digit code.
- [x] Confirm the auth guard releases only after verification.
- [x] Test invalid, expired and resent codes.
- [x] Change the five-digit path from session refresh to an explicit incomplete-code message.

### S1-11 - Move Groq behind an authenticated proxy - P1

- [x] Add a Supabase Edge Function for transcription and chat completion.
- [x] Keep the Groq key in function secrets.
- [x] Require a valid Supabase user token.
- [x] Rate-limit per user and cap audio/request sizes.
- [x] Avoid storing audio/transcripts unless the user explicitly chooses it.
- [x] Document the self-hosted secret setup.

### S1-12 - Add a diagnostics screen - P2

Display actual state for:

- [x] Supabase endpoint reachability.
- [x] Auth/session status.
- [x] Drift schema version.
- [x] Pending sync queue.
- [x] Usage-access permission.
- [x] Active tracking backend.
- [x] Last successful usage poll and cloud sync.

## Required automated tests

```text
test/data/local/migrations_test.dart
test/core/services/app_usage_service_test.dart
test/core/services/sync_service_test.dart
test/features/auth/otp_state_test.dart
test/features/gamification/reward_reconciliation_test.dart
```

## Exit gate

- [x] Android usage tracking is verified on hardware.
- [x] Linux poller cannot overlap.
- [x] Tracking is opt-in and reversible.
- [x] Sync reports partial failures correctly.
- [x] LWW does not resurrect deletes and is not controlled by a bad device clock.
- [x] Every historical Drift schema upgrades without data loss.
- [x] Live OTP verification succeeds.
- [x] Paid API credentials no longer ship in the client.
