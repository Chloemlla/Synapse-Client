# PRD: Android UI layout visual polish

## Goal

Beautify Synapse Mobile Compose layout beyond spacing: clearer visual hierarchy, grouped sections, refined chrome (top bar / tabs / header), and more polished cards/actions — while keeping adaptive spacing, scroll-on-short-viewport, and secret-safe UI contracts.

## Scope

- `SynapseMobileTheme.kt` — light + dark color schemes; keep Material 3 tonal surfaces; optional shapes polish
- `SynapseMobileApp.kt` — scaffold chrome, section grouping, header/card/action visual hierarchy
- `TurnstileVerificationView.kt` / `QrScannerView.kt` only if needed for consistency
- `.trellis/spec/android/ui-guidelines.md` — document layout polish patterns after implementation

## Out of scope

- Auth/API/ViewModel behavior changes
- Local Gradle
- Package rename / new login providers
- Brand-new navigation architecture (keep 3 tabs)

## Design direction (MVP polish)

1. **Theme depth**
   - Add matching dark color scheme (`isSystemInDarkTheme()`).
   - Keep existing blue/teal brand family; refine surface layers so cards lift from background.
   - Prefer theme shapes on cards/buttons over scattered one-off radii where practical.

2. **App chrome**
   - Top app bar + tabs read as one surface (subtle divider or shared surface, no heavy double borders).
   - Tabs keep icons + labels; selected state clearer (primary content color + indicator).

3. **Section grouping (Login / QR / Session)**
   - Wrap related controls into tonal section cards, e.g. Login:
     - Primary: username / password / device / Turnstile / primary login button
     - Secondary: Google / Linux.do / Passkey (existing sections, calmer containers)
     - Advanced: JWT paste path
   - QR: scan actions + payload + confirm as clearer stages
   - Session: primary actions vs destructive clear separated visually

4. **Header / status**
   - Keep compact adaptive header; polish avatar + pills (active pill stronger, inactive quieter).
   - StatusBanner remains dismissible; slightly softer success / clearer error hierarchy.

5. **Actions**
   - Primary filled buttons for main path; outlined/text for secondary.
   - Destructive remains outlined + confirmation (no behavior change).
   - Consistent button height feel via content padding where needed.

6. **Preserve**
   - `PanelSpacing` adaptive page + component tokens
   - Scrollable `PanelColumn`, `widthIn(max = 760.dp)`
   - No full secrets in UI
   - Empty-state undraw illustrations + dynamic color

## Acceptance

- [ ] Dark mode available and readable
- [ ] Login/QR/Session show clearer section grouping (not one flat field list)
- [ ] Primary vs secondary actions visually distinct
- [ ] Compact short viewport still scrolls; no pinned chrome blocking forms
- [ ] Spec updated with layout polish conventions
- [ ] No auth logic regressions; no local Gradle
