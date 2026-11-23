# SOCYTO Android

Native Android application for interior-design project management, using **Kotlin, Jetpack Compose, Material 3 and Supabase**.

## Actual application capabilities

- Email/password sign-up and sign-in through Supabase Auth.
- Platform administrator management of business accounts and branding.
- Business administrator projects, client onboarding and project management.
- Custom checklist and quantity-based requirements with relative effort scores.
- Server-side publication of effort-weighted project progress.
- Client read-only viewing of authorized projects and published updates.
- Project-specific received-material records and expenditure tracking.

These capabilities depend on the live backend, its RLS policies and an appropriately provisioned account. Their presence in source code does not by itself certify runtime behavior.

## Build configuration

Set `SOCYTO_SUPABASE_URL` and `SOCYTO_SUPABASE_PUBLISHABLE_KEY` in the build environment; see [docs/BUILD_CONFIGURATION.md](docs/BUILD_CONFIGURATION.md). No production endpoint or key is embedded as a source-code fallback.

Build using JDK 17 and Android Studio, or run:

```bash
./gradlew testDebugUnitTest assembleDebug
```

Release builds require valid backend client configuration; do not include any secret or service-role key in the APK.

## Development rules

See [AGENTS.md](AGENTS.md), [REQUIREMENTS.md](REQUIREMENTS.md), [DESIGN.md](DESIGN.md), [ARCHITECTURE.md](ARCHITECTURE.md) and [SECURITY.md](SECURITY.md).

Never ship dummy credentials, fake project responses, hardcoded client data or unapproved publication. Tests may use isolated fixtures. Never commit client records, API secrets, service-role keys, signing keys or invoices to this public repository.

## Verification status

The repository has implementation and unit-test sources, but runtime functionality, build results and backend access policies must be verified before calling a release production-ready. A Supabase security advisory can flag intentionally privileged RPCs: verify each function's caller checks and grants rather than blindly disabling needed business features.
