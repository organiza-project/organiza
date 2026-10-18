class LegalDocs {
  static const String privacyPolicy = '''
# Organiza Privacy Policy

**Effective Date:** October 18, 2026
**License:** MIT Open Source License

At **Organiza**, we believe your digital discipline, focus habits, and personal productivity data belong exclusively to you. Our architecture is designed with local-first, zero-telemetry principles.

### 1. Local-First Data Sovereignty
- **On-Device Storage:** All daily habits, timetable schedules, stoic essays, and screen time metrics are stored locally in an encrypted Drift SQLite database on your device.
- **Unprivileged Usage Tracking:** Our Linux active window monitor operates entirely within unprivileged user space (`xdotool`, GNOME session DBus, and local `/proc` inspection). We never request or require root (`sudo`) permissions, keystroke logging, or background file indexing.
- **No Third-Party Analytics:** Organiza contains no hidden third-party tracking SDKs, ad networks, or telemetry trackers.

### 2. Optional Supabase Cloud Sync
- **Synchronized Data:** Only your profile username, display name, total XP, current streak, Star balance, cosmetic loadout, and social friend connections are synchronized to the cloud.
- **Encrypted Transmission:** All cloud interactions occur over TLS 1.3 with Row-Level Security (RLS) enforcing strict user-ownership guards.
- **Self-Hosting Freedom:** You may connect Organiza to your own self-hosted Supabase instance via the in-app Server Connection settings at any time without code modification.

### 3. Artificial Intelligence & API Keys
- If you use the AI Assistant, your Groq or Gemini API keys are saved strictly to local device preferences (`SharedPreferences`).
- Prompts and timetable queries are dispatched directly to the respective API provider over HTTPS without passing through intermediary Organiza proxy servers.

### 4. Account Deletion & Data Export
You have full authority to delete your cloud profile and wipe local databases at any time through the Settings screen or by signing out.

### 5. Open Source Auditing
Organiza is open source software licensed under the MIT License. You may audit our complete source code and network calls on our official repository.
''';

  static const String termsOfService = '''
# Organiza Terms of Service

**Effective Date:** October 18, 2026
**License:** MIT License — Open Source Productivity Engine

Welcome to **Organiza**, the gamified Stoic productivity and discipline engine. By using Organiza, you agree to these Terms of Service.

### 1. Acceptance of Terms
Organiza is distributed as free, open-source software under the terms of the MIT License. You may run, inspect, modify, fork, and self-host the application freely in accordance with the license.

### 2. User Accounts & Fair Play
- **Unique Handles:** You are responsible for maintaining the security of your account and your unique `@username`.
- **Friendly Competition:** In Social Duels and Leaderboards, any exploitation of simulated clock drifts, automated habit tampering, or abusive friend spam is contrary to the Stoic ethos of Organiza.
- **Self-Hosting Exemption:** Self-hosted instances operate under the policies set by their respective administrators.

### 3. Screen Time Blocker & Digital Habit Enforcement
- Organiza includes optional digital defense mechanisms, such as Stoic Essay Lockouts and app usage limits.
- You acknowledge that these mechanisms are designed as behavioral discipline aids. You maintain full ultimate authority over your device's operating system settings.

### 4. Disclaimer of Warranty & Limitation of Liability
The software is provided "AS IS", without warranty of any kind, express or implied, including but not limited to the warranties of merchantability, fitness for a particular purpose, and non-infringement. In no event shall the authors or copyright holders be liable for any claim, damages, or other liability.

### 5. Changes to Terms
Because Organiza is community-driven, updates to these terms will be published directly within the source repository and bundled within new application releases.
''';
}
