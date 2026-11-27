-- ============================================================================
-- Unique Case-Insensitive Display Names & Emails + 3 Free Name Suggestions RPC
-- ============================================================================

-- 1. Enforce case-insensitive unique display names on public.profiles
create unique index if not exists profiles_username_lower_unique_idx
    on public.profiles (lower(trim(username)));

-- 2. Enforce case-insensitive unique non-empty emails on public.profiles
create unique index if not exists profiles_email_lower_unique_idx
    on public.profiles (lower(trim(google_email)))
    where trim(google_email) <> '';

-- 3. Harden handle_new_auth_user() so OAuth signups with colliding full_name auto-deduplicate
create or replace function private.handle_new_auth_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    base_name text;
    candidate text;
    suffix integer := 1;
begin
    base_name := coalesce(
        nullif(trim(new.raw_user_meta_data->>'username'), ''),
        nullif(trim(new.raw_user_meta_data->>'full_name'), ''),
        split_part(coalesce(new.email, 'tester'), '@', 1)
    );
    if char_length(base_name) < 2 then
        base_name := 'Tester';
    end if;
    base_name := left(base_name, 34);
    candidate := base_name;

    while exists (
        select 1 from public.profiles p
        where lower(trim(p.username)) = lower(trim(candidate))
    ) loop
        suffix := suffix + 1;
        candidate := left(base_name, 32) || '_' || suffix::text;
    end loop;

    insert into public.profiles (id, username, google_email, karma_points)
    values (new.id, left(candidate, 40), coalesce(new.email, ''), 50)
    on conflict (id) do nothing;

    insert into public.karma_ledger (user_id, delta, reason, note)
    values (new.id, 50, 'SIGNUP_WELCOME', 'Welcome starter Karma credit');

    return new;
end;
$$;

-- 4. Public RPC: Check display name availability & return 3 guaranteed-free suggestions when taken
create or replace function public.check_display_name_availability(
    p_display_name text
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_clean text := trim(coalesce(p_display_name, ''));
    v_is_taken boolean;
    v_base text;
    v_suggestions text[] := array[]::text[];
    v_candidate text;
    v_pool text[];
    v_i integer := 1;
    v_rand integer;
begin
    if char_length(v_clean) < 2 then
        return jsonb_build_object(
            'available', false,
            'error', 'Display name must be at least 2 characters.',
            'suggestions', '[]'::jsonb
        );
    end if;

    if char_length(v_clean) > 40 then
        return jsonb_build_object(
            'available', false,
            'error', 'Display name must be 40 characters or fewer.',
            'suggestions', '[]'::jsonb
        );
    end if;

    select exists (
        select 1 from public.profiles
        where lower(trim(username)) = lower(v_clean)
    ) into v_is_taken;

    if not v_is_taken then
        return jsonb_build_object(
            'available', true,
            'suggestions', '[]'::jsonb
        );
    end if;

    v_base := left(v_clean, 30);
    v_pool := array[
        v_base || 'Dev',
        v_base || 'QA',
        v_base || 'Hub',
        v_base || '_26',
        v_base || '2026',
        v_base || '_Pro',
        v_base || '_Lab'
    ];

    -- First try curated friendly suffixes
    foreach v_candidate in array v_pool loop
        exit when coalesce(array_length(v_suggestions, 1), 0) >= 3;
        if not exists (
            select 1 from public.profiles
            where lower(trim(username)) = lower(trim(v_candidate))
        ) and not (v_candidate = any(v_suggestions)) then
            v_suggestions := array_append(v_suggestions, v_candidate);
        end if;
    end loop;

    -- Fallback numeric suffixes if curated pool was all taken
    while coalesce(array_length(v_suggestions, 1), 0) < 3 and v_i <= 25 loop
        v_rand := 10 + floor(random() * 890)::integer;
        v_candidate := left(v_base, 32) || '_' || v_rand::text;
        if not exists (
            select 1 from public.profiles
            where lower(trim(username)) = lower(trim(v_candidate))
        ) and not (v_candidate = any(v_suggestions)) then
            v_suggestions := array_append(v_suggestions, v_candidate);
        end if;
        v_i := v_i + 1;
    end loop;

    return jsonb_build_object(
        'available', false,
        'suggestions', to_jsonb(v_suggestions)
    );
end;
$$;

revoke execute on function public.check_display_name_availability(text) from public;
grant execute on function public.check_display_name_availability(text) to anon, authenticated;
