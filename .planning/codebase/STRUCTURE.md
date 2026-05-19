# Codebase Structure

**Analysis Date:** 2026-05-19

## Directory Layout

```
JUKES/
├── app/                          # Main application module
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/juke/
│   │   │   │   ├── MainActivity.kt
│   │   │   │   ├── JukeApplication.kt
│   │   │   │   ├── analytics/          # Analytics and tracking
│   │   │   │   ├── database/           # Room database layer
│   │   │   │   ├── models/             # Data models and DTOs
│   │   │   │   ├── network/            # HTTP API clients
│   │   │   │   ├── services/           # Core business logic
│   │   │   │   ├── ui/                 # All Compose UI
│   │   │   │   ├── utils/              # Utility functions
│   │   │   │   └── viewmodels/         # UI state management
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
├── docs/
└── spotdown-kv-worker/         # Cloudflare Workers script
```

## Directory Purposes

**`app/src/main/java/com/example/juke/`**
- Purpose: All Kotlin source code
- Contains: All packages below

**`analytics/`**
- Purpose: Analytics and tracking (PostHog integration)
- Contains: `AnalyticsManager.kt`, `AnalyticsEvent.kt`, `PlayerAnalyticsHelper.kt`, `UsageExamples.kt`
- Key files: AnalyticsManager (singleton for tracking), AnalyticsEvent (event definitions)

**`database/`**
- Purpose: Room database layer with migrations and type converters
- Contains: `MusicDatabase.kt` (Room database with 9 migrations), `PlaylistEntities.kt`
- Key files: Database config, DAOs (`TrackDao`, `PlaylistDao`), entities (`TrackEntity`, `PlaylistEntity`, `PlaylistTrackEntity`)

**`models/`**
- Purpose: Data models and DTOs used throughout the application
- Contains: `Track.kt` (domain model), `SpotifyModels.kt` (Spotify API DTOs), `GithubRelease.kt` (update checks)
- Key files: Track model (central data structure), Spotify models (API responses)

**`network/`**
- Purpose: HTTP API clients for external services
- Contains: `ApiClient.kt` (Ktor client configuration), `SpotifyApi.kt` (Spotify Web API), `RecommenderApi.kt` (YouTube Music recommendations)
- Key files: ApiClient (singleton HTTP client), SpotifyApi (authenticated requests), RecommenderApi (radio queue fetching)

**`services/`**
- Purpose: Core business logic and long-running operations
- Contains: `PlaybackService.kt` (Media3 playback service), `PlaybackManager.kt` (ExoPlayer control), `MusicService.kt` (download/stream), `QueueManager.kt` (recommendations), `AudioEffectController.kt` (audio effects), `UpdateManager.kt` (GitHub updates)
- Key files: PlaybackService (foreground audio service), MusicService (download logic), QueueManager (smart recommendations)

**`viewmodels/`**
- Purpose: UI state management using StateFlow and ViewModel lifecycle
- Contains: `MusicViewModel.kt` (central playback state), `SearchViewModel.kt`, `LibraryViewModel.kt`, `PlayerViewModel.kt`, `HomeViewModel.kt`, `AlbumDetailViewModel.kt`, `PlaylistDetailViewModel.kt`
- Key files: MusicViewModel (primary state holder), others (screen-specific state)

**`ui/`**
- Purpose: All Compose UI implementation following Material Design 3
- Contains: `screens/` (navigation destinations), `components/` (reusable UI), `theme/` (MaterialTheme configuration)
- Key files: Screens (HomeScreen, SearchScreen, etc.), components (MiniPlayer, etc.), theme (color/type/shape definitions)

**`utils/`**
- Purpose: Utility functions and helpers used across layers
- Contains: `FastDownloader.kt` (multi-threaded downloads), `ArtistUtils.kt` (artist name matching), `BlacklistManager.kt`, `DatabaseMigrationHelper.kt`, `HapticHelper.kt`, `LyricsRomanizer.kt`
- Key files: FastDownloader (concurrent downloading), ArtistUtils (fuzzy matching), DatabaseMigrationHelper (schema migrations)

**`res/`**
- Purpose: Android resources and XML configurations
- Contains: `drawable/` (vector assets), `values/` (strings, colors, dimensions), `xml/` (resource configs), `mipmap-*/` (app icons)

## Key File Locations

**Entry Points:**
- `app/src/main/java/com/example/juke/MainActivity.kt` - Main Activity with NavHost and UI setup
- `app/src/main/java/com/example/juke/JukeApplication.kt` - Application class for global init
- `app/src/main/java/com/example/juke/services/PlaybackService.kt` - Media3 foreground service

**Configuration:**
- `app/build.gradle.kts` - App build configuration and dependencies
- `gradle/libs.versions.toml` - Version catalog for dependency management
- `gradle.properties` - Gradle properties (JVM args, etc.)
- `app/proguard-rules.pro` - R8 rules for release builds

**Core Logic:**
- `app/src/main/java/com/example/juke/viewmodels/MusicViewModel.kt` - Central playback and download state
- `app/src/main/java/com/example/juke/services/QueueManager.kt` - Recommendation engine and queue management
- `app/src/main/java/com/example/juke/services/MusicService.kt` - Download and stream management
- `app/src/main/java/com/example/juke/database/MusicDatabase.kt` - Room database configuration
- `app/src/main/java/com/example/juke/services/PlaybackManager.kt` - ExoPlayer control abstraction

**Testing:**
- `app/src/test/java/com/example/juke/ExampleUnitTest.kt` - Unit test example
- `app/src/androidTest/` - Instrumented test source set

## Naming Conventions

**Files:**
- PascalCase: `MusicService.kt`, `TrackCard.kt` (standard Kotlin files)
- Composables: `*Screen.kt` (navigation destinations), `*Component.kt` (reusable UI), `*Card.kt` (material cards)
- Resources: snake_case in XML (e.g., `activity_main.xml`, `ic_play_arrow.xml`)

**Directories:**
- lowercase: `database/`, `models/`, `services/`, `ui/`, `utils/`, `viewmodels/`, `analytics/`, `network/`
- Resources: lowercase with underscores (e.g., `drawable/`, `layout/`, `values/`)

**Classes/Interfaces:**
- PascalCase: `MusicViewModel`, `TrackDao`, `PlaybackService`
- Composable functions: PascalCase (`HomeScreen`, `MiniPlayer`)
- Constants: UPPER_SNAKE_CASE (`MAX_QUEUE_SIZE`, `API_TIMEOUT_MS`)

**Functions/Variables:**
- camelCase: `playTrack()`, `updateUiState()`, `isPlaying`
- Properties: camelCase with appropriate getters/setters
- Boolean properties: is/has prefix (`isPlaying`, `hasStartedWork`)

## Where to Add New Code

**New Feature:**
- Primary code: Feature-specific ViewModel in `viewmodels/` (e.g., `NewFeatureViewModel.kt`)
- UI: New screen in `ui/screens/` (`NewFeatureScreen.kt`) or component in `ui/components/` (`NewFeatureComponent.kt`)
- Business logic: New service in `services/` if complex (`NewFeatureService.kt`), or enhanced ViewModel if simple
- Data changes: Update entities in `models/` and DAOs in `database/` if persistence needed
- API calls: New file in `network/` if external API needed (`NewApi.kt`)

**New Component/Module:**
- Implementation: `ui/components/` directory
- Follow naming: `*Card.kt` for cards, `*Item.kt` for list items, `*Sheet.kt` for bottom sheets
- Make reusable: Accept parameters via `@Composable` function signature
- Preview: Add `@Preview` function in same file if visual component

**New Screen:**
- Implementation: `ui/screens/` directory
- Naming: `*Screen.kt` (e.g., `PodcastScreen.kt`)
- Navigation: Add route to `Screen` sealed class in `MainActivity.kt`
- Add to `NavHost` in `MainActivity.kt` with `composable()` lambda
- ViewModel: Create corresponding ViewModel in `viewmodels/` if needed

**Utilities:**
- Shared helpers: `utils/` directory
- Follow naming: `*Utils.kt` for collections of functions, `*Helper.kt` for specific helpers
- Stateless: Prefer pure functions with clear inputs/outputs
- Documentation: Add KDoc comments for public functions

**New API Integration:**
- New file in `network/` following `*Api.kt` naming (e.g., `WeatherApi.kt`)
- Use existing `ApiClient.kt` for HTTP client configuration (don't create new client)
- Add to `SpotifyApi.kt`/`RecommenderApi.kt` if extending existing services
- Consider authentication needs: OAuth (Spotify-like) vs API key vs none

## Special Directories

**`gradle/wrapper/`**
- Purpose: Gradle wrapper files (gradlew, gradlew.bat, wrapper properties)
- Generated: Yes (by Gradle init/wrapper task)
- Committed: Yes (required for building without Gradle installed)

**`docs/`**
- Purpose: Project documentation (architecture, setup, release notes)
- Contains: Release notes (`unreleased.md`, `CHANGELOG.md`), system docs (`FEATURES.md`, `AGENTS.md`)
- Generated: No (manually maintained)
- Committed: Yes

**`spotdown-kv-worker/`**
- Purpose: Cloudflare Workers script for Spotdown API (separate deployment)
- Generated: No (manual development)
- Committed: Yes (but deployed separately to Cloudflare)
- Note: Not part of Android build process

**`app/build/`**
- Purpose: Build output (compiled classes, packaged APKs)
- Generated: Yes (by Gradle build process)
- Committed: No (excluded by `.gitignore`)

**`.idea/`**
- Purpose: IntelliJ IDEA project settings and configuration
- Generated: Yes (by IDE)
- Committed: Yes (but often personalized - consider .gitignore for user-specific files)

**`.gradle/`**
- Purpose: Gradle daemon and caching files
- Generated: Yes (by Gradle runtime)
- Committed: No (excluded by `.gitignore`)

---
*Structure analysis: 2026-05-19*