# Implement Android Kotlin App For Synapse Mobile Login

## Goal

Create a complete Android Kotlin project under the current `Synapse-Client` repository that implements the mobile login behavior specified by `docs/android-mobile-login-integration.md`. The app should be a working Android project skeleton with Gradle Kotlin DSL, dependencies, UI, API client, secure credential storage, QR/deep-link confirmation flow, and GitHub Actions workflow.

## Requirements

* Create a standalone Android project in `android/` without converting the existing Node/TypeScript repository root into an Android project.
* Implement the Synapse mobile login contract from `docs/android-mobile-login-integration.md`:
  * Standard login against existing `/api/auth/login` endpoint, with a placeholder for TOTP/Passkey follow-up when `requires2FA` is returned.
  * Issue `sml_` client login tokens after standard JWT login.
  * Exchange client login tokens for JWT during silent login.
  * Parse `synapse://mobile-login?...` QR/deep-link payloads.
  * Mark QR challenges as scanned.
  * Confirm web QR login using current JWT first, falling back to the client login token.
  * Revoke the stored client login token.
* Use Kotlin only for app source; no Java or Flutter implementation.
* Use Project-Lumen-inspired Android patterns:
  * Gradle Kotlin DSL.
  * Jetpack Compose UI.
  * OkHttp + coroutines + `org.json`.
  * AndroidX Security Crypto + encrypted MMKV-style storage for credentials.
  * Stable local device ID.
  * HTTPS-only network client and optional certificate pinning.
  * GitHub Actions workflow that runs Android unit tests, lint, and release assemble in CI.
* Do not run local build, test, install, or dependency installation commands. Actual validation must happen in GitHub Actions.

## Acceptance Criteria

* [ ] `android/` contains a complete Android Kotlin app project with root/app Gradle files, manifest, Kotlin sources, resources, and ProGuard rules.
* [ ] `.github/workflows/synapse-android.yml` builds and verifies the Android project from `android/`.
* [ ] App UI covers login, saved session status, silent login, QR payload parsing, scanned marking, confirmation, and revoke actions.
* [ ] API client paths and JSON fields match `docs/android-mobile-login-integration.md`.
* [ ] Credentials are stored in encrypted private storage and are never logged.
* [ ] Local verification is limited to static file inspection.

## Definition of Done

* Android project files are added.
* Static inspection confirms expected files and API path coverage.
* No local build/test/install commands are run.
* Changes are committed after modification per repository instructions.

## Technical Approach

Build a self-contained `android/` Gradle project. Keep implementation practical and dependency-light, using OkHttp directly rather than Retrofit to match Project-Lumen's local style. Use Compose state in a ViewModel-like controller for UI actions, and keep network/storage concerns in repository classes under `core/auth`.

## Decision (ADR-lite)

**Context**: Synapse-Client is a TypeScript full-stack repository, but the requested output is an Android Kotlin application using the existing mobile-login backend contract.

**Decision**: Add a separate Android project in `android/` and a root GitHub Actions workflow that targets that directory.

**Consequences**: The Android app can be built independently in CI without disturbing backend/frontend build tooling. Local verification remains static because the repository policy prohibits local build/test execution.

## Out of Scope

* Backend API changes.
* Full TOTP/Passkey UI implementation beyond representing the returned 2FA requirement.
* Running local Gradle, npm, install, build, or test commands.

## Research References

* [`research/project-lumen-android-reference.md`](research/project-lumen-android-reference.md) - Project-Lumen Android implementation, dependency, security, and workflow patterns mapped to this task.

## Technical Notes

* Source specification: `docs/android-mobile-login-integration.md`.
* Android app target directory: `android/`.
* GitHub Actions workflow target: `.github/workflows/synapse-android.yml`.
