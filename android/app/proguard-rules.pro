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

# FileProvider is referenced directly from the manifest and its XML metadata.
-keep,allowoptimization class androidx.core.content.FileProvider {
    public <init>();
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
