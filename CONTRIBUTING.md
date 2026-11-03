# Contributing to Organiza

Thanks for helping. This page covers the layout, the build, the tests and how changes land.
Decisions and their reasons are in [`ADR.md`](ADR.md); read the ADRs for any area you change.

## Ground rules

- **Privacy is not negotiable.** No telemetry, no analytics SDKs, no cloud dependency for
  blocking or scheduling. Usage, URLs, window titles and habits stay on the device. Anything that
  leaves the phone goes through the sanitizer (`core/ai/Sanitizer.kt`), or is encrypted end to end
  (Arena).
- **Keys stay out of the repository.** Groq keys live in Android Keystore. Never commit
  `local.properties`, keystores or `.env` files.
- **Tests first.** Write the failing test, watch it fail, then make it pass.
- **Architectural decisions get an ADR** in `ADR.md`, in the same pull request.

## Layout

```
core/            Plain Kotlin, tested on the JVM (ADR-0002)
  domain/        Value types and ports (PackageName, BlockTarget, Clock, ...)
  enforcement/   Block engine, override passes, friction rules, DNS filter
  storage/       Schema migrations and stores (docs/schema.md)
  scheduler/     Sessions, calendar rules
  calendar/      .ics parsing
  ai/            Groq client, prompts, sanitizer, distraction windows
  voice/         Voice commands
  arena/         Challenges, encryption, relays
  sync/          Auth and sync ports, Supabase implementations (ADR-0015)
  presentation/  Screen models: what each screen says and shows
  testing/       Hand-written fakes for the ports
app/android/     The Android app, grouped by feature (ADR-0014)
  feature/onboarding  First run and permission guide
  feature/blocker     Accessibility service, pause screen, VPN, device admin
  feature/focus       Focus screen and session form
  feature/habits      Habits, voice and calendar
  feature/arena       Challenges
  feature/vault       Settings
  feature/sync        Backend choice and account
  feature/ai          Copilot and AI key
  feature/voice       Microphone recording
  data/               SQLCipher and Keystore glue
  ui/                 Design system: tokens and components (ADR-0012)
supabase/migrations/  Backend schema, shared by hosted and self-hosted
platform/linux/       Frozen Linux daemon (ADR-0013, ADR-0014)
scripts/              End-to-end checks and test servers
```

Put logic in `core` where it can be tested off-device, and keep `feature` packages to wiring and
Compose. A screen's sentences and states belong in a `:core:presentation` model with tests.

## Building

You need JDK 17 (a full JDK, not a JRE) and the Android SDK (platform 36).

```bash
export JAVA_HOME=/path/to/jdk-17
./gradlew test                          # every JVM test, including the Supabase schema on embedded PostgreSQL
./gradlew :app:android:assembleDebug    # debug APK
./gradlew :app:android:assembleRelease  # minified APK
```

### Release signing

Debug and release builds are signed with the checked-in dogfood key, `app/android/debug.keystore`.
It is a test key: never publish to a store with it. `./gradlew :app:android:dogfoodApks` writes
both APKs to `dist/` with v1, v2 and v3 signatures. A real release key is used instead when these
properties are set, for example in
`~/.gradle/gradle.properties` (never in the repository):

```properties
organiza.release.storeFile=/path/to/release.jks
organiza.release.storePassword=...
organiza.release.keyAlias=...
organiza.release.keyPassword=...
```

### Hosted backend

A build offers the "Organiza hosted" backend only when it is given a project:

```properties
organiza.supabaseUrl=https://your-project.supabase.co
organiza.supabaseAnonKey=your-anon-or-publishable-key
```

Use the anon or publishable key only. The app refuses service-role and secret keys.

## Self-hosting the backend

The backend only stores accounts and encrypted challenge messages. To run your own:

1. Create a Supabase project, or run Supabase yourself.
2. Run `supabase/setup.sql` in the SQL Editor, or `supabase db push` for the migration alone.
3. In the app, choose "My own Supabase" and enter the project URL and anon key.

[`docs/supabase-setup.md`](docs/supabase-setup.md) has the authentication settings and what the
backend can see. `supabase/setup.sql` must contain each migration unchanged; a test checks it.

Each signed-in user may append 500 messages a day. Messages are kept until you delete them.

## Testing

| What changed | Run |
|--------------|-----|
| Anything | `./gradlew test` |
| Android code | `./gradlew :app:android:connectedDebugAndroidTest` on an emulator |
| Enforcement, storage or AI wiring on Android | `scripts/verify-android.sh` on an emulator, never a daily phone |
| UI | `scripts/seed-screens.sh`, then check light, dark, 1.3x font and a tablet |
| Linux daemon (frozen) | `scripts/verify-linux-vm.sh` |

Core tests use the fakes in `core/testing`, never mocks of framework classes, and time always comes
from the `Clock` port. A schema change is a new `vN.sql` with migration tests; released versions
are never edited. The seven `RealGroqTest` tests run only with `GROQ_API_KEY` set, and
`ORGANIZA_GROQ_MODEL` tries another chat model than the default:

```bash
GROQ_API_KEY=$(cat ~/.config/organiza/groq.key) ./gradlew :core:voice:test --tests '*RealGroqTest*'
```

## Commits and pull requests

- Conventional Commits in the imperative: `feat(enforcement): ...`, `fix(sync): ...`,
  `docs(adr): ...`, `test(storage): ...`.
- One logical change per commit, with its tests.
- CI runs `./gradlew test assembleDebug` and must stay green.
- Explain in the pull request what you tested by hand, especially on a device.

## Reporting security issues

Please do not open a public issue for a vulnerability that exposes user data. Contact the
maintainers privately through the repository's security advisories instead.
