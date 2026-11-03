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

| Error | Cause | Fix |
|-------|-------|-----|
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | An Organiza signed with another key is installed, for example one built on another computer | `adb uninstall org.organiza.app`, then install again. This deletes that copy's data |
| `INSTALL_FAILED_VERSION_DOWNGRADE` | The installed copy has a higher version code | Add `-d`, as above |
| `INSTALL_FAILED_OLDER_SDK` | The phone runs Android 7 or older | Not supported |
| `INSTALL_PARSE_FAILED_NOT_APK`, or "package appears to be invalid" | The file was damaged or cut short in transfer | Compare `sha256sum android-release.apk` on both ends, and copy it again |
| `INSTALL_FAILED_USER_RESTRICTED` | On Xiaomi and some others, installing over USB is a separate switch | Turn on **Install via USB** in Developer options |
| Blocked by Play Protect | Play Protect does not know the app or its signer | Choose **More details** > **Install anyway**. In some countries Play Protect refuses sideloaded apps that ask for accessibility outright when they come from a browser or file manager; installing over adb is not affected |

R8 cannot cause an install failure: it only changes the code inside the APK, and Android checks
the manifest, the signature and the SDK level before any of that code runs. The release APK keeps
every component the manifest names.

## After installing

Android 13 and later restrict accessibility for apps installed outside a store. If **App
blocking** cannot be turned on, open **Settings** > **Apps** > **Organiza**, tap the menu in the
corner, choose **Allow restricted settings**, and try again. Onboarding explains this too.
