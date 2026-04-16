# Technology Stack

**Analysis Date:** 2026-04-16

## Languages

**Primary:**
- Kotlin 2.0.21 - All application code
- Java 11 - JVM target compatibility

**Secondary:**
- XML - Android resources and manifest

## Runtime

**Environment:**
- Android SDK 26-36 (minSdk 26, targetSdk 35, compileSdk 36)
- JVM 11

**Package Manager:**
- Gradle with version catalog (`libs.versions.toml`)
- Foojay Resolver Convention 1.0.0

## Frameworks

**Core:**
- Jetpack Compose BOM 2024.12.01 - Declarative UI
- Material Design 3 - Design system
- Navigation Compose 2.8.5 - Type-safe navigation

**Data:**
- Room Database 2.6.1 - SQLite ORM with KSP
- Kotlinx Coroutines 1.8.1 - Async operations
- Kotlinx Serialization 1.6.3 - JSON parsing

**Media:**
- Media3 ExoPlayer 1.5.0 - Audio playback
- Media3 Session 1.5.0 - Media controls and service
- Media3 UI 1.5.0 - Player UI components

**Networking:**
- Ktor Client 3.0.0 - HTTP client engine
- Gson 2.10.1 - JSON parsing

**Image Loading:**
- Coil 2.7.0 - Image loading and caching

**Analytics:**
- PostHog Android 3.40.2 - Analytics tracking

**Build:**
- Android Gradle Plugin 8.13.1
- KSP 2.0.21-1.0.28 - Kotlin Symbol Processing

## Key Dependencies

**Critical:**
- `androidx.compose.material3:material3` - Material 3 components
- `androidx.media3:media3-exoplayer` - Audio playback
- `io.ktor:ktor-client-android` - HTTP networking
- `androidx.room:room-runtime` - Database

**Infrastructure:**
- `androidx.palette:palette-ktx` - Album color extraction
- `androidx.lifecycle:lifecycle-viewmodel-compose` - ViewModel integration

## Configuration

**Environment:**
- `local.properties` - Contains `sdk.dir`, `SPOTIFY_CLIENT_ID`, `SPOTIFY_CLIENT_SECRET`
- Credentials loaded at build time via `Properties` class

**Build:**
- `gradle.properties` - JVM args, AndroidX, configuration cache
- `libs.versions.toml` - Centralized version catalog
- `build.gradle.kts` - Root and app build config
- `proguard-rules.pro` - R8 optimization rules

## Platform Requirements

**Development:**
- Android Studio Hedgehog (2023.1.1) or later
- Android SDK 36
- Gradle 8.x

**Production:**
- Android 8.0+ (API 26)
- Target: Android 15 (API 35)

---

*Stack analysis: 2026-04-16*
