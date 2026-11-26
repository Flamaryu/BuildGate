-- Allow app owners to delete QA missions belonging to their own published apps
grant delete on public.test_missions to authenticated;

create policy "test_missions_delete_app_owner" on public.test_missions
    for delete to authenticated
    using (
        exists (
            select 1 from public.app_listings a
            where a.id = app_id and a.owner_id = (select auth.uid())
        )
    );
