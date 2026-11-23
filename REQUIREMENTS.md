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
