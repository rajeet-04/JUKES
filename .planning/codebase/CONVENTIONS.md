# Coding Conventions

**Analysis Date:** 2026-04-16

## Naming Patterns

**Files:**
- Kotlin files: PascalCase - `MusicService.kt`, `TrackCard.kt`
- Screen composables: PascalCase - `HomeScreen.kt`, `PlayerScreen.kt`
- Component composables: PascalCase - `MiniPlayer.kt`, `TrackCard.kt`

**Functions:**
- camelCase: `playTrack()`, `addToQueue()`, `getTrackByUuid()`
- Private helper: `_internalMethod()` or `processSomething()`
- Suspend functions: Same naming, clearly marked as suspend

**Variables:**
- camelCase: `musicViewModel`, `currentTrack`, `isPlaying`
- Mutable vs Immutable: `MutableStateFlow` for private, `StateFlow` for public
- Constants: SCREAMING_SNAKE_CASE in companion objects or top-level

**Types:**
- Classes: PascalCase - `MusicViewModel`, `TrackEntity`
- Data classes: PascalCase - `MusicUiState`, `DownloadItem`
- Enums: PascalCase - `DownloadStatus.QUEUED`
- Type aliases: PascalCase

## Code Style

**Formatting:**
- Tool: Gradle Kotlin DSL (built-in)
- 4-space indentation
- No line length limit (Compose often exceeds 100 chars)

**Linting:**
- Not explicitly configured (no detekt or ktlint)
- Relies on Kotlin compiler and IDE

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

**Logging:**
```kotlin
private const val TAG = "ClassName"
Log.d(TAG, "MethodName: action description")
Log.e(TAG, "Error occurred", e)
```

## Comments

**When to Comment:**
- Complex algorithms (Levenshtein distance, scoring)
- Business logic rationale
- Workarounds for bugs
- API quirks

**JSDoc/TSDoc:**
- Minimal usage
- KDoc for public APIs: `/** Description */`

## Function Design

**Size:**
- Small, focused functions preferred
- Complex logic broken into private helpers
- Example: `isOffline()` as extension function

**Parameters:**
- Named parameters for clarity in complex calls
- Nullable with defaults where appropriate
- Suspend functions clearly marked

**Return Values:**
- Nullability explicit in return types
- `List<T>` for collections (not arrays)
- Flow/StateFlow for reactive streams

## Module Design

**Exports:**
- Top-level `object` declarations for singletons: `SpotifyApi`, `RecommenderApi`
- Factory methods in companion objects: `MusicDatabase.getDatabase()`

**Barrel Files:**
- Not used
- Direct imports per file

---

*Convention analysis: 2026-04-16*
