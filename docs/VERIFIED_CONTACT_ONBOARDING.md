# SOCYTO — Contact-flexible provisioning and verified platform owner activation

## Intended journeys

### Super Admin (the pre-authorized platform owner)
1. Register with either email or an international-format mobile number.
2. Complete Supabase's real email confirmation or SMS OTP confirmation. Verification never grants an elevated role by itself.
3. After confirming the email or mobile, sign in and **explicitly tap Send activation code**. Normal signup and login never create or send privileged invitations.
4. The backend checks the verified Auth identity against `platform_owner_authorizations`, which only a trusted database operator can provision. It generates a cryptographically random 64-hex code, stores only its SHA-256 digest, binds the invitation to the current Auth user ID, and sends it to the same verified, pre-approved contact.
5. Enter the one-time code in the app. It expires after 30 minutes; the recipient must match the approval and the inviting identity. The approval is consumed on successful activation.
6. Subsequent sign-in opens Super Admin. Standard user signups cannot request platform access.

**Delivery configuration required before this can work:** configure a verified Resend sender and `SOCYTO_ACTIVATION_EMAIL_API_KEY` + `SOCYTO_ACTIVATION_EMAIL_FROM` for email, and/or an enabled Twilio sender with `SOCYTO_ACTIVATION_TWILIO_SID`, `SOCYTO_ACTIVATION_TWILIO_TOKEN`, `SOCYTO_ACTIVATION_TWILIO_FROM` for SMS. India-region SMS may require DLT/sender registration. Keep all secrets in Supabase Edge Function secrets, never in Android, GitHub source, or documentation.

A trusted operator must populate `public.platform_owner_authorizations` for the platform owner's independently verified contact. This is not a public registration endpoint. Code delivery fails closed with HTTP 503 if the selected messaging provider has not been set up. After verification, the owner may explicitly request an owner-bound activation code. Without configured delivery the app stays secure and the code cannot be sent.

### Firm and client registration
- Super Admin creates a firm by entering its name and **Mobile or email**, and receives F-prefixed login ID plus one-time temporary password. Standard Indian 10-digit mobile input is normalized to E.164 (+91); other countries use international format.
- Firm Admin creates a client by entering name and **Mobile or email**, and receives C-prefixed login ID plus one-time temporary password.
- If email is provided, the Auth identity uses that actual address, uses Supabase's normal email signup to request the initial confirmation message and stays unverified until the recipient confirms it. They can request a new confirmation from the sign-in screen; error responses must not reveal account existence.
- If mobile is provided, the Auth identity is linked to that phone; the generated account ID and temporary password work using the existing first-login password-change gate. Real SMS OTP remains a prerequisite for phone-recovery proof.
- F/C ID login now resolves the stored Auth email (real if email-only, internal if mobile-only) only on the server, then authenticates password using Supabase Auth. A wrong password never grants a session.
- Clients see only their own published project updates, never their firm's draft work or other tenants.

## Operational controls
- Confirm Supabase Auth's redirect URL is a reachable app/web address; localhost links are not useful to customers.
- Confirm SMTP and SMS providers, regional routing, delivery quotas, OTP expiry, and abuse/rate limits.
- Test Super Admin code deliver/consume, incorrect recipient, expired code, repeated attempts, email delivery errors and SMS delivery errors.
- Test email-only and phone-only firm/client creation, confirmation, first-login change, login by F/C ID, direct email/mobile login, cross-tenant isolation and client publication gating.
- Release signed Android updates only after compatible backend deployment and passing emulator/device tests.
- **No automatic elevation of arbitrary verified users.** All codes are bound to explicit owner preapproval.

## Owner-only, on-demand issuance rule (2026-11-23)
- Signing in or confirming an email/mobile **never** issues an activation code automatically.
- The verified, pre-approved platform owner explicitly taps **Send activation code to verified email or mobile**.
- The system generates at most **one unclaimed activation code per user**. A valid, unclaimed code is not regenerated, replaced, or resent by another request.
- The invitation expires 30 minutes after generation. Expiry alone does not issue a replacement: another code is possible only after the owner explicitly requests it again, and the expired invitation is retired.
- A consumed invitation cannot be used twice. An account already holding Super Admin access cannot request more bootstrap codes.
- Rate limiting applies to explicit requests; account creation, verification, normal login, and page reloads cannot regenerate codes.
- The backend checks a verified phone/email against private preapproval; no ordinary firm/client account can get an elevated activation code.
- Email/SMS delivery requires operational provider secrets and real delivery verification, otherwise requests fail closed. Do not claim a code was delivered when the provider is not configured.

## Permanent, single-person SOCYTO Super Admin owner lock
- The platform owner is anchored to one existing, already verified Auth user ID in `platform_owner_identity_lock`.
- Exactly one approved verified email contact and one approved mobile number are held in a **private production database row**, not embedded in the mobile app or GitHub source.
- Authentication and activation require the **same locked user ID** plus one verified approved contact. Merely taking control of an approved phone number or address from a second account never grants Super Admin access.
- The secondary phone cannot trigger activation delivery until it is verified as a contact on that locked Auth identity.
- Database triggers prohibit additional owner approvals, privilege assignment to another user, or routine changes to the owner lock. Replacing the owner requires an explicit reviewed database migration.
- Existing firm creation, client creation, project progress, and read-only client access are unaffected.
- Normal login does not generate any activation code. Only a deliberate request does; repeat requests cannot rotate an active unclaimed code.
- SOCYTO activation messaging still depends on operational provider credentials. The backend fails closed rather than pretending to deliver an email/SMS.
- Live database verification after deployment: one lock, two matching contacts, one matching Super Admin, zero outstanding owner invitations; negative tests for a third contact and a second administrator were rejected.

## Restricted Co-Admin delegation
- Only the permanently locked, authenticated Primary Super Admin can create, list, cancel, and revoke Co-Admin invitations using `coadmin-management`. This is a separate role from `platform_administrators` and does not change the permanent owner lock.
- Primary Owner enters a mobile or email, receives a random 64-character, once-visible invitation code and shares it privately. An active invitation is never silently regenerated. Invitations expire after 24 hours and may be cancelled.
- The invitee signs up independently and verifies the nominated email/mobile; they then enter the code on the **Awaiting access** screen. A server-side RPC checks the verified contact, code digest, expiry and revocation state before granting restricted access.
- Active Co-Admins can list and manage firms, create firm accounts, update business status and firm branding, and view the platform's firm/project list. They cannot issue Super Admin activation codes, alter the Primary Owner, invite or revoke other administrators, or access client-private draft requirements unless they are separately assigned as that firm's administrator.
- The Primary Owner sees Co-Admin contact, active/revoked state and pending invitations, and can revoke access or cancel invitations with confirmation. Access is checked live in the database, so revocation takes effect without waiting for a JWT claim refresh.
- Only invitation digests are stored; the code is returned to the owner once. Messaging delivery is **not** claimed or automatic—the owner shares the invitation through their own trusted channel. No new dummy credentials or platform owner records are introduced.
- Before release: confirm Kotlin and Deno builds, check per-role RLS, verify an invited user accepts a real contact, and verify revoked sessions lose all firm-management permissions.

## Zero-record first-owner recovery

- After wiping all user data, **no Auth user, owner lock, authorization, or invitation remains**.
- A single private configuration row retains the two approved owner contacts, without generating an Auth account or entitlement.
- On the first **explicit** activation-code request from a verified approved user, the service-role-only RPC `bootstrap_verified_platform_owner` securely re-establishes the immutable owner lock and the two contact approvals.
- The RPC cannot be invoked by `anon` or `authenticated` database roles; it does not mint a code, send email/SMS, or grant admin privileges.
- The regular activation delivery process still requires a configured provider. Until it is configured the signed-in user remains unassigned and no code can be issued.
- Never embed approved owner contact values in public GitHub migrations, client-side code, application assets or screenshots. Provision them privately in the database, and preserve configuration independently from customer data.
