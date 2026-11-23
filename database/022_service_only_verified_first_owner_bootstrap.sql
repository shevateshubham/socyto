-- Service-role-only bootstrap called only after checking a real Supabase Auth
-- session in the owner-activation Edge Function. No public user gains a role
-- from registration, and this never generates an activation code.
create or replace function public.bootstrap_verified_platform_owner(target_user_id uuid)
returns boolean language plpgsql security definer set search_path=''
as $fn$
declare
  approved record;
  profile record;
  normalized_phone text;
  current_owner uuid;
begin
  if target_user_id is null then
    raise exception 'A verified owner account is required' using errcode='42501';
  end if;
  select approved_email, approved_phone into approved
  from public.platform_owner_bootstrap_config where singleton=true;
  if not found then
    raise exception 'Owner authorization configuration missing' using errcode='42501';
  end if;

  select id, lower(email) as email, email_confirmed_at,
    case when coalesce(phone,'') like '+%' then phone
         when coalesce(phone,'') <> '' then '+'||phone else null end as phone,
    phone_confirmed_at
  into profile from auth.users where id=target_user_id;
  if not found then
    raise exception 'Verified owner account not found' using errcode='42501';
  end if;
  if not (
      (profile.email = approved.approved_email and profile.email_confirmed_at is not null)
      or (profile.phone = approved.approved_phone and profile.phone_confirmed_at is not null)
  ) then
    raise exception 'Verified identity is not an approved platform owner'
      using errcode='42501';
  end if;
  select owner_user_id into current_owner
  from public.platform_owner_identity_lock where singleton=true for update;
  if current_owner is not null and current_owner <> target_user_id then
    raise exception 'Platform owner already belongs to another user'
      using errcode='42501';
  end if;
  if current_owner is null then
    insert into public.platform_owner_identity_lock
      (singleton,owner_user_id,approved_email,approved_phone)
    values (true,target_user_id,approved.approved_email,approved.approved_phone)
    on conflict(singleton) do nothing;
    select owner_user_id into current_owner
    from public.platform_owner_identity_lock where singleton=true;
    if current_owner is distinct from target_user_id then
      raise exception 'Platform ownership was reserved by another account'
        using errcode='42501';
    end if;
  end if;
  insert into public.platform_owner_authorizations(contact_method,contact_value)
    values ('email',approved.approved_email),('phone',approved.approved_phone)
    on conflict(contact_method,contact_value) do nothing;
  return true;
end
$fn$;
revoke all on function public.bootstrap_verified_platform_owner(uuid) from public, anon, authenticated;
grant execute on function public.bootstrap_verified_platform_owner(uuid) to service_role;
