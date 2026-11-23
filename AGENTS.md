# SOCYTO — AI Development Instructions

## Mission
Build a reliable, production-oriented native Android application for SOCYTO interior-project execution, material receipts, expenditures, and client-visible status tracking.

## Source of truth
1. Existing working source code and tests; do not overwrite functionality blindly.
2. REQUIREMENTS.md for product decisions.
3. SECURITY.md for access and data boundaries.
4. ARCHITECTURE.md for implementation constraints.
5. DESIGN.md for UI styling and accessibility.
If anything conflicts, document it and request a decision instead of inventing behavior.

## Required engineering workflow
- Inspect affected code, database schema and existing tests before proposing changes.
- Make small, reviewable commits on a feature branch; do not write directly to main.
- Implement end-to-end functionality, including persistence, loading, empty, error and offline states where appropriate.
- Never misrepresent a prototype as functional. Do not invent test results, account access, API responses, database rows, credentials, build outputs or deployments.
- No production fake data, hardcoded login credentials, simulated completions or mock network responses. Explicitly named, isolated test fixtures are permissible in tests only.
- No secrets, private client documents, signing credentials or service-role keys in source code, logs, screenshots or APKs.
- Use configured environment/build configuration for public client settings; public/publishable keys are not privileged secrets.
- Check existing data and migrations before schema changes; protect data with database-side access control.
- Use stable project/task IDs and consistent units/currencies; avoid magic status strings and hardcoded project-specific categories.
- Create or update focused unit/UI/integration tests for each behavioral change; report exactly what was and wasn't run.
- Keep Android views adaptive to phone and tablet widths, portrait and landscape, and font scaling.
- After every feature, update affected product/technical docs and verify acceptance criteria.

## Project-specific guardrails
- SOCYTO has one internal architect/admin initially; clients have read-only access to only their assigned project data.
- Every project's requirement/task list is customizable, not a fixed room-wise checklist.
- Progress is an effort-weighted combination of binary and quantity-based task progress: see REQUIREMENTS.md.
- Client-facing status changes only on an explicit admin publish operation; editing draft tasks must not silently change client-visible status.
- Received goods drive actual project expenditure. No fixed allocated budget or current-on-site material tracking in v1.
- Never expose unpublished draft values, other clients' records, material cost internals, or internal notes unless explicitly authorized.

## Definition of done
Acceptance criteria implemented; security checks covered; tests/build run or limitations disclosed; no placeholders in production; docs updated; diff reviewed.

## Account onboarding workflow
- Follow .agents/skills/account-onboarding/SKILL.md for firm/client identity changes.
- Read docs/PHONE_ID_ONBOARDING.md before editing provisioning, password recovery or user-access screens.
- Never merge an account-flow change without confirming Auth configuration, compile/unit tests, per-role end-to-end checks and live RLS restrictions.

## Mandatory installed-skill and responsive-design rule (2026-11-23)
- Before changing SOCYTO, inspect the relevant **installed skills** and the repository's `.agents/skills/` guidance; explicitly apply those that fit the task. For backend use Supabase; for Android use android-compose, account-onboarding and the responsive-ui-audit skill.
- The video toolkit in `docs/codex-video-toolkit.md` is a resource catalog, not proof that every external package is installed. Review licences and security before adding dependencies; reuse our skills-first workflow as in Vardhiva.
- Treat screenshots and device behavior as acceptance evidence. On Android 15/16/17 reserve status and navigation bar insets; never draw app labels beneath the clock, notch, battery indicators or gesture area.
- Every major screen must be inspected at compact, standard, tablet, portrait, landscape, 1.5x+ font and keyboard-visible sizes. Check both visual rendering and accessible actionable controls.
- UI colors are centralized as tokens and user-firm branding remains data-driven. No new hardcoded sample projects, login credentials, fake records or decorative disabled controls.
- A passing Gradle compilation is insufficient to claim responsive UI validated: review emulator screenshots or device screenshots and report evidence/limitations.

## Unified identifier and zero-state tests
- Follow `.agents/skills/account-onboarding/SKILL.md` and `.agents/skills/responsive-ui-audit/SKILL.md` for login changes.
- Preserve one email/mobile/account-ID login field and separate verified bootstrap; never assign the first signup Super Admin automatically.
- Acceptance includes mobile/email input validation, confirmed phone OTP, wrong-password rejection, correct-account routing, empty database and safe first-login password changes.
