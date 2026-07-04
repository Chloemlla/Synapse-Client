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
ButtonLabel(Icons.Outlined.Login, "...")
StatusPill(icon = Icons.Outlined.Key, label = "SML", value = "已保存", active = true)
```

### 3. Contracts

- Use `SynapseMobileTheme` as the single source for app colors and shapes.
- Prefer Material 3 tonal surfaces (`surfaceContainer`, `primaryContainer`, `secondaryContainer`, `errorContainer`) over ad-hoc colors.
- Keep cards at 14-18 dp radius for screen-level panels and 10-12 dp radius for rows/pills.
- Buttons that perform clear actions should include an icon and short label via the local button-label pattern.
- Authentication, QR, session, scanner, and destructive-action states must use semantic icons and colors.
- Do not render full JWT, `clientLoginToken`, `scanToken`, Turnstile token, password, or certificate pin values. UI may show a token preview and may copy the full SML token only through an explicit copy action.
- Keep panels constrained for larger screens and scrollable for mobile screens; text must use `maxLines` and `TextOverflow.Ellipsis` where long account/device/token values appear.
- Do not add visible instructional text about internal design choices, keyboard shortcuts, or implementation details.

### 4. Validation & Error Matrix

| Condition | Required handling |
|-----------|-------------------|
| New auth or session action button | Add an icon and keep the label short enough for mobile. |
| New information/status card | Use tonal surface, consistent radius, and semantic icon. |
| New destructive action | Require a confirmation dialog with warning icon and explicit confirm text. |
| New credential/token display | Show availability or preview only; never render the full secret value. |
| Long account, device, URL, or token-adjacent text | Cap lines and use ellipsis. |
| New scanner/permission UI | Keep the permission rationale visible before launching permission request. |
| Need build/lint/test verification | Use GitHub Actions; do not run local Gradle commands. |

### 5. Good/Base/Bad Cases

Good: a login action uses a full-width Material button with `Icons.Outlined.Login`, a short Chinese label, and disabled state tied to `SynapseUiState`.

Base: an informational QR detail card uses `surfaceContainer`, a QR icon, and truncates long values.

Bad: a naked text-only button in a dense form, a raw `clientLoginToken` rendered in `Text`, or a destructive action running immediately without confirmation.

### 6. Tests Required

- GitHub Actions must run Android unit tests, lint, and release assemble for Android UI changes.
- Static review must confirm no raw JWT, `clientLoginToken`, `scanToken`, Turnstile token, password, or certificate pin value is rendered or logged.
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
```
