# Codebase Structure

**Analysis Date:** 2026-03-08

## Directory Layout

```
juke/  (repository directory: JUKES)
├── app/                    # Main Android application module
├── gradle/                 # Gradle configuration and version definitions
├── spotdown-kv-worker/     # Cloudflare worker for API key management
├── .github/                # GitHub Actions workflows and issue templates
└── .vscode/                # VS Code configuration files
```

## App Module Directory Structure

```
app/src/main/java/com/example/juke/  (app package ID: com.example.juke)
├── analytics/              # Analytics tracking and event management
├── database/               # Room database implementation and entities
├── models/                 # Data models and DTOs
├── network/                # Network clients and API integrations
├── services/               # Background services and managers
├── ui/                     # UI components, screens, and themes
│   ├── components/         # Reusable UI components
│   ├── screens/            # Full-screen UI components
│   └── theme/              # App theme and styling
├── utils/                  # Utility functions and helper classes
├── viewmodels/             # ViewModel classes for UI state management
├── JukeApplication.kt      # Custom Application class
└── MainActivity.kt         # Main entry point Activity
```

## Directory Purposes

**analytics:**
- Purpose: User behavior tracking and analytics collection
- Contains: Analytics event definitions, manager implementation, usage examples
- Key files: `AnalyticsManager.kt`, `AnalyticsEvent.kt`

**database:**
- Purpose: Local data persistence using Room ORM
- Contains: Database class, entities, DAOs, migrations, and playlist support
- Key files: `MusicDatabase.kt`, `PlaylistEntities.kt`

**models:**
- Purpose: Domain data models and DTOs
- Contains: Data classes representing core domain objects
- Key files: `SpotifyModels.kt`, `Track.kt`, `GithubRelease.kt`

**network:**
- Purpose: External API communication and networking
- Contains: HTTP clients, API service implementations, response models
- Key files: `SpotifyApi.kt`, `ApiClient.kt`, `RecommenderApi.kt`

**services:**
- Purpose: Background operations and system-level services
- Contains: Media playback service, queue management, update checking
- Key files: `PlaybackService.kt`, `QueueManager.kt`, `MusicService.kt`

**ui:**
- Purpose: User interface components and styling
- Contains: Compose components, themed elements, and screen layouts
- Key files: Components in `ui/components/`, screens in `ui/screens/`

**ui/components:**
- Purpose: Reusable UI building blocks
- Contains: Individual UI components like cards, players, dialogs
- Key files: `MiniPlayer.kt`, `TrackCard.kt`, `PlayerScreen.kt`

**ui/screens:**
- Purpose: Full-screen UI compositions
- Contains: High-level screens organized by app sections
- Key files: `HomeScreen.kt`, `SearchScreen.kt`, `PlayerScreen.kt`

**ui/theme:**
- Purpose: App-wide styling and theming
- Contains: Color definitions, typography, theme composition
- Key files: `Theme.kt`, `Color.kt`, `Type.kt`

**utils:**
- Purpose: Common utility functions and helper classes
- Contains: Helper classes for specific operations
- Key files: `BlacklistManager.kt`, `DatabaseMigrationHelper.kt`

**viewmodels:**
- Purpose: UI state management and business logic coordination
- Contains: ViewModel classes implementing MVVM pattern
- Key files: `MusicViewModel.kt`, `SearchViewModel.kt`, `HomeViewModel.kt`

## Key File Locations

**Entry Points:**
- `app/src/main/java/com/example/juke/MainActivity.kt`: Main application entry point
- `app/src/main/java/com/example/juke/JukeApplication.kt`: Custom Application class
- `app/src/main/java/com/example/juke/services/PlaybackService.kt`: Background playback service

**Configuration:**
- `app/build.gradle.kts`: Module-level build configuration
- `gradle/libs.versions.toml`: Dependency version management
- `app/src/main/AndroidManifest.xml`: Android application manifest

**Core Logic:**
- `app/src/main/java/com/example/juke/viewmodels/MusicViewModel.kt`: Core music playback logic
- `app/src/main/java/com/example/juke/network/SpotifyApi.kt`: Spotify API integration
- `app/src/main/java/com/example/juke/services/QueueManager.kt`: Playback queue management

**Testing:**
- `app/src/test/`: Unit tests
- `app/src/androidTest/`: Instrumentation tests

## Naming Conventions

**Files:**
- PascalCase for classes and composables: `MusicViewModel.kt`, `HomeScreen.kt`
- CamelCase for functions and properties: `downloadSong()`, `isPlaying`

**Directories:**
- Lowercase with hyphens for multi-word concepts: `ui/components/`
- Descriptive names matching contained functionality: `network/`, `database/`

## Where to Add New Code

**New Feature:**
- Primary code: `app/src/main/java/com/example/juke/viewmodels/` (if business logic) or `app/src/main/java/com/example/juke/ui/screens/` (if UI)
- Tests: `app/src/test/java/com/example/juke/` for unit tests

**New Component/Module:**
- Implementation: `app/src/main/java/com/example/juke/ui/components/` for UI components
- ViewModel support: `app/src/main/java/com/example/juke/viewmodels/` if state management needed
- Data model: `app/src/main/java/com/example/juke/models/` if new domain entities

**Utilities:**
- Shared helpers: `app/src/main/java/com/example/juke/utils/`

## Special Directories

**database:**
- Purpose: Room database entities and DAOs for local persistence
- Generated: No
- Committed: Yes

**build:**
- Purpose: Generated build artifacts and intermediate files
- Generated: Yes
- Committed: No (in .gitignore)

---

*Structure analysis: 2026-03-08*