# Troubleshooting

The places where Android, Linux, Windows or a backend get in the way, and what to do about each.
If something here does not match what you see, open an issue with your device and version.

## Android

### Play Protect blocks the install

**Why.** Organiza is installed from a file, not from the Play Store, and it uses an accessibility
service, which lets an app see which app is open. Banking malware abuses that same permission, so
Play Protect is wary of any sideloaded app that asks for it. Organiza uses it only to see which app
is in front, to read web addresses in browsers, and to draw the pause screen; nothing is stored off
the device or sent ([ADR-0005](ADR.md)). You can read every line of it in this repository.

**What to do.**

1. When Play Protect says the app is harmful or unknown, tap **More details**.
2. Tap **Install anyway**.

In some countries Play Protect refuses sideloaded apps that ask for accessibility outright when
they are opened from a browser or file manager. Installing over USB is not affected:

```bash
adb install -r organiza-release.apk
```

Only install an APK from this repository's releases, or one you built yourself.

### "Restricted setting" when turning on App blocking

**Why.** Since Android 13, accessibility for apps installed outside a store is locked until you
unlock it for that app. Organiza's switch in **Accessibility** is greyed out, and Android shows
"Restricted setting".

**What to do.** Onboarding shows these steps with a button for step 2.

1. In Organiza, tap **Allow** next to **App blocking** and try to turn Organiza on once. Android
   greys it out; this first try is what unlocks the next step.
2. Open **Settings** > **Apps** > **Organiza** (or **Open App info** in Organiza), tap the
   **three dots** in the top corner, and choose **Allow restricted settings**.
3. Come back to Organiza and tap **Allow** again. The switch now turns on.

If **Allow restricted settings** is missing from the menu, repeat step 1: Android only offers it
after a blocked attempt.

### Install errors

`adb install` prints the reason, which the phone's own "App not installed" message hides.

| Error | Cause | Fix |
|-------|-------|-----|
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | An Organiza signed with another key is installed, for example one built on another computer. Android only updates an app with the same signature | `adb uninstall org.organiza.app`, then install again. This deletes that copy's data |
| `INSTALL_FAILED_VERSION_DOWNGRADE` | The installed copy has a higher version code | Add `-d`: `adb install -r -d organiza-release.apk` |
| `INSTALL_FAILED_OLDER_SDK` | The phone runs Android 7 or older | Not supported; Organiza needs Android 8.0 |
| `INSTALL_PARSE_FAILED_NOT_APK`, or "package appears to be invalid" | The file was damaged or cut short in transfer | Compare `sha256sum` of the file on both ends, and copy it again |
| `INSTALL_FAILED_USER_RESTRICTED` | On Xiaomi and some others, installing over USB is a separate switch | Turn on **Install via USB** in Developer options |
| Blocked by Play Protect | See [above](#play-protect-blocks-the-install) | **More details** > **Install anyway**, or install over USB |

None of these can be fixed from inside Organiza: Android checks the signature, the version and the
SDK level before any of the app's code runs. Minifying the release build (R8) cannot cause them
either; it only changes the code inside the APK.

### Sessions start late or blocking stops

Some phones put background apps to sleep. Allow **Keep running** in Organiza, and look for
Organiza under background activity, auto-start or battery in your phone's Settings.

## Linux

### No usage is tracked on GNOME

Usage tracking on GNOME needs Organiza's small shell extension, which GNOME loads only at login.
Allow usage tracking in Organiza, then log out and back in once. Check that **Organiza Focus** is
on in the **Extensions** app.

### No tray icon

GNOME shows tray icons only with the AppIndicator extension, which Ubuntu includes. On other GNOME
systems, install **AppIndicator and KStatusNotifierItem Support** and log in again. KDE on Wayland
cannot report the app in front yet, so tracking does not work there.

## Windows

### SmartScreen warns about the installer

The installer is not code-signed yet. Choose **More info** > **Run anyway**. It installs for your
user only and needs no administrator.

### A program is not tracked

Organiza knows programs from their Start menu shortcuts. A program started from a folder with no
shortcut, a Store app, or a program running as administrator is not seen.

## Self-hosting

### Getting a Project URL and anon key

**On your own machine or server, with Docker or Portainer** (the full guide is
[`docs/SELF_HOSTING.md`](docs/SELF_HOSTING.md)):

1. In `supabase/self-host`, run `./make-keys.sh https://organiza.example.com` with the https
   address your devices will use.
2. It prints the two values to enter in Organiza under **My own Supabase**:
   ```
   URL: https://organiza.example.com
   Key: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9....
   ```
3. Run `docker compose up -d`, then the schema from the repository root:
   ```bash
   docker exec -i organiza-db-1 psql -U supabase_admin -d postgres -v ON_ERROR_STOP=1 < supabase/setup.sql
   ```

Lost them? The URL is `PUBLIC_URL` and the key is `ANON_KEY` in `supabase/self-host/.env`.

**On supabase.com:** **Project Settings** > **API Keys**. Copy the project URL and the
**publishable** key (`sb_publishable_...`) or the legacy **anon** key
([`docs/supabase-setup.md`](docs/supabase-setup.md)).

### "The project URL must start with https://"

Phones and computers only accept an https address, so a password cannot travel in the clear. A
stack on `http://localhost` works only from the same computer in a debug build. For your devices,
give the stack a domain (Caddy gets a certificate) or put it behind your reverse proxy.

### "That is a secret key"

You pasted the `service_role` or a `sb_secret_` key. That key bypasses row-level security for
every user, so Organiza refuses it. Use the anon or publishable key.

### Sign-in works but challenges or shared limits do not sync

The schema is missing or PostgREST has not seen it. Run `supabase/setup.sql` again (it is safe to
repeat), then `docker restart organiza-rest-1`. Check that `curl https://your-address/auth/v1/health`
answers.
