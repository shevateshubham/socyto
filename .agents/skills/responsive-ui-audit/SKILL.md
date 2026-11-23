---
name: responsive-ui-audit
description: Apply installed Android Compose and design skills, audit SOCYTO across screen sizes, inset safety, large type, and role-based workflows before shipping any APK.
---
# SOCYTO Responsive UI Audit

## Every UI task
1. Read AGENTS.md, DESIGN.md, installed relevant skills, and .agents/skills/android-compose/SKILL.md.
2. Look at any screenshot supplied by the user. Identify actual overlaps/clipping and the affected composable.
3. Make responsive Compose changes using WindowInsets, heightIn, adaptive widths and scroll/IME handling. Avoid fixed-position text or arbitrary pixel offsets.
4. Honor tenant branding but retain SOCYTO's neutral sage-and-linen defaults.
5. Add or update screenshot smoke tests for compact phone, standard phone, tablet, landscape and enlarged text (1.5x).
6. Verify system status/navigation regions stay clear, login inputs are reachable, and buttons remain usable with keyboard open.
7. Run Gradle unit tests, emulator UI tests and inspect screenshots. If real-device evidence is unavailable, report that limitation.
8. Only after evidence, generate and sign a release APK. Preserve the signing key for upgrade compatibility.

## Security & real-data guardrails
No demo credentials, fake data, simulated progress or secret keys in source. Clients see only explicitly published project progress. Changes to backend or onboarding use the Supabase and account-onboarding skills, not design shortcuts.

## Release checklist
- No text beneath system icons or camera cutouts
- No clipped buttons or navigation overlap
- Compact and enlarged-text scrolling works
- Proper contrast/semantics/touch targets
- All roles retain access, publication and privacy behaviors
- Tests and screenshots reviewed; release notes accurately distinguish what was actually tested
