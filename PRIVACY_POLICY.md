# Organiza Privacy Policy

**Effective Date:** October 18, 2026  
**License:** MIT Open Source License

At **Organiza**, we believe your digital discipline, focus habits, and personal productivity data belong exclusively to you. Our architecture is designed with local-first, zero-telemetry principles.

---

## 1. Local-First Data Sovereignty
- **On-Device Storage:** All daily habits, timetable schedules, stoic essays, and screen time metrics are stored locally in an encrypted Drift SQLite database on your device.
- **Unprivileged Usage Tracking:** Our Linux active window monitor operates entirely within unprivileged user space (`xdotool`, GNOME session DBus, and local `/proc` inspection). We never request or require root (`sudo`) permissions, keystroke logging, or background file indexing.
- **No Third-Party Analytics:** Organiza contains no hidden third-party tracking SDKs, ad networks, or telemetry trackers.

---

## 2. Optional Supabase Cloud Sync
If you choose to register an account or connect to a self-hosted Supabase instance:
- **Synchronized Data:** Only your profile username, display name, total XP, current streak, Star balance, cosmetic loadout, and social friend connections are synchronized to the cloud.
- **Encrypted Transmission:** All cloud interactions occur over TLS 1.3 with Row-Level Security (RLS) enforcing strict user-ownership guards.
- **Self-Hosting Freedom:** You may connect Organiza to your own self-hosted Supabase instance via the in-app Server Connection settings at any time without code modification.

---

## 3. Artificial Intelligence & API Keys
- If you use the AI Assistant, your Groq or Gemini API keys are saved strictly to local device preferences (`SharedPreferences`).
- Prompts and timetable queries are dispatched directly to the respective API provider over HTTPS without passing through intermediary Organiza proxy servers.

---

## 4. Account Deletion & Data Export
You have full authority to delete your cloud profile and wipe local databases at any time through the Settings screen or by signing out.

---

## 5. Contact & Open Source Auditing
Organiza is open source software. You may audit our complete source code and network calls on our official GitHub repository.
