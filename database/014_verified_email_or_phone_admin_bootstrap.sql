-- An initial platform owner can verify email OR phone, never merely claim either.
-- The one-time bootstrap token remains mandatory and single-use.
create or replace function public.claim_platform_admin_bootstrap(one_time_code text)
returns boolean
language plpgsql
security definer
set search_path = ''
as $function$
declare
  claim_id uuid;
  actor uuid := (select auth.uid());
begin
  if actor is null then
    raise exception 'Sign in before activating platform access' using errcode='42501';
  end if;
  if not exists (
    select 1 from auth.users u
    where u.id = actor and
      (u.email_confirmed_at is not null or u.phone_confirmed_at is not null)
  ) then
    raise exception 'Verify your email or mobile before activating Super Admin access'
      using errcode='42501';
  end if;
  if one_time_code is null or one_time_code !~ '^[a-f0-9]{64}$' then
    raise exception 'Invalid or expired setup invitation' using errcode='22023';
  end if;
  select i.invitation_id into claim_id
  from public.platform_admin_bootstrap_invites i
  where i.token_digest = encode(extensions.digest(one_time_code, 'sha256'),'hex')
    and i.claimed_at is null and i.expires_at > now()
  for update;
  if claim_id is null then
    raise exception 'Invalid or expired setup invitation' using errcode='42501';
  end if;
  insert into public.platform_administrators (user_id) values (actor)
    on conflict (user_id) do nothing;
  update public.platform_admin_bootstrap_invites
    set claimed_at = now(), claimed_by = actor where invitation_id = claim_id;
  return true;
end;
$function$;
