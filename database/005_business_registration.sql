-- Platform-owner onboarding for an existing authenticated business administrator.
-- Run after migrations 001..004. The administrator must first have an auth.users account
-- created by an approved invite/signup workflow. NEVER create users by embedding service keys
-- in Android. A business becomes active only after required verification and subscription checks.
create or replace function public.register_business_for_existing_user(
  new_business_name text,
  administrator_user_id uuid,
  new_business_type text default 'interior_design'
)
returns uuid
language plpgsql security definer set search_path = ''
as $$
declare
  registered_business_id uuid;
begin
  if not public.is_platform_administrator() then
    raise exception 'Super Admin permission required' using errcode = '42501';
  end if;
  if new_business_name is null or length(trim(new_business_name)) not between 2 and 120 then
    raise exception 'Business name must contain 2 to 120 characters' using errcode = '22023';
  end if;
  if administrator_user_id is null or not exists (
    select 1 from auth.users u where u.id = administrator_user_id
  ) then
    raise exception 'Administrator account must already exist' using errcode = '22023';
  end if;
  if new_business_type is null or length(trim(new_business_type)) not between 2 and 60 then
    raise exception 'Invalid business type' using errcode = '22023';
  end if;

  insert into public.businesses (business_name,business_type,account_status)
  values(trim(new_business_name),trim(new_business_type),'pending')
  returning business_id into registered_business_id;

  insert into public.business_members (business_id,user_id,member_role)
  values(registered_business_id,administrator_user_id,'business_admin');

  insert into public.business_branding (business_id,display_name)
  values(registered_business_id,trim(new_business_name));

  return registered_business_id;
end;
$$;

revoke all on function public.register_business_for_existing_user(text,uuid,text)
from public, anon;
grant execute on function public.register_business_for_existing_user(text,uuid,text)
to authenticated;

-- Future onboarding UI should call this RPC with an existing verified user's ID.
-- RLS restrictions on cross-tenant project and client tables remain unchanged.
