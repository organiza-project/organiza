# Organiza Production Plan

> Baseline: `organiza-project/organiza`, branch `main`, commit `3b45347584da6184987f76b7cf7b1b599e44069c`, reviewed 2026-10-23.
>
> Target platforms: Android, iOS, Windows and Linux. Web is explicitly optional and not part of the production gate.

This directory is the executable roadmap for taking Organiza from its current Flutter repository to a production-ready, supportable application. Work in stage order. A later stage does not make an earlier exit gate optional.

## Recommended release order

1. Complete shared Stages 0-2 once.
2. Ship Linux and Android first.
3. Build and ship Windows.
4. Start iOS only after Apple answers the Screen Time / DeviceActivity entitlement request.

The codebase is not equally mature across platforms: Linux is the most developed target, Android is close but still unverified on hardware, and Windows and iOS are mostly generated platform scaffolds.

## Documents

- [Current codebase and technology stack](00-current-codebase-and-tech-stack.md)
- [Stage 0 - Stabilise](01-stage-0-stabilise.md)
- [Stage 1 - Verified local core](02-stage-1-verified-local-core.md)
- [Stage 2 - Backend as code](03-stage-2-backend-as-code.md)
- [Stage 3 - Platform release tracks](04-stage-3-platform-release-tracks.md)
- [Stage 4 - Release engineering](05-stage-4-release-engineering.md)
- [Stage 5 - Store, legal and operations](06-stage-5-store-legal-and-operations.md)
- [Production release gates](07-production-release-gates.md)

## Status model

Use one status on every task:

- `Not started`
- `In progress`
- `Blocked`
- `Done`
- `Deferred`

## Priority model

- **P0:** Blocks production or risks security/data loss.
- **P1:** Blocks the affected platform's supported release.
- **P2:** Required before broad public adoption or store submission.
- **P3:** Improvement that may be deferred without misrepresenting readiness.

## Definition of production ready

Organiza is production ready only when:

- Every supported platform installs from a signed, versioned CI artifact.
- There are no open P0 or P1 defects for that platform.
- `flutter analyze --fatal-infos` and the full automated test suite pass in CI.
- The Android usage, live OTP and two-account duel tests have run against real services/devices.
- A v1 database fixture upgrades to the current Drift schema without losing data.
- Supabase schema, RLS policies and RPCs are version-controlled and reproducible.
- Privacy, deletion, export and incident-response paths are documented and tested.
- Unsupported capabilities are stated honestly instead of simulated with placeholder data.
