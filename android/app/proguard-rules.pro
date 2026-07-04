-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature

# Android framework entry points.
-keep,allowoptimization class com.synapse.mobile.MainActivity {
    public <init>();
    public <methods>;
    protected <methods>;
}
-keep,allowoptimization class com.synapse.mobile.SynapseApplication {
    public <init>();
    public <methods>;
    protected <methods>;
}

# MMKV loads native code and stores credential values by stable keys.
-keep class com.tencent.mmkv.** { *; }
-dontwarn com.tencent.mmkv.**

-dontwarn androidx.compose.**
-dontwarn androidx.lifecycle.**
-dontwarn androidx.camera.**
-dontwarn okhttp3.**
-dontwarn okio.**
