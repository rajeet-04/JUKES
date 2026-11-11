## JUKE v1.0.8-beta Release Notes

### 📦 Latest Release
**JUKE v1.0.8-beta** is now available! 🎉

---

### What's New
- 🔍 **Search Screen Redesign**: Complete visual overhaul with glassmorphism, filter chips, and improved layout.
- 📊 **Most Played Sort**: Sort library tracks by play count.
- 📱 **Portrait Lock**: Orientation locked to portrait on phones for consistent UX.
- 🗑️ **Purge Redundant Tracks**: Clean up unused files (from v1.0.7).
- 📻 **Radio Mode**: Infinite discovery (from v1.0.7).

---

### ✨ Features
- **Spotify Integration**: Search and browse millions of tracks, artists, albums, and playlists
- **Region Selection**: Choose your Spotify market for localized search results
- **Smart Downloads**: Automatically download tracks with album art, lyrics, and metadata
- **Instant Playback**: Stream songs immediately while background download completes
- **Playlist Import**: Import entire Spotify playlists with real-time progress tracking
- **Save Playlist Offline**: Download all tracks from any playlist with one click
- **Offline Playback**: Play downloaded music without internet connection
- **Synced Lyrics**: Display time-synced lyrics during playback (fetched concurrently)
- **AI-Powered Queue**: Automatically generates similar song recommendations using YouTube Music's algorithm
- **Seamless Playback**: Pre-downloads upcoming tracks for uninterrupted listening
- **Infinite Radio**: Never-ending music stream based on your current track
- **High-Quality Matches**: Validates recommendations against Spotify for accuracy
- **Material Design 3**: Modern, beautiful UI following Google's latest design guidelines
- **Glassmorphic Search**: Premium translucent search bar with filter chips
- **Dark Theme**: Eye-friendly dark mode for comfortable viewing
- **Swipe Gestures**: Queue management with intuitive swipe-to-add-next and swipe-to-open-queue
- **Mini Player**: Persistent mini player with progress line and swipe skip gestures
- **Full-Screen Player**: Immersive player with lyrics, queue management, album art, swipe-to-skip gestures, and artist navigation
- **All Tracks View**: Browse all downloaded music in one place
- **Favorites**: Mark and filter your favorite songs
- **Playlist Organization**: View imported playlists as separate collections
- **Recently Played**: Quick access to your listening history
- **Most Played**: Discover your top tracks
- **Multi-Format Search**: Search by track name, artist, album, or URL
- **URL Support**: Direct Spotify URL/URI parsing for tracks, albums, artists, and playlists
- **Artist Deep Dive**: View artist details, top tracks, and full discography
- **Album Exploration**: Browse album tracks with one-click playback
- **Playlist Preview**: View playlist details before importing
- **Queue Management**: Drag-to-reorder, swipe-to-delete queue items
- **Add Next**: Smart queue insertion for up-next playback
- **Shuffle & Repeat**: Standard playback modes
- **Skip Controls**: Previous, pause/play, next with seek bar
- **Background Playback**: Continue playing while using other apps
- **Notification Controls**: Media controls in notification shade
- **Sleep Timer**: Auto-stop playback after 1 minute to 3 hours
- **10-Band Equalizer**: Professional-grade audio equalizer with 10 frequency bands
- **Volume Booster**: Enhanced volume up to 200% amplification
- **Phone Call Handling**: Auto-pause on incoming calls, resume when call ends

---

### 🛠️ Fixes
- **Device Orientation**: Fixed orientation consistency across devices.

---

### 🏗️ Architecture & Tech Stack
- **UI Layer**: Jetpack Compose, Material Design 3, Navigation Compose, Coil
- **Data Layer**: Room Database, Kotlin Coroutines, StateFlow
- **Network Layer**: Ktor Client, Kotlinx Serialization, Spotify Web API, YouTube Music API, Spotdown API, LRCLib API
- **Media Playback**: Media3 ExoPlayer, MediaSession, Notification Controls

---

### 🔧 Development
- **Unit tests**: `./gradlew test`
- **Instrumented tests**: `./gradlew connectedAndroidTest`
- **Kotlin Coding Conventions**: Enforced
- **Git Workflow**: Feature branches, PRs, release tagging

---

### 🐛 Troubleshooting
- **Spotify credentials not configured**: Ensure `SPOTIFY_CLIENT_ID` and `SPOTIFY_CLIENT_SECRET` are in `local.properties`, rebuild after adding.
- **No search results**: Check internet, verify credentials, try different terms.
- **Download failures**: Check storage, internet, Spotdown API status.
- **Playback issues**: Ensure tracks are fully downloaded, check permissions, verify ExoPlayer.

---

### 📄 License
AGPL v3

---

### 🙏 Acknowledgments
- Spotify, YouTube Music, Spotdown, LRCLib, Google, Coil

---

### 🗺️ Roadmap
- [x] Equalizer and audio effects
- [x] Sleep timer
- [x] Instant streaming playback
- [x] Queue persistence
- [x] Spotify region selector
- [x] Player screen swipe gestures
- [x] Queue synchronization for duplicates
- [ ] User accounts and cloud sync
- [ ] Social features (share playlists, collaborative queues)
- [ ] Chromecast support
- [ ] Android Auto integration
- [ ] Podcast support
- [ ] Crossfade and gapless playback