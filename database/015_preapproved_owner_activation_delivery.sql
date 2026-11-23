-- Delivery is restricted to contacts preapproved by the existing platform
-- owner or a trusted database operator. A verified public signup is not enough.
create table if not exists public.platform_owner_authorizations (
  authorization_id uuid primary key default gen_random_uuid(),
  contact_method text not null check (contact_method in ('email','phone')),
  contact_value text not null,
  approved_at timestamptz not null default now(),
  last_sent_at timestamptz,
  consumed_at timestamptz,
  unique(contact_method,contact_value)
);
alter table public.platform_owner_authorizations enable row level security;
revoke all on public.platform_owner_authorizations from anon, authenticated;

alter table public.platform_admin_bootstrap_invites
  add column if not exists intended_user_id uuid references auth.users(id) on delete cascade;

create index if not exists socyto_invites_intended_user
  on public.platform_admin_bootstrap_invites(intended_user_id);

-- Only the person preapproved for this specific code can claim it.
create or replace function public.claim_platform_admin_bootstrap(one_time_code text)
returns boolean
language plpgsql security definer set search_path = ''
as $function$
declare
  claim_id uuid;
  actor uuid := (select auth.uid());
  verified_email text;
  verified_phone text;
begin
  if actor is null then
    raise exception 'Sign in before activating platform access' using errcode='42501';
  end if;
  select case when email_confirmed_at is not null then lower(email) end,
         case when phone_confirmed_at is not null then
           case when phone like '+%' then phone else '+' || phone end end
    into verified_email, verified_phone
  from auth.users where id = actor;
  if verified_email is null and verified_phone is null then
    raise exception 'Verify your email or mobile before activating Super Admin access'
      using errcode='42501';
  end if;
  if one_time_code is null or one_time_code !~ '^[a-f0-9]{64}$' then
    raise exception 'Invalid or expired activation code' using errcode='22023';
  end if;
  select i.invitation_id into claim_id
  from public.platform_admin_bootstrap_invites i
  where i.token_digest = encode(extensions.digest(one_time_code,'sha256'),'hex')
    and i.intended_user_id = actor
    and i.claimed_at is null and i.expires_at > now()
  for update;
  if claim_id is null then
    raise exception 'Invalid or expired activation code' using errcode='42501';
  end if;
  update public.platform_owner_authorizations a set consumed_at = now()
  where consumed_at is null
    and ((a.contact_method = 'email' and a.contact_value = verified_email)
      or (a.contact_method = 'phone' and a.contact_value = verified_phone));
  if not found then
    raise exception 'Platform-owner approval missing or already used' using errcode='42501';
  end if;
  insert into public.platform_administrators(user_id) values (actor)
    on conflict (user_id) do nothing;
  update public.platform_admin_bootstrap_invites
    set claimed_at = now(), claimed_by = actor where invitation_id = claim_id;
  return true;
end;
$function$;

revoke all on function public.claim_platform_admin_bootstrap(text) from public,anon;
grant execute on function public.claim_platform_admin_bootstrap(text) to authenticated;
