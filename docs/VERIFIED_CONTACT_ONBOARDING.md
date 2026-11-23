# SOCYTO — Verified owner, firm and client onboarding

## Primary Super Admin (email-first, SOCYTO 0.8.9)

1. The permanent Primary Owner registers using the privately approved email and verifies it using **Supabase Auth's standard email confirmation**.
2. Sign in normally. Registration or verification alone does not grant administrator rights.
3. On Awaiting access, **explicitly tap Activate Primary Super Admin**. No code field and no separate activation email/SMS is required.
4. A trusted Edge Function verifies the real Auth session and verified email; service-role-only RPCs check the private owner allowlist and immutable owner user ID lock.
5. A single database transaction consumes the approved email authorization and grants the permanent owner role. Retries for an already-active owner are safe and do not create a second role.
6. Only the locked, email-verified Primary Owner is eligible. A different email, mobile number or user account must fail. The permanent owner cannot be reassigned through the app.

**Zero-record fresh start:** Owner contacts remain as private configuration, independent of user records. The first explicit activation request from the approved and email-verified owner establishes the lock; it does not grant access until the final verified-activation transaction succeeds.

**No custom activation email service is needed.** Do not reintroduce code generation for the Primary Owner. SMS remains optional until a working SMS provider is configured and tested.

## Firm and client

- Primary Owner or active operational Co-Admin can create a firm using its name and a mobile or email contact, receiving a generated F-prefixed login ID and temporary password.
- Firm admins can create clients with a mobile or email contact, receiving generated C-prefixed IDs and temporary passwords.
- Email-provisioned users must complete Supabase Auth email verification. Mobile OTP signup and recovery require a functioning SMS provider.
- First-login password changes are required for managed accounts. Password checks and tenant roles are enforced server-side.
- Clients see only the progress explicitly published for their assigned project.

## Co-Admins

Co-Admin invitation codes remain owner-created, private and one-time. Co-Admins can manage firms but cannot alter the Primary Owner, create another administrator or change the approved owner contacts. Revocations take effect through database authorization rules.

## Acceptance and operations

- Verify the Primary Owner can activate with the approved, verified email, and incorrect identities are denied.
- Validate firm/client ID login, password-change gates, project assignment, publication visibility, and cross-tenant isolation with real accounts.
- Validate Co-Admin invitation, reuse rejection and revocation.
- Keep SMS registration unavailable until SMS delivery is working. Correct the Supabase Auth localhost redirect for a proper mobile confirmation experience.
- Build and test Android release on compact, standard, tablet, landscape and enlarged text profiles; retain the release signing certificate.
