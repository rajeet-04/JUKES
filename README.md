# JUKE Music Player

<div align="center">

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple.svg)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-UI-blue.svg)](https://developer.android.com/jetpack/compose)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-26-orange.svg)](https://developer.android.com/about/versions/oreo)
[![Compile SDK](https://img.shields.io/badge/Compile%20SDK-36-blue.svg)](https://developer.android.com/about/versions/api-levels)
[![Version](https://img.shields.io/badge/Version-2.3.2--beta-blue.svg)](https://github.com/rajeet-04/JUKES/releases/tag/v2.3.2-beta)
[![License: AGPL v3](https://img.shields.io/badge/License-AGPL%20v3-blue.svg)](https://www.gnu.org/licenses/agpl-3.0.html)

**A modern Android music streaming app with smart recommendations, Spotify integration, offline playback, and advanced audio features**

[Features](#features) • [Screenshots](#screenshots) • [Installation](#installation) • [Architecture](#architecture) • [Contributing](#contributing)

</div>

---

## 📱 Overview

JUKE is a feature-rich Android music player that combines Spotify's search API, YouTube Music's recommendation engine, and local playback to deliver an exceptional music experience. Download tracks, import playlists, enjoy smart recommendations, and customize your audio with equalizer and effects. Supports offline playback, synced lyrics, and seamless queue management.

## 📦 Latest Release

**JUKE v2.3.2-beta** is now available! 🚀

**Incremental Beta Release** focused on smarter lyrics fallback, queue-aware download recovery, and faster search interactions.

What's new:

- Three-stage lyrics fallback chain (LRCLib -> YouTube Music lyrics -> YouTube captions)
- Spotmate queue-aware recovery with immediate fallback and deferred task polling
- Faster split search architecture with debounced autocomplete suggestions

[📥 Download v2.3.2-beta](https://github.com/rajeet-04/JUKES/releases/tag/v2.3.2-beta) | [📝 Full Release Notes](v2.3.2-beta-RELEASE_NOTES.md)

## ✨ Features

### 🎵 Core Playback

- **ExoPlayer-based playback** using Android Media3 with foreground service and MediaSession integration
- **Audio focus handling** - Automatic pause/resume on audio focus change
- **Call state detection** - Pause during calls, auto-resume after call ends (requires `READ_PHONE_STATE` permission)
- **Stream mode** - Play tracks without permanent download (256MB LRU cache)
- **Queue management** - Smart queue with recommendations, pre-fetching, reorder, remove, and play-from-queue
- **Shuffle & Repeat** - Standard modes plus Off/One/All repeat options
- **Sleep timer** - Configurable auto-stop (1 minute to 3 hours)
- **Play count tracking** - Increments at 50% playback completion (once per session)

### 🔍 Search & Discovery

- **Unified search** across Spotify (tracks, artists, playlists, albums) and local library
- **Spotify URL support** - Paste track/artist/playlist/album URLs for direct playback
- **YouTube Music suggestions** - Debounced (100ms) live suggestions with 64-entry cache
- **Recent searches** - Last 5 queries, removable
- **Search filters** - Tracks, Local Tracks, Artists, Playlists, Albums tabs
- **Import playlists** - Batch download with progress indicator (max 6 concurrent)

### 🎼 Smart Recommendations

- **AI-powered queue** - YouTube Music radio queue validated against Spotify for accuracy
- **Online recommendations** - YouTube Music → Spotify validation → Spam filter
- **Offline fallback** - Score-based local library recommendations (artist/album match, favorites, play count)
- **Infinite Radio** - Start radio mode to keep queue populated with similar tracks
- **Pre-fetch system** - Ensures next 3 tracks are ready for uninterrupted playback
- **Blacklist management** - Block artists from appearing in recommendations

### 📚 Library Management

- **Download tracks** from Spotify via Spotmate/Gamepvz proxy services
- **Playlist support** - Create, import from Spotify, manage tracks, drag reorder
- **Favorites system** - Mark/unmark tracks as favorites
- **Sort options** - Recently added, Title, Artist, Last played, Most played
- **Search within library** - Filter by title, artist, or lyrics content
- **Selection mode** - Multi-select tracks for batch operations
- **Swipe to delete** with 5-second undo window
- **Purge stale tracks** - Remove tracks not played in 14+ days or downloaded 30+ days ago
- **Import audio files** - Add local audio files via content URI

### 🎛️ Audio Effects

- **Volume Booster** - Up to 100% boost (0-500dB range)
- **Loudness Normalization** - DynamicsProcessing with limiter (Android 9+)
- **Skip Silence** - Automatically skip silent passages

### 🎙️ Lyrics Features

- **Synced lyrics** - LRC format with offset adjustment
- **5-tier fallback chain**:
  1. LRCLib synced lyrics (primary)
  2. YouTube Captions synced LRC
  3. LRCLib plain lyrics
  4. YouTube Music plain lyrics
  5. YouTube Captions plain text
- **Romanized lyrics** - Convert lyrics to Roman characters
- **Mini-player lyrics** - Optional ROMajiized synced lyrics in mini-player
- **Refresh lyrics** - Re-fetch from LRCLib on demand

### 🎨 User Interface

- **Material Design 3** - Modern, beautiful UI following Google's latest guidelines
- **Jetpack Compose** - Declarative UI with state management via StateFlow
- **Dark Theme** - Eye-friendly dark mode
- **Home Screen** - Time-based greeting banner, Recently Played, Most Played, Favorites sections
- **Search Screen** - Live suggestions, recent searches, tabbed results
- **Library Screen** - Tab toggle (All Tracks/Favorites), playlists, sort options
- **Player Screen** - Full-screen with backdrop gradient, mini-player, synced lyrics, queue view
- **Detail Screens** - Artist (top tracks, albums), Album (track list), Playlist (import, reorder)
- **Settings Screen** - Audio settings, stream mode, recommendations, market code, blacklist

### 🎛️ Playback Controls

- **Mini Player** - Persistent bottom player with thumbnail, title, artist, play/pause, next, lyrics toggle
- **Full-Screen Player** - Seek bar, shuffle/repeat, queue view, favorite toggle, share track
- **Notification Controls** - Media controls with favorite and download buttons (for streams)
- **Background Playback** - Continue playing while using other apps
- **Android Auto Support** - Browse Recently Played, Favorites, All Tracks, custom commands

### 🔧 Other Features

- **Portrait Lock**: Lock portrait mode for phones (<600dp smallest width)
- **Edge-to-Edge Display**: Full-screen content with system bar insets
- **Dynamic Theme**: Player screen colors extracted from album art (Vibrant → Light Vibrant → Dark Vibrant → Dominant → Muted)
- **Intent Handling**: Share tracks via Spotify link, paste Spotify URLs in search, open player via intent extra
- **Update System**: GitHub Releases check on launch, emergency update detection for critical releases
- **Error Resilience**: Retry with exponential backoff, offline fallback, download source fallback (Spotmate → Gamepvz), offline recommendations
- **Image Loading**: Coil with thumbnail pre-warming, 25% memory cache, 2% disk cache

## 📸 Screenshots

<!-- Add screenshots here when available -->

```
┌─────────────────────┐  ┌─────────────────────┐  ┌─────────────────────┐
│    Home Screen      │  │   Search Results    │  │   Library View      │
│                     │  │                     │  │                     │
│  Recently Played    │  │  🎵 Tracks          │  │  All Tracks  ⭐    │
│  Most Played        │  │  👤 Artists         │  │                     │
│  Favorites          │  │  📀 Playlists       │  │  🎵 Playlists      │
└─────────────────────┘  └─────────────────────┘  └─────────────────────┘

┌─────────────────────┐  ┌─────────────────────┐  ┌─────────────────────┐
│   Now Playing       │  │   Artist Detail     │  │  Playlist Import    │
│                     │  │                     │  │                     │
│   🎵 Track Info     │  │  Top Tracks         │  │  Importing...       │
│   🎚️ Progress Bar   │  │  Albums (Grid)      │  │  Progress: 45/100   │
│   📝 Synced Lyrics  │  │                     │  │  ▓▓▓▓▓▓▓▓░░         │
└─────────────────────┘  └─────────────────────┘  └─────────────────────┘
```

## 🚀 Installation

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- Java 11
- Kotlin 2.0.21
- Gradle 8.13.1
- Android SDK 36 (compile/target)
- Spotify Developer Account (for API credentials)

### Setup Instructions

1. **Clone the repository**

   ```bash
   git clone https://github.com/rajeet-04/JUKES.git
   cd JUKES
   ```

2. **Download from Releases** (Recommended for end users)

   **Latest Release: [v2.3.2-beta](https://github.com/rajeet-04/JUKES/releases/tag/v2.3.2-beta)**
   - Download the APK file from the [releases page](https://github.com/rajeet-04/JUKES/releases)
   - Install the APK on your Android device (min SDK 26+)
   - Grant necessary permissions when prompted

3. **Configure Spotify API**
   - Visit [Spotify Developer Dashboard](https://developer.spotify.com/dashboard)
   - Create a new app
   - Copy your Client ID and Client Secret
   - Create `local.properties` in the project root:

     ```properties
     sdk.dir=/path/to/android/sdk
     SPOTIFY_CLIENT_ID=your_client_id_here
     SPOTIFY_CLIENT_SECRET=your_client_secret_here
     ```

   > 📖 For detailed Spotify setup instructions, see [SPOTIFY_SETUP.md](SPOTIFY_SETUP.md)

4. **Build the project**

   ```bash
   ./gradlew assembleDebug
   ```

5. **Install on device/emulator**

   ```bash
   ./gradlew installDebug
   ```

## 🏗️ Architecture

### Tech Stack

**UI Layer**

- **Jetpack Compose**: Declarative UI framework
- **Material Design 3**: Modern design components
- **Navigation Compose**: Type-safe navigation
- **Coil**: Image loading and caching (25% memory cache, 2% disk cache)

**Data Layer**

- **Room Database**: Local SQLite database with entities for Tracks, Playlists, PlaylistTracks
- **Kotlin Coroutines**: Asynchronous programming
- **StateFlow**: Reactive state management
- **SharedPreferences**: Playback state persistence (queue, index)

**Network Layer**

- **Ktor Client**: HTTP client with 30s connect timeout, 120s read/request timeout, retry on failure
- **Kotlinx Serialization**: JSON parsing
- **Spotify Web API**: OAuth2 Client Credentials flow, search, metadata, pagination
- **YouTube Music API**: Recommendation engine, video matching, lyrics
- **Spotmate/Gamepvz**: MP3 download proxies with fallback support
- **LRCLib API**: Lyrics database with 5-tier fallback
- **GitHub Releases API**: Update checking with version comparison

**Media Playback**

- **Media3 ExoPlayer**: Audio playback engine with 256MB LRU stream cache
- **MediaLibraryService**: Foreground service with MediaSession
- **Audio Focus**: Automatic pause/resume with call state detection
- **Notification Controls**: Custom layout with favorite/download buttons

### Project Structure

```
app/src/main/java/com/example/juke/
├── JukeApplication.kt          # Application class with Coil config, portrait lock
├── MainActivity.kt              # Main activity with navigation routes
├── database/
│   ├── MusicDatabase.kt        # Room database configuration
│   ├── Track.kt                 # Core track model
│   ├── PlaylistEntities.kt     # Playlist & track relationship entities
│   └── DAOs/
│       ├── TrackDao.kt          # Track database operations
│       └── PlaylistDao.kt       # Playlist database operations
├── models/
│   ├── SpotifyModels.kt        # Spotify API response models
│   ├── GithubRelease.kt        # GitHub release model
│   └── SpotdownSong.kt         # Search result model (deprecated, use Spotify models)
├── network/
│   ├── ApiClient.kt            # Shared Ktor HTTP client
│   ├── SpotifyApi.kt           # Spotify Web API, Spotmate, Gamepvz, LRCLib
│   └── RecommenderApi.kt       # YouTube Music recommendations
├── services/
│   ├── PlaybackService.kt      # Media3 foreground service
│   ├── PlaybackManager.kt      # MediaController singleton
│   ├── QueueManager.kt         # Smart queue & recommendations
│   ├── MusicService.kt         # Download & indexing service
│   ├── AudioEffectController.kt # Equalizer, booster, normalization
│   └── UpdateManager.kt        # GitHub release update checker
├── ui/
│   ├── screens/
│   │   ├── HomeScreen.kt       # Home with recently/most played
│   │   ├── SearchScreen.kt     # Search & playlist import
│   │   ├── LibraryScreen.kt    # Downloaded tracks library
│   │   ├── PlayerScreen.kt     # Full-screen player
│   │   ├── ArtistDetailScreen.kt # Artist info & albums
│   │   ├── AlbumDetailScreen.kt # Album tracks
│   │   ├── PlaylistDetailScreen.kt # Playlist tracks
│   │   ├── AudioSettingsScreen.kt # Audio & app settings
│   │   └── PurgeSelectionScreen.kt # Cache cleanup
│   ├── components/             # Reusable UI components
│   └── theme/                  # Material3 theming
├── viewmodels/                 # State management
│   ├── MusicViewModel.kt       # Playback & download state
│   ├── SearchViewModel.kt      # Search & import logic
│   ├── LibraryViewModel.kt     # Library filtering
│   ├── HomeViewModel.kt        # Home screen data
│   └── *DetailViewModels.kt    # Detail screen logic
└── analytics/
    └── AnalyticsManager.kt     # App event tracking
```

### Data Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                         UI Layer (Compose)                      │
│  HomeScreen │ SearchScreen │ LibraryScreen │ PlayerScreen       │
└───────────────────────┬─────────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────────┐
│                    ViewModel Layer (StateFlow)                  │
│  MusicViewModel │ SearchViewModel │ LibraryViewModel            │
└───────────────────────┬─────────────────────────────────────────┘
                        │
        ┌───────────────┼───────────────┐
        ▼               ▼               ▼
┌──────────────┐ ┌─────────────┐ ┌──────────────────┐
│   Services   │ │  Repository │ │  Network         │
│              │ │             │ │                  │
│ PlaybackMgr  │ │ Room DB     │ │ Spotify API      │
│ QueueManager │ │ TrackDao    │ │ YouTube API      │
│ MusicService │ │ PlaylistDao │ │ Spotmate/Gamepvz │
└──────────────┘ └─────────────┘ └──────────────────┘
```

## 🎼 How It Works

### Smart Recommendation System

1. **Track Selection**: User plays a track
2. **Video Matching**: Find corresponding YouTube Music video (title/artist similarity, duration check)
3. **Radio Generation**: Fetch 50-track YouTube Music radio queue
4. **Spotify Validation**: Validate each song against Spotify (artist similarity, duration proximity)
5. **Filtering**: Remove spam (remixes, covers, karaoke, AI)
6. **Prioritization**: Prioritize official releases, favorites, play count
7. **Queue Population**: Pre-fetch next 3 tracks, auto-download recommendations
8. **Continuous Loop**: Repeat when queue drops below threshold

> 📖 For detailed documentation, see [RECOMMENDATION_SYSTEM.md](RECOMMENDATION_SYSTEM.md)

### Playlist Import Flow

1. User pastes Spotify playlist URL
2. App detects URL and fetches playlist metadata (pagination support)
3. Display import button with track count
4. Download each track with max 6 concurrent, dual source fallback (Spotmate → Gamepvz)
5. Save to Room database with playlist association
6. Update progress indicator in real-time
7. Playlist appears in Library with filter chip

### Download & Indexing

1. Check if track already exists in database
2. Validate Spotify track URL
3. Download MP3 from Spotmate/Gamepvz with retry (5 attempts, exponential backoff)
4. Validate MP3 (ID3/MP3 frame header) and duration (±5s tolerance)
5. Fetch synced/plain lyrics from LRCLib with 5-tier fallback
6. Download and cache album artwork
7. Save track to local storage
8. Index in Room database
9. Update UI via Flow emissions

## 🛠️ Configuration

### Build Variants

- **Debug**: Development build with logging
- **Release**: Optimized production build with ProGuard

### Gradle Configuration

```kotlin
android {
    compileSdk = 36
    minSdk = 26
    targetSdk = 36

    buildFeatures {
        compose = true
        buildConfig = true
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}
```

### Key Dependencies

```kotlin
// UI
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.material3:material3")
implementation("io.coil-kt:coil-compose:2.5.0")

// Database
implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")
ksp("androidx.room:room-compiler:2.6.1")

// Network
implementation("io.ktor:ktor-client-android:2.3.7")
implementation("io.ktor:ktor-client-content-negotiation:2.3.7")
implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.7")

// Media
implementation("androidx.media3:media3-exoplayer:1.2.0")
implementation("androidx.media3:media3-session:1.2.0")

// Audio Effects
implementation("androidx.media:media:1.7.0")
```

### App Settings

All settings are stored in `music_settings_prefs` and `audio_effects_prefs`:

- **Stream Mode**: Play without saving (default: false)
- **Volume Booster**: 0-100% boost (default: 0)
- **Loudness Normalization**: Android 9+ (default: false)
- **Skip Silence**: Auto-skip silent passages (default: false)
- **Recommendation Count**: 3-15 songs (default: 5)
- **Market Code**: Spotify region (ISO 3166-1 alpha-2, default: "IN")
- **Romanized Lyrics**: Convert to Roman characters (default: false)
- **Mini-Player Lyrics**: Show lyrics in mini-player (default: true)
- **Equalizer**: 10-band levels, toggle on/off

## 🔧 Development

### Running Tests

```bash
# Unit tests
./gradlew test

# Instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest

# Build smoke test
./gradlew assembleDebug
```

### Code Style

This project follows [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html) and project-specific conventions in [.github/copilot-instructions.md](.github/copilot-instructions.md).

### Git Workflow

1. Create feature branch: `git checkout -b feature/amazing-feature`
2. Commit changes: `git commit -m 'Add amazing feature'`
3. Push to branch: `git push origin feature/amazing-feature`
4. Open Pull Request

## 🐛 Troubleshooting

### Common Issues

**"Spotify credentials not configured"**

- Ensure `SPOTIFY_CLIENT_ID` and `SPOTIFY_CLIENT_SECRET` are in `local.properties`
- Rebuild the project after adding credentials

**No search results**

- Check internet connection
- Verify Spotify credentials are correct
- Try different search terms
- Check Spotify market code in settings

**Download failures**

- Check available storage space
- Verify internet connection stability
- Check Spotmate/Gamepvz status
- Retry failed downloads from queue

**Playback issues**

- Ensure tracks are fully downloaded
- Check file permissions
- Verify ExoPlayer initialization
- Check audio focus settings

**Lyrics not showing**

- Verify internet connection for LRCLib fetch
- Check lyrics settings (enable synced lyrics)
- Adjust lyrics offset if sync is off

## 📄 License

This project is licensed under the AGPL v3 - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- **Spotify** for their comprehensive Web API
- **YouTube Music** for recommendation algorithms
- **Spotmate/Gamepvz** for music download infrastructure
- **LRCLib** for lyrics database
- **Google** for Jetpack Compose and Media3
- **Coil** team for image loading library

## 📞 Support

For issues, questions, or feature requests, please [open an issue](https://github.com/rajeet-04/JUKES/issues).

## 🗺️ Roadmap

- [x] ~~Equalizer and audio effects~~
- [x] ~~Sleep timer~~
- [x] ~~Instant streaming playback~~
- [x] ~~Queue persistence~~
- [x] ~~Spotify region selector~~
- [x] ~~Player screen swipe gestures~~
- [x] ~~Queue synchronization for duplicates~~
- [x] ~~Android Auto integration~~
- [x] ~~Blacklist management~~
- [x] ~~Lyrics offset adjustment~~
- [x] ~~Purge stale tracks~~
- [ ] User accounts and cloud sync
- [ ] Social features (share playlists, collaborative queues)
- [ ] Chromecast support
- [ ] Podcast support
- [ ] Crossfade and gapless playback
- [ ] Lyrics editing
- [ ] Custom theme colors

## 👥 Contributors

Contributions are welcome! Please read our [Contributing Guidelines](CONTRIBUTING.md) before submitting a PR.

---

<div align="center">

**Made with ❤️ using Kotlin and Jetpack Compose**

[⬆ Back to Top](#juke-music-player)

**MADE WITH ❤️ BY MEEK**

[![GitHub](https://img.shields.io/badge/GitHub-100000?style=for-the-badge&logo=github&logoColor=white)](https://github.com/rajeet-04/JUKES)
[![Release](https://img.shields.io/badge/Release-v2.3.2--beta-blue?style=for-the-badge)](https://github.com/rajeet-04/JUKES/releases/tag/v2.3.2-beta)

</div>
