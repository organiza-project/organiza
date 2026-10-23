# Apple App Store Privacy Nutrition Labels & Entitlement Declarations

## 1. App Privacy Nutrition Labels

| Data Category | Data Type | Usage / Purpose | Linked to User? | Used for Tracking? |
|---|---|---|---|---|
| **Contact Info** | Email Address | User Account Authentication / Sync | Yes (Optional account) | No |
| **Audio Data** | Voice / Microphone Audio | Voice Assistant Speech-to-Text | No | No |
| **User Content** | Tasks & Calendar Schedules | Core App Productivity Functionality | No (Local SQLite) | No |
| **Diagnostics** | Crash & Error Performance | Technical Diagnostics (Scrubbed of PII) | No | No |

---

## 2. Reviewer Notes & Demo Account

- **Demo Account**: `reviewer@organiza.app` (Magic link / passwordless OTP).
- **Entitlement Notes**:
  - The app includes offline local productivity (Eisenhower tasks, timetable, Stoic journaling) and gamification without requiring Screen Time entitlements.
  - Background screen time monitoring on iOS is deactivated unless the official Apple Family Controls entitlement is granted.
- **In-App Account Deletion**:
  - Reviewer path: Open Settings (bottom right icon) -> Scroll to "Data Privacy & Account" -> Tap "Delete Account & Wipe Data".
