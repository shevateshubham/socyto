-- Prevent future automated or accidental addition of any unapproved
-- platform-owner email or phone, even via a privileged API.
create or replace function public.enforce_locked_owner_contact()
returns trigger language plpgsql security definer set search_path=''
as $fn$
begin
  if not exists (
    select 1 from public.platform_owner_identity_lock l
    where (new.contact_method='email' and new.contact_value=l.approved_email)
       or (new.contact_method='phone' and new.contact_value=l.approved_phone)
  ) then
    raise exception 'SOCYTO owner contact is not on the permanent locked allowlist'
      using errcode='42501';
  end if;
  return new;
end;
$fn$;
revoke all on function public.enforce_locked_owner_contact() from public,anon,authenticated;
drop trigger if exists socyto_locked_owner_contacts on public.platform_owner_authorizations;
create trigger socyto_locked_owner_contacts
before insert or update of contact_method,contact_value
on public.platform_owner_authorizations
for each row execute function public.enforce_locked_owner_contact();

-- Changes to the single owner require an intentionally reviewed migration.
create or replace function public.prevent_owner_lock_mutation()
returns trigger language plpgsql security definer set search_path=''
as $fn$
begin
  raise exception 'Locked SOCYTO platform owner cannot be changed by routine application operations'
    using errcode='42501';
end;
$fn$;
revoke all on function public.prevent_owner_lock_mutation() from public,anon,authenticated;
drop trigger if exists socyto_prevent_owner_lock_mutation on public.platform_owner_identity_lock;
create trigger socyto_prevent_owner_lock_mutation
before update or delete on public.platform_owner_identity_lock
for each row execute function public.prevent_owner_lock_mutation();
