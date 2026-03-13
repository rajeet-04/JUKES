# Technology Stack

**Analysis Date:** 2026-03-08

## Languages

**Primary:**
- Kotlin - Android application development
- TOML - Gradle version catalog

**Secondary:**
- Shell scripts - Build and deployment scripts

## Runtime

**Environment:**
- Android SDK API 36
- JVM 11

**Package Manager:**
- Gradle 8.13.1
- Lockfile: present (gradle/libs.versions.toml)

## Frameworks

**Core:**
- Android SDK - Native Android development
- Jetpack Compose - Modern UI toolkit
- Android Architecture Components - MVVM pattern implementation

**Testing:**
- JUnit 4.13.2 - Unit testing framework
- Espresso 3.5.1 - UI testing framework

**Build/Dev:**
- Gradle - Build automation
- KSP (Kotlin Symbol Processing) - Compile-time code generation

## Key Dependencies

**Critical:**
- Ktor 3.0.0 - HTTP client for network requests
- Room 2.6.1 - SQLite database abstraction
- Media3 1.5.0 - Media playback framework
- Coil 2.7.0 - Image loading and caching
- PostHog 3.32.+ - Analytics tracking

**Infrastructure:**
- Gson 2.10.1 - JSON serialization/deserialization
- Kotlin Coroutines 1.8.1 - Asynchronous programming
- Kotlin Serialization 1.6.3 - JSON handling
- Android Navigation 2.8.5 - Screen navigation
- Palette - Color extraction from images

## Configuration

**Environment:**
- local.properties - API keys and build configuration
- AndroidManifest.xml - Android app configuration

**Build:**
- build.gradle.kts - Gradle build scripts
- gradle/libs.versions.toml - Dependency version management

## Platform Requirements

**Development:**
- JDK 11+
- Android Studio or IntelliJ IDEA
- Android SDK 36

**Production:**
- Android API 26+ (Android 8.0+)

---

*Stack analysis: 2026-03-08*