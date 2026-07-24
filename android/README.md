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

## Verification

Repository policy prohibits local build, test, install, or dependency installation commands. Android verification is defined in `.github/workflows/synapse-android.yml` and must run in GitHub Actions.
