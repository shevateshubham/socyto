-- Human-shareable IDs, separate from permanent UUID primary keys.
-- Not a secret: never use public codes in place of authorization.
alter table public.businesses add column if not exists firm_code text;
alter table public.businesses add column if not exists contact_phone text;
alter table public.clients add column if not exists client_code text;
create unique index if not exists socyto_firm_code_unique
  on public.businesses(firm_code) where firm_code is not null;
create unique index if not exists socyto_client_code_unique
  on public.clients(client_code) where client_code is not null;
alter table public.businesses add constraint socyto_firm_code_format
  check (firm_code is null or firm_code ~ '^F[0-9]{9}$');
alter table public.clients add constraint socyto_client_code_format
  check (client_code is null or client_code ~ '^C[0-9]{9}$');
-- Temporary-password users must not access firm data before changing it.
create or replace function public.is_business_administrator(target_business_id uuid)
returns boolean language sql stable security definer set search_path = ''
as $$
  select coalesce(
      (auth.jwt() -> 'app_metadata' ->> 'socyto_password_reset_required')::boolean,
      false
    ) = false and exists (
      select 1 from public.business_members m
      join public.businesses b on b.business_id = m.business_id
      where m.business_id = target_business_id
        and m.user_id = (select auth.uid())
        and m.member_role = 'business_admin'
        and b.account_status = 'active'
    );
$$;
revoke all on function public.is_business_administrator(uuid) from public, anon;
grant execute on function public.is_business_administrator(uuid) to authenticated;
