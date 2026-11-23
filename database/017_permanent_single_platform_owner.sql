-- Single-owner security boundary. Owner contacts are seeded separately as
-- trusted deployment data, never committed into public source control.
create table if not exists public.platform_owner_identity_lock (
  singleton boolean primary key default true check (singleton),
  owner_user_id uuid not null references auth.users(id) on delete restrict,
  approved_email text not null,
  approved_phone text not null,
  created_at timestamptz not null default now(),
  constraint socyto_owner_email_lowercase check (approved_email = lower(approved_email)),
  constraint socyto_owner_phone_e164 check (approved_phone ~ '^[+][1-9][0-9]{7,14}$')
);
alter table public.platform_owner_identity_lock enable row level security;
revoke all on public.platform_owner_identity_lock from public, anon, authenticated;

-- Prevent a separate identity from being assigned the platform owner role.
create or replace function public.enforce_locked_super_admin_identity()
returns trigger language plpgsql security definer set search_path=''
as $function$
begin
  if not exists (
    select 1 from public.platform_owner_identity_lock l
    where l.owner_user_id = new.user_id
  ) then
    raise exception 'SOCYTO platform ownership is restricted to the locked owner account'
      using errcode='42501';
  end if;
  return new;
end;
$function$;
revoke all on function public.enforce_locked_super_admin_identity() from public, anon, authenticated;
drop trigger if exists socyto_locked_super_admin_identity on public.platform_administrators;
create trigger socyto_locked_super_admin_identity
before insert or update of user_id on public.platform_administrators
for each row execute function public.enforce_locked_super_admin_identity();

-- Even a valid invitation cannot authorize a second, unrelated user.
create or replace function public.claim_platform_admin_bootstrap(one_time_code text)
returns boolean
language plpgsql security definer set search_path=''
as $function$
declare
  actor uuid := (select auth.uid());
  claim_id uuid;
  verified_email text;
  verified_phone text;
begin
  if actor is null then
    raise exception 'Sign in before activating platform access' using errcode='42501';
  end if;
  select case when u.email_confirmed_at is not null then lower(u.email) end,
         case when u.phone_confirmed_at is not null then
           case when u.phone like '+%' then u.phone else '+' || u.phone end end
    into verified_email, verified_phone
  from auth.users u where u.id=actor;

  if not exists (
    select 1 from public.platform_owner_identity_lock l
    where l.owner_user_id=actor
      and (verified_email=l.approved_email or verified_phone=l.approved_phone)
  ) then
    raise exception 'Not authorized as the SOCYTO platform owner'
      using errcode='42501';
  end if;
  if one_time_code is null or one_time_code !~ '^[a-f0-9]{64}$' then
    raise exception 'Invalid activation code' using errcode='22023';
  end if;
  select i.invitation_id into claim_id from public.platform_admin_bootstrap_invites i
  where i.token_digest=encode(extensions.digest(one_time_code,'sha256'),'hex')
    and i.intended_user_id=actor and i.claimed_at is null and i.expires_at>now()
  for update;
  if claim_id is null then
    raise exception 'Invalid or expired activation code' using errcode='42501';
  end if;
  update public.platform_owner_authorizations a set consumed_at=now()
  where a.consumed_at is null
    and ((a.contact_method='email' and a.contact_value=verified_email)
      or (a.contact_method='phone' and a.contact_value=verified_phone));
  if not found then
    raise exception 'Owner approval missing or already used' using errcode='42501';
  end if;
  insert into public.platform_administrators(user_id) values(actor)
  on conflict(user_id) do nothing;
  update public.platform_admin_bootstrap_invites
  set claimed_at=now(),claimed_by=actor where invitation_id=claim_id;
  return true;
end;
$function$;
revoke all on function public.claim_platform_admin_bootstrap(text) from public, anon;
grant execute on function public.claim_platform_admin_bootstrap(text) to authenticated;
