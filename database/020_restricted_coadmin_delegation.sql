-- Co-Admins are operational delegates, never platform owners.
-- Existing permanent owner lock and platform_administrators table are unchanged.
create table if not exists public.platform_coadmins (
  user_id uuid primary key references auth.users(id) on delete cascade,
  invited_by uuid not null references auth.users(id),
  contact_method text not null check(contact_method in ('email','phone')),
  contact_value text not null,
  active boolean not null default true,
  created_at timestamptz not null default now(),
  revoked_at timestamptz,
  constraint coadmin_revocation_consistency check (
    (active and revoked_at is null) or (not active and revoked_at is not null)
  )
);
create table if not exists public.platform_coadmin_invitations (
  invitation_id uuid primary key default gen_random_uuid(),
  token_digest text not null unique,
  contact_method text not null check(contact_method in ('email','phone')),
  contact_value text not null,
  invited_by uuid not null references auth.users(id),
  expires_at timestamptz not null,
  claimed_at timestamptz,
  claimed_by uuid references auth.users(id),
  revoked_at timestamptz,
  created_at timestamptz not null default now(),
  constraint coadmin_invitation_digest check(token_digest ~ '^[a-f0-9]{64}$'),
  constraint coadmin_invitation_claim check(
    (claimed_at is null and claimed_by is null) or
    (claimed_at is not null and claimed_by is not null)
  )
);
create unique index if not exists coadmin_unclaimed_contact_unique
 on public.platform_coadmin_invitations(contact_method, contact_value)
 where claimed_at is null and revoked_at is null;
alter table public.platform_coadmins enable row level security;
alter table public.platform_coadmin_invitations enable row level security;
revoke all on public.platform_coadmins from public,anon,authenticated;
revoke all on public.platform_coadmin_invitations from public,anon,authenticated;
grant select on public.platform_coadmins to authenticated;
create policy coadmin_self_read on public.platform_coadmins
 for select to authenticated using (user_id=(select auth.uid()));

-- Revocation is enforced against the table on every privileged operation,
-- never against stale JWT app_metadata.
create or replace function public.is_platform_operator()
returns boolean language sql stable security definer set search_path=''
as $fn$
 select public.is_platform_administrator() or exists(
   select 1 from public.platform_coadmins c
   where c.user_id=(select auth.uid()) and c.active
 );
$fn$;
revoke all on function public.is_platform_operator() from public,anon;
grant execute on function public.is_platform_operator() to authenticated;

-- Co-Admins can view/manage firms; they cannot claim ownership or create
-- Super Admins. Existing tenant and client editing permissions remain intact.
drop policy if exists business_read on public.businesses;
create policy business_read on public.businesses for select to authenticated
 using (public.is_platform_operator() or public.is_business_administrator(business_id));
drop policy if exists membership_read on public.business_members;
create policy membership_read on public.business_members for select to authenticated
 using (public.is_platform_operator() or public.is_business_administrator(business_id));
drop policy if exists subscription_read on public.business_subscriptions;
create policy subscription_read on public.business_subscriptions for select to authenticated
 using (public.is_platform_operator() or public.is_business_administrator(business_id));
drop policy if exists business_branding_read on public.business_branding;
create policy business_branding_read on public.business_branding for select to authenticated
 using (public.is_platform_operator() or public.is_business_administrator(business_id)
   or exists (select 1 from public.projects p where p.business_id=business_branding.business_id
     and public.is_project_client(p.business_id,p.project_id)));
drop policy if exists projects_read on public.projects;
create policy projects_read on public.projects for select to authenticated
 using (public.is_platform_operator() or public.is_business_administrator(business_id)
    or public.is_project_client(business_id,project_id));

create or replace function public.set_business_account_status(target_business_id uuid,new_status text)
returns void language plpgsql security definer set search_path=''
as $fn$
begin
  if not public.is_platform_operator() then
    raise exception 'Firm management permission required' using errcode='42501';
  end if;
  if new_status not in ('pending','active','suspended') or new_status is null then
    raise exception 'Invalid business status' using errcode='22023';
  end if;
  update public.businesses set account_status=new_status where business_id=target_business_id;
  if not found then raise exception 'Business does not exist' using errcode='22023'; end if;
end;
$fn$;
revoke all on function public.set_business_account_status(uuid,text) from public,anon;
grant execute on function public.set_business_account_status(uuid,text) to authenticated;

-- Reuse the existing branding validation function; replace only its owner-role
-- check, preserving every input validation and storage check in the function.
do $$
declare original_def text;
begin
 select pg_get_functiondef(
   'public.save_business_branding(uuid,text,text,text,text,text,text,text)'::regprocedure
 ) into original_def;
 if position('if not public.is_platform_administrator()' in original_def)=0 then
   raise exception 'Branding permission check changed, review migration manually';
 end if;
 original_def:=replace(original_def,
   'if not public.is_platform_administrator()',
   'if not public.is_platform_operator()');
 execute original_def;
end $$;

-- The invited user must sign in and verify EXACTLY the nominated email/phone.
-- No public database role can create invites or grant Co-Admin directly.
create or replace function public.claim_platform_coadmin_invitation(one_time_code text)
returns boolean language plpgsql security definer set search_path=''
as $fn$
declare
 actor uuid := (select auth.uid());
 verified_email text;
 verified_phone text;
 invitation public.platform_coadmin_invitations%rowtype;
begin
 if actor is null then raise exception 'Sign in to activate Co-Admin access' using errcode='42501'; end if;
 if one_time_code is null or one_time_code !~ '^[a-f0-9]{64}$' then
   raise exception 'Invalid invitation code' using errcode='22023';
 end if;
 select case when email_confirmed_at is not null then lower(email) end,
        case when phone_confirmed_at is not null then
          case when phone like '+%' then phone else '+'||phone end end
 into verified_email,verified_phone from auth.users where id=actor;
 select * into invitation from public.platform_coadmin_invitations i
 where i.token_digest=encode(extensions.digest(one_time_code,'sha256'),'hex')
   and i.claimed_at is null and i.revoked_at is null and i.expires_at>now()
 for update;
 if not found then raise exception 'Invalid, expired or revoked invitation' using errcode='42501'; end if;
 if not ((invitation.contact_method='email' and invitation.contact_value=verified_email)
      or (invitation.contact_method='phone' and invitation.contact_value=verified_phone)) then
    raise exception 'Verify the invited email or mobile on this account' using errcode='42501';
 end if;
 if exists(select 1 from public.platform_owner_identity_lock l where l.owner_user_id=actor) then
   raise exception 'Platform owner cannot become a Co-Admin' using errcode='42501';
 end if;
 if exists(select 1 from public.platform_coadmins c where c.user_id=actor and c.active) then
   raise exception 'Co-Admin already active' using errcode='42501';
 end if;
 insert into public.platform_coadmins(user_id,invited_by,contact_method,contact_value,active,revoked_at)
 values(actor,invitation.invited_by,invitation.contact_method,invitation.contact_value,true,null)
 on conflict(user_id) do update set active=true,revoked_at=null,
   invited_by=excluded.invited_by, contact_method=excluded.contact_method,
   contact_value=excluded.contact_value;
 update public.platform_coadmin_invitations
 set claimed_at=now(),claimed_by=actor where invitation_id=invitation.invitation_id;
 return true;
end;
$fn$;
revoke all on function public.claim_platform_coadmin_invitation(text) from public,anon;
grant execute on function public.claim_platform_coadmin_invitation(text) to authenticated;
