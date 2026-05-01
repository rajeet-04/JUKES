# JUKES Codebase Map

**Last Updated:** 2026-05-01

## Overview

JUKES is an Android music streaming application built with Kotlin and Jetpack Compose. It integrates with Spotify Web API for metadata, YouTube Music for recommendations, and multiple sources (Spotmate/Gamepvz) for MP3 downloads. The app features offline playback, lyrics support, playlist management, and analytics via PostHog.

---

## Package Structure

```
app/src/main/java/com/example/juke/
├── JukeApplication.kt              # Application class
├── MainActivity.kt                  # Main activity with Compose UI
│
├── models/                         # Data models
│   ├── Track.kt                    # Main track model, SpotdownSong, LRCLibResult
│   ├── SpotifyModels.kt            # Spotify API response models
│   └── GithubRelease.kt            # GitHub release data
│
├── network/                        # API services
│   ├── ApiClient.kt               # Ktor HTTP client configuration
│   ├── SpotifyApi.kt              # Spotify Web API, OAuth, lyrics search
│   └── RecommenderApi.kt          # YouTube Music recommendations
│
├── services/                       # Background services
│   ├── PlaybackService.kt         # Media3/ExoPlayer service
│   ├── MusicService.kt            # Download and indexing
│   ├── QueueManager.kt            # Queue management & recommendations
│   ├── UpdateManager.kt           # App update checking
│   └── AudioEffectController.kt   # Audio effects (bass boost, etc.)
│
├── viewmodels/                     # MVVM ViewModels
│   ├── MusicViewModel.kt          # Main music state, queue, playback
│   ├── SearchViewModel.kt         # Search functionality
│   ├── HomeViewModel.kt           # Home screen data
│   ├── LibraryViewModel.kt        # Library management
│   ├── PlayerViewModel.kt         # Player state
│   ├── PlaylistDetailViewModel.kt # Playlist details
│   └── AlbumDetailViewModel.kt    # Album details
│
├── ui/                            # UI layer
│   ├── theme/                      # Compose theming
│   │   ├── Theme.kt                # Material3 theme configuration
│   │   ├── Color.kt                # Color constants
│   │   └── Type.kt                 # Typography
│   │
│   ├── screens/                    # Composable screens
│   │   ├── HomeScreen.kt           # Home with recently played, favorites
│   │   ├── SearchScreen.kt         # Search with filters (tracks/artists/playlists albums)
│   │   ├── LibraryScreen.kt        # Library with downloads, playlists, favorites
│   │   ├── PlayerScreen.kt         # Full player with lyrics, queue
│   │   ├── PlaylistDetailScreen.kt # Playlist tracks
│   │   ├── AlbumDetailScreen.kt   # Album tracks
│   │   ├── ArtistDetailScreen.kt  # Artist info, top tracks, albums
│   │   ├── AudioSettingsScreen.kt  # Audio effects, stream mode settings
│   │   └── PurgeSelectionScreen.kt # Bulk delete UI
│   │
│   └── components/                # Reusable UI components
│       ├── player/                   # Player-specific components
│       │   ├── PlayerControls.kt     # Play/pause, skip, shuffle, repeat
│       │   ├── PlayerProgress.kt     # Seek bar with time display
│       │   ├── PlayerArtwork.kt     # Album art with backdrop
│       │   ├── QueueSheet.kt         # Bottom sheet queue
│       │   └── LyricsOverlay.kt     # Synced lyrics display
│       │
│       ├── TrackCard.kt              # Standard track item
│       ├── CompactTrackCard.kt        # Compact track item
│       ├── HeroTrackCard.kt           # Featured track display
│       ├── PlaylistCard.kt            # Playlist item
│       ├── AlbumCard.kt               # Album item
│       ├── ArtistCard.kt              # Artist item
│       ├── LibraryTrackItem.kt        # Library track with swipe actions
│       ├── SearchResultItem.kt        # Search result
│       ├── MiniPlayer.kt              # Mini player at bottom
│       ├── SwipeToAddNextContainer.kt # Swipe to add to queue
│       ├── AddToPlaylistDialog.kt     # Add track to playlist
│       ├── CreatePlaylistDialog.kt    # Create new playlist
│       ├── EditPlaylistDialog.kt       # Edit playlist name
│       ├── DownloadingTrackItem.kt    # Download progress item
│       ├── AudioComponents.kt          # Audio settings controls
│       ├── DancingGlassBackground.kt # Animated background
│       └── ...                        # Other UI components
│
├── database/                       # Room database
│   ├── MusicDatabase.kt            # Database setup, migrations (v1-v8)
│   ├── PlaylistEntities.kt         # Playlist, PlaylistTrack entities
│   └── TrackEntity extensions      # toTrack(), toEntity() converters
│
├── analytics/                      # Analytics
│   ├── AnalyticsManager.kt         # PostHog integration, event tracking
│   ├── PlayerAnalyticsHelper.kt   # Player-specific analytics
│   ├── AnalyticsEvent.kt           # Event definitions
│   └── UsageExamples.kt            # Analytics usage documentation
│
└── utils/                          # Utility classes
    ├── LyricsRomanizer.kt           # Convert lyrics to Roman script
    ├── BlacklistManager.kt           # Manage blocked artists
    ├── FastDownloader.kt            # Multi-threaded download
    ├── ArtistUtils.kt               # Artist name comparison
    ├── HapticHelper.kt              # Haptic feedback
    └── DatabaseMigrationHelper.kt    # DB migration utilities
```

---

## Kotlin Source Files (Detailed)

### Application Entry Points

| File | Purpose |
|------|---------|
| `JukeApplication.kt` | Application class, configures Coil image loader with memory/disk cache settings |
| `MainActivity.kt` | Main activity, sets up Compose UI, Navigation, update checks, permission handling, MiniPlayer |

### Models (`models/`)

| File | Purpose |
|------|---------|
| `Track.kt` | Core data models: `Track` (local track), `SpotdownSong` (search result), `SpotdownCheckResponse`, `LRCLibResult` (lyrics) |
| `SpotifyModels.kt` | Spotify Web API models: `SpotifyTrack`, `SpotifyAlbum`, `SpotifyArtist`, `SpotifyPlaylist`, `SpotifySearchResponse`, etc. |
| `GithubRelease.kt` | GitHub release data for app updates (`tagName`, `htmlUrl`, `body`, `isPrerelease`) |

### Network Layer (`network/`)

| File | Purpose |
|------|---------|
| `ApiClient.kt` | Configures Ktor `HttpClient` with OkHttp engine, JSON serialization, logging, timeouts |
| `SpotifyApi.kt` | Spotify OAuth, search tracks/albums/artists/playlists, download URLs from Spotmate/Gamepvz, lyrics search via LRCLib/YouTube |
| `RecommenderApi.kt` | YouTube Music recommendations, video matching with Levenshtein distance, artist-based prioritization |

### Services (`services/`)

| File | Purpose |
|------|---------|
| `PlaybackService.kt` | Media3 `MediaLibraryService`, ExoPlayer management, audio focus, phone state handling, notification, cache management, Android Auto support |
| `MusicService.kt` | Download MP3s from Spotmate/Gamepvz, index tracks to DB, stream mode support, file validation, promotions (stream→download) |
| `QueueManager.kt` | Smart queue management, recommendation fetching, background downloads, duplicate prevention, playback history |
| `UpdateManager.kt` | Check GitHub releases for app updates (emergency vs regular) |
| `AudioEffectController.kt` | Bass boost, equalizer, skip silence settings |

### ViewModels (`viewmodels/`)

| File | Purpose |
|------|---------|
| `MusicViewModel.kt` | Main state holder: current track, queue, playback state, download queue, shuffle/repeat, stream mode, skip silence, color extraction |
| `SearchViewModel.kt` | Search state, YouTube suggestions, Spotify search, playlist URL import, artist/album detail loading |
| `HomeViewModel.kt` | Home screen data: greeting, recently played, most played, favorites |
| `LibraryViewModel.kt` | Library state: downloads, playlists, favorites, selection mode, sorting, search |
| `PlayerViewModel.kt` | Player-specific state (references MusicViewModel) |
| `PlaylistDetailViewModel.kt` | Single playlist details and tracks |
| `AlbumDetailViewModel.kt` | Album details and track listing |

### UI Screens (`ui/screens/`)

| File | Purpose |
|------|---------|
| `HomeScreen.kt` | Home with greeting, recently played horizontal pager, most played section, favorites section, pull-to-refresh |
| `SearchScreen.kt` | Search bar with filters (All/Tracks/Artists/Playlists/Albums), YouTube suggestions, local + Spotify results |
| `LibraryScreen.kt` | Downloads, playlists, favorites tabs, selection mode, bulk delete, sort options |
| `PlayerScreen.kt` | Full-screen player with artwork, controls, progress, lyrics overlay, queue sheet, sleep timer, share |
| `PlaylistDetailScreen.kt` | Playlist tracks with drag reorder, add/remove tracks |
| `AlbumDetailScreen.kt` | Album info, track list, add to playlist |
| `ArtistDetailScreen.kt` | Artist info, top tracks, albums list |
| `AudioSettingsScreen.kt` | Stream mode toggle, skip silence, bass boost, equalizer |
| `PurgeSelectionScreen.kt` | Select tracks for bulk deletion with filters |

### UI Components (`ui/components/`)

| File | Purpose |
|------|---------|
| `player/PlayerControls.kt` | Play/pause, skip previous/next, shuffle, repeat buttons |
| `player/PlayerProgress.kt` | Seek bar with current time and duration |
| `player/PlayerArtwork.kt` | Album art with gradient backdrop |
| `player/QueueSheet.kt` | Bottom sheet with draggable queue list |
| `player/LyricsOverlay.kt` | Synced lyrics with scrolling highlight |
| `TrackCard.kt` | Standard track item with artwork, title, artist |
| `MiniPlayer.kt` | Collapsed player at bottom of screens |
| `SwipeToAddNextContainer.kt` | Swipe gesture to add track to "Play Next" |
| `AddToPlaylistDialog.kt` | Dialog to add track to existing/new playlist |

### Database (`database/`)

| File | Purpose |
|------|---------|
| `MusicDatabase.kt` | Room database (v8), entities: `TrackEntity`, DAOs: `TrackDao`, migrations 1→8 |
| `PlaylistEntities.kt` | `PlaylistEntity`, `PlaylistTrackEntity`, `PlaylistWithTracks`, DAO: `PlaylistDao` |

**Database Schema (TrackEntity):**
- `uuid` (PK), `title`, `artist`, `thumbnail_uri`, `duration_sec`, `local_uri`, `yt_video_id`
- `synced_lyrics`, `plain_lyrics`, `is_favourite`, `play_count`, `last_played_at`
- `downloaded_at`, `spotify_id`, `album_spotify_id`, `artist_spotify_ids`
- `is_stream`, `lyrics_offset_ms`

### Analytics (`analytics/`)

| File | Purpose |
|------|---------|
| `AnalyticsManager.kt` | PostHog initialization, event tracking (song play/skip/complete, search, app open/close, session start/end), offline queue |
| `PlayerAnalyticsHelper.kt` | Player-specific analytics helper functions |
| `AnalyticsEvent.kt` | Event type constants and property definitions |

### Utilities (`utils/`)

| File | Purpose |
|------|---------|
| `LyricsRomanizer.kt` | Convert CJK/non-Latin lyrics to Roman script |
| `BlacklistManager.kt` | Manage blacklisted artist names |
| `FastDownloader.kt` | Multi-threaded HTTP download with progress |
| `ArtistUtils.kt` | Artist name normalization and comparison |
| `HapticHelper.kt` | Compose haptic feedback wrapper |
| `DatabaseMigrationHelper.kt` | Helper for database schema migrations |

---

## Resource Files

### Values (`res/values/`)

| File | Purpose |
|------|---------|
| `strings.xml` | App name "JUKE" |
| `colors.xml` | Color resources (if any custom defined) |
| `themes.xml` | App theme `Theme.JUKE` |

### Drawable (`res/drawable/`)

| File | Purpose |
|------|---------|
| `ic_launcher_foreground.xml` | Adaptive icon foreground |
| `ic_launcher_background.xml` | Adaptive icon background |
| `baseline_play_24.xml` | Play icon |
| `baseline_pause_24.xml` | Pause icon |
| `baseline_favorite_24.xml` | Favorite filled icon |
| `baseline_favorite_border_24.xml` | Favorite border icon |
| `baseline_download_24.xml` | Download icon |
| `baseline_mix.xml` | Mix/shuffle icon |
| `prev_svgrepo_com.xml` | Previous track icon |
| `next_svgrepo_com.xml` | Next track icon |
| `library.xml` / `library_outlined.xml` | Library icons |

### Mipmap (`res/mipmap-anydpi-v26/`)

- `ic_launcher.xml` - Adaptive launcher icon
- `ic_launcher_round.xml` - Round launcher icon

### XML Config (`res/xml/`)

| File | Purpose |
|------|---------|
| `automotive_app_desc.xml` | Android Auto manifest metadata |
| `backup_rules.xml` | Auto-backup exclusions |
| `data_extraction_rules.xml` | Data extraction rules for backup |

---

## Build Configuration

### Gradle Files

| File | Purpose |
|------|---------|
| `build.gradle.kts` (root) | Plugin declarations: Android application, Kotlin Android, Kotlin Compose, Kotlin Serialization, KSP |
| `app/build.gradle.kts` | App configuration: namespace `com.example.juke`, compileSdk 36, minSdk 26, targetSdk 36, version 2.3.2-beta-unreleased, dependencies, BuildConfig fields for Spotify/PostHog credentials |
| `settings.gradle.kts` | Plugin management, repositories, project name "JUKE", include `:app` |

### Properties

| File | Purpose |
|------|---------|
| `gradle.properties` | JVM args (6GB heap), parallel builds, daemon enabled, caching enabled, Kotlin incremental, R8 full mode |
| `local.properties` | Local config: `SPOTIFY_CLIENT_ID`, `SPOTIFY_CLIENT_SECRET`, `POSTHOG_API_KEY`, `POSTHOG_HOST` (not committed to git) |
| `gradle/wrapper/gradle-wrapper.properties` | Gradle 8.13.1 |
| `gradle/gradle-daemon-jvm.properties` | Daemon JVM config |

### Key Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| Kotlin | 2.0.21 | Programming language |
| Android Gradle Plugin | - | Build system |
| Jetpack Compose BOM | latest | UI toolkit |
| Compose Material3 | - | Material Design 3 components |
| Media3 (ExoPlayer) | - | Audio playback |
| Room | - | Local database (v8) |
| Ktor | - | HTTP client (Spotify, YouTube, LRCLib) |
| Kotlin Serialization | - | JSON parsing |
| Kotlin Coroutines | - | Async/flow |
| Coil | - | Image loading |
| Gson | - | JSON in RecommenderApi |
| PostHog Android | 3.40.2 | Analytics |

---

## Architecture Patterns

### Pattern: MVVM (Model-View-ViewModel)

```
┌─────────────────────────────────────────────────────────┐
│                        UI Layer                         │
│  Composable Screens → observe StateFlow from ViewModels │
└──────────────────────┬──────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────────┐
│                    ViewModel Layer                      │
│  MusicViewModel, SearchViewModel, etc.                  │
│  - Expose UI state via StateFlow                        │
│  - Handle user actions                                  │
│  - Coordinate services and database                     │
└──────────────────────┬──────────────────────────────────┘
                       │
       ┌───────────────┴───────────────┐
       │                               │
┌──────▼────────┐              ┌───────▼────────┐
│  Services     │              │  Database      │
│  PlaybackSvc  │              │  Room (v8)     │
│  MusicSvc     │              │  TrackEntity   │
│  QueueMgr     │              │  PlaylistEnt   │
└──────┬────────┘              └───────┬────────┘
       │                               │
┌──────▼───────┐                       │
│  Network     │                       │
│  SpotifyApi  │                       │
│  Recommender │───────────────────────┘
│  ApiClient   │
└──────────────┘
```

### Key Patterns

1. **State Management**: ViewModels expose `StateFlow<UiState>` to Compose screens
2. **Navigation**: Jetpack Navigation Compose with sealed class `Screen` routes
3. **Database**: Room with `TrackEntity`, `PlaylistEntity`, `PlaylistTrackEntity`
4. **Playback**: Media3 `MediaLibraryService` with `ExoPlayer`, notification integration
5. **Downloads**: Dual-source (Spotmate/Gamepvz) with fallback, stream vs permanent modes
6. **Recommendations**: YouTube Music integration with Spotify validation
7. **Lyrics**: Multi-source (LRCLib → YouTube Captions → YouTube Music)
8. **Analytics**: PostHog with offline queue and network-aware sync
9. **Image Loading**: Coil with custom cache config in `JukeApplication`
10. **Settings**: SharedPreferences for audio effects, stream mode, etc.

### Data Flow Example (Play a Track)

```
User taps track
    ↓
MusicViewModel.setQueue(tracks, index)
    ↓
PlaybackManager.initializeQueue(tracks)
    ↓
QueueManager.initializeQueue(tracks)
    ↓
PlaybackService -> ExoPlayer.prepare(MediaItem)
    ↓
MediaSession callback → UI state updates via Flow
    ↓
Compose observes StateFlow → UI recomposes
```

---

## File Count Summary

| Category | Count |
|----------|-------|
| Kotlin source files (main) | ~50 |
| Kotlin source files (test) | 2 |
| XML resource files | ~30 |
| Gradle build files | 4 |
| Properties files | 3 |

---

## Notes

- **Minimum SDK**: 26 (Android 8.0 Oreo)
- **Target/Compile SDK**: 36
- **Architecture**: MVVM with Compose, no traditional Repository pattern (ViewModels use Services/DAOs directly)
- **Playback**: Uses AndroidX Media3 (ExoPlayer) with `MediaLibraryService` for Android Auto support
- **Offline Support**: Downloads MP3s for offline playback, Room database for metadata
- **Analytics**: PostHog integration with offline event queue
- **Stream Mode**: Can stream without permanent download (LRU cache management)
- **Update System**: GitHub releases checked on app start, emergency updates can force app closure
