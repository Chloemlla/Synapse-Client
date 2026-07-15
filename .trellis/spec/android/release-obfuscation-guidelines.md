# Android Release Obfuscation Guidelines

## Scenario: Android R8 / ProGuard Release Obfuscation

### 1. Scope / Trigger

Trigger: changes under `android/` that touch release build types, `android/app/proguard-rules.pro`, manifest-instantiated Android components, WebView JavaScript bridges, native/JNI code, reflection-based libraries, generated persistence layers, or public Android API surfaces.

Local Gradle build, test, install, lint, and assemble commands remain prohibited. Actual verification must run through GitHub Actions.

### 2. Signatures

Release Gradle entry points:

```kotlin
buildTypes {
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro",
        )
    }
}
```

Primary rules file:

```text
android/app/proguard-rules.pro
```

Required Synapse runtime boundary rules:

```proguard
-keep,allowoptimization class com.chloemlla.synapse.mobile.MainActivity { public <init>(); public <methods>; protected <methods>; }
-keep,allowoptimization class com.chloemlla.synapse.mobile.SynapseApplication { public <init>(); public <methods>; protected <methods>; }
-keep,allowoptimization class com.chloemlla.synapse.mobile.core.migration.MigrationConfigProvider { public <init>(); public <methods>; protected <methods>; }
-keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }
-keep class com.tencent.mmkv.** { *; }
```

The vendored `:lumen-crash` module ships consumer ProGuard rules for author attribution integrity. Prefer those consumer rules over copying broad crash-package keeps into the app module.

### 3. Contracts

- Release builds must keep `isMinifyEnabled = true`, `isShrinkResources = true`, and `proguard-android-optimize.txt`.
- Do not add broad app-wide rules such as `-keep class com.chloemlla.synapse.mobile.** { *; }`; they defeat obfuscation and dead-code removal.
- Keep rules must document a concrete runtime boundary: manifest component, JavaScript bridge method, JNI/native loading class, serialized/persisted enum name, public external API, generated database/accessor, or reflection entry point.
- Do not copy rules from Project-Lumen or another Android app unless Synapse has the matching runtime surface.
- Prefer dependency consumer rules for AndroidX, Google, OkHttp, and Kotlin libraries; add Synapse rules only when local code creates a stable-name boundary.
- If a future feature adds Room, WorkManager, services, receivers, native C++ bridges, AIDL, or a public SDK surface, add targeted rules in the same change.

### 4. Validation & Error Matrix

| Condition | Required handling |
|-----------|-------------------|
| New manifest activity/application/provider/service/receiver | Add a targeted keep rule for the concrete class and required constructor/lifecycle methods. |
| New WebView JavaScript bridge | Keep `@JavascriptInterface` methods by annotation; do not keep the whole UI package. |
| New native/JNI bridge or native-loading library | Keep the bridge class/method names required by native lookup. |
| New persisted enum names | Keep enum `values()` / `valueOf(String)` and public enum fields for that package. |
| New reflection or class-name string lookup | Add a targeted keep rule and a comment explaining the lookup path. |
| Broad `com.chloemlla.synapse.mobile.**` keep rule | Reject unless the task explicitly proves a whole package is a public stable-name API. |
| Lumen-only rules such as Room/WorkManager/native bridge copied without matching Synapse code | Remove them. |
| Need to verify R8 output | Use GitHub Actions; do not run local Gradle commands. |

### 5. Good/Base/Bad Cases

Good: release builds shrink and obfuscate app implementation code while only manifest classes, WebView bridge methods, MMKV native-loading classes, and documented runtime boundaries retain stable names.

Base: a dependency warning is suppressed with `-dontwarn` after confirming the library provides consumer rules and Synapse does not depend on local reflection names.

Bad: adding `-keep class com.chloemlla.synapse.mobile.** { *; }`, copying Room or WorkManager rules without those dependencies, or disabling minification to work around a release failure.

### 6. Tests Required

- GitHub Actions must run Android unit tests, Android lint, and release assemble for Android changes.
- Static review must confirm `android/app/proguard-rules.pro` has no broad app-wide keep rule.
- Static review must confirm unused external rules were not copied from another app.
- For new reflection, native, JS bridge, parser, or credential behavior, add focused unit tests where the behavior can be validated without instrumentation.

### 7. Wrong vs Correct

#### Wrong

```proguard
-keep class com.chloemlla.synapse.mobile.** { *; }
-keep,allowoptimization class com.chloemlla.synapse.mobile.** extends androidx.work.ListenableWorker { *; }
-keep class com.projectlumen.app.core.security.NativeSecurityBridge { *; }
```

#### Correct

```proguard
-keep,allowoptimization class com.chloemlla.synapse.mobile.MainActivity {
    public <init>();
    public <methods>;
    protected <methods>;
}

-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

-keep class com.tencent.mmkv.** { *; }
```
