# SOCYTO onboarding UI polish

This is an Android Jetpack Compose implementation following DESIGN.md and the android-compose and account-onboarding repository skills. It draws on the video toolkit's Open Design and iterative Loop Stack approaches; third-party UI packages have **not** been installed.

## Updated experiences
- **Sign-in:** human-friendly login ID or administrator email, clearly separated initial Super Admin setup, integrated phone recovery, improved compact landscape behaviour.
- **Super Admin / add firm:** concise step page, legal/display name guidance, E.164 phone input with immediate validation, reason for mobile use, and one prominent creation action.
- **Firm / add client:** clear client-first/project-second flow, numeric phone keyboard and explicit recoverability explanation.
- **Credential handoff:** login ID prominent, password hidden by default, separate copy actions, a warning about once-only display and mandatory acknowledgement before dismissal.
- **Projects:** project creation stays separate from client provisioning; only explicitly published project updates are client-visible.

## Accessibility and privacy
- Native Material 3 surfaces, color-scheme-aware contrast, 48dp+ primary touch targets, functional UI states with no sample accounts.
- Copying a password to the system clipboard is a convenience with a security trade-off: clipboard contents may remain available to other apps or system UI. Consider Android's sensitive clipboard flag and a clear-before-exit approach in a later dedicated security review.
- Temporary passwords are not persisted by these screens; they remain transient in UI state until dismissed. Confirm lifecycle behaviour on a real device.

## Acceptance checks
- Compile and run debug unit tests for the feature branch.
- Verify small phone, normal phone, large text, landscape, and tablet layouts.
- Confirm the create action is disabled for invalid mobile format and while pending, and that error messages remain legible.
- Confirm that closing the handoff cannot happen before acknowledgement, and that password visibility defaults to hidden.
- Confirm firm/client IDs map correctly and that password changing and SMS recovery use the real configured backend.

## Status
Changes are committed to a draft PR, not deployed. Backend onboarding and SMS still require connected tests and rollout coordination with database migration 013.

## November 23 — five-device compatibility verification

The automated emulator run at https://github.com/shevateshubham/socyto/actions/runs/45150566352 completed **successfully** (all five jobs):
- compact 720×1280 at 320 dpi — passed
- standard 1080×2400 at 420 dpi — passed
- compact 720×1480 at 150% font scaling — passed
- tablet 1600×2560 at 320 dpi — passed
- landscape 1600×720 at 320 dpi — passed

Changes addressing the previous failures:
1. Corrected a real user-visible status-bar overlap by adding safe insets to the signed-in header.
2. Set appropriate dark system status icons on the warm-linen login background and light icons for the deep-sage signed-in header.
3. Stacked login content on short landscape screens to allow vertical scrolling.
4. Fixed screen-test swipe positions: `wm size` returns unrotated dimensions in landscape, so the test now reads actual rendered screenshot dimensions.
5. Retried captures obstructed by transient **Pixel Launcher** system ANR dialogs instead of mistaking an OS overlay for an app defect.

Reviewed compact screenshots for readable system bars and the final landscape screenshot for reachable setup and password recovery controls. Tests use an intentionally offline debug build to verify screen layout; this is not a connected end-to-end account-flow test. A separate SOCYTO 0.8.1 release build with public Supabase configuration was built and signed using the established SOCYTO release certificate, with Android APK Signature Scheme v2/v3 verification and signer continuity checked against 0.8.0.

Remaining before production customer rollout: authenticated role-boundary acceptance tests, SMS provider validation and live device testing.
