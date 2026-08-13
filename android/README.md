# Synapse Mobile Android

Kotlin Android client for the Synapse mobile login flow described in `../docs/android-mobile-login-integration.md`.

## Scope

The app implements:

* standard identifier/password login against `/api/auth/login`;
* TOTP second-factor verification against `/api/totp/verify-token`;
* client login token issue/exchange/revoke;
* `synapse://mobile-login` deep-link parsing;
* QR scan and manual QR payload entry;
* web login scan marking and confirmation;
* encrypted local credential storage;
* HTTPS-only OkHttp client.

## UI illustrations

Empty states (first-run login, missing web-login credentials, empty session) use theme-bound undraw-style `ImageVector`s under `ui/svg/` (ported from Seal):

* `DynamicColorImageVectors.download()` / `videoFiles()` / `videoSteaming()` / `coder()`
* Path fills bind to `MaterialTheme.colorScheme` (e.g. `primaryContainer`, `surfaceContainerHigh`) so illustrations follow the app theme
* Session tab footer credits unDraw (Katerina Limpitsouni, unDraw License) and shows `VERSION_NAME` · `SHORT_HASH` · `BUILD_TIME`

## Lumen Crash SDK Integration

Crash reporting and startup ANR protection are provided by the Lumen Crash SDK:

* Dependency: `com.chloemlla.lumen:lumen-crash` (bundle — core collection plus the Compose crash-report UI). The version is never hardcoded: `.github/scripts/fetch-lumen-crash-sdk.py` resolves the latest non-draft `lumen-crash-v*` release in `Chloemlla/Project-Lumen` and stages it to `android/local-maven/`; Gradle resolves the version via the `lumenCrashVersion` gradle property → `LUMEN_CRASH_VERSION` env var → `android/lumen-crash.resolved.version`, in that order.
* Runtime integration: `SynapseApplication.attachBaseContext` installs the SDK first (idempotent, safe to call from both `attachBaseContext` and `onCreate`); `MainActivity` gates pending reports through `LumenCrashReportScreen`; `CrashBreadcrumbs.record` captures breadcrumbs; the ANR and startup-hang watchdogs run until the host calls `markStartupComplete()` after the first frame.
* Obfuscation: `app/proguard-rules.pro` ships complete Lumen Crash keep rules (including author-integrity exemptions), so release builds with minify/shrink enabled cold-start without white-screen.
* SDK docs: https://github.com/Chloemlla/Project-Lumen (lumen-crash README).

## Verification

Repository policy prohibits local build, test, install, or dependency installation commands. Android verification is defined in `.github/workflows/synapse-android.yml` and must run in GitHub Actions.
