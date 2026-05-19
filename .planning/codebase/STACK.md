# Technology Stack

**Analysis Date:** 2026-05-19

## Languages

**Primary:**
- Kotlin [2.0.21] - Main application language used throughout the app (`app/build.gradle.kts`)

**Secondary:**
- Java [11] - Used for Android SDK compatibility and some library interfaces (`app/build.gradle.kts` shows source/target compatibility = JavaVersion.VERSION_11)

## Runtime

**Environment:**
- Android Runtime (ART) [API Level 36] - Compile and target SDK version 36 (`app/build.gradle.kts` lines 14, 19)

**Package Manager:**
- Gradle [8.13.1] - Wrapper scripts present (`gradlew`, `gradlew.bat`)
- Lockfile: Present (`gradle/libs.versions.toml` provides dependency version management)

## Frameworks

**Core:**
- Android Gradle Plugin [8.13.1] - Build system for Android applications (`gradle/libs.versions.toml` line 2, `settings.gradle.kts`)
- Kotlin Android Extensions [2.0.21] - Kotlin language support for Android (`gradle/libs.versions.toml` line 3)
- Jetpack Compose [2024.12.01] - Modern UI toolkit for native Android (`gradle/libs.versions.toml` line 10, `app/build.gradle.kts` lines 84-86)
- Material Design 3 - UI components and theming (`app/build.gradle.kts` lines 100-102)

**Testing:**
- JUnit [4.13.2] - Unit testing framework (`gradle/libs.versions.toml` line 5)
- Espresso [3.5.1] - Android UI testing framework (`gradle/libs.versions.toml` line 8)
- AndroidX Test - Core testing utilities (`app/build.gradle.kts` lines 139-145)

**Build/Dev:**
- Gradle [8.13.1] - Build automation tool
- Kotlin KSP [2.0.21-1.0.28] - Kotlin Symbol Processing API for code generation (`gradle/libs.versions.toml` line 80)
- ProGuard/R8 - Code shrinking and optimization (`app/build.gradle.kts` lines 65-71)

## Key Dependencies

**Critical:**
- Kotlin Coroutines [1.8.1] - Asynchronous programming (`gradle/libs.versions.toml` line 11, used extensively in `AnalyticsManager.kt`)
- Ktor Client [3.0.0] - HTTP client for network requests (`gradle/libs.versions.toml` lines 13, 50-55, used in `ApiClient.kt`)
- Room Database [2.6.1] - SQLite object mapping library (`gradle/libs.versions.toml` line 14, used in `MusicDatabase.kt`)
- Media3 [1.5.0] - Media playback library (`gradle/libs.versions.toml` line 15, used for audio playback)
- Coil [2.7.0] - Image loading library (`gradle/libs.versions.toml` line 16, used for album art)
- Gson [2.10.1] - JSON serialization (`gradle/libs.versions.toml` line 18, used in `AnalyticsManager.kt`)
- PostHog Android [3.40.2] - Analytics and event tracking (`app/build.gradle.kts` line 137, used in `AnalyticsManager.kt`)

**Infrastructure:**
- AndroidX Core KTX [1.15.0] - Kotlin extensions for Android framework (`gradle/libs.versions.toml` line 4)
- AndroidX Lifecycle [2.8.7] - Lifecycle-aware components (`gradle/libs.versions.toml` line 8)
- AndroidX Activity Compose [1.9.3] - Compose integration with Activity (`gradle/libs.versions.toml` line 9)
- AndroidX Navigation Compose [2.8.5] - Navigation component for Compose (`gradle/libs.versions.toml` line 17)
- AndroidX Palette [1.0.0] - Color extraction from images (`gradle/libs.versions.toml` line 20)

## Configuration

**Environment:**
- Configured via `local.properties` file (not committed, contains API keys)
- BuildConfig fields generated for: SPOTIFY_CLIENT_ID, SPOTIFY_CLIENT_SECRET, POSTHOG_API_KEY, POSTHOG_HOST (`app/build.gradle.kts` lines 25-51)

**Build:**
- Version catalog in `gradle/libs.versions.toml` for centralized dependency management
- Build types: debug and release configurations with minification enabled for release
- Kotlin compiler targeting JVM 11

## Platform Requirements

**Development:**
- Java JDK 11
- Android SDK 36 (compileSdk/targetSdk)
- Android Build Tools (implied by AGP 8.13.1)
- Gradle 8.13.1+

**Production:**
- Minimum Android SDK 26 (Android 8.0 Oreo) (`app/build.gradle.kts` line 18)
- Target Android SDK 36 (Android 14)

---

*Stack analysis: 2026-05-19*