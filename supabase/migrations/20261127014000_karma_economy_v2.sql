-- ============================================================================
-- BuildGate Karma Economy v2: Positive Spend Sinks & Fair-Play Accountability
-- ============================================================================

-- 1. Expand karma_ledger reasons to support Spend sinks & Fair-Play penalties
alter table public.karma_ledger
    drop constraint if exists karma_ledger_reason_check;

alter table public.karma_ledger
    add constraint karma_ledger_reason_check check (reason in (
        'SIGNUP_WELCOME',
        'DAILY_VERIFICATION',
        'DEEPLINK_VERIFIED_BONUS',
        'TRIAGED_BUG_BOUNTY',
        'DAY_14_COMPLETION_BONUS',
        'EARLY_UPGRADER_BONUS',
        'FEATURED_SPOTLIGHT_SPEND',
        'PRIORITY_BOUNTY_POOL_SPEND',
        'BUILD_RETEST_PING_SPEND',
        'VETERAN_WAVE_SPEND',
        'EXTRA_GRACE_SHIELD_SPEND',
        'EARLY_UNINSTALL_PENALTY',
        'SPAM_REPORT_PENALTY',
        'GHOST_ENROLLMENT_TIMEOUT'
    ));

-- 2. Add SPAM status to feedback_items (distinct from normal WONT_FIX)
alter table public.feedback_items
    drop constraint if exists feedback_items_status_check;

alter table public.feedback_items
    add constraint feedback_items_status_check check (status in (
        'OPEN', 'IN_PROGRESS', 'FIXED', 'WONT_FIX', 'SPAM'
    ));

-- 3. Add Karma Boost columns to app_listings
alter table public.app_listings
    add column if not exists featured_until timestamptz,
    add column if not exists priority_bounty_remaining integer not null default 0 check (priority_bounty_remaining >= 0),
    add column if not exists retest_ping_until timestamptz,
    add column if not exists veteran_wave_until timestamptz;

-- 4. Add Extra Streak Grace Shield column to enrollments
alter table public.enrollments
    add column if not exists extra_grace_days integer not null default 0 check (extra_grace_days between 0 and 3);

-- 5. Update app_discovery_feed view with Featured Spotlight (+500) & Owner Triage Accountability (0.75x if stale OPEN bugs > 7d)
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
    (
        (
            (
                (coalesce(p.karma_points, 50) * 2)
                + (count(e.id) filter (where e.status in ('ACTIVE', 'COMPLETED'))::integer * 5)
            ) * (
                case when exists (
                    select 1 from public.feedback_items f
                    where f.app_id = a.id
                      and f.status = 'OPEN'
                      and f.created_at < now() - interval '7 days'
                ) then 0.75 else 1.0 end
            )
        )::integer
        + (case when a.featured_until is not null and a.featured_until > now() then 500 else 0 end)
    ) as discovery_score,
    a.featured_until,
    a.priority_bounty_remaining,
    a.retest_ping_until,
    a.veteran_wave_until
from public.app_listings a
left join public.profiles p on p.id = a.owner_id
left join public.enrollments e on e.app_id = a.id
where a.is_active = true
group by a.id, p.karma_points;

grant select on public.app_discovery_feed to anon, authenticated;

-- 6. Hardened RPC: Spend Karma on Developer App Boosts
create or replace function public.spend_karma_for_app_boost(
    p_app_id bigint,
    p_boost_type text
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := (select auth.uid());
    v_app public.app_listings%rowtype;
    v_karma integer;
    v_cost integer;
    v_reason text;
    v_note text;
begin
    if v_uid is null then
        raise exception 'Authentication required';
    end if;

    select * into v_app
    from public.app_listings
    where id = p_app_id
    for update;

    if not found then
        raise exception 'App listing not found';
    end if;

    if v_app.owner_id is distinct from v_uid then
        raise exception 'Only the app owner can purchase boosts for this app';
    end if;

    case p_boost_type
        when 'FEATURED_SPOTLIGHT' then
            v_cost := 100;
            v_reason := 'FEATURED_SPOTLIGHT_SPEND';
            v_note := '48h Featured Spotlight for ' || left(v_app.title, 40);
        when 'PRIORITY_BOUNTY_POOL' then
            v_cost := 50;
            v_reason := 'PRIORITY_BOUNTY_POOL_SPEND';
            v_note := '2x Priority Bug Bounty Pool (+2 bugs) for ' || left(v_app.title, 40);
        when 'BUILD_RETEST_PING' then
            v_cost := 30;
            v_reason := 'BUILD_RETEST_PING_SPEND';
            v_note := 'New Build Re-Test Ping (' || v_app.current_build_version || ') for ' || left(v_app.title, 40);
        when 'VETERAN_WAVE' then
            v_cost := 75;
            v_reason := 'VETERAN_WAVE_SPEND';
            v_note := '72h Veteran 4.5+ Tester Priority Wave for ' || left(v_app.title, 40);
        else
            raise exception 'Unknown boost type: %', p_boost_type;
    end case;

    select karma_points into v_karma
    from public.profiles
    where id = v_uid
    for update;

    if coalesce(v_karma, 0) < v_cost then
        raise exception 'Not enough Karma: need %⚡, you have %⚡', v_cost, coalesce(v_karma, 0);
    end if;

    update public.profiles
    set karma_points = karma_points - v_cost,
        updated_at = now()
    where id = v_uid;

    insert into public.karma_ledger (user_id, delta, reason, app_id, note)
    values (v_uid, -v_cost, v_reason, p_app_id, v_note);

    case p_boost_type
        when 'FEATURED_SPOTLIGHT' then
            update public.app_listings
            set featured_until = greatest(coalesce(featured_until, now()), now()) + interval '48 hours'
            where id = p_app_id;
        when 'PRIORITY_BOUNTY_POOL' then
            update public.app_listings
            set priority_bounty_remaining = priority_bounty_remaining + 2
            where id = p_app_id;
        when 'BUILD_RETEST_PING' then
            update public.app_listings
            set retest_ping_until = now() + interval '24 hours'
            where id = p_app_id;
        when 'VETERAN_WAVE' then
            update public.app_listings
            set veteran_wave_until = greatest(coalesce(veteran_wave_until, now()), now()) + interval '72 hours'
            where id = p_app_id;
    end case;

    return jsonb_build_object(
        'app_id', p_app_id,
        'boost_type', p_boost_type,
        'karma_spent', v_cost,
        'remaining_karma', v_karma - v_cost
    );
end;
$$;

revoke execute on function public.spend_karma_for_app_boost(bigint, text) from public, anon;
grant execute on function public.spend_karma_for_app_boost(bigint, text) to authenticated;

-- 7. Hardened RPC: Purchase Extra Streak Grace Shield (-40⚡)
create or replace function public.purchase_grace_shield(
    p_enrollment_id bigint
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := (select auth.uid());
    v_enr public.enrollments%rowtype;
    v_karma integer;
begin
    if v_uid is null then
        raise exception 'Authentication required';
    end if;

    select * into v_enr
    from public.enrollments
    where id = p_enrollment_id
    for update;

    if not found then
        raise exception 'Enrollment not found';
    end if;

    if v_enr.tester_id is distinct from v_uid then
        raise exception 'You can only buy a grace shield for your own enrollment';
    end if;

    if v_enr.extra_grace_days >= 3 then
        raise exception 'Maximum 3 extra grace shields per campaign reached';
    end if;

    select karma_points into v_karma
    from public.profiles
    where id = v_uid
    for update;

    if coalesce(v_karma, 0) < 40 then
        raise exception 'Not enough Karma: need 40⚡, you have %⚡', coalesce(v_karma, 0);
    end if;

    update public.profiles
    set karma_points = karma_points - 40,
        updated_at = now()
    where id = v_uid;

    update public.enrollments
    set extra_grace_days = extra_grace_days + 1
    where id = p_enrollment_id;

    insert into public.karma_ledger (user_id, delta, reason, app_id, note)
    values (v_uid, -40, 'EXTRA_GRACE_SHIELD_SPEND', v_enr.app_id, 'Purchased +1 Extra Streak Grace Shield');

    return jsonb_build_object(
        'enrollment_id', p_enrollment_id,
        'extra_grace_days', v_enr.extra_grace_days + 1,
        'remaining_karma', v_karma - 40
    );
end;
$$;

revoke execute on function public.purchase_grace_shield(bigint) from public, anon;
grant execute on function public.purchase_grace_shield(bigint) to authenticated;

-- 8. Hardened RPC: Record Early Uninstall Fair-Play Penalty (-20⚡ & -0.3 Trust)
create or replace function public.record_early_uninstall_penalty(
    p_enrollment_id bigint
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_uid uuid := (select auth.uid());
    v_enr public.enrollments%rowtype;
    v_karma integer;
    v_deduct integer;
begin
    if v_uid is null then
        raise exception 'Authentication required';
    end if;

    select * into v_enr
    from public.enrollments
    where id = p_enrollment_id
    for update;

    if not found or v_enr.tester_id is distinct from v_uid then
        raise exception 'Enrollment not found for caller';
    end if;

    if v_enr.status <> 'ACTIVE' or v_enr.verified_days_count <= 0 then
        return jsonb_build_object('penalty_applied', false);
    end if;

    select karma_points into v_karma
    from public.profiles
    where id = v_uid
    for update;

    v_deduct := least(coalesce(v_karma, 0), 20);

    update public.enrollments
    set status = 'DROPPED_UNINSTALLED',
        current_streak = 0
    where id = p_enrollment_id;

    update public.profiles
    set karma_points = greatest(0, karma_points - 20),
        trusted_score = greatest(1.0, round((trusted_score - 0.3)::numeric, 1)),
        updated_at = now()
    where id = v_uid;

    if v_deduct > 0 then
        insert into public.karma_ledger (user_id, delta, reason, app_id, note)
        values (v_uid, -v_deduct, 'EARLY_UNINSTALL_PENALTY', v_enr.app_id, 'Uninstalled closed-test app before Day 14');
    end if;

    return jsonb_build_object(
        'penalty_applied', true,
        'karma_deducted', v_deduct
    );
end;
$$;

revoke execute on function public.record_early_uninstall_penalty(bigint) from public, anon;
grant execute on function public.record_early_uninstall_penalty(bigint) to authenticated;

-- 9. Upgrade triage_feedback_with_bounty to support 2x Priority Bounty Pool (+50⚡) & SPAM penalty (-15⚡)
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
    v_app public.app_listings%rowtype;
    v_awarded boolean := false;
    v_bounty integer := 25;
    v_author_karma integer;
    v_spam_deduct integer := 0;
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

    select * into v_app
    from public.app_listings
    where id = v_item.app_id
    for update;

    if v_app.owner_id is distinct from v_uid then
        raise exception 'Only the app owner can triage feedback status';
    end if;

    if (not v_item.bounty_awarded) and p_new_status in ('IN_PROGRESS', 'FIXED') then
        v_awarded := true;
        if coalesce(v_app.priority_bounty_remaining, 0) > 0 then
            v_bounty := 50;
            update public.app_listings
            set priority_bounty_remaining = priority_bounty_remaining - 1
            where id = v_app.id;
        end if;
    end if;

    update public.feedback_items
    set status = p_new_status,
        bounty_awarded = (bounty_awarded or v_awarded)
    where id = p_feedback_id;

    if v_awarded then
        insert into public.karma_ledger (user_id, delta, reason, app_id, note)
        values (
            v_item.author_id,
            v_bounty,
            'TRIAGED_BUG_BOUNTY',
            v_item.app_id,
            case when v_bounty = 50 then '2x Priority Bug Bounty: ' else 'Bug report accepted: ' end || left(v_item.title, 60)
        );

        update public.profiles
        set karma_points = karma_points + v_bounty,
            updated_at = now()
        where id = v_item.author_id;
    elsif p_new_status = 'SPAM' and v_item.status <> 'SPAM' and v_item.author_id is distinct from v_uid then
        select karma_points into v_author_karma
        from public.profiles
        where id = v_item.author_id
        for update;

        v_spam_deduct := least(coalesce(v_author_karma, 0), 15);
        update public.profiles
        set karma_points = greatest(0, karma_points - 15),
            updated_at = now()
        where id = v_item.author_id;

        if v_spam_deduct > 0 then
            insert into public.karma_ledger (user_id, delta, reason, app_id, note)
            values (v_item.author_id, -v_spam_deduct, 'SPAM_REPORT_PENALTY', v_item.app_id, 'Report flagged as spam: ' || left(v_item.title, 60));
        end if;
    end if;

    return jsonb_build_object(
        'feedback_id', p_feedback_id,
        'status', p_new_status,
        'bounty_awarded', v_awarded,
        'bounty_amount', case when v_awarded then v_bounty else 0 end,
        'spam_penalty', v_spam_deduct
    );
end;
$$;
