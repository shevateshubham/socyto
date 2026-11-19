# SOCYTO backend deployment checklist

## Current status
The Android v0.2 application now supports Supabase email/password login,
server-determined roles, business-scoped project listing, and client access to
published progress updates. **No Supabase deployment or users are provisioned
automatically by this repository.** Without backend settings the app displays
a configuration message and does not use fabricated project data.

## Database setup
1. Create a dedicated Supabase project for SOCYTO with a strong database password.
2. Run `database/001_multi_tenant_foundation.sql` followed by
   `database/002_project_visibility_and_permissions.sql` in the SQL editor.
3. Review table grants and Row Level Security in a staging environment; add
   automated cross-tenant integration tests before giving access to real firms.
4. Provision the first Super Admin through a trusted server-side process using
   the appropriate `auth.users.id`; never let an Android client assign its own role.
5. Provision businesses, admins, clients and subscriptions using privileged server
   code. Do not expose Supabase service-role keys to Android or GitHub source.
6. Set GitHub repository **Settings → Secrets and variables → Actions → Variables**:
   - `SOCYTO_SUPABASE_URL` = project's HTTPS URL
   - `SOCYTO_SUPABASE_PUBLISHABLE_KEY` = project's public client key
7. Trigger **Android Debug APK** in the Actions tab and install the artifact.

## Role and privacy requirements
- Super Admin: read business list and project metadata; platform management writes
  will use privileged server-side endpoints.
- Business Admin: authenticated access only to matching business ID; can read own
  projects, receipts and private draft requirements under Row Level Security.
- Client: authenticated read-only access to linked projects and immutable published
  project snapshots; never private drafts, receipts, costs or other firms' clients.
- Public profile names, generated IDs, login credentials and passwords are NEVER
  included as sample real business data.

## Important unfinished work
- Cloud deployment, provisioning and subscription billing.
- Secure admin writes: clients/projects, requirement drafts, material receipts.
- Transactional publishing and published photo storage.
- Login refresh / secure durable session handling.
- Verify RLS with two business accounts and clients before live deployment.
- Screens for client assignments, inventory, expenditure and publication tools.
- End-to-end Android device testing.

The initial progress calculation and its tests remain in
`app/src/main/java/com/socyto/app/domain/Progress.kt` and
`app/src/test/java/com/socyto/app/domain/ProgressTest.kt`.

Do not treat a successful APK build as confirmation of backend security, data
persistence or production readiness.
