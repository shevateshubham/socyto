-- New client logins cannot read client-scoped data with the temporary password.
-- The auth service writes this flag to app_metadata and clears it only after
-- a successful password change; user_metadata is never trusted for access.
create or replace function public.is_project_client(
    target_business_id uuid, target_project_id uuid
) returns boolean
language sql stable security definer
set search_path = ''
as $$
  select
    coalesce(
      (auth.jwt() -> 'app_metadata' ->> 'socyto_password_reset_required')::boolean,
      false
    ) = false
    and exists (
      select 1
        from public.projects p
        join public.businesses b on b.business_id = p.business_id
        join public.clients c
          on c.business_id = p.business_id and c.client_id = p.client_id
       where p.business_id = target_business_id
         and p.project_id = target_project_id
         and c.login_user_id = (select auth.uid())
         and b.account_status = 'active'
    );
$$;
-- Preserve original RLS policies and function grants.
