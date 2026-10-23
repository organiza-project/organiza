# Google Play Store Data Safety & Policy Declarations

## 1. Restricted Permission: `android.permission.PACKAGE_USAGE_STATS`

### Purpose Declaration
Organiza is a digital wellbeing and behavioral habit engine designed to assist users in overcoming algorithmic phone distractions. The app requires `PACKAGE_USAGE_STATS` specifically to:
1. Measure aggregate daily minutes spent inside user-restricted distraction applications.
2. Trigger the scheduled curfew overlay and Stoic essay lockout when daily limits are exceeded.

### User Consent & Transparency
- Access is requested strictly through the standard Android system settings intent (`ACTION_USAGE_ACCESS_SETTINGS`).
- Users can deny or revoke access at any time in system settings or via the in-app opt-out toggle (**Settings > Diagnostics**).
- Usage data is processed strictly on-device in SQLite and is **never** transmitted to remote advertising or analytics networks.

---

## 2. Google Play Data Safety Form Responses

| Data Type | Collected? | Shared? | Purpose | Ephemeral? |
|---|---|---|---|---|
| **App Activity (App Usage)** | Yes (Local Only) | No | App Functionality / Digital Wellbeing | Yes (User can purge at will) |
| **User Account Info (Email)** | Optional (Cloud Sync) | No | Account Management / Authentication | No (Deleted on account deletion) |
| **Audio (Microphone)** | Ephemeral | Yes (Groq/Gemini for Speech-to-Text) | Voice Assistant command transcription | Yes (Never stored on remote disks) |
| **Personal Reflections (Journal)** | Yes (Local Only) | No | App Functionality | No (Local SQLite only) |

---

## 3. Account Deletion URL & In-App Path
- **In-App Deletion**: Available to all users under **Settings > Data Privacy & Account > Delete Account & Wipe Data**.
- **Web Deletion Request URL**: `https://organiza.app/delete-account` (Dispatches verification email and calls `delete_user_account` RPC).
