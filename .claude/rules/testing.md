# Testing rules

- Red first: run the new test and see it fail before writing production code.
- Use the fakes in `core/testing`. Do not mock framework classes in core tests.
- Time always comes from the `Clock` port, never `Instant.now()` in logic.
- Schema changes get a new `vN.sql` plus tests; never edit a released version.
- Run `./gradlew test` before every commit.
- Android changes also need `scripts/verify-android.sh` on an emulator (all checks must pass). Count checks use check_eq, because "0" also matches "10". Check that a
  new assertion can fail: the first overlay check here passed because its grep never matched.
- The Android build needs a full JDK. If the default `java` has no compiler, set `JAVA_HOME`.
