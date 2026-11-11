# Architecture

**Analysis Date:** 2026-05-19

## Pattern Overview

**Overall:** MVVM + Clean Architecture + Repository Pattern with Media3 Integration

**Key Characteristics:**
- UI layer uses Jetpack Compose with unidirectional data flow
- ViewModels manage UI state via StateFlow and expose UI events
- Services handle business logic (playback, downloads, recommendations, audio effects)
- Data layer uses Room DAOs for database access with coroutine support
- Network layer uses Ktor client for API calls (Spotify, Recommender)
- Media3 (ExoPlayer) handles audio playback with service-based architecture
- Background work managed via coroutine scopes and ViewModel lifecycle

## Layers

**UI Layer (Compose):**
- Purpose: Display data and capture user input with reactive UI
- Location: `app/src/main/java/com/example/juke/ui/`
- Contains: Screens (HomeScreen, SearchScreen, etc.), components (MiniPlayer, etc.), theme (JUKETheme)
- Depends on: ViewModels (via viewModel() delegate), Services (for direct calls like analytics)
- Used by: MainActivity (sets content via setContent)

**ViewModel Layer:**
- Purpose: Manage UI state, business logic coordination, survive configuration changes
- Location: `app/src/main/java/com/example/juke/viewmodels/`
- Contains: MusicViewModel (central playback state), SearchViewModel, LibraryViewModel, AlbumDetailViewModel, PlaylistDetailViewModel, PlayerViewModel, HomeViewModel
- Depends on: Services (MusicService, PlaybackManager, QueueManager), DAOs, API clients (SpotifyApi, RecommenderApi), Models
- Used by: UI layer (Compose screens via viewModel() delegate), Services (for callbacks)

**Service Layer:**
- Purpose: Core business logic (playback, downloads, queue management, audio effects, updates)
- Location: `app/src/main/java/com/example/juke/services/`
- Contains: 
  - PlaybackService (Media3 service for audio playback, foreground service, media session)
  - PlaybackManager (singleton for ExoPlayer control via MediaController)
  - MusicService (download and stream management with Spotmate/Gamepvz fallback)
  - QueueManager (singleton for recommendation engine and queue management)
  - AudioEffectController (bass boost, virtualizer, etc. via audio session ID)
  - UpdateManager (GitHub release checking and APK download/update)
- Depends on: Database (Room), Network (Ktor clients), Models, Utils (FastDownloader, ArtistUtils)
- Used by: ViewModels (primary consumers), PlaybackService (for callbacks like favouriteChangedFlow)

**Data Layer (Room):**
- Purpose: Local SQLite database access with type converters and migrations
- Location: `app/src/main/java/com/example/juke/database/`
- Contains: MusicDatabase (Room database with 9 migrations), TrackDao, PlaylistDao, TrackEntity/PlaylistEntity models
- Depends on: Room runtime, TypeConverters (for List<String> storage)
- Used by: Services (for persistence), ViewModels (for UI state synchronization)

**Network Layer:**
- Purpose: HTTP API communication with Spotify and recommendation services
- Location: `app/src/main/java/com/example/juke/network/`
- Contains: ApiClient (singleton Ktor HttpClient config), SpotifyApi (Spotify Web API wrapper), RecommenderApi (YouTube Music-based recommendations)
- Depends on: Ktor client (OkHttp engine, JSON serialization, timeout/logging plugins)
- Used by: Services (MusicService for downloads/streams, QueueManager for recommendations)

**Utils Layer:**
- Purpose: Cross-cutting utility functions and helpers
- Location: `app/src/main/java/com/example/juke/utils/`
- Contains: FastDownloader (multi-threaded download), ArtistUtils (artist name matching), BlacklistManager, DatabaseMigrationHelper, HapticHelper, LyricsRomanizer
- Depends on: Kotlin stdlib, Android SDK
- Used by: Services (primary consumers), ViewModels (occasionally)

**Analytics Layer:**
- Purpose: User behavior tracking and analytics
- Location: `app/src/main/java/com/example/juke/analytics/`
- Contains: AnalyticsManager (PostHog integration), AnalyticsEvent (event definitions), PlayerAnalyticsHelper (playback-specific tracking), UsageExamples
- Depends on: PostHog Android SDK
- Used by: MainActivity (app lifecycle), PlaybackService (playback events), Services (feature usage)

## Data Flow

**Play Track Flow:**
1. User taps track in UI (HomeScreen/SearchScreen/etc.)
2. Screen calls `musicViewModel.playTrack(track)` or `queueSpotifyTrackNext()`
3. MusicViewModel updates `_uiState` via `MutableStateFlow.update {}`
4. MusicViewModel calls `playbackManager.setQueue(listOf(track), 0)`
5. PlaybackService receives command via MediaController (from PlaybackManager)
6. ExoPlayer loads and plays MediaItem (local file or stream)
7. Notification updates with track info via MediaSession callback
8. PlaybackService detects 50% playback threshold and increments play count in DB
9. QueueManager triggers for recommendations when queue ≤2 tracks
10. UI observes musicViewModel.uiState and recomposes with new state

**Download Flow:**
1. User requests download (via search/artist/album screens)
2. MusicViewModel calls `musicService.smartDownloadAndIndex(song)`
3. MusicService tries Spotmate (faster) → fallback to Gamepvz
4. Multi-threaded download via FastDownloader service
5. MP3 header and duration verification
6. Lyrics fetched from LRCLib (synced + plain)
7. Thumbnail downloaded and saved locally
8. Track saved to Room database via TrackDao.insertTrack()
9. UI updates via Flow observation of database queries (getAllTracksFlow, etc.)

**Recommendation Flow (Online):**
1. QueueManager detects queue size ≤2 via PlaybackManager.currentQueueFlow
2. QueueManager.fetchAndQueueRecommendations() called
3. RecommenderApi gets YouTube Music radio queue for seed track
4. Each recommended track validated via SpotifyApi (search + metadata match)
5. Validated tracks filtered by artist blacklist (BlacklistManager)
6. New tracks added to main queue via QueueManager.addToQueue()
7. PlaybackManager.updateHistory() called for context preservation
8. Downloads auto-start for new tracks (max 6 concurrent via MusicService)

**Recommendation Flow (Offline Fallback):**
1. When online recommendations fail, QueueManager scores local library
2. Scoring factors: play count, recent play, favorite status, duration
3. Artist diversity enforced via blacklist checks
4. Top-scoring tracks added to queue
5. No downloads triggered (assumes tracks already available locally)

**State Management:**
- ViewModels expose `StateFlow` for UI state (immutable data classes)
- `MutableStateFlow.update {}` used for immutable state updates (thread-safe)
- Services use `StateFlow`/`SharedFlow` internally for event broadcasting
- UI observes via `collectAsState()` with automatic lifecycle handling
- Complex state split across multiple ViewModels when needed (tab-specific state)

## Key Abstractions

**MusicViewModel:**
- Purpose: Central state holder for playback, downloads, queue, and UI coordination
- Examples: `MusicUiState` (current track, queue, playback state, download queue, extracted colors), `DownloadItem`, `DownloadStatus`
- Pattern: Single source of truth for playback state with UI event handling
- Location: `app/src/main/java/com/example/juke/viewmodels/MusicViewModel.kt`

**PlaybackManager (Singleton):**
- Purpose: Control ExoPlayer via MediaController, abstract service communication
- Examples: `playTrack()`, `addToQueue()`, `seekTo()`, `toggleShuffle()`, `getCurrentPosition()`
- Pattern: Singleton with companion object, delegates to PlaybackService via MediaController
- Location: `app/src/main/java/com/example/juke/services/PlaybackManager.kt`

**QueueManager (Singleton):**
- Purpose: Smart recommendation engine and queue management logic
- Examples: `initializeQueue()`, `fetchAndQueueRecommendations()`, `replaceTrackInQueue()`
- Pattern: Singleton managing background coroutine scope for recommendation work
- Location: `app/src/main/java/com/example/juke/services/QueueManager.kt`

**MusicService:**
- Purpose: Download and stream management with fallback mechanisms
- Examples: `smartDownloadAndIndex()`, `streamTrack()`, `promoteStreamToDownload()`
- Pattern: Context-dependent service class with lazy initialization of dependencies
- Location: `app/src/main/java/com/example/juke/services/MusicService.kt`

**SpotifyApi (Object):**
- Purpose: Spotify Web API wrapper with authentication and retry logic
- Examples: `search()`, `getTrack()`, `getPlaylist()`, `getArtist()`, `getAlbum()`
- Pattern: Object declaration with Ktor client, token refresh mutex
- Location: `app/src/main/java/com/example/juke/network/SpotifyApi.kt`

**RecommenderApi (Object):**
- Purpose: YouTube Music-based recommendation engine
- Examples: `getRadioQueue()`, `getRelatedContent()`
- Pattern: Object declaration with Ktor client, YouTube Music web scraping
- Location: `app/src/main/java/com/example/juke/network/RecommenderApi.kt`

**PlaybackService (Media3 Service):**
- Purpose: Foreground service handling audio playback, media session, notifications
- Examples: ExoPlayer setup, Media3 session creation, audio focus handling, call state handling
- Pattern: Android Service extending MediaLibraryService with Media3 (ExoPlayer)
- Location: `app/src/main/java/com/example/juke/services/PlaybackService.kt`

## Entry Points

**MainActivity:**
- Location: `app/src/main/java/com/example/juke/MainActivity.kt`
- Triggers: App launch (LAUNCHER intent), intent handling (open_player), newIntent
- Responsibilities: Navigation (NavHost), permission handling (READ_PHONE_STATE), update checks (UpdateManager), UI theming, analytics tracking

**PlaybackService (Media3):**
- Location: `app/src/main/java/com/example/juke/services/PlaybackService.kt`
- Triggers: Media button presses, notification actions, Android Auto browse/play commands
- Responsibilities: Foreground service management, ExoPlayer preparation microbiome, media session callbacks, notification updates, audio focus handling, call state interruption handling

**JukeApplication:**
- Location: `app/src/main/java/com/example/juke/JukeApplication.kt`
- Triggers: App start (onCreate)
- Responsibilities: PostHog initialization (AnalyticsManager), Coil ImageLoaderFactory setup (memory/disk cache configuration)

**AndroidManifest.xml:**
- Location: `app/src/main/AndroidManifest.xml`
- Declares: MainActivity (LAUNCHER), PlaybackService (foreground service, media3 session), JukeApplication (application), receivers (BootCompleted for startup analytics), services (foregroundService type for PlaybackService)

## Error Handling

**Strategy:** Exception propagation with specific error types and graceful degradation

**Patterns:**
- Custom exceptions: `OfflineException` (network unavailable), `SpotmateQueuedException` (Spotmate rate limit)
- `Throwable.isOffline()` extension for network detection (checks for common offline exceptions)
- Retry logic in `MusicService.retryWithBackoff()` (exponential backoff with jitter)
- Graceful degradation: Online → offline recommendations, cached data fallback
- User-facing errors: Toast messages (download failures), UI state error fields (MusicUiState.error)
- Logging: All exceptions logged with context via `android.util.Log` (error/warn levels)

## Cross-Cutting Concerns

**Logging:** `android.util.Log` with class-specific TAG constants (typically class simple name)
- Levels: Error (unexpected failures), Warn (recoverable issues), Debug (development tracing), Info (lifecycle events)
- Tag format: Usually the class name (e.g., "MusicViewModel", "PlaybackService")

**Validation:** 
- Spotify URL format validation (regex patterns for track/album/playlist/artist URLs)
- Duration matching tolerance (±2 seconds for exact matches, ±15 seconds for fuzzy matching)
- Artist matching with Levenshtein distance via ArtistUtils.areArtistsEqual() (threshold-based)
- File existence checks before media item creation (createValidatedMediaItem in PlaybackService)
- MP3 header verification after download (FastDownloader validates MPEG frame sync)

**Authentication:**
- OAuth token refresh with mutex (synchronized block on SpotifyApi companion object)
- 5-minute buffer before token expiry (proactive refresh)
- Token persistence in SharedPreferences (encrypted not required as tokens are short-lived)
- Request retry on 401 response (refresh token and retry once)

**Audio Focus:**
- AudioManager.OnAudioFocusChangeListener registered in PlaybackService
- Handles AUDIOFOCUS_LOSS (pause), AUDIOFOCUS_LOSS_TRANSIENT (pause + resume later), AUDIOFOCUS_GAIN (resume)
- Uses AudioFocusRequest.Builder for API 26+, deprecated method for older APIs
- Requests focus when playback starts, abandons when stopped

**Telephony Integration:**
- TelephonyManager.ACTION_PHONE_STATE_CHANGED BroadcastReceiver in PlaybackService
- Handles EXTRA_STATE_RINGING/OFFHOOK (pause + save state), EXTRA_STATE_IDLE (resume if was playing)
- Delayed resume (500ms) to allow app to come to foreground after call
- WasPlayingBeforeCall flag prevents resume if user actively paused before call

**Notification & Media Session:**
- Media3 MediaSession with custom layout (Favorite/Download buttons)
- Notification channel "media_playback" (importance low) for Android 8+
- Custom commands: CUSTOM_COMMAND_TOGGLE_FAVORITE, CUSTOM_COMMAND_DOWNLOAD_TRACK
- Artwork handling via applyArtwork() (resolves http/file/content URIs to artwork)
- Metadata updates on media item transitions (notification stays current with track)

---
*Architecture analysis: 2026-05-19*