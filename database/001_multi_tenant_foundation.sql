-- SOCYTO multi-tenant database foundation
-- PostgreSQL / Supabase. Review and test policies before production deployment.
-- Naming: descriptive snake_case; identifiers are UUIDs, never sequential tenant numbers.
create extension if not exists pgcrypto;

create table public.businesses (
  business_id uuid primary key default gen_random_uuid(),
  business_name text not null check (length(trim(business_name)) > 0),
  business_type text not null default 'interior_design',
  contact_email text,
  account_status text not null default 'pending' check (account_status in ('pending','active','suspended')),
  created_at timestamptz not null default now(),
  unique (business_id)
);

create table public.platform_administrators (
  user_id uuid primary key references auth.users(id) on delete cascade,
  created_at timestamptz not null default now()
);

create table public.business_members (
  membership_id uuid primary key default gen_random_uuid(),
  business_id uuid not null references public.businesses(business_id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  member_role text not null check (member_role in ('business_admin')),
  created_at timestamptz not null default now(),
  unique (business_id, user_id)
);

create table public.clients (
  client_id uuid primary key default gen_random_uuid(),
  business_id uuid not null references public.businesses(business_id) on delete cascade,
  client_name text not null,
  client_email text,
  client_phone text,
  login_user_id uuid references auth.users(id) on delete set null,
  created_at timestamptz not null default now(),
  unique (business_id, client_id),
  unique (business_id, login_user_id)
);

create table public.projects (
  project_id uuid primary key default gen_random_uuid(),
  business_id uuid not null references public.businesses(business_id) on delete cascade,
  client_id uuid not null,
  project_name text not null,
  project_type text,
  site_location text,
  start_date date,
  expected_handover_date date,
  created_at timestamptz not null default now(),
  unique (business_id, project_id),
  foreign key (business_id, client_id) references public.clients(business_id, client_id)
);

create table public.project_requirements (
  requirement_id uuid primary key default gen_random_uuid(),
  business_id uuid not null,
  project_id uuid not null,
  requirement_name text not null,
  tracking_type text not null check (tracking_type in ('checklist','quantity')),
  effort_score smallint not null default 2 check (effort_score between 1 and 3),
  is_completed boolean not null default false,
  required_quantity numeric(14,3),
  completed_quantity numeric(14,3) not null default 0 check (completed_quantity >= 0),
  unit_name text,
  display_order integer not null default 0,
  updated_at timestamptz not null default now(),
  unique (business_id, requirement_id),
  foreign key (business_id, project_id) references public.projects(business_id, project_id) on delete cascade,
  constraint valid_quantity_requirement check (
    (tracking_type = 'checklist' and required_quantity is null)
    or (tracking_type = 'quantity' and required_quantity > 0)
  )
);

create table public.material_receipts (
  material_receipt_id uuid primary key default gen_random_uuid(),
  business_id uuid not null,
  project_id uuid not null,
  material_name text not null,
  material_category text,
  received_quantity numeric(14,3) not null check (received_quantity > 0),
  unit_name text not null,
  unit_rate numeric(14,2) not null check (unit_rate >= 0),
  total_amount numeric(16,2) generated always as (round(received_quantity * unit_rate, 2)) stored,
  supplier_name text,
  received_date date not null,
  invoice_storage_path text,
  internal_notes text,
  created_at timestamptz not null default now(),
  foreign key (business_id, project_id) references public.projects(business_id, project_id) on delete cascade
);

create table public.project_publications (
  publication_id uuid primary key default gen_random_uuid(),
  business_id uuid not null,
  project_id uuid not null,
  published_by_user_id uuid not null references auth.users(id),
  published_at timestamptz not null default now(),
  overall_progress_percent numeric(5,2) check (overall_progress_percent between 0 and 100),
  published_requirements jsonb not null,
  public_update_note text,
  public_photo_paths jsonb not null default '[]'::jsonb,
  foreign key (business_id, project_id) references public.projects(business_id, project_id) on delete cascade
);

create table public.business_subscriptions (
  subscription_id uuid primary key default gen_random_uuid(),
  business_id uuid not null references public.businesses(business_id) on delete cascade,
  plan_name text not null,
  subscription_status text not null check (subscription_status in ('trial','active','past_due','cancelled','expired')),
  starts_at timestamptz not null,
  ends_at timestamptz,
  created_at timestamptz not null default now()
);

-- SECURITY: helper functions run as their owner; fixed search_path prevents object shadowing.
-- These helpers should be owned by a trusted database role, not exposed for writes.
create or replace function public.is_platform_administrator()
returns boolean language sql stable security definer set search_path = ''
as $$ select exists (select 1 from public.platform_administrators a where a.user_id = (select auth.uid())) $$;

create or replace function public.is_business_administrator(target_business_id uuid)
returns boolean language sql stable security definer set search_path = ''
as $$ select exists (
  select 1 from public.business_members m
  where m.business_id = target_business_id and m.user_id = (select auth.uid())
    and m.member_role = 'business_admin'
) $$;

create or replace function public.is_project_client(target_business_id uuid, target_project_id uuid)
returns boolean language sql stable security definer set search_path = ''
as $$ select exists (
  select 1 from public.projects p
  join public.clients c on c.business_id = p.business_id and c.client_id = p.client_id
  where p.business_id = target_business_id and p.project_id = target_project_id
    and c.login_user_id = (select auth.uid())
) $$;

revoke all on function public.is_platform_administrator() from public;
revoke all on function public.is_business_administrator(uuid) from public;
revoke all on function public.is_project_client(uuid,uuid) from public;
grant execute on function public.is_platform_administrator() to authenticated;
grant execute on function public.is_business_administrator(uuid) to authenticated;
grant execute on function public.is_project_client(uuid,uuid) to authenticated;

alter table public.businesses enable row level security;
alter table public.platform_administrators enable row level security;
alter table public.business_members enable row level security;
alter table public.clients enable row level security;
alter table public.projects enable row level security;
alter table public.project_requirements enable row level security;
alter table public.material_receipts enable row level security;
alter table public.project_publications enable row level security;
alter table public.business_subscriptions enable row level security;

-- Only trusted server-side provisioning may create firms, admins, memberships and subscriptions.
create policy business_read on public.businesses for select to authenticated
using (public.is_platform_administrator() or public.is_business_administrator(business_id));
create policy platform_admin_self_read on public.platform_administrators for select to authenticated
using (user_id = (select auth.uid()));
create policy membership_read on public.business_members for select to authenticated
using (public.is_platform_administrator() or public.is_business_administrator(business_id));
create policy subscription_read on public.business_subscriptions for select to authenticated
using (public.is_platform_administrator() or public.is_business_administrator(business_id));

-- Business administrators own business data. Client records are NOT directly visible to clients.
create policy clients_admin_select on public.clients for select to authenticated
using (public.is_business_administrator(business_id));
create policy clients_admin_insert on public.clients for insert to authenticated
with check (public.is_business_administrator(business_id));
create policy clients_admin_update on public.clients for update to authenticated
using (public.is_business_administrator(business_id))
with check (public.is_business_administrator(business_id));
create policy clients_admin_delete on public.clients for delete to authenticated
using (public.is_business_administrator(business_id));

create policy projects_read on public.projects for select to authenticated
using (public.is_business_administrator(business_id) or public.is_project_client(business_id,project_id));
create policy projects_admin_insert on public.projects for insert to authenticated
with check (public.is_business_administrator(business_id));
create policy projects_admin_update on public.projects for update to authenticated
using (public.is_business_administrator(business_id))
with check (public.is_business_administrator(business_id));
create policy projects_admin_delete on public.projects for delete to authenticated
using (public.is_business_administrator(business_id));

-- Draft requirements and private financial information are never exposed to clients.
create policy requirements_admin_all on public.project_requirements for all to authenticated
using (public.is_business_administrator(business_id))
with check (public.is_business_administrator(business_id));
create policy receipts_admin_all on public.material_receipts for all to authenticated
using (public.is_business_administrator(business_id))
with check (public.is_business_administrator(business_id));

-- Published snapshots are client-visible and immutable to preserve history.
create policy publications_read on public.project_publications for select to authenticated
using (public.is_business_administrator(business_id) or public.is_project_client(business_id,project_id));
-- Publishing must happen through a trusted server-side transaction that calculates
-- progress from current requirements, validates all public fields and stamps publisher.
-- No direct authenticated INSERT/UPDATE/DELETE policy is intentionally provided.

create index idx_members_user on public.business_members(user_id,business_id);
create index idx_clients_business on public.clients(business_id);
create index idx_projects_business on public.projects(business_id);
create index idx_requirements_project on public.project_requirements(business_id,project_id);
create index idx_receipts_project on public.material_receipts(business_id,project_id);
create index idx_publications_project on public.project_publications(business_id,project_id,published_at desc);

-- Business expenditure is computed only from received materials.
create view public.project_material_expenditure with (security_invoker = true) as
select p.business_id, p.project_id, coalesce(sum(r.total_amount),0)::numeric(16,2) as expenditure_to_date
from public.projects p
left join public.material_receipts r on r.business_id=p.business_id and r.project_id=p.project_id
group by p.business_id,p.project_id;

-- IMPORTANT: Do not grant the client role SELECT on the expenditure view.
-- On Supabase, revoke grants on the view and grant only to an appropriate
-- trusted role or replace with an administrator-only RPC before production.
revoke all on public.project_material_expenditure from public, anon, authenticated;
