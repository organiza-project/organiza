# Organiza Incident Response & Security Disclosure Policy

## 1. Security Contact & Vulnerability Reporting

- **Security Reporting Channel**: `security@organiza.app`
- **PGP Key**: Published at `https://organiza.app/.well-known/security.txt`
- **Public Issues**: Do **NOT** report vulnerabilities or credential leaks in public GitHub issues. Use GitHub Private Vulnerability Reporting or direct email.

---

## 2. Severity Classification & Service Level Agreements (SLAs)

| Severity Level | Definition | Initial Acknowledgment | Remediation Target |
|---|---|---|---|
| **SEV-1 (Critical)** | Active remote code execution, database breach, authentication bypass, or client-side secret exposure | < 2 Hours | < 24 Hours |
| **SEV-2 (High)** | Row Level Security (RLS) leak between users, RPC balance exploitation, or recurring crash on launch | < 8 Hours | < 72 Hours |
| **SEV-3 (Medium)** | Non-exploitable logic bug, minor permission discrepancy, or isolated platform crash | < 24 Hours | Next Scheduled Release |

---

## 3. Incident Response Playbooks

### Playbook A: Credential or Key Compromise
1. **Immediate Revocation**:
   - Rotate Supabase `service_role` and `JWT_SECRET` in dashboard/environment.
   - Invalidate all existing user sessions via GoTrue admin API.
   - Rotate LLM provider keys (`GROQ_API_KEY`) on Edge Function secrets.
2. **Client Verification**:
   - Verify that client binaries and source code contain only public `SUPABASE_URL` and `SUPABASE_ANON_KEY`.
3. **Auditing**:
   - Query PostgreSQL logs for unauthorized IP addresses accessing tables directly with the leaked secret.

### Playbook B: Data Breach Notification
1. Isolate the affected database or Edge Function instance.
2. Identify the scope of accessed user IDs and tables.
3. Within 72 hours of confirmation, notify affected registered users via email with clear remediation advice (e.g. session resets).
4. Publish a post-mortem to the community.
