# PRD: Android mobile page spacing auto-adapt

## Goal

Make Synapse Mobile Compose pages breathe better on phones. Current outer padding (8dp) and inter-section gaps (4–6dp) feel cramped after prior “tighten” work. Spacing must auto-adapt to available viewport height/width while keeping low-height/landscape layouts usable via scroll + compact header.

## Scope

- Primary: `android/app/src/main/java/com/chloemlla/synapse/mobile/ui/SynapseMobileApp.kt`
  - `PanelColumn` page padding + section spacing
  - `SynapseAppHeader` compact metrics
  - Related shared pieces that inherit panel spacing (`StatusBanner` bottom gap, empty-state card density where it crowds short screens)
- Secondary only if needed for consistency: `TurnstileVerificationView.kt`, `QrScannerView.kt`
- Spec: update `.trellis/spec/android/ui-guidelines.md` with adaptive spacing contract after implementation

## Out of scope

- Local Gradle build/lint/test (forbidden; CI only)
- Package rename / auth logic / new features
- Changing copy, colors, or visual hierarchy beyond spacing/density

## Requirements

1. **Not cramped on phones**: default portrait phone must use clearly larger page padding and section gaps than today’s 8 / 4–6 dp.
2. **Auto-adapt**: derive spacing from `BoxWithConstraints` (`maxHeight` / `maxWidth`), not a single fixed density.
3. **Keep compact header on low height**: landscape / short viewports still collapse header pills; compact ≠ zero breathing room.
4. **Scroll remains the overflow strategy**: never pin large non-scrollable chrome that hides form actions.
5. **Large screens**: keep `widthIn(max = 760.dp)` centering; generous but not sparse spacing.
6. **Empty-state illustrations**: on short height, shrink illustration footprint so form fields remain reachable without endless scroll.

## Suggested metrics (implementer may refine slightly)

| Viewport | pagePadding | sectionSpacing | compactHeader |
|----------|-------------|----------------|---------------|
| height < 640 dp (short / landscape) | 12 dp | 10 dp | true |
| height < 800 dp (typical phone) | 14–16 dp | 12 dp | width < 400 or height < 720 |
| else (tall phone / tablet width) | 16–20 dp | 14–16 dp | false |

Header internal: compact outer ≥ 10–12 dp, vertical ≥ 6–8 dp; non-compact outer ≥ 12–14 dp, vertical ≥ 8–10 dp.

## Acceptance

- [ ] Phone portrait sections no longer look glued together (visually ≥ ~10–12 dp between major blocks).
- [ ] Short/landscape still uses compact header and full vertical scroll.
- [ ] No secrets rendered; no auth behavior changes.
- [ ] UI guidelines document the adaptive spacing rule.
- [ ] CI (not local Gradle) remains the verification path.
