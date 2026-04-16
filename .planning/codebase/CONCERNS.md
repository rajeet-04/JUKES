# Codebase Concerns

**Analysis Date:** 2026-04-16

## Tech Debt

**Large Files:**
- Issue: Several files exceed 1000 lines
- Files: `MusicViewModel.kt` (1400+), `QueueManager.kt` (1136), `SpotifyApi.kt` (1239), `RecommenderApi.kt` (931)
- Impact: Harder to navigate, understand, and modify
- Fix approach: Extract to smaller helper classes or utilities

**Singleton Pattern Overuse:**
- Issue: `PlaybackManager`, `QueueManager`, `MusicDatabase` use singleton pattern
- Files: `services/PlaybackManager.kt`, `services/QueueManager.kt`, `database/MusicDatabase.kt`
- Impact: Difficult to test, tight coupling
- Fix approach: Consider dependency injection

**Magic Numbers:**
- Issue: Hardcoded numbers throughout codebase
- Examples: `30_000` buffer time, `256L * 1024 * 1024` cache size, `180` rate limit
- Files: `PlaybackService.kt`, `MusicService.kt`, `SpotifyApi.kt`
- Impact: Unclear meaning, hard to maintain
- Fix approach: Extract to named constants

**Commented Code:**
- Issue: Historical commented code remains in files
- Files: `MusicService.kt` (line 632 commented delete), `MusicDatabase.kt` (multiple commented sections)
- Impact: Clutter, confusion
- Fix approach: Remove dead code

## Known Bugs

**Stream Cache Eviction Timing:**
- Symptom: Playback crashes when stream file evicted during play
- Files: `services/MusicService.kt`, `services/QueueManager.kt`
- Trigger: LRU eviction runs while track is playing
- Workaround: `pinnedUuids` parameter partially mitigates

**Notification Artwork Lag:**
- Symptom: Stale or missing album art in notification
- Files: `services/PlaybackService.kt`
- Trigger: Thumbnail not downloaded when notification created
- Workaround: 3-second delayed metadata refresh (line 446-463)

## Security Considerations

**Spotify Credentials:**
- Risk: API keys stored in `local.properties` (not committed)
- Files: `app/build.gradle.kts` (reads local.properties)
- Current mitigation: File is gitignored
- Recommendations: Use Gradle secrets plugin or environment variables

**HTTP Downloads:**
- Risk: MP3 files downloaded over HTTP
- Files: `services/MusicService.kt`, `network/SpotifyApi.kt`
- Current mitigation: Downloads from known services only
- Recommendations: Use HTTPS for all downloads

**No ProGuard Optimization of Logs:**
- Risk: Debug logs remain in release builds
- Files: `proguard-rules.pro`
- Current mitigation: `-assumenosideeffects` removes Log.d/v/i/w in release
- Recommendations: Already addressed

## Performance Bottlenecks

**Database Queries:**
- Problem: Full scans for search operations
- Files: `database/MusicDatabase.kt`
- Cause: LIKE queries on large datasets
- Improvement path: Add FTS (Full-Text Search) index

**Recommendation Fetching:**
- Problem: Sequential YouTube Music API calls for ensemble seeds
- Files: `services/QueueManager.kt`
- Cause: `coroutineScope { async { } }` but limited parallelism
- Improvement path: Parallel fetching with rate limiting

**Large Download Queue:**
- Problem: UI thread blocked during queue operations
- Files: `viewmodels/MusicViewModel.kt`
- Cause: Heavy operations in `addNext()` and `setQueue()`
- Improvement path: Background processing with progress updates

## Fragile Areas

**Spotify Token Management:**
- Files: `network/SpotifyApi.kt`
- Why fragile: Token expiry race condition, no refresh mechanism mid-request
- Safe modification: Always use `tokenMutex.withLock`
- Test coverage: Gap - no unit tests

**YouTube Music API:**
- Files: `network/RecommenderApi.kt`
- Why fragile: Unofficial API, parsing JSON with deep nested paths
- Safe modification: Add null checks for each JSON extraction
- Test coverage: Gap - no unit tests

**ExoPlayer State Management:**
- Files: `services/PlaybackService.kt`, `services/PlaybackManager.kt`
- Why fragile: Complex state machine across service/controller boundary
- Safe modification: Always check player initialization with `::player.isInitialized`
- Test coverage: Gap - no unit tests

## Scaling Limits

**Database:**
- Current capacity: Unknown
- Limit: SQLite performance degrades with millions of rows
- Scaling path: Paginate queries, add indices

**Stream Cache:**
- Current capacity: 256MB ExoPlayer cache + LRU 30 files stream cache
- Limit: Storage dependent
- Scaling path: Configurable cache size

**Queue Manager:**
- Current capacity: 6 concurrent downloads
- Limit: Memory and network bandwidth
- Scaling path: Configurable concurrency

## Dependencies at Risk

**Spotmate/Gamepvz:**
- Risk: Unofficial services, may change or go offline
- Impact: Downloads fail
- Migration plan: Add fallback services (spotDL, youtubedl)

**mp3juice3.ninja:**
- Risk: Unofficial scraper, may break
- Impact: Video matching fails
- Migration plan: Alternative YouTube API

**LRCLib:**
- Risk: Community-maintained service
- Impact: Lyrics unavailable
- Migration plan: Multiple lyrics sources (Genius, Musixmatch)

## Missing Critical Features

**Offline Mode:**
- Problem: App requires network for initial song selection
- Blocks: Full offline usage
- Priority: High

**Playlist Sync:**
- Problem: Playlists stored locally only
- Blocks: Multi-device sync
- Priority: Medium

## Test Coverage Gaps

**Untested Areas:**
- `MusicService` - No unit tests for download logic
- `QueueManager` - No unit tests for recommendation algorithm
- `SpotifyApi` - No mocking of network responses
- `PlaybackManager` - No state machine tests
- UI screens - No Compose UI tests

**Risk:** Changes to these components may break silently

**Priority:** High for MusicService and QueueManager (core business logic)

---

*Concerns audit: 2026-04-16*
