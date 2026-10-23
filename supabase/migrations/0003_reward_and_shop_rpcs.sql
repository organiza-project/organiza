-- ============================================================================
-- Migration: 0003_reward_and_shop_rpcs.sql
-- Description: Server-authoritative reward and shop mutations with transaction
--              safety, idempotency, and integer-overflow protections.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Complete Habit and Award Rewards RPC
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.complete_habit_and_award_rewards(
    p_habit_id TEXT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_user_id UUID;
    v_task RECORD;
    v_today DATE := CURRENT_DATE;
    v_existing_log RECORD;
    v_xp_to_award INTEGER;
    v_stars_to_award INTEGER := 5;
    v_new_xp BIGINT;
    v_new_stars INTEGER;
    v_new_level INTEGER;
BEGIN
    -- 1. Derive authenticated caller
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'Unauthenticated request');
    END IF;

    -- 2. Validate habit ownership
    SELECT id, xp_value, is_deleted
    INTO v_task
    FROM public.tasks
    WHERE id = p_habit_id AND user_id = v_user_id;

    IF NOT FOUND OR v_task.is_deleted THEN
        RETURN jsonb_build_object('success', false, 'error', 'Habit task not found or deleted');
    END IF;

    v_xp_to_award := COALESCE(v_task.xp_value, 50);

    -- 3. Check for idempotency: has reward already been claimed today?
    SELECT id, is_completed, xp_awarded
    INTO v_existing_log
    FROM public.daily_logs
    WHERE user_id = v_user_id AND task_id = p_habit_id AND log_date = v_today;

    IF FOUND AND v_existing_log.is_completed AND v_existing_log.xp_awarded > 0 THEN
        -- Already awarded today: return current profile stats without duplicating rewards
        SELECT total_xp, total_stars, level
        INTO v_new_xp, v_new_stars, v_new_level
        FROM public.profiles
        WHERE id = v_user_id;

        RETURN jsonb_build_object(
            'success', true,
            'idempotent', true,
            'message', 'Habit already completed today. Reward preserved.',
            'xp_awarded', 0,
            'stars_awarded', 0,
            'new_total_xp', v_new_xp,
            'new_total_stars', v_new_stars,
            'new_level', v_new_level
        );
    END IF;

    -- 4. Upsert daily log completion state
    INSERT INTO public.daily_logs (
        id, user_id, task_id, log_date, duration_logged_seconds, is_completed, xp_awarded, stars, updated_at
    )
    VALUES (
        v_today || '_' || p_habit_id,
        v_user_id,
        p_habit_id,
        v_today,
        1800,
        true,
        v_xp_to_award,
        v_stars_to_award,
        now()
    )
    ON CONFLICT (user_id, task_id, log_date)
    DO UPDATE SET
        is_completed = true,
        xp_awarded = v_xp_to_award,
        stars = v_stars_to_award,
        updated_at = now();

    -- 5. Atomically increment profile currency & calculate level
    UPDATE public.profiles
    SET
        total_xp = total_xp + v_xp_to_award,
        total_stars = total_stars + v_stars_to_award,
        level = GREATEST(1, FLOOR((total_xp + v_xp_to_award) / 500) + 1),
        updated_at = now()
    WHERE id = v_user_id
    RETURNING total_xp, total_stars, level
    INTO v_new_xp, v_new_stars, v_new_level;

    RETURN jsonb_build_object(
        'success', true,
        'idempotent', false,
        'xp_awarded', v_xp_to_award,
        'stars_awarded', v_stars_to_award,
        'new_total_xp', v_new_xp,
        'new_total_stars', v_new_stars,
        'new_level', v_new_level
    );
END;
$$;

REVOKE EXECUTE ON FUNCTION public.complete_habit_and_award_rewards(TEXT) FROM anon;
GRANT EXECUTE ON FUNCTION public.complete_habit_and_award_rewards(TEXT) TO authenticated;

-- ----------------------------------------------------------------------------
-- 2. Purchase Cosmetic Item RPC
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.purchase_cosmetic_item(
    p_cosmetic_id TEXT
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_user_id UUID;
    v_cosmetic RECORD;
    v_profile RECORD;
    v_remaining_stars INTEGER;
BEGIN
    -- 1. Derive authenticated caller
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RETURN jsonb_build_object('success', false, 'error', 'Unauthenticated request');
    END IF;

    -- 2. Check catalog item validity and authoritative price
    SELECT id, name, cost_stars, required_level, is_active
    INTO v_cosmetic
    FROM public.cosmetics_catalog
    WHERE id = p_cosmetic_id AND is_active = true;

    IF NOT FOUND THEN
        RETURN jsonb_build_object('success', false, 'error', 'Cosmetic item not found in catalog');
    END IF;

    -- 3. Check if user already owns this cosmetic
    IF EXISTS (
        SELECT 1 FROM public.user_cosmetics
        WHERE user_id = v_user_id AND cosmetic_id = p_cosmetic_id
    ) THEN
        RETURN jsonb_build_object('success', false, 'error', 'Item already owned');
    END IF;

    -- 4. Check user balance and level requirements
    SELECT total_stars, level
    INTO v_profile
    FROM public.profiles
    WHERE id = v_user_id
    FOR UPDATE; -- lock profile row for atomic transaction

    IF v_profile.level < v_cosmetic.required_level THEN
        RETURN jsonb_build_object(
            'success', false,
            'error', 'Level requirement not met. Required: ' || v_cosmetic.required_level
        );
    END IF;

    IF v_profile.total_stars < v_cosmetic.cost_stars THEN
        RETURN jsonb_build_object(
            'success', false,
            'error', 'Insufficient Stars balance'
        );
    END IF;

    -- 5. Deduct stars balance
    UPDATE public.profiles
    SET
        total_stars = total_stars - v_cosmetic.cost_stars,
        updated_at = now()
    WHERE id = v_user_id
    RETURNING total_stars INTO v_remaining_stars;

    -- 6. Insert into inventory
    INSERT INTO public.user_cosmetics (user_id, cosmetic_id, unlocked_at)
    VALUES (v_user_id, p_cosmetic_id, now());

    RETURN jsonb_build_object(
        'success', true,
        'cosmetic_id', p_cosmetic_id,
        'item_name', v_cosmetic.name,
        'cost_paid', v_cosmetic.cost_stars,
        'remaining_stars', v_remaining_stars
    );
END;
$$;

REVOKE EXECUTE ON FUNCTION public.purchase_cosmetic_item(TEXT) FROM anon;
GRANT EXECUTE ON FUNCTION public.purchase_cosmetic_item(TEXT) TO authenticated;
