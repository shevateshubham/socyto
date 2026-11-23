---
name: socyto-account-onboarding
description: Build and verify SOCYTO three-role phone-linked account onboarding and project visibility.
---

# SOCYTO account onboarding

Use this reusable skill for authentication, IDs, phone recovery, permissions, and first-login credentials.

## Development loop
1. Inspect actual Compose, Edge Function, SQL migrations, SECURITY.md, and tests.
2. Verify third-party Auth and SMS capabilities before coding.
3. Make reviewable changes in a feature branch, following AGENTS.md.
4. Test with real distinct roles, and review database policies and REST access.
5. Verify first-password-change gates at database level, not only UI.
6. Inspect responsive Compose layout and accessibility.
7. Check CI, connected backend, diff, and security before merging.

## Requirements
- Only a verified Super Admin can provision F-prefixed firm logins.
- Firm admins can provision C-prefixed client logins inside their own active firm.
- Codes are not secrets or authorization keys; preserve UUID internal identities.
- Phone numbers must be in E.164 form; recovery must verify an SMS OTP.
- CSPRNG temporary passwords display once and are never committed or stored in business tables.
- Enforce first-login password change before protected data access.
- Never silently overwrite existing identities or passwords.
- Client account creation does not require a project.
- Clients can see only published progress from projects assigned to them.
- Cross-firm and cross-client access must be denied by the server.
- No fake credentials or claim of SMS delivery without a real provider.

## Video toolkit applied
Research Workflow for verifying auth; Make Your Own Skill for this instruction set; Loop Stack for iterative change and testing; Codebase Memory patterns for documentation; Open Design ideas for restrained adaptive UI. No external tool is a production dependency without review.

## Acceptance matrix
Super Admin creates firm; first-login gate works; firm signs in; firm creates client; client changes temporary password; firm creates project; draft hidden; published progress visible only to authorized client; other-client and other-firm requests fail; suspended firm denied; verified SMS OTP recovery works; invalid OTP rejected; failed provisioning leaves no working orphan account.

Do not call this complete until connected tests and SMS provider verification succeed.
