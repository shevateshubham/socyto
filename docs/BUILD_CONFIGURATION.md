# SOCYTO Android build configuration

The app deliberately does not ship a hardcoded backend URL or publishable key. Both are project-specific settings that must be supplied by the build environment.

## Build environment
Provide these two environment variables to Android Studio, Gradle command-line builds, or your GitHub Actions workflow:

- `SOCYTO_SUPABASE_URL`: your actual HTTPS Supabase project URL.
- `SOCYTO_SUPABASE_PUBLISHABLE_KEY`: the matching public `sb_publishable_...` key.

These are public client configuration values, not privileged credentials. Do **not** inject any Supabase secret/service-role key into Android apps. Use GitHub Actions variables/secrets to configure CI and never commit privileged values.

Example command (substitute environment values locally before running):

```bash
./gradlew testDebugUnitTest assembleDebug
```

A debug build without configuration can still compile but reports the backend as unconfigured and cannot sign in. A release build must fail without valid supplied values. The release verifier checks the presence and form of the URL/key but does not establish backend ownership or validate RLS.

## Release checklist
1. Confirm production URL and publishable key are injected correctly.
2. Run `./gradlew testDebugUnitTest assembleRelease` and inspect CI logs.
3. Verify real logins, admin-only mutations and direct-API client isolation against the current deployed RLS.
4. Check published snapshot visibility and ensure unpublished draft changes cannot leak to clients.
5. Verify signing and store release settings separately.
