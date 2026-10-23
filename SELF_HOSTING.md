# Self-Hosting Organiza Backend

This document provides complete instructions for deploying, operating, and backing up the self-hosted Supabase backend for Organiza.

---

## 1. Prerequisites & Version Requirements

- **Supabase CLI**: `>= 1.145.0` (Recommended: latest stable release)
  ```bash
  # Install or update via npm or brew
  npm install -g supabase
  # or
  brew install supabase/tap/supabase
  ```
- **Docker Engine**: `>= 24.0.0` and **Docker Compose**: `>= 2.20.0`
- **PostgreSQL**: `15.x` (bundled in Supabase container images)
- **Deno**: `>= 1.37.0` (for local Edge Function testing)

---

## 2. Local Environment Setup

### 2.1 Start Local Containers
From the repository root:
```bash
supabase start
```
This boots PostgreSQL, Auth (GoTrue), Storage, PostgREST, Studio, and Inbucket (local mail testing).

### 2.2 Apply Migrations & Seeds
Run migrations sequentially in the defined order:
```bash
supabase db reset
```
`supabase db reset` automatically:
1. Re-creates the database cleanly.
2. Applies all files in `supabase/migrations/` in numerical order:
   - `0001_base_schema.sql`: Core tables, indexes, constraints, auto-profile trigger.
   - `0002_rls_and_grants.sql`: Strict RLS policies and column-level grant revocation.
   - `0003_reward_and_shop_rpcs.sql`: Atomic rewards and cosmetic purchases.
   - `0004_social_and_challenge_rpcs.sql`: Reciprocal friendships and duel challenge settlements.
3. Applies `supabase/seed.sql` to populate cosmetic items and Season 1 battlepass tiers.

---

## 3. Remote Deployment to Managed or Self-Hosted Supabase

### 3.1 Link Your Project
```bash
supabase login
supabase link --project-ref <your-supabase-project-id>
```

### 3.2 Push Migrations & Seeds
```bash
# Push schema migrations
supabase db push

# Seed cosmetics and battlepass tiers
psql "$DATABASE_URL" -f supabase/seed.sql
```

### 3.3 Deploy Edge Functions
Deploy proxy functions for Groq/Gemini LLM queries and Whisper audio transcription:
```bash
# Deploy Edge Functions
supabase functions deploy assistant-proxy
supabase functions deploy transcription-proxy

# Set Server-Side Secrets
supabase secrets set GROQ_API_KEY="your-groq-api-key"
```

---

## 4. Auth & Email Configuration

Organiza uses 6-digit OTP codes and magic links for email verification.

In your Supabase Studio or `config.toml`:
1. **Enable Email Signup**:
   - `[auth.email] enable_signup = true`
   - `[auth.email] double_confirm_changes = true`
2. **OTP Expiry**: Set OTP expiry to 300 seconds (5 minutes).
3. **Email Template**:
   Ensure the OTP token template contains:
   ```html
   <h2>Your Organiza Verification Code</h2>
   <p>Your one-time code is: <strong>{{ .Token }}</strong></p>
   ```

---

## 5. Client Build Flags (`--dart-define`)

When building or running the Flutter app against your self-hosted backend, supply your instance URL and anonymous public key:

```bash
flutter run \
  --dart-define=SUPABASE_URL=https://your-project.supabase.co \
  --dart-define=SUPABASE_ANON_KEY=eyJhbGciOi...
```

For release builds:
```bash
flutter build apk --release \
  --dart-define=SUPABASE_URL=https://api.organiza.yourdomain.com \
  --dart-define=SUPABASE_ANON_KEY=eyJhbGciOi...
```

---

## 6. Security & RLS Verification

Run the automated test suites using `psql` or `pg_prove`:

```bash
# Test Row Level Security Isolation
psql "$DATABASE_URL" -f supabase/tests/rls.sql

# Test Reward Atomicity & Star Balances
psql "$DATABASE_URL" -f supabase/tests/rewards.sql

# Test Two-Account Duels & Wager Settlement
psql "$DATABASE_URL" -f supabase/tests/challenges.sql
```

---

## 7. Backup and Disaster Recovery Runbook

### 7.1 Automated Daily Backups
For managed Supabase, daily backups are automated. For self-hosted Docker, configure a cron job using `pg_dump`:
```bash
#!/usr/bin/env bash
set -euo pipefail
BACKUP_DIR="/var/backups/organiza"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
mkdir -p "$BACKUP_DIR"

docker exec -t supabase-db pg_dump -U postgres -d postgres -F c -b -v \
  -f "/tmp/backup_${TIMESTAMP}.dump"

docker cp supabase-db:"/tmp/backup_${TIMESTAMP}.dump" "${BACKUP_DIR}/"
docker exec -t supabase-db rm "/tmp/backup_${TIMESTAMP}.dump"

# Retain 30 days
find "$BACKUP_DIR" -type f -name "backup_*.dump" -mtime +30 -delete
```

### 7.2 Point-in-Time Restore Runbook
To restore a backup into a fresh environment:
```bash
# 1. Boot fresh target instance
supabase start

# 2. Restore custom-format dump
pg_restore -U postgres -d postgres -v --clean --no-owner --no-privileges "${BACKUP_DIR}/backup_YYYYMMDD_HHMMSS.dump"

# 3. Verify data integrity
psql -U postgres -d postgres -c "SELECT count(*) FROM public.profiles;"
psql -U postgres -d postgres -c "SELECT count(*) FROM public.tasks;"
```
