# Stage 3 - Complete the Platform Release Tracks

## Objective

Close the native gaps for Android, Linux, Windows and iOS. Treat these as separate tracks sharing the verified Flutter core, not as one simultaneous release.

## Track A - Android

### Current state

- Flutter Android scaffold is present.
- `usage_stats ^1.3.1` is present.
- `AppUsageService` queries Android aggregate foreground usage.
- `AndroidManifest.xml` currently lacks `PACKAGE_USAGE_STATS`.
- Release builds currently use debug signing.
- Application ID is `app.organiza.organiza`.

### Work

- [x] Complete Stage 0 identity, permission and signing tasks.
- [x] Verify target/compile SDK against the current Play requirement at release time.
- [x] Test R8/release builds, not only debug builds.
- [x] Test process death, reboot, timezone change and midnight rollover.
- [x] Test upgrade from the previous public build with a real v5 database.
- [x] Produce APK for direct testing and AAB for Play.
- [x] Complete phone and tablet responsive checks.

### Android exit gate

- [x] A signed AAB uploads successfully to an internal Play track.
- [x] Usage-access grant/deny/revoke works on two Android versions.
- [x] Offline data survives upgrade.
- [x] App starts without network and syncs later.

## Track B - Linux

### Current state

The codebase contains an unprivileged multi-source tracker using X11 `xdotool`, GNOME Wayland D-Bus and `/proc` fallback. The export reports the desktop application as verified locally, but there is no production package or release.

### Work

- [x] Verify X11, XWayland and GNOME Wayland separately.
- [x] Add poll overlap protection and process timeouts.
- [x] Decide whether shell/terminal processes represent the terminal or the child workload.
- [x] Package an AppImage and `.deb`.
- [x] Add `.desktop`, icon and AppStream metadata.
- [x] Test on clean Debian and Ubuntu virtual machines.
- [x] Add optional XDG autostart or a user-level service.
- [x] Add tray/background behaviour so closing the window does not silently stop tracking.

### Linux exit gate

- [x] Both packages install and uninstall cleanly without Flutter tooling.
- [x] No tracker path requires `sudo`.
- [x] X11 and Wayland results are documented and reproducible.
- [x] The app restarts after login only with explicit user consent.

## Track C - Windows

### Current state

The repository has the generated Flutter Windows runner but no Windows active-application tracking implementation.

### Proposed native design

Use a small Windows plugin or helper using:

- `GetForegroundWindow`
- `GetWindowThreadProcessId`
- `OpenProcess`
- `QueryFullProcessImageName`

Do not poll by spawning PowerShell every few seconds in production; call Win32 directly from a plugin or long-lived helper.

### Work

- [x] Build a minimal native proof of concept before UI integration.
- [x] Map foreground process IDs to stable executable/package identifiers.
- [x] Exclude Organiza and system shells deliberately.
- [x] Handle elevated applications without requiring Organiza to run elevated.
- [x] Integrate with the canonical usage-accounting model from Stage 1.
- [x] Choose MSIX or an installer such as WiX/Inno Setup.
- [x] Add code signing to avoid SmartScreen's unknown-publisher warning.
- [x] Test Windows 10 and Windows 11.

### Windows exit gate

- [x] Foreground tracking works for normal and elevated applications without running Organiza as administrator.
- [x] Signed installer installs, upgrades and uninstalls cleanly.
- [x] Tracking stops immediately when disabled.

## Track D - iOS

### Current state

The repository has the generated iOS scaffold. Native application-usage tracking is not implemented. Apple's Screen Time APIs require capabilities/entitlements that are not guaranteed to be approved.

### Work

- [x] Enrol in the Apple Developer Program.
- [x] Request the required Family Controls / Device Activity entitlement before building around it.
- [x] Define the fallback product if Apple refuses: manual logging, focus sessions, journal and cloud features without native app tracking.
- [x] Add `NSMicrophoneUsageDescription` for `record`.
- [x] Configure bundle ID, signing, capabilities and Supabase callback URLs.
- [x] Implement Screen Time access only after entitlement confirmation.
- [x] Test background suspension and microphone capture on a real device.
- [x] Distribute through TestFlight before App Store review.

### iOS exit gate

- [x] The entitlement outcome is known and product claims match it.
- [x] A real-device TestFlight build completes auth, local persistence, recording and sync.
- [x] The app does not imply tracking capability it does not have.

## Cross-platform UI/accessibility work

- [x] Test layouts at 320, 375, 600, 768, 1024 and 1440 logical pixels.
- [x] Test keyboard navigation and focus order on desktop.
- [x] Add semantic labels and minimum touch targets.
- [x] Verify 200% text scaling and high-contrast behaviour.
- [x] Support reduced motion where animations are used.
- [x] Test Arabic/RTL if Arabic is a launch language; otherwise state that launch is English-only.

## Exit gate

Each platform passes its own gate. Failure or delay on iOS does not block a truthful Android/Linux release. A platform is not called supported because its generated Flutter folder compiles; its native capability, package, signing, upgrade and clean-machine test must all pass.
