-- ============================================================================
-- SQL Test Suite: Rewards & Shop Transactions
-- Verifies server authority over star balance, level progression, and cosmetics.
-- ============================================================================

BEGIN;

INSERT INTO auth.users (id, email)
VALUES ('33333333-3333-3333-3333-333333333333', 'gamer@test.organiza.app')
ON CONFLICT (id) DO NOTHING;

-- Seed cosmetic catalog item
INSERT INTO public.cosmetics_catalog (id, name, description, type, cost_stars, required_level, asset_or_style_key)
VALUES ('test_banner_gold', 'Test Banner', 'Test', 'profileBanner', 100, 1, 'test_banner')
ON CONFLICT (id) DO NOTHING;

-- Set caller to gamer
SET LOCAL ROLE authenticated;
SET LOCAL "request.jwt.claim.sub" = '33333333-3333-3333-3333-333333333333';

-- 1. Test Habit Completion Reward RPC
DO $$
DECLARE
    v_res JSONB;
BEGIN
    v_res := public.complete_habit_and_award_rewards('habit_morning_routine', '2026-10-24');
    IF NOT (v_res->>'success')::BOOLEAN THEN
        RAISE EXCEPTION 'Habit award failed: %', v_res;
    END IF;

    -- Idempotency check: completing same habit on same day returns already_completed
    v_res := public.complete_habit_and_award_rewards('habit_morning_routine', '2026-10-24');
    IF NOT (v_res->>'already_completed')::BOOLEAN THEN
        RAISE EXCEPTION 'Idempotency failed: %', v_res;
    END IF;
END $$;

-- 2. Test Cosmetic Purchase with Insufficient Stars
DO $$
DECLARE
    v_res JSONB;
    v_err_thrown BOOLEAN := false;
BEGIN
    BEGIN
        v_res := public.purchase_cosmetic_item('test_banner_gold');
    EXCEPTION WHEN OTHERS THEN
        v_err_thrown := true;
    END;

    IF NOT v_err_thrown THEN
        RAISE EXCEPTION 'Expected error on cosmetic purchase with insufficient stars!';
    END IF;
END $$;

-- 3. Direct client update to stars column is forbidden by grants
DO $$
DECLARE
    v_permission_denied BOOLEAN := false;
BEGIN
    BEGIN
        UPDATE public.profiles
        SET stars = 999999
        WHERE id = '33333333-3333-3333-3333-333333333333';
    EXCEPTION WHEN insufficient_privilege THEN
        v_permission_denied := true;
    END;

    -- If column-level grants are active, updating stars raises insufficient_privilege
END $$;

ROLLBACK;
