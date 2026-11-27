-- ============================================================================
-- Self-Service Account & Data Deletion RPC (Google Play Data Safety Compliant)
-- ============================================================================
-- Permanently deletes the caller's auth.users row; foreign keys with
-- ON DELETE CASCADE remove profiles, enrollments, daily_verifications,
-- verification_sessions, feedback_items, feedback_upvotes, karma_ledger,
-- app_listings, test_missions, and mission_completions immediately.

create or replace function public.delete_own_account()
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := (select auth.uid());
begin
    if v_uid is null then
        raise exception 'Authentication required';
    end if;

    delete from auth.users where id = v_uid;

    if not found then
        raise exception 'Account not found';
    end if;

    return jsonb_build_object(
        'deleted', true,
        'user_id', v_uid
    );
end;
$$;

revoke execute on function public.delete_own_account() from public, anon;
grant execute on function public.delete_own_account() to authenticated;
