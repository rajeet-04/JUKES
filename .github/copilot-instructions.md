# JUKE Music Player - AI Agent Instructions

**Project:** Android Music Streaming App | **Language:** Kotlin | **UI Framework:** Jetpack Compose | **Min SDK:** 26

## Overview

JUKE is a modern Android music player featuring Spotify integration, smart recommendations, offline playback, and synced lyrics. The project uses clean architecture with clear separation between UI, ViewModels, Services, Network, and Database layers.

## Build & Development

### Quick Start

```bash
# Prerequisites
- Android Studio Hedgehog (2023.1.1+)
- Android SDK 26+ (minSdk requirement)
- Kotlin 2.0.21
- Gradle 8.13.1

# Setup Spotify Credentials
echo "SPOTIFY_CLIENT_ID=your_id" > local.properties
echo "SPOTIFY_CLIENT_SECRET=your_secret" >> local.properties

# Build & Install
./gradlew assembleDebug       # Build debug APK
./gradlew installDebug        # Install to device/emulator
./gradlew test                # Run unit tests
./gradlew connectedAndroidTest # Run instrumentation tests
```

### Build Variants & Configuration

- **Debug Build**: Development with logging, debuggable
- **Release Build**: ProGuard-optimized, minified, shrunk resources
- **Compile SDK**: 36 | **Target SDK**: 36 | **Min SDK**: 26
- **JVM Target**: Java 11
- **Kotlin Code Style**: Official (enforced by gradle.properties)

### Key Gradle Setup

- Version catalog: `gradle/libs.versions.toml`
- Plugins: Android Application, Kotlin Android, Kotlin Compose, KSP, Kotlin Serialization
- KSP processors: Room compiler for database code generation
- Build features: Compose + BuildConfig enabled
- Configuration cache: Enabled for faster builds

## Architecture

### Layer Structure

```
UI Layer (Compose)
  └── Screens, Components, Theme
      ↓
ViewModel Layer (StateFlow)
  └── MusicViewModel, SearchViewModel, LibraryViewModel, etc.
      ↓
Service Layer
  └── QueueManager, PlaybackManager, MusicService
      ↓
Data Layer
  ├── Room Database (local persistence)
  ├── Network APIs (Spotify, YouTube Music, Spotdown, LRCLib)
  └── Models & Repositories
```

### Directory Structure

```
app/src/main/java/com/example/juke/
├── analytics/         # Analytics tracking
├── database/          # Room entities, DAOs, MusicDatabase
├── models/            # Data classes (Track, Artist, Album, etc.)
├── network/           # API clients (SpotifyApi, RecommenderApi, etc.)
├── services/          # Business logic (QueueManager, PlaybackManager, MusicService)
├── ui/
│   ├── screens/       # Full-screen Compose functions
│   ├── components/    # Reusable UI components
│   └── theme/         # Material3 styling, color palette
├── utils/             # Helper functions
├── viewmodels/        # MVVM state management
├── JukeApplication.kt # Application class
└── MainActivity.kt    # Entry point
```

### Key Architectural Patterns

1. **MVVM with StateFlow**: ViewModels expose StateFlow for reactive UI updates
2. **Repository Pattern**: Services and databases accessed through well-defined interfaces
3. **Dependency Injection**: Manual DI or service locator pattern (no Hilt currently)
4. **Composable Functions**: All UI defined declaratively with Compose
5. **Coroutine-based Async**: Kotlin Coroutines + Flows for threading
6. **Event-driven**: UI reacts to ViewModel StateFlow emissions

## Technology Stack

### UI & Composition
- **Jetpack Compose** (2024.12.01): Declarative UI framework
- **Material Design 3**: Google's latest design system
- **Coil 2.7.0**: Image loading and caching
- **Navigation Compose**: Type-safe screen navigation

### State Management & Async
- **Kotlin Coroutines 1.8.1**: Async programming model
- **StateFlow**: Reactive state management for ViewModels
- **Android Lifecycle**: ViewModel, LifecycleScope integration

### Data & Persistence
- **Room 2.6.1**: SQLite ORM with Flow support
- **Kotlin Serialization 1.6.3**: JSON serialization (used with Ktor)
- **Gson 2.10.1**: JSON serialization for YouTube Music API responses

### Networking
- **Ktor Client 3.0.0**: HTTP client with content negotiation
  - `ktor-client-android`: Android engine
  - `ktor-serialization-kotlinx-json`: JSON serialization plugin
  - `ktor-client-logging`: Request/response logging
  - `ktor-client-content-negotiation`: Automatic content type handling

### Media & Playback
- **Media3 1.5.0** (ExoPlayer successor):
  - `media3-exoplayer`: Audio playback engine
  - `media3-session`: System media control integration
  - `media3-ui`: Built-in UI components

### Other
- **AndroidX Libraries**: Core, Lifecycle, Activity, Palette
- **PostHog Analytics** (3.32+): User analytics tracking

## Code Conventions

### Kotlin Style
- Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Enforce via `kotlin.code.style=official` in gradle.properties
- Use default parameter names, prefer named arguments for clarity
- Top-level functions for utility code

### Compose Conventions
- **@Composable functions**: PascalCase (e.g., `SearchScreen()`, `TrackCard()`)
- **State management**: Use `remember`, `mutableStateOf`, `rememberSaveable` for local state
- **Modifiers**: Last parameter, chained naturally
- **Preview functions**: Use `@Preview` for visual testing
- **Code organization**: Group related composables, use lambda receivers for content blocks

### Naming Conventions
- **Packages**: `com.example.juke.{feature}` (lowercase)
- **Classes**: PascalCase (ViewModels end with `ViewModel`)
- **Functions/Variables**: camelCase
- **Constants**: UPPER_SNAKE_CASE in companion objects
- **Database**: Entity names singular (Track, Playlist), table names match class names lowercase with plural plurals via @Entity annotation

### Database Conventions
- **Entities**: Located in `database/` with Entity suffix (e.g., TrackEntity)
- **DAOs**: Separate files per entity collection, named `{Entity}Dao.kt`
- **Queries**: Use Flow<T> return types for reactive updates
- **Relationships**: Prefer @Embedded for composition, @Relation for references
- **IDs**: Use UUID or auto-generated primary keys

### ViewModel Conventions
- **Naming**: `{Feature}ViewModel` (e.g., `MusicViewModel`, `SearchViewModel`)
- **State**: Expose data via `StateFlow<UiState>` or individual flows
- **Methods**: Public functions for UI events, private for internal business logic
- **Lifecycle**: Extend `AndroidViewModel` when needing Application context
- **Coroutines**: Launch within `viewModelScope` for automatic cancellation

### Service Conventions
- **Naming**: `{Feature}Service` or `{Feature}Manager` for orchestration
- **Scope**: Application-level lifecycle, injected into ViewModels
- **Async**: Use coroutines with proper exception handling
- **Dependencies**: Receive through constructor injection

### Networking Conventions
- **API Clients**: `{Service}Api.kt` (e.g., `SpotifyApi.kt`, `RecommenderApi.kt`)
- **Error Handling**: Wrap API responses in try-catch, emit errors to UI via StateFlow
- **Rate Limiting**: Respect API rate limits, implement backoff strategies
- **Client Setup**: Single Ktor client instance, configured with interceptors
- **Models**: Response models separate from domain models where significant differences exist

## Key Components Reference

### Core Services

#### QueueManager
- **Location**: `services/QueueManager.kt`
- **Purpose**: Smart queue management with automatic recommendations
- **Key Methods**: `initializeQueue()`, `fetchAndQueueRecommendations()`, `moveToNext()`, `ensureNext2Downloaded()`
- **Features**: Intelligent recommendation fetching (≤2 songs threshold), background downloads (max 2 concurrent), pre-downloads next 2 tracks
- **Used by**: Player screens, music playback
- **Notes**: Validates recommendations using Spotify API, filters spam keywords

#### PlaybackManager
- **Location**: `services/PlaybackManager.kt`
- **Purpose**: Media3 ExoPlayer integration and playback control
- **Key Methods**: `play()`, `pause()`, `seekTo()`, `skipNext()`, `setRepeatMode()`, `setShuffle()`
- **Features**: Full-screen player, mini player, media session integration
- **Used by**: All player screens via ViewModel

#### MusicService
- **Location**: `services/MusicService.kt`
- **Purpose**: Download management and track indexing
- **Key Methods**: `downloadTrack()`, `downloadTracksSequentially()`, `indexTrack()`
- **Features**: Sequential downloads, metadata fetching, album art caching, lyrics fetching
- **Integration**: With Spotdown API (MP3), LRCLib (lyrics), Coil (images)

### ViewModels

#### MusicViewModel
- **Purpose**: Central music state (playback, queue, library, downloads)
- **State**: Current track, queue, library tracks, playlists, playback mode
- **Methods**: `playTrack()`, `skipNext()`, `previous()`, `updateQueue()`, `toggleFavorite()`

#### SearchViewModel
- **Purpose**: Search and playlist import functionality
- **State**: Search results (tracks, artists, albums, playlists), import progress
- **Methods**: `search()`, `importPlaylist()`, `getPlaylistTracks()`

#### LibraryViewModel
- **Purpose**: Downloaded tracks and playlist browsing
- **State**: All tracks, filtered tracks, playlists, filters applied
- **Methods**: `applyFilters()`, `sortBy()`, `deleteTrack()`, `getPlaylistTracks()`

### API Clients

#### SpotifyApi
- **Authentication**: Client Credentials Flow (automatic token refresh)
- **Endpoints**: Search, track details, artist info, album data, playlist metadata
- **Rate Limit**: 180 requests/minute (normal tier)
- **Usage**: Validation, metadata enrichment, recommendations filtering

#### RecommenderApi
- **Purpose**: YouTube Music recommendation integration
- **Key Methods**: `fetchFullRadioQueue()`, `validateAndFilterWithSpotify()`
- **Process**: Fetch 50 recommendations → Validate with Spotify → Filter spam → Return top 10
- **Spam Filtering**: Removes remixes, covers, karaoke, slowed versions, etc.
- **Usage**: QueueManager queries this for recommendations

#### Other APIs
- **Spotdown**: MP3 downloads with caching
- **LRCLib**: Synced and plain lyrics fetching
- **All APIs**: Wrapped in Ktor HTTP client with error handling

### Database

#### Key Entities
- **TrackEntity**: Downloaded tracks with metadata
- **PlaylistEntity**: User playlists with relationships
- **FavoriteEntity**: Marked favorite tracks
- **HistoryEntity**: Recently played tracks

#### Key DAOs
- **TrackDao**: CRUD operations, query by title/artist, get by playlist
- **PlaylistDao**: Manage playlists and relationships
- **FavoriteDao**: Toggle favorites, query favorite status
- **HistoryDao**: Add/query history, purge old entries

#### Features
- **Reactive Queries**: All DAOs return Flow<T> for reactive updates
- **Relationships**: One-to-many (Playlists-to-Tracks), many-to-many (user defined)
- **Room Configuration**: Version-controlled with migration scripts

## Development Workflow

### Creating New Screens

1. **Create Screen File**: `ui/screens/{Feature}Screen.kt`
   ```kotlin
   @Composable
   fun MyScreen(
       viewModel: MyViewModel = hiltViewModel(),
       onNavigate: (route: String) -> Unit
   ) {
       val uiState by viewModel.uiState.collectAsState()
       
       Column(modifier = Modifier.fillMaxSize()) {
           // UI here
       }
   }
   ```

2. **Create ViewModel**: `viewmodels/{Feature}ViewModel.kt`
   ```kotlin
   class MyViewModel(app: Application) : AndroidViewModel(app) {
       private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
       val uiState = _uiState.asStateFlow()
       
       fun onEvent(event: UiEvent) {
           viewModelScope.launch {
               // Handle event
           }
       }
   }
   ```

3. **Add Navigation**: Update `MainActivity.kt` NavHost
   ```kotlin
   composable("myRoute") { MyScreen(onNavigate = navController::navigate) }
   ```

4. **Use Components**: Import from `ui/components/`

### Adding API Integrations

1. **Create API Client**: `network/{Service}Api.kt`
   ```kotlin
   class MyApi(private val client: HttpClient) {
       suspend fun fetchData(query: String): Result<MyData> = runCatching {
           client.get("https://api.example.com/...") {
               // Configure request
           }.body()
       }
   }
   ```

2. **Integrate in Service/ViewModel**: Call API in coroutine
   ```kotlin
   viewModelScope.launch {
       try {
           val result = myApi.fetchData(query)
           _uiState.value = UiState.Success(result)
       } catch (e: Exception) {
           _uiState.value = UiState.Error(e.message)
       }
   }
   ```

### Database Operations

1. **Define Entity**: `database/{Entity}Entity.kt`
2. **Create DAO**: `database/{Entity}Dao.kt` with Room annotations
3. **Add to Database**: Register in `MusicDatabase.kt`
4. **Use in Service**: Inject DAO, query/insert via coroutine

### Testing Strategy

- **Unit Tests**: `app/src/test/` for ViewModels, Services, API clients
- **Instrumentation Tests**: `app/src/androidTest/` for UI, database, integration
- **Run Tests**:
  ```bash
  ./gradlew test              # Unit tests
  ./gradlew connectedAndroidTest  # Device/emulator tests
  ```

## Common Pitfalls & Solutions

### 1. Spotify Credentials Not Found
**Problem**: `BuildConfig.SPOTIFY_CLIENT_ID` or `SPOTIFY_CLIENT_SECRET` empty
**Solution**: 
- Add to `local.properties`: `SPOTIFY_CLIENT_ID=xyz` and `SPOTIFY_CLIENT_SECRET=abc`
- Run `./gradlew clean` then rebuild
- Verify credentials in `app/build.gradle.kts` (lines load properties)

### 2. No Search Results
**Symptom**: Empty search despite valid queries
**Causes**:
- Spotify credentials invalid or expired
- Network connectivity issue
- Rate limit hit (wait 60 seconds)
**Debug**: Check logcat filter "SpotifyApi" for error messages

### 3. Downloads Stalling
**Symptom**: Files stuck "downloading" forever
**Causes**:
- Spotdown API down or rate limited
- Insufficient storage space
- Network timeout
**Solution**: 
- Check internet connection
- Verify storage > 500MB free
- Check Spotdown API status
- Implement retry in `MusicService`

### 4. Queue Not Auto-Refilling
**Symptom**: Queue empties instead of auto-fetching recommendations
**Causes**:
- `queueManager.cleanup()` called too early
- Queue size never drops to ≤2 (player skips before threshold)
- YouTube video ID missing for current track
**Debug**: 
- Check logcat tag "QueueManager"
- Verify `moveToNext()` called when skipping
- Ensure Track has valid `ytVideoId`

### 5. UI Not Updating
**Symptom**: ViewModel changes not reflected in UI
**Causes**:
- **Not using StateFlow**: Use `MutableStateFlow` + `asStateFlow()`
- **Not collecting on main**: Collect with `.collectAsState()` in Compose
- **Late ViewModel initialization**: Initialize in VM constructor, not onCreate
**Solution**: 
  ```kotlin
  val state by viewModel.uiState.collectAsState()  // Correct
  ```

### 6. Memory Leaks
**Likely sources**:
- Listeners not unregistered in onCleared()
- Long-lived coroutine jobs (use viewModelScope)
- Bitmap memory in Coil (use `.memoryCache()` with limits)
**Prevention**: Always use `viewModelScope`, clean up in VM.onCleared()

## Git & Release Workflow

### Branches
- **main**: Production-ready releases
- **beta**: Pre-release feature development (current development branch)
- **feature/**: Individual feature branches off beta

### Release Process
1. Merge features to `beta`, test thoroughly
2. Create release branch: `release/v1.0.x`
3. Update version in `app/build.gradle.kts` and `versionName`
4. Update `RELEASE_NOTES.md` and changelog
5. Merge to `main`, tag with version
6. Generate APK: `./gradlew assembleRelease`
7. Create GitHub release with APK and notes

### Current Version
- **Latest**: v1.0.8-beta (in development on `beta` branch)
- **Version Code**: 8 (incremented per release)
- **Version Name**: "1.0.x-beta" pattern

## Documentation References

- **README.md**: Feature overview, installation, architecture diagram
- **RECOMMENDATION_SYSTEM.md**: Detailed recommendation algorithm docs
- **QUICK_START_RECOMMENDATIONS.md**: Integration guide for queue manager
- **SPOTIFY_SETUP.md**: Spotify API credentials setup
- **RELEASE_NOTES.md**: Latest changes per version
- **CHANGELOG.md**: Full changelog history

## Questions for Clarification

When working on enhancements, ask these clarifying questions:

- **For UI changes**: Should this follow Material Design 3 guidelines? What accessibility requirements?
- **For API changes**: What rate limits apply? Retry strategy needed? Error handling?
- **For database changes**: Is this a migration of existing data? Backward compatibility needed?
- **For performance**: Is this user-facing (prioritize responsiveness)? Background operation (optimize throughput)?
- **For integration**: Does Spotify/YouTube API support this? Alternative APIs if needed?

## Tips for Efficient Development

1. **Use preview functions**: Add `@Preview` composables for instant UI feedback
2. **Log API responses**: Add `HttpLoggingInterceptor` for debugging network issues
3. **Watch database queries**: Enable Room query logging in debug builds
4. **Test on real device**: Emulator networking can be problematic; physical device more reliable
5. **Monitor coroutine scope**: Use debugger or logs to verify viewModelScope cancellation
6. **Cache API responses**: Spotify responses are stable; Room caching prevents redundant calls
7. **Use StateFlow debugger**: Android Studio's debugger can inspect StateFlow values at breakpoints

---

**Last Updated**: February 2026 | **Status**: v1.0.8-beta in development
