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
| 0005 | Enforcement threat model and the limits of "unbypassable" | Accepted | 2026-11-01 |
| 0006 | Challenge commitments are not zero-knowledge proofs | Accepted | 2026-11-01 |
| 0007 | Android enforcement pipeline | Accepted | 2026-11-01 |
| 0008 | Encrypted storage and deterministic calendar scheduling | Accepted | 2026-11-01 |
| 0009 | BYOK AI pipeline and on-device sanitizer | Accepted | 2026-11-01 |
| 0010 | Voice commands through Groq Whisper | Accepted | 2026-11-01 |
| 0011 | Arena challenges, encrypted sync and a blind relay | Accepted | 2026-11-02 |
| 0012 | Compose UI on tested screen models, achromatic ledger style | Accepted | 2026-11-02 |
| 0013 | Linux daemon on the shared Kotlin core; native core deferred | Accepted | 2026-11-02 |
| 0014 | Android-only milestone and feature packages | Accepted | 2026-11-02 |
| 0015 | Backend choice: local only, hosted Supabase or self-hosted | Accepted | 2026-11-02 |
| 0016 | Mindful friction: typed-phrase overrides instead of hard lockouts | Accepted | 2026-11-02 |
| 0017 | AI copilot insights and override reflection | Accepted | 2026-11-02 |
| 0018 | Wireframe redesign: limits, habit plans, schedule import, XP and nudges | Accepted | 2026-11-03 |

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

- **Status:** Accepted
- **Date:** 2026-11-01
- **Deciders:** jackforce

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

- **Status:** Accepted
- **Date:** 2026-11-01
- **Deciders:** jackforce

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

---

## ADR-0007: Android enforcement pipeline

- **Status:** Accepted
- **Date:** 2026-11-01
- **Deciders:** jackforce

### Context
Phase 1 implements the pipeline in the roadmap under the limits in ADR-0005. The
deterministic parts need tests without a device, and a mistake in this layer can lock
someone out of their phone.

### Decision
- A new `:core:enforcement` module holds the block engine, content policy, DNS packet
  handling and tamper monitor. `:app:android` only translates framework calls.
- Debouncing is leading-edge: the first event for an app acts at once, and only repeats
  inside the window are dropped.
- **Lockout protection:** launchers, dialers, emergency apps and Organiza itself can
  never be blocked, whatever the block list says.
- **Scoped text reading:** visible text is read only on system settings and installer
  screens during strict mode, and browser URLs only from known URL bars. In-app short
  form players match on view IDs, never on labels: YouTube's Shorts tab label is always
  on screen, so matching it would block the whole app.
- **DNS:** only the virtual DNS address is routed through the VPN, so other traffic is
  untouched. TCP to port 53 is reset. Allowed queries use a protected socket to the
  resolver of the underlying non-VPN network.
- Sessions are held in memory until the Phase 2 store exists, so killing the process
  ends the session. That is a known gap, not the final behaviour.
- The Phase 1 screen is a plain setup tool for granting permissions and testing.
  Phase 5 replaces it through `taste-skill`.
- A debug-only receiver, guarded by the `DUMP` permission so only the shell can use it,
  starts sessions for adb-driven verification. It is not in release builds.

### Consequences
Short form view IDs and browser URL bar IDs are private to those apps and will rot, so
they need checks against real app versions. Truncated DNS answers that need a TCP retry
fail. Application ID `org.organiza.app` assumes the project can keep using that name;
change it before the first public release if not.

### Alternatives Considered
Trailing-edge debounce: adds visible delay before each block. Full-tunnel VPN: routes
all traffic through the app for no enforcement benefit, and worsens privacy and battery.

---

## ADR-0008: Encrypted storage and deterministic calendar scheduling

- **Status:** Accepted
- **Date:** 2026-11-01
- **Deciders:** jackforce

### Context
Sessions live in memory, so killing the process ends them (ADR-0007). Scheduling must
work offline and give the same answer every time, and calendar files are untrusted input.

### Decision
- **Storage:** `:core:storage` holds migrations and repositories over a small `Database`
  port. Tests use SQLite through JDBC. Android uses `sqlcipher-android` (AES-256).
- **Key handling:** a random 256-bit database key is wrapped with AES-256-GCM by a
  non-exportable Android Keystore key and kept in `noBackupFilesDir`. If unwrapping fails,
  the app reports the error and never deletes or recreates the database.
- **Schema v2** adds `rule.block_short_form`, `session.granted_capabilities` and
  `tamper_event`. `tamper_event` stores the lost capability and the time, nothing else.
- **`:core:calendar`** is a pure parser and recurrence engine for an RFC 5545 subset.
  Anything it cannot interpret faithfully, such as an unsupported RRULE part or unknown
  time zone, is reported as an issue and skipped. It never guesses at a schedule.
- **`:core:scheduler`** turns occurrences into sessions and sessions into the active
  policy. Only start and end times and the event UID are stored, never titles or
  descriptions. Tags match whole tokens, case-insensitively, so `#focused` is not
  `#focus`. With several matching tags, the alphabetically first one wins.
- **Anti-bypass:** calendar sync only changes sessions still `scheduled`, so editing or
  deleting an event cannot end a running session. Strict sessions cannot be stopped early.
- **Lockout protection:** strict sessions are limited to 24 hours, so a mistake or a
  recurring event can never become open-ended.
- **Timing:** no exact alarms, which would need a restricted permission. The scheduler
  re-evaluates on a timer while the process runs and on every foreground change, so a
  session is enforced by the time the phone is actually being used.
- **Calendar source for now:** `.ics` import through the system file picker.

### Consequences
Sessions survive a process kill, and tamper events are durable. Enforcement relies on the
accessibility service keeping the process alive. The wall clock is trusted, so moving the
system clock forward can end a session early; detecting that is unsolved. A multi-day
strict event is skipped and reported rather than shortened.

The roadmap's bidirectional CalDAV sync is **not** covered here. It needs a network client
and credentials in the keystore, so it gets its own ADR.

### Alternatives Considered
Room: adds annotation processing and would hide the tested SQL artifact. Reading Android's
calendar provider: needs a sensitive permission and exposes titles of every event.
AlarmManager exact alarms: restricted, and unnecessary while the phone is not in use.

---

## ADR-0009: BYOK AI pipeline and on-device sanitizer

- **Status:** Accepted
- **Date:** 2026-11-01
- **Deciders:** jackforce

### Context
AI features send text to a third party using the user's own Groq key. CLAUDE.md requires
every outbound prompt to pass an on-device sanitizer. Regex and heuristics cannot catch
every name or identifier, so the sanitizer cannot be the only protection.

### Decision
- **Minimise first, sanitize second.** Prompts are built from structured facts: durations,
  counts, hours of the day, tags and habit names. App names, domains, URLs, event titles and
  descriptions are never inputs. Only short user-written text, such as goals, tags, habit
  names and a gatekeeper answer, is free text, and it always goes through the sanitizer.
- **The compiler enforces the route.** `SanitizedText` and `Prompt` have internal
  constructors, and `GroqClient` accepts only a `Prompt`. Raw text cannot reach it without
  going through `Sanitizer`.
- **Sanitizer scope.** Deterministic rules remove URLs, bare domains, package names, emails,
  IP addresses, phone numbers, long digit runs and handles. Names are caught by context
  ("with", "call", titles, relations) and by a user-supplied list of private terms. There is
  no NER model and no contacts permission: a model adds size and the permission adds risk.
  Coverage is best-effort, particularly for names in non-Latin scripts and names with no
  context, and the UI should say so.
- **Key handling.** The key is sealed with AES-256-GCM by a non-exportable Android Keystore
  key, in `noBackupFilesDir`. `ApiKey.toString()` is redacted. The key is sent only in the
  `Authorization` header, and neither keys nor prompts are logged.
- **Endpoint and model.** The host is fixed to `api.groq.com` over HTTPS in release builds.
  The default model is `openai/gpt-oss-20b`, because Groq's deprecations page lists older
  Llama IDs as past shutdown. The model is a parameter, and "model unavailable" is its own
  error, because hosted model IDs change often.
- **Model output is untrusted.** Replies must parse into a strict shape or they become a typed
  failure. Schedule suggestions are only shown, and never applied automatically.
- **The gatekeeper fails closed.** No key, no network, a server error or a malformed reply all
  count as a denial, so going offline is not a way out.
- **A gatekeeper grant changes nothing yet.** ADR-0008 says strict sessions cannot be stopped
  early. A persuadable model that could override that would be the easiest bypass in the app,
  so the decision is computed and displayed but not connected to session state.

### Consequences
AI features are optional: blocking and scheduling never depend on them. A sanitizer miss in
free text can still reach Groq, which the design limits by keeping free text short and rare.

### Alternatives Considered
On-device NER model: large, and weak across languages. Sending titles after sanitizing: more
useful suggestions, but it makes the sanitizer the only protection. Storing the key in the
database: the key would then be protected only by the database key, not directly by Keystore.

---

## ADR-0010: Voice commands through Groq Whisper

- **Status:** Accepted
- **Date:** 2026-11-01
- **Deciders:** jackforce

### Context
The user asked for dictated commands to add, remove and mark habits and to change the
schedule, using their Groq key and naming Wispr Flow. Wispr Flow is a separate company's
product and is not hosted by Groq. Groq hosts Whisper speech-to-text.

### Decision
- **Transcription** uses Groq's `whisper-large-v3-turbo` with the user's own key. Using Wispr
  Flow would add a second cloud vendor and a second key for no functional gain.
- **This is a stated exception to the sanitizer rule.** Audio cannot be sanitized on the device,
  so a recording reaches Groq as spoken. The exposure is limited as follows: voice is off until
  the user agrees to a notice, recording only runs while the talk button is held in the open app,
  each recording is capped at 30 seconds and held only in memory, and neither audio nor
  transcripts are stored or logged.
- **Silence never leaves the device.** Whisper invents text for silence (a silent clip returned
  "Thank you."), so an on-device level check rejects it before any upload.
- **A local grammar decides what can happen.** Transcripts go to a deterministic parser. Only if
  that fails, the sanitized transcript goes to the chat model, which may only rewrite it into the
  same grammar. The result goes back through the local parser, so a model reply cannot express any
  action the grammar lacks.
- **Every command needs confirmation.** Real Whisper heard "Add a habit" as "Add a hobbit" in
  testing, so the parsed command is shown first and applied only on a tap.
- **Voice has no extra power.** Removing a habit archives it and keeps its history. A running
  session cannot be cancelled by voice, and while a strict session runs the schedule cannot be
  changed by voice either.
- Times without am or pm resolve deterministically: 7 to 11 mean morning, 12 means noon, and 1 to
  6 mean afternoon.

### Consequences
Dictation needs a network connection and a Groq key, and blocking does not depend on it. The
grammar is English; other languages rely on the model rewrite.

### Alternatives Considered
Android's on-device recognizer: better for privacy, but only on Android 12+ and only where a
language pack is present. It is a good option to add later. Letting the model return actions
directly: more flexible, but its output would then decide what runs.

---

## ADR-0011: Arena challenges, encrypted sync and a blind relay

- **Status:** Accepted
- **Date:** 2026-11-02
- **Deciders:** jackforce

### Context
Phase 4 needs peer challenges that share results and nothing else. ADR-0006 settled what a
daily commitment is. This records how results move between people, given that CLAUDE.md allows
only encrypted relays and rules out cloud servers for core features. No relay exists yet.

### Decision
- **Rules are a pure fold.** Each day is met, missed or tampered. A missed day uses a freeze if one
  is left, otherwise it is a failure and resets the streak. A tampered day in a strict challenge
  is always a failure and cannot be frozen (ADR-0005). More failures than the challenge allows
  forfeits it. Closed days never change, and a clock moved backwards cannot reopen them.
- **A relay is a blind mailbox.** It stores opaque bytes under a mailbox ID derived from the group
  secret, and hands them back by cursor. It holds no keys and runs no challenge logic.
- **Encryption.** An invite carries a random 256-bit group secret. HKDF-SHA256 derives the mailbox
  ID and an AES-256-GCM key. Plaintext is padded to a fixed size, so a success and a failure look
  the same to the relay. The relay can still see network addresses and when messages are sent.
- **Identity.** Each member has a per-challenge Ed25519 key and is known by its public key plus a
  display name they choose, visible to the group only. Android below API 33 has no Ed25519, so
  BouncyCastle's lightweight API is added to `:core:arena`.
- **Group model.** Anyone holding the invite can join and read. There are no admins and no
  member removal, so an invite should be shared like a password.
- **Verification is recomputed, never trusted.** Each member's standing is derived from their
  signed entries. A bad signature is rejected on receipt. A broken chain, an impossible streak or
  two different signed answers for one day mark that member invalid. Salts are revealed when a
  member's challenge ends, which confirms their commitments.
- **Honesty stays self-attested (ADR-0006).** Verification proves results were not rewritten, not
  that they were true, and the leaderboard must not imply otherwise.
- **Automatic criteria stay local.** Screen time and launch checks read usage stats on the device,
  and only the resulting outcome is shared.
- **Sync never affects blocking.** A relay failure leaves results queued and changes nothing else.

### Consequences
Arena needs someone to run a relay. `scripts/arena-relay.py` is a small reference relay for testing
and self-hosting, not a hardened public service. A member who stops reporting is shown as behind,
not failed, because silence cannot be told apart from being offline.

### Alternatives Considered
Direct peer-to-peer: phones are rarely reachable at the same moment, and it exposes addresses to
peers. A third-party service such as Firebase: a Google dependency, against the project's privacy
position. Pairwise encryption: stronger after a member leaves, but much more complex for
small groups of friends sharing one invite.

---

## ADR-0012: Compose UI on tested screen models, achromatic ledger style

- **Status:** Accepted
- **Date:** 2026-11-02
- **Deciders:** jackforce

### Context
Phase 5 replaces the plain setup screen with the product UI. CLAUDE.md routes visual work through
`taste-skill`, which dealt the **achromatic ledger** app style for this brief (seed 1398748691,
`.taste-skill/direction.json`). Screen logic needs tests, and Compose code is slow to test.

### Decision
- **Jetpack Compose with foundation only.** Material 3 is left out, because its components bring a stock
  look the style rules out. A small design system in `ui/` holds the tokens and the few components needed.
- **Screen models live in `:core:presentation`**, plain Kotlin with tests: what each screen shows, its
  sentences, durations, positions and states. Compose only lays them out.
- **Style.** No colour in the chrome. The primary action is near-black and the secondary a grey pill.
  One hue, #1d5fbf, marks each screen's main data element: the running session, today's column and
  your own row. Status colours appear only on status. Tokens follow the sheet, with dark-theme
  values from the skill's defaults.
- **Face.** A blue-grey ground (#f2f4f7) taken from a printed planner's ledger paper, and Geist,
  bundled under the OFL in `res/font` so no font is fetched at runtime.
- **Signature element.** The day strip: today from 06:00 to midnight, with every session as a bar and
  a line at now. It answers what the app is for: when you are protected.
- **Navigation.** A bottom bar with four destinations, Focus, Habits, Arena and Vault, with the
  screen's name as its title. No navigation library: the screens are few and share one state holder.

### Consequences
The look is checked on emulator screenshots against the skill's app critique, because its browser
inspector cannot measure a native screen. Screen-reader labels and touch targets are reviewed by hand.

### Alternatives Considered
Material 3: fast, but its defaults are the stock look. Keeping Android Views: no tooling gain, and
more code for the same screens.

---

## ADR-0013: Linux daemon on the shared Kotlin core; native core deferred

- **Status:** Accepted
- **Date:** 2026-11-02
- **Deciders:** jackforce

### Context
Phase 6 planned to extract the core into Rust and port to Linux, Windows and iOS. ADR-0002 asked for
Rust to be weighed against Kotlin Multiplatform first. The build machine has no macOS, so iOS cannot be
built or tested, and no Windows toolchain, so the Windows Filtering Platform driver cannot either. The
core is plain Kotlin on the JVM, covered by more than 300 tests.

### Decision
- **Linux first, on the JVM.** `:platform:linux` reuses the core modules as they are, so no logic is
  rewritten and every existing test still applies.
- **Choosing a native core is deferred** until iOS work starts. iOS cannot run a JVM, so that is
  when Rust and Kotlin Multiplatform should be compared in a new ADR, against a real iOS constraint.
  The Windows port waits for a Windows build machine.
- **A root daemon and a user CLI.** A systemd service runs the scheduler and enforcement as root. The
  `organiza` command talks to it over a Unix socket at `/run/organiza/control.sock`, which is open to
  members of the `organiza` group only. The daemon refuses to stop a strict session, as on Android.
- **Process blocking.** The daemon scans `/proc` every second and ends a blocked process: SIGTERM, then
  SIGKILL. A target matches the executable's name, `argv[0]`, the 15-character `comm` or a Flatpak app
  ID. **Lockout protection:** it never touches root or system processes, its own process, shells,
  `sudo`, `sshd`, login and display servers, or desktop shells.
- **Website blocking** writes a marked block into `/etc/hosts`, resolving blocked domains and their
  `www.` and `m.` forms to 0.0.0.0. The block is removed when nothing is blocked, and nothing outside it
  is ever changed. Hosts files cannot use wildcards, and browsers using DNS-over-HTTPS skip them. These
  are the same limits as ADR-0005, and they are stated in the CLI's help.
- **Encrypted storage.** The SQLite database uses SQLCipher format through `sqlite-jdbc` from
  `io.github.willena`. Its key is a systemd credential, sealed by `systemd-creds` with the host key and
  the TPM when there is one, and the daemon reads it from `$CREDENTIALS_DIRECTORY`. This protects
  copies and backups of the database. It does not protect against root on the running machine, which
  has the host key.
- **Tamper evidence.** systemd restarts the daemon if it crashes or is killed. Anyone with `sudo` can
  still stop it. A heartbeat file shows a gap during an active session, and the next start records it
  as a `DAEMON` tamper event.
- Schema v4 adds `process` rule targets and the `DAEMON` capability.

### Consequences
Linux gets real enforcement without a second copy of the logic. Window titles and browser URLs are not
read on Linux, so in-browser blocking depends on the hosts file alone. Both platforms share the
database layout, which a future sync or export can rely on.

### Alternatives Considered
Rust core now: a rewrite of tested logic, with no platform that needs it yet. Kotlin Multiplatform now:
JVM-only libraries (BouncyCastle, JDBC, java.time) would need replacing before any Linux work.
An nftables DNS filter: stronger than a hosts file, but much more invasive to a desktop's network.

---

## ADR-0014: Android-only milestone and feature packages

- **Status:** Accepted
- **Date:** 2026-11-02
- **Deciders:** jackforce

### Context
The core engine is done and the Linux daemon works (ADR-0013). The next milestone is a polished,
open-source Android app that the maintainer dogfoods before any other platform gets more work.
External contributors need to find their way around the Android code, where most logic had grown
into one `OrganizaApp` class and one `Screens.kt` file.

### Decision
- **Platform freeze.** Linux, Windows and iOS get no new work until the Android build has been
  dogfooded and the next phase is approved. `:platform:linux` stays in the build and in CI, so it
  keeps compiling against the shared core, but it gets no new features.
- **Feature packages in `:app:android`.** Code is grouped by what the user sees:
  `feature/onboarding`, `feature/blocker`, `feature/sync`, `feature/ai`, `feature/focus`,
  `feature/habits`, `feature/arena`, `feature/vault` and `feature/voice`. Shared Android glue
  (encrypted storage, Keystore) lives in `data`, and the design system stays in `ui`. Logic that
  can be tested off-device stays in the `:core` modules, so a feature package is mostly wiring and
  Compose.
- **Packages, not Gradle modules, for now.** Feature modules would need the single app state holder
  split behind interfaces first. Packages give contributors the same map at a fraction of the
  risk, and can be promoted to modules once their boundaries hold.
- **Release builds** are minified with R8. They are signed with a release key when one is
  configured outside the repository (`CONTRIBUTING.md`), and with the debug key otherwise, so a
  dogfood APK can always be built and installed.

### Consequences
Component class names change (the accessibility service, the VPN service and the device admin move
into `feature/blocker`), so an installed debug build has to be re-enabled after updating. A
debug-signed release APK cannot be upgraded in place by a properly signed one later.

### Alternatives Considered
Feature Gradle modules now: enforced boundaries, but a large refactor of shared state with no
behaviour change to show for it. Leaving the layout as it is: cheaper today, harder for every new
contributor.

---

## ADR-0015: Backend choice: local only, hosted Supabase or self-hosted

- **Status:** Accepted
- **Date:** 2026-11-02
- **Deciders:** jackforce

### Context
Arena challenges need a relay that friends share (ADR-0011), and asking every user to run
`scripts/arena-relay.py` is too much. The product now offers a managed backend, Supabase on
PostgreSQL, while self-hosters and privacy purists bring their own. CLAUDE.md still rules: the
single-player app works offline, no telemetry leaves the device, and Arena shares only encrypted,
padded commitments.

### Decision
- **Three choices, made in onboarding and changeable later.** *Local only* (the default when
  nothing else is configured): no account, and Arena uses a relay URL the user enters. *Organiza
  hosted*: the Supabase project whose URL and anon key are set at build time
  (`organiza.supabaseUrl`, `organiza.supabaseAnonKey`); a build without them shows the option as
  unavailable. *Self-hosted*: the user enters their own Supabase URL and anon key.
- **Two ports in `:core:sync`.** `AuthRepository` signs up, signs in, refreshes and signs out.
  `SyncRepository` reports the backend and hands Arena the relay for a challenge. Each has a
  local-only implementation and a Supabase one, chosen once from the stored `BackendConfig`, so
  screens and business logic never branch on the backend.
- **What the backend stores.** Supabase Auth holds an email address and a password hash. One table,
  `arena_message`, holds the same opaque, padded ciphertext the blind relay holds, under the same
  derived mailbox ID. Habits, sessions, usage, overrides and AI keys never leave the device. The
  schema and its row-level security are in `supabase/migrations`, so a self-hoster applies the
  same file.
- **Access goes through two `SECURITY DEFINER` functions,** `arena_append` and `arena_fetch`. The
  table itself has row-level security on and no policies, so clients cannot list, update or
  delete messages, or read a mailbox without its ID. Both functions require a signed-in user and
  check the sizes.
- **Only the anon key is accepted.** A key whose JWT role is `service_role` is refused in the UI,
  because it bypasses row-level security for every user of that project and must never sit on a
  phone. Raw PostgreSQL credentials are not accepted either: a phone would then hold a database
  password. A self-hoster runs Supabase, or anything that serves the same Auth and PostgREST
  endpoints.
- **Sessions are sealed.** The refresh token is stored with the Keystore-sealed secret store, and
  the access token is kept in memory only.
- **Only POST is used,** for both Auth and the RPC functions, so the existing `HttpTransport` port
  carries it and the fakes stay small.

### Consequences
Hosted Arena needs someone to run and pay for the Organiza Supabase project. Until it exists, the
hosted option is unavailable and self-hosting works. The backend can see account emails, network
addresses, mailbox IDs and message timing, but not results. Signing in is never needed for
blocking, habits or AI.

### Alternatives Considered
Syncing personal data through Supabase: it breaks local data ownership unless end-to-end encrypted,
which is a separate design. Firebase: a Google dependency (ADR-0011). Direct PostgreSQL from the
phone: credentials on the device and no row-level security for anonymous peers.

---

## ADR-0016: Mindful friction: typed-phrase overrides instead of hard lockouts

- **Status:** Accepted
- **Date:** 2026-11-02
- **Deciders:** jackforce

### Context
Until now a blocked app flashed a black overlay and went home, with no way through except ending the
session, and strict sessions could not be ended at all. People who hit a wall with no exit uninstall
the app. Products such as StayFree keep users by asking for a deliberate pause instead. ADR-0005
already set the target at "bypass requires deliberate effort, and is recorded". This decision moves
the everyday experience to that target.

### Decision
- **An intervention screen replaces the flash.** Opening a blocked app or site shows a full-screen
  intervention over the home screen. It cannot be dismissed by back or by tapping outside. It
  offers two ways out: *Stay focused*, always available, or the override path.
- **The override path.** First, a breathing pause that cannot be skipped. Then a reason, chosen from
  a short list with an optional note. Then the user types a reflective phrase drawn from a bundled
  set, matched ignoring case, spacing and punctuation. Pasting is refused. Last, the user chooses
  1, 5 or 15 minutes, gets a pass for that one app or site, and the app reopens.
- **Friction escalates.** The pause starts at 10 seconds and doubles with each override that day, up
  to 2 minutes. Strict sessions start at 30 seconds, need the longer phrases, and, with an AI key,
  show an AI reflection on the stated reason before the phrase (ADR-0017).
- **Passes are narrow and short.** A pass covers one package, one blocked site entry, or the short
  video feed of one app, and ends on time even if the app is open. Passes live in memory, so a
  restart blocks again. A pass for a site also lifts it in the DNS filter for that time.
- **Strict keeps its meaning.** A strict session still cannot be stopped early as a whole, and the
  guard on Organiza's own settings during a strict session still has no override, because it
  protects the enforcement itself.
- **Every outcome is logged locally.** Each intervention records whether the person stayed focused or
  overrode, the target, the reason, the pause and the pass length. Schema v5 adds the
  `intervention_event` table. The Focus screen shows today's choices, and the AI copilot can use the
  counts by hour (ADR-0017).
- **Phrases are original text,** written for Organiza in a stoic spirit, so there is no attribution or
  licensing question, and they are easy to translate.

### Consequences
A determined person can get through any block with two minutes of effort, which is the product's
intent now, and the log makes each time visible. CLAUDE.md's mission line changes from
"unbypassable" to "hard to bypass, mindful by design". The verify script checks the intervention
instead of a plain return to the home screen.

### Alternatives Considered
Keeping hard lockouts with a separate soft mode: two enforcement experiences to explain and test. A
fixed delay with no typing: easy to wait out on autopilot. Allowing paste: defeats the point of
typing.

---

## ADR-0017: AI copilot insights and override reflection

- **Status:** Accepted
- **Date:** 2026-11-02
- **Deciders:** jackforce

### Context
The BYOK features from ADR-0009 (weekly review, schedule suggestion, gatekeeper) each sit behind a
button and answer once. The goal is a background copilot: notice distraction windows, propose focus
blocks around the calendar, suggest habit changes, and help in the moment of an override.

### Decision
- **Local analysis first.** `DistractionWindows` in `:core:ai` finds the hours with the most blocked
  attempts and overrides over the last 14 days, from the local intervention log. It runs without a
  key, so the insight exists even when AI is off.
- **The copilot prompt sends counts, not content:** attempts and overrides per hour of the week,
  focus minutes, habit completion rates with sanitized names, and busy calendar blocks with
  sanitized tags. It never sends app or site names, typed reasons, or usage per app. The reply must
  parse into proposed focus blocks, warnings and habit tips, or it is discarded. Proposals are
  never applied without a tap.
- **The copilot runs at most once a day,** when the app is opened with a key saved, and on demand.
- **Override reflection.** In a strict session with a key, the stated reason is sanitized and sent
  with the minutes left and how many overrides happened today. The model returns a short, neutral
  reflection, which is shown before the phrase. It never decides: a failure or a timeout shows a
  plain local prompt, and the person continues either way. This replaces the gatekeeper's yes or
  no, which the old panel showed as advice only.
- **The sanitizer stays regex-based** (ADR-0009): emails, phone numbers, URLs, card-like numbers,
  handles and capitalised name pairs. There is no on-device NER model, because shipping one would
  add tens of megabytes. Typed reasons are short, and the prompt asks the model not to repeat
  personal details.

### Consequences
Insights get better with a week of use. The copilot sees when someone is distracted, not what with.
Groq's terms still apply to whatever is sent, which the onboarding AI step says.

### Alternatives Considered
Sending per-app usage: more precise advice, but it reveals what someone uses. An on-device language
model: private, but too large for this milestone. Letting the model decide overrides: it would
fail closed or fail open, and both are wrong for a reflection.

---

## ADR-0018: Wireframe redesign: limits, habit plans, schedule import, XP and nudges

- **Status:** Accepted
- **Date:** 2026-11-03
- **Deciders:** jackforce

### Context
The maintainer's wireframe (ten screens) asks for a home dial, weekly and monthly habits with targets
and reminders, an agenda grid, a schedule import hub, a distraction hub with daily limits and
separate Reels and Shorts switches, a "Limit reached" pause, a podium with nudges, and a profile with
levels and badges. Most of these are new features. They must keep the privacy rules: nothing about
usage leaves the phone, and the relay learns nothing new.

### Decision
- **Style.** The achromatic ledger (ADR-0012) stays, with the wireframe's face: crisp ink outlines on
  cards and controls instead of hairlines, a 16dp card and 12dp control radius, one outline icon set
  drawn in code, and the daily-goal dial as the signature element. The ink borders go past
  taste-skill's border limit on purpose, because the brief asks for high-contrast functionalism.
  Colour stays on data: the dial, today and now.
- **Navigation.** Five destinations: Habits, Arena, Home in the middle, Metrics and Profile. The agenda,
  import hub, dictation, new habit and distraction hub open on top with a back stack.
- **Daily limits.** Each app may have a daily foreground limit, from 5 minutes to 3 hours, read from
  UsageStats. Over the limit, the app gets the pause screen as "Limit reached", in or out of a
  session. An override grants a pass as usual. `intervention_event` gains the `limit` kind.
- **Short video feeds.** YouTube Shorts and Instagram Reels each have an always-on switch, applied by
  the same content policy that sessions use. A session's "Shorts and Reels" still means every feed.
- **Habit plans.** A habit is daily, weekly or monthly, with a target of met days per period (always 1
  when daily) and an optional reminder time. Reminders are local alarms and notifications; the
  habit's name appears only on this phone. Only daily habits can join challenges, as before.
- **Schedule import.** A timetable photo is read on the phone with Tesseract (Apache-2.0, through
  JitPack, the only place it is published), with English data from `tessdata_fast` bundled. The
  photo never leaves the phone. Its text, a dictation transcript or pasted text is sanitized and sent
  to the BYOK model, which returns weekly blocks. Nothing is scheduled until the person adds them,
  and only times are stored, never the labels. ML Kit was rejected because it reports usage metrics
  to Google.
- **XP, levels and badges** are computed on the phone from local results: 10 XP per met habit day, 1
  per 5 focus minutes, 5 per time the person stayed focused, 100 per completed challenge. Level n
  starts at 50 n (n - 1) XP. None of it is shared.
- **Challenge XP and nudges.** Friends are the members of a challenge. Their XP is computed from the
  results they already share (10 per met day, 2 per streak day), so nothing new is sent. A nudge is a
  fourth signed message type, padded like the others, so the relay cannot tell it apart. Each member
  can nudge another once a day.
- **Signing.** A dogfood key is checked in at `app/android/debug.keystore`, so every machine and CI
  builds APKs that update each other. `dogfoodApks` re-signs both APKs with v1, v2 and v3 schemes for
  vendor installers. It is a test key and must never sign a store release.
- Schema v6 holds the habit plan columns and the `usage_limit`, `setting` and `arena_nudge` tables.

### Consequences
Limits need usage access; without it they cannot be enforced, and the hub says so. Photo OCR adds
about 9 MB and is weaker on handwriting than cloud OCR. Nudges arrive at the next sync, not instantly.

### Alternatives Considered
Sharing XP with friends: it would reveal focus minutes and habit counts. Google sign-in: deferred, to
keep Google out of the app. Cloud OCR: the image cannot be sanitized.


## ADR-0019: Event-paired usage tracking, a full calendar and Habitica-style tasks

### Status
Accepted, 2026-11-04.

### Context
Usage limits read nonsense: the Android reader closed a stretch on every `ACTIVITY_STOPPED`, and a
stop after a pause added the whole time since midnight, so one 20 s visit counted as 23 hours. We
studied how StayFree (Sensor Tower's usage SDK) reads `UsageStatsManager` events in its shipped APK:
it keeps resume and pause events only, pairs them per app, joins stretches split by a pause and
resume within 3 s, drops stretches under a second, and closes an app still in front at "now". It
caches raw events in a local database and asks only for events since the last one it stored. Its
Reels and Shorts view IDs are not in the APK; they arrive by remote config. The agenda showed only
today and tomorrow, and habits earned XP but nothing could be lost.

### Decision
- **Foreground time** is computed in `core/enforcement` (`ForegroundTime`) from typed `UsageEvent`s.
  An app is in front while any of its activities is resumed, keyed by activity class, so moving
  between its own screens is one stretch with no 3 s heuristic. Stops are ignored. Screen off and
  shutdown close every stretch. Events are read from 3 hours before the window, so an app already
  open at midnight is counted from midnight. The hub reads every app in one pass, and its total over
  launchable apps is the day's screen time (launchers and system UI have no launcher entry).
- **No event cache.** One `queryEvents` call per foreground change of a limited app, and one per hub
  refresh, is cheap next to the accessibility events that trigger it. The system keeps about a week
  of events, which covers every window Organiza reads.
- **Short video detection** keeps Organiza's own view IDs (ADR-0016); no remote parser list, since
  fetching one would be a network call for core enforcement. Snapchat Discover is not covered.
- **App picker.** The hub lists every launchable app with its icon, searchable and grouped by the
  category the app declares (`ApplicationInfo.category`). A limit bar turns red at 80%.
- **Calendar.** Month, week and day views, built in `core/presentation` (`CalendarMonth`, `AgendaModel`)
  with no calendar library. Any day can take a focus block; a block not yet started can be moved or
  removed. Moving adds the new block before removing the old one, so a refused time loses nothing.
  Blocks are scheduled sessions on the focus list, so the scheduler arms them at their start.
  Imported calendar events show but stay read-only.
- **Tasks with health.** Habits (+5 XP or -5 HP per tap), dailies (+10 XP; -8 HP each when undone at
  midnight) and to-dos (+15 XP once). A session override costs 10 HP for that day. Health is 50. At 0
  the hero faints: full health, and XP falls to the start of the previous level. Task XP adds to the
  XP from ADR-0018, so Profile and Habits show one level. Missed days are charged when the app next
  refreshes, at most 7 days back, and each day once (`setting.rpg_settled`).
- Schema v7 adds `rpg_task` and `rpg_event`. Task titles stay on the phone and are never synced.

### Consequences
Limits and the hub now match what the system's own screen time shows to within a second. Damage is
applied when the app next runs, not exactly at midnight. A daily created in the evening is judged
that night.

### Alternatives Considered
kizitonwose/Calendar: a dependency for a grid that is 40 lines here. A remote parser list for
Reels/Shorts (as StayFree does): needs a server. Storing HP and XP as running totals: an event log
lets a daily be unchecked cleanly and keeps a history.

## ADR-0020: Strictness tiers, minute habits, a copilot chat and account deletion

### Status
Accepted, 2026-11-07.

### Context
Sessions blocked one list or nothing, so a person who wanted the whole phone off, or only wanted to
see where time went, had no option. Habits were done or not done, with no way to count reading time
or credit time spent in a reading app. The copilot only produced weekly suggestions, the Profile sync
switch turned on a backend without showing where to sign in, and an account could not be deleted
from the app. The reminder time used the platform's old dialog.

### Decision
- **Tiers** live on the session row (`session.tier`, schema v8), because manual rules are deleted
  when their session ends and a finished monitor session must still be found. Lockdown makes the
  policy an allow list (`BlockList.allowOnly`): its rule's packages are the apps left open, which by
  default is the SMS app; the launcher, dialer, emergency apps and Organiza stay exempt (ADR-0007).
  A lockdown in force wins over standard sessions, and blocks no sites, since browsers are paused.
  Monitor sessions add nothing to the policy and cannot be strict. Calendar and voice blocks stay
  standard.
- **Monitor summary.** The last monitor session of the past day shows its top apps from
  `UsageStatsManager`, on the phone. The optional AI summary receives minutes by app category only.
- **Minute habits.** `habit.goal_minutes` and `habit.linked_package` (v8), with hand-logged minutes in
  `habit_minutes`. A linked app's foreground time is read live on each refresh and added, never
  stored. A day is marked met once the total reaches the goal, or on any minutes without a goal.
- **Copilot chat.** A dedicated screen, opened by a button on every tab. A message the local grammar
  parses becomes a pending command with no AI call. Otherwise it goes to Groq with today's screen time
  by category, focus minutes and sanitized habits, and the reply may carry one grammar line, which
  is parsed locally and waits for Confirm (ADR-0010). The grammar gains `log N minutes of NAME`,
  habits with a frequency word, and meetings: a schedule line naming a meeting, call or appointment
  becomes an event handed to the phone's calendar app with `ACTION_INSERT`, which asks before
  saving, so Organiza needs no calendar permission and stores no title. Turns are kept in memory only.
  The chat uses `openai/gpt-oss-120b`: `llama-3.3-70b-versatile` was asked for but Groq no longer
  serves it.
- **Account deletion.** A `delete_account()` function deletes the caller's `auth.users` row; relay
  messages go with it by cascade. It is `security definer`, uses only `auth.uid()`, and is granted
  to `authenticated` only. The app asks once more, then signs out on success. Data on the phone stays.
- **Dashboard.** Metrics ranks today's apps by time with their icons, loaded off the main thread, and
  a bar against the limit. A tap opens a sheet with one-tap limits and the Reels or Shorts switch.
- **Clock dial.** Material 3 is added for its `TimePicker` only, coloured from the house palette.

### Consequences
Lockdown is as strong as the accessibility service, like every other block. Calendar events are
created by the calendar app, so a phone without one cannot take them. Linked-app minutes need usage
access. The hosted backend needs the new migration before deletion works there.

### Alternatives Considered
A Supabase Edge Function for deletion: a second runtime and a service key for what one SQL function
does. Coil for icons: a dependency for a bitmap that `PackageManager` already gives. Android's
`SpeechRecognizer`: may send audio to a vendor without the consent Organiza already asks for, so the
chat reuses the Groq Whisper path. A rule-level tier: lost with the manual rule when a session ends.

## ADR-0021: Home leads with focus, the blocker moves to Metrics, typed times and named ranks

### Status
Accepted, 2026-11-08.

### Context
Starting a session or seeing the day's plan took a scroll or a second screen. The blocker hub sat
behind a button on the session card, apart from the usage it acts on. Long pages ended in an 80dp
blank kept free for the copilot button. The session length read "3 × 15 min = 45 min", and times
were set with steppers and the Material 3 clock dial, which is slow for "45 minutes" or "08:30".
Habitica-style habits, dailies and to-dos lived only on the Habits tab, and levels had five titles
that changed every few levels with no milestone to aim for.

### Decision
- **Home** opens with the session card, then the calendar (`CalendarPanel`, shared with the full
  calendar; a tapped day opens it), then the dial, today's habits and the same `QuestsPanel` as the
  Habits tab. The header shows the logo instead of an initial.
- **One source.** Both tabs render the one `UiSnapshot` that `refreshUi` builds from the SQLCipher
  stores (ADR-0018), so a task added on either tab shows on both after the same refresh. No
  per-screen flows or view models are added.
- **Metrics** holds the blocker hub (`BlockerHub`) under the usage dashboard. The hub route is gone;
  the start form's link to the focus list switches to the Metrics tab.
- **Page end.** Content ends with the 16dp margin. The copilot button fades out within 24px of the
  end of a page, instead of every page carrying blank space for it.
- **Typed times.** `MinutesField` (a number-pad box with one-tap presets) and `ClockField` (hour and
  minute boxes, AM and PM when the phone keeps a 12 hour clock) replace the steppers and the dial for
  session length, limits, habit goals, logged time, calendar blocks and reminders. The first digit
  after a tap replaces the box, so typing never appends to the old number. Parsing and the
  first-keystroke rule live in `TimeInput` (core/presentation) with tests. Material 3 is removed.
- **Limits** are kept as typed, 5 to 720 minutes as the schema already allowed, instead of snapping
  to slider steps. A typed limit is saved when its sheet closes or on Save limit, so typing "120"
  never passes through a 12 minute limit.
- **Ranks.** Level n starts at 25 n (n - 1) XP, half the old curve, so early levels come in days.
  Ranks: Novice 1, Initiate 10, Focus Adept 20, Stoic Master 30, Zen Grandmaster 50. The hero card
  shows the rank, a level badge, a five-step ladder and the XP to the next rank.
- **Arena over Supabase, end to end.** `ArenaSyncE2ETest` runs two phones through sign-in, create,
  join, a closed day, commitments and reveals over real HTTP. It always runs against `LocalSupabase`:
  the Auth password endpoints and PostgREST's rpc route over embedded PostgreSQL with the real
  migrations. With `ORGANIZA_E2E_*` set, the same test runs against a hosted or self-hosted project.

### Consequences
Home is longer, with the habits below the calendar. Lowering the curve raises existing heroes a few
levels. `LocalSupabase` stands in for GoTrue and PostgREST, so a change in either is only caught by
the configured run, which needs two accounts on that project.

### Alternatives Considered
A wheel picker: slower than typing for a known number, and fiddly with large fonts. Keeping the
spacer and shrinking it: still blank space. A `Flow` per screen: a second state path beside the
snapshot, for data the snapshot already shares. The Supabase CLI stack for the E2E test: needs
Docker on every machine that runs `./gradlew test`.

## ADR-0022: Home opens with the daily goal, then the session, the week and the habits

### Status
Accepted, 2026-11-08. Amends the Home order in ADR-0021.

### Context
After ADR-0021, Home led with the session card and a full month grid, and the daily goal dial sat
below both, out of sight without a scroll. The month grid alone took most of a phone screen, and
the hero card repeated what the Habits tab already shows.

### Decision
- **Order.** A header (the logo, a greeting for the hour, today's date), then the daily goal, the
  session card, the week, today's habits, and the quests. Each sits in its own `Panel`, spaced by
  `Space.gap` as on every tab.
- **Daily goal** is a panel with a 128dp dial beside the goals met and what is left ("1 goal left
  today", "Every goal met today"). `HomeModel` builds the greeting, date and these lines from the
  hour and date it is given, so they are tested in core.
- **This week.** `WeekPanel` shows the chosen day's week as one row of days and today's blocks still
  to come, up to three. A tapped day, or Open calendar, opens the full calendar, which keeps the
  month, week and day views (`CalendarPanel`).
- **Quests on Home** are the same lists as the Habits tab under a "Quests" heading, without the hero
  card (`QuestsPanel(hero = false)`); the rank stays on the Habits tab.
- **Blocker settings stay in Metrics.** Home only offers the session's own switches in the start
  form: strict, and short videos for that one session.

### Consequences
The dial, the header and the session card fit a standard phone screen without a scroll. Changing
months or weeks takes a tap into the full calendar.

### Alternatives Considered
Keeping the month grid on Home: it pushes the habits a full screen down. A horizontal scrolling day
strip: hides days off screen, where a seven-day row fits at every width tested.

## ADR-0023: Linux desktop through a shared Compose Multiplatform UI

### Status
Accepted, 2026-11-08. Lifts the Linux part of the platform freeze in ADR-0014.

### Context
The Android app has been dogfooded, and Linux (Ubuntu/Debian) is the next target, with the same
screens and the same behaviour. Every screen lived in `:app:android`, but almost none of it used
Android: the Compose code reads only a `UiSnapshot` and calls `Actions` (ADR-0012), and every
decision already lives in the plain Kotlin/JVM `:core` modules.

### Decision
- **`:app:shared`** is a Kotlin Multiplatform module with an Android library target and a `desktop`
  JVM target. `commonMain` holds the design system (`org.organiza.ui`), every screen
  (`org.organiza.feature.*`), `UiSnapshot`, `Actions` and `OrganizaRoot`, the tab and screen stack
  that `MainActivity` used to own. It uses Compose Multiplatform 1.11, which resolves to the androidx
  Compose that the app's BOM already brings on Android.
- **Four platform calls** go through `expect`/`actual`: the Geist font (an Android font resource;
  the same file on the desktop classpath), whether the clock is 24 hour, the back handler (a no-op on
  the desktop, where each screen has its own back button) and an installed app's icon.
- **`:core` stays plain Kotlin/JVM.** Android and the desktop are both JVMs, so `commonMain` uses the
  core modules and `java.time` directly. Rewriting core for non-JVM targets (kotlinx-datetime,
  SQLDelight or Room KMP instead of the `Database` port) would buy nothing for Linux, and SQLCipher
  stays behind the existing `Database` port, which the desktop can back with the encrypted SQLite
  JDBC driver already used in tests.
- **`:app:desktop`** is the Compose Desktop application: `./gradlew :app:desktop:run` opens the
  window, `:app:desktop:packageDeb` builds a Debian package. Its `DesktopApp` is the desktop state
  holder. In this first stage it only draws the screens and answers every action with a "Not on
  Linux yet" message; storage, sync, AI, usage tracking and enforcement come in the next stages.
- **Android-only code stays in `:app:android`** under `org.organiza.android`: the state holder,
  controllers, services, receivers and Keystore adapters, so the manifest and the enforcement path
  are untouched.
- `:platform:linux` stays frozen until the desktop daemon replaces it.

### Consequences
A screen is written once and drawn on both platforms. The desktop build and its UI test run with
`./gradlew test`. The desktop app does nothing useful until its state holder is wired, so it is not
released before then. Compose Multiplatform 1.12 needs compileSdk 37 and AGP 9.1, so moving to it
waits for that toolchain update.

### Alternatives Considered
Converting every `:core` module to Kotlin Multiplatform: a rewrite of tested code for targets that
are not planned. A separate desktop UI: two copies of every screen to keep alike by hand.

## ADR-0024: Usage tracking and app icons on the Linux desktop

### Status
Accepted, 2026-11-09. Stage 2 of the Linux port (ADR-0023).

### Context
Linux has no usage log like Android's UsageStatsManager, so the desktop app has to watch which app
is in front and keep that record itself. No portable way exists to ask: X11 publishes the active
window, but on Wayland every compositor keeps it private. GNOME, the default on Ubuntu and the
desktop this is developed on, reveals it only to code running inside GNOME Shell.

### Decision
- **App identity is the desktop file ID** (`org.gnome.Nautilus`, `firefox_firefox`), which also
  gives the name, icon and category. Most IDs are not valid `PackageName`s (`google-chrome`,
  `code_code`), so `LinuxApps.packageName` maps them deterministically: an ID that already fits is
  kept, and any other ID gets a `linux.` prefix, with each character outside `[A-Za-z0-9_.]` turned
  into `_`. The core modules (`ForegroundTime`, limits and, in Stage 3, `BlockEngine`) then work
  unchanged.
- **Focus sources:**
  - On GNOME (Wayland or X11), a bundled shell extension, `organiza-focus@organiza.org`, reports
    `Shell.WindowTracker`'s focus app. It answers `Current` and emits `Changed` on the D-Bus
    connection that already owns `org.gnome.Shell`, and the app reads that through `gdbus`.
    GNOME turns extensions off on the lock screen, and the extension then reports that nothing is
    in front, so time spent locked or suspended is not counted. "Allow usage access" installs and
    enables the extension. It starts after the next login.
  - On other X11 desktops, `xprop -spy` reports the active window. Its `_GTK_APPLICATION_ID` or
    `WM_CLASS` is matched to a desktop entry.
  - Windows without a desktop entry never count, as with launchers on Android.
  - wlroots, Hyprland and KDE Wayland have no source yet. A source is one small class when someone
    needs one.
- **A local focus log.** Table `focus_change` (schema v9) stores the time and the app now in front,
  with no app when nothing is. `FocusLog` turns it back into `UsageEvent`s for `ForegroundTime`.
  Rows are written only on a change, and window titles and URLs are never read. Nothing in the log
  feeds sync.
- **Desktop storage** is SQLCipher through the encrypting JDBC driver, at
  `$XDG_DATA_HOME/organiza/organiza.db`. Its 32-byte key lives in the login keyring through
  libsecret, which a small `python3` + `gi` helper calls. The key goes over stdin and stdout, never
  argv. A database whose key is missing is never replaced.
- **Icons** come from each entry's `Icon=` key. An absolute path is used as it is. A name is looked
  up in the hicolor theme and `/pixmaps` under each XDG data directory. PNG is decoded by Skia, and
  SVG is drawn into a bitmap.
- Tracking runs while the desktop app is open. The Stage 3 daemon takes it over.

### Consequences
The metrics and blocker screens show real per-app minutes on Linux. The desktop needs `python3-gi`
and `gir1.2-secret-1`, which Ubuntu installs by default. On GNOME, tracking starts only after one
re-login. If the app crashes, the stretch that was open is dropped rather than counted until the
next start.

### Alternatives Considered
- `org.gnome.Shell.Eval`: disabled since GNOME 41.
- Polling `xdotool`: blind to Wayland windows.
- A JVM D-Bus library or JNA for libsecret: a new dependency to replace two short subprocess calls.
- Widening `PackageName`: desktop IDs can contain spaces, and Android input validation would get
  weaker.

## ADR-0025: One shared app core, and blocking on the Linux desktop

### Status
Accepted, 2026-11-09. Stages 3 and 4 of the Linux port (ADR-0023, ADR-0024).

### Context
The desktop drew every screen but did nothing behind them. Habits, quests, sessions, the calendar,
Arena, sync and the copilot all lived in Android's `OrganizaApp` and its controllers, although almost
none of that code used Android. Copying it into the desktop would give two state holders to keep
alike by hand.

### Decision
- **`OrganizaCore`** (`:app:shared`, `org.organiza.app`) is the old `OrganizaApp` without Android:
  - It holds storage, sessions, the screen snapshot and every feature controller. The controllers
    moved with it: hub, schedule, calendar, quests, chat, copilot and sync.
  - **`Platform`** supplies what each OS does differently: preferences, the database, installed
    apps and their names, granted capabilities, reminders, the site blocking layer, the UI thread
    and logging.
  - **`CoreActions`** maps every screen action onto the core. A platform adds only its pickers,
    dialogs, settings screens and microphone.
  - Android's `OrganizaApp` is now the Android `Platform`, and `MainActivity` supplies Android's
    actions.
- **Permissions by device.** `Device.PHONE` and `Device.DESKTOP` list the onboarding permissions and
  protection rows each offers. The desktop asks for:
  - usage tracking, which is required for blocking;
  - starting at login;
  - keeping running in the top bar;
  - a copilot shortcut.
- **Focus sources are probed in order:** the GNOME extension (Wayland or X11), Hyprland's event
  socket, Sway's IPC, then X11 through `xprop`. Each reports the app and its window's process. KDE
  on Wayland has none yet, and onboarding says so.
- **Background running.** The window process is the background service. Starting at login installs
  a systemd user unit tied to `graphical-session.target`, with `Restart=on-failure`, so a killed
  Organiza comes back. A Unix socket in `$XDG_RUNTIME_DIR` keeps one instance per login: a second
  start shows the window, or opens the copilot when given `--copilot`. Super+Shift+Space is a GNOME
  custom shortcut that runs that command.
- **The desktop pause.** The shared `BlockEngine` runs on the UI thread, fed by the focus source.
  - A block opens the shared intervention in a full-screen, always-on-top, undecorated window.
  - The blocked app's processes are stopped with SIGSTOP. Stopping keeps their memory, so no work
    is lost.
  - They continue (SIGCONT) when the person overrides, when the session's policy changes, when
    Organiza quits, and at the next start. The frozen process IDs are kept in a file for that.
  - The desktop session's own processes are never stopped: the shell, Xwayland, the compositor and
    Organiza with its parents.
- **Dictation** uses Java Sound, which reaches PipeWire and PulseAudio through ALSA.

### Consequences
Every feature works on the desktop with the code Android runs, and a change to one platform's
behaviour is a change in one place. Website blocking on the desktop waits for a root helper that can
edit `/etc/hosts`; until then sites are blocked only on Android. Reading timetable photos on the
desktop needs the `tesseract` command.

### Alternatives Considered
- A separate root daemon for app blocking (the frozen `:platform:linux`): it cannot see which app
  is in front on Wayland, and it splits state across two processes.
- Killing blocked apps instead of stopping them: that loses unsaved work.
- Closing their windows instead: Wayland gives other clients no way to do that.

## ADR-0026: The frozen Linux daemon is removed

### Status
Accepted, 2026-11-09. Supersedes ADR-0013 and the freeze in ADR-0014.

### Context
`:platform:linux` was a root daemon that blocked apps by ending processes and sites through
`/etc/hosts`. It was frozen until a desktop app could replace it. The desktop app (ADR-0025) now
tracks usage, blocks apps and keeps its own encrypted database, so the daemon was a second copy of
the database code that nobody ran.

### Decision
Remove `:platform:linux`, its root installer in `packaging/linux` and its VM check
`scripts/verify-linux-vm.sh`. The desktop app is the only Linux build.

### Consequences
About 1,300 fewer lines to build and test. Website blocking on the desktop still needs a root
helper for `/etc/hosts`; the daemon's `HostsFile` stays in git history as a starting point.

## ADR-0027: Windows 11 desktop on the shared core

### Status
Accepted, 2026-11-09. Windows is the third platform; the roadmap to v1.0 approves the phase that
the mission statement held back.

### Context
The desktop app (ADR-0023, ADR-0025) is plain JVM Compose with a `Platform` of its own. Most of it
already runs on any desktop. What is Linux-specific is how it learns the app in front, how it lists
apps and their icons, how it stops a blocked app, where it keeps secrets and data, and how it starts
at login.

### Decision
- **One desktop module.** `:app:desktop` serves Linux and Windows, choosing per OS at start
  (`WINDOWS` in `LinuxApps.kt`). No new module: the windows, screens, pause and tray are the same.
- **Apps** are the Start menu's shortcuts (all users and this user) that point at an `.exe`, read
  through `FileSystemView.getLinkLocation`, which asks the Windows shell. An app's ID is its program
  file name in lower case (`Discord.exe` is `discord`), the name its window's process carries; in
  core it is `windows.discord`. Uninstallers are skipped.
- **Icons** come from the shell with `FileSystemView.getSystemIcon(file, 96, 96)`, which returns
  the program's own high-resolution icon.
- **The app in front** is `GetForegroundWindow` and `GetWindowThreadProcessId` from `User32`
  through JNA, polled twice a second; the process's program file names the app.
- **The pause** is the same full-screen, always-on-top, undecorated window as on Linux. The blocked
  app's processes are suspended with `NtSuspendProcess` and resumed with `NtResumeProcess`. Windows
  counts suspensions, so a process is suspended only once while it is frozen. Explorer, the window
  manager, `ApplicationFrameHost` and the other session processes are never suspended.
- **Secrets** (the database key, the Groq key) are sealed with DPAPI, which only this Windows
  login can open, one file each next to the database in `%LOCALAPPDATA%\Organiza`.
- **Starting at sign-in** writes the per-user `Run` registry key with `--background`.
- **Reminders** are tray notifications. The installer is an MSI from `jpackage`, built by a Windows
  CI job, which also runs the tests that call Windows itself.
- **JNA** (`jna-platform`) is the one new dependency, for `User32`, `ntdll`, DPAPI and the registry.

### Consequences
Windows gets usage tracking, limits, sessions, the pause, the tray, autostart and every shared
screen. Store (UWP) apps show their frame host as the app in front, so they are neither tracked nor
blocked by name. A suspended app's window turns into Windows' "Not responding" ghost behind the
pause. Website blocking waits, as on Linux, for an elevated helper. A killed Organiza is not
restarted until the next sign-in.

### Alternatives Considered
- `SetWinEventHook` for focus changes: no polling, but it needs a thread with a message loop. Worth
  it if polling ever shows in battery use.
- Killing blocked apps: loses unsaved work (ADR-0025).
- Windows Credential Manager for secrets: limits each secret to 2.5 KB and adds no protection over
  DPAPI for one user.
