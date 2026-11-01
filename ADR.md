# Architectural Decision Records — Organiza

This log captures every significant architectural decision for Organiza v2.
Each entry is immutable once **Accepted**; superseding decisions get a new entry
that references the one it replaces.

## Format

```
## ADR-NNNN: <Title>

- **Status:** Proposed | Accepted | Deprecated | Superseded by ADR-XXXX
- **Date:** YYYY-MM-DD
- **Deciders:** <names>

### Context
What forces are at play? What problem are we solving?

### Decision
What we decided, stated in full sentences.

### Consequences
What becomes easier, what becomes harder, what risks we accept.

### Alternatives Considered
Options rejected and why.
```

## Index

| ID | Title | Status | Date |
|----|-------|--------|------|
| 0001 | Native Kotlin for Android | Accepted | 2026-11-01 |
| 0002 | Hexagonal module layout | Accepted | 2026-11-01 |
| 0003 | Ports and hand-written fakes for OS APIs | Accepted | 2026-11-01 |
| 0004 | Local schema v1 as a tested SQL artifact | Accepted | 2026-11-01 |
| 0005 | Enforcement threat model and the limits of "unbypassable" | Proposed | 2026-11-01 |
| 0006 | Challenge commitments are not zero-knowledge proofs | Proposed | 2026-11-01 |

---

<!-- New ADRs go below this line, newest last. -->

## ADR-0001: Native Kotlin for Android

- **Status:** Accepted
- **Date:** 2026-11-01
- **Deciders:** jackforce

### Context
Enforcement depends on AccessibilityService, VpnService and DeviceAdminReceiver, all
of which are Android framework components. The purged v1 prototype was a Flutter app,
where those components would live behind platform channels anyway.

### Decision
Android is built natively in Kotlin with Gradle (Kotlin DSL) and a version catalog.
Bytecode targets JVM 17 so that pure Kotlin modules can be consumed by an Android app.

### Consequences
Service code talks to the framework directly with no bridge layer. UI cannot be shared
with other platforms; Phase 6 ports need their own UI.

### Alternatives Considered
Flutter: every critical path would still be native, and the UI gain does not justify
the channel layer. React Native: same objection.

---

## ADR-0002: Hexagonal module layout

- **Status:** Accepted
- **Date:** 2026-11-01
- **Deciders:** jackforce

### Context
Business logic must be deterministic and testable without a device, and is expected
to be reused on other platforms in Phase 6.

### Decision
The repository is split as follows:

| Module | Contents | May depend on |
|--------|----------|---------------|
| `:core:domain` | Value types, enforcement ports, rules | Kotlin stdlib only |
| `:core:storage` | Schema artifact, later the storage engine | `:core:domain` |
| `:core:testing` | Fakes of every port | `:core:domain` |
| `:app:android` (Phase 1) | Services, overlay, VPN, UI | all `:core:*` |

Android imports are impossible in `:core:*` because those modules are plain Kotlin/JVM.

### Consequences
The compiler enforces the boundary, and core tests run in seconds on any JVM. Android
adapters must stay thin, because code in them is only covered by slower tests.

Phase 6 plans to extract the core into Rust, which would be a rewrite of this code
rather than a move. Kotlin Multiplatform is the cheaper alternative and should be
weighed in a new ADR before Phase 6 starts. Nothing here forecloses either option.

### Alternatives Considered
Single module with package conventions: the boundary would not be enforced. Rust core
from day one: slows Phase 1 for platforms that are not being built yet.

---

## ADR-0003: Ports and hand-written fakes for OS APIs

- **Status:** Accepted
- **Date:** 2026-11-01
- **Deciders:** jackforce

### Context
TDD is mandatory, but framework classes cannot be instantiated in a JVM test, and
mocks of them encode guesses about framework behaviour.

### Decision
Each OS capability is a narrow interface in `org.organiza.domain.port`: `Clock`,
`ForegroundAppMonitor`, `BlockSurface`, `DnsUpstream`, `UsageHistory` and
`EnforcementCapabilities`. `:core:testing` provides a stateful fake of each, with its
own tests. Validation failures are typed values (`Parsed.Invalid`), not exceptions.

### Consequences
Enforcement logic can be tested red/green without a device. Fakes can drift from real
behaviour, so Phase 1 adapters need instrumented tests on a device or emulator.

### Alternatives Considered
MockK on framework classes: brittle and tells us nothing about the real OS.
Robolectric only: useful for adapters later, but too slow and heavy for core logic.

---

## ADR-0004: Local schema v1 as a tested SQL artifact

- **Status:** Accepted
- **Date:** 2026-11-01
- **Deciders:** jackforce

### Context
Phase 0 must establish the schema before the storage engine exists, and the privacy
constraints need to hold at the data model level.

### Decision
Schema v1 lives in `core/storage/src/main/resources/org/organiza/storage/schema/v1.sql`
and is documented in `docs/schema.md`. Tests execute it against in-memory SQLite and
assert its constraints. Tables are `STRICT` with foreign keys. `challenge_log` columns
are pinned by an allow-list test, so raw usage data cannot be added to the only table
that feeds sync without a test failing. Instants are UTC epoch milliseconds; habit and
challenge days are local ISO dates, because a day's result is defined by the user's
local calendar.

In Phase 2 the same SQL runs under SQLCipher with a random 256-bit database key,
wrapped by an AES-256-GCM key held in Android Keystore.

### Consequences
The schema is executable and reviewable before any engine choice. Tests use plain
SQLite, so encryption itself must be verified separately in Phase 2.

### Alternatives Considered
Room entities now: pulls Android into Phase 0. Markdown-only spec: nothing would
enforce it.

---

## ADR-0005: Enforcement threat model and the limits of "unbypassable"

- **Status:** Proposed
- **Date:** 2026-11-01
- **Deciders:** pending jackforce review

### Context
The mission promises unbypassable enforcement. A third-party Android app without root
or device-owner status cannot deliver that absolutely, and design and marketing should
not assume it can. Known limits:

- Safe mode disables third-party accessibility services.
- Device admin blocks uninstall only until the user deactivates the admin in Settings.
- Android 13+ restricted settings can stop sideloaded apps being granted accessibility
  until the user allows it manually.
- Only one VPN runs at a time, so starting another one revokes ours.
- A VPN capturing only port 53 does not see browser DNS-over-HTTPS or Private DNS.
- A "zero-upstream" sinkhole cannot be literal: non-blocked queries must still be
  forwarded to a resolver, or all name resolution on the device breaks.
- Google Play restricts Accessibility, VPN and Device Admin use, so listing is not assured.

### Decision
The target is: **bypass requires deliberate effort, and is detected and recorded.**

1. The threat model is a user's own impulsive self, not a determined attacker or
   another person. Organiza is installed and enabled by the person it restricts, who
   can always remove it with enough effort. Controlling a third party's device is out
   of scope.
2. Layers back each other up: accessibility URL inspection covers the DNS gap.
3. Loss of any capability (`onRevoke`, service disconnect, admin disabled) is recorded
   locally, and counts as a failed day in a strict challenge.
4. Blocked queries are answered locally; allowed queries are forwarded to the resolver
   the device already uses, so no new party sees DNS traffic.
5. Prefer `TYPE_ACCESSIBILITY_OVERLAY` over `TYPE_APPLICATION_OVERLAY`, since it needs
   no extra permission and is tied to the service that enforces the block.

### Consequences
User-facing copy should say "hard to bypass" unless a stronger claim can be backed up.
Distribution may need to be sideload or F-Droid first.

### Alternatives Considered
Device-owner provisioning: much stronger, but requires a factory reset, so at most an
optional future tier.

---

## ADR-0006: Challenge commitments are not zero-knowledge proofs

- **Status:** Proposed
- **Date:** 2026-11-01
- **Deciders:** pending jackforce review

### Context
The roadmap describes `H(UserID || ChallengeID || Date || SuccessBit || Salt)` as a
ZKP, with the boolean, the streak and the hash sent to peers. That has three problems:

- It is a hash commitment, not a zero-knowledge proof. It cannot show the success bit
  is true, and a modified client can claim success on any day.
- Without the salt, peers cannot recompute the hash, so it verifies nothing.
- Bare `||` concatenation is ambiguous across field boundaries.

### Decision
Call these **commitments** in code and user-facing copy. For Phase 4:

1. SHA-256 over a domain-separation tag and length-prefixed fields, with a fresh
   32-byte CSPRNG salt per day.
2. Each day's commitment includes the previous one, forming a hash chain, and is signed
   with a per-challenge Ed25519 key. The user ID is a per-challenge pseudonym.
3. Salts are revealed when the challenge ends, so peers can verify the chain. This
   proves results were not rewritten later. It does not prove they were true.
4. Payloads remain limited to the success bit, streak, commitment and signature.

### Consequences
What peers get is tamper evidence and no backdating, plus strong privacy because no
usage data ever leaves the device. Honesty is still self-attested, and the UI must not
imply otherwise.

### Alternatives Considered
Real ZK with device attestation: Android attestation can vouch for the device and app
build, but not for the usage data the app reads, and it adds a Google dependency.
Possibly worth revisiting as an optional anti-cheat tier.
