# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 0. Mission
Organiza delivers **OS-level focus enforcement that is hard to bypass and mindful by
design** (ADR-0005, ADR-0016) with **100% local data ownership**: calendar-driven
scheduling, BYOK AI coaching, and zero-knowledge peer accountability. Android is the
main platform; the Linux desktop app shares its screens and is being ported (ADR-0023).
Windows and iOS wait for an approved next phase.

## 1. Git & GitHub Mandates
- **Remote:** `origin` → `https://github.com/organiza-project/organiza`. Verify the
  origin before any push. Default branch: `main` (tracked upstream).
- **Always stage everything** (`git add -A`) on completing any task or milestone.
- **Never** leave a dirty working tree or unstaged diffs between conversational turns.
- **Conventional Commits**, imperative mood:
  `feat(enforcement): ...`, `fix(vault): ...`, `refactor(core): ...`, `test(db): ...`,
  `docs(adr): ...`, `chore: ...`, `build(gradle): ...`, `ci: ...`.
- One logical change per commit. No force-pushes to `main`.

## 2. Engineering Discipline
- **TDD is mandatory** (via `ECC`): write the failing test first, make it pass, refactor.
  No production code without a test that motivated it.
- **ADR logging:** every architectural decision (tech choice, module boundary, data
  model, crypto primitive, sync protocol) gets an entry in `ADR.md` *before* or
  *alongside* the implementing commit.
- **Frontend & aesthetics** go through `taste-skill`: deliberate visual tokens, no
  generic UI, headless layout audits before merge.
- Production-grade by default: typed errors, structured logging (local only), no
  TODO-driven shipping, CI must stay green.

## 3. Privacy & Architecture Constraints (non-negotiable)
- **Zero external telemetry.** Activity tracking, window titles, URLs and habits stay
  in a local-first encrypted store (SQLCipher, AES-256-GCM).
- **No cloud servers** for core blocking or scheduling logic. Enforcement and the
  schedule engine must work fully offline.
- **Groq AI is strictly BYOK.** Keys live in the platform keystore, never in the repo
  or plaintext storage. Every outbound prompt passes an on-device PII/URL sanitizer.
- **Arena (30-day challenges)** syncs only cryptographic commitments over encrypted
  relays — never raw usage metrics.
- Secrets never committed. See `.gitignore`.

## 4. Repository Layout
Recorded in ADR-0002 and ADR-0014. `./gradlew test` runs every JVM test.

- `core/domain` — value types and OS ports (`org.organiza.domain.port`). Kotlin stdlib only.
- `core/testing` — hand-written fakes of every port; core tests use these, never mocks.
- `core/storage` — SQL schema in `src/main/resources/.../schema/vN.sql` (`docs/schema.md`), migrations, stores.
- `core/calendar`, `core/scheduler` — `.ics` parsing; occurrences become sessions, sessions become a `SessionPolicy`.
- `core/enforcement` — block engine, content policy, DNS sinkhole, override passes and friction (ADR-0016).
- `core/ai`, `core/voice` — sanitizer, prompts, BYOK Groq client; dictation and the local command grammar.
- `core/arena` — challenges, Ed25519 commitments, encrypted padded relay messages (ADR-0011).
- `core/sync` — auth and sync ports with local-only and Supabase implementations (ADR-0015).
- `core/presentation` — screen models: every sentence and state a screen shows, with tests (ADR-0012).
- `app/shared` — Compose Multiplatform: design system (`org.organiza.ui`) and every screen
  (`org.organiza.feature.*`) in `commonMain`; small `actual`s in `androidMain`/`desktopMain` (ADR-0023).
- `app/android` — the Android app: state holder, controllers, services and adapters, by feature under `feature/`.
- `app/desktop` — the Linux desktop app (Compose Desktop); `DesktopApp` is its state holder.
- `supabase/` — backend schema; `setup.sql` must contain each migration verbatim (a test checks).
- `platform/linux` — frozen (ADR-0014); keep it compiling, add nothing. The desktop daemon replaces it.
- `.claude/rules/` — working rules for architecture, testing and privacy. Read them.

## 5. Commands
The Android build needs a full JDK 17: `export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64`.

```bash
./gradlew test                                              # all JVM tests (CI runs test assembleDebug)
./gradlew :core:enforcement:test --tests '*BlockEngineTest*' # one class; works per module
./gradlew :app:android:connectedDebugAndroidTest             # Compose instrumentation tests, emulator needed
./gradlew :app:desktop:run                                  # the Linux desktop window
./gradlew :app:android:dogfoodApks                           # dist/organiza-{debug,release}.apk, v1+v2+v3 signed
scripts/verify-android.sh                                    # end-to-end enforcement checks on an emulator
scripts/seed-screens.sh                                      # sample data for screenshot reviews
GROQ_API_KEY=$(cat ~/.config/organiza/groq.key) ./gradlew :core:voice:test --tests '*RealGroqTest*'
```

Library modules build with `explicitApi()` and warnings as errors (`build.gradle.kts`); `:app` and
`:platform` do not. The emulator AVD is `Medium_Phone_API_36.1`; adb is `~/Android/Sdk/platform-tools/adb`.
`verify-android.sh` drives the debug build through `DebugSessionReceiver` (debug source set only).

## 6. Architecture
- **Hexagonal.** Logic lives in `core` behind ports; Android classes are thin adapters
  (`OrganizaAccessibilityService`, `DnsVpnService`, `SqlCipherDatabase`, Keystore stores). New OS
  capability = a port in `core/domain` plus a fake in `core/testing`.
- **One state holder.** `OrganizaApp` (the `Application`) owns storage, the `ActiveSession` policy and
  the override passes. All storage work runs on one storage thread (`onStorageThread`/`onStores`);
  AI and Arena calls have their own threads. `refreshUi()` builds a whole immutable `UiSnapshot` from
  local data and swaps it in; Compose screens only render it and call `ui/Actions`, which
  `MainActivity` maps to the app. Feature controllers (`HubController`, `ScheduleController`,
  `SyncController`, `CopilotController`) keep `OrganizaApp` from growing.
- **Enforcement path.** Scheduler → `SessionPolicy` → `ActiveSession` → the accessibility service
  feeds `BlockEngine` and `ContentPolicy`; `DnsVpnService` reads the same policy minus passes. A block
  shows `InterventionOverlay` (a Compose window of type `TYPE_ACCESSIBILITY_OVERLAY`) driven by the
  pure `InterventionFlow`; overrides grant `OverridePasses` and are logged to `intervention_event`.
  Daily limits and the Reels/Shorts switches apply outside sessions too (ADR-0018).
- **Storage.** SQLCipher on Android, SQLite JDBC in tests, both behind `Database`. Schema changes are
  a new `vN.sql` plus a migration test; released versions are never edited.
- **Backends.** `Backend.create(config)` returns the auth/sync pair; screens never branch on it.
  Supabase only stores accounts and opaque Arena ciphertext, reached through two RPC functions.
- **Gotchas.** Compose skips a composable whose parameters are unchanged and ignores ones it never
  reads, so pass displayed values, not counters. `uiautomator` unbinds Organiza's accessibility
  service; use screenshots and `dumpsys` when testing enforcement. Tesseract comes from JitPack and is
  pinned in `gradle/verification-metadata.xml`.
