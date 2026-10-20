# JUKES - Release Notes

## Changelog

## [Unreleased]

## [2.6.0-beta] - 2026-10-20

### Added

- Recommendations now match YouTube Music: the radio is seeded with the song's album-track (audio) upload, not a stored music-video id (about 36 of 50 picks in common instead of 16). The backend does the same.
- Personalised radio from a connected YouTube Music account (Settings toggle: personalised backend radio first, or anonymous direct radio first).
- Android Auto: list-style browse (Recently Played, Favorites, Downloaded), voice and search playback, resume on reconnect, and cover art.
- Playlists: "Custom order" sort with drag-to-reorder; the order is saved per playlist and kept across restarts.
- Queue: dragging a song slides the other rows aside to make room; the queue changes once on drop.
- Albums: Play, Shuffle, Play next and Add to queue (queueing places the songs saved offline).
- Home: press and hold any card or row for Play, Play next and Add to queue (album cards: Open album, Play, Shuffle).
- Player: with synced lyrics, the artwork rides up and the current lyric line shows under it (tap opens the full lyrics).

### Changed

- Spotify credentials moved off the device into a Cloudflare Worker (token cached in KV); builds need `SPOTIFY_TOKEN_KEY` instead of the client id and secret.
- Lyrics are fetched from `lrclib.net` directly; the shared worker was rate-limited and has been removed (older builds lose lyrics).

### Fixed

- Search no longer fails when Spotify returns a playlist with a missing owner.
- Restarting the app no longer fetches recommendations when the restored queue is already full.
- Synced lyrics keep the active line at its reading position; unsynced lyrics are left-aligned, larger and remember their scroll position per song; inline word timestamps no longer show in lyric lines.
- The search field no longer takes focus or shows the keyboard while the full player is open.
- Playlists no longer get the same song twice, existing repeats are cleaned up once (database v12), and reordering a playlist with repeats no longer crashes.

See full release notes: [v2.6.0-beta-RELEASE_NOTES.md](docs/v2.6.0-beta-RELEASE_NOTES.md)

## [2.5.1-beta] - 2026-10-12

### Fixed

- Playback no longer pauses and stutters about 3 seconds into every song: the delayed notification-artwork refresh replaced the playing item and seeked, which flushed the audio buffer. It now does nothing when the artwork and text are already right, and only seeks if a replace really reset the position.
- The notification and lock-screen artwork and title now follow track changes while the app is in the background or the screen is off. Updates were skipped whenever the app wasn't in the foreground, so they only refreshed once playback paused (seen on an OriginOS phone).

See full release notes: [v2.5.1-beta-RELEASE_NOTES.md](docs/v2.5.1-beta-RELEASE_NOTES.md)

## [2.5.0-beta] - 2026-10-12

### Added

- Save the queue as a playlist from the "Save as playlist" button in Up Next or the player's ⋮ menu: name it (suggested "<first song> Mix"), and optionally bring the greyed waiting songs, which are matched on Spotify and download in the background like a playlist import.
- Playlists made in the app get cover art from their songs when they have none: one song fills it, two split it, three are one large plus two stacked, four make a 2x2 mosaic; an empty playlist gets a tinted tile with a note instead of a blank square.
- Up Next shows the whole YouTube Music radio at once. Songs not fetched yet are greyed (desaturated artwork, dimmed text) and colour up as they become ready; tapping one fetches it right away and plays it (the waiting songs above it are skipped, as in any queue).
- Swipe right on a queued song to play a copy next while the original keeps its place. A waiting song swiped right is fetched first and held greyed as "Plays next"; if the current song ends before it is ready, playback continues with ready songs and the pick slots in after whatever is playing once it loads.
- New app icon, fully vector: a black J-note over a grey vinyl record on white, with a monochrome layer for Android 13 themed icons. The PNG/WebP launcher bitmaps are gone; `app/src/main/ic_launcher-playstore.svg` is the master.
- Neon edge on the selected Home / Search / Library tab, in the open tab bar and in the folded button beside the mini player: a rim of light in the album accent, a soft bloom and a glowing icon; it flares while the selection slides between tabs.

### Changed

- The mini player and the full player are now one surface: opening grows the glass bar into the full-screen player and minimising shrinks it back into the bar (container transform with shared artwork), instead of a black sheet sliding over the bar. Motion uses a critically damped spring, and a swipe-down hands its release velocity to the settle. The header, title row, progress bar, controls and bottom actions arrive in stages and leave in reverse. Honors Remove animations.
- One motion language across the app: screens no longer use Navigation's default 700 ms fade. Switching Home / Search / Library is a quick fade-through, and opening a detail screen (album, artist, playlist, settings) slides in on a critically damped spring and slides back out on Back. Track rows and cards settle slightly in on press and spring back (`ui/theme/Motion.kt`).
- The mini player stays visible on the Settings pages (including Power tools and Purge) so you can see and control what's playing while adjusting the equalizer or volume; the tab bar stays hidden there, and the settings sub-pages no longer show it either.
- Smoother lyrics: they fade in over the artwork instead of popping, the active line eases between emphasis states without re-wrapping the text or bouncing, the list glides to the next line on a spring, and switching between synced, loading and plain lyrics crossfades.
- Recommendations follow the playing song's YouTube Music radio in its own order. One radio at a time; the next starts only when it runs out, seeded from the last queued song. Songs heard, skipped or shown a lot lately wait at the end. The weighted random mix of several radios is gone.
- Only the songs about to play are fetched: the Recommendation Queue setting (5–15) is how many load ahead, and each one joins the player as soon as it (and the ones before it) is ready instead of after the whole batch. This also cuts load on the backend.
- Up Next rows are flat Library-style rows with hairline separators, and swiping uses the same glass reveal as Library and Search.
- Song identity reads brackets instead of cutting them: upload labels (Official Video, Lyrics, HD, From "Movie", Remastered, a year) are ignored, versions (Remix, Live, Acoustic, Slowed, Female Version, Lo-fi...) count as different songs, and credits (feat., with) plus alternate titles are decided by length, so "Pal Pal" and the longer "Pal Pal (with Talwiinder)" are two songs. Spotify's " - Female Version" matches YouTube's "(Female Version)", "A&B" artist lists match "A, B", and Spotify matching penalises a different version (`network/SongTitle.kt`).

### Fixed

- Downloading a streamed song no longer resets its favourite, play count or last played: the download kept the stats of the queue's (older) copy of the song instead of the stored ones.
- "Fetch from another source" now really swaps in the new audio and starts it from 0:00; the player used to keep playing what it had open because the new file has the same path.
- Glass sheet headers (Up Next's back, title and menu) were nearly invisible: sheets now set their own text colour instead of inheriting the player's tint.
- Recommended songs no longer repeat in blocks or get queued twice: the player queue is extended only with the songs the engine just fetched (it used to re-append every song it had queued this session that the player lacked, e.g. after Clear played or while a previous add was still in flight), and the player skips IDs it already holds.
- Reordering the queue or reopening the app no longer restarts the recommendation session and forgets what was already played; auto-advanced songs now count as played.
- A YouTube radio that doesn't start with its seed song (an unrelated generic list YouTube sometimes returns) is ignored instead of filling the queue with off-topic songs.
- Library snackbars (removal, undo, "will play next") and the selection bar sit directly above the player chrome and follow it as it folds into the merged player, instead of floating above a gap.

- Downloading a song within its first few seconds no longer restarts it from 0:00; the delayed notification-artwork refresh keeps the current source and playback position.
- Opening the app while a later song is playing no longer briefly shows (and then switches to) another song; the UI queue sync now follows the player's live index.
- Starting a mix, enqueueing a whole playlist or clearing the queue no longer causes a blink/delay in the player; cache eviction and media-item file checks run on a background thread.

See full release notes: [v2.5.0-beta-RELEASE_NOTES.md](docs/v2.5.0-beta-RELEASE_NOTES.md)

## [2.4.2-beta] - 2026-10-09

### Added

- Weighted cross-session variety for recommendations: decaying play/skip/recommended exposure per song (DB v11) so the same seed no longer yields the same radio picks every session.
- Progressive playback, on by default (switch in Audio settings): start on the backend's HLS stream while the song downloads.
- Spotify and YouTube / YouTube Music links open in JUKES from the share sheet and as tapped links, including `spotify.link` short links.
- Manual "Check for updates" button in Audio settings (bypasses the 6-hour and "Not now" throttles).
- Faster cold start: baseline and startup profile, background warm-up of the HTTP client, database and stream cache.

### Fixed

- Evicted or cache-cleared stream and download audio is re-downloaded ahead of playback and on ENOENT instead of being skipped.
- The Spotify token response is no longer written to logcat.
- Queue restore no longer blocks on the network; unplayable tracks are dropped and stale `localUri` values are cleared.

See full release notes: [v2.4.2-beta-RELEASE_NOTES.md](docs/v2.4.2-beta-RELEASE_NOTES.md)

## [2.4.1-beta] - 2026-10-07

### Added

- Home discovery feed (new releases from your artists, history-based picks) with per-section skeletons, offline notice and auto-reload.
- Flat Library and Search with glass swipe reveals, offline album saving, JukeIcons icon set.
- Update sheet with live download progress and a smarter GitHub check.
- Softer haptic feel with a tab-change tap and a long buzz for delete.

### Changed

- Recommendations use YouTube Music directly first, the shared backend only as fallback.
- Smaller release APK: ARM-only (arm64-v8a, armeabi-v7a), unused libraries removed, tighter R8 shrinking.

See full release notes: [v2.4.1-beta-RELEASE_NOTES.md](docs/v2.4.1-beta-RELEASE_NOTES.md)

## [2.3.5-beta] - 2026-10-04

### Added

- Power Tools (locked until the version line is tapped 7 times): preferred audio source, repeat threshold, mini-player gestures, diagnostics and JSON export.
- Queue tools (shuffle upcoming, sort A–Z, clear played, save as playlist) and a Search tab re-tap that selects the query.

### Fixed

- Player backdrop reaches the bottom edge; Power Tools list scrolls.
- 30-second previews and wrong-length audio are rejected: fetched length is verified against Spotify and the source is switched and remembered.

See full release notes: [v2.3.5-beta-RELEASE_NOTES.md](docs/v2.3.5-beta-RELEASE_NOTES.md)

## [2.3.4-beta] - 2026-10-04

### Added

- Liquid glass UI revamp with redesigned player, search, and library screens; reserve-based recommendations.
- In-pipeline volume/bass boost with stable volume AGC, edge-only skip silence, playback speed, and queue status.
- Call handling via audio focus and a shared audio controller.
- Resumable Spotify playlist imports that survive app close and network loss.
- Repeat-aware recommendations (daily plays), reseeding from recent songs when a radio runs dry, and offline recommendations scored from play statistics.

### Fixed

- Forward seeking while a track is still streaming now switches to the completed local file at the requested position.
- Crashes opening Search and during playback with idle audio processors; tab navigation returns to the tab root.

See full release notes: [v2.3.4-beta-RELEASE_NOTES.md](docs/v2.3.4-beta-RELEASE_NOTES.md)

## [2.3.2-beta] - 2026-04-27

### Added

- Three-stage lyrics fallback chain: LRCLib primary lookup with YouTube Music lyrics and YouTube captions fallbacks.
- Search UX upgrade with YouTube Music autocomplete, debounced typing suggestions, and submit-only full Spotify metadata searches.
- Search tab multi-tap behavior in `MainActivity` (single tap navigate, double tap reset-and-focus, triple tap focus-without-clear).
- Mini-player lyrics intelligence: music-symbol filtering and adaptive gap threshold fallback.

### Fixed

- Spotmate queue responses (`queued`/`processing`) now trigger typed queue handling instead of hard-failing convert requests.
- Playback/download resilience improved through immediate provider fallback and deferred Spotmate task polling recovery.
- Search keyboard handling refined with scroll-to-dismiss and reliable delayed focus triggers.

See full release notes: [v2.3.2-beta-RELEASE_NOTES.md](v2.3.2-beta-RELEASE_NOTES.md)

## [2.3.1-beta] - 2026-04-15

### Added

- Ensemble Recommendation Engine improvements (robust seed pool, parallel network intersections, offline fallback scoring).
- Streaming playback & download resilience (seamless stream promotion, optimized source routing, async lyrics and YouTube ID hydration).
- UI & UX polish: bottom action pill, mini-player redesign, animated play/pause transition, Material 3 player menu refresh, Search/Home/AudioSettings M3 updates.
- Haptics engine upgrade with semantic haptics and `VIBRATE` permission.

### Fixed

- Queue race condition and duplication resolved via atomic `replaceMediaItem()` swaps.
- LRU streaming stability fixes (eviction protection, redundant download prevention).
- Duplicate stream request avoidance and related stability improvements.

See full release notes: [v2.3.1-beta-RELEASE_NOTES.md](v2.3.1-beta-RELEASE_NOTES.md)

## [2.3.0-beta] - 2026-04-12
 
### Added
 
- **LRU Streaming Cache**: Implemented a 15-file LRU disk cache for streaming playback. Temporary stream files are automatically evicted as the limit is reached, optimizing storage usage.
- **Dual Download Sources**: Integrated "Gamepvz" as a secondary download source alongside "Spotmate". Includes load-balanced source selection and automatic fallback on errors or duration mismatches.
- **Stream/DB Decoupling**: Streaming tracks are no longer persisted to the database by default, keeping the user's library clean and focused on permanent downloads.
 
### Fixed
 
- **Lyrics Auto-Scroll**: Fixed a bug where synced lyrics wouldn't scroll to the active line when toggled on during paused playback.
- **Library Bloat**: Updated library queries to filter out streaming tracks, ensuring an organized view across the app.
- **Startup Cleanup**: Added a background process to purge legacy stale stream entries from the persistent database.
 
### See full release notes
 
[v2.3.0-beta-RELEASE_NOTES.md](v2.3.0-beta-RELEASE_NOTES.md)
 
---

## [2.2.0-beta] - 2026-03-18

### Added

- **Stream Playback Caching**: Integrated ExoPlayer `SimpleCache` with a 256MB LRU disk cache. Seeking back to already-buffered parts of a stream is now instant and doesn't require re-fetching from the network.
- **Artist Blacklist UI**:
  - Added "Blacklist Artist" option to the Player Screen more options menu.
  - Added a dedicated Blacklist toggle button on the Artist Detail screen.
  - Added a "Blacklisted Artists" management section in Audio Settings.

### Fixed

- **Radio Mode Pause**: Optimized `startRadio` to surgically trim the playback queue using `removeMediaItem` instead of resetting the entire player. This prevents the noticeable audio drop when starting radio mode.
- **Foreground Service Crash**: Added robust exception handling for `ForegroundServiceStartNotAllowedException` when the app is in the background on Android 12+.
- **Shuffle Logic**: Fixed a bug where the shuffle toggle failed to correctly update the playback queue or track sequencing.
- **Search Screen Crash**: Resolved `IllegalArgumentException` in the search results list caused by duplicate keys in `LazyColumn`.
- **Stream Seek Crash**: Fixed `MalformedURLException` (no protocol) when playing local files through the new `CacheDataSource` by using `DefaultDataSource` as the upstream factory.

### See full release notes

[v2.2.0-beta-RELEASE_NOTES.md](v2.2.0-beta-RELEASE_NOTES.md)

---

## [2.1.1-beta] - 2026-03-03

### Fixed

- Swipe-to-Add-Next firing multiple times per gesture
- Player pausing/freezing on 403 HTTP error

### See full release notes

[v2.1.1-beta-RELEASE_NOTES.md](v2.1.1-beta-RELEASE_NOTES.md)

---

## [2.1.0-beta] - 2026-03-02

### Added

- **Secure API Key via Cloudflare KV**: Spotdown API key is no longer bundled in the APK. Fetched at runtime from a Cloudflare Worker backed by KV storage. Key can be rotated without an app update.
- **Referer Header for Spotdown**: All Spotdown download/check requests now include `Referer: https://spotdown.org/` as required by the API.
- **Automatic Download Fallback**: Spotdown ↔ Spotmate auto-fallback on any error or duration mismatch exceeding 10%. Silent, no user action required.
- **Emergency Update Dialogs**: Update checker now distinguishes emergency (`hotfix`/`critical`/`emergency` tags) from normal updates — red themed, non-dismissible for emergencies.
- **Scrollable Update Changelog**: Release notes inside the update dialog are now fully scrollable.
- **Fully Responsive Player Screen**: `BoxWithConstraints` used to scale all player elements (artwork, icons, spacers, typography) based on 3 device height breakpoints — compact, normal, and large/tablet.
- **HomeScreen Settings Button Fix**: `Modifier.weight(1f)` on greeting column prevents the settings button from being pushed off-screen by long greetings.

### Fixed

- Spotdown API 403 due to missing Referer and API key headers
- Download silently failing when Spotdown rejected request
- Duration mismatch not triggering download source fallback
- Update dialog text unreadable on dynamic dark/light themes
- Emergency updates dismissible (should be forced)
- Player album art overflowing into controls on small-screen phones
- Lyrics close button displaced due to wrong `statusBarsPadding` in clipped lyrics frame
- Player controls using fixed sizes regardless of device screen size

### See full release notes

[v2.1.0-beta-RELEASE_NOTES.md](v2.1.0-beta-RELEASE_NOTES.md)

---

## [2.0.0-stable] - 2026-02-20

### Overview

**First Major Stable Release** - JUKE v2.0.0-stable consolidates all features from v1.0.0 through v1.0.8-beta into a production-ready, battle-tested music streaming application.

### Status: Production Ready

- ✅ Proven stability through 8 beta releases
- ✅ Zero breaking changes from v1.0.x
- ✅ Seamless upgrade path for all users
- ✅ Complete feature parity with v1.0.8-beta

### Key Achievements

- **50+ Features**: Comprehensive music streaming platform
- **4 API Integrations**: Spotify, YouTube Music, Spotdown, LRCLib
- **Zero Crashes**: < 0.1% crash rate from beta testing
- **Professional Audio**: 10-band equalizer, volume booster, sleep timer
- **Offline-First**: Download and keep music forever
- **Beautiful UI**: Material Design 3 with glassmorphism

---

## [1.0.8-beta] - 2026-02-18

### See full release notes

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
