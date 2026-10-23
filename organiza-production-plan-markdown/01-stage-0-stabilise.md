# Stage 0 - Stabilise the Existing Repository

## Objective

Remove immediate security, identity and build blockers before adding features. This stage changes the current repository from a development build into a safe base for production work.

## Current codebase involved

- `android/app/build.gradle.kts`
- `android/app/src/main/AndroidManifest.xml`
- `.gitignore`
- `pubspec.lock`
- `lib/core/config/supabase_config.dart`
- `lib/core/network/assistant_ai_service.dart`
- `lib/core/network/groq_transcription_service.dart`
- `lib/data/local/app_database.g.dart`
- GitHub Actions under `.github/workflows/`

## Work items

### S0-01 - Rotate the exposed Groq key - P0

### S0-01 - Rotate the exposed Groq key - P0

- [x] Revoke the key contained in the project context export.
- [x] Create a replacement for server-side use only.
- [x] Check provider usage logs for unexpected calls or spend.
- [x] Verify the revoked key no longer authenticates.

**Done when:** the exposed key is invalid and no replacement key exists in source, notes, chat exports or client build arguments.

### S0-02 - Audit all credentials - P0

- [x] Search git history, working tree and private notes for `gsk_`, service-role JWTs and signing passwords.
- [x] Rotate the Supabase service-role key if it has ever left the private deployment environment.
- [x] Keep the Supabase anon key public and rely on verified RLS; do not mislabel it as a secret.

**Done when:** every bearer credential has a known owner, storage location and rotation status.

### S0-03 - Replace Android debug release signing - P0

Current release configuration uses:

```kotlin
signingConfig = signingConfigs.getByName("debug")
```

- [x] Generate a permanent upload keystore.
- [x] Back it up in two secure locations.
- [x] Configure `key.properties` locally and encrypted CI secrets remotely.
- [x] Keep the debug key restricted to debug builds.
- [x] Verify the APK/AAB signature with `apksigner`.

**Done when:** a release artifact is signed by the upload key and Play Console accepts it.

### S0-04 - Finalise application identity - P0

The current namespace and application ID are `app.organiza.organiza`.

- [x] Choose a permanent reverse-DNS identifier under a controlled domain.
- [x] Update Android namespace/application ID and Kotlin package path.
- [x] Align iOS bundle identifier, Windows identity and Linux desktop ID.
- [x] Record the identifiers in release documentation.

**Done when:** all four platforms share a deliberate, permanent product identity before the first store upload.

### S0-05 - Add Android usage permission declaration - P0

- [x] Add `android.permission.PACKAGE_USAGE_STATS` to the manifest.
- [x] Add a first-run explanation before opening system settings.
- [x] Check the permission after returning from settings.
- [x] Handle denial without crashing or showing fabricated usage.

**Done when:** the hardware permission flow works for grant, denial and later revocation.

### S0-06 - Correct repository hygiene - P1

- [x] Commit `pubspec.lock`.
- [x] Decide whether to commit generated Drift code; recommended: generate it in CI and remove it from tracking.
- [x] Remove `.idea/` and `organiza.iml` from tracking.
- [x] Add signing files, provisioning files and local configuration to `.gitignore`.
- [x] Remove obsolete scaffold comments and change the pubspec description.

### S0-07 - Create the continuous-integration gate - P1

On every pull request and push to `main`:

```bash
flutter pub get
dart run build_runner build --delete-conflicting-outputs
flutter analyze --fatal-infos
flutter test
```

- [x] Pin the Flutter channel/version used by CI.
- [x] Cache Flutter/pub dependencies.
- [x] Upload test output on failure.
- [x] Protect `main` against merging when the workflow fails.

### S0-08 - Establish issue and commit traceability - P2

- [x] Use stage task IDs in issue titles and commit messages.
- [x] Add pull-request checkboxes for analyzer, tests, migration safety and platform impact.
- [x] Require an explicit note when a change affects RLS or reward authority.

## Verification commands

```bash
git ls-files | grep -E '(\.idea|\.iml|app_database\.g\.dart|pubspec\.lock)'
flutter pub get
dart run build_runner build --delete-conflicting-outputs
flutter analyze --fatal-infos
flutter test
flutter build apk --release
apksigner verify --print-certs build/app/outputs/flutter-apk/app-release.apk
```

## Exit gate

- [x] Exposed keys are revoked.
- [x] Permanent application identifiers are selected.
- [x] Android release signing no longer uses the debug key.
- [x] Usage access is declared and fails gracefully when denied.
- [x] CI enforces generation, analysis and tests.
- [x] Repository contains `pubspec.lock` and no IDE/signing secrets.
