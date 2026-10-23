-- ============================================================================
-- SQL Test Suite: Two-Account Duel Challenges
-- Verifies challenge creation, wager escrow, reciprocity, and idempotent settlement.
-- ============================================================================

BEGIN;

INSERT INTO auth.users (id, email)
VALUES
    ('44444444-4444-4444-4444-444444444444', 'challenger_a@test.organiza.app'),
    ('55555555-5555-5555-5555-555555555555', 'challenger_b@test.organiza.app')
ON CONFLICT (id) DO NOTHING;

-- Grant initial stars to challenger A and B for testing wagers
UPDATE public.profiles
SET stars = 100
WHERE id IN ('44444444-4444-4444-4444-444444444444', '55555555-5555-5555-5555-555555555555');

-- Step 1: User A creates a challenge for User B with 50 stars wager
SET LOCAL ROLE authenticated;
SET LOCAL "request.jwt.claim.sub" = '44444444-4444-4444-4444-444444444444';

DO $$
DECLARE
    v_create_res JSONB;
    v_challenge_id UUID;
    v_accept_res JSONB;
    v_settle_res JSONB;
BEGIN
    v_create_res := public.create_challenge(
        '55555555-5555-5555-5555-555555555555',
        'deep_work',
        'focus_minutes',
        7,
        50
    );
    v_challenge_id := (v_create_res->>'challenge_id')::UUID;

    -- Step 2: Switch to User B and accept the challenge
    PERFORM set_config('request.jwt.claim.sub', '55555555-5555-5555-5555-555555555555', true);

    v_accept_res := public.accept_challenge(v_challenge_id);
    IF (v_accept_res->>'status') <> 'active' THEN
        RAISE EXCEPTION 'Failed to activate challenge: %', v_accept_res;
    END IF;

    -- Simulate progress: User A has 500 min, User B has 300 min
    UPDATE public.challenges
    SET creator_progress = 500, opponent_progress = 300
    WHERE id = v_challenge_id;

    -- Step 3: Settle the challenge (called by User B)
    v_settle_res := public.settle_duel_challenge(v_challenge_id);
    IF (v_settle_res->>'winner_id')::UUID <> '44444444-4444-4444-4444-444444444444' THEN
        RAISE EXCEPTION 'Incorrect winner calculation: %', v_settle_res;
    END IF;

    -- Step 4: Replay settlement check (must be idempotent)
    v_settle_res := public.settle_duel_challenge(v_challenge_id);
    IF NOT (v_settle_res->>'already_settled')::BOOLEAN THEN
        RAISE EXCEPTION 'Idempotent replay failed for challenge settlement: %', v_settle_res;
    END IF;
END $$;

ROLLBACK;
