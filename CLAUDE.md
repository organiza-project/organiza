# CLAUDE.md — Project Organiza Directives

## 0. Mission
Organiza delivers **unbypassable OS-level focus enforcement** with **100% local data
ownership**: calendar-driven scheduling, BYOK AI coaching, and zero-knowledge peer
accountability. Android first (AccessibilityService, VpnService loopback,
DeviceAdminReceiver), then Linux, Windows, iOS.

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
Recorded in ADR-0002. `./gradlew test` runs everything.

- `core/domain` — value types and OS ports. Kotlin stdlib only, no Android.
- `core/testing` — fakes of every port.
- `core/storage` — SQL schema (`docs/schema.md`), migrations, repositories (ADR-0008).
- `core/calendar` — `.ics` parser and recurrence engine (ADR-0008).
- `core/scheduler` — turns occurrences into sessions and sessions into policy (ADR-0008).
- `core/enforcement` — block engine, content policy, DNS sinkhole, tamper monitor (ADR-0007).
- `core/ai` — sanitizer, prompt templates, BYOK Groq client (ADR-0009).
- `core/voice` — dictation pipeline and local command grammar (ADR-0010).
- `core/arena` — challenge rules, commitments, encrypted relay sync, leaderboard (ADR-0011).
- `core/presentation` — tested screen models for the Compose UI (ADR-0012).
- `app/android` — Phase 1: services, overlay, VPN, UI.
- `.claude/rules/` — working rules for architecture, testing and privacy.
