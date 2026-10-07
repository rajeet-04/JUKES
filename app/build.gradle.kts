import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.juke"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.juke"
        minSdk = 26
        targetSdk = 36
        versionCode = 19
        versionName = "2.4.0-beta"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Load Spotify credentials from local.properties
        val properties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            properties.load(FileInputStream(localPropertiesFile))
        }

        buildConfigField(
            "String",
            "SPOTIFY_CLIENT_ID",
            "\"${properties.getProperty("SPOTIFY_CLIENT_ID", "")}\""
        )
        buildConfigField(
            "String",
            "SPOTIFY_CLIENT_SECRET",
            "\"${properties.getProperty("SPOTIFY_CLIENT_SECRET", "")}\""
        )
        buildConfigField(
            "String",
            "POSTHOG_API_KEY",
            "\"${properties.getProperty("POSTHOG_API_KEY", "")}\""
        )
        buildConfigField(
            "String",
            "POSTHOG_HOST",
            "\"${properties.getProperty("POSTHOG_HOST", "")}\""
        )
        // JUKES backend (youtube-music-alexa-skill): first audio source when both are set. The URL
        // is public. The server ignores the key now, but the app's gate still needs it non-blank.
        buildConfigField(
            "String",
            "JUKE_BACKEND_URL",
            "\"${properties.getProperty("JUKE_BACKEND_URL", "https://ms.rajeet.in")}\""
        )
        buildConfigField(
            "String",
            "JUKE_BACKEND_KEY",
            "\"${properties.getProperty("JUKE_BACKEND_KEY", "unused")}\""
        )
        // Rollout flag: false = use only the legacy /audio/ endpoint instead of /v1 prepare + poll.
        buildConfigField(
            "boolean",
            "JUKE_BACKEND_V1",
            properties.getProperty("JUKE_BACKEND_V1", "true")
        )
    }

    androidResources {
        // Strips all languages except English
        localeFilters += "en"
    }

    signingConfigs {
        getByName("debug")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    packaging {
        resources {
            // Build metadata the app never reads at runtime.
            excludes += setOf(
                "META-INF/*.version",
                "META-INF/*.kotlin_module",
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json"
            )
        }
    }
    dependenciesInfo {
        // Play-only dependency report; this APK is side-loaded from GitHub.
        includeInApk = false
        includeInBundle = false
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.palette)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Kotlin
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // Ktor
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.serialization.kotlinx.json)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.compose.foundation.layout)
    ksp(libs.androidx.room.compiler)

    // Media3
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.datasource)
    implementation(libs.androidx.media3.datasource.okhttp)

    // Coil
    implementation(libs.coil.compose)
    implementation(libs.haze)

    // Gson
    implementation(libs.gson)

    // PostHog
    implementation("com.posthog:posthog-android:3.71.4")

    testImplementation(libs.junit)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}