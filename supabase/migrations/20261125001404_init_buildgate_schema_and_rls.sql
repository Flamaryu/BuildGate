-- BuildGate Ground-Up Schema, Indexes, Views, Storage & Strict RLS
-- Follows supabase-postgres-best-practices & Supabase Security Checklist

create schema if not exists private;
revoke all on schema private from public, anon, authenticated;

-- ============================================================================
-- 1. TABLES & CONSTRAINTS
-- ============================================================================

create table public.profiles (
    id uuid primary key references auth.users(id) on delete cascade,
    username text not null check (char_length(trim(username)) between 2 and 40),
    google_email text not null default '',
    karma_points integer not null default 50 check (karma_points >= 0),
    trusted_score numeric(3,2) not null default 5.00 check (trusted_score between 0.00 and 5.00),
    completed_campaigns_count integer not null default 0 check (completed_campaigns_count >= 0),
    registered_devices jsonb not null default '[]'::jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table public.app_listings (
    id bigint generated always as identity primary key,
    owner_id uuid not null references public.profiles(id) on delete cascade,
    package_name text not null unique check (package_name ~ '^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z0-9_]+)+$'),
    title text not null check (char_length(trim(title)) between 2 and 80),
    short_summary text not null default '',
    play_store_opt_in_url text not null check (play_store_opt_in_url ~ '^https://'),
    google_group_url text not null check (google_group_url ~ '^https://'),
    deep_link_scheme text check (deep_link_scheme is null or deep_link_scheme ~ '^[a-z][a-z0-9+.-]*$'),
    current_build_version text not null default '1.0.0',
    min_version_code bigint not null default 1 check (min_version_code >= 1),
    target_days integer not null default 14 check (target_days between 7 and 30),
    target_testers integer not null default 16 check (target_testers >= 12),
    category text not null default 'Productivity',
    icon_url text,
    is_active boolean not null default true,
    created_at timestamptz not null default now()
);

create table public.test_missions (
    id bigint generated always as identity primary key,
    app_id bigint not null references public.app_listings(id) on delete cascade,
    build_version text not null,
    title text not null check (char_length(trim(title)) >= 3),
    instructions text not null default '',
    is_required boolean not null default true,
    created_at timestamptz not null default now()
);

create table public.mission_completions (
    mission_id bigint not null references public.test_missions(id) on delete cascade,
    tester_id uuid not null references public.profiles(id) on delete cascade,
    completed_at timestamptz not null default now(),
    primary key (mission_id, tester_id)
);

create table public.enrollments (
    id bigint generated always as identity primary key,
    app_id bigint not null references public.app_listings(id) on delete cascade,
    tester_id uuid not null references public.profiles(id) on delete cascade,
    has_joined_google_group boolean not null default false,
    has_accepted_play_opt_in boolean not null default false,
    verified_days_count integer not null default 0 check (verified_days_count >= 0),
    current_streak integer not null default 0 check (current_streak >= 0),
    missed_days_used integer not null default 0 check (missed_days_used >= 0),
    last_verified_utc_date date,
    last_seen_version_code bigint,
    status text not null default 'PENDING_INSTALL'
        check (status in ('PENDING_INSTALL', 'ACTIVE', 'COMPLETED', 'DROPPED_UNINSTALLED', 'DROPPED_INACTIVE')),
    enrolled_at timestamptz not null default now(),
    unique (app_id, tester_id)
);

create table public.verification_sessions (
    id bigint generated always as identity primary key,
    enrollment_id bigint not null references public.enrollments(id) on delete cascade,
    tester_id uuid not null references public.profiles(id) on delete cascade,
    session_nonce text not null unique,
    started_at timestamptz not null default now(),
    expires_at timestamptz not null default (now() + interval '30 minutes'),
    deeplink_verified_at timestamptz,
    completed_at timestamptz
);

create table public.daily_verifications (
    id bigint generated always as identity primary key,
    enrollment_id bigint not null references public.enrollments(id) on delete cascade,
    day_number integer not null check (day_number >= 1),
    session_token text not null,
    verified_utc_date date not null,
    active_duration_seconds integer not null default 60 check (active_duration_seconds >= 0),
    karma_awarded integer not null default 10 check (karma_awarded >= 0),
    method text not null default 'ZERO_SDK_SESSION'
        check (method in ('ZERO_SDK_SESSION', 'VERIFIED_DEEPLINK_HANDSHAKE')),
    verified_at timestamptz not null default now(),
    unique (enrollment_id, verified_utc_date)
);

create table public.feedback_items (
    id bigint generated always as identity primary key,
    app_id bigint not null references public.app_listings(id) on delete cascade,
    author_id uuid not null references public.profiles(id) on delete cascade,
    session_id bigint references public.verification_sessions(id) on delete set null,
    build_version text not null,
    category text not null check (category in ('BUG', 'UX_FRICTION', 'IDEA')),
    status text not null default 'OPEN' check (status in ('OPEN', 'IN_PROGRESS', 'FIXED', 'WONT_FIX')),
    title text not null check (char_length(trim(title)) >= 3),
    description text not null check (char_length(trim(description)) >= 3),
    device_metadata jsonb not null,
    screenshot_path text,
    video_clip_path text,
    media_duration_ms integer check (media_duration_ms is null or media_duration_ms between 0 and 30000),
    upvotes integer not null default 1 check (upvotes >= 0),
    bounty_awarded boolean not null default false,
    created_at timestamptz not null default now()
);

create table public.feedback_upvotes (
    feedback_id bigint not null references public.feedback_items(id) on delete cascade,
    user_id uuid not null references public.profiles(id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (feedback_id, user_id)
);

create table public.karma_ledger (
    id bigint generated always as identity primary key,
    user_id uuid not null references public.profiles(id) on delete cascade,
    delta integer not null,
    reason text not null check (reason in (
        'SIGNUP_WELCOME',
        'DAILY_VERIFICATION',
        'DEEPLINK_VERIFIED_BONUS',
        'TRIAGED_BUG_BOUNTY',
        'DAY_14_COMPLETION_BONUS'
    )),
    app_id bigint references public.app_listings(id) on delete set null,
    note text not null default '',
    created_at timestamptz not null default now()
);

-- ============================================================================
-- 2. FOREIGN KEY & QUERY PERFORMANCE INDEXES
-- ============================================================================

create index app_listings_owner_id_idx on public.app_listings (owner_id);
create index app_listings_active_created_idx on public.app_listings (is_active, created_at desc);

create index test_missions_app_id_idx on public.test_missions (app_id);
create index mission_completions_tester_id_idx on public.mission_completions (tester_id);

create index enrollments_tester_id_idx on public.enrollments (tester_id);
create index enrollments_app_id_status_idx on public.enrollments (app_id, status);

create index verification_sessions_enrollment_id_idx on public.verification_sessions (enrollment_id);
create index verification_sessions_tester_id_idx on public.verification_sessions (tester_id);

create index daily_verifications_enrollment_id_idx on public.daily_verifications (enrollment_id);

create index feedback_items_app_id_status_idx on public.feedback_items (app_id, status, created_at desc);
create index feedback_items_author_id_idx on public.feedback_items (author_id);
create index feedback_items_session_id_idx on public.feedback_items (session_id);

create index feedback_upvotes_user_id_idx on public.feedback_upvotes (user_id);
create index karma_ledger_user_id_created_idx on public.karma_ledger (user_id, created_at desc);
create index karma_ledger_app_id_idx on public.karma_ledger (app_id);

-- ============================================================================
-- 3. SECURITY INVOKER VIEWS (Respects Caller RLS)
-- ============================================================================

create or replace view public.app_discovery_feed
with (security_invoker = true)
as
select
    a.id,
    a.owner_id,
    a.package_name,
    a.title,
    a.short_summary,
    a.play_store_opt_in_url,
    a.google_group_url,
    a.deep_link_scheme,
    a.current_build_version,
    a.min_version_code,
    a.target_days,
    a.target_testers,
    a.category,
    a.icon_url,
    a.created_at,
    coalesce(p.karma_points, 50) as owner_karma_balance,
    count(e.id) filter (where e.status in ('ACTIVE', 'COMPLETED'))::integer as active_testers_count,
    count(e.id) filter (where e.status = 'COMPLETED')::integer as completed_testers_count,
    ((coalesce(p.karma_points, 50) * 2) + (count(e.id) filter (where e.status in ('ACTIVE', 'COMPLETED'))::integer * 5)) as discovery_score
from public.app_listings a
left join public.profiles p on p.id = a.owner_id
left join public.enrollments e on e.app_id = a.id
where a.is_active = true
group by a.id, p.karma_points;

-- ============================================================================
-- 4. ROW LEVEL SECURITY & EXPLICIT TABLE GRANTS
-- ============================================================================

alter table public.profiles enable row level security;
alter table public.app_listings enable row level security;
alter table public.test_missions enable row level security;
alter table public.mission_completions enable row level security;
alter table public.enrollments enable row level security;
alter table public.verification_sessions enable row level security;
alter table public.daily_verifications enable row level security;
alter table public.feedback_items enable row level security;
alter table public.feedback_upvotes enable row level security;
alter table public.karma_ledger enable row level security;

grant usage on schema public to anon, authenticated;
grant select on public.app_discovery_feed to anon, authenticated;
grant select on public.profiles, public.app_listings, public.test_missions, public.enrollments, public.daily_verifications, public.feedback_items, public.feedback_upvotes to anon, authenticated;
grant select on public.mission_completions, public.verification_sessions, public.karma_ledger to authenticated;

grant insert on public.app_listings, public.test_missions, public.mission_completions, public.enrollments, public.feedback_items, public.feedback_upvotes to authenticated;
grant update (username, registered_devices, updated_at) on public.profiles to authenticated;
grant update on public.app_listings, public.test_missions to authenticated;
grant update (has_joined_google_group, has_accepted_play_opt_in, last_seen_version_code) on public.enrollments to authenticated;
grant delete on public.mission_completions, public.feedback_upvotes to authenticated;

-- PROFILES policies
create policy "profiles_select_public" on public.profiles
    for select to anon, authenticated
    using (true);

create policy "profiles_update_own" on public.profiles
    for update to authenticated
    using ((select auth.uid()) = id)
    with check ((select auth.uid()) = id);

-- APP LISTINGS policies
create policy "app_listings_select_public" on public.app_listings
    for select to anon, authenticated
    using (is_active = true or (select auth.uid()) = owner_id);

create policy "app_listings_insert_owner" on public.app_listings
    for insert to authenticated
    with check ((select auth.uid()) = owner_id);

create policy "app_listings_update_owner" on public.app_listings
    for update to authenticated
    using ((select auth.uid()) = owner_id)
    with check ((select auth.uid()) = owner_id);

-- TEST MISSIONS policies
create policy "test_missions_select_public" on public.test_missions
    for select to anon, authenticated
    using (true);

create policy "test_missions_insert_app_owner" on public.test_missions
    for insert to authenticated
    with check (
        exists (
            select 1 from public.app_listings a
            where a.id = app_id and a.owner_id = (select auth.uid())
        )
    );

create policy "test_missions_update_app_owner" on public.test_missions
    for update to authenticated
    using (
        exists (
            select 1 from public.app_listings a
            where a.id = app_id and a.owner_id = (select auth.uid())
        )
    )
    with check (
        exists (
            select 1 from public.app_listings a
            where a.id = app_id and a.owner_id = (select auth.uid())
        )
    );

-- MISSION COMPLETIONS policies
create policy "mission_completions_select_own_or_owner" on public.mission_completions
    for select to authenticated
    using (
        (select auth.uid()) = tester_id
        or exists (
            select 1 from public.test_missions m
            join public.app_listings a on a.id = m.app_id
            where m.id = mission_id and a.owner_id = (select auth.uid())
        )
    );

create policy "mission_completions_insert_own" on public.mission_completions
    for insert to authenticated
    with check ((select auth.uid()) = tester_id);

create policy "mission_completions_delete_own" on public.mission_completions
    for delete to authenticated
    using ((select auth.uid()) = tester_id);

-- ENROLLMENTS policies (streak & verified_days_count locked out via column-level grant + RPCs)
create policy "enrollments_select_visible" on public.enrollments
    for select to anon, authenticated
    using (true);

create policy "enrollments_insert_own" on public.enrollments
    for insert to authenticated
    with check ((select auth.uid()) = tester_id and verified_days_count = 0 and current_streak = 0);

create policy "enrollments_update_onboarding_own" on public.enrollments
    for update to authenticated
    using ((select auth.uid()) = tester_id)
    with check ((select auth.uid()) = tester_id);

-- VERIFICATION SESSIONS & DAILY VERIFICATIONS (Read-only via RLS; written via RPC / Edge Function)
create policy "verification_sessions_select_own" on public.verification_sessions
    for select to authenticated
    using ((select auth.uid()) = tester_id);

create policy "daily_verifications_select_public" on public.daily_verifications
    for select to anon, authenticated
    using (true);

-- FEEDBACK ITEMS & UPVOTES policies
create policy "feedback_items_select_public" on public.feedback_items
    for select to anon, authenticated
    using (true);

create policy "feedback_items_insert_author" on public.feedback_items
    for insert to authenticated
    with check ((select auth.uid()) = author_id and bounty_awarded = false);

create policy "feedback_upvotes_select_public" on public.feedback_upvotes
    for select to anon, authenticated
    using (true);

create policy "feedback_upvotes_insert_own" on public.feedback_upvotes
    for insert to authenticated
    with check ((select auth.uid()) = user_id);

create policy "feedback_upvotes_delete_own" on public.feedback_upvotes
    for delete to authenticated
    using ((select auth.uid()) = user_id);

-- KARMA LEDGER (Read-only for owner; written exclusively via hardened RPCs)
create policy "karma_ledger_select_own" on public.karma_ledger
    for select to authenticated
    using ((select auth.uid()) = user_id);

-- ============================================================================
-- 5. AUTH SIGNUP TRIGGER & HARDENED LEDGER / VERIFICATION RPCs
-- ============================================================================

create or replace function private.handle_new_auth_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
    derived_username text;
begin
    derived_username := coalesce(
        nullif(trim(new.raw_user_meta_data->>'username'), ''),
        nullif(trim(new.raw_user_meta_data->>'full_name'), ''),
        split_part(coalesce(new.email, 'tester'), '@', 1)
    );

    insert into public.profiles (id, username, google_email, karma_points)
    values (new.id, left(derived_username, 40), coalesce(new.email, ''), 50)
    on conflict (id) do nothing;

    insert into public.karma_ledger (user_id, delta, reason, note)
    values (new.id, 50, 'SIGNUP_WELCOME', 'Welcome starter Karma credit');

    return new;
end;
$$;

revoke execute on function private.handle_new_auth_user() from public, anon, authenticated;

create trigger on_auth_user_created
    after insert on auth.users
    for each row execute function private.handle_new_auth_user();

-- Start a signed daily QA test session
create or replace function public.start_test_session(p_app_id bigint)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := (select auth.uid());
    v_enrollment public.enrollments%rowtype;
    v_nonce text;
    v_session_id bigint;
begin
    if v_uid is null then
        raise exception 'Authentication required';
    end if;

    insert into public.enrollments (app_id, tester_id, status)
    values (p_app_id, v_uid, 'ACTIVE')
    on conflict (app_id, tester_id) do update
        set status = case
            when public.enrollments.status = 'COMPLETED' then 'COMPLETED'
            else 'ACTIVE'
        end
    returning * into v_enrollment;

    v_nonce := 'bg_' || replace(gen_random_uuid()::text, '-', '');

    insert into public.verification_sessions (enrollment_id, tester_id, session_nonce)
    values (v_enrollment.id, v_uid, v_nonce)
    returning id into v_session_id;

    return jsonb_build_object(
        'session_id', v_session_id,
        'enrollment_id', v_enrollment.id,
        'session_nonce', v_nonce
    );
end;
$$;

revoke execute on function public.start_test_session(bigint) from public, anon;
grant execute on function public.start_test_session(bigint) to authenticated;

-- Internal helper applying 14-day continuity + 1-day grace window + Karma awards
create or replace function private.apply_session_verification(
    p_session_nonce text,
    p_active_seconds integer,
    p_method text,
    p_version_code bigint default null
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_session public.verification_sessions%rowtype;
    v_enrollment public.enrollments%rowtype;
    v_app public.app_listings%rowtype;
    v_today date := (now() at time zone 'utc')::date;
    v_days_diff integer;
    v_new_streak integer;
    v_new_missed integer;
    v_just_completed boolean := false;
    v_karma integer := 10;
begin
    select * into v_session
    from public.verification_sessions
    where session_nonce = p_session_nonce
    for update;

    if not found then
        raise exception 'Invalid session nonce';
    end if;

    if v_session.expires_at < now() then
        raise exception 'Verification session expired';
    end if;

    select * into v_enrollment
    from public.enrollments
    where id = v_session.enrollment_id
    for update;

    select * into v_app
    from public.app_listings
    where id = v_enrollment.app_id;

    if v_enrollment.last_verified_utc_date = v_today then
        return jsonb_build_object(
            'already_verified_today', true,
            'day_number', v_enrollment.current_streak,
            'karma_earned', 0
        );
    end if;

    v_new_missed := v_enrollment.missed_days_used;
    if v_enrollment.last_verified_utc_date is null then
        v_new_streak := 1;
    else
        v_days_diff := v_today - v_enrollment.last_verified_utc_date;
        if v_days_diff = 1 then
            v_new_streak := v_enrollment.current_streak + 1;
        elsif v_days_diff = 2 and v_enrollment.missed_days_used < 1 then
            v_new_streak := v_enrollment.current_streak + 1;
            v_new_missed := v_new_missed + 1;
        else
            v_new_streak := 1;
            v_new_missed := 0;
        end if;
    end if;

    v_new_streak := least(v_new_streak, v_app.target_days);
    v_just_completed := (v_enrollment.status <> 'COMPLETED') and (v_new_streak >= v_app.target_days);

    if p_method = 'VERIFIED_DEEPLINK_HANDSHAKE' then
        v_karma := v_karma + 5;
    end if;
    if v_just_completed then
        v_karma := v_karma + 100;
    end if;

    insert into public.daily_verifications (
        enrollment_id, day_number, session_token, verified_utc_date,
        active_duration_seconds, karma_awarded, method
    ) values (
        v_enrollment.id, v_new_streak, p_session_nonce, v_today,
        greatest(p_active_seconds, 0), v_karma, p_method
    );

    update public.enrollments
    set verified_days_count = verified_days_count + 1,
        current_streak = v_new_streak,
        missed_days_used = v_new_missed,
        last_verified_utc_date = v_today,
        last_seen_version_code = coalesce(p_version_code, last_seen_version_code),
        status = case when v_new_streak >= v_app.target_days then 'COMPLETED' else 'ACTIVE' end
    where id = v_enrollment.id;

    update public.verification_sessions
    set completed_at = now(),
        deeplink_verified_at = case when p_method = 'VERIFIED_DEEPLINK_HANDSHAKE' then now() else deeplink_verified_at end
    where id = v_session.id;

    insert into public.karma_ledger (user_id, delta, reason, app_id, note)
    values (
        v_enrollment.tester_id,
        10,
        'DAILY_VERIFICATION',
        v_app.id,
        format('Day %s/%s verified for %s', v_new_streak, v_app.target_days, v_app.title)
    );

    if p_method = 'VERIFIED_DEEPLINK_HANDSHAKE' then
        insert into public.karma_ledger (user_id, delta, reason, app_id, note)
        values (v_enrollment.tester_id, 5, 'DEEPLINK_VERIFIED_BONUS', v_app.id, 'Verified Deep-Link Handshake');
    end if;

    if v_just_completed then
        insert into public.karma_ledger (user_id, delta, reason, app_id, note)
        values (v_enrollment.tester_id, 100, 'DAY_14_COMPLETION_BONUS', v_app.id, 'Completed 14-day closed test!');
    end if;

    update public.profiles
    set karma_points = karma_points + v_karma,
        completed_campaigns_count = completed_campaigns_count + (case when v_just_completed then 1 else 0 end),
        updated_at = now()
    where id = v_enrollment.tester_id;

    return jsonb_build_object(
        'already_verified_today', false,
        'day_number', v_new_streak,
        'karma_earned', v_karma,
        'campaign_completed', v_just_completed
    );
end;
$$;

revoke execute on function private.apply_session_verification(text, integer, text, bigint) from public, anon, authenticated;

-- Authenticated tester completion RPC (Zero-SDK 60s session)
create or replace function public.complete_test_session(
    p_session_nonce text,
    p_active_seconds integer default 60,
    p_version_code bigint default null
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := (select auth.uid());
    v_owner uuid;
begin
    if v_uid is null then
        raise exception 'Authentication required';
    end if;

    select tester_id into v_owner
    from public.verification_sessions
    where session_nonce = p_session_nonce;

    if v_owner is distinct from v_uid then
        raise exception 'Forbidden session token';
    end if;

    if p_active_seconds < 55 then
        raise exception 'Minimum 60-second active test duration required';
    end if;

    return private.apply_session_verification(p_session_nonce, p_active_seconds, 'ZERO_SDK_SESSION', p_version_code);
end;
$$;

revoke execute on function public.complete_test_session(text, integer, bigint) from public, anon;
grant execute on function public.complete_test_session(text, integer, bigint) to authenticated;

-- Service-role RPC called exclusively by verify-deeplink-heartbeat Edge Function
create or replace function public.confirm_deeplink_nonce_service(
    p_session_nonce text,
    p_package_name text
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_pkg text;
begin
    select a.package_name into v_pkg
    from public.verification_sessions vs
    join public.enrollments e on e.id = vs.enrollment_id
    join public.app_listings a on a.id = e.app_id
    where vs.session_nonce = p_session_nonce;

    if v_pkg is null or v_pkg <> p_package_name then
        raise exception 'Nonce and package_name mismatch';
    end if;

    return private.apply_session_verification(p_session_nonce, 60, 'VERIFIED_DEEPLINK_HANDSHAKE', null);
end;
$$;

revoke execute on function public.confirm_deeplink_nonce_service(text, text) from public, anon, authenticated;
grant execute on function public.confirm_deeplink_nonce_service(text, text) to service_role;

-- Developer Triage RPC: updates feedback status and awards +25 Karma bounty once when accepted
create or replace function public.triage_feedback_with_bounty(
    p_feedback_id bigint,
    p_new_status text
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := (select auth.uid());
    v_item public.feedback_items%rowtype;
    v_owner uuid;
    v_awarded boolean := false;
begin
    if v_uid is null then
        raise exception 'Authentication required';
    end if;

    select * into v_item
    from public.feedback_items
    where id = p_feedback_id
    for update;

    if not found then
        raise exception 'Feedback item not found';
    end if;

    select owner_id into v_owner
    from public.app_listings
    where id = v_item.app_id;

    if v_owner is distinct from v_uid then
        raise exception 'Only the app owner can triage feedback status';
    end if;

    if (not v_item.bounty_awarded) and p_new_status in ('IN_PROGRESS', 'FIXED') then
        v_awarded := true;
    end if;

    update public.feedback_items
    set status = p_new_status,
        bounty_awarded = (bounty_awarded or v_awarded)
    where id = p_feedback_id;

    if v_awarded then
        insert into public.karma_ledger (user_id, delta, reason, app_id, note)
        values (v_item.author_id, 25, 'TRIAGED_BUG_BOUNTY', v_item.app_id, 'Bug report accepted: ' || left(v_item.title, 60));

        update public.profiles
        set karma_points = karma_points + 25,
            updated_at = now()
        where id = v_item.author_id;
    end if;

    return jsonb_build_object(
        'feedback_id', p_feedback_id,
        'status', p_new_status,
        'bounty_awarded', v_awarded
    );
end;
$$;

revoke execute on function public.triage_feedback_with_bounty(bigint, text) from public, anon;
grant execute on function public.triage_feedback_with_bounty(bigint, text) to authenticated;

-- ============================================================================
-- 6. SUPABASE STORAGE BUCKET & RLS (Screenshots + 15s 720p Screen Clips)
-- ============================================================================

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
    'feedback-media',
    'feedback-media',
    true,
    15728640, -- 15 MB max (covers WebP/PNG screenshots + 15s 720p MP4 bug clips)
    array['image/webp', 'image/png', 'image/jpeg', 'video/mp4']
)
on conflict (id) do update
set file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

create policy "feedback_media_select_public" on storage.objects
    for select to anon, authenticated
    using (bucket_id = 'feedback-media');

create policy "feedback_media_insert_own_folder" on storage.objects
    for insert to authenticated
    with check (
        bucket_id = 'feedback-media'
        and (storage.foldername(name))[1] = (select auth.uid())::text
    );

create policy "feedback_media_update_own_folder" on storage.objects
    for update to authenticated
    using (
        bucket_id = 'feedback-media'
        and (storage.foldername(name))[1] = (select auth.uid())::text
    )
    with check (
        bucket_id = 'feedback-media'
        and (storage.foldername(name))[1] = (select auth.uid())::text
    );
