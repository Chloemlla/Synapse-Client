# Deep Android Obfuscation Configuration

## Goal

Implement a Project-Lumen-style Android release obfuscation configuration for Synapse Client so release APKs use R8/ProGuard minification, resource shrinking, dead-code removal, and narrowly scoped keep rules for runtime entry points.

## Requirements

* Keep release builds minified and resource-shrunk with the optimized Android default ProGuard file.
* Expand `android/app/proguard-rules.pro` beyond the current shallow rules using Project-Lumen's pattern: preserve only Android/runtime boundaries that must remain stable, and allow application implementation code to be obfuscated and optimized.
* Preserve Synapse framework entry points from `AndroidManifest.xml`: `MainActivity`, `SynapseApplication`, and `FileProvider` metadata behavior.
* Preserve WebView `@JavascriptInterface` methods used by the Turnstile bridge.
* Preserve native-loading library boundaries for MMKV and avoid suppressing AndroidX Security Crypto behavior.
* Preserve CameraX and ML Kit barcode scanning runtime behavior used by the QR scanner without broad keep rules that block shrinking.
* Preserve enum runtime helpers where enum names may be compared or displayed.
* Add R8 optimization/removal settings that make dead-code stripping explicit while avoiding unsafe assumptions such as native bridge rules that Synapse does not use.
* Do not add dependencies, do not install packages, and do not run local Gradle build/test/install commands.

## Acceptance Criteria

* [x] `android/app/proguard-rules.pro` contains deep, Synapse-specific rules modeled after Project-Lumen rather than broad app-wide keeps.
* [x] Release Gradle configuration still uses `isMinifyEnabled = true`, `isShrinkResources = true`, and `proguard-android-optimize.txt`.
* [x] No rules are copied for unused Project-Lumen-only systems such as Room, WorkManager, public AIDL/Open API, or native C++ bridges.
* [x] GitHub Actions remains the source of actual Android verification (`testDebugUnitTest`, `lintDebug`, `assembleRelease`).
* [x] Local verification is limited to static inspection and git diff review.

## Definition of Done

* Android ProGuard/R8 rules are updated.
* Relevant Trellis task notes are recorded.
* No local build/test/install command has been run.
* Changes are committed with a conventional commit message.

## Technical Approach

Use Project-Lumen's `app/proguard-rules.pro` as the structural template, then adapt it to Synapse's actual Android surface:

* Keep manifest-instantiated application/activity classes.
* Keep `@JavascriptInterface` methods by annotation.
* Keep enum helper members.
* Keep MMKV's native-loading boundary and suppress dependency-internal warnings for AndroidX Security Crypto, CameraX, ML Kit barcode scanning, OkHttp, and Okio.
* Add explicit R8 tuning for optimization passes, repackaging, annotation/signature retention, and optional diagnostic output comments.

## Decision (ADR-lite)

**Context**: Synapse already enables release minification and resource shrinking, but current keep rules are minimal and do not fully document runtime boundaries. Project-Lumen has a deeper, boundary-focused ProGuard configuration.

**Decision**: Apply a Synapse-specific deep ruleset rather than copying Project-Lumen verbatim.

**Consequences**: R8 can obfuscate and remove more Synapse implementation code while fragile runtime entry points remain stable. Future Android features that add reflection, native bridges, services, workers, or generated persistence layers must add matching rules.

## Out of Scope

* Adding native C++ security bridge code.
* Adding new dependencies or changing Gradle plugin versions.
* Running local Android builds/tests.
* Backend/frontend obfuscation.

## Research References

* [`research/lumen-obfuscation.md`](research/lumen-obfuscation.md) - comparison of Project-Lumen rules and Synapse Android runtime boundaries.

## Technical Notes

* Synapse files inspected: `android/app/build.gradle.kts`, `android/app/proguard-rules.pro`, `android/app/src/main/AndroidManifest.xml`, Android source under `android/app/src/main/java/com/synapse/mobile`.
* Project-Lumen files inspected: `app/build.gradle.kts`, `app/proguard-rules.pro`, `gradle.properties`.
* Repository policy prohibits local Gradle build/test/install commands; CI handles Android verification.
