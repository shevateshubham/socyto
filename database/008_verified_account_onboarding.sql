-- Real firm and client account onboarding.
-- Deploy after 007_data_api_permissions.sql.
-- No user can self-assign Super Admin or Business Admin rights.
-- Only verified accounts can be linked to firm/client records.

create or replace function public.register_business_for_verified_email(
  new_business_name text,
  administrator_email text,
  new_business_type text default 'interior_design'
)
returns uuid
language plpgsql security definer set search_path = ''
as $$
declare
  administrator_user_id uuid;
begin
  if not public.is_platform_administrator() then
    raise exception 'Super Admin permission required' using errcode = '42501';
  end if;
  if administrator_email is null or length(trim(administrator_email)) not between 5 and 254 then
    raise exception 'Provide the administrator account email' using errcode = '22023';
  end if;
  select u.id into administrator_user_id
  from auth.users u
  where lower(u.email) = lower(trim(administrator_email))
    and u.email_confirmed_at is not null;
  if administrator_user_id is null then
    raise exception 'The administrator must sign up and verify their email first'
      using errcode = '22023';
  end if;
  return public.register_business_for_existing_user(
    new_business_name, administrator_user_id, new_business_type
  );
end;
$$;

revoke all on function public.register_business_for_verified_email(text,text,text)
  from public,anon;
grant execute on function public.register_business_for_verified_email(text,text,text)
  to authenticated;

-- Business Admin links an existing client record to the matching, verified
-- email owner, never to an arbitrary user ID supplied by the Android client.
create or replace function public.link_verified_client_login(
  target_business_id uuid,
  target_client_id uuid
)
returns void
language plpgsql security definer set search_path = ''
as $$
declare
  requested_email text;
  verified_user_id uuid;
begin
  if not public.is_business_administrator(target_business_id) then
    raise exception 'Business administrator permission required' using errcode = '42501';
  end if;
  select c.client_email into requested_email from public.clients c
  where c.business_id = target_business_id and c.client_id = target_client_id
  for update;
  if requested_email is null or length(trim(requested_email)) = 0 then
    raise exception 'Add the client email before linking login' using errcode = '22023';
  end if;
  select u.id into verified_user_id from auth.users u
  where lower(u.email) = lower(trim(requested_email))
    and u.email_confirmed_at is not null;
  if verified_user_id is null then
    raise exception 'Client must sign up and verify that email address' using errcode = '22023';
  end if;
  update public.clients
  set login_user_id = verified_user_id
  where business_id = target_business_id and client_id = target_client_id;
exception when unique_violation then
  raise exception 'This verified user is already linked to a different client in this business'
    using errcode = '23505';
end;
$$;

revoke all on function public.link_verified_client_login(uuid,uuid)
  from public,anon;
grant execute on function public.link_verified_client_login(uuid,uuid)
  to authenticated;
