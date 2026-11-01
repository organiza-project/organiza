# Privacy rules

- No analytics, crash-reporting or ad SDKs, in any module or build type.
- Logs stay on the device and never contain URLs, window titles or package usage.
- `challenge_log` is the only table that feeds sync. Its column allow-list test must not
  be widened to carry usage data.
- Calling a hash commitment a "zero-knowledge proof" is not allowed (ADR-0006).
- Keys live in the platform keystore. Never write them to plaintext storage, logs or tests.
