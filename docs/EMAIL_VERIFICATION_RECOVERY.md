# Email verification recovery — SOCYTO 0.8.4

## Incident evidence (2026-11-23)
Supabase Auth created an email/password account with email confirmation pending. An auth log recorded a confirmation email being sent, followed by an attempted GET /verify rejected with **One-time token not found**. Subsequent POST /token password grants failed with `email_not_confirmed`. The account existed but was not verified; zero platform administrator records and zero bootstrap invitations existed at inspection.

## Fix
- Android sign-in parses Supabase's stable error code `email_not_confirmed`, displaying an actionable verification message instead of the incorrect generic password error.
- If a syntactically valid email is entered, the account screen displays a **Resend verification email** action that calls `POST /auth/v1/resend` with `{type:"signup",email}`.
- Request responses remain generic and do not disclose existence/verification state to an unauthenticated caller. Rate limit failures display an appropriate message.
- The login screen no longer duplicates the entered email below the field.
- No code path sets `email_confirmed_at` by hand, bypasses ownership verification, or creates Super Admin privileges automatically.
- After email verification, users must sign in and redeem a new expiring, single-use Super Admin activation invitation to receive platform privileges.

## Operational follow-up
1. Verify actual email delivery from Supabase and the configured SMTP provider.
2. If fresh confirmation links continue reporting invalid/expired tokens, investigate email security scanners prefetching links and change the confirmation template to support explicit OTP verification in app. Review Auth Site URL (currently found to reference localhost:3000 in recent logs), redirect allowlist and custom domain.
3. End-to-end test with a fresh email, confirmation, successful login, invitation claim, and Super Admin workspace.
4. SMS signup/recovery is a separate path and requires an operational SMS provider. A green Android layout test does not prove email or SMS delivery.

Reference: https://supabase.com/docs/guides/auth/passwords
