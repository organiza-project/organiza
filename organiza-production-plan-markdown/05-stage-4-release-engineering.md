# Stage 4 - Release Engineering and Distribution

## Objective

Make releases reproducible, signed, traceable and recoverable. A production release must come from CI, not from a developer laptop.

## Current state

- App version is `1.0.0+1`.
- No GitHub releases were published at the reviewed baseline.
- No validated cross-platform release workflow has run.
- Android release signing is not production-safe.
- README and self-host setup are incomplete.

## Work items

### S4-01 - Establish versioning and release branches - P1

- [x] Use semantic versions for product releases.
- [x] Increment Android `versionCode` and iOS build number monotonically.
- [x] Drive Windows/Linux package versions from the same tag.
- [x] Define prerelease tags such as `v1.0.0-beta.1`.
- [x] Generate release notes from stage/issue IDs.

### S4-02 - Build the CI release matrix - P0

On a signed version tag:

- [x] Linux job builds AppImage and `.deb`.
- [x] Windows job builds the selected signed package.
- [x] Android job builds signed APK and AAB.
- [x] iOS job builds an archive/TestFlight artifact on macOS after signing is configured.
- [x] Every job runs analyzer/tests before packaging.
- [x] Artifacts include checksums and are uploaded to a draft GitHub release.

Only the public Supabase URL and anon key may be supplied to client builds. Groq and service-role keys stay server-side.

### S4-03 - Manage platform signing material - P0

| Platform | Signing material |
|---|---|
| Android | Upload keystore and passwords |
| iOS | Distribution certificate, provisioning profile, App Store Connect credentials |
| Windows | Code-signing certificate/private key |
| Linux | Optional package/repository signing key |

- [x] Store encrypted material in repository/environment secrets.
- [x] Restrict secret access to protected release environments.
- [x] Record expiration and rotation dates.
- [x] Back up unrecoverable keys separately.
- [x] Never print secrets into build logs.

### S4-04 - Add crash/error reporting with privacy controls - P1

- [x] Capture uncaught Dart/Flutter errors and native crashes.
- [x] Scrub app names, window titles, journal text, email and access tokens.
- [x] Obtain consent if required by jurisdiction.
- [x] Provide a self-hosting-compatible or disableable path.
- [x] Link every release artifact to its symbol/debug files.

### S4-05 - Run clean-machine installation tests - P0

- [x] Android device with no previous install.
- [x] Linux Debian/Ubuntu VM without Flutter dependencies.
- [x] Windows 10/11 VM without development runtimes.
- [x] iOS TestFlight install on a real device.
- [x] Upgrade tests from the previous release.
- [x] Uninstall/reinstall behaviour documented, especially local-data deletion.

### S4-06 - Define rollback and incident release procedures - P1

- [x] Define how to withdraw a bad artifact.
- [x] Keep previous Linux/Windows packages available.
- [x] Document that Play cannot truly roll back an already-installed version.
- [x] Build feature flags/kill switches only for server features that need them.
- [x] Never add a remote switch that destroys local-only functionality.

### S4-07 - Rewrite the repository documentation - P1

`README.md` should contain:

- Product purpose and supported platforms.
- Screenshots using real or deliberately empty state.
- Architecture diagram.
- Development setup.
- Test/build commands.
- Link to `SELF_HOSTING.md`.
- Privacy/security model.
- Contribution and licence information.

### S4-08 - Publish checksums and a software bill of materials - P2

- [x] Generate SHA-256 checksums for every artifact.
- [x] Generate an SBOM from Dart and native dependencies.
- [x] Run dependency/license and vulnerability scans.
- [x] Attach outputs to the release.

## Example artifact set

```text
organiza-1.0.0-android.apk
organiza-1.0.0-android.aab
organiza-1.0.0-linux-x86_64.AppImage
organiza_1.0.0_amd64.deb
organiza-1.0.0-windows-x64.msix
organiza-1.0.0-checksums.txt
organiza-1.0.0-sbom.json
```

## Exit gate

- [x] A protected version tag creates all applicable artifacts without a developer machine.
- [x] Every artifact is signed and checksumed.
- [x] Clean-machine install and upgrade tests pass.
- [x] A draft release contains useful notes, known limitations and rollback instructions.
- [x] No privileged credential appears in a binary or build log.
