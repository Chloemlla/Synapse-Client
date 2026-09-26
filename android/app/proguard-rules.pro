# Release obfuscation is intentionally boundary-focused. Avoid broad
# com.chloemlla.synapse.mobile keeps so R8 can shrink, optimize, and rename app code.
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature
-allowaccessmodification
-repackageclasses s

# Android framework entry points declared in AndroidManifest.xml.
-keep,allowoptimization class com.chloemlla.synapse.mobile.MainActivity {
    public <init>();
    public <methods>;
    protected <methods>;
}
-keep,allowoptimization class com.chloemlla.synapse.mobile.SynapseApplication {
    public <init>();
    public <methods>;
    protected <methods>;
}

# Legacy package migration export ContentProvider (legacy flavor manifest).
-keep,allowoptimization class com.chloemlla.synapse.mobile.core.migration.MigrationConfigProvider {
    public <init>();
    public <methods>;
    protected <methods>;
}

# WebView JavaScript bridge methods are invoked by source names from Turnstile HTML.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Enum helpers may be used by framework/runtime code and string comparisons.
-keepclassmembers enum com.chloemlla.synapse.mobile.** {
    public static final <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# MMKV loads native code and stores credential values by stable keys.
-keep class com.tencent.mmkv.** { *; }
-dontwarn com.tencent.mmkv.**

# AndroidX Security Crypto is used for encrypted credential metadata.
-dontwarn androidx.security.crypto.**

# CameraX and ML Kit ship consumer rules for runtime-loaded scanner components.
-dontwarn androidx.camera.**
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.internal.mlkit_vision_barcode.**

# ML Kit registrars are named only in AndroidManifest meta-data and instantiated with
# getDeclaredConstructor().newInstance(). Shrinking dropped their no-arg constructors,
# so discovery skipped every registrar and BarcodeScanning.getClient() dereferenced a
# null scanner factory.
-keep class * implements com.google.firebase.components.ComponentRegistrar {
    <init>();
}

# OkHttp/Okio are direct networking dependencies and may reference optional platforms.
-dontwarn okhttp3.**
-dontwarn okio.**

# Compose and lifecycle warnings are dependency-internal; app code remains obfuscatable.
-dontwarn androidx.compose.**
-dontwarn androidx.lifecycle.**

# Credential Manager / Passkey (Play Services auth provider)
-dontwarn androidx.credentials.**
-keep class androidx.credentials.** { *; }
-keep class com.google.android.gms.auth.** { *; }
-dontwarn com.google.android.gms.auth.**
# Google Identity / SIWG Credential Manager helpers
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn com.google.android.libraries.identity.googleid.**

# 设备证明（服务端 P2）：Play Integrity SDK 内部按类名反射装配 IntegrityManager，
# 与上面的 gms.auth 同理整包保留，避免 R8 改名后运行时取不到实现。
-keep class com.google.android.play.core.integrity.** { *; }
-dontwarn com.google.android.play.core.integrity.**

############################################################
# Lumen Crash SDK minify exemption
# Artifact: com.chloemlla.lumen:lumen-crash
# Required for third-party hosts with isMinifyEnabled=true.
# Prevents release white-screen / fail-closed author integrity.
############################################################

# Keep annotations / signatures used by integrity + public API.
-keepattributes RuntimeVisibleAnnotations, AnnotationDefault
# Persisted crash stack traces are unusable without line numbers.
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# Required: author attribution + integrity checks
-keep class com.chloemlla.lumen.crash.CrashAuthorAttribution {
    public static final java.lang.String *;
    public static *** payload();
}
-keepclassmembers class com.chloemlla.lumen.crash.CrashAuthorAttribution {
    public static final java.lang.String *;
}
-keep class com.chloemlla.lumen.crash.AuthorIntegrity {
    public static *** verifyOrThrow(...);
    public static *** fingerprintHex();
    public static *** verifiedAuthorBlock();
}
-keep class com.chloemlla.lumen.crash.AuthorBlock { *; }

# Required backup: keep public SDK API used by host integration
-keep class com.chloemlla.lumen.crash.LumenCrash { *; }
-keep class com.chloemlla.lumen.crash.LumenCrashConfig { *; }
-keep class com.chloemlla.lumen.crash.LumenCrashConfigBuilder { *; }
-keep class com.chloemlla.lumen.crash.LumenCrashDefaults { *; }
-keep class com.chloemlla.lumen.crash.LumenCrashFileProvider { *; }
-keep class com.chloemlla.lumen.crash.CrashReport { *; }
-keep class com.chloemlla.lumen.crash.CrashAppInfo { *; }
-keep class com.chloemlla.lumen.crash.CrashReportStore { *; }
-keep class com.chloemlla.lumen.crash.CrashBreadcrumbs { *; }
-keep class com.chloemlla.lumen.crash.CrashReportPasteUploader { *; }
-keep class com.chloemlla.lumen.crash.ui.LumenCrashReportScreenKt { *; }
-keep class com.chloemlla.lumen.crash.ui.LumenCrashGateKt { *; }

# Package-level exemption (safe default for third-party hosts)
-keep class com.chloemlla.lumen.crash.** { *; }
-keepclassmembers class com.chloemlla.lumen.crash.** { *; }
-keepnames class com.chloemlla.lumen.crash.**
-dontwarn com.chloemlla.lumen.crash.**
