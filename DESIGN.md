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
