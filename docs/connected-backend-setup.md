# SOCYTO — Connected backend setup

## Current cloud project

- Organization: **socyto**
- Project: **SOCYTO Production**
- Region: `ap-south-1` (Mumbai)
- Project ref: `ycpwqdukfsmxrfntsfyd`
- API URL: `https://ycpwqdukfsmxrfntsfyd.supabase.co`
- Installed migrations: `socyto_001` through `socyto_010` (there is no migration 000; 009 fixes anon access)
- No firms, clients, or auth users have been provisioned yet.

## GitHub Actions configuration (repo administrator)

The normal debug workflow builds independently of the backend and is **not a usable connected-release signal**. Use the separate manually-triggered **SOCYTO Connected Android APK** workflow once configured. This workflow fails instead of building a disconnected APK if the variables are missing.

Under [SOCYTO repository Actions variables](https://github.com/shevateshubham/socyto/settings/variables/actions), save these **repository variables** (not secrets):

| Name | Value |
|---|---|
| `SOCYTO_SUPABASE_URL` | `https://ycpwqdukfsmxrfntsfyd.supabase.co` |
| `SOCYTO_SUPABASE_PUBLISHABLE_KEY` | Supabase dashboard → Project Settings → API Keys → publishable key (`sb_publishable_...`) |

Never use the secret, service_role, database password, or any other privileged key in Android or a public repo.

After setting variables, trigger **Actions → SOCYTO Connected Android APK → Run workflow**. Download its `socyto-connected-apk` artifact.

## First Super Admin account

1. Use the connected APK to register using your actual email address and a strong password.
2. Open your confirmation email and verify the Supabase Auth email address.
3. In Supabase SQL Editor, *after confirming the email belongs to the intended platform owner*, run the bootstrap operation below, replacing the one bind literal with the actual verified email:

```sql
insert into public.platform_administrators (user_id)
select id from auth.users
where lower(email) = lower('YOUR_VERIFIED_EMAIL@EXAMPLE.COM')
  and email_confirmed_at is not null
on conflict (user_id) do nothing;
```

Confirm exactly one platform admin exists and sign back into the connected APK. Never put login passwords or privileged API keys in GitHub. This SQL requires a trusted database administrator; no Android user can self-assign the role.

## Firm registration and client access

- Super Admin registers a firm by its verified Business Admin email.
- New firms start in `pending`; Super Admin activates the firm before access.
- Business Admin adds clients and projects, and links existing email-verified client accounts.
- Business Admin edits requirements; only explicit publishing reveals snapshots to clients.
- No role may see another tenant's private projects, contacts, draft requirements or receipts.

## Verification already performed

- Confirmed 10 SOCYTO public tables have RLS enabled.
- Confirmed anonymous callers have no access to these tables and cannot execute the privileged RLS helper functions.
- Confirmed clients cannot directly write project publications or link arbitrary login identities.
- Executed two-firm SQL-role isolation checks with transactional fixture data, including project access, forbidden cross-firm writes and draft/financial privacy. The entire transaction was rolled back; verified zero business, project, and Auth records remain.
- Supabase advisors still warn about signed-in access to intentionally callable SECURITY DEFINER API functions. They check permissions internally; review this privilege model and test real authenticated HTTP calls before production.
- **Not yet completed:** real-email signup, Super Admin bootstrap, populated two-firm integration using real Auth sessions, and on-device testing.
