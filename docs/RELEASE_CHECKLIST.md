# Organiza 1.0 release checklist

Tick each line before publishing. The automated part was last run on 2026-11-18 at commit `02dbc05`.

## 1. Automated checks

- [x] `./gradlew test`: every JVM test passes
- [x] `./gradlew :app:android:connectedDebugAndroidTest`: every Compose and reminder test passes on the emulator
- [x] `scripts/verify-android.sh`: 105 of 105 enforcement checks pass on the emulator
- [x] The minified dogfood build installs fresh on the emulator and opens onboarding without a crash
- [x] The packaged Linux app starts and runs with no errors
- [ ] CI is green on GitHub for the commit you release, including the Windows job

## 2. Signing key (once, and keep it forever)

The checked-in key is public. Only an APK signed with your own key may be published (CONTRIBUTING.md).

- [ ] Make the key: `keytool -genkeypair -keystore ~/organiza-release.jks -alias organiza -keyalg RSA -keysize 4096 -validity 10000`
- [ ] Put the four `organiza.release.*` lines in `~/.gradle/gradle.properties`, never in the repository
- [ ] Back up the `.jks` file and its passwords somewhere safe and offline. Losing it means no update can ever reach installed phones again

## 3. Backend

- [ ] Run `supabase/migrations/20261118120000_relay_retention.sql`, then `20261124120000_relay_retention_all.sql`, in the hosted project's SQL editor (ADR-0034)
- [ ] Self-hosted: run `supabase/setup.sql` again (`docs/SELF_HOSTING.md`, step 3)
- [ ] Check: `select prosrc like '%m.created_at < now() - interval ''15 days''%' and prosrc not like '%m.mailbox = p_mailbox and%' from pg_proc where proname = 'arena_append';` returns `true`

## 4. Build the installers

- [ ] Android: `./gradlew :app:android:releaseApk` writes `dist/organiza-release.apk`
- [ ] Check the signer is yours: `apksigner verify --print-certs dist/organiza-release.apk` must not say `Organiza Dogfood`
- [ ] Linux: `./gradlew :app:desktop:packageDeb` writes `app/desktop/build/compose/binaries/main/deb/organiza_1.0.0-1_amd64.deb`
- [ ] Windows: download the `organiza-msi` artifact from the green CI run
- [ ] Write checksums of the three installers into `SHA256SUMS` with `sha256sum`

## 5. Android, on your own phone

A phone with a dogfood build must uninstall it once before installing the release, because the key
differs. That erases the phone's Organiza data.

- [ ] Install `organiza-release.apk`. If Play Protect warns, follow `TROUBLESHOOTING.md`
- [ ] Onboarding: the lockout step is shown and off by default; every permission step opens the right settings page
- [ ] Android 13 and later: **Allow restricted settings**, then App blocking turns on
- [ ] Focus session: start 5 minutes with Instagram on the list; opening Instagram shows the pause screen
- [ ] Daily limit: set 1 minute on an app; after a minute the app is paused
- [ ] Blocker tab shows the Instagram and YouTube icons; the Reels and Shorts switches pause only those feeds
- [ ] Lockout on: raising a limit, ending a session, moving a focus block and unlinking devices each ask for the essay; a paste is refused and a wrong letter is not taken
- [ ] Habits: adding from the Habits tab and from Home opens the same form
- [ ] Notes: a typed note saves under Today; a dictated note fills the text box; Organize with AI files it under a subject
- [ ] Notes: Delete asks for a second tap
- [ ] A note reminder fires at its time, and the locked screen shows only "Organiza", never the note
- [ ] Metrics: Allow usage access, then Day, Week, Month and Range show time per app and per category; Earlier and Later move the range and never pass today
- [ ] Copilot: with your Groq key, About me is saved and the answers use it; with the key removed, it says so
- [ ] Profile: the XP and level wording reads clearly
- [ ] Errors: a wrong password, airplane mode during sign-in, and a wrong Supabase URL each show a sentence, not a code

## 6. Linux, on your own computer

- [ ] Quit the older Organiza that is running, then `sudo apt install ./organiza_1.0.0-1_amd64.deb`
- [ ] Top bar: the Organiza icon shows GNOME's own menu with Open Organiza, Copilot and Quit
- [ ] After a reboot with automatic login: Organiza says the keyring is locked; after unlocking it, Habits and Metrics work within 30 seconds without a restart
- [ ] Habits tab adds and checks off a habit
- [ ] Metrics: turning on app tracking shows one message, and time appears after using a few apps
- [ ] Focus session: a blocked app is paused, and Stop session asks for the essay when the lockout is on
- [ ] Notes and copilot work as on Android

## 7. Windows

- [ ] Install the MSI; Organiza starts, sits in the notification area, and starts again after signing in
- [ ] A focus session pauses a blocked app; Metrics shows app time

## 8. Across devices

- [ ] Sign in with the same account on the phone and the computer
- [ ] Turn on **Usage across devices** on one, copy the device code, and paste it on the other
- [ ] Within 20 minutes, Metrics on each device shows the other's time with **All devices** on
- [ ] A daily limit on an app both devices have counts the time from both

## 9. Publish

- [ ] Tag the release commit: `git tag -a v1.0.0 -m "Organiza 1.0.0"`, then `git push origin v1.0.0`
- [ ] Create the GitHub release from the tag, and attach the APK, the `.deb`, the MSI and `SHA256SUMS`
- [ ] Open the site and the README install links and make sure each one downloads

## Known limits in 1.0

- Android keeps per-app usage for about a week, so Month and Range fill in from the day 1.0 is installed.
- On Windows, a reinstall gives the computer a new device id. Its old reports count as another device until the relay forgets them, at most 15 days.
- The lockout cannot stop an uninstall, or the removal of Organiza's permissions in system settings.
