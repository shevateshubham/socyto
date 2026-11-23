# SOCYTO — Firm and client IDs (feature branch)

## User-facing journey
1. Existing verified Super Admin signs in with their administrator email/password.
2. Under Add firm, Super Admin enters firm name and registered mobile number in E.164 format (e.g. +91 followed by 10 digits).
3. Trusted Edge Function verifies Super Admin membership, generates a random F + 9-digit login code and a strong 48-character temporary password, creates a managed Auth identity, active firm, business-admin membership and branding. Returns credentials once.
4. Firm signs in using the code, and replaces the temporary password before any firm data is accessible.
5. Firm selects Add client, enters name and mobile; the trusted Edge Function creates a random C + 9-digit code, account, and firm-scoped client record. No email or project is required.
6. Client changes initial password before seeing data. Firm separately creates the client project, writes custom weighted requirements and publishes progress.
7. Client signs in using the code and sees only the latest published snapshots for projects assigned to their own verified Auth identity.
8. Forgotten password: user enters registered phone, receives SMS OTP through Supabase Auth, verifies it and sets a new password. No manual reset or reusable hardcoded password.

## Important distinctions
- Codes F######### and C######### are *login handles*, not passwords or authorization tokens. UUIDs remain internal identities.
- The synthetic email mapping is internal to Auth because existing SOCYTO API uses email/password Auth; never send mail to the reserved domain.
- The Super Admin bootstrap path using verified personal email remains intact; it must not give arbitrary users platform privileges.
- Existing email-linked firms/clients continue to work; the previous legacy onboarding endpoint remains for compatibility pending later removal.
- A phone can only belong to one managed Auth user in the current model. Shared phones require a separately designed multi-account recovery method.
- SMS OTP requires a working SMS provider, phone Auth enabled, region rules/fees, and rate-limits. **The source code alone does not configure this.**
- If Auth rejects synthetic email identities, adjust the identity-provider strategy before deployment and verify on a nonproduction project.
- Temporary password generation uses crypto.getRandomValues and is returned only in one response. Do not log, save, or hardcode it.
- Creating active firms is intentional for this simplified onboarding flow. Suspending them revokes access through firm-status checks.

## Files
- database/013_human_account_ids.sql: human codes, contact phone, unique constraints, first-login firm access gate.
- supabase/functions/client-onboarding/index.ts: protected firm and client account provisioning, legacy client provisioning and password change.
- Android: SocytoApi, RegisterBusinessForm, BusinessSiteForms, SocytoClientOnboarding, SocytoFlootLogin, MainActivity.
- .agents/skills/account-onboarding/SKILL.md: reusable engineering checklist.

## Before rollout
1. Verify migrations 001–012 actually exist in the target database and deploy 013 in a controlled manner.
2. Review the Edge Function against real Auth settings, verify SMS provider and existing function deployment.
3. Deploy function only after database supports new fields.
4. Run Android compile and unit tests on GitHub Actions; install connected APK.
5. Run fresh Super Admin → F account → C account → project → publish → client test.
6. Test all negative scenarios: wrong role, pending/suspended firm, repeated phone, invalid SMS OTP, first-login data denial, cross-tenant read, failed database insert and rollback.
7. Verify credential screen once-only semantics and private copying; never post real passwords to GitHub or logs.

## Release status
This branch contains an implementation proposal. It has not been deployed to the connected production database, and the end-to-end SMS and device tests have **not** been proven successful. Merge only after CI and connected acceptance checks.
