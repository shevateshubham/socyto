# SOCYTO — Clean-start onboarding checklist

## 0. Pre-flight
- Install the **connected** SOCYTO Android build with its Supabase URL and publishable key configured.
- Never include service-role keys, shared QA credentials, or dummy users.
- User signs up with the permanently approved Primary Owner email and clicks the **signup confirmation link**.
- The signup confirmation link is the sole email proof for initial owner activation. A second email/OTP is deliberately not used.
- Authentication confirmation alone does not grant the Super Admin role: the user must **sign in and explicitly tap Activate Primary Super Admin**.
- The privileged Edge Function independently validates the authenticated Supabase account, verified email, permanent approved-owner allowlist, immutable exclusive-owner lock, and a single-use owner authorization.

## 1. Create Primary Super Admin
1. Register with the approved Primary Owner email; follow the Supabase signup verification link.
2. Sign in with the email and password. Before activation, the account is unassigned.
3. Tap **Activate Primary Super Admin** exactly once. The server checks eligibility and grants the role.
4. The application refreshes the role and opens the Super Admin workspace.
5. Future sign-ins use the same account, without a second activation email, OTP, or role grant.
6. A different email or unverified account cannot activate the Primary Owner role.

## 2. Super Admin provisions firm and Firm Admin login
1. Sign in as the verified Super Admin.
2. Choose **New firm** and enter firm name, business type, and the desired Firm Admin email.
3. Tap **Create firm and Firm Admin login**. The privileged backend creates a real auth identity with a random temporary password and a mandatory password-reset flag. It then registers the firm, membership, and branding using the real signed-in Super Admin permissions. Firm starts **pending**.
4. The Firm Admin login ID and temporary password are shown **once**, only to Super Admin. Copy and share privately.
5. In the Super Admin business list, **Activate** the firm.
6. Sign out; sign in with the newly provisioned Firm Admin login ID and temporary password.
7. The app must require a password change before granting workspace access. Change password, then sign out and sign back in using the new password.
8. Only after the firm is active and password reset is complete may the Firm Admin create clients and projects.
9. Firm Admins do not self-register. Existing emails are never silently taken over or assigned a new password.

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
- Missing signup confirmation email: check the Supabase signup template and email delivery configuration. No additional owner activation email is required.
- Unverified account: check Supabase Auth email confirmation.
- Firm Admin cannot enter: check firm account **active** and verified membership.
- Client cannot enter: ensure login was provisioned and one-time password replaced.
- Project missing: check client-to-project assignment, the firm account state and row-level security.
- Missing progress: publish from the Firm Admin account after adding requirements.
- Unexpected cross-client access: stop testing and audit RLS immediately.

## Verification status
This is an operational checklist, not a record of successful end-to-end manual testing. The full path must be tested using Super Admin-provisioned Firm Admin and Client credentials.
