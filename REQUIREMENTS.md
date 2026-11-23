# SOCYTO Product Requirements — v1

## Users
- Single internal architect/admin initially manages projects and publishes updates.
- Each client has read-only access to projects explicitly assigned to that client; cross-project access is prohibited.
- Admin alone creates and edits tasks, materials, expenditure records and client-visible publication.

## Projects
- Create and manage distinct client projects.
- Each project has its own configurable requirement list. No prebuilt mandatory room-wise classification.
- Requirement descriptions and effort assignments vary by client/project.

## Hybrid progress
Every requirement supports one of:
1. Binary: complete (100%) or not complete (0%).
2. Quantity: completed quantity / required quantity, bounded to 0%–100%; preserve unit consistency.

Each task has an effort score: small=1, medium=2, large=3 initially, with positive effort weighting. Aggregate = 100 × Σ(effort × fraction complete)/Σ(effort), excluding no tasks. Calculate progress on current draft for admin; only explicitly published version appears to clients. No automatic client publication.

## Materials and actual expenditure
- Record project-wise goods received and their amounts.
- Auto-total actual expenditure to date based on recorded eligible received goods.
- No allocated capital/budget field in v1.
- No current quantity-on-site inventory calculation in v1.
- Allow admin to correct legitimate input errors with traceable updates.

## Client experience
- Authenticated client sees only assigned projects and explicitly published status.
- Show published percent and relevant task progress clearly, with publication timestamp.
- Clients cannot edit requirements, costs or statuses.

## Acceptance scenarios
- Binary and quantity progress both produce correct weighted overall percentage.
- Changing a task without publishing does not change what the client sees.
- Publishing a coherent update changes only that project's authorized client view.
- A client cannot read another client's project by changing request IDs.
- Received materials update the project's actual expenditure; planned/unreceived items do not.
- Empty projects, invalid quantities, connectivity failures and unauthorized access produce honest UI states.

## Out of scope unless separately approved
Payments, supplier procurement workflow, detailed stock-on-site, budget allocation, room-wise predefined progress, client editing, public project visibility.

## Firm and client account onboarding (new)
- Platform Super Admin is provisioned once through verified email and a trusted one-time bootstrap invitation.
- Only Super Admin provisions firm accounts from a firm name and registered E.164 phone number; each firm receives a random F-prefixed login code and a once-visible temporary password.
- Each firm has its own business administrator and data scope; firm administrators provision clients from client name and phone and receive a C-prefixed login code and once-visible password.
- Firm and client credentials must be changed at first sign-in before protected project data access. No shared/dummy/password-in-source accounts.
- Forgotten passwords are recovered by a real SMS verification challenge to the registered mobile number, then a new user-chosen password; an SMS provider must be configured.
- Creating a client does not require a project. A firm administrator may then add one or more projects, edit private drafts and publish only intended progress updates.
- Codes are not database keys or authorization capabilities; server-verified Auth identity, tenant membership and RLS decide access.

## Unified authentication identifiers (Nov 23, 2026)
- Sign-in presents one field accepting verified email, E.164 mobile number or a generated F/C account ID for all roles.
- First-time Super Admin registration presents one field for verified email OR verified E.164 mobile. Email requires email confirmation; phone requires SMS OTP. A one-time admin bootstrap invitation remains mandatory for role elevation.
- Managed firm/client account IDs and phone numbers remain usable as login handles with a password; no phone number alone grants access. Email login is available for accounts with an actual registered email.
- Existing phone-only firm/client contact collection remains unchanged until optional email contacts and recovery can be implemented with delivery verification.
- The account-login Edge Function resolves managed phone logins server-side and validates passwords using Supabase Auth, with generic invalid-credential failures and no service-role key in the APK.
- Every reset to zero includes user identities, sessions, firms, clients, projects, invitations and administrators. Preserve schema and migrations.
