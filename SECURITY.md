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

## Phone-linked account provisioning
- Firm/client IDs are public-facing handles only; password or verified SMS OTP is required to authenticate.
- Provisioning must run in a trusted Edge Function with server-side role and tenant checks; never put an Auth administrative secret in the app.
- Temporary passwords are strong server-generated secrets, never logged, committed, or stored in client/firm records; display only once to the provisioning administrator.
- Reject existing phone identities rather than silently linking or replacing them. Require additional design before allowing one phone to manage multiple accounts.
- Keep first-login access blocked in RLS for firm and client users until a trusted backend clears the password-reset-required flag.
- Password recovery uses Supabase Auth SMS OTP with no signup, OTP expiry and rate limits; do not treat possession of an ID or a mobile number string as identity verification.
- Confirm the Auth provider can support reserved internal login addresses before shipping. Never use synthetic addresses for mail delivery or security notifications.
- Keep the new provisioning branch undeployed until SMS, RLS, cross-tenant and connected device tests pass.

## Unified login protections
- A public endpoint can map a mobile login identifier only inside the trusted backend; never expose the resolved synthetic Auth email or a user-existence endpoint to clients.
- Passwords must be validated by Supabase Auth. Keep identifier failures generic and honor authentication rate limits. Before real-customer rollout add gateway-level rate limiting and abuse monitoring for the public phone-login endpoint.
- Mobile sign-up requires Supabase SMS delivery and OTP verification, which must be confirmed with a working provider before being advertised as available.
- The bootstrap RPC allows Super Admin activation only after verified email OR verified phone AND presentation of a valid, unexpired, single-use invitation. It must not create an invitation automatically from public signup.
- Do not turn on `phone_confirm=true` or `email_confirm=true` for unverified user-supplied identities merely to bypass delivery issues.

## Restricted Co-Admin delegation
- The permanent Primary Super Admin remains the only record in `platform_administrators` and the only identity in `platform_owner_identity_lock`. Never relax the single-owner trigger for delegated administrators.
- Delegated administrators are stored separately in `platform_coadmins`. Only the Primary Owner can call the authenticated `coadmin-management` Edge Function to issue, cancel, list or revoke invitations.
- Invites use CSPRNG 256-bit one-time codes; only their SHA-256 digests are stored. The invited user's **verified** email or phone must match exactly; invitations expire after 24 hours and cannot be used twice.
- Co-Admin invitation codes are shown once to the Primary Owner for private transfer. There is no assumed email/SMS sending, no mock identity and no hardcoded shared password.
- Co-Admin access to firms is enforced on every privileged call via `is_platform_operator()` and the active `platform_coadmins` row. Revoked users have no operational authority even with an unexpired JWT.
- Co-Admins cannot manage Co-Admins, claim primary ownership, generate Primary Super Admin codes, or change the owner-contact lock.
- Firm-level client/project editing continues to belong to the firm's own business administrator. Delegation does not grant access to material receipts or unpublished client requirements across tenants.
- A passing compilation is not a substitute for live distinct-user privilege boundary tests. Before rollout, verify invite expiry, identity mismatch, cancellation, reuse, revocation, firm status changes, and client project isolation.
