# SOCYTO Security and Access Rules

## Default stance
Deny by default. Never trust UI role checks as an authorization boundary. Treat client project information, progress drafts, receipts and cost details as private.

## Identity and access
- Use real managed authentication; never ship dummy, shared or hardcoded test credentials.
- Admin role assignment must be controlled server-side; a user cannot self-promote by editing their profile.
- Link clients to projects through protected membership assignments.
- Enforce access in backend/DB policies for every project, requirement, receipt and published snapshot.
- Clients read only their assigned projects' published snapshots and explicitly approved related data; never unpublished drafts, other clients, internal notes or costs by default.
- Require admin permission for draft edits, materials writes and publication.

## Supabase-specific boundaries
- Use only publishable keys in Android builds; never ship secret/service-role keys.
- Apply RLS on all Data API exposed project tables, scoped by authenticated user and verified membership. Authentication alone is not sufficient authorization.
- Do not base RLS role authorization on user-editable user_metadata; maintain protected roles/membership or trusted app_metadata.
- For UPDATE operations, scope both USING and WITH CHECK; ensure necessary SELECT policies.
- Avoid SECURITY DEFINER functions and RLS-bypassing views; when unavoidable, review execute grants and explicit caller validation.
- Secure storage buckets and object paths separately from table RLS; use short-lived links where appropriate.
- Test cross-client isolation, publication gating and direct API access, not only UI visibility.

## Operational controls
- Keep signing keys, invoices and sensitive customer files out of this public GitHub repository.
- Mask personal data and auth tokens from logs, crash reports and screenshots.
- Audit publication actions, privilege changes and sensitive record corrections.
- Rotate exposed credentials, revoke compromised sessions and handle access changes.
- Maintain distinct dev/test/prod configurations and prevent debug data reaching production.
- Security reviews are required before production release and after changing any policy/schema.
