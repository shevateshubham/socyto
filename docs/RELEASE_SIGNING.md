# SOCYTO real signed-release setup

The release pipeline is .github/workflows/android-signed-release.yml. It builds the Android **release** variant, checks the real backend, signs with a persistent private release key, verifies the APK with Android SDK apksigner, and publishes only the signed APK and checksum as a GitHub Actions artifact.

## Initial one-time setup (repository owner)

1. Securely store the SOCYTO release signing keystore and its passwords offline. The key determines Android update compatibility. Never commit it, upload it as a public artifact, or include it in screenshots.
2. On GitHub, go to repository Settings -> Environments -> New environment, create `production` if absent.
3. In that environment, configure the following **secrets**:
   - `SOCYTO_RELEASE_KEYSTORE_BASE64`: base64 encoding of the persistent `.jks` / PKCS12 signing keystore
   - `SOCYTO_RELEASE_KEY_ALIAS`: alias of the release key
   - `SOCYTO_RELEASE_STORE_PASSWORD`: keystore password
   - `SOCYTO_RELEASE_KEY_PASSWORD`: private key password
4. In Settings -> Secrets and variables -> Actions -> Variables, set:
   - `SOCYTO_SUPABASE_URL`: production project HTTPS API URL
   - `SOCYTO_SUPABASE_PUBLISHABLE_KEY`: public `sb_publishable_` API key for that project
   These are **configuration variables**, not privileged server secrets. Never embed service-role or secret keys into the Android app.
5. From Actions -> SOCYTO Signed Release APK -> Run workflow, select the feature branch and run.
6. The resulting artifact `socyto-signed-release-apk` contains `SOCYTO-release.apk` plus its SHA-256 hash. The workflow verifies v2/v3 signatures via `apksigner verify`.

## Important deployment constraints
- A release APK is not enough to activate the new firm/client login flow; the relevant database migration and Supabase Edge Function also need to be deployed and tested.
- Test SMS/OTP provider setup, one-time password change, business scoping, and client publication access **before** onboarding real users.
- A release-signed APK and a debug-signed APK with the same application ID cannot be installed over one another. Android requires uninstalling the debug APK before the first release install; this clears locally stored app data.
- To update an existing **release** install without uninstalling, the next APK must use the **same signing key** and have a higher `versionCode`.
- This workflow never generates a new temporary signing key because doing so would strand installed users on an un-updatable certificate.
- Do not expose the signing keystore, password, or keystore base64 as a public GitHub file or build artifact.

## Current verification
The first release workflow correctly blocked itself on missing `SOCYTO_SUPABASE_URL`. Until required settings are supplied, no signed release APK can be produced.

## Signer continuity
Keep the generated certificate in trusted backups. Its public SHA-256 certificate fingerprint is the reference for signing future updates; record that fingerprint separately from private credentials.
