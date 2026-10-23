-- ============================================================================
-- Migration: 0001_base_schema.sql
-- Description: Core relational schema for Organiza productivity platform
-- ============================================================================

-- Enable required extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ----------------------------------------------------------------------------
-- 1. Profiles (User progression, XP, Stars, Level, Equipped Cosmetics)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    username TEXT UNIQUE NOT NULL,
    total_xp BIGINT NOT NULL DEFAULT 0,
    total_stars INTEGER NOT NULL DEFAULT 0,
    level INTEGER NOT NULL DEFAULT 1,
    level_title TEXT NOT NULL DEFAULT 'Spartan Initiate',
    streak_days INTEGER NOT NULL DEFAULT 0,
    equipped_avatar_frame TEXT,
    equipped_profile_banner TEXT,
    equipped_name_effect TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now())
);

-- Index for leaderboard queries
CREATE INDEX IF NOT EXISTS idx_profiles_total_xp ON public.profiles (total_xp DESC);
CREATE INDEX IF NOT EXISTS idx_profiles_username ON public.profiles (username);

-- ----------------------------------------------------------------------------
-- 2. Tasks (Habits, routines, focus blocks)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.tasks (
    id TEXT PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    description TEXT,
    cadence_unit TEXT NOT NULL DEFAULT 'day',
    target_frequency INTEGER NOT NULL DEFAULT 1,
    target_duration_seconds INTEGER,
    best_time_start TEXT,
    best_time_end TEXT,
    xp_value INTEGER NOT NULL DEFAULT 50,
    linked_package_name TEXT,
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now())
);

CREATE INDEX IF NOT EXISTS idx_tasks_user_id ON public.tasks (user_id);
CREATE INDEX IF NOT EXISTS idx_tasks_updated_at ON public.tasks (updated_at);

-- ----------------------------------------------------------------------------
-- 3. Daily Logs (Task completion records and durations)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.daily_logs (
    id TEXT PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    task_id TEXT NOT NULL REFERENCES public.tasks(id) ON DELETE CASCADE,
    log_date DATE NOT NULL,
    duration_logged_seconds INTEGER NOT NULL DEFAULT 0,
    is_completed BOOLEAN NOT NULL DEFAULT false,
    xp_awarded INTEGER NOT NULL DEFAULT 0,
    stars INTEGER NOT NULL DEFAULT 0,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    CONSTRAINT uq_daily_logs_task_date UNIQUE (user_id, task_id, log_date)
);

CREATE INDEX IF NOT EXISTS idx_daily_logs_user_date ON public.daily_logs (user_id, log_date);

-- ----------------------------------------------------------------------------
-- 4. Timetable Entries (Scheduled calendar blocks)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.timetable_entries (
    id TEXT PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    day_of_week INTEGER NOT NULL DEFAULT 1,
    specific_date TEXT,
    start_time TEXT NOT NULL,
    end_time TEXT NOT NULL,
    location_or_notes TEXT,
    is_canceled BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now())
);

CREATE INDEX IF NOT EXISTS idx_timetable_user ON public.timetable_entries (user_id);

-- ----------------------------------------------------------------------------
-- 5. Journal Entries (Offline-capable reflective logs)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.journal_entries (
    id TEXT PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    entry_date DATE NOT NULL,
    content TEXT NOT NULL,
    tags TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now())
);

CREATE INDEX IF NOT EXISTS idx_journal_user_date ON public.journal_entries (user_id, entry_date);

-- ----------------------------------------------------------------------------
-- 6. App Limits (Curfews, blocking rules, and screen limits)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.app_limits (
    id TEXT PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    package_name TEXT NOT NULL,
    app_name TEXT NOT NULL,
    daily_cap_minutes INTEGER NOT NULL DEFAULT 30,
    curfew_start TEXT,
    curfew_end TEXT,
    block_reels_shorts BOOLEAN NOT NULL DEFAULT false,
    is_essay_locked BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now())
);

CREATE INDEX IF NOT EXISTS idx_app_limits_user ON public.app_limits (user_id);

-- ----------------------------------------------------------------------------
-- 7. Cosmetics Catalog (Canonical item definitions and price authority)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.cosmetics_catalog (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT NOT NULL,
    type TEXT NOT NULL CHECK (type IN ('avatarFrame', 'profileBanner', 'nameEffect')),
    cost_stars INTEGER NOT NULL DEFAULT 0,
    required_level INTEGER NOT NULL DEFAULT 1,
    asset_or_style_key TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now())
);

-- ----------------------------------------------------------------------------
-- 8. User Cosmetics (Purchased and unlocked inventory items)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.user_cosmetics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    cosmetic_id TEXT NOT NULL REFERENCES public.cosmetics_catalog(id) ON DELETE RESTRICT,
    unlocked_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    CONSTRAINT uq_user_cosmetics UNIQUE (user_id, cosmetic_id)
);

CREATE INDEX IF NOT EXISTS idx_user_cosmetics_user ON public.user_cosmetics (user_id);

-- ----------------------------------------------------------------------------
-- 9. Friendships (Social reciprocity without duplicate reversed rows)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.friendships (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    friend_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    status TEXT NOT NULL CHECK (status IN ('pending', 'accepted', 'blocked')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    CONSTRAINT chk_friendship_distinct CHECK (user_id <> friend_id),
    CONSTRAINT uq_friendship_pair UNIQUE (user_id, friend_id)
);

CREATE INDEX IF NOT EXISTS idx_friendships_user ON public.friendships (user_id);
CREATE INDEX IF NOT EXISTS idx_friendships_friend ON public.friendships (friend_id);

-- ----------------------------------------------------------------------------
-- 10. Challenges (Two-account duels and accountability contracts)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.challenges (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    creator_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    opponent_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    challenge_type TEXT NOT NULL,
    duration_days INTEGER NOT NULL DEFAULT 7,
    target_metric TEXT NOT NULL,
    wager_stars INTEGER NOT NULL DEFAULT 0,
    creator_progress INTEGER NOT NULL DEFAULT 0,
    opponent_progress INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL CHECK (status IN ('pending', 'active', 'completed', 'declined')),
    winner_id UUID REFERENCES auth.users(id),
    settled_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT timezone('utc'::text, now()),
    CONSTRAINT chk_challenge_distinct_players CHECK (creator_id <> opponent_id)
);

CREATE INDEX IF NOT EXISTS idx_challenges_participants ON public.challenges (creator_id, opponent_id);

-- ----------------------------------------------------------------------------
-- 11. Battlepass Tiers (Seasonal reward progression ladder)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.battlepass_tiers (
    tier_level INTEGER PRIMARY KEY,
    required_xp BIGINT NOT NULL,
    reward_type TEXT NOT NULL,
    reward_id TEXT,
    reward_amount INTEGER NOT NULL DEFAULT 0
);

-- ----------------------------------------------------------------------------
-- Auto-create Profile Trigger for new Auth users
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
BEGIN
    INSERT INTO public.profiles (id, username)
    VALUES (
        new.id,
        COALESCE(new.raw_user_meta_data->>'username', 'Spartan_' || SUBSTRING(new.id::text, 1, 8))
    )
    ON CONFLICT (id) DO NOTHING;
    RETURN new;
END;
$$;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();
