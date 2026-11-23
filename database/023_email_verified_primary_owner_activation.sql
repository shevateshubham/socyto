-- Primary Owner activation now relies on Supabase's completed email verification,
-- not on a second custom email provider or separately mailed bootstrap code.
-- Called ONLY by a trusted service role after /auth/v1/user validated a real JWT.
-- This explicit, one-time action consumes the existing owner authorization.
create or replace function public.activate_verified_primary_owner(target_user_id uuid)
returns boolean
language plpgsql security definer set search_path = ''
as $fn$
declare
  approved_email text;
  target_email text;
  owner_id uuid;
  existing_admin boolean;
begin
  if target_user_id is null then
    raise exception 'Verified SOCYTO owner required' using errcode='42501';
  end if;
  select c.approved_email into approved_email
  from public.platform_owner_bootstrap_config c where c.singleton = true;
  if approved_email is null then
    raise exception 'Owner configuration missing' using errcode='42501';
  end if;
  select lower(u.email) into target_email from auth.users u
  where u.id = target_user_id
    and u.email_confirmed_at is not null
    and lower(u.email) = approved_email;
  if target_email is null then
    raise exception 'Verify the approved owner email first' using errcode='42501';
  end if;
  select l.owner_user_id into owner_id
    from public.platform_owner_identity_lock l where l.singleton = true for update;
  if owner_id is distinct from target_user_id then
    raise exception 'Owner account does not match locked identity' using errcode='42501';
  end if;
  select exists(select 1 from public.platform_administrators a where a.user_id=target_user_id)
    into existing_admin;
  if existing_admin then
    return true;  -- Idempotent retry; never issue a new invitation or role.
  end if;
  update public.platform_owner_authorizations a set consumed_at=now()
  where a.contact_method='email'
    and a.contact_value=target_email
    and a.consumed_at is null;
  if not found then
    raise exception 'Owner authorization already consumed or missing' using errcode='42501';
  end if;
  insert into public.platform_administrators(user_id) values(target_user_id);
  return true;
end
$fn$;
revoke all on function public.activate_verified_primary_owner(uuid) from public,anon,authenticated;
grant execute on function public.activate_verified_primary_owner(uuid) to service_role;
