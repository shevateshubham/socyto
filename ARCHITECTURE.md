# SOCYTO Technical Architecture

## Verified baseline
Repository is a native Android Gradle/Kotlin project using Jetpack Compose, Material 3 and JDK 17. Existing build files configure a Supabase URL and a publishable key, with current app release metadata. Inspect actual repository code before assuming a working production backend or login.

## Target boundaries (not a claim that all exist)
- UI: Compose screen/state components; no direct privileged writes in UI widgets.
- Domain: project requirements, effort-weighted progress, material-receipt expenditure, published snapshots.
- Data: repository interfaces; backend adapters for authentication, project assignment, tasks, receipts and published snapshots.
- Persistence: server source of truth for shared project state. Local caching only with clear synchronization/conflict rules.
- Authentication/authorization: backend-managed identity and enforced row-level access policies, not just client-side role hiding.

## Suggested logical entities
Project, ProjectMembership (user-to-project relation and role), Requirement (type, effort, required/completed quantities, completion), MaterialReceipt (project, amount, date, metadata), PublishedProjectSnapshot (version, published_at, approved summary/task states). These are design concepts; do not create tables until existing database schema is inspected.

## Progress contract
For each task i:
- binary task completion fraction c_i = 1 or 0;
- quantity task completion fraction c_i = clamp(completed_i / required_i, 0, 1), when required_i > 0;
- relative effort w_i is a positive task weight, default small=1, medium=2, large=3.
Overall progress percentage = 100 × sum(w_i × c_i) / sum(w_i).
For projects with no requirements, progress is undefined (show “No requirements yet”, not 100%). Reject missing/zero required quantity or invalid weight instead of silently dividing by zero. Round for display only, preserving exact computation for state changes. Do not add room-wise or fixed-list requirements.

## Publish model
Admin edits affect a draft state. A deliberate publish action validates and writes a coherent, versioned snapshot; clients read only the latest authorized published snapshot. Provide publication audit metadata and protect against partial publication. Do not treat a locally calculated preview as published status.

## Expenses
Track goods actually received against a project and sum eligible actual received-goods cost. Exclude planned purchases, unreceived orders and assigned budget from v1 actuals. Define reversals/returns and taxes explicitly before implementation.

## Delivery and validation
Use existing Gradle/build workflow and GitHub Actions where available. Verify build, unit tests, UI-state behavior and backend authorization with actual configured access. Record test status; do not claim unexecuted CI or database tests passed.
