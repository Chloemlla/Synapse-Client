plugins {
    id("com.android.application") version "9.3.1" apply false
    // AGP 9 provides built-in Kotlin (KGP 2.2.10); do NOT apply org.jetbrains.kotlin.android.
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}
