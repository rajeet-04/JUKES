# Coding Conventions

**Analysis Date:** 2026-05-19

## Naming Patterns

**Files:**
- Kotlin files: PascalCase - `MusicService.kt`, `TrackCard.kt`
- Screen composables: PascalCase - `HomeScreen.kt`, `PlayerScreen.kt`
- Component composables: PascalCase - `MiniPlayer.kt`, `TrackCard.kt`

**Functions:**
- camelCase: `playTrack()`, `addToQueue()`, `getTrackByUuid()`
- Private helper: `_internalMethod()` or `processSomething()`
- Suspend functions: Same naming, clearly marked as suspend
- ViewModel factory functions: `viewModel()` delegate

**Variables:**
- camelCase: `musicViewModel`, `currentTrack`, `isPlaying`
- Mutable vs Immutable: `MutableStateFlow` for private, `StateFlow` for public
- Constants: SCREAMING_SNAKE_CASE in companion objects or top-level
- Flow variables: Prefixed with `_` for private mutable, public as StateFlow

**Types:**
- Classes: PascalCase - `MusicViewModel`, `TrackEntity`
- Data classes: PascalCase - `MusicUiState`, `DownloadItem`
- Enums: PascalCase - `DownloadStatus.QUEUED`
- Type aliases: PascalCase
- Sealed classes: PascalCase for class names, lowercase for objects

## Code Style

**Formatting:**
- Tool: Gradle Kotlin DSL (built-in)
- 4-space indentation
- No line length limit (Compose often exceeds 100 chars)
- Trailing commas preferred in multi-line lists
- Spacing around operators and after commas

**Linting:**
- Not explicitly configured (no detekt or ktlint)
- Relies on Kotlin compiler and IDE
- Basic code quality maintained through team practices

## Import Organization

**Order:**
1. Android framework (`android.*`)
2. Kotlin standard library (`kotlin.*`)
3. AndroidX libraries (`androidx.*`)
4. Third-party libraries (coil, ktor, etc.)
5. App local imports (`com.example.juke.*`)

**Example:**
```kotlin
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.compose.material3.*
import kotlinx.coroutines.flow.MutableStateFlow
import io.ktor.client.*
import com.example.juke.database.MusicDatabase
import com.example.juke.models.Track
```

## Error Handling

**Patterns:**
- Custom exceptions for specific errors: `OfflineException`, `SpotmateQueuedException`
- Try-catch with logging: `try { } catch (e: Exception) { Log.e(TAG, ...); throw e }`
- Extension function for offline detection: `Throwable.isOffline()`
- Graceful degradation with fallback paths
- Result types for API responses where appropriate
- Early returns for invalid states

**Logging:**
```kotlin
private const val TAG = "ClassName"
Log.d(TAG, "MethodName: action description")
Log.e(TAG, "Error occurred", e)
Log.w(TAG, "Warning message")
```

## Comments

**When to Comment:**
- Complex algorithms (Levenshtein distance, scoring)
- Business logic rationale
- Workarounds for platform-specific bugs
- API quirks or non-obvious behavior
- Performance considerations

**JSDoc/TSDoc:**
- Minimal usage
- KDoc for public APIs: `/** Description */`
- TODO comments with issue references when applicable
- // For brief explanatory comments

## Function Design

**Size:**
- Small, focused functions preferred
- Complex logic broken into private helpers
- Example: `isOffline()` as extension function
- ViewModel functions should be concise delegates to services

**Parameters:**
- Named parameters for clarity in complex calls
- Nullable with defaults where appropriate
- Suspend functions clearly marked
- Context passed as first parameter when needed
- Lambda parameters for callbacks

**Return Values:**
- Nullability explicit in return types
- `List<T>` for collections (not arrays)
- Flow/StateFlow for reactive streams
- Boolean for success/failure where appropriate
- Unit for fire-and-forget operations

## Module Design

**Exports:**
- Top-level `object` declarations for singletons: `SpotifyApi`, `RecommenderApi`
- Factory methods in companion objects: `MusicDatabase.getDatabase()`
- ViewModels accessed via `viewModel()` delegate
- Utilities as top-level functions or object declarations

**Barrel Files:**
- Not used
- Direct imports per file
- Package-level functions where appropriate

## Concurrency

**Coroutines:**
- ViewModel scope: `viewModelScope` for UI-related work
- IO Dispatcher: `Dispatchers.IO` for disk/network operations
- Main Dispatcher: `Dispatchers.Main` for UI updates (often implicit)
- Proper exception handling in coroutines
- Avoid blocking calls in main thread

**State Management:**
- StateFlow for exposing state to UI
- MutableStateFlow for internal state mutation
- collectAsState() for Compose consumption
- distinctUntilChanged() to prevent unnecessary recompositions