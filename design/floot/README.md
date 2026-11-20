# SOCYTO — Floot to native Android design handoff

Design origin: Floot project **SOCYTO — UI/UX Design Studio**
Project ID: `feb8f2b6-0a8f-4896-8663-f351b23196b9`

Floot source archived in this repository:
- `design/floot/SocytoStudio.tsx`: the interactive desktop/mobile design study
- `design/floot/SocytoStudio.module.css`: responsive component and screen layout
- `design/floot/theme-tokens.css`: palette, typography, surface, spacing and elevation tokens
- `design/floot/design-principles.md`: design rationale

These files are **design source**, not Android build inputs. Floot uses React/TypeScript; the production SOCYTO application uses Kotlin/Jetpack Compose. Do not embed a Floot iframe or publish an alternate data store to ship Android.

## Design translation into native Compose

| Floot pattern | Production native implementation |
| --- | --- |
| Architectural headline and geometry | `SocytoEditorialHeader`, `SocytoArchitecturalHero` |
| Project cards | `SocytoArchitectureProjectCard` |
| Role-specific primary navigation | `SocytoRoleNavigation` / Material 3 bottom navigation |
| Materials overview from received records | `SocytoMaterialsOverview` (live API) |
| Work progress, private drafts, publish confirmation | `ProjectPublishingPanel` |
| Super Admin tenant list and account activation | `MainActivity`, `RegisterBusinessForm` |
| Client-only publications | Existing authenticated `publications()` API and `SocytoUpdateCard` |
| Firm-specific appearance | `BusinessBranding` + MaterialTheme; logo and tenant colors loaded from Supabase |

### Styling
- Signature: architectural geometry, quiet olive, warm ivory, charcoal text, sage accent.
- Primary `#2E4A2B`, secondary `#6B8E49`, background `#F7F5F0`, gold `#D4AF7C`.
- Floot uses Newsreader / DM Sans. Native system serif headings and Compose typography offer an editable, dependency-light approximation; typography is not yet pixel-identical.
- Rounded architectural cards, thin borders, visually distinct surfaces and accessibility labels.

### Data and security
**No Floot design records are imported into the actual Android app or Supabase production.** Native project lists and expenditures always read the currently authenticated tenant's records; client portals show only explicit, published progress snapshots. Admin controls remain protected by database row-level security and privileged RPCs.

### Quality criteria
- Android CI: `:app:testDebugUnitTest`, `:app:assembleDebug`, and backend configuration checks must succeed.
- Android emulator matrix: compact phone, standard phone, 150% text scaling, tablet and landscape.
- Full end-to-end verification of real Super Admin, Business Admin and Client accounts remains a separate release gate; successful source compilation alone is not authorization of production use.
- Floot prototype has no production Supabase credentials and was typechecked, visually captured on a mobile viewport, and exercised through role changes.

The Floot archived source may use explicit illustrative design-only examples. Treat those as visual specifications, not fixed production data or authorized login credentials.
