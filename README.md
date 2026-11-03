# Organiza

<img src="docs/brand/icon.svg" width="72" alt="" align="right">

Focus enforcement for Android that is hard to bypass and mindful by design. Your data stays on
your phone.

- **Sessions** block apps, websites and short video feeds, by hand or from calendar events tagged
  `#focus`.
- **Mindful friction.** A blocked app shows a pause screen instead of a wall. To open it anyway
  you breathe for a moment, say why, type a short phrase, and choose 1, 5 or 15 minutes. Every
  choice is logged on the phone so you can see when and why focus broke (ADR-0016).
- **Habits and Arena.** Daily habits, and 30-day challenges with friends that share only encrypted
  results (ADR-0011).
- **Bring your own AI.** With your own Groq key, a copilot proposes focus blocks around your
  calendar from the hours you get distracted, never from which apps (ADR-0017).
- **Local first.** SQLCipher storage with a key held by Android Keystore. No telemetry. An account
  is only needed for challenges, on the hosted backend or your own Supabase (ADR-0015).

Android is the only platform in active development. A Linux daemon exists in `platform/linux` and
is frozen until the Android build is approved (ADR-0014).

## Install a dogfood build

```bash
./gradlew :app:android:assembleRelease
adb install -r app/android/build/outputs/apk/release/android-release.apk
```

Without a configured release key the APK is signed with the debug key. Android 13 and later may
ask you to "Allow restricted settings" in Organiza's App info before the accessibility service can
be turned on, because the app does not come from a store. If the install fails, see
[`docs/install.md`](docs/install.md).

Screenshots of every screen are in [`docs/screenshots`](docs/screenshots).

## How it works

| Layer | What it does |
|-------|--------------|
| Accessibility service | Sees which app is open, reads browser address bars, and draws the pause screen |
| Local VPN | A DNS filter on the phone. Blocked sites get no answer, and no traffic leaves through it |
| Device admin | Optional uninstall protection |
| SQLCipher database | Sessions, habits, rules and the log of choices, encrypted at rest |

Nothing here is unbypassable: safe mode, revoking a permission or uninstalling all work, and
losing a permission during a session is recorded. ADR-0005 explains the threat model.

## Documentation

- [`ADR.md`](ADR.md): every architectural decision and its reasons.
- [`CONTRIBUTING.md`](CONTRIBUTING.md): building, testing and proposing changes.
- [`docs/schema.md`](docs/schema.md): the local database.
- [`docs/supabase-setup.md`](docs/supabase-setup.md): the backend schema, authentication settings and keys.
- [`docs/install.md`](docs/install.md): installing an APK and what install errors mean.

## License

MIT. Geist is bundled under the SIL Open Font License (`docs/licenses`).
