# Android UI Guidelines

## Scenario: Synapse Android Compose UI

### 1. Scope / Trigger

Trigger: changes under `android/app/src/main/java/com/synapse/mobile/ui/` that affect Compose screens, theme, visual hierarchy, buttons, cards, dialogs, scanner UI, crash report UI, credential display, or authentication state messaging.

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
android/app/src/main/java/com/synapse/mobile/ui/SynapseMobileTheme.kt
android/app/src/main/java/com/synapse/mobile/ui/SynapseMobileApp.kt
android/app/src/main/java/com/synapse/mobile/ui/TurnstileVerificationView.kt
android/app/src/main/java/com/synapse/mobile/ui/QrScannerView.kt
android/app/src/main/java/com/synapse/mobile/ui/CrashReportScreen.kt
```

Reusable local patterns:

```kotlin
SectionTitle(text = "...", subtitle = "...", icon = Icons.Outlined.Security)
ButtonLabel(Icons.AutoMirrored.Outlined.Login, "...")
StatusPill(icon = Icons.Outlined.Key, label = "SML", value = "已保存", active = true)
```

### 3. Contracts

- Use `SynapseMobileTheme` as the single source for app colors and shapes.
- Prefer Material 3 tonal surfaces (`surfaceContainer`, `primaryContainer`, `secondaryContainer`, `errorContainer`) over ad-hoc colors.
- Keep cards at 14-18 dp radius for screen-level panels and 10-12 dp radius for rows/pills.
- Buttons that perform clear actions should include an icon and short label via the local button-label pattern.
- Authentication, QR, session, scanner, and destructive-action states must use semantic icons and colors.
- Do not render full JWT, `clientLoginToken`, `scanToken`, Turnstile token, or password values. UI may show a token preview and may copy the full SML token only through an explicit copy action.
- Crash reports are shareable diagnostics: sanitize local paths, content/file URIs, bearer credentials, token/password query parameters, and common API-key formats in root causes, stack traces, fallback reports, and recent-event breadcrumbs before persistence or display.
- Keep panels constrained for larger screens and scrollable for mobile screens; text must use `maxLines` and `TextOverflow.Ellipsis` where long account/device/token values appear.
- Large summary/status regions must live inside the tab's scrollable content and switch to a compact form on low-height layouts such as landscape, so users can always scroll past them to the form/actions below.
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
| New auth or session action button | Add an icon and keep the label short enough for mobile. |
| New information/status card | Use tonal surface, consistent radius, and semantic icon. |
| New destructive action | Require a confirmation dialog with warning icon and explicit confirm text. |
| New credential/token display | Show availability or preview only; never render the full secret value. |
| Crash report or breadcrumb content | Apply the shared crash-report sanitizer before persistence, rendering, copying, or sharing; fallback report construction must use the same sanitizer. |
| Long account, device, URL, or token-adjacent text | Cap lines and use ellipsis. |
| Landscape or low-height screen | Keep header/status content scrollable and compact; do not pin a large banner above the scroll area. |
| New scanner/permission UI | Keep the permission rationale visible before launching permission request. |
| Dismissible status banner | Provide a close action when status/error is non-loading; clearing must not wipe form input. |
| Password field | Trailing visibility toggle; Done IME may submit only when login preconditions pass. |
| Confirm web QR login | Show account + target site confirmation before calling confirm API. |
| Expiry display | Format local datetime with remaining-time label; never show full secrets. |
| Need build/lint/test verification | Use GitHub Actions; do not run local Gradle commands. |

### 5. Good/Base/Bad Cases

Good: a login action uses a full-width Material button with `Icons.AutoMirrored.Outlined.Login`, a short Chinese label, and disabled state tied to `SynapseUiState`.

Base: an informational QR detail card uses `surfaceContainer`, a QR icon, and truncates long values.

Bad: a naked text-only button in a dense form, a raw `clientLoginToken` rendered in `Text`, or a destructive action running immediately without confirmation.

### 6. Tests Required

- GitHub Actions must run Android unit tests, lint, and release assemble for Android UI changes.
- Static review must confirm no raw JWT, `clientLoginToken`, `scanToken`, Turnstile token, or password value is rendered or logged.
- Unit tests must cover crash-report redaction for local paths, content/file URIs, bearer credentials, token/password query parameters, and supported API-key formats.
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
    val compactHeader = maxHeight < 520.dp
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        SynapseAppHeader(state = state, compact = compactHeader)
        StatusBanner(state = state)
        TabContent()
    }
}
```


