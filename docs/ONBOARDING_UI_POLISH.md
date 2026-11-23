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
