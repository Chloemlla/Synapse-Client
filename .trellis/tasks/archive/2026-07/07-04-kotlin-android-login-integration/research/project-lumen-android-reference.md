# Project-Lumen Android Reference

## Source

Reference repository: `F:\Repositories\GitHub\Project-Lumen`

## Relevant Project-Lumen Patterns

* Android app is implemented in Kotlin under `app/src/main/java`.
* Root Gradle uses Kotlin DSL with Android Gradle Plugin `8.13.2`, Kotlin `2.1.20`, Compose compiler plugin `2.1.20`, and KSP `2.1.20-2.0.1`.
* App Gradle uses Java/Kotlin toolchain 21, `compileSdk = 37`, `minSdk = 26`, `targetSdk = 37`, and BuildConfig fields for API base URLs, certificate pins, build metadata, and secrets supplied by CI.
* Runtime dependencies relevant to Synapse mobile login:
  * `androidx.activity:activity-compose`
  * Compose Material 3 and Navigation Compose
  * `androidx.security:security-crypto`
  * `kotlinx-coroutines-android`
  * `androidx.datastore:datastore-preferences`
  * `com.tencent:mmkv`
  * `androidx.lifecycle:*`
  * `com.squareup.okhttp3:okhttp`
* Manifest includes `INTERNET` and `ACCESS_NETWORK_STATE`; cleartext traffic is disabled through `android:usesCleartextTraffic="false"`.
* API client pattern:
  * Central `ApiConfig` normalizes base URLs and reads BuildConfig values.
  * Central `SecureOkHttpFactory` rejects non-HTTPS base URLs and optionally configures certificate pinning.
  * API methods run on `Dispatchers.IO`, use OkHttp directly, send `Accept: application/json`, JSON request bodies, and bearer tokens only when present.
  * Non-2xx responses are converted into typed `IOException` subclasses with HTTP status code.
  * JSON parsing is performed through `org.json` model mappers instead of ad-hoc string parsing.
* Credential storage pattern:
  * `EncryptedSharedPreferences` stores encryption metadata.
  * MMKV can store encrypted credential data using a key generated and protected by encrypted preferences.
  * Device identity is generated locally from package and device attributes, then stored as a stable install identifier.
* Project-Lumen CI workflow pattern:
  * GitHub Actions sets up Java 21 and Gradle.
  * CI reads app version/build metadata into environment variables.
  * CI runs `gradle testDebugUnitTest`, `gradle lintDebug`, and `gradle assembleRelease`.
  * Release signing config is written from repository secrets.
  * Verification reports and release APK assets are uploaded as artifacts.

## Mapping to Synapse Android Login Documentation

* The Synapse doc should present a complete Kotlin integration reference rather than only endpoint descriptions.
* Keep Kotlin code dependency-light and close to Project-Lumen: OkHttp + coroutines + `org.json` + Security Crypto/MMKV/DataStore, with Compose workflow guidance.
* Include Gradle Kotlin DSL snippets, AndroidManifest requirements, ProGuard/R8 guidance, and GitHub Actions workflow snippets because the user explicitly requested dependencies and workflow.
* Keep verification commands in GitHub Actions only; do not suggest running local build/test commands as required local actions.
