# Android UI Guidelines

## Scenario: Synapse Android Compose UI

### 1. Scope / Trigger

Trigger: changes under `android/app/src/main/java/com/chloemlla/synapse/mobile/ui/` that affect Compose screens, theme, visual hierarchy, buttons, cards, dialogs, scanner UI, crash report UI, credential display, or authentication state messaging.

Local Gradle build, lint, test, install, and assemble commands remain prohibited. Actual Android verification must run in GitHub Actions.

### 2. Signatures

Theme entry point:

```kotlin
SynapseMobileTheme {
    SynapseMobileApp(viewModel)
}
```

Primary UI files:

```text
android/app/src/main/java/com/chloemlla/synapse/mobile/ui/SynapseMobileTheme.kt
android/app/src/main/java/com/chloemlla/synapse/mobile/ui/SynapseMobileApp.kt
android/app/src/main/java/com/chloemlla/synapse/mobile/ui/TurnstileVerificationView.kt
android/app/src/main/java/com/chloemlla/synapse/mobile/ui/QrScannerView.kt
com.chloemlla.lumen.crash.ui.LumenCrashReportScreen (GitHub Packages: com.chloemlla.lumen:lumen-crash)
```

Reusable local patterns:

```kotlin
SectionTitle(text = "...", subtitle = "...", icon = Icons.Outlined.Security)
SectionCard(title = "...", subtitle = "...", icon = ..., emphasized = true) { /* grouped controls */ }
ButtonLabel(Icons.AutoMirrored.Outlined.Login, "...")
StatusPill(icon = Icons.Outlined.Key, label = "SML", value = "已保存", active = true)
PanelColumn(state, onDismissFeedback) { /* tab body */ } // derives PanelSpacing from BoxWithConstraints
```

### 3. Contracts

- Use `SynapseMobileTheme` as the single source for app colors and shapes. Theme must provide matching light + dark schemes via `isSystemInDarkTheme()`; keep the blue/teal brand family and lift cards from the page background with tonal surface layers (no ad-hoc `Color.White` card fills).
- Prefer Material 3 tonal surfaces (`surface`, `surfaceContainer*`, `primaryContainer`, `secondaryContainer`, `errorContainer`) over ad-hoc colors. Prefer `MaterialTheme.shapes` (large ≈ 14 dp panels, medium ≈ 10–12 dp rows/pills) over scattered one-off radii.
- **Section grouping**: multi-step tab panels (Login / QR / Session) wrap related controls in `SectionCard` rather than one flat field list. Typical Login groups: primary password path (+ in-flow 2FA) as `emphasized = true`; passwordless/provider sections (Passkey / Google / Linux.do) as `secondary = true` with outlined CTAs; advanced JWT as `secondary = true`. QR stages: scan → payload/details → confirm. Session: primary session actions vs destructive clear (`secondary`). Nested `InfoCard` / Turnstile cards use a higher tonal layer (`surfaceContainerHigh` / `surfaceContainer`) so they lift inside parent section cards.
- **Action hierarchy**: filled `Button` only for the main path of a stage (password login, QR scan open, QR confirm, silent login, in-flow 2FA complete); `OutlinedButton` / `TextButton` for secondary providers and alternate actions; destructive stays outlined + confirmation dialog. Prefer shared roomier content padding (`SynapseButtonContentPadding`, vertical ~12 dp) on primary/secondary action buttons.
- **App chrome**: top app bar + tab row share one surface color; optional thin divider under tabs; selected tab uses primary content color + indicator (no heavy double borders).
- **Header / status polish**: active `StatusPill` uses `primaryContainer`; inactive uses quieter surfaceVariant emphasis. StatusBanner stays dismissible with softer success and clearer error hierarchy.
- Keep cards at 14-18 dp radius for screen-level panels and 10-12 dp radius for rows/pills.
- Buttons that perform clear actions should include an icon and short label via the local button-label pattern.
- Authentication, QR, session, scanner, and destructive-action states must use semantic icons and colors.
- Do not render full JWT, `clientLoginToken`, `scanToken`, Turnstile token, or password values. UI may show a token preview and may copy the full SML token only through an explicit copy action.
- Crash reporting is provided by the published `com.chloemlla.lumen:lumen-crash` SDK from GitHub Packages. Host product copy may override title/message/share subject via `LumenCrashConfig`; do not reintroduce app-local crash core/UI under `core/crash` or `ui/CrashReportScreen`. Sanitization of paths, content/file URIs, bearer credentials, token/password query parameters, and API-key formats is owned by the SDK before persistence or display.
- Keep panels constrained for larger screens and scrollable for mobile screens; text must use `maxLines` and `TextOverflow.Ellipsis` where long account/device/token values appear.
- Large summary/status regions must live inside the tab's scrollable content and switch to a compact form on low-height layouts such as landscape, so users can always scroll past them to the form/actions below.
- **Adaptive page + component spacing**: derive metrics from `PanelColumn`'s `BoxWithConstraints` (`maxHeight` / `maxWidth`) via `PanelSpacing` / `LocalPanelSpacing`, not a single fixed density. Page metrics space major tab siblings; component tokens space content *inside* cards, rows, and local action groups so form fields that already inherit `sectionSpacing` are not double-padded. Major tab blocks should not look glued together on portrait phones (target ≥ ~10–12 dp between sections). Suggested tiers:

  | Viewport | pagePadding | sectionSpacing | itemSpacing | cardPadding | rowPad (H/V) | tightText | compactHeader |
  |----------|-------------|----------------|-------------|-------------|--------------|-----------|---------------|
  | height < 640 dp (short / landscape) | 12 dp | 10 dp | 8 dp | 12 dp | 12 / 8 dp | 2 dp | true |
  | height < 800 dp (typical phone) | 16 dp | 12 dp | 10 dp | 14 dp | 14 / 10 dp | 3 dp | width < 400 dp or height < 720 dp |
  | else (tall phone / tablet width) | 18 dp | 14 dp | 12 dp | 16 dp | 14 / 12 dp | 4 dp | false |

  Component token roles:
  - `itemSpacing` — stacked actions / related controls inside a local group (QR/session button stacks, card internal stacks).
  - `cardPadding` — CredentialSummary, InfoCard, SectionCard, empty-state, credits, Turnstile cards.
  - `rowPaddingHorizontal` / `rowPaddingVertical` — CopyableLine, account selector rows, status banners (pills may be ~2 dp denser).
  - `tightTextSpacing` — title/subtitle stacks (header, SectionTitle, label stacks).

  Header internal: compact outer ≥ 12 dp, vertical ≥ 6 dp; non-compact outer ≥ 14 dp, vertical ≥ 8 dp. Status banners must not add a fixed extra bottom pad that fights `sectionSpacing`. Empty-state / credits illustrations shrink on short or compact viewports so form actions stay reachable without endless scroll. Keep `widthIn(max = 760.dp)` centering on large screens.
- Do not add visible instructional text about internal design choices, keyboard shortcuts, or implementation details.
- Status and error banners must be dismissible when not loading; call a ViewModel clear-feedback action rather than leaving stale banners permanently visible. Failure banners may be multi-line: keep top alignment for error details and soft-wrap the full diagnostic text from `SynapseFailureMessage` / API error formatter.
- Password fields must support show/hide via a trailing visibility icon; never log or mirror the password value elsewhere.
- Destructive or high-impact actions (revoke token, clear credentials, confirm web QR login) require an explicit confirmation dialog that names the account and target site when applicable.
- Token and challenge expiry timestamps shown in UI should use a local friendly format plus remaining-time bucket (minutes/hours/days), not raw ISO-8601 only.
- Empty states for first-run login, missing web-login credentials, and empty session should explain the next action without exposing secrets.
- After successful client authorization (password login, TOTP, Passkey, or JWT issue), clear sensitive form fields (password / JWT paste) and switch to the Session tab so the user lands on usable next steps.

### 4. Validation & Error Matrix

| Condition | Required handling |
|-----------|-------------------|
| New auth or session action button | Add an icon and keep the label short enough for mobile. Prefer filled primary vs outlined secondary hierarchy. |
| Multi-step panel controls (Login/QR/Session) | Group related fields/actions in `SectionCard`; do not leave one flat ungrouped list. |
| Theme / surface change | Provide light + dark schemes; cards must lift from background via tonal surfaces, not flat white. |
| New information/status card | Use tonal surface, consistent theme shape radius, and semantic icon. |
| New destructive action | Require a confirmation dialog with warning icon and explicit confirm text; keep outlined, secondary visually. |
| New credential/token display | Show availability or preview only; never render the full secret value. |
| Crash report or breadcrumb content | Use `LumenCrash.recordBreadcrumb` / `LumenCrash.record`; rely on the SDK sanitizer and do not fork app-local crash report builders. |
| Long account, device, URL, or token-adjacent text | Cap lines and use ellipsis. |
| Landscape or low-height screen | Keep header/status content scrollable and compact; do not pin a large banner above the scroll area. Compact still keeps ≥ ~10–12 dp page padding / section gaps. |
| New or revised tab panel spacing | Drive page + component gaps from viewport via `PanelColumn` / `PanelSpacing` / `LocalPanelSpacing`; do not hardcode 8 / 4–6 dp page density or fixed 8 dp action stacks / 12 dp card padding. |
| Empty-state illustration on short height | Shrink illustration footprint (width fraction / max height) so fields remain reachable. |
| New scanner/permission UI | Keep the permission rationale visible before launching permission request. |
| Dismissible status banner | Provide a close action when status/error is non-loading; clearing must not wipe form input. |
| Password field | Trailing visibility toggle; Done IME may submit only when login preconditions pass. |
| Confirm web QR login | Show account + target site confirmation before calling confirm API. |
| Expiry display | Format local datetime with remaining-time label; never show full secrets. |
| Need build/lint/test verification | Use GitHub Actions; do not run local Gradle commands. |

### 5. Good/Base/Bad Cases

Good: a login action uses a full-width filled Material button with `Icons.AutoMirrored.Outlined.Login`, a short Chinese label, disabled state tied to `SynapseUiState`, and lives inside an emphasized `SectionCard` with related fields.

Base: an informational QR detail card uses `surfaceContainer`, a QR icon, and truncates long values; QR scan/payload/confirm sit in staged section cards.

Bad: a naked text-only button in a dense form, a raw `clientLoginToken` rendered in `Text`, a destructive action running immediately without confirmation, light-only theme with flat white cards, or an ungrouped login field list with no primary/secondary action distinction.

### 6. Tests Required

- GitHub Actions must run Android unit tests, lint, and release assemble for Android UI changes.
- Static review must confirm no raw JWT, `clientLoginToken`, `scanToken`, Turnstile token, or password value is rendered or logged.
- Crash SDK integrity/export coverage is owned by the published `lumen-crash` package release pipeline; host unit tests remain focused on auth/migration pure helpers.
- Static review must confirm new destructive actions have confirmation.
- For ViewModel-driven behavior changes, add/update unit tests for pure state or parser behavior where feasible.

### 7. Wrong vs Correct

#### Wrong

```kotlin
Text(credentials.clientLoginToken.orEmpty())
Button(onClick = viewModel.clearCredentials) {
    Text("清理")
}
Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
    Text("状态")
}
```

#### Correct

```kotlin
CopyableLine(
    label = "SML 登录令牌",
    value = active.clientLoginTokenPreview ?: "未保存",
    copyValue = active.clientLoginToken.orEmpty(),
)

OutlinedButton(onClick = { showClearConfirmation = true }) {
    ButtonLabel(Icons.Outlined.DeleteOutline, "清理本地凭据")
}

InfoCard(
    title = "网页登录二维码详情",
    icon = Icons.Outlined.QrCodeScanner,
    lines = detailLines,
)

BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val spacing = panelSpacingFor(maxWidth = maxWidth, maxHeight = maxHeight)
    CompositionLocalProvider(LocalPanelSpacing provides spacing) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(spacing.pagePadding),
            verticalArrangement = Arrangement.spacedBy(spacing.sectionSpacing),
        ) {
            SynapseAppHeader(state = state, compact = spacing.compactHeader)
            StatusBanner(state = state)
            // Cards/rows/action groups use spacing.cardPadding / itemSpacing / rowPadding*
            TabContent()
        }
    }
}
```


