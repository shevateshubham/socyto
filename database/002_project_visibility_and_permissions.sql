-- SOCYTO security refinement: Super Admin may inspect project metadata across tenants.
-- This is intentionally SELECT-only; a platform administrator is not silently
-- granted permission to edit business data or read private material receipts.
-- Clients remain restricted to projects linked to their own authenticated account.

drop policy if exists projects_read on public.projects;
create policy projects_read on public.projects for select to authenticated
using (
    public.is_platform_administrator()
    or public.is_business_administrator(business_id)
    or public.is_project_client(business_id, project_id)
);

-- Revoke access to private tables from unauthenticated callers even if
-- defaults or future migrations grant schema-level privileges.
revoke all on public.platform_administrators from anon;
revoke all on public.business_members from anon;
revoke all on public.clients from anon;
revoke all on public.projects from anon;
revoke all on public.project_requirements from anon;
revoke all on public.material_receipts from anon;
revoke all on public.project_publications from anon;
revoke all on public.business_subscriptions from anon;
revoke all on public.businesses from anon;

-- Clients can view only published snapshots through project_publications.
-- Business administrators can edit draft requirements and receipts.
-- Row Level Security remains active; no client write policy exists.
