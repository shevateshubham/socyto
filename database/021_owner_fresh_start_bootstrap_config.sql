-- Configuration only, not a user account, authentication record or invitation.
-- This preserves the pre-authorized owner contacts across app-data resets.
create table if not exists public.platform_owner_bootstrap_config (
  singleton boolean primary key default true check (singleton),
  approved_email text not null check (approved_email = lower(approved_email)),
  approved_phone text not null check (approved_phone ~ '^[+][1-9][0-9]{7,14}$'),
  created_at timestamptz not null default now()
);
alter table public.platform_owner_bootstrap_config enable row level security;
revoke all on public.platform_owner_bootstrap_config from public,anon,authenticated;

-- No app client can change or remove the owner allowlist.
create or replace function public.reject_owner_bootstrap_config_change()
returns trigger language plpgsql security definer set search_path=''
as $func$
begin
  raise exception 'Permanent SOCYTO owner contacts require reviewed operator migration'
    using errcode='42501';
end
$func$;
revoke all on function public.reject_owner_bootstrap_config_change() from public,anon,authenticated;
drop trigger if exists socyto_owner_bootstrap_config_immutable on public.platform_owner_bootstrap_config;
create trigger socyto_owner_bootstrap_config_immutable
  before update or delete on public.platform_owner_bootstrap_config
  for each row execute function public.reject_owner_bootstrap_config_change();

-- Owner contacts must be provisioned by a trusted database operator through
-- a private administrative SQL action after deployment. Never commit personal
-- owner contact details to this public repository. The row is configuration,
-- not an Auth account, and remains intact when application records are reset.
