# JUKES - Release Notes

## Changelog

## [Unreleased]

### Added

- **Playlist Deletion**: Added option to delete playlists via a context menu on playlist chips
- **Robust Artist Matching**: Improved duplicate detection for playlist imports and smart downloads
  - Handles multiple artists (comma, &, feat)
  - Ignores artist order and case variations to prevent re-downloads
- **Library Screen Rework**: New UI layout with 70% search field and 10% sort button in a single row
- **Sort Options**: Added sorting by Recently Added (default), Title, Artist, and Last Played
- **Sort Bottom Sheet**: Modal sheet to select sorting options with visual feedback
- **Shuffle & Play Controls**: Added shuffle toggle and play button to Library Screen header
  - Shuffle button shows active state with primary color
  - Play button plays all visible/filtered tracks
  - Shuffle mode shuffles queue before playback
- **Tap-to-Seek**: Added tap gesture to seek bar for instant position changes
- **Audio Focus Handling**: Music now pauses for other apps (YouTube, notifications) and auto-resumes when they stop
  - Preserves manual playback control during phone calls

### Fixed

- **Library UI**: Fixed Sort button height alignment with search field
- Sort order now persists after playing tracks (no longer resets to Recently Played)
- Queue respects shuffle state - shuffled tracks are added in random order when shuffle is enabled

### Performance & Build

- **Queue Recommendation Reliability**: Fixed critical issue where recommendations stopped generating after prolonged use or app restart.
  - Implemented proper lifecycle reset for `QueueManager` to prevent "zombie" instance state.
- **ProGuard Optimization**: Updated rules to safely strip logging for battery saving while preserving critical data models and Room database classes.

## [1.0.1-beta_2] - December 2025

This release brings significant UI/UX improvements, enhanced queue management, and several bug fixes for a more polished music experience.

---

## ✨ New Features

### Playlist Management

- **Create Playlists**: Create custom playlists directly from the Library screen via the "New" chip button.
- **Add/Remove Tracks**: Easily add tracks to playlists using the **+** icon, or remove them with the **-** icon when viewing a playlist.
- **Context-Aware Dialog**: The "Add to Playlist" dialog now shows which playlists a track is already in and allows removing from those playlists directly.
- **Shuffle Play**: Shuffle any list of tracks (All Tracks, Favorites, or a specific Playlist) with a single tap from the Library header.

### Smart Recommendation System

- **Intelligent Queue Generation**: Automatic recommendations based on currently playing tracks using YouTube Music API integration.
- **Spotify Validation**: All recommendations are validated against Spotify to ensure high-quality matches (70% minimum confidence).
- **Background Downloads**: Concurrent download management with pre-buffering of next 2 songs for seamless playback.
- **Infinite Queue**: Automatic queue replenishment when songs drop to ≤2 remaining.
- **Spam Filtering**: Automatic filtering of remixes, covers, karaoke, and low-quality variants.
- See [RECOMMENDATION_SYSTEM.md](RECOMMENDATION_SYSTEM.md) for comprehensive documentation and [QUICK_START_RECOMMENDATIONS.md](QUICK_START_RECOMMENDATIONS.md) for integration guide.

### Enhanced Artist Matching

- **Multi-Artist Parsing**: Intelligent parsing of artist strings to handle collaborations and featured artists.
- **Order-Independent Matching**: Artist order no longer affects recommendation matching (e.g., "Drake, The Weeknd" matches "The Weeknd, Drake").
- **Flexible Similarity Scoring**: Uses Levenshtein distance for name matching with >60% threshold for valid matches.
- **Weighting System**: 70% average artist similarity + 30% matched artist ratio for balanced scoring.
- **Confidence Boosts**: Contextual confidence boosts based on artist match quality (0.05 to 0.15).
- See [ARTIST_MATCHING_ENHANCEMENT.md](ARTIST_MATCHING_ENHANCEMENT.md) for technical details and [ARTIST_MATCHING_QUICK_REFERENCE.md](ARTIST_MATCHING_QUICK_REFERENCE.md) for quick reference.

### App Update Checker

- **Automatic Updates**: Checks for new releases (including betas) on app launch.
- **In-App Notification**: Alerts the user with a dialog if a newer version is available on GitHub.
- **Direct Download**: Provides a direct link to the release page for easy downloading.

### Call Handling

- **Smart Pause**: Playback automatically pauses on incoming calls (`RINGING`) via `BroadcastReceiver`.
- **Manual Control**: Users can manually resume playback during a call (`OFFHOOK`). This is enabled by disabling ExoPlayer's automatic Audio Focus handling, preventing the system from blocking manual play requests during active calls.
- **Runtime Permissions**: Integrated `READ_PHONE_STATE` permission request in `MainActivity` on app launch.

### Notification Thumbnails

- **Optimized Thumbnails**: Separate 64x64 thumbnails are now stored for media notifications, reducing memory usage while maintaining 640x640 images for the player screen.
- **Database Migration**: Existing songs are gracefully handled; notifications fall back to the larger image if the new thumbnail is unavailable.

---

## 🎨 UI/UX Enhancements

### Search Screen Redesign

- **Modern Search Bar**: Visually refreshed search input with improved clarity and feedback.
- **Enhanced Track Display**: Better visual indicators for download status and improved spacing/typography.
- **Polished Empty States**: More informative and visually appealing empty and error states.

### MiniPlayer Redesign

- **Progress Line**: The MiniPlayer now displays a progress line indicating the current track position.
- **Swipe Gestures**: Swipe left to skip to the next track, swipe right for the previous track.

### Library Screen Redesign

- **Unified Filter Row**: All filter options (All Tracks, Favorites, Playlists) are now in a single, horizontally scrollable row.
- **Lazy Loading**: Optimized performance with "load when needed" patterns.
- **Centered Empty States**: Improved text alignment for empty library messages.

---

## 🔧 Improvements & Bug Fixes

### Queue Management Enhancements

- **Recommendation Variety**: Recently played artists are now tracked to influence future song recommendations, preventing repetitive suggestions.
- **Duplicate Prevention**: The system now prevents duplicate songs from being added to the queue, whether through recommendations or manual user additions.
- **"Move" Functionality**: Adding a song already in the queue now moves it to the new position instead of creating a duplicate.
- **Queue Hydration Safety**: Fixed queue contamination issue where selecting a new song while recommendations were downloading would pollute the new queue with old recommendations.
- **App Restart Duplication Fix**: Fixed issue where tracks from index 0 were duplicated and added below the last track when app was restarted. Removed duplicate `QueueManager.initializeQueue()` call in `MusicViewModel.loadRestoredQueue()`.
- **Auto-Resume After Call**: Implemented smart playback handling for phone calls. Music now explicitly pauses on incoming/outgoing calls and automatically resumes when the call ends, provided it was playing before the interruption.
- See [QUEUE_HYDRATION_FIX.md](QUEUE_HYDRATION_FIX.md) for technical details on the fix.

### Track Matching Accuracy

- **Exact Match Logic**: Database lookups for tracks now use exact matching for title and artist (case-insensitive) and include a duration check (±2 seconds tolerance). This prevents issues like "Jhol" incorrectly playing "Jhol - Acoustic".

### Equalizer Fix

- **Sound Output Debugging**: Addressed issues where adjusting the equalizer in Audio Settings did not affect sound output. Ensured equalizer settings are correctly applied to the audio player's session.

### Notification Visibility Fix (Samsung One UI 6.x+)

- **Media Notification Fix**: Fixed notification not appearing on Samsung Galaxy S25 and Tab S11+ devices (Android 14/15). Uses platform MediaStyle notification formatting to ensure Samsung devices recognize it as a valid media player notification.
- **Background Playback Crash Fix**: Fixed `ForegroundServiceStartNotAllowedException` crash when resuming playback from notification while app is in background. Added manual `startForeground()` call with proper Android 14+ service type (`FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK`).

### Navigation & State Fixes

- **Search Tab Behavior**: Detail screens (Artist, Playlist, Album) now correctly navigate back to the main Search screen when the Search tab is clicked, and the Search tab remains highlighted on these screens.
- **Playlist URL Flag**: Fixed a bug where the "Import Playlist" button could incorrectly appear for non-playlist URLs.
- **Library Scroll Reset**: Prevented unwanted scroll reset in the Library when deleting tracks or toggling favorites.
- **Lyrics Reset**: Lyrics now correctly reset to the beginning when a new track starts playing.

---

## 📦 Technical Notes

- `PlaylistDao` updated with `getPlaylistsForTrack` for reverse lookup.
- `LibraryViewModel` methods (`addToPlaylist`, `removeFromPlaylist`) are now `suspend` functions for better state synchronization.
- `TrackDao.findTrackByTitleArtist` signature updated to include `durationSec` for accurate matching.
- `ValidatedRecommendation` now includes `durationSec`.
