# Installing a dogfood build

Two APKs come out of each build. Both are signed with the same key, so you can switch between them
without uninstalling:

| File | Built with | Use it to |
|------|------------|-----------|
| `dist/organiza-release.apk` | `./gradlew :app:android:dogfoodApks` | Dogfood: minified with R8, about 40 MB |
| `dist/organiza-debug.apk` | the same task | Rule out R8: not minified, debuggable, about 50 MB |

Both need Android 8.0 (API 26) or later and contain native code for `arm64-v8a`, `armeabi-v7a`,
`x86` and `x86_64`; most of their size is the on-phone OCR engine. Both are signed with the
checked-in dogfood key, `app/android/debug.keystore`, with v1, v2 and v3 signatures, so a build
from any machine or CI updates the one on your phone. An Organiza installed before November 2026
was signed with a different key: uninstall it once.

## Install over USB

Turn on **Developer options** and **USB debugging** on the phone, connect it, and allow the computer
when asked. Then:

```bash
adb devices
```

```bash
adb install -r -d dist/organiza-release.apk
```

`-r` replaces an installed copy and `-d` allows a lower version code. If it fails, adb prints the
reason, which the phone's own "App not installed" message hides.

## What the errors mean

The install errors, Play Protect and the Android 13 "Restricted setting" message are explained in
[`TROUBLESHOOTING.md`](../TROUBLESHOOTING.md).

## After installing

Android 13 and later restrict accessibility for apps installed outside a store. If **App
blocking** cannot be turned on, open **Settings** > **Apps** > **Organiza**, tap the menu in the
corner, choose **Allow restricted settings**, and try again. Onboarding explains this too.
