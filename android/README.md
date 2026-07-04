# Synapse Mobile Android

Kotlin Android client for the Synapse mobile login flow described in `../docs/android-mobile-login-integration.md`.

## Scope

The app implements:

* standard username/password login against `/api/auth/login`;
* client login token issue/exchange/revoke;
* `synapse://mobile-login` deep-link parsing;
* QR scan and manual QR payload entry;
* web login scan marking and confirmation;
* encrypted local credential storage;
* HTTPS-only OkHttp with optional certificate pinning.

## Verification

Repository policy prohibits local build, test, install, or dependency installation commands. Android verification is defined in `.github/workflows/synapse-android.yml` and must run in GitHub Actions.
