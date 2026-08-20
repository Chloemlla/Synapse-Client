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

Crash reporting and startup ANR protection are provided by the Lumen Crash SDK (`com.chloemlla.lumen:lumen-crash` — core collection plus the Compose crash-report UI).

* **Version is auto-resolved, never hard-pinned.** `.github/scripts/fetch-lumen-crash-sdk.py` picks the newest non-draft `lumen-crash-v*` release in `Chloemlla/Project-Lumen`, stages the AAR/POM into `android/local-maven/`, and writes `android/lumen-crash.resolved.version`. Gradle resolves in this order: `lumenCrashVersion` gradle property → `LUMEN_CRASH_VERSION` env → that file. `settings.gradle.kts` registers the GitHub Packages repo only when `gpr.user`/`gpr.key` (or `GITHUB_ACTOR`/`GITHUB_TOKEN`) are non-blank — empty credentials make GitHub Packages answer 401 and abort resolution even though the locally staged AAR would satisfy it.
* **A Compose BOM is mandatory.** The SDK publishes Compose UI / Material3 / material-icons-extended / material3-window-size-class as **unversioned** `api` dependencies, so the BOM must be applied before `lumen-crash` — otherwise Gradle fails with empty-version coordinates such as `androidx.compose.ui:ui:.`.
* **Host touchpoints.** `SynapseApplication` installs the SDK first thing after `super.attachBaseContext` (install is idempotent, so `onCreate` may call it again); `MainActivity` gates pending reports through `LumenCrashReportScreen` before app UI and calls `markStartupComplete()` after the first frame, which is what stops the ANR/startup-hang watchdogs; `CrashBreadcrumbs.record` marks startup paths.
* **Release minify/shrink.** The AAR's merged `consumer-rules.pro` plus the explicit backup keep block in `app/proguard-rules.pro` (including the author-integrity exemptions) keep release cold start off the white screen; `app/src/main/res/raw/keep.xml` pins `@string/lumen_crash_*` and `@plurals/lumen_crash_*` because `isShrinkResources = true`.
* **File share** uses the SDK-owned authority `${applicationId}.lumen.crash.fileprovider`, merged from the AAR; the host no longer declares a FileProvider of its own for this.
* **Report upload.** The SDK's built-in uploader posts to `crashReportBackendBaseUrl` (this app passes its own `BuildConfig.SYNAPSE_API_BASE_URL`) and correlates reports through `deviceInstallationIdProvider`.
* SDK docs: https://github.com/Chloemlla/Project-Lumen (lumen-crash README).

## Verification

Repository policy prohibits local build, test, install, or dependency installation commands. Android verification is defined in `.github/workflows/synapse-android.yml` and must run in GitHub Actions.
