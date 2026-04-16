# JUKES Codebase Map

**Analysis Date:** 2026-04-16

**Project:** JUKE Music Player - Android Music Streaming App
**Version:** 2.3.1-beta-unreleased

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Technology Stack](#2-technology-stack)
3. [Architecture Overview](#3-architecture-overview)
4. [Project Structure](#4-project-structure)
5. [Entry Points](#5-entry-points)
6. [Key Modules](#6-key-modules)
7. [Configuration](#7-configuration)
8. [Dependencies & Integrations](#8-dependencies--integrations)
9. [Code Patterns & Conventions](#9-code-patterns--conventions)
10. [Data Flow](#10-data-flow)
11. [Testing Strategy](#11-testing-strategy)
12. [Build Configuration](#12-build-configuration)

---

## 1. Project Overview

**JUKE** is a feature-rich Android music player that integrates with Spotify's search API and YouTube Music's recommendation engine to provide:
- Smart music downloads with album art and lyrics
- AI-powered recommendation queue generation
- Offline playback support
- Instant streaming with background download
- Playlist import from Spotify

**Key Files:**
- `README.md` - Project documentation
- `CHANGELOG.md` - Version history
- `LICENSE` - AGPL v3 license

---

## 2. Technology Stack

### Languages & Runtimes

| Language | Version | Purpose |
|---------|--------|---------|
| **Kotlin** | 2.0.21 | Primary development language |
| **Java** | 11 | JVM target compatibility |

### Build System

| Tool | Version | Purpose |
|------|---------|---------|
| **Gradle** | Foojay Resolver 1.0.0 | Build automation |
| **Android Gradle Plugin (AGP)** | 8.13.1 | Android build integration |
| **Kotlin Gradle Plugin** | 2.0.21 | Kotlin compilation |
| **KSP** | 2.0.21-1.0.28 | Kotlin Symbol Processing (Room) |

### Core Android SDK

| SDK Component | Version |
|--------------|---------|
| **compileSdk** | 36 |
| **targetSdk** | 35 |
| **minSdk** | 26 (Android 8.0) |

### UI Framework

| Library | Version | Purpose |
|---------|---------|---------|
| **Jetpack Compose BOM** | 2024.12.01 | UI toolkit |
| **Compose Material3** | Latest via BOM | Material Design 3 |
| **Compose Navigation** | 2.8.5 | Type-safe navigation |
| **Coil** | 2.7.0 | Image loading/caching |
| **Palette** | 1.0.0 | Album color extraction |

### Networking

| Library | Version | Purpose |
|---------|---------|---------|
| **Ktor Client** | 3.0.0 | HTTP client |
| **Kotlinx Serialization** | 1.6.3 | JSON parsing |
| **Gson** | 2.10.1 | JSON parsing |

### Data Layer

| Library | Version | Purpose |
|---------|---------|---------|
| **Room Database** | 2.6.1 | SQLite ORM |
| **Kotlinx Coroutines** | 1.8.1 | Async operations |

### Media Playback

| Library | Version | Purpose |
|---------|---------|---------|
| **Media3 ExoPlayer** | 1.5.0 | Audio playback |
| **Media3 Session** | 1.5.0 | Media controls/service |
| **Media3 UI** | 1.5.0 | Media controls UI |

### Analytics

| Library | Version | Purpose |
|---------|---------|---------|
| **PostHog Android** | 3.40.2 | Analytics tracking |

### Package Manager

- **Gradle Wrapper**: Included with `gradlew` scripts
- **Version Catalog**: `gradle/libs.versions.toml`

---

## 3. Architecture Overview

### Pattern: MVVM + Clean Architecture + Repository Pattern

```
┌─────────────────────────────────────────────────────────────────┐
│                         UI Layer (Compose)                      │
│  HomeScreen │ SearchScreen │ LibraryScreen │ PlayerScreen       │
│                    ↓              ↑                             │
│              Composable UI ←── ViewModel ←── StateFlow          │
└───────────────────────┬─────────────────────────────────────────┘
                        │
┌───────────────────────┴─────────────────────────────────────────┐
│                    ViewModel Layer (StateFlow)                  │
│  MusicViewModel │ SearchViewModel │ LibraryViewModel │ PlayerVM │
└───────────────────────┬─────────────────────────────────────────┘
                        │
        ┌───────────────┼───────────────┐
        ↓               ↓               ↓
┌──────────────┐ ┌─────────────┐ ┌──────────────┐
│   Services   │ │ Repository  │ │   Network    │
│              │ │   Layer     │ │   Layer      │
│ PlaybackMgr  │ │  (DAOs)     │ │ SpotifyApi   │
│ QueueManager │ │  Room DB    │ │ Recommender  │
│ MusicService │ │             │ │ ApiClient    │
│ PlaybackSvc  │ │             │ │              │
└──────────────┘ └─────────────┘ └──────────────┘
```

### Layer Responsibilities

| Layer | Location | Responsibilities |
|-------|----------|------------------|
| **UI** | `ui/screens/`, `ui/components/` | Compose UI, user interactions |
| **ViewModel** | `viewmodels/` | State management, business logic |
| **Service** | `services/` | Playback, downloads, queue management |
| **Data/Repository** | `database/` | Room DAOs, data access |
| **Network** | `network/` | API clients, HTTP requests |
| **Models** | `models/` | Data classes, DTOs |

---

## 4. Project Structure

```
JUKES/
├── app/                          # Main application module
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/juke/
│   │   │   │   ├── MainActivity.kt           # Entry point
│   │   │   │   ├── JukeApplication.kt         # Application class
│   │   │   │   ├── database/                  # Room DB layer
│   │   │   │   │   ├── MusicDatabase.kt       # Database config
│   │   │   │   │   ├── PlaylistEntities.kt    # Playlist DAOs
│   │   │   │   │   └── (implicit TrackDao)  # Track DAO in MusicDatabase
│   │   │   │   ├── models/                    # Data models
│   │   │   │   │   ├── Track.kt              # Core track model
│   │   │   │   │   ├── SpotifyModels.kt      # Spotify API DTOs
│   │   │   │   │   └── GithubRelease.kt      # Update model
│   │   │   │   ├── network/                   # API clients
│   │   │   │   │   ├── ApiClient.kt          # Ktor HTTP client
│   │   │   │   │   ├── SpotifyApi.kt          # Spotify Web API
│   │   │   │   │   └── RecommenderApi.kt     # YouTube Music API
│   │   │   │   ├── services/                  # Business services
│   │   │   │   │   ├── PlaybackService.kt    # Media3 service
│   │   │   │   │   ├── PlaybackManager.kt   # Player controller
│   │   │   │   │   ├── QueueManager.kt     # Smart queue/recommendations
│   │   │   │   │   ├── MusicService.kt     # Download & indexing
│   │   │   │   │   ├── AudioEffectController.kt # Equalizer
│   │   │   │   │   └── UpdateManager.kt     # App updates
│   │   │   │   ├── viewmodels/               # ViewModels
│   │   │   │   │   ├── MusicViewModel.kt    # Main playback state
│   │   │   │   │   ├── SearchViewModel.kt   # Search & import
│   │   │   │   │   ├── LibraryViewModel.kt  # Library management
│   │   │   │   │   ├── PlayerViewModel.kt   # Player state
│   │   │   │   │   ├── HomeViewModel.kt     # Home screen data
│   │   │   │   │   ├── AlbumDetailViewModel.kt
│   │   │   │   │   └── PlaylistDetailViewModel.kt
│   │   │   │   ├── ui/                       # UI layer
│   │   │   │   │   ├── screens/              # Screen composables
│   │   │   │   │   │   ├── HomeScreen.kt
│   │   │   │   │   │   ├── SearchScreen.kt
│   │   │   │   │   │   ├── LibraryScreen.kt
│   │   │   │   │   │   ├── PlayerScreen.kt
│   │   │   │   │   │   ├── AlbumDetailScreen.kt
│   │   │   │   │   │   ├── ArtistDetailScreen.kt
│   │   │   │   │   │   ├── PlaylistDetailScreen.kt
│   │   │   │   │   │   ├── AudioSettingsScreen.kt
│   │   │   │   │   │   └── PurgeSelectionScreen.kt
│   │   │   │   │   ├── components/          # Reusable UI
│   │   │   │   │   │   ├── MiniPlayer.kt
│   │   │   │   │   │   ├── TrackCard.kt
│   │   │   │   │   │   ├── AlbumCard.kt
│   │   │   │   │   │   ├── ArtistCard.kt
│   │   │   │   │   │   ├── PlaylistCard.kt
│   │   │   │   │   │   ├── player/         # Player components
│   │   │   │   │   │   │   ├── PlayerControls.kt
│   │   │   │   │   │   │   ├── PlayerArtwork.kt
│   │   │   │   │   │   │   ├── PlayerProgress.kt
│   │   │   │   │   │   │   ├── LyricsOverlay.kt
│   │   │   │   │   │   │   └── QueueSheet.kt
│   │   │   │   │   │   └── (many more components...)
│   │   │   │   │   └── theme/               # Theming
│   │   │   │   │       ├── Theme.kt         # Material3 theme
│   │   │   │   │       ├── Color.kt         # Color definitions
│   │   │   │   │       └── Type.kt         # Typography
│   │   │   │   ├── analytics/               # Analytics
│   │   │   │   │   ├── AnalyticsManager.kt  # PostHog wrapper
│   │   │   │   │   ├── AnalyticsEvent.kt    # Event definitions
│   │   │   │   │   ├── PlayerAnalyticsHelper.kt
│   │   │   │   │   └── UsageExamples.kt
│   │   │   │   └── utils/                    # Utilities
│   │   │   │       ├── FastDownloader.kt    # Multi-threaded download
│   │   │   │       ├── ArtistUtils.kt      # Artist matching
│   │   │   │       ├── BlacklistManager.kt # Artist blacklist
│   │   │   │       ├── DatabaseMigrationHelper.kt
│   │   │   │       ├── HapticHelper.kt
│   │   │   │       └── LyricsRomanizer.kt
│   │   │   ├── res/                         # Android resources
│   │   │   │   ├── drawable/               # Icons, images
│   │   │   │   ├── values/                 # Strings, colors, themes
│   │   │   │   ├── xml/                    # XML configs
│   │   │   │   └── mipmap-*/              # App icons
│   │   │   └── AndroidManifest.xml
│   │   ├── test/                            # Unit tests
│   │   └── androidTest/                     # Instrumented tests
│   ├── build.gradle.kts                     # App build config
│   └── proguard-rules.pro                   # ProGuard/R8 rules
├── gradle/
│   ├── wrapper/                             # Gradle wrapper
│   └── libs.versions.toml                  # Version catalog
├── build.gradle.kts                         # Root build config
├── settings.gradle.kts                       # Project settings
├── gradle.properties                        # Gradle properties
├── local.properties                         # Local SDK path + secrets
└── gradlew/gradlew.bat                     # Build scripts
```

### Directory Purposes

| Directory | Purpose |
|-----------|---------|
| `app/src/main/java/com/example/juke/` | All Kotlin source code |
| `app/src/main/res/` | Android resources (layouts, drawables, values) |
| `app/src/test/` | Unit tests (JVM) |
| `app/src/androidTest/` | Instrumented tests (Android) |
| `gradle/` | Gradle configuration and wrapper |
| `docs/` | Project documentation |
| `spotdown-kv-worker/` | Cloudflare Workers script for KV storage |

---

## 5. Entry Points

### Application Entry

**File:** `app/src/main/java/com/example/juke/JukeApplication.kt`

```kotlin
class JukeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initializes PostHog analytics
        // Initializes AnalyticsManager
    }
}
```

**Manifest Declaration:**
```xml
<application android:name=".JukeApplication" ...>
```

### Main Activity

**File:** `app/src/main/java/com/example/juke/MainActivity.kt`

**Key Responsibilities:**
- Permission handling (READ_PHONE_STATE)
- Navigation setup with `NavigationCompose`
- Bottom navigation bar (Home, Search, Library)
- Update check dialog
- Player modal overlay
- Deep link handling (`juke://` scheme)

**Navigation Routes:**
- `home` - Home screen
- `search` - Search screen
- `library` - Library screen
- `settings` - Audio settings
- `settings/purge` - Track purge selection
- `artist/{artistId}` - Artist detail
- `playlist/{playlistId}` - Playlist detail
- `album/{albumId}` - Album detail

### Playback Service

**File:** `app/src/main/java/com/example/juke/services/PlaybackService.kt`

**Declaration in Manifest:**
```xml
<service android:name=".services.PlaybackService" 
         android:exported="true" 
         android:foregroundServiceType="mediaPlayback">
    <intent-filter>
        <action name="androidx.media3.session.MediaLibraryService" />
        <action name="android.media.browse.MediaBrowserService" />
    </intent-filter>
</service>
```

**Key Features:**
- Media3 `MediaLibraryService` implementation
- ExoPlayer integration
- Media session and notification controls
- Audio focus handling
- Call state detection
- Android Auto support

---

## 6. Key Modules

### Database Layer

**File:** `app/src/main/java/com/example/juke/database/MusicDatabase.kt`

| Entity | Purpose |
|--------|---------|
| `TrackEntity` | Stores track metadata (title, artist, duration, local_uri, etc.) |
| `PlaylistEntity` | Stores playlist metadata |
| `PlaylistTrackEntity` | Many-to-many relationship between playlists and tracks |

**Database Version:** 8

**Migrations:**
| Version | Changes |
|---------|---------|
| 1→2 | Schema changes |
| 2→3 | Added `notification_thumbnail_uri` |
| 3→4 | Removed thumbnail, recreated tracks table, added playlists table |
| 4→5 | Added `downloaded_at` column |
| 5→6 | Added Spotify IDs (`spotify_id`, `album_spotify_id`, `artist_spotify_ids`) |
| 6→7 | Added `is_stream` column |
| 7→8 | Added `lyrics_offset_ms` column |

**DAOs:**
- `TrackDao` - Track CRUD operations, search, favorites, recently played, most played
- `PlaylistDao` - Playlist CRUD, track relationships

### Network Layer

**File:** `app/src/main/java/com/example/juke/network/SpotifyApi.kt`

**Features:**
- OAuth 2.0 Client Credentials flow (automatic token refresh)
- Search (tracks, artists, albums, playlists)
- Get track/artist/album/playlist details
- Playlist track pagination
- **Download Sources:**
  - Spotmate (`spotmate.online`) - Primary
  - Gamepvz (`gamepvz.com`) - Fallback
- Lyrics fetching from LRCLib

**File:** `app/src/main/java/com/example/juke/network/RecommenderApi.kt`

**Features:**
- YouTube Music video search via `mp3juice3.ninja`
- Radio queue generation (YouTube Music API)
- Spotify validation of recommendations
- Artist matching with Levenshtein distance
- Spam/cover filtering

**File:** `app/src/main/java/com/example/juke/network/ApiClient.kt`

**Configuration:**
- Engine: Ktor Android
- Timeouts: 120s request, 30s connect, 60s socket
- JSON serialization via kotlinx.serialization
- Logging enabled

### Service Layer

**File:** `app/src/main/java/com/example/juke/services/MusicService.kt`

**Key Methods:**
- `smartDownloadAndIndex(song)` - Download and save track
- `streamTrack(song)` - Stream to local file with caching
- `promoteStreamToDownload(track)` - Convert stream to permanent download
- `deleteTrackAndFiles(track)` - Delete track and associated files
- `cleanupOrphanedCacheFiles()` - Remove unreferenced cache
- `evictStreamCache()` - LRU eviction for stream files

**File:** `app/src/main/java/com/example/juke/services/QueueManager.kt`

**Key Features:**
- Smart recommendation fetching (YouTube Music → Spotify validation)
- Download queue management (max 6 concurrent)
- Stream mode toggle
- Artist blacklist filtering
- Offline fallback recommendations
- Pre-fetching upcoming tracks

**File:** `app/src/main/java/com/example/juke/services/PlaybackService.kt`

**Key Components:**
- `ExoPlayer` with `CacheDataSource` (256MB cache)
- `MediaLibrarySession` for Android Auto
- `StreamCacheManager` - ExoPlayer cache singleton
- Audio focus and call state handling
- Sleep timer support

### ViewModel Layer

**File:** `app/src/main/java/com/example/juke/viewmodels/MusicViewModel.kt`

**State (`MusicUiState`):**
```kotlin
data class MusicUiState(
    val currentTrack: Track? = null,
    val queue: List<Track> = emptyList(),
    val queueIndex: Int = -1,
    val isPlaying: Boolean = false,
    val position: Long = 0,
    val duration: Long = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
    val downloadQueue: List<DownloadItem> = emptyList(),
    val currentDownload: DownloadItem? = null,
    val isQueueOperationInProgress: Boolean = false,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val extractedColors: ExtractedColors? = null
)
```

**Key Methods:**
- `playTrack(track)` - Play a track
- `playInstant(song)` - Stream with background download
- `addNext(track)` - Add to queue after current
- `setQueue(tracks, startIndex)` - Set entire queue
- `queueSpotifyTrackNext(track)` - Queue Spotify track
- `downloadSong(song)` - Download to library

---

## 7. Configuration

### Gradle Configuration

**Root:** `build.gradle.kts`
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
```

**App:** `app/build.gradle.kts`
```kotlin
android {
    namespace = "com.example.juke"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.example.juke"
        minSdk = 26
        targetSdk = 36
        versionCode = 15
        versionName = "2.3.1-beta-unreleased"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}
```

### Environment Configuration

**File:** `local.properties` (not committed to git)

**Required Variables:**
```properties
sdk.dir=/path/to/android/sdk
SPOTIFY_CLIENT_ID=your_client_id
SPOTIFY_CLIENT_SECRET=your_client_secret
```

**Build Config Usage:**
```kotlin
BuildConfig.SPOTIFY_CLIENT_ID
BuildConfig.SPOTIFY_CLIENT_SECRET
```

### ProGuard/R8 Configuration

**File:** `app/proguard-rules.pro`

**Key Rules:**
- `-assumenosideeffects` for Log.d/v/i/w (removes logs in release)
- Keep model classes: `-keep class com.example.juke.models.**`
- Keep database classes: `-keep class com.example.juke.database.**`
- Keep network classes: `-keep class com.example.juke.network.**`
- Gson rules for JSON parsing

### Shared Preferences

| File | Purpose |
|------|---------|
| `playback_state_prefs` | Playback state, queue |
| `audio_effects_prefs` | Equalizer settings, skip silence |
| `music_settings_prefs` | Stream mode, recommendation count |
| `analytics_prefs` | User ID, pending events |
| `blacklist_prefs` | Blacklisted artists |

---

## 8. Dependencies & Integrations

### External APIs

| Service | Purpose | Integration |
|---------|---------|-------------|
| **Spotify Web API** | Search, metadata | `SpotifyApi.kt` - OAuth 2.0 |
| **YouTube Music API** | Recommendations | `RecommenderApi.kt` - HTTP POST |
| **Spotmate** | MP3 download | `SpotifyApi.kt` - Web scraping |
| **Gamepvz** | MP3 download | `SpotifyApi.kt` - Web scraping |
| **LRCLib** | Lyrics fetching | `SpotifyApi.kt` - HTTP GET |
| **PostHog** | Analytics | `AnalyticsManager.kt` - SDK |

### Storage

| Type | Implementation |
|------|-----------------|
| **Local Database** | Room SQLite (`music_database`) |
| **Audio Files** | App internal storage (`filesDir/music/`) |
| **Stream Cache** | ExoPlayer cache (`cacheDir/stream_cache/`) |
| **Thumbnails** | App internal storage (`filesDir/music/`) |
| **Stream Files** | App internal storage (`filesDir/stream_files/`) |

### Platform Services

| Service | Purpose |
|---------|---------|
| **MediaSession** | System media controls |
| **Notification** | Playback controls |
| **AudioFocus** | Audio ducking, interruption handling |
| **Telephony** | Call state detection |
| **Connectivity** | Network monitoring |

---

## 9. Code Patterns & Conventions

### Naming Conventions

| Type | Convention | Example |
|------|------------|---------|
| **Files** | PascalCase | `MusicService.kt`, `TrackCard.kt` |
| **Functions** | camelCase | `playTrack()`, `addToQueue()` |
| **Variables** | camelCase | `musicViewModel`, `currentTrack` |
| **Constants** | SCREAMING_SNAKE_CASE | `MAX_CACHE_BYTES`, `TAG` |
| **Classes** | PascalCase | `MusicViewModel`, `TrackEntity` |
| **Packages** | lowercase | `com.example.juke.database` |

### Kotlin Patterns Used

| Pattern | Usage |
|---------|-------|
| **Object Declarations** | `SpotifyApi`, `RecommenderApi`, `ApiClient` |
| **Companion Object** | `MusicDatabase.companion`, `PlaybackManager.companion` |
| **Extension Functions** | `TrackEntity.toTrack()`, `Track.toEntity()` |
| **Sealed Classes** | `Screen` (navigation routes) |
| **Data Classes** | All models and state classes |
| **Flow/StateFlow** | Reactive state management |
| **Coroutines** | `viewModelScope.launch`, `Dispatchers.IO` |
| **Type Aliases** | `OfflineException`, `SpotmateQueuedException` |

### Import Organization (typical)

```kotlin
// Android framework
import android.content.Context
import android.util.Log

// Kotlin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

// AndroidX
import androidx.lifecycle.ViewModel
import androidx.compose.material3.*

// App local
import com.example.juke.database.MusicDatabase
import com.example.juke.models.Track
import com.example.juke.network.SpotifyApi
```

### Error Handling

| Pattern | Usage |
|---------|-------|
| **Custom Exceptions** | `OfflineException`, `SpotmateQueuedException` |
| **Result Types** | Exception propagation with `try/catch` |
| **Offline Detection** | `Throwable.isOffline()` extension |
| **Retry Logic** | `retryWithBackoff()` in `MusicService` |

### State Management

```kotlin
// ViewModel state
private val _uiState = MutableStateFlow(MusicUiState())
val uiState: StateFlow<MusicUiState> = _uiState.asStateFlow()

// Update state
_uiState.update { it.copy(currentTrack = track) }

// Observe in UI
val uiState by musicViewModel.uiState.collectAsState()
```

---

## 10. Data Flow

### Play Track Flow

```
01. User taps track in UI
                ↓
02. Screen calls MusicViewModel.playTrack(track)
                ↓
03. MusicViewModel updates UI state
                ↓
04. PlaybackManager.setQueue(tracks, index)
                ↓
05. PlaybackService receives command via MediaController
                ↓
06. ExoPlayer loads MediaItem
                ↓
07. Notification updates with track info
                ↓
08. QueueManager.initializeQueue() triggered
                ↓
09. QueueManager checks queue size (<=2 triggers recommendations)
                ↓
10. RecommenderApi fetches YouTube Music radio
                ↓
11. SpotifyApi validates with Spotify
                ↓
12. Tracks added to queue, downloads start
```

### Download Flow

```
1. User requests download
   ↓
2. MusicService.smartDownloadAndIndex(song)
   ↓
3. Try Spotmate → fallback to Gamepvz
   ↓
4. FastDownloader.downloadSegmented() - multi-threaded
   ↓
5. Verify MP3 header, check duration
   ↓
6. SpotifyApi.searchLyrics() - fetch lyrics
   ↓
7. RecommenderApi.getBestVideoMatch() - get YT video ID
   ↓
8. Download thumbnail image
   ↓
9. TrackDao.insertTrack() - save to Room DB
   ↓
10. UI updates via Flow observation
```

### Recommendation System Flow

```
1. QueueManager triggered when queue size <= 2
   ↓
2. Check stream mode setting
   ↓
3. Online Path:
   ├─ Get YouTube Music video ID (cached or fresh)
   ├─ fetchFullRadioQueue() - 50 recommendations
   ├─ BlacklistManager filters blocked artists
   ├─ SpotifyApi.validateAndFilterWithSpotify()
   │   └─ For each: calculate similarity scores
   └─ Filter duplicates, add to queue
   ↓
4. Offline Path (fallback):
   ├─ Get all downloaded tracks from Room
   ├─ Score by: same artist, same album, favorites, play count
   ├─ Penalize: recently played, blacklisted artists
   └─ Add top scoring tracks to queue
   ↓
5. processNextDownload() - download next 6 recommendations
```

---

## 11. Testing Strategy

### Test Frameworks

| Type | Framework | Location |
|------|-----------|----------|
| **Unit Tests** | JUnit 4 | `app/src/test/` |
| **Instrumented Tests** | AndroidJUnitRunner | `app/src/androidTest/` |

### Test Configuration

**File:** `app/build.gradle.kts`
```kotlin
testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

testImplementation(libs.junit)
androidTestImplementation(libs.androidx.junit)
androidTestImplementation(libs.androidx.espresso.core)
androidTestImplementation(platform(libs.androidx.compose.bom))
androidTestImplementation(libs.androidx.compose.ui.test.junit4)
```

### Running Tests

```bash
# Unit tests (JVM)
./gradlew test

# Instrumented tests (device/emulator)
./gradlew connectedAndroidTest

# Specific test class
./gradlew test --tests "com.example.juke.ExampleUnitTest"

# With coverage
./gradlew testDebugUnitTestCoverage
```

### Current Test Status

**File:** `app/src/test/java/com/example/juke/ExampleUnitTest.kt`

```kotlin
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }
}
```

**Note:** The codebase has minimal unit tests. The example test is a placeholder. No instrumented tests exist yet.

### Testing Recommendations

| Area | Test Type | Suggested Coverage |
|------|-----------|-------------------|
| **MusicService** | Unit | Download retry, stream cache, LRU eviction |
| **QueueManager** | Unit | Recommendation scoring, blacklist filtering |
| **SpotifyApi** | Mock/Unit | Token refresh, search parsing |
| **TrackDao** | Instrumented | DB operations, migrations |
| **UI Screens** | Compose UI tests | Navigation, state observation |

---

## 12. Build Configuration

### Gradle Properties

**File:** `gradle.properties`

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8 -XX:+UseParallelGC
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
org.gradle.configuration-cache=true
```

### Version Catalog

**File:** `gradle/libs.versions.toml`

```toml
[versions]
agp = "8.13.1"
kotlin = "2.0.21"
coreKtx = "1.15.0"
composeBom = "2024.12.01"
coroutines = "1.8.1"
serialization = "1.6.3"
ktor = "3.0.0"
room = "2.6.1"
media3 = "1.5.0"
coil = "2.7.0"
navigation = "2.8.5"
```

### Build Variants

| Variant | Minify | ProGuard | Signing |
|---------|--------|----------|---------|
| **Debug** | No | No | Debug keystore |
| **Release** | Yes | Yes | Debug keystore |

### Build Commands

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Install to device
./gradlew installDebug

# Clean build
./gradlew clean

# Run all checks
./gradlew check
```

---

## Appendix: Key File Reference

| File Path | Purpose | Lines |
|-----------|---------|-------|
| `app/src/main/java/com/example/juke/MainActivity.kt` | Entry point, navigation | 571 |
| `app/src/main/java/com/example/juke/viewmodels/MusicViewModel.kt` | Playback state | 1400+ |
| `app/src/main/java/com/example/juke/services/MusicService.kt` | Downloads | 980 |
| `app/src/main/java/com/example/juke/services/QueueManager.kt` | Recommendations | 1136 |
| `app/src/main/java/com/example/juke/services/PlaybackService.kt` | Media3 service | 1068 |
| `app/src/main/java/com/example/juke/network/SpotifyApi.kt` | Spotify API | 1239 |
| `app/src/main/java/com/example/juke/network/RecommenderApi.kt` | YT Music API | 931 |
| `app/src/main/java/com/example/juke/database/MusicDatabase.kt` | Room DB | 442 |

---

*Codebase analysis completed: 2026-04-16*
