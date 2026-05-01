# JUKES - Complete Feature Documentation

> **Coverage**: 100% of core logic, services, ViewModels, network APIs, and data models.  
> **Total Files Analyzed**: 18+ Kotlin files  
> **Last Updated**: 2026-05-01

---

## 1. Core Features

### 1.1 Music Playback System
- **ExoPlayer-based playback** using Android Media3 (androidx.media3.exoplayer)
- **MediaLibraryService** (`PlaybackService.kt`) - Foreground service with Media3 session
- **MediaController integration** via `PlaybackManager` singleton
- **Audio focus handling** - Automatic pause/resume on audio focus change
- **Call state detection** - Pause during calls, auto-resume after call ends (requires `READ_PHONE_STATE` permission)
- **Stream mode support** - Play tracks without permanent download (256MB LRU cache)
- **Queue management** - Smart queue with recommendations and pre-fetching

### 1.2 Search & Discovery
- **Unified search** across Spotify (tracks, artists, playlists, albums)
- **YouTube Music recommendations** - Queue suggestions based on current track
- **LRCLib lyrics integration** - Synced (.lrc) and plain lyrics
- **YouTube lyric fallbacks** - YouTube Music lyrics endpoint, captions extraction
- **Offline recommendations** - Score-based local library recommendations when network unavailable

### 1.3 Library Management
- **Download tracks** from Spotify via Spotmate/Gamepvz proxy services
- **Playlist support** - Create, import from Spotify, manage tracks
- **Favorites system** - Mark/unmark tracks as favorites
- **Sort options** - Recently added, Title, Artist, Last played, Most played
- **Search within library** - Filter by title, artist, or lyrics content
- **Selection mode** - Multi-select tracks for batch operations

### 1.4 Audio Effects

- **Volume Booster** - Up to 100% boost (0-500dB range)
- **Loudness Normalization** - DynamicsProcessing with limiter (Android 9+)
- **Skip Silence** - Automatically skip silent passages

---

## 2. UI Screens and Components

### 2.1 Navigation Structure
**Sealed class `Screen` routes** (defined in `MainActivity.kt`):
- `home` - Home screen
- `search` - Search screen
- `library` - Library screen
- `settings` - Audio settings screen
- `settings/purge` - Cache purge selection screen
- `artist/{artistId}` - Artist detail screen
- `playlist/{playlistId}` - Playlist detail screen
- `album/{albumId}` - Album detail screen

**Navigation bar** (bottom): Home, Search, Library with filled/outlined icons

### 2.2 Home Screen (`HomeScreen.kt`)
- **Greeting banner** - Time-based ("First Light Sounds!", "Afternoon Drift!", "The Golden Hour!", "After Dark!")
- **Recently Played** section (up to 10 tracks)
- **Most Played** section (up to 10 tracks)
- **Favorites** section (up to 10 tracks)
- **See All button** - Navigates to Library screen
- **Settings icon** - Navigates to settings

### 2.3 Search Screen (`SearchScreen.kt`)
- **Search bar** with live suggestions from YouTube Music API
- **Recent searches** - Last 5 queries, removable
- **Spotify URL support** - Paste track/artist/playlist/album URLs
- **Search results tabs**: Tracks, Local Tracks, Artists, Playlists, Albums
- **YouTube suggestions** - Debounced (100ms) with caching (64 entries)
- **Import playlist** - Batch download with progress indicator (max 6 concurrent)

### 2.4 Library Screen (`LibraryScreen.kt`)
- **Tab toggle**: All Tracks / Favorites filter
- **Playlist section** - Horizontal scroll with create option
- **Track list** with sort options dialog
- **Search within library** - Filters title, artist, and lyrics
- **Swipe to delete** with 5-second undo
- **Selection mode** - Multi-select with select all option
- **Sort options**: Recently Added, Title, Artist, Last Played, Most Played

### 2.5 Player Screen (`PlayerScreen.kt`)
- **Full-screen player** with backdrop gradient from album art
- **Mini-player** (persistent at bottom, shows on all screens except settings)
  - Displays: thumbnail, title, artist, play/pause, next, lyrics toggle
  - **Mini-player lyrics** - Optional ROMajiized synced lyrics (setting: `miniplayer_lyrics_enabled`)
- **Playback controls**: Play/Pause, Skip Next, Skip Previous, Shuffle, Repeat (Off/One/All)
- **Seek bar** with elapsed/total time
- **Lyrics display** - Synced LRC format with offset adjustment
- **Share track** - Sends Spotify link via Android share intent
- **Queue view** - Reorder, remove, play from queue
- **Favorite toggle** - Heart icon

### 2.6 Artist Detail Screen (`ArtistDetailScreen.kt`)
- **Artist header** with image and name
- **Top Tracks** section (from Spotify)
- **Albums** grid (from Spotify)
- **Play/Queue buttons** for tracks

### 2.7 Album Detail Screen (`AlbumDetailScreen.kt`)
- **Album header** - Cover art, title, artist, release date
- **Track list** with duration
- **Play/Queue album** buttons

### 2.8 Playlist Detail Screen (`PlaylistDetailScreen.kt`)
- **Playlist header** - Cover, name, description, track count
- **Import offline** - Download all tracks to local library
- **Track list** with drag reorder

### 2.9 Audio Settings Screen (`AudioSettingsScreen.kt`)
- **Stream Mode toggle** - Play without saving (CloudSync icon)
- **Volume Normalization toggle** - Loudness normalization (GraphicEq icon)
- **Volume Booster** - Slider (0-100%) with preview
- **Skip Silence toggle** - Skip silent passages
- **Recommendation Count** - Slider (3-15 songs)
- **Market Code** - Spotify region (ISO 3166-1 alpha-2)
- **Romanized Lyrics toggle** - Convert lyrics to Roman characters
- **Mini-Player Lyrics toggle** - Show lyrics in mini-player
- **Purge Cache** - Navigate to purge selection
- **Blacklist Management** - Block artists from recommendations

### 2.10 Purge Selection Screen (`PurgeSelectionScreen.kt`)
- **Select tracks** for cache cleanup
- **Filter Criticism**: Not played in 14+ days OR downloaded 30+ days ago
- **Bulk delete** with confirmation

---

## 3. Data Management

### 3.1 Data Models (`Track.kt`, `SpotifyModels.kt`)

**Track** (local database entity):
- `uuid: String` - Unique identifier
- `title: String` - Track title
- `artist: String` - Artist name(s), comma-separated
- `thumbnailUri: String?` - Local or remote artwork URI
- `durationSec: Int` - Duration in seconds
- `localUri: String?` - Local file path or stream URI
- `ytVideoId: String?` - YouTube video ID for recommendations
- `syncedLyrics: String?` - LRC format synced lyrics
- `plainLyrics: String?` - Plain text lyrics
- `isFavourite: Boolean` - Favorite status
- `playCount: Int` - Number of plays
- `lastPlayedAt: String?` - ISO timestamp of last play
- `downloadedAt: Long?` - Epoch timestamp of download
- `spotifyId: String?` - Spotify track ID
- `albumSpotifyId: String?` - Spotify album ID
- `artistSpotifyIds: List<String>?` - Spotify artist IDs
- `isStream: Boolean` - True if streamed (not permanently downloaded)
- `lyricsOffsetMs: Long` - LRC lyrics sync offset

**SpotdownSong** (search result from Spotify):
- `title, artist, album, thumbnail, url, duration, cached, spotifyId, albumSpotifyId, artistSpotifyIds`

**LRCLibResult** (lyrics API response):
- `id, name, trackName, artistName, albumName, duration, instrumental, plainLyrics, syncedLyrics`

**Spotify API Models** (`SpotifyModels.kt`):
- `SpotifyTrack` - Full track with album, artists, duration_ms, explicit, external_urls, popularity
- `SpotifyArtist` - Artist with followers, genres, popularity, images
- `SpotifyAlbum` - Album with album_type, release_date, total_tracks, images
- `SpotifyPlaylist` - Playlist with collaborative, description, images, owner, tracks
- `SpotifySimplifiedTrack` - Album track with track_number
- `SpotifyImage` - Image with height, width, url
- `SpotifyTokenResponse` - OAuth token with expires_in

**GithubRelease** (`GithubRelease.kt`):
- `tagName` - Version tag (e.g., "v1.0.1")
- `htmlUrl` - Release page URL
- `body` - Release notes
- `isPrerelease: Boolean`

### 3.2 Database (Room)
**MusicDatabase** with entities:
- **TrackEntity** - Local track storage (uuid primary key)
- **PlaylistEntity** - User-created playlists (id, name, description, thumbnailUri, spotifyId, trackCount, createdAt)
- **PlaylistTrackEntity** - Junction table (playlistId, trackUuid, position, addedAt)

**DAOs**:
- `TrackDao` - `getRecentlyPlayed()`, `getMostPlayed()`, `getFavourites()`, `findTracksByTitleAndDuration()`, `getDownloadedTracks()`, `getStreamTracks()`, `searchTracksRaw()`, `incrementPlayCount()`, `updateTrackFavourite()`, `updateLyricsOffset()`, `deleteTrack()`, `deleteTracks()`, `getAllTracks()`, `getPurgeableTracks()`
- `PlaylistDao` - `getAllPlaylists()`, `getPlaylist()`, `insertPlaylist()`, `updatePlaylist()`, `deletePlaylist()`, `getPlaylistTracks()`, `insertPlaylistTrack()`, `removeTrackFromPlaylist()`, `deletePlaylistTracksForTrack()`, `getPlaylistsForTrack()`, `updatePlaylistTrackCount()`

### 3.3 Playback State Persistence
- **SharedPreferences** (`playback_state_prefs`):
  - `queue_track_ids` - Comma-separated UUIDs
  - `queue_start_index` - Current track index
- **Restoration on app launch** - Rebuilds queue from saved state

---

## 4. Services and Integrations

### 4.1 Network APIs

**Spotify Web API** (`SpotifyApi.kt`):
- **OAuth2 Client Credentials flow** - Automatic token refresh (5-minute buffer)
- **Search** - Tracks, artists, playlists, albums with market code
- **Get Track/Artist/Album/Playlist** - By Spotify ID
- **Get Playlist Tracks** - With pagination support (auto-fetches all pages)
- **Artist Albums** - All albumns by artist
- **Artist Top Tracks** - Most popular tracks
- **Market code** - Configurable ISO 3166-1 alpha-2 (default: "IN", configurable in settings)

**Spotmate Integration** (`SpotifyApi.kt`):
- **getSpotmateStreamUrl()** - Resolve Spotify URL to direct MP3
- **getSpotmateDownloadRequest()** - Get download request with headers
- **downloadSongFromSpotmate()** - Direct bytearray download
- **resolveSpotmateTask()** - Poll queued conversion tasks (30 attempts, 3s delay)
- **Queued conversion handling** - `SpotmateQueuedException` with taskId

**Gamepvz Integration** (`SpotifyApi.kt`):
- **getGamepvzDownloadRequest()** - Get download URL with Referer/User-Agent headers
- **downloadSongFromGamepvz()** - Direct MP3 download
- **Fallback support** - Random primary/fallback selection, automatic retry

**LRCLib Lyrics API** (`SpotifyApi.kt`):
- **searchLyrics()** - Multi-tier fallback:
  - Tier 1: LRCLib synced lyrics (primary)
  - Tier 2: YouTube Captions synced LRC
  - Tier 3: LRCLib plain lyrics
  - Tier 4: YouTube Music plain lyrics
  - Tier 5: YouTube Captions plain text
- **Title cleaning** - Removes "(From...)", "(feat....)" for better matching
- **Duration validation** - 5% tolerance for match confirmation
- **Fallback artist search** - Individual artist names if primary fails

**YouTube Music Recommender API** (`RecommenderApi.kt`):
- **getBestVideoMatch()** - Search YouTube Music for best video match
  - Scoring: Title similarity (50%), word match (30%), official keywords, no-spam filter
  - Levenshtein distance for string similarity
- **fetchFullRadioQueue()** - Get 50-track radio queue from YouTube Music
  - Two-step Innertube flow: NEXT → browseId, BROWSE → tracks
- **validateAndFilterWithSpotify()** - Cross-validate YouTube recs with Spotify
  - Artist similarity scoring (Levenshtein)
  - Duration proximity scoring
  - Official keyword weighting
  - Spam/variant filtering (remix, cover, AI, karaoke, etc.)

**GitHub Releases API** (`UpdateManager.kt`):
- **checkForUpdates()** - Fetches latest release from `rajeet-04/JUKES`
- **Version comparison** - SemVer with beta/alpha suffix handling
- **Emergency detection** - "emergency" or "hotfix" in tag/body triggers critical update UI

**ApiClient** (`ApiClient.kt`):
- **Ktor HTTP client** with timeout configuration
  - Connect timeout: 30s
  - Read timeout: 120s
  - Request timeout: 120s
- **Retry on connection failure**
- **Used by**: SpotifyApi, RecommenderApi, LRCLib, Spotmate, Gamepvz

### 4.2 Services

**PlaybackService** (`PlaybackService.kt`):
- **MediaLibraryService** - Android Media3 service with `MediaLibrarySession`
- **ExoPlayer configuration**:
  - `DefaultRenderersFactory` with extension renderer
  - `CacheDataSource` for stream cache (256MB LRU)
  - `DefaultLoadControl` - 30s min buffer, 120s max buffer, 30s back-buffer
- **Notification system**:
  - Custom layout: Favorite button OR Download button (for streams)
  - Channel: "media_playback" (IMPORTANCE_LOW)
  - ID: 1
- **Android Auto support**:
  - Browse roots: Recently Played, Favorites, All Tracks
  - Custom commands: Toggle Favorite, Download Track
- **Audio focus**: `AudioFocusRequest` with gain/loss handling
- **Call state receiver**: Pause on ring/outgoing, resume after call (500ms delay)
- **Sleep timer** - Configurable minutes (via PlaybackManager)
- **Play count tracking** - Increments at 50% completion (once per session)
- **Artwork handling** - Supports http, file, content URIs
- **Stream cache management** - `StreamCacheManager` singleton with `clearAllCache()`, `removeTrackCache()`

**QueueManager** (`QueueManager.kt`):
- **Recommendation engine**:
  - Online: YouTube Music → Spotify validation → Filter
  - Offline fallback: Score-based local library (artist match +10, album match +8, favorite +10, play count +5/10, recency -10/-20)
- **Queue operations**: `initializeQueue()`, `addToQueue()`, `insertQueueItem()`, `removeFromQueue()`, `moveToNext()`, `clearQueue()`
- **Pre-fetch system** - Ensures next 3 tracks are ready (checks file existence, URL expiry)
- **Download management** - Max 6 concurrent, pending queue with `ConcurrentLinkedQueue`
- **Stream mode support** - Downloads to `stream_files/` directory with LRU eviction (30 file max)
- **Blacklist filtering** - `BlacklistManager` integration
- **History tracking** - Last 50 played tracks, last 50 artists for diversity
- **Session history** - Prevents duplicate recommendations

**MusicService** (`MusicService.kt`):
- **smartDownloadAndIndex()** - Full download with:
  - Dual source (Spotmate/Gamepvz) with random selection and fallback
  - Retry with backoff (5 attempts, exponential delay)
  - MP3 validation (ID3/MP3 frame header check)
  - Duration verification (±5s tolerance)
  - Thumbnail download and local storage
  - Lyrics fetch (LRCLib)
  - YouTube video ID resolution
- **streamTrack()** - Stream to local file without permanent storage
  - Reuses existing cache if healthy
  - Evicts LRU when >30 files (protects pinned UUIDs)
- **promoteStreamToDownload()** - Convert stream to permanent download
- **deleteTrackAndFiles()** - Removes track, files, playlist entries, updates counts
- **Cache cleanup** - `cleanupOrphanedCacheFiles()` (7+ day old orphaned files)
- **Stream cache eviction** - `evictStreamCache()` with pinned UUID protection
- **Purge stale streams** - `purgeStaleStreamEntries()` on startup

**UpdateManager** (`UpdateManager.kt`):
- **checkForUpdates()** - GitHub Releases API (lists all, takes first as latest)
- **isNewer()** - Version comparison (SemVer with beta/stable handling)
- **Emergency update detection** - Critical updates cannot be dismissed

**AudioEffectController** (`AudioEffectController.kt`):
- **Equalizer** (Android `Equalizer` class):
  - 10 bands with configurable levels (-5000 to 5000 mB)
  - Persists band levels to `audio_effects_prefs`
  - Preference listener for external changes
- **LoudnessEnhancer** (Android `LoudnessEnhancer`):
  - Target gain control (0-100% → 0-50000 mB)
  - Toggle on/off
- **DynamicsProcessing** (Android 9+ `DynamicsProcessing`):
  - Limiter: Threshold -1dB, Ratio 10:1, Release 60ms
  - MBC band: Cutoff 20Hz, Ratio 3:1, PostGain +3dB
  - Normalization toggle
- **Preference keys**: `equalizer_enabled`, `eq_band_0` through `eq_band_9`, `booster_enabled`, `booster_level`, `normalization_enabled`

### 4.3 External Integrations Summary
| Service | Purpose | Key Functions |
|---------|---------|---------------|
| Spotify Web API | Track/artist/album metadata | `search()`, `getTrack()`, `getArtist()`, `getPlaylist()`, `getAlbumTracks()` |
| Spotmate | MP3 download proxy | `getSpotmateDownloadRequest()`, `downloadSongFromSpotmate()`, `resolveSpotmateTask()` |
| Gamepvz | MP3 download proxy (fallback) | `getGamepvzDownloadRequest()`, `downloadSongFromGamepvz()` |
| LRCLib | Lyrics database | `searchLyrics()` with 5-tier fallback |
| YouTube Music | Recommendations & lyrics | `getBestVideoMatch()`, `fetchFullRadioQueue()`, `getYoutubeMusicLyrics()`, `getYoutubeCaptions()` |
| GitHub API | App updates | `checkForUpdates()` |

---

## 5. Settings and Configuration

### 5.1 Audio Settings (`music_settings_prefs` & `audio_effects_prefs`)
- **Stream Mode** (`stream_mode`) - Default: `false` → Play without saving (toggle in UI)
- **Skip Silence** (`skip_silence_enabled`) - Default: `false` → Skip silent passages
- **Volume Booster Enabled** (`booster_enabled`) - Default: `false`
- **Volume Booster Level** (`booster_level`) - Default: 0 (0-100%)
- **Normalization Enabled** (`normalization_enabled`) - Default: `false` (Android 9+)
- **Recommendation Count** (`recommendation_count`) - Default: 5 (range: 3-15)
- **Market Code** (`spotify_market_code`) - Default: "IN" (ISO 3166-1 alpha-2)
- **Romanized Lyrics** (`romanized_lyrics_enabled`) - Default: `false`
- **Mini-Player Lyrics** (`miniplayer_lyrics_enabled`) - Default: `true`

### 5.2 Playback Settings (`playback_state_prefs`)
- **Queue Track IDs** (`queue_track_ids`) - Comma-separated UUIDs for state restoration
- **Queue Start Index** (`queue_start_index`) - Current track position in queue

### 5.3 Search Settings (`search_history`)
- **Recent Searches** - Last 5 queries stored as pipe-separated string ("|||" delimiter)

### 5.4 Audio Effect Preferences (`audio_effects_prefs`)
- All equalizer band levels (10 bands)
- Booster level and enabled state
- Normalization enabled state
- Equalizer enabled state
- Preference change listener updates hardware in real-time

---

## 6. Minor/Supporting Features

### 6.1 App Initialization
- **JukeApplication** (`JukeApplication.kt`):
  - Coil `ImageLoaderFactory` configuration:
    - Memory cache: 25% of available
    - Disk cache: 2% of available, directory: `cacheDir/image_cache`
    - Crossfade enabled
- **Portrait lock** for phones (<600dp smallest width)
- **Edge-to-edge display** enabled

### 6.2 Analytics (`AnalyticsManager`)
- **App opened tracking** - `trackAppOpened()`
- **Search query tracking** - `trackSearchQuery()`
- **App closed tracking** - `trackAppClosed()`
- **Session end** - `endSession()`

### 6.3 Permissions
- **READ_PHONE_STATE** - For call state detection (auto-pause during calls)
  - Requested at startup via `requestPermissionLauncher`
  - Graceful degradation if denied (just loses auto-pause feature)

### 6.4 Update System
- **GitHub Releases check** on every app launch
- **Emergency updates** - Cannot be dismissed, forces "Download Update" with app close
- **Regular updates** - Dismissible "Maybe Later" option
- **Pre-release detection** - Shows "beta" note if `isPrerelease=true`
- **Version comparison** - Handles "v1.0.1" vs "1.0.1-beta" correctly

### 6.5 Intent Handling
- **`open_player` extra** - Opens player modal on app launch (used by notification/shortcut)
- **Spotify URL sharing** - Paste Spotify URLs in search to play directly
- **Share track** - Sends `https://open.spotify.com/track/{id}` via Android share intent

### 6.6 Color Extraction
- **Palette from thumbnail** (`MusicViewModel.extractColors()`):
  - Uses Android `Palette` library
  - Priority: Vibrant → Light Vibrant → Dark Vibrant → Dominant → Muted
  - Ensures luminance >0.15 for visibility
  - Applies to theme: `primary`, `secondary`, `tertiary`, `background`, `surface`

### 6.7 Download Management
- **Download queue** (`MusicViewModel`):
  - UI state: `downloadQueue: List<DownloadItem>`
  - Statuses: QUEUED, DOWNLOADING, COMPLETED, FAILED
  - Cancel/Retry support
- **Smart download** - Checks existing before re-downloading
- **Stream promotion** - Convert stream to permanent download preserving UUID

### 6.8 Queue Operations
- **Add to queue** - `addToQueue()` (deduplicates by UUID)
- **Add next** - `addNext()` / `addNext(list)` (inserts after current)
- **Move in queue** - `moveInQueue(fromIndex, toIndex)`
- **Remove from queue** - `removeFromQueue(trackId)`
- **Play from queue** - `playTrackFromQueue(track)`
- **Shuffle/Repeat** - Toggle shuffle mode, repeat mode (Off/One/All)
- **Radio mode** - `startRadio()` keeps only current track then fetches recommendations

### 6.9 Playlist Management
- **Create playlist** - `createPlaylist(name)`
- **Update playlist** - `updatePlaylist()` with name/thumbnail
- **Delete playlist** - `deletePlaylist()` (cascades to playlist_tracks)
- **Add to playlist** - `addToPlaylist()` (deduplicates)
- **Add tracks to playlist** - `addTracksToPlaylist()` (batch, O(m+n))
- **Remove from playlist** - `removeFromPlaylist()`
- **Import Spotify playlist** - `importPlaylist()` with progress tracking

### 6.10 Library Management
- **Import audio files** - `importAudioFiles()` from content URIs (extracts metadata via `MediaMetadataRetriever`)
- **Delete track** - 5-second undo window, then permanent delete
- **Delete selected tracks** - Batch delete in selection mode
- **Purge stale tracks** - `getPurgeableTracks()` (not played 14+ days OR downloaded 30+ days)
- **Blacklist artist** - `BlacklistManager` prevents recommended artists

### 6.11 Lyrics Features
- **Synced lyrics** - LRC format with `.lrc` styling in player
- **Lyrics offset** - Adjust sync with `saveLyricsOffset()`
- **Refresh lyrics** - `refreshLyrics()` re-fetches from LRCLib
- **Romanized lyrics** - Convert to Roman characters (toggle in settings)
- **YouTube Music lyrics** - Fallback plain text from YTM browse endpoint
- **YouTube captions** - Fallback from video captions (TVHTML5 client, no poToken needed)

### 6.12 Error Handling & Resilience
- **Offline detection** - `OfflineException` for network operations
- **Retry with backoff** - `retryWithBackoff()` (5 attempts, exponential delay)
- **Spotmate queued handling** - Poll task endpoint with `SpotmateQueuedException`
- **Download source fallback** - Spotmate → Gamepvz → Queued Spotmate task
- **Graceful degradation** - Offline recommendations when network unavailable
- **Stream cache LRU** - Evicts oldest files when >30, protects currently playing tracks

### 6.13 Coil Image Loading
- **Thumbnail pre-warming** - Preloads search results to Coil memory cache
- **Artwork caching** - Disk cache in `image_cache`, memory cache 25%
- **Crossfade** - Enabled for smooth transitions

### 6.14 Notification Customization
- **Favorite button** - Toggles in notification (heart/outline icon)
- **Download button** - Shows for stream tracks (promotes to permanent)
- **Media style** - Standard Play/Pause/Next/Previous actions

---

## Appendix: Key File Locations

| Component | File Path |
|-----------|-----------|
| Application Class | `app/src/main/java/com/example/juke/JukeApplication.kt` |
| Main Activity | `app/src/main/java/com/example/juke/MainActivity.kt` |
| Track Model | `app/src/main/java/com/example/juke/models/Track.kt` |
| Music ViewModel | `app/src/main/java/com/example/juke/viewmodels/MusicViewModel.kt` |
| Search ViewModel | `app/src/main/java/com/example/juke/viewmodels/SearchViewModel.kt` |
| Library ViewModel | `app/src/main/java/com/example/juke/viewmodels/LibraryViewModel.kt` |
| Playback Service | `app/src/main/java/com/example/juke/services/PlaybackService.kt` |
| Queue Manager | `app/src/main/java/com/example/juke/services/QueueManager.kt` |
| Music Service | `app/src/main/java/com/example/juke/services/MusicService.kt` |
| Spotify API | `app/src/main/java/com/example/juke/network/SpotifyApi.kt` |
| Recommender API | `app/src/main/java/com/example/juke/network/RecommenderApi.kt` |

---

**End of Feature Documentation**
