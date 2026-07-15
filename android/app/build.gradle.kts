plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun buildConfigString(value: String): String =
    value.replace("\\", "\\\\").replace("\"", "\\\"")

fun providerString(name: String, defaultValue: String): String =
    providers.environmentVariable(name)
        .orNull
        ?.takeIf { it.isNotBlank() }
        ?: providers.gradleProperty(name).orNull?.takeIf { it.isNotBlank() }
        ?: defaultValue

val releaseKeystoreFile = providerString("KEYSTORE_FILE", "")
val releaseKeystorePassword = providerString("KEYSTORE_PASSWORD", "")
val releaseKeyAlias = providerString("KEY_ALIAS", "")
val releaseKeyPassword = providerString("KEY_PASSWORD", "")
val hasReleaseSigningConfig = listOf(
    releaseKeystoreFile,
    releaseKeystorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { it.isNotBlank() }

android {
    namespace = "com.chloemlla.synapse.mobile"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.chloemlla.synapse.mobile"
        minSdk = 26
        targetSdk = 37
        versionCode = providers.environmentVariable("SYNAPSE_ANDROID_VERSION_CODE")
            .orNull
            ?.toIntOrNull()
            ?: 1
        versionName = providers.environmentVariable("SYNAPSE_ANDROID_VERSION_NAME")
            .orNull
            ?.takeIf { it.isNotBlank() }
            ?: "1.0.0"

        val synapseApiBaseUrl = providerString("SYNAPSE_API_BASE_URL", "https://tts.chloemlla.com")
        val synapseApiHost = runCatching {
            java.net.URI(synapseApiBaseUrl).host
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "tts.chloemlla.com"

        buildConfigField(
            "String",
            "SYNAPSE_API_BASE_URL",
            "\"${buildConfigString(synapseApiBaseUrl)}\"",
        )
        buildConfigField(
            "String",
            "LEGACY_APPLICATION_ID",
            "\"com.synapse.mobile\"",
        )
        buildConfigField(
            "String",
            "PRODUCTION_APPLICATION_ID",
            "\"com.chloemlla.synapse.mobile\"",
        )
        // Used by Android App Links for Linux.do / provider-bind HTTPS callbacks.
        manifestPlaceholders["synapseApiHost"] = synapseApiHost
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("production") {
            dimension = "distribution"
            isDefault = true
            applicationId = "com.chloemlla.synapse.mobile"
            buildConfigField("boolean", "IS_LEGACY_PACKAGE", "false")
        }
        create("legacy") {
            dimension = "distribution"
            applicationId = "com.synapse.mobile"
            buildConfigField("boolean", "IS_LEGACY_PACKAGE", "true")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    val releaseSigningConfig = if (hasReleaseSigningConfig) {
        signingConfigs.create("release") {
            storeFile = file(releaseKeystoreFile)
            storePassword = releaseKeystorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    } else {
        null
    }

    buildTypes {
        release {
            releaseSigningConfig?.let {
                signingConfig = it
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    lint {
        disable += "GradleDependency"
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.tencent:mmkv:2.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
