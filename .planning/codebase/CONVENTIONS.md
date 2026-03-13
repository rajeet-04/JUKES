# Coding Conventions

**Analysis Date:** 2026-03-08

## Naming Patterns

**Files:**
- PascalCase for class files: `MusicViewModel.kt`, `SpotifyApi.kt`
- Descriptive names matching class purpose

**Functions:**
- camelCase for function names: `searchSongs()`, `downloadSong()`
- Verb-based naming for actions: `play()`, `pause()`, `download()`
- Boolean functions prefixed with `is` or `has`: `isPlaying()`, `hasRestoredState()`

**Variables:**
- camelCase for variables: `accessToken`, `trackList`, `uiState`
- Descriptive names with clear purpose
- Constants in UPPER_SNAKE_CASE: `SPOTIFY_API_BASE_URL`, `TAG`

**Types:**
- Data classes for models: `Track`, `SpotifyTrack`, `DownloadItem`
- Sealed classes for state representation: `DownloadStatus`
- Enums for fixed sets of values: `ScreenState`

## Code Style

**Formatting:**
- Standard Kotlin formatting with 4-space indentation
- Line length generally kept under 120 characters
- Consistent spacing around operators and keywords

**Linting:**
- Android lint checks integrated via Gradle
- Kotlin compiler warnings treated as errors in strict mode

## Import Organization

**Order:**
1. Standard Java/Android imports
2. Third-party library imports
3. Project-relative imports (com.example.juke.*)

**Path Aliases:**
- No custom path aliases used
- Full package imports always used

## Error Handling

**Patterns:**
- Try-catch blocks for network operations and file I/O
- Custom exception types for specific error cases: `OfflineException`
- Result types for suspending functions where appropriate
- Proper logging of errors with appropriate log levels

## Logging

**Framework:** Android Log system with tag-based categorization

**Patterns:**
- Log.d for debug information
- Log.e for error conditions
- Log.w for warning conditions
- Consistent tagging with class name or functional area

## Comments

**When to Comment:**
- Function documentation for public APIs
- Complex algorithm explanations
- Non-obvious implementation decisions

**JSDoc/TSDoc:**
- KotlinDoc-style comments for classes and public functions
- `@param` and `@return` annotations for function documentation

## Function Design

**Size:** Functions typically under 50 lines, with complex logic extracted to helper functions

**Parameters:** 
- Named parameters preferred for functions with multiple arguments
- Default parameter values used to reduce function overloads

**Return Values:** 
- Suspended functions for async operations
- Sealed classes or Result types for operations that can fail
- Non-null returns preferred with safe-call operators

## Module Design

**Exports:** 
- Public functions and classes clearly marked
- Internal visibility for implementation details not meant for external consumption

**Barrel Files:** 
- No barrel files used
- Direct imports of specific classes preferred

---

*Convention analysis: 2026-03-08*