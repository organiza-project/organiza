-- ============================================================================
-- Migration 0004: Social & Challenge RPCs
-- Hardened security-definer procedures with idempotency, balance checks,
-- zero-trust caller identity via auth.uid(), and recursion-safe duel logic.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Send Friend Request (with bidirectional reciprocity & auto-accept)
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.send_friend_request(p_friend_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_caller_id UUID;
    v_existing_id UUID;
    v_existing_status TEXT;
    v_is_sender BOOLEAN;
BEGIN
    v_caller_id := auth.uid();
    IF v_caller_id IS NULL THEN
        RAISE EXCEPTION 'Authentication required.' USING ERRCODE = '42501';
    END IF;

    IF v_caller_id = p_friend_id THEN
        RAISE EXCEPTION 'Cannot send friend request to yourself.' USING ERRCODE = '22023';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM public.profiles WHERE id = p_friend_id) THEN
        RAISE EXCEPTION 'Target user not found.' USING ERRCODE = 'P0002';
    END IF;

    -- Look for existing relationship in either direction
    SELECT id, status, (user_id = v_caller_id)
    INTO v_existing_id, v_existing_status, v_is_sender
    FROM public.friendships
    WHERE (user_id = v_caller_id AND friend_id = p_friend_id)
       OR (user_id = p_friend_id AND friend_id = v_caller_id)
    LIMIT 1;

    IF v_existing_id IS NOT NULL THEN
        IF v_existing_status = 'accepted' THEN
            RETURN jsonb_build_object(
                'status', 'already_friends',
                'friendship_id', v_existing_id
            );
        ELSIF v_existing_status = 'blocked' THEN
            RAISE EXCEPTION 'Unable to send friend request.' USING ERRCODE = '42501';
        ELSIF v_existing_status = 'pending' THEN
            -- If the other person already sent a pending request to caller, auto-accept reciprocally!
            IF NOT v_is_sender THEN
                UPDATE public.friendships
                SET status = 'accepted', updated_at = timezone('utc'::text, now())
                WHERE id = v_existing_id;

                RETURN jsonb_build_object(
                    'status', 'accepted',
                    'friendship_id', v_existing_id,
                    'reciprocal', true
                );
            ELSE
                -- Already sent by caller
                RETURN jsonb_build_object(
                    'status', 'pending',
                    'friendship_id', v_existing_id,
                    'already_sent', true
                );
            END IF;
        END IF;
    END IF;

    -- Insert new pending request
    INSERT INTO public.friendships (user_id, friend_id, status)
    VALUES (v_caller_id, p_friend_id, 'pending')
    RETURNING id INTO v_existing_id;

    RETURN jsonb_build_object(
        'status', 'pending',
        'friendship_id', v_existing_id,
        'already_sent', false
    );
END;
$$;

-- ----------------------------------------------------------------------------
-- 2. Accept Friend Request
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.accept_friend_request(p_friendship_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_caller_id UUID;
    v_updated INTEGER;
BEGIN
    v_caller_id := auth.uid();
    IF v_caller_id IS NULL THEN
        RAISE EXCEPTION 'Authentication required.' USING ERRCODE = '42501';
    END IF;

    UPDATE public.friendships
    SET status = 'accepted', updated_at = timezone('utc'::text, now())
    WHERE id = p_friendship_id
      AND friend_id = v_caller_id
      AND status = 'pending';

    GET DIAGNOSTICS v_updated = ROW_COUNT;

    IF v_updated = 0 THEN
        -- Check if it was already accepted
        IF EXISTS (
            SELECT 1 FROM public.friendships
            WHERE id = p_friendship_id
              AND (friend_id = v_caller_id OR user_id = v_caller_id)
              AND status = 'accepted'
        ) THEN
            RETURN jsonb_build_object('success', true, 'status', 'already_accepted');
        END IF;

        RAISE EXCEPTION 'Pending friend request not found or unauthorized.' USING ERRCODE = 'P0002';
    END IF;

    RETURN jsonb_build_object('success', true, 'status', 'accepted');
END;
$$;

-- ----------------------------------------------------------------------------
-- 3. Create Challenge (With balance check and wager escrow)
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.create_challenge(
    p_opponent_id UUID,
    p_challenge_type TEXT,
    p_target_metric TEXT,
    p_duration_days INTEGER DEFAULT 7,
    p_wager_stars INTEGER DEFAULT 0
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_caller_id UUID;
    v_caller_stars INTEGER;
    v_challenge_id UUID;
BEGIN
    v_caller_id := auth.uid();
    IF v_caller_id IS NULL THEN
        RAISE EXCEPTION 'Authentication required.' USING ERRCODE = '42501';
    END IF;

    IF v_caller_id = p_opponent_id THEN
        RAISE EXCEPTION 'Cannot challenge yourself.' USING ERRCODE = '22023';
    END IF;

    IF p_wager_stars < 0 THEN
        RAISE EXCEPTION 'Wager cannot be negative.' USING ERRCODE = '22023';
    END IF;

    IF p_duration_days <= 0 OR p_duration_days > 90 THEN
        RAISE EXCEPTION 'Challenge duration must be between 1 and 90 days.' USING ERRCODE = '22023';
    END IF;

    -- Verify opponent exists
    IF NOT EXISTS (SELECT 1 FROM public.profiles WHERE id = p_opponent_id) THEN
        RAISE EXCEPTION 'Opponent does not exist.' USING ERRCODE = 'P0002';
    END IF;

    -- Verify caller has enough stars for wager
    SELECT stars INTO v_caller_stars
    FROM public.profiles
    WHERE id = v_caller_id
    FOR UPDATE;

    IF v_caller_stars < p_wager_stars THEN
        RAISE EXCEPTION 'Insufficient stars for wager. Required: %, Available: %',
            p_wager_stars, v_caller_stars
            USING ERRCODE = '22003';
    END IF;

    -- Deduct escrow if wager > 0
    IF p_wager_stars > 0 THEN
        UPDATE public.profiles
        SET stars = stars - p_wager_stars,
            updated_at = timezone('utc'::text, now())
        WHERE id = v_caller_id;
    END IF;

    INSERT INTO public.challenges (
        creator_id,
        opponent_id,
        challenge_type,
        target_metric,
        duration_days,
        wager_stars,
        status,
        expires_at
    )
    VALUES (
        v_caller_id,
        p_opponent_id,
        p_challenge_type,
        p_target_metric,
        p_duration_days,
        p_wager_stars,
        'pending',
        timezone('utc'::text, now()) + (p_duration_days || ' days')::interval
    )
    RETURNING id INTO v_challenge_id;

    RETURN jsonb_build_object(
        'success', true,
        'challenge_id', v_challenge_id,
        'status', 'pending',
        'wager_stars', p_wager_stars
    );
END;
$$;

-- ----------------------------------------------------------------------------
-- 4. Accept Challenge (Locks opponent wager, sets active)
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.accept_challenge(p_challenge_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_caller_id UUID;
    v_challenge RECORD;
    v_opponent_stars INTEGER;
BEGIN
    v_caller_id := auth.uid();
    IF v_caller_id IS NULL THEN
        RAISE EXCEPTION 'Authentication required.' USING ERRCODE = '42501';
    END IF;

    SELECT * INTO v_challenge
    FROM public.challenges
    WHERE id = p_challenge_id
    FOR UPDATE;

    IF v_challenge.id IS NULL THEN
        RAISE EXCEPTION 'Challenge not found.' USING ERRCODE = 'P0002';
    END IF;

    IF v_challenge.opponent_id <> v_caller_id THEN
        RAISE EXCEPTION 'Unauthorized to accept this challenge.' USING ERRCODE = '42501';
    END IF;

    IF v_challenge.status = 'active' THEN
        RETURN jsonb_build_object('success', true, 'status', 'already_active');
    END IF;

    IF v_challenge.status <> 'pending' THEN
        RAISE EXCEPTION 'Challenge cannot be accepted in status %', v_challenge.status USING ERRCODE = '22023';
    END IF;

    -- Check opponent's star balance for wager
    IF v_challenge.wager_stars > 0 THEN
        SELECT stars INTO v_opponent_stars
        FROM public.profiles
        WHERE id = v_caller_id
        FOR UPDATE;

        IF v_opponent_stars < v_challenge.wager_stars THEN
            RAISE EXCEPTION 'Insufficient stars for wager. Required: %, Available: %',
                v_challenge.wager_stars, v_opponent_stars
                USING ERRCODE = '22003';
        END IF;

        UPDATE public.profiles
        SET stars = stars - v_challenge.wager_stars,
            updated_at = timezone('utc'::text, now())
        WHERE id = v_caller_id;
    END IF;

    -- Update challenge status
    UPDATE public.challenges
    SET status = 'active',
        expires_at = timezone('utc'::text, now()) + (v_challenge.duration_days || ' days')::interval
    WHERE id = p_challenge_id;

    RETURN jsonb_build_object(
        'success', true,
        'challenge_id', p_challenge_id,
        'status', 'active'
    );
END;
$$;

-- ----------------------------------------------------------------------------
-- 5. Settle Duel Challenge (Atomic wager distribution, idempotent)
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.settle_duel_challenge(p_challenge_id UUID)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, pg_temp
AS $$
DECLARE
    v_caller_id UUID;
    v_challenge RECORD;
    v_winner_id UUID;
    v_total_pot INTEGER;
    v_result_type TEXT;
BEGIN
    v_caller_id := auth.uid();
    IF v_caller_id IS NULL THEN
        RAISE EXCEPTION 'Authentication required.' USING ERRCODE = '42501';
    END IF;

    SELECT * INTO v_challenge
    FROM public.challenges
    WHERE id = p_challenge_id
    FOR UPDATE;

    IF v_challenge.id IS NULL THEN
        RAISE EXCEPTION 'Challenge not found.' USING ERRCODE = 'P0002';
    END IF;

    -- Caller must be one of the participants
    IF v_caller_id <> v_challenge.creator_id AND v_caller_id <> v_challenge.opponent_id THEN
        RAISE EXCEPTION 'Unauthorized to settle this challenge.' USING ERRCODE = '42501';
    END IF;

    -- Idempotency check: if already settled, return stored outcome
    IF v_challenge.settled_at IS NOT NULL OR v_challenge.status = 'completed' THEN
        RETURN jsonb_build_object(
            'success', true,
            'already_settled', true,
            'challenge_id', p_challenge_id,
            'status', 'completed',
            'winner_id', v_challenge.winner_id,
            'settled_at', v_challenge.settled_at
        );
    END IF;

    IF v_challenge.status <> 'active' THEN
        RAISE EXCEPTION 'Cannot settle challenge in status %', v_challenge.status USING ERRCODE = '22023';
    END IF;

    v_total_pot := v_challenge.wager_stars * 2;

    -- Evaluate outcome
    IF v_challenge.creator_progress > v_challenge.opponent_progress THEN
        v_winner_id := v_challenge.creator_id;
        v_result_type := 'creator_won';

        IF v_total_pot > 0 THEN
            UPDATE public.profiles
            SET stars = stars + v_total_pot,
                updated_at = timezone('utc'::text, now())
            WHERE id = v_winner_id;
        END IF;
    ELSIF v_challenge.opponent_progress > v_challenge.creator_progress THEN
        v_winner_id := v_challenge.opponent_id;
        v_result_type := 'opponent_won';

        IF v_total_pot > 0 THEN
            UPDATE public.profiles
            SET stars = stars + v_total_pot,
                updated_at = timezone('utc'::text, now())
            WHERE id = v_winner_id;
        END IF;
    ELSE
        -- Tie: Refund both participants their escrowed wager
        v_winner_id := NULL;
        v_result_type := 'tie';

        IF v_challenge.wager_stars > 0 THEN
            UPDATE public.profiles
            SET stars = stars + v_challenge.wager_stars,
                updated_at = timezone('utc'::text, now())
            WHERE id = v_challenge.creator_id;

            UPDATE public.profiles
            SET stars = stars + v_challenge.wager_stars,
                updated_at = timezone('utc'::text, now())
            WHERE id = v_challenge.opponent_id;
        END IF;
    END IF;

    -- Mark settled
    UPDATE public.challenges
    SET status = 'completed',
        winner_id = v_winner_id,
        settled_at = timezone('utc'::text, now())
    WHERE id = p_challenge_id;

    RETURN jsonb_build_object(
        'success', true,
        'already_settled', false,
        'challenge_id', p_challenge_id,
        'status', 'completed',
        'result_type', v_result_type,
        'winner_id', v_winner_id,
        'wager_distributed', v_total_pot
    );
END;
$$;

-- ----------------------------------------------------------------------------
-- Revoke execution from anonymous public; Grant to authenticated users
-- ----------------------------------------------------------------------------
REVOKE ALL ON FUNCTION public.send_friend_request(UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.send_friend_request(UUID) TO authenticated, service_role;

REVOKE ALL ON FUNCTION public.accept_friend_request(UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.accept_friend_request(UUID) TO authenticated, service_role;

REVOKE ALL ON FUNCTION public.create_challenge(UUID, TEXT, TEXT, INTEGER, INTEGER) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.create_challenge(UUID, TEXT, TEXT, INTEGER, INTEGER) TO authenticated, service_role;

REVOKE ALL ON FUNCTION public.accept_challenge(UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.accept_challenge(UUID) TO authenticated, service_role;

REVOKE ALL ON FUNCTION public.settle_duel_challenge(UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.settle_duel_challenge(UUID) TO authenticated, service_role;
