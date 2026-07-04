# Project-Lumen Obfuscation Mapping

## Source Project-Lumen Configuration

Project-Lumen release builds use:

* `isMinifyEnabled = true`
* `isShrinkResources = true`
* `getDefaultProguardFile("proguard-android-optimize.txt")`
* app-specific `proguard-rules.pro`

Its custom rules keep only runtime boundaries:

* Android framework entry points: activity, application, receivers, services, workers.
* Room database generated/runtime entry points.
* Persisted enum helpers.
* WebView `@JavascriptInterface` methods.
* Native bridge methods/classes.
* Public AIDL/Open API packages.
* Library warning suppression for Compose, Lifecycle, Navigation, Room.

## Synapse Runtime Boundaries

Synapse already has release minification and resource shrinking enabled with the optimized default ProGuard file. Its runtime boundaries differ from Project-Lumen:

* Manifest entry points: `com.synapse.mobile.MainActivity`, `com.synapse.mobile.SynapseApplication`, `androidx.core.content.FileProvider`.
* WebView JavaScript bridge: `@android.webkit.JavascriptInterface` methods in `TurnstileVerificationView.kt`.
* Camera scanner: CameraX and ML Kit barcode scanning are used from `QrScannerView.kt`.
* Storage/security: MMKV and AndroidX Security Crypto are used by credential storage.
* Networking: OkHttp/Okio are used directly.
* JSON mapping uses `org.json` manually, not reflection-based Gson/Moshi/serialization.

## Rules Not Ported

The following Project-Lumen rules should not be copied because Synapse does not currently use those systems:

* Room database/entity/DAO keeps.
* WorkManager `ListenableWorker` constructor keeps.
* App receivers/services keeps, unless future manifest/components add them.
* Native bridge and `native <methods>` keeps.
* Public AIDL/Open API package keeps.
* Navigation suppressions unless Synapse adds Navigation Compose.

## Recommended Approach

Use a boundary-focused ruleset:

* Keep manifest-instantiated Synapse activity/application constructors and lifecycle methods.
* Keep annotated WebView bridge methods.
* Keep enum `values()`/`valueOf()` and public enum fields.
* Keep MMKV classes and suppress native-loading warnings.
* Keep only library runtime surfaces that require stable names, such as MMKV native-loading classes.
* Suppress dependency-internal warnings for AndroidX Security Crypto, CameraX, ML Kit barcode, OkHttp, Okio, Compose, and Lifecycle while relying on their consumer rules for generated/runtime-loaded code.
* Avoid broad `-keep class com.synapse.mobile.** { *; }` because that defeats obfuscation and dead-code removal.
