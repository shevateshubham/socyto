# SOCYTO — Hardcoded Behavior Audit

Date: 2026-11-23

## Scope examined
- Android entry point: `app/src/main/java/com/socyto/app/MainActivity.kt`
- Supabase API adapter: `app/src/main/java/com/socyto/app/data/SocytoApi.kt`
- Gradle build configuration: `app/build.gradle.kts`
- Repo README and progress helpers
- Read-only inspection of the connected production Supabase public table definitions and RLS policies

This is not an exhaustive repository-history or APK binary scan; other source files, distributed APKs and historical commits may still contain earlier test credentials or sample values.

## Replaced
1. Fixed Supabase endpoint and publishable-key fallbacks: now supplied through environment variables. Release check requires client configuration.
2. Fake tenant display name `Project workspace`: replaced with explicit missing-branding error; admin branding screen no longer special-cases the fake name.
3. Outdated prototype README: replaced with accurate backend and build information.
4. Over-completion of quantity tasks: now rejected by client validation. Pure hybrid progress helper and unit tests introduced in the same development branch.
5. Login error language referring to a test password: replaced with neutral authentication guidance.

## Legitimate constants retained
- Default SOCYTO colours and Material 3 theme tokens are design defaults, not customer records.
- Effort scale 1/2/3 follows product requirements.
- Authentication route segments, Supabase table names and required HTTP headers are API contracts.
- UI copy and accepted MIME types are static application resources, not fake functionality.

## Actual backend observations
- Supabase project is connected and active.
- Public project, requirement, receipt, client and publication tables have RLS enabled.
- Read-only policy inspection shows client access to projects and published snapshots through `is_project_client` checks; draft requirements and receipts use business-administrator-only policies.
- A Supabase security advisor flagged nine signed-in-callable SECURITY DEFINER functions. Source inspection found role checks in the state-changing functions examined, but this is not a substitute for authenticated adversarial/API tests. Review helper execute grants and disable leaked-password protection warning where supported.
- Existing quantity rows checked during this audit contained no invalid requirements; this does not guarantee future direct-API writes cannot exceed required quantity. Server-side database constraints still require deployment and validation.

## Outstanding checks before production release
- Inspect remaining repository paths, historical commits, CI variables and built APKs for stale credentials.
- Ensure CI sets the real `SOCYTO_SUPABASE_URL` and `SOCYTO_SUPABASE_PUBLISHABLE_KEY` values.
- Run Android unit tests and Gradle build; none was executed during this audit.
- Test user roles against direct Supabase REST APIs, not just UI visibility.
- Consider a database constraint enforcing quantity limits on direct writes.
- Do not remove existing authenticated users or records without first identifying genuine versus QA identities.

## Operational rule
Never claim that all hardcoded values have been removed until the entire repository, history, CI configuration and released artifacts have been checked.
