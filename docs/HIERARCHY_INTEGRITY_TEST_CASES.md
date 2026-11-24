# SOCYTO hierarchy integrity — top-down and bottom-up acceptance tests

## Actual production roles
**Super Admin → Firm Admin → Client.** The Super Admin alone provisions a firm and
its Firm Admin temporary credentials. The Firm Admin alone provisions clients and
their projects. A client sees only assigned projects and explicitly published
requirements. Firm Admin and Client must change their one-time passwords before
accessing their workspaces.

**Primary owner restriction:** the permanent owner email/phone and locked owner
account cannot be replaced by a disposable 'dummy Super Admin'. This test starts
with the existing approved Super Admin's genuine authenticated login. It creates
only a disposable firm, Firm Admin, client and project **in the real production
environment**, using actual user-facing authenticated APIs rather than SQL seed data.

## Executable tests

Run manually with GitHub Actions → **SOCYTO Connected Hierarchy E2E** →
Run workflow; configure the verified owner credentials in encrypted
`SOCYTO_E2E_ADMIN_EMAIL` and `SOCYTO_E2E_ADMIN_PASSWORD` repo secrets first.
The workflow intentionally never triggers on a push. It never prints passwords,
session tokens or email verification links. It creates real records, **which are
not deleted automatically**; preserve the emitted `RECORD_IDS` for authorized
cleanup after reviewing the outcome.

| ID | Test | Expected |
|---|---|---|
| H01 | Sign in as approved Super Admin | Authentication succeeds; verified platform role present |
| H02 | Super Admin creates one firm | New firm and dedicated Firm Admin user created through edge function |
| H03 | Firm Admin temp credentials | One-time temp password returned to Super Admin, firm pending |
| H04 | Firm Admin temp sign-in | Login succeeds; password reset flag set |
| H05 | Firm Admin tries project access before reset | No project data visible |
| H06 | Firm Admin changes temp password | Password accepted and flag cleared |
| H07 | Firm Admin tries old temp password | Authentication fails |
| H08 | Firm Admin signs in with new password | Login succeeds |
| H09 | Pending firm | Firm Admin cannot access firm data |
| H10 | Super Admin activates firm | Status changes from pending to active |
| H11 | Active firm membership | Firm Admin can read own firm workspace |
| H12 | Firm Admin attempts another firm creation | Forbidden; cannot create or manage other firms |
| H13 | Firm Admin creates client | Client record scoped to correct firm |
| H14 | Firm Admin creates project | Project assigned to correct client and firm |
| H15 | Firm Admin adds checklist and quantity works | Task titles, weights, units and quantities persisted |
| H16 | Firm Admin updates drafts | Checklist complete; quantity partially complete |
| H17 | Firm Admin generates client temp login | One-time client credentials issued through client-onboarding |
| H18 | Client temp login | Password-reset flag set |
| H19 | Client reads project before resetting password | No projects accessible |
| H20 | Client changes password, retries old password, signs in new | New login works, old password rejected |
| H21 | Client sees assigned project before publication | Project visible; unpublished work snapshot absent |
| H22 | Client requests draft requirements | RLS returns no private work records |
| H23 | Firm Admin publishes requirements | Published, immutable server-side snapshot produced |
| H24 | Client receives exact published works | Names, checklist state and quantities match published snapshot |
| H25 | Overall project progress | Weighted result 62.5% for effort 1 completed plus effort 3 at 50% |
| H26 | Firm Admin changes draft without publishing | Client snapshot remains at previously published 50/100 and 62.5% |
| H27 | Client attempts to create a work requirement | API denies unauthorized write |
| H28 | Client requests private material receipts | No expense data visible |
| H29 | Client requests unrelated project | No project data visible |
| H30 | Android client project screen | Shows exact latest approved task names and completion state, never drafts |

## Current verification state (as of 24 November 2026)
- Database RLS policies and security functions inspected; their existence is **not**
  a substitute for real signed-in actor tests.
- Android client bug found: app was reading only published progress/notes/time,
  silently omitting published task contents. New change reads
  `project_publications.published_requirements` and displays the approved tasks.
- Connected E2E H01–H29 are **not run** until the approved owner's login secrets
  are supplied. H30 requires an Android emulator/device UI test to verify rendering.
- Project progress unit tests compile and run in Android CI; that does **not**
  establish E2E success.

## Cleanup
After validation, use the exact `RECORD_IDS` printed by the workflow and
approved Supabase administrative tools to remove only the test-created firm,
its clients/projects/requirements/publications, test firm and client auth users,
and their sessions. **Never delete the permanent Super Admin, its identity lock
or platform-owner configuration**. If the test fails mid-flow, retained IDs
still identify the partial records for cleanup. Rotate the temporary Super Admin
testing password after use. Do not commit passwords to code or logs.

## Test layers
1. Backend correctness: authenticated REST/Edge APIs and RLS, tracked in H01–H29
2. Android presentation: real login screen and read-only work visibility H30
3. Security isolation: add a second client/firm in a future explicit isolation run
   to prove cross-tenant segregation. A single-firm test cannot fully prove this.
