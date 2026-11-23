-- A platform owner can have only one outstanding activation invitation.
-- The issue endpoint removes EXPIRED invitations for the same user only after
-- a new, explicit request, so old codes are never silently regenerated.
create unique index if not exists socyto_one_outstanding_owner_invitation
  on public.platform_admin_bootstrap_invites(intended_user_id)
  where intended_user_id is not null and claimed_at is null;
