-- Operational account controls and identity-link hardening.
-- A firm must be active before its administrator or clients can access its workspaces.

create or replace function public.is_business_administrator(target_business_id uuid)
returns boolean language sql stable security definer set search_path = ''
as $$
  select exists (
    select 1 from public.business_members m
    join public.businesses b on b.business_id = m.business_id
    where m.business_id = target_business_id
      and m.user_id = (select auth.uid())
      and m.member_role = 'business_admin'
      and b.account_status = 'active'
  );
$$;

create or replace function public.is_project_client(target_business_id uuid, target_project_id uuid)
returns boolean language sql stable security definer set search_path = ''
as $$
  select exists (
    select 1 from public.projects p
    join public.businesses b on b.business_id = p.business_id
    join public.clients c on c.business_id = p.business_id and c.client_id = p.client_id
    where p.business_id = target_business_id and p.project_id = target_project_id
      and c.login_user_id = (select auth.uid())
      and b.account_status = 'active'
  );
$$;

-- Keep role checks inaccessible to anonymous API users even when functions are replaced.
revoke execute on function public.is_business_administrator(uuid) from anon, public;
revoke execute on function public.is_project_client(uuid,uuid) from anon, public;
grant execute on function public.is_business_administrator(uuid) to authenticated;
grant execute on function public.is_project_client(uuid,uuid) to authenticated;

-- Firm registration cannot create a branding record with an invalid display name.
create or replace function public.register_business_for_existing_user(
  new_business_name text, administrator_user_id uuid,
  new_business_type text default 'interior_design'
)
returns uuid language plpgsql security definer set search_path = ''
as $$
declare registered_business_id uuid;
begin
  if not public.is_platform_administrator() then
    raise exception 'Super Admin permission required' using errcode = '42501';
  end if;
  if new_business_name is null or length(trim(new_business_name)) not between 2 and 80 then
    raise exception 'Business name must contain 2 to 80 characters' using errcode = '22023';
  end if;
  if administrator_user_id is null or not exists (
    select 1 from auth.users u where u.id = administrator_user_id
      and u.email_confirmed_at is not null
  ) then
    raise exception 'Administrator must verify their email first' using errcode = '22023';
  end if;
  if new_business_type is null or length(trim(new_business_type)) not between 2 and 60 then
    raise exception 'Invalid business type' using errcode = '22023';
  end if;
  insert into public.businesses(business_name,business_type,account_status)
    values(trim(new_business_name),trim(new_business_type),'pending')
    returning business_id into registered_business_id;
  insert into public.business_members(business_id,user_id,member_role)
    values(registered_business_id,administrator_user_id,'business_admin');
  insert into public.business_branding(business_id,display_name)
    values(registered_business_id,trim(new_business_name));
  return registered_business_id;
end;
$$;
revoke execute on function public.register_business_for_existing_user(text,uuid,text) from anon,public;
grant execute on function public.register_business_for_existing_user(text,uuid,text) to authenticated;

-- Super Admin can activate or suspend an onboarded business.
create or replace function public.set_business_account_status(
  target_business_id uuid, new_status text
)
returns void language plpgsql security definer set search_path = ''
as $$
begin
  if not public.is_platform_administrator() then
    raise exception 'Super Admin permission required' using errcode = '42501';
  end if;
  if new_status not in ('pending','active','suspended') or new_status is null then
    raise exception 'Invalid business status' using errcode = '22023';
  end if;
  update public.businesses
    set account_status = new_status where business_id = target_business_id;
  if not found then
    raise exception 'Business does not exist' using errcode = '22023';
  end if;
end;
$$;
revoke all on function public.set_business_account_status(uuid,text) from public,anon;
grant execute on function public.set_business_account_status(uuid,text) to authenticated;

-- Changing a client's email invalidates the prior verified login linkage.
create or replace function public.unlink_client_on_email_change()
returns trigger language plpgsql set search_path = ''
as $$
begin
  if new.client_email is distinct from old.client_email then
    new.login_user_id := null;
  end if;
  return new;
end;
$$;
create trigger socyto_client_email_reverification
before update of client_email on public.clients
for each row execute function public.unlink_client_on_email_change();
revoke all on function public.unlink_client_on_email_change() from public,anon,authenticated;
