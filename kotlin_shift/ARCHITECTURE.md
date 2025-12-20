# JUKE Music Player - Kotlin Architecture Documentation

## Overview

JUKE is a modern Android music streaming and recommendation app that integrates:
- **Spotify** for music search and download
- **YouTube Music** for personalized recommendations
- **LRCLib** for synced and plain lyrics
- **Local storage** with SQLite/Room for offline playback

## Architecture Pattern

The app follows **Clean Architecture** principles with clear separation of concerns:

```
┌─────────────────────────────────────────────────┐
│                  UI Layer                        │
│  (Activities, Fragments, Composables)           │
└────────────────┬────────────────────────────────┘
                 │
┌────────────────▼────────────────────────────────┐
│              ViewModel Layer                     │
│     (State Management, UI Logic)                │
└────────────────┬────────────────────────────────┘
                 │
┌────────────────▼────────────────────────────────┐
│             Service Layer                        │
│  (MusicService, PlaybackManager)                │
└────────────────┬────────────────────────────────┘
                 │
┌────────────────▼────────────────────────────────┐
│           Repository Layer                       │
│  (TrackDao, API Clients)                        │
└────────────────┬────────────────────────────────┘
                 │
┌────────────────▼────────────────────────────────┐
│          Data Sources                            │
│  (Room DB, Network APIs)                        │
└─────────────────────────────────────────────────┘
```

## Project Structure

```
com.juke/
├── models/              # Data models and DTOs
│   └── Track.kt        # Core data structures
│
├── database/           # Local persistence
│   └── MusicDatabase.kt # Room database and DAOs
│
├── network/            # API clients
│   ├── ApiClient.kt    # HTTP client configuration
│   ├── RecommenderApi.kt # YouTube Music API
│   └── SpotifyApi.kt   # Spotify/Spotdown API
│
├── services/           # Business logic
│   ├── MusicService.kt # Download and indexing
│   └── PlaybackService.kt # Media playback
│
├── ui/                 # User interface
│   ├── screens/        # Screen composables
│   ├── components/     # Reusable UI components
│   └── theme/          # Material Design theme
│
└── viewmodels/         # State management
    ├── MusicViewModel.kt
    └── PlayerViewModel.kt
```

## Core Components

### 1. Data Models (`models/Track.kt`)

#### Track
Primary data structure representing a music track.

```kotlin
data class Track(
    val uuid: String,           // Unique identifier
    val title: String,          // Song title
    val artist: String,         // Artist name
    val thumbnailUri: String?,  // Album artwork path
    val durationSec: Int,       // Duration in seconds
    val localUri: String?,      // Local MP3 file path
    val ytVideoId: String?,     // YouTube video ID for recs
    val syncedLyrics: String?,  // LRC format lyrics
    val plainLyrics: String?,   // Plain text lyrics
    val isFavourite: Boolean,   // User favorite status
    val playCount: Int,         // Play count
    val lastPlayedAt: String?   // ISO 8601 timestamp
)
```

#### SpotdownSong
Represents Spotify search results before download.

```kotlin
data class SpotdownSong(
    val title: String,
    val artist: String,
    val thumbnail: String,      // HTTP URL
    val url: String,            // Spotify track URL
    val duration: String        // "MM:SS" format
)
```

#### YouTubeRecommendation
YouTube Music recommendation for auto-play.

```kotlin
data class YouTubeRecommendation(
    val id: String,             // YouTube video ID
    val title: String,
    val artist: String
)
```

### 2. Network Layer

#### ApiClient (`network/ApiClient.kt`)
Configures Ktor HTTP client with:
- JSON serialization
- Logging
- Custom timeouts (2-min for downloads)
- User-Agent headers

#### SpotifyApi (`network/SpotifyApi.kt`)
**Base URL:** `https://spotdown.org/api`

Methods:
- `searchSongs(query: String)`: Search Spotify catalog
- `checkDirectDownload(spotifyUrl: String)`: Check if song is cached
- `downloadSong(spotifyUrl: String)`: Download MP3 with retry logic
- `searchLyrics(title, artist, duration)`: Fetch lyrics from LRCLib
- `parseDuration(durationStr: String)`: Convert "MM:SS" to seconds

#### RecommenderApi (`network/RecommenderApi.kt`)
**Search URL:** `https://mp3juice3.ninja/api/yt-data`
**YT Music URL:** `https://music.youtube.com/youtubei/v1/next`

Methods:
- `getBestVideoMatch(songName: String)`: Find best YouTube video
  - First pass: Look for "official" keywords
  - Second pass: Similarity matching (> 0.5 threshold)
- `getRecommendations(videoId: String)`: Get top 3 recommendations
  - Step 1: Get radio playlist ID
  - Step 2: Fetch full track list

### 3. Database Layer (`database/MusicDatabase.kt`)

Uses **Room** for local persistence.

#### TrackDao Interface
Key methods:
- `insertTrack(track)`: Insert or replace track
- `getAllTracks()`: Get all tracks by last played
- `getRecentlyPlayed(limit)`: Get N recent tracks
- `getFavourites()`: Get favorite tracks
- `getDownloadedTracks()`: Get tracks with local files
- `incrementPlayCount(uuid, timestamp)`: Update play stats
- `searchTracks(query)`: Full-text search
- `findTrackByTitleArtist(title, artist)`: Fuzzy match

#### Flow Support
Many queries return `Flow<List<Track>>` for reactive UI updates.

### 4. Service Layer

#### MusicService (`services/MusicService.kt`)
Handles downloading and indexing tracks.

**Key Method:** `smartDownloadAndIndex(song, onProgress)`
1. Validate Spotify URL
2. Check cache status
3. Download MP3 file
4. Verify file integrity (ID3 tags or MP3 frames)
5. Fetch lyrics and YouTube ID in parallel
6. Download thumbnail
7. Save to local storage
8. Insert into database

**Retry Logic:**
- Exponential backoff (2s, 4s, 8s, 16s, max 10s)
- Retries on 500 errors, network timeouts
- Max 5 attempts

#### PlaybackService (`services/PlaybackService.kt`)
Android **MediaSessionService** using **Media3 (ExoPlayer)**.

Features:
- Background playback
- Media notifications
- Audio focus handling
- Headphone disconnect detection
- Playback state tracking
- Auto-increment play count

**PlaybackManager** provides high-level interface:
- `playTrack(track)`: Load and play single track
- `setQueue(tracks, startIndex)`: Set playlist
- `addToQueue(track)`: Add to end of queue
- `togglePlayPause()`: Play/pause
- `skipToNext()` / `skipToPrevious()`: Navigation
- `seekTo(positionMs)`: Seek
- `getCurrentPosition()` / `getDuration()`: Progress

## Data Flow

### 1. Search and Download Flow

```
User Search
    ↓
SpotifyApi.searchSongs()
    ↓
Display Results (SpotdownSong[])
    ↓
User Selects Song
    ↓
MusicService.smartDownloadAndIndex()
    ├→ SpotifyApi.checkDirectDownload()
    ├→ SpotifyApi.downloadSong()
    ├→ SpotifyApi.searchLyrics()
    ├→ RecommenderApi.getBestVideoMatch()
    └→ Save to DB (TrackDao.insertTrack())
    ↓
Track Ready for Playback
```

### 2. Playback Flow

```
User Plays Track
    ↓
PlaybackManager.playTrack()
    ↓
ExoPlayer loads from localUri
    ↓
Media3 handles playback
    ├→ Show notification
    ├→ Handle media buttons
    └→ Increment play count
```

### 3. Recommendation Flow

```
Track Ends
    ↓
Get ytVideoId from current Track
    ↓
RecommenderApi.getRecommendations(videoId)
    ├→ Step 1: Get radio playlist ID
    └→ Step 2: Fetch top 3 tracks
    ↓
For each recommendation:
    ↓
MusicService.downloadRecommendedTrack()
    ├→ Check if already in DB
    ├→ SpotifyApi.searchSongs()
    └→ smartDownloadAndIndex()
    ↓
Add to queue
```

## Threading Model

- **Main Thread**: UI updates, player control
- **IO Dispatcher**: Network calls, database operations
- **Default Dispatcher**: Heavy computation (similarity matching)

All suspend functions use coroutines:
```kotlin
viewModelScope.launch {
    withContext(Dispatchers.IO) {
        // Network/DB operations
    }
}
```

## Error Handling

### Network Errors
- Retry with exponential backoff
- Log all errors with context
- Show user-friendly messages
- Graceful degradation (continue without lyrics if fetch fails)

### File Errors
- Validate file integrity after download
- Clean up partial downloads on failure
- Check file size before saving

### Database Errors
- Use transactions for multi-step operations
- Handle constraint violations
- Log errors but don't crash app

## Performance Optimizations

1. **Parallel Fetching**: Lyrics and YouTube ID fetched simultaneously
2. **Lazy Loading**: Use Flow for reactive updates
3. **Caching**: Check server cache before slow download
4. **Indexed Queries**: Database indices on `last_played_at`, `is_favourite`, `play_count`
5. **Chunked Processing**: Large files processed in chunks

## Security Considerations

1. **HTTPS Only**: All API calls use HTTPS
2. **File Validation**: Verify MP3 integrity before playing
3. **Input Sanitization**: Validate all user inputs
4. **No Hardcoded Credentials**: API keys in BuildConfig

## Dependencies

### Core
- Kotlin 1.9+
- Coroutines 1.7+

### Network
- Ktor Client 2.3+
- Kotlinx Serialization

### Database
- Room 2.6+
- SQLite

### Media
- Media3 (ExoPlayer) 1.2+
- MediaSession

### UI
- Jetpack Compose
- Material3
- Coil (image loading)

## Testing Strategy

### Unit Tests
- `SpotifyApi`: Mock HTTP responses
- `RecommenderApi`: Test similarity algorithm
- `MusicService`: Test download logic

### Integration Tests
- Database CRUD operations
- End-to-end download flow

### UI Tests
- Compose UI tests
- Navigation flow
- Player controls

## Build Configuration

```kotlin
// build.gradle.kts
android {
    compileSdk = 34
    
    defaultConfig {
        minSdk = 24
        targetSdk = 34
    }
    
    buildFeatures {
        compose = true
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.3"
    }
}
```

## Future Enhancements

1. **Offline Mode**: Full offline support with sync
2. **Playlists**: User-created playlists
3. **Social Features**: Share tracks, follow friends
4. **Equalizer**: Built-in audio effects
5. **Cloud Backup**: Backup library to cloud
6. **Chromecast**: Cast to speakers
7. **Android Auto**: Car integration
8. **Wear OS**: Smartwatch controls

## Contributing

See `CONTRIBUTING.md` for development guidelines.

## License

[Your license here]
