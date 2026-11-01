# Testing rules

- Red first: run the new test and see it fail before writing production code.
- Use the fakes in `core/testing`. Do not mock framework classes in core tests.
- Time always comes from the `Clock` port, never `Instant.now()` in logic.
- Schema changes get a new `vN.sql` plus tests; never edit a released version.
- Run `./gradlew test` before every commit.
