# brainstorm: project wide user experience optimization

## Goal

Review the Synapse Client project end to end and improve the user experience in practical, low-risk increments, starting with the client paths that directly affect users.

## What I already know

* The user asked for a full-project investigation and comprehensive UX optimization.
* The repository currently contains the Android client under `android/`, docs, GitHub workflow files, and Trellis/spec files. `frontend/src` and `src` are not present in this repo.
* Local build, test, lint, Gradle, npm, and install commands are prohibited by repository policy; verification must happen in GitHub workflows.
* Code modifications must be committed and pushed after completion.
* The current user-visible recent work focused on Android login, Turnstile verification, account switching, and web login confirmation.
* Android UI entry points inspected: `SynapseMobileApp.kt`, `SynapseLoginViewModel.kt`, `SynapseUiState.kt`, `TurnstileVerificationView.kt`, `QrScannerView.kt`, and `CrashReportScreen.kt`.

## Assumptions (temporary)

* The first optimization batch should prioritize the Android client because recent failures and user-visible flows are there.
* Backend/frontend changes should be limited to defects or copy/API mismatches found during the client UX review.
* This task should prefer targeted usability improvements over broad redesigns.

## Open Questions

* Which UX scope should be treated as the MVP if the project-wide review finds more issues than can safely fit in one change set?

## Requirements (evolving)

* Inspect user-facing Android screens and state transitions.
* Identify high-impact UX issues in login, account management, QR/web-login confirmation, error handling, and crash reporting.
* Implement a focused first batch of improvements that matches existing UI patterns and keeps risk low.
* Prevent invalid/expired QR payload actions before network requests.
* Keep sensitive local authorization values readable enough for users without rendering long raw tokens by default.
* Add confirmation before destructive local/session actions.

## Acceptance Criteria (evolving)

* [x] UX review notes identify affected files and user flows.
* [x] First optimization batch is implemented without local build/test execution.
* [x] Changes are committed and pushed with a conventional commit message.

## Definition of Done

* Tests or verification notes are updated where feasible.
* CI/GitHub workflow is expected to perform build, lint, and tests.
* Docs or task notes capture scope and residual risks.
* Rollback is straightforward because changes are scoped.

## Out of Scope (explicit)

* Large visual redesigns across all product surfaces in one commit.
* Dependency installation or local build/test execution.
* Backend contract changes unless required by a concrete UX defect.

## Technical Notes

* Task directory: `.trellis/tasks/07-04-project-ux-optimization`.
* Applicable spec layers discovered so far: `android`, `backend`, `frontend`.
* First-batch UX findings:
  * `QrPanel` allows mark/confirm actions when the pasted QR payload is malformed or expired; validation is deferred to repository errors.
  * `SessionPanel` executes revoke and clear actions immediately, which is risky for destructive account/session operations.
  * `CredentialSummary` renders the full `sml_` client login token, which is noisy on small screens and increases accidental exposure; the full token can still be copied.
  * Long account/device/token text can compress action buttons and reduce scanability.
* Implemented first-batch UX changes:
  * `SynapseUiState` now exposes derived UI readiness flags for QR payloads, credentials, client token availability, and stored account presence.
  * `SynapseLoginViewModel` validates malformed or expired QR payloads before mark/confirm network actions, and blocks web-login confirmation when no usable local credential exists.
  * `SynapseMobileApp` shows inline QR helper/error text, disables invalid QR actions, adds destructive-action confirmation dialogs, changes major actions to full-width mobile-friendly buttons, and renders SML tokens as previews while preserving full-token copy.
  * `QrScannerView` now explains the camera permission requirement before asking the user to grant access.
* Implemented second-batch visual polish:
  * `SynapseMobileTheme` now defines a fuller Material 3 light color scheme and shared rounded shapes for consistent surfaces.
  * `SynapseMobileApp` now has a security-focused top app bar, account/token/QR readiness header, icon tabs, icon action buttons, tonal status banners, constrained panel width, and upgraded credential/account rows.
  * `TurnstileVerificationView` now uses the same tonal card language with loading, error, retry, and verified state icons.
  * `QrScannerView` now presents camera permission as a polished card and clips the camera preview into the app's rounded visual system.
* Verification notes:
  * `git diff --check` passed.
  * Static grep found no new debug logging, plaintext token logging/display pattern, or new suppressions in changed UI files.
  * Sensitive-token review found only the expected manual JWT field label and full SML token copy value; rendered UI still uses token preview text.
  * Local Gradle/lint/test commands were not run because repository policy prohibits local build/test execution.
