# Organiza Operations Manual

## 1. Monitoring & Observability

### 1.1 Weekly Operational Rhythm
- **Crash Review**: Review unsent/sanitized crash logs and uncaught error counts from `CrashReporter`.
- **RPC Telemetry**: Review execution logs for `complete_habit_and_award_rewards`, `purchase_cosmetic_item`, and `settle_duel_challenge` in Supabase Studio.
  - Alert trigger: Stored procedure execution failures exceeding 0.1% of transactions.
- **Quota & Resource Alerts**:
  - Database disk usage: Alert at 75% capacity.
  - PostgREST connection pool: Alert at 80% saturation.
  - LLM Proxy tokens/day: Alert at 85% of monthly budget.

---

## 2. Backup & Disaster Recovery Schedule

| Task | Frequency | Target / Tool | Verification Drill |
|---|---|---|---|
| Automated Full DB Dump | Daily (03:00 UTC) | `pg_dump` compressed format to encrypted object storage | Verified automated checksum |
| Cold Restore Drill | Quarterly | Scratch test container (`supabase start`) | Live test account login & task query |
| Local Client Backup | Pre-migration | Embedded `app.sqlite.backup` copy | Automated unit test verification |

### Emergency Restore Procedure
Refer to [SELF_HOSTING.md](../SELF_HOSTING.md) for step-by-step restoration instructions using `pg_restore`.

---

## 3. Release & Maintenance Cadence

- **Patch Releases (`1.0.x`)**: Bi-weekly or immediate for critical regressions.
- **Minor Releases (`1.x.0`)**: Monthly for planned feature enhancements.
- **Dependency Auditing**: Monthly execution of `scripts/generate_sbom.sh` and vulnerability audit.
- **End-of-Support**: Major versions are supported with security patches for 12 months after a successor release.
