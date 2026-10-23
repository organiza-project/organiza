-- ============================================================================
-- Migration 0005: In-App User Account Deletion RPC
-- Allows authenticated users to irrevocably delete their account and cascade-wipe all data.
-- ============================================================================

CREATE OR REPLACE FUNCTION public.delete_user_account()
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth, pg_temp
AS $$
DECLARE
    v_user_id UUID;
BEGIN
    v_user_id := auth.uid();
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Authentication required.' USING ERRCODE = '42501';
    END IF;

    -- Delete from public.profiles (triggers ON DELETE CASCADE across all user tables)
    DELETE FROM public.profiles WHERE id = v_user_id;

    -- Delete from auth.users to invalidate credentials and tokens permanently
    DELETE FROM auth.users WHERE id = v_user_id;

    RETURN jsonb_build_object(
        'success', true,
        'message', 'Account and associated records successfully deleted.'
    );
END;
$$;

REVOKE ALL ON FUNCTION public.delete_user_account() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.delete_user_account() TO authenticated, service_role;
