# SOCYTO 0.8.8 - Release readiness

## Verified
- Android release compiled successfully: GitHub Actions run 45193636554.
- Kotlin unit tests and all five Android display profiles passed: run 45193636450 (compact, standard, 150% font, tablet and landscape).
- Signed release APK verified using Android v2/v3 schemes with same certificate as v0.8.7.
- The phone-provider-disabled registration error now gives an actionable explanation.
- Deployed fresh-owner bootstrap backend establishes the permanent owner lock only following an explicit request from a verified, preapproved user; no code or admin role is automatically granted.
- The service-only RPC is not executable by anonymous or ordinary authenticated roles; an invalid/unverified identity was rejected in an SQL test.
- The database still has zero users, owner locks, invitations, firms, clients, projects or uploads. One private owner-configuration row is retained as settings, not user data.

## External blockers (cannot be bypassed)
- Supabase Phone authentication must be enabled with a working SMS provider, including India sender requirements as applicable. Recent auth logs show phone_provider_disabled.
- Activation-code delivery requires a configured, trusted email and/or SMS provider. The Edge Function fails closed without this configuration.
- Supabase Auth Site URL must be changed from localhost:3000 to a verified reachable redirect destination for confirmation email links.
- After provider configuration, approved owner must enroll and verify their own contact before an activation invitation can be sent.
- Test real Primary Super Admin -> Firm -> Client -> Project -> Publication and Co-Admin invitation/revocation flows using user-created accounts. Deliberately do not create dummy credentials.
- PR #5 stays draft until authenticated end-to-end acceptance and provider checks satisfy project rules.

## Engineering guardrail
- Phone screen smoke and Kotlin unit tests now run separately to isolate occasional emulator Gradle dependency-transform instability.
- Continue to use installed Supabase, Android Compose, account-onboarding, and responsive-ui-audit skills.