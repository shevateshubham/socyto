# SOCYTO Design System

## Brand and tone
Architectural, restrained, calm, legible, premium without excess decoration.
Provisional identity: abstract structural/interlocking-geometry monogram; final asset approval remains separate.

## Palette — directional, not final hex specifications
- Olive green: primary brand action and accent.
- Warm ivory: primary light surface.
- Charcoal: heading and body contrast.
- Restrained muted gold: selective detail, never the sole indicator of status.
Do not invent final hex codes; centralize tokens in the Compose theme until a finalized brand palette is supplied.

## Native Android UI
- Use Jetpack Compose and Material 3 components, typography and accessibility APIs.
- Support small phones, larger phones, tablets, portrait and landscape.
- Prefer responsive grids/panes over fixed pixel dimensions and clipped horizontal content.
- Minimum comfortable 48dp interactive hit targets; provide text scaling and accessible labels.
- Offer high-contrast foreground/background combinations, readable error messages, and non-color status cues.
- Ensure keyboard focus, touch and screen-reader usability.
- Use quiet, well-spaced surfaces and concise status labels. Avoid ornamental animations that obscure useful information.

## Main experiences
- Admin: project overview; editable custom requirements; binary/quantity completion; publication review; goods received; project expenditure.
- Client: assigned project; last published status; progress overview; published requirement details; clear updated-at indicator.
- Loading, empty, error, retry and no-access screens must be real states, not fictitious content.
- Never surface unapproved client edits or progress on client screens.

## Implementation
Compose theme tokens and reusable components are the design source of truth; add light/dark support without assuming exact brand color values. Review screenshots at phone and tablet sizes before calling the UI complete.

## 2026 responsive refresh — Sage & Linen (working palette)
The visual direction is quiet architectural editorial UI. This is the **new working default**, not a finalized logo/color approval. Existing firm-specific colors from the database still take precedence for tenant pages.

| Role | Hex | Treatment |
|---|---|---|
| Deep sage / primary | `#314A43` | Primary buttons and branded header |
| Muted eucalyptus / secondary | `#687F77` | Subheadings and supporting emphasis |
| Warm linen / background | `#F7F6F2` | Calm page canvas |
| Ink charcoal / text | `#26312F` | High-contrast labels and body |
| Stone beige / accent | `#B69F89` | Sparing accent only, never status by color alone |
| Pure white / cards | `#FFFFFF` | Legible form and content surfaces |

### Device compatibility contract
- Header and app content never overlap system status/navigation bars, camera cutouts or IME keyboard; use Compose WindowInsets/statusBarsPadding and navigationBarsPadding appropriately.
- Text is allowed to wrap; no forced one-line project/client names unless ellipsized and accessible in detail.
- At 320–360dp width, use single-column stacking; adapt at 600dp+ without assuming physical pixel density.
- Ensure 48dp minimum touch targets; all forms, error feedback and actions remain reachable after font scaling.
- Make creation, empty state, pending, published, awaiting activation and error states scannable with concise copy.
- Account onboarding emphasizes the next single action rather than ornate headers.
- Validate on screen captures *after* each layout change; automated tests must check real current labels, not retired UI copy.
