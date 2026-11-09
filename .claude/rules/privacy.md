# Privacy rules

- No analytics, crash-reporting or ad SDKs, in any module or build type.
- Logs stay on the device and never contain URLs, window titles or package usage.
- `challenge_log` is the only table that feeds sync. Its column allow-list test must not
  be widened to carry usage data.
- The one other thing that leaves a device is the sealed, padded device usage report of
  ADR-0028, readable only by the person's linked devices. Never send usage any other way.
- Calling a hash commitment a "zero-knowledge proof" is not allowed (ADR-0006).
- Keys live in the platform keystore. Never write them to plaintext storage, logs or tests.
