# Organiza Privacy Policy

**Effective Date:** October 24, 2026  
**License:** MIT Open Source License  
**Contact:** privacy@organiza.app  
**Public Repository:** https://github.com/organiza-project/organiza  

---

## 1. Overview & Data Sovereignty

Organiza is an offline-first productivity platform engineered with zero-telemetry and privacy-first architectural principles. Your daily habits, focus sessions, timetable events, reflective journals, and application usage analytics belong solely to you.

---

## 2. Information We Process & Where It Lives

### 2.1 On-Device Storage (Strictly Local)
- **Local SQLite Database (Drift)**:
  - Task lists, Eisenhower Matrix classifications, and completion state.
  - Timetable events, class schedules, and calendar blocks.
  - Personal journal entries and daily Stoic reflections.
  - Distraction blocker rules, curfews, and Stoic essay unlocks.
  - Pre-migration automated database snapshots.
- **Application & Screen Time Usage Data**:
  - Android: Queried through `UsageStatsManager` with explicit user consent (`PACKAGE_USAGE_STATS`).
  - Linux: Queried through unprivileged user-space APIs (X11 `xdotool` and GNOME Wayland D-Bus). No root (`sudo`) access or kernel drivers are ever used.
  - Windows: Queried through unprivileged Win32 APIs (`GetForegroundWindow`, `QueryFullProcessImageName`).
  - **Storage & Purging**: Usage snapshots are stored exclusively in your local database. You can review, opt out of, or permanently purge usage history at any time in **Settings > System Diagnostics**.

### 2.2 Synchronized Cloud Data (Optional via Supabase)
When you choose to sign in to a hosted or self-hosted Supabase instance, only the following social and gamification data is synchronized:
- Public profile: Unique username (`@username`), display name, avatar, and equipped cosmetic items.
- Gamification state: Total XP, current level, current streak, and Star balance.
- Social features: Reciprocal friendships and duel challenges.
- Sync mechanism: All data transmitted over TLS 1.3, guarded by PostgreSQL Row Level Security (RLS) ensuring non-owners cannot read or modify private rows.

### 2.3 Audio & AI Transcription Processing
- When you use the Voice Assistant, microphone audio is captured strictly while the recording modal is active.
- Audio recordings are sent over HTTPS to the configured transcription processor (Groq Whisper or Gemini) solely to generate the text prompt.
- Organiza does **not** store audio recordings on remote servers or use your voice data for model training.
- API keys (if provided directly by the user) are saved strictly to local device preferences (`SharedPreferences`).

---

## 3. Crash Reporting & Diagnostics

- Uncaught errors are intercepted by our internal `CrashReporter`.
- **Privacy Scrubbing**: All error logs are automatically stripped of personal identifiers, including email addresses, JWT tokens, Bearer authorization headers, and local filesystem user paths (`/home/username` or `C:\Users\username`).
- Diagnostic telemetry is disabled by default and requires explicit user opt-in in **Settings > Diagnostics**.

---

## 4. Data Retention & Erasure

- **Local Storage**: Retained on your device indefinitely until you manually clear it, purge history in Diagnostics, or uninstall the app.
- **Cloud Account**: Retained while your account remains active.
- **Account & Data Deletion**: You can permanently delete your cloud account and wipe all local databases at any time in **Settings > Account > Delete Account**. Deletion cascades through all cloud tables (profile, cosmetics, friendships, challenges) and wipes local SQLite storage.

---

## 5. Hosted vs. Self-Hosted Deployments

- **Official Hosted Instance**: Governed by this Privacy Policy.
- **Self-Hosted Instances**: If you connect Organiza to your own self-hosted Supabase instance via **Settings > Server Connection**, all data is transmitted directly to your infrastructure. No telemetry or copy is transmitted to the Organiza project authors.

---

## 6. Contact & Rights

For questions, data access inquiries, or bug reports regarding privacy:
- Email: `privacy@organiza.app`
- Security Disclosures: `security@organiza.app`
- GitHub Issues: https://github.com/organiza-project/organiza/issues
