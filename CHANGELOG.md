# JUKES - Release Notes

## Changelog

## [Unreleased]

### Added

### Fixed

---

## [1.0.8-beta] - 2026-02-18


### See full release notes:
[release-v1.0.8-beta.md](release-v1.0.8-beta.md)

---

## [1.0.7-beta] - 2026-02-15

### Added

- **Purge Redundant Tracks**: New storage cleanup feature in Audio Settings. Identifies and deletes unused songs based on: low play count (< 5 plays, > 14 days old), never-played downloads (> 30 days), short audio (< 60s), and incomplete metadata. Excludes Favorites and playlisted tracks. Shows estimated storage savings with multi-select deletion.
- **Radio Mode**: One-tap radio button on the Player Screen. Resets the current queue to only the playing song, clears recommendation history, and fetches fresh recommendations based on the current track for an infinite, discovery-driven listening session.
- **Repeat Mode**: Added Repeat One, Repeat All, and Repeat Off modes. Cycle through modes via a new Repeat button in the player controls next to the Next button.
- **Notification Favorites**: Added a Heart (Favorite) toggle button to the media notification. Tapping it toggles the current track's favorite status in real-time with filled/border heart icon updates.
- **Shuffle Button in Player Controls**: Shuffle toggle now available directly in the player controls for easier access.

### Fixed

- **Shuffle Queue Premature End**: Fixed critical bug where shuffle mode would stop playing after ~14 tracks. Root cause was `player.replaceMediaItem()` in `onMediaItemTransition` which reset ExoPlayer's internal shuffle order/seed. Removed the offending call; metadata is now correctly set when tracks are initially added to the queue.
- **Spotdown API Key Requirement**: Updated `SpotifyApi` to include the required `x-api-key` header for `checkDirectDownload` and `downloadSong` requests. Added `SpotdownCheckResponse` data class for structured error handling. Prevents `Unexpected JSON token` parsing failures.
- **Spotdown Error Handling**: Implemented robust JSON parsing error handling and network exception catching in `SpotifyApi` to gracefully handle API failures and fall through to Spotmate.
- **Shuffle Recommendation Trigger**: Fixed recommendations triggering prematurely during shuffle play. Added `getRemainingTracksCount()` to `PlaybackManager` which traverses ExoPlayer's timeline using `getNextWindowIndex` to accurately count remaining songs in the shuffle order instead of relying on the linear queue index.

---

## [1.0.6-beta] - 2026-01-08

### Added

- **Local Audio Import**: Seamlessly import audio files from device storage. Copied to internal storage `imported_music/` with cover art extraction.
- **Batch Operations**: Multi-select songs in Library to "Add to Queue", "Play Next", or "Add to Playlist".
- **Playlist Management**: Added "Add to Queue" for entire playlists and inline "Rename" functionality.
- **Library UI Refinements**: Updated Library header to display active playlist name and integrated quick action buttons.
- **Player Visuals**: Solid black background for tracks with missing album art.
- **Audio Settings**: Added "Skip Silence" option to skip silent parts at start/end of tracks.

### Fixed

- **Queue Synchronization**: Fixed critical bug where deleting the playing track desynchronized the UI. `MusicViewModel` now observes `queueFlow` for real-time updates.
- **LRCLib Source**: Updated lyrics API endpoint to `https://lrclib.meek.workers.dev` for improved reliability.
- **Batch Deletion**: Optimized deletion logic to use batch database operations.
- **Compilation**: Fixed `PlayerScreen` crash by updating `AddToPlaylistDialog` signature.
- **Queue Duplication**: Implemented intelligent deduplication logic—new tracks are added and duplicates are moved to the end while excluding the currently playing song.
- **Playback Continuity**: Fixed playback restarts and random skips when updating queue structure by precisely maintaining current position and calculating shifted indices.

---

## [1.0.5-beta] - January 2026 (Emergency Release)

> ⚠️ **Emergency Release**: Critical fixes for queue, player, and lyrics issues.

### Added

- **Spotify Region Selector**: New option in Audio Settings to choose your Spotify market region (ISO 3166-1 alpha-2). Includes 20 popular markets (IN, US, GB, PK, NP, BD, LK, etc.) with a searchable dialog. All Spotify API searches now use the selected region for personalized results.
- **Instant Playback with Concurrent Lyrics**: Songs now start playing immediately via streaming while downloading in the background. Lyrics are fetched concurrently and appear within 1-2 seconds of playback start.
- **Queue Persistence for Streaming**: Streaming tracks are now saved to the database immediately, allowing queue restoration across app restarts. When downloads complete, the same track record is updated with the local file path.
- **Deleted Track Safety**: Critical safety features to prevent crashes when tracks are deleted:
  - Deleted tracks are automatically removed from the playback queue
  - If a deleted track is playing, playback automatically skips to the next song
  - File-not-found errors gracefully skip to next track instead of crashing
- **Play Statistics Preservation**: Play count and last played time are now preserved when streaming tracks are downloaded, maintaining accurate listening history.
- **Create Playlist from Player**: Added "Create Playlist" option to the "Add to Playlist" dialog on both Player Screen and Library Screen for quick playlist creation.
- **Improved Delete UX**: When deleting multiple tracks rapidly, pending deletes are now committed immediately before starting a new countdown, allowing true one-swipe deletion.
- **Player Screen Swipe Gestures**: Swipe left/right on the album art to skip to next/previous track.
- **Swipe-to-Open Queue**: Swipe up from the Player Screen bottom controls to open the queue.
- **Refresh Lyrics**: Added "Refresh Lyrics" option to the player 3-dots menu to re-fetch lyrics from LRCLib.
- **Smart Lyrics Fallback**: If primary lyrics search fails for multi-artist tracks, the app now retries with each artist individually (5% duration tolerance for strict matching).
- **Add to Playlist on Player**: Added a "+" button on the Player Screen to manage playlist membership for the current track.
- **Spotmate Fallback**: Added Spotmate as a secondary download source if Spotdown fails, with CSRF token handling.
- **Search Screen Redesign**: Premium redesign with glassmorphic search bar, filter chips (All, Tracks, Artists, Playlists, Albums), and frosted glass track cards. Enhanced empty states and polished result list.
- **Save Playlist Offline**: Added "Save Playlist Offline" button to Playlist Detail screens to download all tracks with progress indicator.

### Fixed

- **Refined Lyrics Selection**: Improved lyrics selection logic to prioritize synced lyrics and tie-break equal scores using duration proximity. This ensures the most accurate version (e.g., standard vs. extended/lo-fi) is selected.
- **Queue Synchronization Fix**: Fixed UI desynchronization when duplicate tracks exist in the queue. The player now uses the exact queue index for state updates instead of `indexOfFirst`, ensuring correct track highlighting.
- **Rapid Skip Race Condition**: Fixed a race condition where quickly skipping tracks caused metadata updates to overwrite the wrong track's info. The player now validates the current track before applying delayed updates.

## [Beta-Released]

### Added

- **Multi-Selection Bulk Delete**: Long-press any track to enter selection mode; features "Select All", batched deletion, and haptic feedback.
- **Enhanced Haptics**: Integrated tactile feedback for track selection, long-press gestures, and destructive confirmations.
- **Undo Timeout Update**: Increased deletion undo window from 3 seconds to 5 seconds for better recoverability.
- **Service Termination Lifecycle**: Application now explicitly stops playback and terminates background service when swiped away from recents.
- **Spotify Navigation Integration**:
  - Clickable artist names in Player Screen with a selection dialog for multi-artist tracks.
  - "Go to Album" and "Share Track" options added to the player context menu.
- **AI & OCR Modules**:
  - Integrated OpenRouter Gemma 3 model for image processing.
  - Local Image-to-Text extraction using Ollama and DeepSeek model.
  - On-device offline OCR using Google ML Kit with CameraX and Text-to-Speech support.
- **Playlist Deletion**: Added option to delete playlists via a context menu on playlist chips.
- **Robust Artist Matching**: Improved duplicate detection for playlist imports and smart downloads (handles multi-artists/case variations).
- **Library Screen Rework**: New UI layout with 70% search field and 10% sort button; includes persistent sort order and shuffle/play controls.
- **Tap-to-Seek**: Added tap gesture to seek bar for instant position changes.
- **Audio Focus Handling**: Music now pauses for other apps and auto-resumes correctly.

### Fixed

- **Samsung Media Notification Sync**: Fixed issue on Samsung devices where playback controls disappeared. Synchronized Notification IDs between manual `startForeground` and Media3 `DefaultMediaNotificationProvider` (ID 1).
- **Playlist Sync**: Fixed `track_count` desynchronization when tracks are deleted from the library.
- **UI Crash (Duplicate Keys)**: Fixed `IllegalArgumentException` in playlist `LazyColumn` by implementing a unique key strategy for duplicate tracks.
- **Background Resume Crash**: Resolved `ForegroundServiceStartNotAllowedException` when resuming playback from background on Android 12+.
- **Library UI**: Fixed Sort button height alignment with search field.
- **Queue Shuffle Logic**: Queue now correctly respects shuffle state when adding tracks.
- **Duplicate Download Bug**: Fixed race condition and cross-path duplication issues when adding songs to the download queue via different methods (swipe vs. click).
- **Lyrics Persistence Bug**: Refreshed lyrics now persist correctly when navigating between queue tracks.
- **Notification Thumbnail Repaint**: Added 500ms delay to notification metadata update to force thumbnail refresh on track change.

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
