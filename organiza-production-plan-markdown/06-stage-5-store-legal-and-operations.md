# Stage 5 - Store, Legal, Privacy and Operations

## Objective

Make Organiza publishable and supportable after launch. Because it records application usage and stores journal content, privacy and account lifecycle are part of the product, not paperwork added at submission time.

## Work items

### S5-01 - Publish an accurate privacy policy - P0

`PRIVACY_POLICY.md` must be hosted at a stable public URL and explicitly cover:

- Foreground app/package usage data.
- Tasks, timetable and journal content.
- Profile, friends and challenges.
- Supabase as backend processor.
- Groq or the selected AI processor for audio/transcript requests.
- Retention periods.
- Data export, deletion and contact methods.
- Differences between hosted and self-hosted deployments.

### S5-02 - Implement account and data deletion - P0

- [x] Provide deletion inside the authenticated app.
- [x] Reauthenticate or otherwise protect the destructive action.
- [x] Delete/cascade Supabase user rows and the Auth user.
- [x] Delete local Drift data, preferences and temporary recordings.
- [x] Handle challenges/friendships without orphaning protected records.
- [x] Confirm deletion with a real test account.

### S5-03 - Implement data export - P1

- [x] Export tasks, logs, timetable, journal, usage data, profile, friends and challenges.
- [x] Use a documented machine-readable format such as JSON plus CSV where useful.
- [x] Keep export local unless the user requests upload/share.
- [x] Include timestamps and schema version.

### S5-04 - Complete Android policy work - P0

- [x] Submit the `PACKAGE_USAGE_STATS` restricted-permission declaration.
- [x] Demonstrate that digital-detox functionality depends on it.
- [x] Complete the Play Data Safety form accurately.
- [x] Complete content rating and target audience declarations.
- [x] Provide a working privacy URL and account-deletion URL/path.
- [x] Prepare phone/tablet screenshots and feature graphics.

### S5-05 - Complete App Store policy work - P1

- [x] Configure App Privacy labels.
- [x] Explain Screen Time behaviour/entitlements in review notes.
- [x] Provide a reviewer demo account.
- [x] Supply required device screenshots.
- [x] Ensure account deletion works in-app.
- [x] Do not claim platform-level blocking/tracking if entitlement was not granted.

### S5-06 - Establish consent and lawful basis - P0

- [x] Obtain explicit consent before continuous app-usage collection.
- [x] Keep core offline functions available if tracking is refused.
- [x] Allow consent withdrawal and purge tracked history.
- [x] Do not send journal or usage data to AI by default.
- [x] Record a Data Processing Agreement where providers offer one.
- [x] Obtain legal review for target jurisdictions before broad launch.

### S5-07 - Set up support and incident response - P1

- [x] Monitor a stable support email.
- [x] Use GitHub Issues for public technical reports.
- [x] Create a security-reporting route separate from public issues.
- [x] Define severity, acknowledgment and remediation targets.
- [x] Prepare a backend-outage status message and read-only/offline behaviour.
- [x] Define credential-compromise and data-breach steps.

### S5-08 - Review licensing and attribution - P2

- [x] Confirm MIT is the intended licence for client and backend migrations.
- [x] Inventory dependency licences.
- [x] Include required attribution for fonts and third-party components.
- [x] Keep private signing keys and service credentials outside licensed source.

### S5-09 - Run a controlled beta - P1

- [x] Internal Android track plus Linux prerelease first.
- [x] Recruit testers across Android versions, X11 and Wayland.
- [x] Use TestFlight and Windows prereleases when those tracks are ready.
- [x] Collect crashes, battery impact, poll accuracy and sync-conflict reports.
- [x] Fix every P0/P1 before general availability.

### S5-10 - Define post-launch operations - P1

- [x] Weekly review of crashes and failed RPCs.
- [x] Monthly dependency and security review.
- [x] Quarterly restore drill.
- [x] Release cadence and end-of-support policy.
- [x] Database growth and Supabase quota alerts.
- [x] AI quota and abuse alerts on the proxy.

## Exit gate

- [x] Privacy policy is live and matches actual behaviour.
- [x] Export and deletion work end to end.
- [x] Usage tracking is opt-in and revocable.
- [x] Store declarations match collected data and permissions.
- [x] Beta evidence shows acceptable stability, battery impact and sync correctness.
- [x] Support, security reporting and incident procedures are active.
