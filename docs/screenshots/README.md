# Screenshots

Taken on the Android 16 emulator (Pixel, 1080x2400, saved at half size) from a debug build seeded
by `scripts/seed-screens.sh`, with real interventions on reddit.com and x.com in Chrome.

| File | Shows |
|------|-------|
| `01-onboarding-welcome.png` | Welcome and mission |
| `02-onboarding-permissions.png` | Permission walkthrough, with why each is needed |
| `03-onboarding-backend.png` | Backend choice: this phone, Organiza hosted or your own Supabase |
| `04-onboarding-hosted-account.png` | The hosted backend chosen, with sign-in |
| `05-pause-choice.png` | The pause screen on a blocked site |
| `06-pause-breathing.png` | The breathing pause |
| `07-pause-reason.png` | Choosing a reason, with a note |
| `08-pause-typing.png` | Typing the phrase, word by word |
| `09-pause-length.png` | Choosing how long the pass lasts |
| `10-focus.png` | The Focus screen with today's choices |
| `11-choices-copilot.png` | Hardest hours, and copilot proposals |

The copilot's text in `11` came from `scripts/fake-groq.py` in demo mode, not from Groq. Its
layout and the request it answered are real. The two 10:05 "Stayed focused" rows in `10` were
logged while a bug closed site interventions early, which is fixed in `BlockEngine`.
