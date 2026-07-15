# Android package rename to com.chloemlla.synapse.mobile with config migration

## Goal

Rename the Android app identity from `com.synapse.mobile` to `com.chloemlla.synapse.mobile`, and auto-migrate local config (encrypted multi-account credentials + device id) from the old package sandbox when the legacy app is still installed.

## What I already know

* User request: package → `com.chloemlla.synapse.mobile`; old config must auto-migrate.
* Current identity: `namespace`/`applicationId` = `com.synapse.mobile`; sources under `android/app/src/main/java/com/synapse/mobile/**`.
* Local config:
  * Credentials: EncryptedSharedPreferences `synapse_secure_metadata` + MMKV `synapse_encrypted_credentials` (`SynapseCredentialStore`)
  * Device id: SharedPreferences `synapse_device` (`SynapseDeviceId`)
  * Crash reports: private files (`CrashReportStore`) — not required for login continuity
* Credential store already has single-account → multi-account in-sandbox migration.
* Manifest uses relative components; FileProvider authority is `${applicationId}.fileprovider`.
* Docs/sample assetlinks hardcode `com.synapse.mobile`.
* Repo rule: no local Gradle build/test; CI verifies Android.

## Requirements

* Change both Gradle `applicationId` and Kotlin `namespace`/source+test packages to `com.chloemlla.synapse.mobile`.
* Implement **dual-package transition export** for auto-migration:
  * **Legacy flavor/APK** keeps `applicationId = com.synapse.mobile` and exposes a signature-protected export entry (ContentProvider) that can read local credentials + device id.
  * **Primary flavor/APK** uses `applicationId = com.chloemlla.synapse.mobile` and on first launch, if local config is empty and legacy package is present, imports config then marks migration complete.
* Export only:
  * multi-account credential payload (`accounts_json` + active account id, including JWT / client login token / profile fields)
  * device id
* Access control: only the new package may read; enforce package-name allowlist + same signing certificate (signature-level custom permission and/or runtime caller signature check).
* Keep in-sandbox storage names unchanged after rename so future same-package upgrades remain seamless.
* Update ProGuard keeps, docs/sample assetlinks package name, and Android Trellis specs that hardcode the old package path.
* Unit-test pure migration payload encode/decode and package-allowlist/signature decision helpers where feasible without instrumented tests.
* Do not run local Gradle/install commands.

## Acceptance Criteria

* [ ] Primary app `applicationId`/`namespace` are `com.chloemlla.synapse.mobile`
* [ ] Kotlin main/test packages and directories match the new package
* [ ] Legacy build keeps `applicationId = com.synapse.mobile` and exports the migration ContentProvider
* [ ] New app imports credentials + device id from legacy app when local stores are empty and legacy is installed
* [ ] Migration is one-shot (marked complete; no repeated export/import loops)
* [ ] Non-allowlisted / different-signature callers cannot read export data
* [ ] ProGuard, docs (`docs/assetlinks.synapse-mobile.sample.json`, integration doc package mention), and `.trellis/spec/android/*` package paths updated
* [ ] Unit tests cover migration payload codec / allowlist helper behavior
* [ ] No leftover production package refs to old name except intentional legacy constants (`LEGACY_APPLICATION_ID`, provider authority, etc.)

## Definition of Done

* Migration + rename implemented in `android/`
* Specs/docs updated for new package and dual-package migration path
* Unit tests updated/added for pure helpers
* Commit + push per repo guidelines
* CI is the build/test authority

## Technical Approach

**Approach A: product flavors (Recommended)**

* `legacy` flavor: `applicationId = com.synapse.mobile`, includes migration export ContentProvider + minimal keep-alive app surface.
* `production` (default) flavor: `applicationId = com.chloemlla.synapse.mobile`, includes importer on app start (`SynapseApplication` / early auth bootstrap).
* Shared source for credential/device stores and export payload codec.
* Export authority like `com.synapse.mobile.migration` (legacy applicationId-based).
* Importer queries legacy provider once, writes into new sandbox stores via existing `SynapseCredentialStore` / `SynapseDeviceId` APIs, then sets a local “migration_done” flag.
* If legacy app absent or export empty: no-op; user signs in normally.
* Crash reports are out of migration scope.

## Decision (ADR-lite)

**Context**: Renaming `applicationId` creates a new Android app sandbox, so silent upgrade migration is impossible.

**Decision**:
1. Rename both `applicationId` and Kotlin package/namespace to `com.chloemlla.synapse.mobile`.
2. Use dual-package transition export (legacy ContentProvider → new importer) for auto-migrating credentials + device id.

**Consequences**:
* Requires a short dual-APK transition: users need a legacy build that contains the exporter (update old app first, or keep old app installed while installing new).
* Server-side `assetlinks.json` / OAuth package registration must eventually list the new package (repo sample/docs updated; live host may be outside this repo).
* Slightly more Android build complexity (flavors).

## Out of Scope

* Migrating crash report files
* Backend production `assetlinks.json` deploy on Happy-TTS host (document only; update repo sample)
* Play Console / Google Cloud OAuth client re-bind operations (document note only)
* Local Gradle build/test/install
* Forced uninstall of legacy app after migration (optional UX later)

## Rollout note (for implementation comments/docs)

1. Publish/update legacy package with exporter.
2. Publish new package; first launch imports if legacy present.
3. Users may remove legacy app after successful import.

## Technical Notes

* Key files: `android/app/build.gradle.kts`, `AndroidManifest.xml`, `SynapseApplication.kt`, `SynapseCredentialStore.kt`, `SynapseDeviceId.kt`, `proguard-rules.pro`, `docs/assetlinks.synapse-mobile.sample.json`, `docs/android-mobile-login-integration.md`, `.trellis/spec/android/*`
* Current code has no package rename yet; only this planning task exists.
* CI workflow: unit test / lintDebug / assembleRelease under `android/`.
