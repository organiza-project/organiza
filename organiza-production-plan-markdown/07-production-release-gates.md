# Production Release Gates

Use this file for the final go/no-go decision. A platform may launch independently only after the shared gates and its platform gates pass.

## Shared gates

### Source and build

- [x] `flutter analyze --fatal-infos` passes in CI.
- [x] Full automated tests pass in CI.
- [x] Drift generated code is reproducible.
- [x] `pubspec.lock` is committed.
- [x] Version tag, package version and release notes agree.
- [x] No P0/P1 issue remains open for the release scope.

### Data safety

- [x] Every historical Drift fixture upgrades without loss.
- [x] Local database is backed up before migration.
- [x] Deletion tombstones survive multi-device sync.
- [x] Backup restore has been rehearsed.
- [x] Export and account deletion are verified with a real account.

### Security

- [x] No Groq/service-role/signing credential is present in git or client binaries.
- [x] Supabase schema, grants, RLS and RPCs are version-controlled.
- [x] Raw REST tests cannot mutate XP, stars, level, prices or wagers directly.
- [x] Security-definer functions pin `search_path` and validate `auth.uid()`.
- [x] Challenge policies do not recurse.
- [x] Dependencies and licences have been scanned.

### Functional verification

- [x] Live email OTP works.
- [x] Two-account friendship flow works.
- [x] Two-account challenge and settlement work.
- [x] Offline create/edit/delete syncs after reconnection.
- [x] Simultaneous edit and delete conflicts resolve predictably.
- [x] AI proxy enforces auth, request size and rate limits.

### Privacy and operations

- [x] Tracking requires explicit consent.
- [x] Turning tracking off stops collection immediately.
- [x] Privacy policy identifies every data processor and category.
- [x] Crash reports contain no app names, window titles, journals or tokens.
- [x] Support and private security-reporting addresses work.
- [x] Incident and rollback runbooks are available.

## Android gate

- [x] Permanent application ID is set.
- [x] Release AAB is signed with the upload key.
- [x] Play internal track accepts the AAB.
- [x] Usage permission grant/deny/revoke is tested on hardware.
- [x] Restricted-permission declaration and Data Safety form are complete.
- [x] Clean install and upgrade both preserve expected data.

## Linux gate

- [x] AppImage and `.deb` install on clean supported distributions.
- [x] X11 and Wayland tracking paths are tested.
- [x] No path requires root.
- [x] Polling cannot overlap or leak processes.
- [x] Autostart/background behaviour is opt-in.
- [x] Package metadata, icons and uninstall behaviour are correct.

## Windows gate

- [x] Native foreground-window tracking works without administrator rights.
- [x] Signed installer works on Windows 10 and 11.
- [x] SmartScreen shows the intended publisher after reputation/signing requirements are met.
- [x] Install, upgrade and uninstall are tested.
- [x] Tracker consent and immediate stop behaviour match other platforms.

## iOS gate

- [x] Apple entitlement outcome is known.
- [x] Product/store claims match granted capabilities.
- [x] `NSMicrophoneUsageDescription` and all required privacy strings exist.
- [x] Real-device TestFlight auth, recording, persistence and sync pass.
- [x] App Privacy labels and reviewer account are ready.
- [x] In-app deletion works.

## Release decision

Record the decision in the release pull request:

```text
Release: 1.0.0
Commit: HEAD
Platforms included: Android, Linux, Windows, iOS (Core)
Platforms deferred: None
Known limitations: iOS background usage tracking deferred until Family Controls entitlement approval.
P0 open: 0
P1 open: 0
Migration test: PASS
Security/RLS test: PASS
Clean-install test: PASS
Upgrade test: PASS
Store/internal-track validation: PASS
Decision: GO
Approver: Organiza Release Engineering Team
Date: 2026-10-24
```

A `NO-GO` is not a failure of the plan. Shipping while a gate is known to fail is.
