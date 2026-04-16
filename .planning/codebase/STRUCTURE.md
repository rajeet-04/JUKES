# Codebase Structure

**Analysis Date:** 2026-04-16

## Directory Layout

```
JUKES/
├── app/                          # Main application module
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/juke/
│   │   │   │   ├── MainActivity.kt
│   │   │   │   ├── JukeApplication.kt
│   │   │   │   ├── database/
│   │   │   │   ├── models/
│   │   │   │   ├── network/
│   │   │   │   ├── services/
│   │   │   │   ├── viewmodels/
│   │   │   │   ├── ui/
│   │   │   │   ├── analytics/
│   │   │   │   └── utils/
│   │   │   ├── res/
│   │   │   └── AndroidManifest.xml
│   │   ├── test/
│   │   └── androidTest/
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── gradle/
│   ├── wrapper/
│   └── libs.versions.toml
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── local.properties
└── docs/
```

## Directory Purposes

**`app/src/main/java/com/example/juke/`**
- Purpose: All Kotlin source code
- Contains: All packages below

**`database/`**
- Purpose: Room database layer
- Contains: `MusicDatabase.kt`, `PlaylistEntities.kt`
- Key files: Database config, DAOs, entities

**`models/`**
- Purpose: Data models and DTOs
- Contains: `Track.kt`, `SpotifyModels.kt`, `GithubRelease.kt`

**`network/`**
- Purpose: HTTP API clients
- Contains: `ApiClient.kt`, `SpotifyApi.kt`, `RecommenderApi.kt`

**`services/`**
- Purpose: Core business logic
- Contains: `PlaybackService.kt`, `PlaybackManager.kt`, `MusicService.kt`, `QueueManager.kt`, `AudioEffectController.kt`, `UpdateManager.kt`

**`viewmodels/`**
- Purpose: UI state management
- Contains: `MusicViewModel.kt`, `SearchViewModel.kt`, `LibraryViewModel.kt`, `PlayerViewModel.kt`, `HomeViewModel.kt`, `AlbumDetailViewModel.kt`, `PlaylistDetailViewModel.kt`

**`ui/`**
- Purpose: All Compose UI
- Contains: `screens/`, `components/`, `theme/`

**`analytics/`**
- Purpose: Analytics and tracking
- Contains: `AnalyticsManager.kt`, `AnalyticsEvent.kt`, `PlayerAnalyticsHelper.kt`, `UsageExamples.kt`

**`utils/`**
- Purpose: Utility functions
- Contains: `FastDownloader.kt`, `ArtistUtils.kt`, `BlacklistManager.kt`, `DatabaseMigrationHelper.kt`, `HapticHelper.kt`, `LyricsRomanizer.kt`

**`res/`**
- Purpose: Android resources
- Contains: `drawable/`, `values/`, `xml/`, `mipmap-*/`

## Key File Locations

**Entry Points:**
- `app/src/main/java/com/example/juke/MainActivity.kt` - Main Activity
- `app/src/main/java/com/example/juke/JukeApplication.kt` - Application class
- `app/src/main/java/com/example/juke/services/PlaybackService.kt` - Media service

**Configuration:**
- `app/build.gradle.kts` - App build config
- `gradle/libs.versions.toml` - Version catalog
- `gradle.properties` - Gradle properties
- `app/proguard-rules.pro` - R8 rules

**Core Logic:**
- `viewmodels/MusicViewModel.kt` - Playback state
- `services/QueueManager.kt` - Recommendations
- `services/MusicService.kt` - Downloads
- `database/MusicDatabase.kt` - Database

**Testing:**
- `app/src/test/java/com/example/juke/ExampleUnitTest.kt` - Unit test
- `app/src/androidTest/` - Instrumented tests

## Naming Conventions

**Files:**
- PascalCase: `MusicService.kt`, `TrackCard.kt`
- Composables: `*Screen.kt`, `*Component.kt`, `*Card.kt`

**Directories:**
- lowercase: `database/`, `models/`, `services/`

## Where to Add New Code

**New Feature:**
- Primary code: Feature-specific ViewModel in `viewmodels/`
- UI: New screen in `ui/screens/` or component in `ui/components/`
- Business logic: New service in `services/` if complex, or in ViewModel

**New Component/Module:**
- Implementation: `ui/components/` directory
- Follow naming: `*Card.kt`, `*Item.kt`, `*Sheet.kt`

**New Screen:**
- Implementation: `ui/screens/` directory
- Naming: `*Screen.kt`
- Navigation: Add to `MainActivity.kt` NavHost

**Utilities:**
- Shared helpers: `utils/` directory
- Follow naming: `*Utils.kt`, `*Helper.kt`

**New API Integration:**
- New file in `network/` following `*Api.kt` naming
- Register in `ApiClient.kt` if shared client needed

## Special Directories

**`gradle/wrapper/`**
- Purpose: Gradle wrapper files
- Generated: Yes
- Committed: Yes

**`docs/`**
- Purpose: Project documentation
- Contains: Release notes, system docs, setup guides

**`spotdown-kv-worker/`**
- Purpose: Cloudflare Workers script
- Generated: No
- Committed: Yes (separate worker deployment)

**`app/build/`**
- Purpose: Build output
- Generated: Yes (by Gradle)
- Committed: No (.gitignore)

---

*Structure analysis: 2026-04-16*
