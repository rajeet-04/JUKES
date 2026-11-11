# Codebase Concerns

**Analysis Date:** 2026-05-19

## Tech Debt

**[Update Check Logic in MainActivity.kt]:**
- Issue: The update check logic is embedded directly in MainActivity.kt with complex dialog rendering, making the activity file very large (675 lines) and difficult to maintain
- Files: `app/src/main/main/java/com/example/juke/MainActivity.kt` (lines 176-326)
- Impact: Mixes UI logic with update checking, increases complexity of main activity, makes testing difficult
- Fix approach: Extract update check logic into a separate UpdateViewModel or service class to follow separation of principles

**[SpotifyApi.kt JSON Setter TODO]:**
- Issue: The `json` setter in SpotifyApi.kt contains a TODO() placeholder that is not implemented
- Files: `app/src/main/java/com/example/juke/network/SpotifyApi.kt` (line 140)
- Impact: If the setter is ever called, it will throw a NotImplementedError causing runtime crashes
- Fix approach: Implement the setter properly or remove it if not needed

**[MusicService.kt Stream File Management]:**
- Issue: Complex stream file management with multiple fallback sources (Spotmate/Gamepvz) creates intricate error handling paths
- Files: `app/src/main/java/com/example/juke/services/MusicService.kt` (multiple functions)
- Impact: Difficult to trace download failures, potential for resource leaks if cleanup fails
- Fix approach: Consider extracting download logic into a separate class with clearer error handling contracts

**[UpdateManager.kt Download Manager Usage]:**
- Issue: Uses Android's DownloadManager directly which creates complexity around file paths, permissions, and cleanup
- Files: `app/src/main/java/com/example/juke/services/UpdateManager.kt`
- Impact: Platform-specific code that's difficult to test, potential issues with scoped storage in newer Android versions
- Fix approach: Consider abstracting download functionality behind an interface for better testability

## Known Bugs

**[MusicService.kt Cleanup Orphaned Cache Files Logic]:**
- Symptoms: Potential deletion of actively used stream files under certain conditions
- Files: `app/src/main/java/com/example/juke/services/MusicService.kt` (lines 842-890)
- Trigger: When cleanupOrphanedCacheFiles() is called while stream files are actively being used
- Workaround: The code includes a comment indicating this was fixed, but logic complexity remains
- Fix approach: Review the orphaned file detection logic to ensure it correctly distinguishes between orphaned and active files

**[SpotifyApi.kt Token Expiry Handling]:**
- Symptoms: Potential race conditions in token refresh under high load
- Files: `app/src/main/java/com/example/juke/network/SpotifyApi.kt` (lines 147-228)
- Trigger: Concurrent token requests when token is near expiry
- Workaround: Mutex protection helps but complex refresh logic could still have edge cases
- Fix approach: Consider simplifying token refresh logic or adding more comprehensive tests

**[QueueManager.kt Recommendation Fetch Deduplication]:**
- Symptoms: Potential for recommendation fetch flag to not reset properly on exceptions
- Files: `app/src/main/java/com/example/juke/services/QueueManager.kt` (inferred from docs)
- Trigger: Exception occurs during recommendation fetch before flag is reset
- Workaround: Currently uses try/finally pattern which should be safe
- Fix approach: Verify all exit paths properly reset the flag

## Security Considerations

**[Spotify Credentials Handling]:**
- Risk: Spotify client credentials stored in BuildConfig could potentially be exposed
- Files: `app/src/main/java/com/example/juke/network/SpotifyApi.kt` (lines 156-161), `local.properties`
- Current mitigation: Credentials stored in local.properties which is gitignored
- Recommendations: Consider using Android Keystore or encrypted preferences for additional security

**[Download Manager File Paths]:**
- Risk: UpdateManager uses external storage downloads directory which may be accessible by other apps
- Files: `app/src/main/java/com/example/juke/services/UpdateManager.kt` (line 112)
- Current mitigation: Standard Android practice for app updates
- Recommendations: Consider using app-specific download directories for better isolation

## Performance Bottlenecks

**[Lyrics Search in SpotifyApi.kt]:**
- Problem: Multiple sequential fallback attempts for lyrics lookup can cause noticeable delays
- Files: `app/src/main/java/com/example/juke/network/SpotifyApi.kt` (searchLyrics function, ~1150 lines)
- Cause: Chain of LRLib -> YouTube Captions -> LRLib plain -> YTM -> YT Captions
- Improvement path: Consider caching lyrics results or implementing parallel fallback with timeout

**[Image Loading in JukeApplication.kt]:**
- Problem: Coil image loader configuration uses fixed percentages that may not be optimal for all devices
- Files: `app/src/main/java/com/example/juke/JukeApplication.kt` (lines 13-25)
- Cause: Memory cache set to 25%, disk cache to 2% of total space
- Improvement path: Consider making cache sizes configurable or based on device memory class

**[Stream Cache Eviction in MusicService.kt]:**
- Problem: Stream cache eviction sorts all files each time it's called
- Files: `app/src/main/java/com/example/juke/services/MusicService.kt` (evictStreamCache function, lines 911-951)
- Cause: Sorting operation on file list can be expensive with many cached streams
- Improvement path: Consider using a priority queue or maintaining sorted list incrementally

## Fragile Areas

**[MainActivity.kt Navigation Logic]:**
- Files: `app/src/main/java/com/example/juke/MainActivity.kt` (lines 336-564)
- Why fragile: Complex nested navigation logic with multiple conditional branches and side effects
- Safe modification: Changes should be accompanied by comprehensive UI testing covering all navigation paths
- Test coverage: Moderate - navigation logic is exercised but edge cases may be missed

**[Update Check Dialog Rendering]:**
- Files: `app/src/main/java/com/example/juke/MainActivity.kt` (lines 199-317)
- Why fragile: Complex AlertDialog with multiple conditional branches for emergency updates, prerelease notes, etc.
- Safe modification: Extract dialog content generation to separate composable functions
- Test coverage: Low - dialog logic is complex and difficult to test comprehensively

**[MusicService Download Orchestration]:**
- Files: `app/src/main/java/com/example/juke/services/MusicService.kt` (smartDownloadAndIndex, streamTrack functions)
- Why fragile: Complex retry logic with multiple fallback sources and timeout handling
- Safe modification: Changes should preserve the fallback orchestration logic exactly
- Test coverage: Moderate - core download paths are tested but error scenarios may be limited

**[SpotifyApi Token Management]:**
- Files: `app/src/main/java/com/example/juke/network/SpotifyApi.kt` (getAccessToken function)
- Why fragile: Thread-safe token refresh with complex expiry checking
- Safe modification: Preserve mutex locking behavior and expiry calculation logic
- Test coverage: Moderate - basic functionality tested but concurrent scenarios may need more coverage

## Scaling Limits

**[Recommendation Queue Size]:**
- Current capacity: QueueManager keeps recommendations for currently playing track only
- Limit: System memory constrains how many recommendations can be pre-downloaded
- Scaling path: Consider implementing persistent recommendation cache or limiting based on available storage

**[Stream File Cache]:**
- Current capacity: MusicService keeps max 30 stream files by default (evictStreamCache)
- Limit: Storage space and file descriptor limits on Android
- Scaling path: Consider making cache size configurable based on available storage or implementing more aggressive cleanup

**[Lyrics Cache]:**
- Current capacity: No explicit caching of lyrics results
- Limit: Repeated lyrics lookups for same tracks waste bandwidth and CPU
- Scaling path: Implement LRU cache for lyrics results with time-based eviction

## Dependencies at Risk

**[Spotmate/Gamepvz Download Services]:**
- Risk: Reliance on third-party services for MP3 downloads that could change or become unavailable
- Impact: Core music download functionality would break
- Migration plan: Implement additional download sources or make the fallback system more pluggable

**[Coil Image Loading Library]:**
- Risk: Dependency on third-party image loading library
- Impact: Image loading throughout app would need replacement
- Migration plan: Abstract image loading behind interface to allow swapping implementations

**[Ktor HTTP Client]:**
- Risk: Dependency on Ktor for all HTTP operations
- Impact: Networking layer would need significant rework
- Migration plan: Current usage is relatively encapsulated in ApiClient wrapper

## Missing Critical Features

**[Offline Mode Improvements]:**
- Problem: Limited functionality when offline - primarily just playing already downloaded tracks
- Blocks: Users cannot search or browse library effectively when offline
- Solution: Implement better offline UI states and cache more metadata locally

**[Background Download Management]:**
- Problem: No user-visible control over background download bandwidth usage
- Blocks: Users on metered connections cannot limit data usage
- Solution: Add settings to control download quality and background behavior

**[Advanced Audio Settings]:**
- Problem: Basic audio equalizer and effects are limited
- Blocks: Audiophiles cannot customize sound output
- Solution: Implement more advanced audio processing options

## Test Coverage Gaps

**[Error Handling in Download Flows]:**
- What's not tested: Comprehensive error scenarios for network failures, service unavailability, malformed responses
- Files: `app/src/main/java/com/example/juke/services/MusicService.kt`, `app/src/main/java/com/example/juke/network/SpotifyApi.kt`
- Risk: Download failures could leave app in inconsistent state or cause crashes
- Priority: High - download core functionality is critical to app purpose

**[Edge Cases in Update Manager]:**
- What's not tested: Various download failure scenarios, permission denial flows, storage full conditions
- Files: `app/src/main/java/com/example/juke/services/UpdateManager.kt`
- Risk: Update mechanism could fail silently or leave incomplete downloads
- Priority: Medium - update system is important but not core to daily functionality

**[Navigation Edge Cases]:**
- What's not tested: Rapid navigation sequences, configuration changes during navigation, deep link handling
- Files: `app/src/main/java/com/example/juke/MainActivity.kt`
- Risk: Navigation crashes or incorrect state under unusual usage patterns
- Priority: Medium - navigation is used frequently but edge cases may be rare

**[Concurrent Access to Services]:**
- What's not tested: Multiple simultaneous accesses to MusicService, SpotifyApi from different coroutines
- Files: All service classes
- Risk: Race conditions or inconsistent state under heavy concurrent usage
- Priority: Medium-High - app architecture encourages concurrent access

---
*Concerns audit: 2026-05-19*