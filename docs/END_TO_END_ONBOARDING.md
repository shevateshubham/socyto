# SOCYTO — Clean-start onboarding checklist

## 0. Pre-flight
- Install an APK built from the secure owner-email OTP branch (the old 64-character invitation UI is obsolete).
- Set the real `SOCYTO_SUPABASE_URL` and `SOCYTO_SUPABASE_PUBLISHABLE_KEY` as GitHub Actions repository variables (Settings → Secrets and variables → Actions → Variables), and rebuild the APK.
- Never embed service-role keys or reuse deleted QA credentials.
- The approved, verified owner explicitly requests an OTP. Supabase Auth generates and emails the one-time secret; SOCYTO does not log or store its plaintext. Requests expire in 10 minutes, with at most 5 verification attempts.
- **External configuration required:** In Supabase Authentication > Email Templates > Magic Link, include `{{ .Token }}` in the email body, so users receive a numeric OTP rather than only a magic link. Verify actual inbox delivery. Do not mistake an Auth `mail.send` log for inbox confirmation.

## 1. Create the first Super Admin (fresh account)
1. Open SOCYTO, choose Create account with an email you control, set a password.
2. Verify your email from the confirmation message.
3. Sign in with your new account. It must initially show **Awaiting access** (not automatic Super Admin).
4. In **One-time administrator activation**, tap **Request activation code by email** explicitly. Only the permanently approved and verified owner identity is eligible.
5. Check the approved email, type the numeric code, then tap **Verify code and activate**. Repeated requests are blocked while a challenge is pending.
6. The backend must validate the one-time code before atomically granting `platform_administrators`. The dashboard then shows **Super Admin**; reused or expired codes fail.
7. If the already-activated approved owner signs in, no new code is generated.

## 2. Register a firm using a real verified account
1. Sign out of Super Admin. Create a *separate* Firm Admin account using its own controlled email and password.
2. Verify that firm's email and then sign out.
3. Sign in as Super Admin. Choose **New firm** and enter its name, business type and verified Firm Admin email.
4. The database function registers the firm, links the verified Firm Admin and creates branding in one transaction. The firm begins in **pending** state.
5. In the Super Admin business list, tap **Activate**. Then sign out.
6. Log in as the Firm Admin. If the firm remains pending or suspended, the Firm Admin will not have project access.
7. Firm accounts are *not* automatically issued temporary passwords in this release: separate verified signup is required. Do not skip this step.

## 3. Firm Admin creates client and project
1. Open **Clients and new projects**.
2. Enter a new client's name and email. Save the client.
3. Create a project for that client. The backend saves the real project and then tries to generate a unique one-time client login.
4. The one-time password is visible only at creation. Copy it privately before leaving the page.
5. If the client already has a verified account, use **Link verified login** instead of trying to overwrite the existing password.
6. In the project, create custom binary or quantity requirements and set 1/2/3 effort scores.
7. Update task progress in the private draft. It is not visible to the client before explicit publishing.
8. Choose **Publish** to create the server-side progress snapshot.

## 4. Client signs in and views the published project
1. Open SOCYTO, sign in using the assigned email and temporary password.
2. Change that password at the enforced first-login step, then sign in again.
3. Open **My projects**; only authorized projects and published progress must appear.
4. Edits by Firm Admin without another publish must remain invisible to the client.

## Troubleshooting and acceptance
- No email OTP: confirm the Magic Link template contains `{{ .Token }}`, Supabase Auth email delivery is enabled, and check the email provider / spam folder. A pending code is not resent automatically.
- Unverified account: check Supabase Auth email confirmation.
- Firm Admin cannot enter: check firm account **active** and verified membership.
- Client cannot enter: ensure login was provisioned and one-time password replaced.
- Project missing: check client-to-project assignment, the firm account state and row-level security.
- Missing progress: publish from the Firm Admin account after adding requirements.
- Unexpected cross-client access: stop testing and audit RLS immediately.

## Verification status
This is an operational checklist, not a record of successful end-to-end manual testing. The full path must be tested using newly registered Super Admin, Firm Admin and Client credentials.
