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
