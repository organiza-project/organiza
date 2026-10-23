# iOS Release Capabilities & Screen Time Fallback

## 1. Capabilities & Permissions

### Microphone Usage
- Permission: `NSMicrophoneUsageDescription` is configured in `ios/Runner/Info.plist`.
- Usage: Audio recording is activated strictly on-demand when the user opens the Voice Assistant modal. No passive or background microphone recording occurs.

### URL Schemes
- Schemes: `app.organiza.organiza` and `organiza` are registered in `CFBundleURLTypes` for Supabase OAuth/Magic Link callbacks.

---

## 2. Screen Time / Family Controls Entitlement Strategy

### Apple Entitlement Landscape
Apple restricts automated application usage and restriction APIs (`DeviceActivity`, `FamilyControls`, `ManagedSettings`) behind a special entitlement that requires developer request and approval from Apple.

### Clear Product Scope & Fallback
1. **Entitlement Granted**:
   - The app links with `DeviceActivityReport` and `DeviceActivityMonitorExtension` to track screen time on iOS.
2. **Entitlement Denied / Unapproved (Standard App Store Release)**:
   - **No Misleading Claims**: Organiza will NOT claim to track third-party iOS app usage in the background if the entitlement is not active.
   - **Full Core Productivity Remains Functional**:
     - Task Management & Eisenhower Matrix.
     - Interactive Timetable & Calendar Blocks.
     - Stoic Journaling & Daily Reflection Prompts.
     - Habit Streaks & Level XP.
     - Cloud Sync & Supabase Leaderboards.
     - Manual Pomodoro / Deep Work Focus Sessions with audio soundscapes.
