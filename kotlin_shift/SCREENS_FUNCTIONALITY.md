# JUKE Screens & Functionality Documentation

## Table of Contents
1. [Screen Overview](#screen-overview)
2. [Home Screen](#home-screen)
3. [Search Screen](#search-screen)
4. [Library Screen](#library-screen)
5. [Player Modal](#player-modal)
6. [Navigation Flow](#navigation-flow)
7. [State Management](#state-management)

---

## Screen Overview

JUKE has 4 main screens:

| Screen | Purpose | Key Features |
|--------|---------|--------------|
| **Home** | Discover music | Recently played, Most played, Personalized recommendations |
| **Search** | Find new music | Spotify search, Real-time results, Download & play |
| **Library** | Manage collection | Downloaded tracks, Favorites filter, Delete tracks |
| **Player Modal** | Control playback | Now playing, Queue, Progress bar, Lyrics |

### Tab Navigation Structure

```
┌─────────────────────────────────────┐
│         Tab Navigator               │
├─────────────────────────────────────┤
│  🏠 Home  │  🔍 Search  │  📚 Library│
└─────────────────────────────────────┘
              ↓
┌─────────────────────────────────────┐
│         Mini Player                 │
│  [Song] [Artist]  [Play/Pause] [▶]  │
└─────────────────────────────────────┘
              ↓ (Tap to expand)
┌─────────────────────────────────────┐
│        Full Player Modal            │
│     (Full-screen playback UI)       │
└─────────────────────────────────────┘
```

---

## Home Screen

**Location:** `app/(tabs)/index.tsx`

### Purpose
Home screen shows personalized music collections and provides quick access to recent and popular tracks.

### Features

#### 1. Recently Played Section
- **Description:** Shows last 10 tracks the user played
- **Data Source:** `getRecentlyPlayed(10)` from database
- **Sorting:** By `last_played_at` DESC
- **Interaction:** Tap any track to play and load that track's queue

**Implementation:**
```kotlin
// Kotlin equivalent
suspend fun loadRecentlyPlayed() {
    val tracks = trackDao.getRecentlyPlayed(10)
    // Update UI with tracks
}
```

#### 2. Most Played (Daily Mix) Section
- **Description:** Top 10 most played songs
- **Data Source:** `getMostPlayed(10)` from database
- **Sorting:** By `play_count` DESC
- **Use Case:** User's favorite tracks

**Implementation:**
```kotlin
suspend fun loadMostPlayed() {
    val tracks = trackDao.getMostPlayed(10)
    // Update UI with tracks
}
```

#### 3. Empty State
- **Trigger:** No recently played AND no most played tracks
- **Message:** "Welcome to JUKE! Start by searching and downloading your favorite music"
- **Icon:** Large music note icon
- **Call to Action:** Guides user to search screen

### UI Components

#### Track Card
```
┌─────────────────┐
│                 │
│   [Album Art]   │  140x140px
│                 │  Rounded corners (8px)
├─────────────────┤
│ Song Title      │  Bold, 14px
│ Artist Name     │  Regular, 12px, 70% opacity
└─────────────────┘
```

**Specs:**
- Width: 140px
- Gap: 12px between cards
- Horizontal scroll
- Thumbnail fallback: Music note icon on gray background

### Data Flow

```
User Opens Home Screen
    ↓
useFocusEffect() triggered
    ↓
loadData() called
    ├→ getRecentlyPlayed(10)
    └→ getMostPlayed(10)
    ↓
Update State
    ├→ setRecentlyPlayed()
    └→ setMostPlayed()
    ↓
Render Track Cards
```

### User Interactions

#### Play Track
```typescript
const handlePlayTrack = async (track: Track, tracks: Track[]) => {
  const index = tracks.findIndex(t => t.uuid === track.uuid);
  await setQueue(tracks, index); // Sets queue and starts playback
};
```

**Kotlin Implementation:**
```kotlin
fun playTrack(track: Track, tracks: List<Track>) {
    viewModelScope.launch {
        val index = tracks.indexOfFirst { it.uuid == track.uuid }
        playbackManager.setQueue(tracks, index)
    }
}
```

---

## Search Screen

**Location:** `app/(tabs)/search.tsx`

### Purpose
Search for songs on Spotify, download MP3 files, and start playback.

### Features

#### 1. Search Bar
- **Input:** Song name, artist, or both
- **Placeholder:** "Search for songs..."
- **Submission:** Enter key or search button
- **Loading State:** Spinner in search button

#### 2. Search Results List
- **Data Source:** Spotdown/Spotify API
- **Display:** FlatList with song cards
- **Each Item Shows:**
  - Album artwork (60x60px)
  - Song title (bold)
  - Artist name (70% opacity)
  - Duration (50% opacity, 12px)
  - Download button (arrow.down.circle icon)

#### 3. Download & Play Flow

```
User Searches "Bohemian Rhapsody"
    ↓
API Call: searchSongs(query)
    ↓
Display Results (SpotdownSong[])
    ↓
User Taps Download Button
    ↓
Check Database: findTrackByTitleArtist()
    ├→ If exists: Play immediately
    └→ If not exists: Download flow
        ↓
    smartDownloadAndIndex(song)
        ├→ Check cache status
        ├→ Download MP3
        ├→ Fetch lyrics
        ├→ Get YouTube video ID
        ├→ Save to database
        └→ Return Track
    ↓
playTrack(track)
```

### API Integration

**Search API Call:**
```kotlin
suspend fun searchSongs(query: String): List<SpotdownSong> {
    val response = httpClient.get("$SPOTDOWN_BASE_URL/song-details") {
        parameter("url", query)
    }
    val searchResponse: SpotdownSearchResponse = response.body()
    return searchResponse.songs
}
```

### Download States

| State | UI Indicator | User Can |
|-------|-------------|----------|
| **Idle** | Download icon | Tap to download |
| **Downloading** | Activity spinner | Wait |
| **Completed** | Auto-plays | Listen |
| **Already Downloaded** | Instant playback | Listen |

### Error Handling

**Scenarios:**
1. **Network Error:** Show alert "Failed to search/download"
2. **No Results:** Display "No results found"
3. **Download Failed:** Show error, allow retry
4. **Invalid MP3:** Log error, show user-friendly message

---

## Library Screen

**Location:** `app/(tabs)/library.tsx`

### Purpose
View and manage all downloaded tracks, organize favorites, delete unwanted songs.

### Features

#### 1. Filter Tabs
Two modes:
- **All Tracks:** Shows all downloaded tracks (default)
- **Favourites:** Shows only favorite tracks

**UI:**
```
┌──────────────┬──────────────┐
│  All Tracks  │  Favourites  │  ← Active tab has colored border
└──────────────┴──────────────┘
```

#### 2. Track List
- **Layout:** Vertical scrollable list
- **Each Item:**
  - Album artwork (60x60px, rounded)
  - Track info (title, artist, duration, play count)
  - Favorite button (heart icon)
  - Delete button (trash icon)

**Track Item Layout:**
```
┌──────────────────────────────────────────────┐
│  [Album]  Song Title              ❤️  🗑️   │
│           Artist Name                        │
│           3:45 • Played 12 times             │
└──────────────────────────────────────────────┘
```

#### 3. Play Track
- **Tap Anywhere:** Plays track and sets queue to current filter
- **Queue:** All visible tracks in list order
- **Start Index:** Tapped track's position

#### 4. Toggle Favorite
- **Icon:** Empty heart (❤️) or filled heart (❤️)
- **Action:** Updates database, reloads list if on Favorites tab
- **Database:** `updateTrackFavourite(uuid, isFavourite)`

**Implementation:**
```kotlin
suspend fun toggleFavorite(track: Track) {
    trackDao.updateTrackFavourite(track.uuid, !track.isFavourite)
    loadTracks() // Refresh UI
}
```

#### 5. Delete Track
- **Confirmation:** Alert dialog "Are you sure?"
- **Actions:**
  - Delete MP3 file from storage
  - Delete thumbnail image
  - Remove from database
- **UI Update:** Remove from list

**Implementation:**
```kotlin
suspend fun deleteTrack(track: Track) {
    // Delete files
    track.localUri?.let { File(it).delete() }
    track.thumbnailUri?.let { File(it).delete() }
    
    // Delete from DB
    trackDao.deleteTrack(track.uuid)
    
    // Refresh UI
    loadTracks()
}
```

### Empty States

#### No Downloaded Tracks
```
      🎵
No downloaded tracks yet

Search and download tracks
to build your library
```

#### No Favorites
```
      🎵
No favourite tracks yet

Mark tracks as favourites
to see them here
```

### Data Queries

**All Tracks:**
```sql
SELECT * FROM tracks 
WHERE local_uri IS NOT NULL 
ORDER BY last_played_at DESC
```

**Favorites Only:**
```sql
SELECT * FROM tracks 
WHERE is_favourite = 1 
ORDER BY last_played_at DESC
```

---

## Player Modal

**Location:** `components/player-modal.tsx`

### Purpose
Full-screen playback control with queue management, lyrics, and progress tracking.

### Features

#### 1. Header
- **Close Button:** Dismiss modal, return to current tab
- **Queue Button:** Show/hide upcoming tracks

#### 2. Album Artwork
- **Size:** Large (300x300px or larger)
- **Fallback:** Music note icon
- **Position:** Center of screen

#### 3. Track Info
- **Title:** Bold, large font
- **Artist:** Regular font, slightly smaller

#### 4. Progress Bar
- **Current Position:** 0:00 format
- **Total Duration:** 3:45 format
- **Slider:** Seekable progress bar
- **Update Rate:** 1 second intervals

**Implementation:**
```kotlin
// Update progress every second
val progressFlow = flow {
    while (true) {
        emit(playbackManager.getCurrentPosition())
        delay(1000)
    }
}
```

#### 5. Playback Controls
**Layout:**
```
    [⏮️]     [⏯️]     [⏭️]
  Previous  Play/Pause  Next
```

**Buttons:**
- **Previous:** Skip to previous track (or restart if > 3 seconds)
- **Play/Pause:** Toggle playback
- **Next:** Skip to next track

#### 6. Queue View
- **Toggle:** Show/hide with button
- **Display:** List of upcoming tracks
- **Interaction:** Tap to skip to that track
- **Visual:** Current track highlighted

#### 7. Lyrics Display
- **Plain Lyrics:** Scrollable text
- **Synced Lyrics:** Highlighted current line (if available)
- **LRC Parsing:** Parse timestamps and sync with playback

**LRC Format:**
```
[00:00.00] Is this the real life?
[00:03.50] Is this just fantasy?
[00:06.00] Caught in a landslide
```

**Implementation:**
```kotlin
fun parseLRC(lrc: String): List<LyricLine> {
    return lrc.lines().mapNotNull { line ->
        val regex = """\[(\d{2}):(\d{2})\.(\d{2})\]\s*(.+)""".toRegex()
        regex.matchEntire(line)?.let { match ->
            val (min, sec, ms, text) = match.destructured
            val timeMs = min.toInt() * 60000 + sec.toInt() * 1000 + ms.toInt() * 10
            LyricLine(timeMs, text)
        }
    }
}
```

### State Management

**Player State:**
```kotlin
data class PlayerState(
    val currentTrack: Track?,
    val queue: List<Track>,
    val queueIndex: Int,
    val isPlaying: Boolean,
    val position: Long,      // milliseconds
    val duration: Long,      // milliseconds
    val isModalOpen: Boolean
)
```

---

## Navigation Flow

### App Launch
```
App Start
    ↓
Initialize Database
    ↓
Initialize TrackPlayer
    ↓
Load Home Screen
```

### Search → Download → Play
```
Search Tab
    ↓
Enter Query
    ↓
Search API Call
    ↓
Display Results
    ↓
Tap Download
    ↓
Check if Already Downloaded
    ├→ Yes: Play Immediately
    └→ No: Download Flow
        ↓
    Save to Database
        ↓
    Play Track
        ↓
    Mini Player Appears
        ↓
    Tap Mini Player
        ↓
    Player Modal Opens
```

### Library → Play
```
Library Tab
    ↓
Select Filter (All/Favorites)
    ↓
Display Tracks
    ↓
Tap Track
    ↓
Set Queue
    ↓
Start Playback
    ↓
Mini Player Shows
```

---

## State Management

### Global State (Zustand Store)

**Location:** `store/useMusicStore.ts` (TypeScript)
**Kotlin Equivalent:** ViewModel with StateFlow

```kotlin
class MusicViewModel(
    private val playbackManager: PlaybackManager,
    private val musicService: MusicService
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(MusicUiState())
    val uiState: StateFlow<MusicUiState> = _uiState.asStateFlow()
    
    data class MusicUiState(
        val currentTrack: Track? = null,
        val queue: List<Track> = emptyList(),
        val queueIndex: Int = -1,
        val isPlaying: Boolean = false,
        val position: Long = 0,
        val duration: Long = 0,
        val isLoading: Boolean = false,
        val error: String? = null
    )
    
    fun playTrack(track: Track) {
        viewModelScope.launch {
            playbackManager.playTrack(track)
            _uiState.update { it.copy(currentTrack = track, isPlaying = true) }
        }
    }
    
    fun setQueue(tracks: List<Track>, startIndex: Int = 0) {
        viewModelScope.launch {
            playbackManager.setQueue(tracks, startIndex)
            _uiState.update { 
                it.copy(
                    queue = tracks,
                    queueIndex = startIndex,
                    currentTrack = tracks.getOrNull(startIndex)
                )
            }
        }
    }
}
```

### Local State (Screen-Level)

Each screen manages its own UI state:

**Home Screen:**
```kotlin
data class HomeUiState(
    val recentlyPlayed: List<Track> = emptyList(),
    val mostPlayed: List<Track> = emptyList(),
    val isLoading: Boolean = false
)
```

**Search Screen:**
```kotlin
data class SearchUiState(
    val query: String = "",
    val results: List<SpotdownSong> = emptyList(),
    val isSearching: Boolean = false,
    val downloadingId: String? = null
)
```

**Library Screen:**
```kotlin
data class LibraryUiState(
    val tracks: List<Track> = emptyList(),
    val showFavoritesOnly: Boolean = false,
    val isLoading: Boolean = false
)
```

---

## User Flows

### First-Time User Flow

```
1. Open App
   ↓
2. See Empty Home Screen
   ↓
3. Navigate to Search
   ↓
4. Search for Song
   ↓
5. Download & Play
   ↓
6. Song Added to Library
   ↓
7. Return to Home → See in Recently Played
```

### Daily User Flow

```
1. Open App
   ↓
2. Home Shows Recently Played
   ↓
3. Tap Track to Resume
   ↓
4. Mini Player Appears
   ↓
5. Expand to Full Player
   ↓
6. Listen to Queue
   ↓
7. Auto-Recommendations Download
   ↓
8. Continuous Playback
```

### Power User Flow

```
1. Open Library
   ↓
2. Filter to Favorites
   ↓
3. Select Track
   ↓
4. Queue Loaded with All Favorites
   ↓
5. Shuffle/Repeat (Future Feature)
   ↓
6. Manage Queue in Player Modal
```

---

## Performance Optimizations

### Home Screen
- **Lazy Loading:** Only load visible track cards
- **Image Caching:** Use `expo-image` for automatic caching
- **Database Queries:** Use indexed columns (`last_played_at`, `play_count`)

### Search Screen
- **Debounced Search:** Wait 300ms after typing before API call
- **Result Caching:** Cache recent searches
- **Parallel Download:** Prefetch next search result

### Library Screen
- **Virtual List:** Only render visible items
- **Database Indexing:** Fast queries on `is_favourite`
- **Reactive Updates:** Use Flow for real-time changes

---

## Accessibility

### Screen Readers
- All buttons have proper labels
- Track info read aloud
- Progress bar announces changes

### Keyboard Navigation
- Tab through controls
- Enter to activate
- Arrow keys for seeking

### High Contrast
- Respect system theme
- Sufficient color contrast (4.5:1 ratio)
- Clear focus indicators

---

## Future Enhancements

### Home Screen
- [ ] Personalized playlists
- [ ] Genre-based sections
- [ ] New releases

### Search Screen
- [ ] Search history
- [ ] Trending searches
- [ ] Voice search

### Library Screen
- [ ] Sort options (name, date, play count)
- [ ] Bulk actions (delete multiple)
- [ ] Custom playlists

### Player Modal
- [ ] Equalizer
- [ ] Sleep timer
- [ ] Crossfade
- [ ] Gapless playback

---

## Component Reusability

### Shared Components

#### TrackCard
Used in: Home, Library
```kotlin
@Composable
fun TrackCard(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Reusable track card UI
}
```

#### SearchResult
Used in: Search
```kotlin
@Composable
fun SearchResultItem(
    song: SpotdownSong,
    isDownloading: Boolean,
    onDownload: () -> Unit
) {
    // Search result item UI
}
```

#### MiniPlayer
Used in: All screens (floating bottom)
```kotlin
@Composable
fun MiniPlayer(
    currentTrack: Track?,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onExpand: () -> Unit
) {
    // Mini player UI
}
```

---

## Testing Screens

### Unit Tests
- ViewModel logic
- State transformations
- Data parsing

### Integration Tests
- Navigation flows
- Database operations
- API calls

### UI Tests
- Button interactions
- List scrolling
- Search input
- Player controls

**Example UI Test:**
```kotlin
@Test
fun testSearchAndDownload() {
    // Enter search query
    onView(withId(R.id.searchInput))
        .perform(typeText("Bohemian Rhapsody"))
    
    // Tap search button
    onView(withId(R.id.searchButton)).perform(click())
    
    // Wait for results
    onView(withId(R.id.resultsList))
        .check(matches(isDisplayed()))
    
    // Tap first download button
    onView(withId(R.id.downloadButton)).perform(click())
    
    // Verify playback started
    onView(withId(R.id.miniPlayer))
        .check(matches(isDisplayed()))
}
```

---

## Build & Deploy

### Debug Build
```bash
./gradlew assembleDebug
```

### Release Build
```bash
./gradlew assembleRelease
```

### Running on Device
```bash
adb install app-release.apk
```

---

## Troubleshooting

### Common Issues

#### "No tracks showing on Home"
- Check database has tracks with `last_played_at` set
- Verify `getRecentlyPlayed()` query

#### "Search returns no results"
- Check network connection
- Verify API endpoint is accessible
- Check query format

#### "Download fails"
- Check storage permissions
- Verify Spotify URL format
- Check MP3 validation logic

#### "Playback doesn't start"
- Verify `local_uri` exists and file is valid
- Check ExoPlayer initialization
- Verify audio focus handling

---

This documentation covers all screens, user flows, and functionality in the JUKE app. Use it as a reference when implementing the Kotlin version.
