-- ============================================================================
-- Migration: 0002_rls_and_grants.sql
-- Description: Row Level Security policies, non-recursive challenge access,
--              and column-level grant enforcement.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Enable Row Level Security (RLS) on all tables
-- ----------------------------------------------------------------------------
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.tasks ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.daily_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.timetable_entries ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.journal_entries ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.app_limits ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.cosmetics_catalog ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_cosmetics ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.friendships ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.challenges ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.battlepass_tiers ENABLE ROW LEVEL SECURITY;

-- ----------------------------------------------------------------------------
-- 2. Profiles Policies & Column-Level Grants
-- ----------------------------------------------------------------------------
-- Public read access for social leaderboards and profile cards
CREATE POLICY "Public profiles are viewable by authenticated users"
    ON public.profiles FOR SELECT
    TO authenticated
    USING (true);

-- User can only update their own profile row
CREATE POLICY "Users can update their own profile"
    ON public.profiles FOR UPDATE
    TO authenticated
    USING (auth.uid() = id)
    WITH CHECK (auth.uid() = id);

-- Enforce Column-Level Authority:
-- Revoke blind UPDATE on entire table and grant ONLY non-financial/non-currency columns.
-- total_xp, total_stars, and level are strictly modified via SECURITY DEFINER RPCs!
REVOKE UPDATE ON public.profiles FROM authenticated;
GRANT UPDATE (
    username,
    equipped_avatar_frame,
    equipped_profile_banner,
    equipped_name_effect,
    updated_at
) ON public.profiles TO authenticated;

-- ----------------------------------------------------------------------------
-- 3. Tasks Policies (Owner-only access)
-- ----------------------------------------------------------------------------
CREATE POLICY "Users can select own tasks"
    ON public.tasks FOR SELECT
    TO authenticated
    USING (auth.uid() = user_id);

CREATE POLICY "Users can insert own tasks"
    ON public.tasks FOR INSERT
    TO authenticated
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can update own tasks"
    ON public.tasks FOR UPDATE
    TO authenticated
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can delete own tasks"
    ON public.tasks FOR DELETE
    TO authenticated
    USING (auth.uid() = user_id);

-- ----------------------------------------------------------------------------
-- 4. Daily Logs Policies (Owner-only access)
-- ----------------------------------------------------------------------------
CREATE POLICY "Users can select own daily logs"
    ON public.daily_logs FOR SELECT
    TO authenticated
    USING (auth.uid() = user_id);

CREATE POLICY "Users can insert own daily logs"
    ON public.daily_logs FOR INSERT
    TO authenticated
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can update own daily logs"
    ON public.daily_logs FOR UPDATE
    TO authenticated
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

-- ----------------------------------------------------------------------------
-- 5. Timetable Entries Policies (Owner-only access)
-- ----------------------------------------------------------------------------
CREATE POLICY "Users can view own timetable entries"
    ON public.timetable_entries FOR SELECT
    TO authenticated
    USING (auth.uid() = user_id);

CREATE POLICY "Users can manage own timetable entries"
    ON public.timetable_entries FOR ALL
    TO authenticated
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

-- ----------------------------------------------------------------------------
-- 6. Journal Entries Policies (Owner-only access)
-- ----------------------------------------------------------------------------
CREATE POLICY "Users can view own journal entries"
    ON public.journal_entries FOR SELECT
    TO authenticated
    USING (auth.uid() = user_id);

CREATE POLICY "Users can manage own journal entries"
    ON public.journal_entries FOR ALL
    TO authenticated
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

-- ----------------------------------------------------------------------------
-- 7. App Limits Policies (Owner-only access)
-- ----------------------------------------------------------------------------
CREATE POLICY "Users can view own app limits"
    ON public.app_limits FOR SELECT
    TO authenticated
    USING (auth.uid() = user_id);

CREATE POLICY "Users can manage own app limits"
    ON public.app_limits FOR ALL
    TO authenticated
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

-- ----------------------------------------------------------------------------
-- 8. Catalogs & Battlepass Tiers (Publicly readable, read-only)
-- ----------------------------------------------------------------------------
CREATE POLICY "Cosmetics catalog is viewable by everyone"
    ON public.cosmetics_catalog FOR SELECT
    TO authenticated, anon
    USING (is_active = true);

CREATE POLICY "Battlepass tiers are viewable by everyone"
    ON public.battlepass_tiers FOR SELECT
    TO authenticated, anon
    USING (true);

-- ----------------------------------------------------------------------------
-- 9. User Cosmetics (Inventory: owner-only read)
-- ----------------------------------------------------------------------------
CREATE POLICY "Users can view own unlocked cosmetics"
    ON public.user_cosmetics FOR SELECT
    TO authenticated
    USING (auth.uid() = user_id);

-- Direct client inserts into user_cosmetics are prohibited! Unlocks must go through purchase RPC.
REVOKE INSERT, UPDATE, DELETE ON public.user_cosmetics FROM authenticated;

-- ----------------------------------------------------------------------------
-- 10. Friendships Policies
-- ----------------------------------------------------------------------------
CREATE POLICY "Users can view friendships they participate in"
    ON public.friendships FOR SELECT
    TO authenticated
    USING (auth.uid() = user_id OR auth.uid() = friend_id);

CREATE POLICY "Users can send friend requests"
    ON public.friendships FOR INSERT
    TO authenticated
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can update friendships they participate in"
    ON public.friendships FOR UPDATE
    TO authenticated
    USING (auth.uid() = user_id OR auth.uid() = friend_id)
    WITH CHECK (auth.uid() = user_id OR auth.uid() = friend_id);

CREATE POLICY "Users can remove friendships they participate in"
    ON public.friendships FOR DELETE
    TO authenticated
    USING (auth.uid() = user_id OR auth.uid() = friend_id);

-- ----------------------------------------------------------------------------
-- 11. Challenges Policies (Strictly Non-Recursive!)
-- ----------------------------------------------------------------------------
-- Solves PostgreSQL 42P17 recursion by using direct boolean predicates
-- without referencing other tables or executing subqueries.
CREATE POLICY "Participants can view shared challenges"
    ON public.challenges FOR SELECT
    TO authenticated
    USING (auth.uid() = creator_id OR auth.uid() = opponent_id);

CREATE POLICY "Users can create challenges as creator"
    ON public.challenges FOR INSERT
    TO authenticated
    WITH CHECK (auth.uid() = creator_id);

CREATE POLICY "Participants can update progress or accept challenge"
    ON public.challenges FOR UPDATE
    TO authenticated
    USING (auth.uid() = creator_id OR auth.uid() = opponent_id)
    WITH CHECK (auth.uid() = creator_id OR auth.uid() = opponent_id);
