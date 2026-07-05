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

## Verification

Repository policy prohibits local build, test, install, or dependency installation commands. Android verification is defined in `.github/workflows/synapse-android.yml` and must run in GitHub Actions.
