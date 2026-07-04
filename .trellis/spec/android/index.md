# Android Development Guidelines

> Conventions for the Android Kotlin client under `android/`.

## Pre-Development Checklist

- [ ] Android app code stays inside `android/`; do not move Gradle files to the repository root unless the Node/TypeScript project layout is intentionally redesigned.
- [ ] Read [`mobile-login-guidelines.md`](./mobile-login-guidelines.md) before changing authentication, QR login, credential storage, or Android CI.
- [ ] Do not run local Gradle build/test/install commands. Actual Android verification runs in GitHub Actions.
- [ ] No JWT, `clientLoginToken`, `scanToken`, password, or certificate pin secret is logged, displayed in full, or written to plaintext storage.

## Quality Check

- [ ] API paths and JSON fields still match `docs/android-mobile-login-integration.md`.
- [ ] Credential storage remains encrypted and private to the app.
- [ ] `synapse://mobile-login` parsing still rejects wrong scheme/host and non-HTTPS `apiBaseUrl`.
- [ ] GitHub Actions still runs unit tests, lint, release assemble, and uploads reports/APK artifacts.
- [ ] Unit tests cover pure parser/security helper behavior.

## Guidelines Index

| Guide | Description | Status |
|-------|-------------|--------|
| [Mobile Login Guidelines](./mobile-login-guidelines.md) | Android project structure, API/env contracts, credential storage, QR flow, CI verification | Filled |
