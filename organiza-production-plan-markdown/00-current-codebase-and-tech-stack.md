# Current Codebase and Technology Stack

## Repository baseline

| Item | Current state |
|---|---|
| Repository | `organiza-project/organiza` |
| Branch | `main` |
| Reviewed commit | `3b45347584da6184987f76b7cf7b1b599e44069c` |
| Published releases | None at review time |
| App version | `1.0.0+1` |
| Dart SDK constraint | `^3.10.0` |
| License | MIT |
| Supported scaffolds present | Android, iOS, Windows, Linux |
| Local schema version | Drift schema version 5 |

## Architecture

Organiza is a Flutter local-first application. Riverpod owns application state; Drift/SQLite is the local source for tasks, logs, timetables, journals, limits and usage records; Supabase provides email/password authentication, cloud sync, social data, multiplayer challenges, profiles and server-authoritative rewards. Platform services collect app-usage data where the operating system permits it. AI voice commands use Groq-compatible HTTP APIs in the current client implementation.

```text
Flutter UI and feature controllers
        |
        +-- Riverpod providers / state notifiers
        |
        +-- Drift SQLite (offline source and reactive streams)
        |      +-- tasks
        |      +-- daily logs
        |      +-- timetable entries
        |      +-- journal entries
        |      +-- app limits
        |      +-- app usage
        |
        +-- Supabase
        |      +-- email/password auth
        |      +-- PostgreSQL + RLS
        |      +-- security-definer reward/shop/social RPCs
        |      +-- LWW multi-device sync
        |
        +-- Native/platform services
               +-- Android UsageStatsManager through usage_stats
               +-- Linux xdotool / GNOME D-Bus / proc fallback
               +-- Windows: not implemented
               +-- iOS: entitlement-dependent and not implemented
```

## Current source layout

```text
lib/
├── main.dart                         # bootstrap, Supabase initialisation, auth root
├── shell.dart                        # responsive application shell/navigation
├── core/
│   ├── config/                       # Supabase runtime/default configuration
│   ├── constants/                    # colours, typography, legal content
│   ├── network/
│   │   ├── assistant_ai_service.dart
│   │   ├── deepseek_assistant_service.dart
│   │   ├── groq_transcription_service.dart
│   │   └── supabase_service.dart
│   ├── services/
│   │   ├── app_usage_service.dart
│   │   ├── sync_service.dart
│   │   └── voice_recording_service.dart
│   └── theme/
├── data/local/
│   ├── app_database.dart             # Drift database, schema v5, DAOs/migrations
│   ├── app_database.g.dart           # generated Drift code, currently committed
│   ├── database_provider.dart
│   └── tables/
│       ├── app_limits_table.dart
│       ├── app_usage_table.dart
│       ├── daily_logs_table.dart
│       ├── journal_entries_table.dart
│       ├── tasks_table.dart
│       └── timetable_entries_table.dart
└── features/
    ├── assistant/
    ├── auth/
    ├── blocker/
    ├── gamification/
    ├── journal/
    ├── leaderboard/
    ├── profile/
    ├── social/
    ├── timetable/
    └── today/
```

## Flutter and Dart dependencies

| Package | Version | Current role |
|---|---:|---|
| `flutter_riverpod` | `^2.5.1` | Dependency injection and state management |
| `drift` | `^2.18.0` | Typed local relational persistence and reactive queries |
| `sqlite3_flutter_libs` | `^0.5.24` | Bundled SQLite runtime |
| `path_provider` | `^2.1.3` | Application-support directory discovery |
| `path` | `^1.9.0` | SQLite path construction |
| `http` | `^1.2.1` | Groq/AI HTTP requests |
| `record` | `^6.2.1` | Microphone recording for voice commands |
| `uuid` | `^4.4.0` | Local identifiers |
| `google_fonts` | `^6.2.1` | Application typography |
| `shared_preferences` | `^2.2.3` | Guest flag, custom server config and other small preferences |
| `usage_stats` | `^1.3.1` | Android usage-access bridge |
| `supabase_flutter` | `^2.8.0` | Auth, PostgreSQL API, realtime/client access |
| `cupertino_icons` | `^1.0.8` | iOS-style icons |

Development dependencies are `flutter_test`, `drift_dev ^2.18.0`, `build_runner ^2.4.9` and `flutter_lints ^6.0.0`.

## Local data layer

`lib/data/local/app_database.dart` registers six Drift tables and sets `schemaVersion => 5`:

- `Tasks`
- `DailyLogs`
- `AppLimits`
- `TimetableEntries`
- `JournalEntries`
- `AppUsageTable`

The migration history currently adds timetable/journal tables in v2, `linkedPackageName` in v3, daily-log `stars` in v4 and app usage in v5. Current DAO methods cover task logging, manual duration edits, soft deletion, XP/star/streak streams, usage accumulation, usage-range queries, limits, timetable CRUD and journal CRUD.

### Confirmed local-data concerns

- Android writes a cumulative value with `setAppUsage`; Linux writes deltas with `incrementUsage`. The same column therefore has two semantics.
- Local `logProgress` and `setLogDuration` assign XP and a hardcoded five stars, while cloud rewards are also meant to be server-authoritative.
- No migration test currently proves v1-to-v5 data survival.
- `app_database.g.dart` is committed while `pubspec.lock` is ignored.

## Cloud/backend layer

Current design uses Supabase for:

- Email/password auth and six-digit email confirmation.
- User profiles, levels, XP, stars, streaks and cosmetics.
- Friend requests and accepted friendships.
- Seven- and thirty-day challenges.
- Leaderboards and multiplayer state.
- Cross-device last-write-wins sync.
- Security-definer RPCs for reward, purchase and social mutations.

The deployed SQL is not present under a `supabase/` or `SQL/` directory. That means the deployed schema, RLS and functions are not currently reproducible from the repository and cannot be reviewed alongside client changes.

## AI and voice layer

- `GroqTranscriptionService` sends recordings to Groq Whisper (`whisper-large-v3`).
- `AssistantAiService` provides tool-calling actions against the local database.
- API keys can be supplied through `--dart-define=GROQ_API_KEY` or saved in `SharedPreferences`.

A client-side Groq key is extractable from any distributed binary and the exported project context contained a live key. The production design must move Groq calls to a server-side proxy with user authentication and rate limiting.

## Current platform maturity

| Platform | Current implementation | Production gap |
|---|---|---|
| Linux | Flutter desktop plus unprivileged active-window polling through `xdotool`, GNOME D-Bus and `/proc` fallback | Poll overlap guard, resource use, Wayland/X11 verification, packaging, autostart and signed artifacts |
| Android | Flutter scaffold, `usage_stats` dependency and Android usage-query branch | Manifest lacks `PACKAGE_USAGE_STATS`; hardware flow unverified; release uses debug signing; application ID is scaffold default |
| Windows | Generated Flutter runner | No active-window tracking, packaging decision, installer or code signing |
| iOS | Generated Flutter scaffold | No Screen Time implementation, entitlement approval unknown, microphone disclosure and real-device testing pending |

## Current Android build configuration

Confirmed in `android/app/build.gradle.kts`:

- Namespace and application ID: `app.organiza.organiza`.
- Java/Kotlin target: 17.
- Release build signs with the debug signing configuration.

Confirmed in `AndroidManifest.xml`:

- Main Flutter activity exists and is exported for launcher use.
- `PACKAGE_USAGE_STATS` is not declared.

Both are release blockers addressed in Stage 0.

## Current test surface

The repository currently contains:

```text
test/
├── widget_test.dart
├── core/config/supabase_config_test.dart
├── core/network/assistant_ai_service_test.dart
├── core/network/supabase_service_test.dart
└── features/profile/user_profile_test.dart
```

The exported project state reports 24 passing test cases, but there are no visible suites for:

- Drift migration v1 to v5.
- Usage poller overlap and accounting semantics.
- Multi-device sync conflicts and deletion tombstones.
- Two-account challenge RLS.
- Live OTP delivery and verification.
- Platform installers or clean-machine launches.

## Current repository hygiene

- `pubspec.lock` is ignored even though this is an application; it should be committed for reproducible builds.
- Generated `app_database.g.dart`, `.idea/` and `organiza.iml` are committed.
- `README.md` is effectively only the project name.
- No version-controlled Supabase migrations were identified.
- No published GitHub release existed at review time.

## Non-negotiable architecture rules

- Remain local-first: core scheduling, habits and journal functions work offline.
- Never drop or recreate a user's Drift database during migration.
- No fabricated profile, usage, leaderboard, reward or shop values; use real state, skeletons or explicit empty states.
- XP, stars, levels, wagers and purchase prices are validated server-side.
- Linux usage tracking never requires `sudo`.
- Android handles denied usage access gracefully.
- Secrets that grant paid API access never ship in client binaries.
