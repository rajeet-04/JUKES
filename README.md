# JUKE Music Player

<div align="center">

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-purple.svg)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-UI-blue.svg)](https://developer.android.com/jetpack/compose)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-26-orange.svg)](https://developer.android.com/about/versions/oreo)
[![Version](https://img.shields.io/badge/Version-1.0.0-blue.svg)](https://github.com/rajeet-04/JUKES/releases/tag/v1.0.0)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**A modern Android music streaming app with smart recommendations, Spotify integration, and offline playback**

[Features](#features) • [Screenshots](#screenshots) • [Installation](#installation) • [Architecture](#architecture) • [Contributing](#contributing)

</div>

---

## 📱 Overview

JUKE is a feature-rich Android music player that combines the power of Spotify's search API with YouTube Music's recommendation engine to deliver an exceptional music streaming experience. Download your favorite tracks, import entire playlists, and enjoy seamless playback with automatically generated queues.

## 📦 Latest Release

**JUKE v1.0.0** is now available! 🎉

### What's New in v1.0.0
- ✅ Complete Spotify integration with search and metadata
- ✅ Smart recommendation system using YouTube Music
- ✅ Playlist import with real-time progress tracking
- ✅ Offline playback with local storage
- ✅ Material Design 3 UI with modern interface
- ✅ Synced lyrics support
- ✅ Queue management with swipe gestures
- ✅ Background playback and notification controls

[📥 Download v1.0.0](https://github.com/rajeet-04/JUKES/releases/tag/v1.0.0) | [📋 Release Notes](RELEASE_NOTES.md)

## ✨ Features

### 🎵 Core Features
- **Spotify Integration**: Search and browse millions of tracks, artists, albums, and playlists
- **Smart Downloads**: Automatically download tracks with album art, lyrics, and metadata
- **Playlist Import**: Import entire Spotify playlists with real-time progress tracking
- **Offline Playback**: Play downloaded music without internet connection
- **Synced Lyrics**: Display time-synced lyrics during playback (when available)

### 🎯 Smart Recommendations
- **AI-Powered Queue**: Automatically generates similar song recommendations using YouTube Music's algorithm
- **Seamless Playback**: Pre-downloads upcoming tracks for uninterrupted listening
- **Infinite Radio**: Never-ending music stream based on your current track
- **High-Quality Matches**: Validates recommendations against Spotify for accuracy

### 🎨 User Interface
- **Material Design 3**: Modern, beautiful UI following Google's latest design guidelines
- **Dark Theme**: Eye-friendly dark mode for comfortable viewing
- **Swipe Gestures**: Queue management with intuitive swipe-to-add-next functionality
- **Mini Player**: Persistent mini player for quick playback control
- **Full-Screen Player**: Immersive player with lyrics, queue management, and album art

### 📚 Library Management
- **All Tracks View**: Browse all downloaded music in one place
- **Favorites**: Mark and filter your favorite songs
- **Playlist Organization**: View imported playlists as separate collections
- **Recently Played**: Quick access to your listening history
- **Most Played**: Discover your top tracks

### 🔍 Search & Discovery
- **Multi-Format Search**: Search by track name, artist, album, or URL
- **URL Support**: Direct Spotify URL/URI parsing for tracks, albums, artists, and playlists
- **Artist Deep Dive**: View artist details, top tracks, and full discography
- **Album Exploration**: Browse album tracks with one-click playback
- **Playlist Preview**: View playlist details before importing

### 🎛️ Playback Controls
- **Queue Management**: Drag-to-reorder, swipe-to-delete queue items
- **Add Next**: Smart queue insertion for up-next playback
- **Shuffle & Repeat**: Standard playback modes
- **Skip Controls**: Previous, pause/play, next with seek bar
- **Background Playback**: Continue playing while using other apps
- **Notification Controls**: Media controls in notification shade

## 📸 Screenshots

<!-- Add screenshots here when available -->
```
┌─────────────────────┐  ┌─────────────────────┐  ┌─────────────────────┐
│    Home Screen      │  │   Search Results    │  │   Library View      │
│                     │  │                     │  │                     │
│  Recently Played    │  │  🎵 Tracks          │  │  All Tracks  ⭐     │
│  Most Played        │  │  👤 Artists         │  │                     │
│                     │  │  📀 Playlists       │  │  🎵 Playlists       │
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
- Android SDK 26 or higher
- Spotify Developer Account (for API credentials)

### Setup Instructions

1. **Clone the repository**
   ```bash
   git clone https://github.com/rajeet-04/JUKES.git
   cd JUKES
   ```

2. **Download from Releases** (Recommended for end users)
   
   **Latest Release: [v1.0.0](https://github.com/rajeet-04/JUKES/releases/tag/v1.0.0)**
   
   - Download the APK file from the [releases page](https://github.com/rajeet-04/JUKES/releases)
   - Install the APK on your Android device
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

3. **Build the project**
   ```bash
   ./gradlew assembleDebug
   ```

4. **Install on device/emulator**
   ```bash
   ./gradlew installDebug
   ```

## 🏗️ Architecture

### Tech Stack

**UI Layer**
- **Jetpack Compose**: Declarative UI framework
- **Material Design 3**: Modern design components
- **Navigation Compose**: Type-safe navigation
- **Coil**: Image loading and caching

**Data Layer**
- **Room Database**: Local SQLite database with Flow-based reactive queries
- **Kotlin Coroutines**: Asynchronous programming
- **StateFlow**: Reactive state management

**Network Layer**
- **Ktor Client**: HTTP client for API calls
- **Kotlinx Serialization**: JSON parsing
- **Spotify Web API**: Music metadata and search
- **YouTube Music API**: Recommendation engine
- **Spotdown API**: MP3 downloads
- **LRCLib API**: Lyrics fetching

**Media Playback**
- **Media3 ExoPlayer**: Audio playback engine
- **MediaSession**: System media controls integration
- **Notification Controls**: Background playback support

### Project Structure

```
app/src/main/java/com/example/juke/
├── database/
│   ├── MusicDatabase.kt          # Room database configuration
│   ├── PlaylistEntities.kt       # Playlist & track relationship entities
│   └── DAOs...                   # Data Access Objects
├── models/
│   ├── Track.kt                  # Core track model
│   ├── SpotifyModels.kt          # Spotify API response models
│   └── SpotdownSong.kt           # Download queue model
├── network/
│   ├── SpotifyApi.kt             # Spotify Web API client
│   ├── RecommenderApi.kt         # YouTube Music recommendations
│   └── ApiClient.kt              # Shared HTTP client
├── services/
│   ├── MusicService.kt           # Download & indexing service
│   ├── PlaybackManager.kt        # Media3 playback controller
│   └── QueueManager.kt           # Smart queue & recommendations
├── ui/
│   ├── screens/
│   │   ├── HomeScreen.kt         # Home with recently/most played
│   │   ├── SearchScreen.kt       # Search & playlist import
│   │   ├── LibraryScreen.kt      # Downloaded tracks library
│   │   ├── PlayerScreen.kt       # Full-screen player
│   │   ├── ArtistDetailScreen.kt # Artist info & albums
│   │   ├── AlbumDetailScreen.kt  # Album tracks
│   │   └── PlaylistDetailScreen.kt # Playlist tracks
│   ├── components/
│   │   ├── MiniPlayer.kt         # Bottom mini player
│   │   ├── TrackCard.kt          # Track list item
│   │   └── SwipeToAddNext.kt     # Swipe gesture wrapper
│   └── theme/
│       └── Theme.kt              # Material3 theming
└── viewmodels/
    ├── MusicViewModel.kt         # Playback & download state
    ├── SearchViewModel.kt        # Search & import logic
    ├── LibraryViewModel.kt       # Library filtering
    ├── HomeViewModel.kt          # Home screen data
    └── *DetailViewModels.kt      # Detail screen logic
```

### Data Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                         UI Layer (Compose)                       │
│  HomeScreen │ SearchScreen │ LibraryScreen │ PlayerScreen       │
└───────────────────────┬─────────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────────────────────┐
│                    ViewModel Layer (StateFlow)                   │
│  MusicViewModel │ SearchViewModel │ LibraryViewModel            │
└───────────────────────┬─────────────────────────────────────────┘
                        │
        ┌───────────────┼───────────────┐
        ▼               ▼               ▼
┌──────────────┐ ┌─────────────┐ ┌──────────────┐
│   Services   │ │  Repository │ │  Network     │
│              │ │             │ │              │
│ PlaybackMgr  │ │ Room DB     │ │ Spotify API  │
│ QueueManager │ │ TrackDao    │ │ YouTube API  │
│ MusicService │ │ PlaylistDao │ │ Spotdown API │
└──────────────┘ └─────────────┘ └──────────────┘
```

## 🎼 How It Works

### Smart Recommendation System

1. **Track Selection**: User plays a track
2. **Video Matching**: Find corresponding YouTube Music video
3. **Radio Generation**: Fetch YouTube Music radio queue (50 songs)
4. **Validation**: Validate each song against Spotify API
5. **Filtering**: Remove spam (remixes, covers, karaoke)
6. **Prioritization**: Prioritize official releases
7. **Download Queue**: Auto-download top 10 matches
8. **Continuous Loop**: Repeat when queue drops below threshold

> 📖 For detailed documentation, see [RECOMMENDATION_SYSTEM.md](RECOMMENDATION_SYSTEM.md)

### Playlist Import Flow

1. User pastes Spotify playlist URL
2. App detects URL and fetches playlist metadata
3. Display import button with track count
4. Download each track sequentially
5. Save to Room database with playlist association
6. Update progress indicator in real-time
7. Playlist appears in Library with filter chip

### Download & Indexing

1. Check if track already exists in database
2. Validate Spotify track URL
3. Download MP3 from Spotdown API with retry logic
4. Fetch synced/plain lyrics from LRCLib
5. Download and cache album artwork
6. Save track to local storage
7. Index in Room database
8. Update UI via Flow emissions

## 🛠️ Configuration

### Build Variants

- **Debug**: Development build with logging
- **Release**: Optimized production build with ProGuard

### Gradle Configuration

```kotlin
android {
    compileSdk = 36
    minSdk = 26
    targetSdk = 35
    
    buildFeatures {
        compose = true
        buildConfig = true
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
```

## 📝 API Usage

### Spotify Web API
- **Authentication**: Client Credentials Flow
- **Endpoints**: Search, track details, artist info, album tracks, playlist metadata
- **Rate Limits**: 180 requests/minute (normal tier)

### YouTube Music API
- **Purpose**: Music recommendations and radio generation
- **Method**: Unofficial API via HTTP POST
- **Data**: Track titles, artists, video IDs

### Spotdown API
- **Purpose**: MP3 file downloads
- **Cache**: Server-side caching for faster downloads
- **Format**: High-quality MP3 audio

### LRCLib API
- **Purpose**: Synced and plain lyrics
- **Matching**: By title, artist, album, and duration
- **Format**: LRC format for synced lyrics

## 🔧 Development

### Running Tests

```bash
# Unit tests
./gradlew test

# Instrumented tests
./gradlew connectedAndroidTest
```

### Code Style

This project follows [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html).

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

**Download failures**
- Check available storage space
- Verify internet connection stability
- Check Spotdown API status

**Playback issues**
- Ensure tracks are fully downloaded
- Check file permissions
- Verify ExoPlayer initialization

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- **Spotify** for their comprehensive Web API
- **YouTube Music** for recommendation algorithms
- **Spotdown** for music download infrastructure
- **LRCLib** for lyrics database
- **Google** for Jetpack Compose and Media3
- **Coil** team for image loading library

## 📞 Support

For issues, questions, or feature requests, please [open an issue](https://github.com/rajeet-04/JUKES/issues).

## 🗺️ Roadmap

- [ ] User accounts and cloud sync
- [ ] Social features (share playlists, collaborative queues)
- [ ] Equalizer and audio effects
- [ ] Sleep timer
- [ ] Chromecast support
- [ ] Android Auto integration
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

**MADE WITH ❤️ BY RASH**

[![GitHub](https://img.shields.io/badge/GitHub-100000?style=for-the-badge&logo=github&logoColor=white)](https://github.com/rajeet-04/JUKES)
[![Release](https://img.shields.io/badge/Release-v1.0.0-blue?style=for-the-badge)](https://github.com/rajeet-04/JUKES/releases/tag/v1.0.0)

</div>
