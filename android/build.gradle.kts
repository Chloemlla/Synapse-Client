buildscript {
    dependencies {
        // AGP 9's built-in Kotlin bundles KGP 2.2.10, whose compiler can only read Kotlin
        // metadata up to 2.3.0. The lumen-crash SDK (0.1.0-a76cae4d) is compiled with Kotlin
        // 2.4.0 (kotlin-stdlib 2.4.10), so raise the built-in Kotlin compiler to 2.4.10 by
        // declaring a higher KGP on the build classpath (see AGP 9 release notes). Must match
        // the org.jetbrains.kotlin.plugin.compose version declared below.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    }
}

plugins {
    id("com.android.application") version "9.3.1" apply false
    // AGP 9 provides built-in Kotlin (raised to KGP 2.4.10 above); do NOT apply
    // org.jetbrains.kotlin.android — it is incompatible with the AGP 9 DSL.
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
}
