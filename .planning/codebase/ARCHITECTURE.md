# Architecture

**Analysis Date:** 2026-04-16

## Pattern Overview

**Overall:** MVVM + Clean Architecture + Repository Pattern

**Key Characteristics:**
- UI layer uses Jetpack Compose with unidirectional data flow
- ViewModels manage UI state via StateFlow
- Services handle business logic (playback, downloads, recommendations)
- Data layer uses Room DAOs for database access
- Network layer uses Ktor client for API calls

## Layers

**UI Layer (Compose):**
- Purpose: Display data and capture user input
- Location: `app/src/main/java/com/example/juke/ui/`
- Contains: Screens, components, theme
- Depends on: ViewModels
- Used by: Android Activity

**ViewModel Layer:**
- Purpose: Manage UI state, business logic coordination
- Location: `app/src/main/java/com/example/juke/viewmodels/`
- Contains: `MusicViewModel`, `SearchViewModel`, `LibraryViewModel`, etc.
- Depends on: Services, DAOs, API clients
- Used by: UI layer (Compose screens)

**Service Layer:**
- Purpose: Core business logic (playback, downloads, queue management)
- Location: `app/src/main/java/com/example/juke/services/`
- Contains: `PlaybackService`, `PlaybackManager`, `MusicService`, `QueueManager`, `AudioEffectController`
- Depends on: Database, Network, Models
- Used by: ViewModels

**Data Layer (Room):**
- Purpose: Local SQLite database access
- Location: `app/src/main/java/com/example/juke/database/`
- Contains: `MusicDatabase`, `TrackDao`, `PlaylistDao`, Entities
- Depends on: Room runtime
- Used by: Services, ViewModels

**Network Layer:**
- Purpose: HTTP API communication
- Location: `app/src/main/java/com/example/juke/network/`
- Contains: `ApiClient`, `SpotifyApi`, `RecommenderApi`
- Depends on: Ktor client
- Used by: Services

## Data Flow

**Play Track Flow:**
1. User taps track in UI
2. Screen calls `MusicViewModel.playTrack(track)`
3. `MusicViewModel` updates `_uiState`
4. `PlaybackManager.setQueue(tracks, index)` called
5. `PlaybackService` receives command via MediaController
6. `ExoPlayer` loads and plays MediaItem
7. Notification updates with track info
8. `QueueManager` triggers for recommendations

**Download Flow:**
1. User requests download
2. `MusicService.smartDownloadAndIndex(song)` called
3. Try Spotmate → fallback to Gamepvz
4. Multi-threaded download via `FastDownloader`
5. MP3 header and duration verification
6. Lyrics fetched from LRCLib
7. Thumbnail downloaded
8. Track saved to Room database
9. UI updates via Flow observation

**Recommendation Flow:**
1. Queue size drops to ≤2 tracks
2. `QueueManager.checkAndFetchRecommendations()` triggered
3. Online: YouTube Music radio queue → Spotify validation
4. Offline fallback: Score local library tracks
5. Filter by artist blacklist
6. Add validated tracks to queue
7. Start downloads (max 6 concurrent)

**State Management:**
- ViewModels expose `StateFlow` for UI state
- `MutableStateFlow.update {}` for immutable state updates
- Services use `StateFlow` internally
- UI observes via `collectAsState()`

## Key Abstractions

**MusicViewModel:**
- Purpose: Central state holder for playback and downloads
- Examples: `MusicUiState`, `DownloadItem`, `DownloadStatus`
- Pattern: Single source of truth for playback state

**PlaybackManager (Singleton):**
- Purpose: Control ExoPlayer via MediaController
- Examples: `playTrack()`, `addToQueue()`, `seekTo()`
- Pattern: Singleton with companion object

**QueueManager (Singleton):**
- Purpose: Smart recommendation engine
- Examples: `initializeQueue()`, `fetchAndQueueRecommendations()`
- Pattern: Singleton managing background coroutine scope

**MusicService:**
- Purpose: Download and stream management
- Examples: `smartDownloadAndIndex()`, `streamTrack()`
- Pattern: Context-dependent service class

**SpotifyApi (Object):**
- Purpose: Spotify Web API wrapper
- Examples: `search()`, `getTrack()`, `getPlaylist()`
- Pattern: Object declaration with Ktor client

## Entry Points

**MainActivity:**
- Location: `app/src/main/java/com/example/juke/MainActivity.kt`
- Triggers: App launch, intent handling
- Responsibilities: Navigation, permission handling, update checks

**PlaybackService (Media3):**
- Location: `app/src/main/java/com/example/juke/services/PlaybackService.kt`
- Triggers: Media button, notification, Android Auto
- Responsibilities: Foreground service, ExoPlayer, media session

**JukeApplication:**
- Location: `app/src/main/java/com/example/juke/JukeApplication.kt`
- Triggers: App start
- Responsibilities: PostHog initialization, AnalyticsManager setup

## Error Handling

**Strategy:** Exception propagation with specific error types

**Patterns:**
- Custom exceptions: `OfflineException`, `SpotmateQueuedException`
- `Throwable.isOffline()` extension for network detection
- Retry logic in `MusicService.retryWithBackoff()`
- Graceful degradation: Online → offline recommendations

## Cross-Cutting Concerns

**Logging:** `android.util.Log` with class-specific TAG constants

**Validation:** 
- Spotify URL format validation
- Duration matching tolerance (±2-15 seconds)
- Artist matching with Levenshtein distance

**Authentication:**
- OAuth token refresh with mutex (thread-safe)
- 5-minute buffer before token expiry

---

*Architecture analysis: 2026-04-16*
