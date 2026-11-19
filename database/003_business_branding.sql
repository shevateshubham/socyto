-- SOCYTO tenant-specific white-label branding.
-- One branding record per business; no hardcoded palette or logo is shared between firms.
-- Execute after 001_multi_tenant_foundation.sql and 002_project_visibility_and_permissions.sql.

create table public.business_branding (
    business_id uuid primary key references public.businesses(business_id) on delete cascade,
    display_name text not null check (length(trim(display_name)) between 1 and 80),
    logo_url text check (
        logo_url is null or (
            length(logo_url) <= 2048
            and logo_url ~ '^https://[A-Za-z0-9.-]+(/[A-Za-z0-9._~%/-]*)?(\\?[A-Za-z0-9._~%&=-]*)?$'
        )
    ),
    primary_color_hex text not null default '#2E4A2B'
        check (primary_color_hex ~ '^#[0-9A-Fa-f]{6}$'),
    secondary_color_hex text not null default '#6B8E49'
        check (secondary_color_hex ~ '^#[0-9A-Fa-f]{6}$'),
    accent_color_hex text not null default '#D4AF7C'
        check (accent_color_hex ~ '^#[0-9A-Fa-f]{6}$'),
    background_color_hex text not null default '#F7F5F0'
        check (background_color_hex ~ '^#[0-9A-Fa-f]{6}$'),
    text_color_hex text not null default '#1F1F1F'
        check (text_color_hex ~ '^#[0-9A-Fa-f]{6}$'),
    updated_at timestamptz not null default now()
);

create index idx_projects_branding_client on public.projects (business_id, project_id);

alter table public.business_branding enable row level security;

-- Clients may read branding ONLY for businesses owning a project assigned to
-- their authenticated account. They never receive another firm's branding.
create policy business_branding_read on public.business_branding
for select to authenticated
using (
    public.is_platform_administrator()
    or public.is_business_administrator(business_id)
    or exists (
        select 1 from public.projects p
        where p.business_id = business_branding.business_id
          and public.is_project_client(p.business_id, p.project_id)
    )
);

revoke all on public.business_branding from anon;
grant select on public.business_branding to authenticated;

-- No INSERT / UPDATE / DELETE policy for regular authenticated users.
-- Branding is initially managed by the platform's trusted provisioning service.
-- A future self-service setting must validate firm membership and media ownership
-- server-side before changing a logo or palette.
-- Public logos only: if using Supabase Storage, permit public access only to
-- approved company logos, NOT to project photos, invoices or customer media.

-- Create each client's brand explicitly at provisioning. Example, using a real
-- business_id from provisioning (not runnable without that identifier):
-- insert into public.business_branding(business_id, display_name, primary_color_hex,
--   secondary_color_hex, accent_color_hex, background_color_hex, text_color_hex)
-- values (:real_business_id, 'SOCYTO', '#2E4A2B',
--   '#6B8E49', '#D4AF7C', '#F7F5F0', '#1F1F1F');
