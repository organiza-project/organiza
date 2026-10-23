-- ============================================================================
-- SQL Test Suite: Row Level Security (RLS) Isolation
-- Verifies owner access, non-owner isolation, and public profile view restrictions.
-- ============================================================================

BEGIN;

-- Test Setup: Create mock auth users
INSERT INTO auth.users (id, email)
VALUES
    ('11111111-1111-1111-1111-111111111111', 'alice@test.organiza.app'),
    ('22222222-2222-2222-2222-222222222222', 'bob@test.organiza.app')
ON CONFLICT (id) DO NOTHING;

-- 1. Test Profile Creation & Public Leaderboard Visibility
SET LOCAL ROLE authenticated;
SET LOCAL "request.jwt.claim.sub" = '11111111-1111-1111-1111-111111111111';

-- Alice can read Bob's public profile fields (for leaderboard/social)
DO $$
DECLARE
    v_bob_username TEXT;
BEGIN
    SELECT username INTO v_bob_username
    FROM public.profiles
    WHERE id = '22222222-2222-2222-2222-222222222222';
    -- Expected: readable (leaderboards require reading profiles)
END $$;

-- Alice CANNOT update Bob's profile
DO $$
BEGIN
    UPDATE public.profiles
    SET display_name = 'Hacked by Alice'
    WHERE id = '22222222-2222-2222-2222-222222222222';

    IF FOUND THEN
        RAISE EXCEPTION 'RLS Failure: Alice updated Bob profile!';
    END IF;
END $$;

-- 2. Test Tasks Isolation
-- Alice creates a task
INSERT INTO public.tasks (id, user_id, title)
VALUES ('task_alice_1', '11111111-1111-1111-1111-111111111111', 'Alice Secret Task');

-- Switch identity to Bob
SET LOCAL "request.jwt.claim.sub" = '22222222-2222-2222-2222-222222222222';

-- Bob CANNOT read Alice's tasks
DO $$
DECLARE
    v_found INTEGER;
BEGIN
    SELECT COUNT(*) INTO v_found
    FROM public.tasks
    WHERE id = 'task_alice_1';

    IF v_found > 0 THEN
        RAISE EXCEPTION 'RLS Failure: Bob could read Alice task!';
    END IF;
END $$;

-- Bob CANNOT delete Alice's tasks
DO $$
BEGIN
    DELETE FROM public.tasks WHERE id = 'task_alice_1';
    IF FOUND THEN
        RAISE EXCEPTION 'RLS Failure: Bob deleted Alice task!';
    END IF;
END $$;

-- 3. Anonymous role has zero access to user tables
SET LOCAL ROLE anon;
DO $$
DECLARE
    v_tasks_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO v_tasks_count FROM public.tasks;
    IF v_tasks_count > 0 THEN
        RAISE EXCEPTION 'RLS Failure: Anonymous role could read private tasks!';
    END IF;
END $$;

ROLLBACK;
