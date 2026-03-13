# Codebase Concerns

**Analysis Date:** 2026-03-08

## Tech Debt

**Incomplete Implementation:**
- Issue: Stubbed function with TODO marker in SpotifyApi.kt
- Files: `app/src/main/java/com/example/juke/network/SpotifyApi.kt` (line 132)
- Impact: Potential runtime issues if setter is called
- Fix approach: Implement proper setter logic or remove if not needed

**Large Files / God Classes:**
- Issue: Several extremely large files with multiple responsibilities
- Files: 
  - `app/src/main/java/com/example/juke/viewmodels/MusicViewModel.kt` (appears to be very large based on directory output)
  - `app/src/main/java/com/example/juke/network/SpotifyApi.kt` (1145 lines)
  - `app/src/main/java/com/example/juke/services/PlaybackService.kt` (96,977 lines from directory output)
  - `app/src/main/java/com/example/juke/services/QueueManager.kt` (39,451 lines from directory output)
- Impact: Hard to maintain, understand, and test; violates single responsibility principle
- Fix approach: Extract related functionality into separate classes or modules

## Known Bugs

**No Explicit Bugs Found:**
- Symptoms: No explicit bug markers found in codebase
- Files: N/A
- Trigger: N/A
- Workaround: N/A

## Security Considerations

**Hardcoded Secrets:**
- Risk: API key exposed in source code
- Files: `app/src/main/java/com/example/juke/JukeApplication.kt`
- Current mitigation: API key is in source code but potentially could be obfuscated
- Recommendations: Use secure keystore or runtime configuration for sensitive keys

**Network Security:**
- Risk: Cleartext traffic enabled in manifest
- Files: `app/src/main/AndroidManifest.xml` (usesCleartextTraffic="true")
- Current mitigation: Allows HTTP traffic for development/debugging
- Recommendations: Disable cleartext traffic in production builds using build flavors

## Performance Bottlenecks

**Large ViewModel:**
- Problem: MusicViewModel appears extremely large
- Files: `app/src/main/java/com/example/juke/viewmodels/MusicViewModel.kt`
- Cause: Multiple concerns (playback, queue management, downloads, UI state) in one class
- Improvement path: Split into focused ViewModels or extract business logic to separate classes

**Blocking Operations on Main Thread:**
- Problem: Potential blocking operations in ViewModel init
- Files: `app/src/main/java/com/example/juke/viewmodels/MusicViewModel.kt`
- Cause: Database migration helper called directly in init
- Improvement path: Ensure all database operations are properly dispatched to IO thread

## Fragile Areas

**Complex State Management:**
- Files: `app/src/main/java/com/example/juke/viewmodels/MusicViewModel.kt`
- Why fragile: Heavy reliance on coordinated state flows between multiple managers
- Safe modification: Add comprehensive tests before modifying state propagation logic
- Test coverage: Appears minimal based on test files

**Network Layer Complexity:**
- Files: `app/src/main/java/com/example/juke/network/SpotifyApi.kt`
- Why fragile: Multiple fallback mechanisms and complex error handling
- Safe modification: Ensure thorough testing of all fallback paths
- Test coverage: No specific network tests found

## Scaling Limits

**Room Database Migrations:**
- Current capacity: Version 7 with 6 migrations
- Limit: Manual migration complexity increases with each version
- Scaling path: Consider automated migration strategies or consolidation

**Concurrent Downloads:**
- Current capacity: Single active download with queue
- Limit: No apparent parallelization of downloads
- Scaling path: Implement concurrent download manager with configurable thread pool

## Dependencies at Risk

**PostHog Version:**
- Risk: Dynamic version resolution ("3.32.+") may cause instability
- Impact: Potential breaking changes without notice
- Migration plan: Pin to specific stable version

## Missing Critical Features

**Comprehensive Testing:**
- Problem: Minimal test implementation with only placeholder tests
- Blocks: Confidence in code changes, regression prevention
- Files: `app/src/test/java/com/example/juke/ExampleUnitTest.kt`, `app/src/androidTest/java/com/example/juke/ExampleInstrumentedTest.kt`

**Static Analysis:**
- Problem: No explicit linting or static analysis configuration
- Blocks: Automated code quality enforcement
- Files: Missing configuration files for linting tools

## Test Coverage Gaps

**No Business Logic Tests:**
- What's not tested: Core playback logic, network operations, database operations
- Files: All major service and ViewModel classes
- Risk: High chance of regressions and undetected bugs
- Priority: High

**UI Tests:**
- What's not tested: Screen interactions and user flows
- Files: All UI components and screens
- Risk: UI bugs may go unnoticed
- Priority: Medium

---

*Concerns audit: 2026-03-08*