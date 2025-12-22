# Smart Recommendation System

This document describes the implementation of the smart music recommendation system in JUKE.

## Overview

The recommendation system automatically generates a queue of similar songs based on what you're currently listening to. It uses YouTube Music's recommendation algorithm and validates results with Spotify to ensure high-quality matches.

## Components

### 1. RecommenderApi.kt
Location: `app/src/main/java/com/example/juke/network/RecommenderApi.kt`

**Key Features:**
- `getBestVideoMatch(songName)` - Finds the best YouTube video for a song query
- `fetchFullRadioQueue(videoId)` - Gets up to 50 recommended songs from YouTube Music
- `validateAndFilterWithSpotify(recommendations, maxResults)` - Validates recommendations against Spotify

**How It Works:**
1. Takes current song's YouTube video ID
2. Calls YouTube Music API to get radio playlist ID
3. Fetches full queue (indexes 1-49, skipping the original song)
4. Validates each recommendation with Spotify search
5. Filters out spam/variant versions (remixes, covers, karaoke, etc.)
6. Returns top 10 high-confidence matches with Spotify links

### 2. QueueManager.kt
Location: `app/src/main/java/com/example/juke/services/QueueManager.kt`

**Key Features:**
- Smart queue management
- Automatic recommendation fetching when queue gets low (≤2 songs)
- Background downloads with concurrency control (max 2 simultaneous)
- Pre-downloads next 2 songs for seamless playback
- StateFlow-based reactive updates

**Core Methods:**
- `initializeQueue(tracks)` - Start with initial tracks
- `fetchAndQueueRecommendations(track)` - Fetch recommendations based on a track
- `moveToNext()` - Move to next track, auto-fetch if needed
- `addToQueue(track)` - Add track manually
- `ensureNext2Downloaded()` - Pre-download upcoming tracks

## Usage Example

### In a ViewModel or Activity:

```kotlin
import com.example.juke.services.QueueManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    
    private val queueManager = QueueManager(application)
    
    // Observe queue changes
    init {
        lifecycleScope.launch {
            queueManager.currentQueue.collect { queue ->
                // Update UI with new queue
                Log.d("PlayerViewModel", "Queue updated: ${queue.size} tracks")
            }
        }
        
        lifecycleScope.launch {
            queueManager.downloadingTracks.collect { downloading ->
                // Show downloading indicators
                Log.d("PlayerViewModel", "Currently downloading: ${downloading.size} tracks")
            }
        }
    }
    
    // Start playing with recommendations
    fun playTrackWithRecommendations(track: Track) {
        // Initialize queue with the track
        queueManager.initializeQueue(listOf(track))
        
        // Fetch recommendations (automatic)
        // This will happen automatically because queue size is 1 (≤2)
    }
    
    // Manually fetch recommendations for current track
    fun fetchMoreRecommendations(currentTrack: Track) {
        queueManager.manuallyFetchRecommendations(currentTrack)
    }
    
    // Move to next song
    fun skipToNext() {
        val nextTrack = queueManager.moveToNext()
        nextTrack?.let { track ->
            // Play the track
            playbackManager.playTrack(track)
        }
    }
    
    // Add song manually
    fun addToQueue(track: Track) {
        queueManager.addToQueue(track)
    }
    
    // Get current queue
    fun getCurrentQueue(): List<Track> {
        return queueManager.getCurrentQueueList()
    }
    
    override fun onCleared() {
        super.onCleared()
        queueManager.cleanup()
    }
}
```

### Integration with PlaybackService:

```kotlin
class PlaybackService : MediaSessionService() {
    
    private lateinit var queueManager: QueueManager
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize queue manager
        queueManager = QueueManager(applicationContext)
        
        // Listen to player events
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    // Song ended, move to next
                    val nextTrack = queueManager.moveToNext()
                    nextTrack?.let { track ->
                        // Queue manager already handled recommendations
                        // Just play the next track
                        playTrack(track)
                    }
                }
            }
        })
    }
    
    fun playWithRecommendations(track: Track) {
        queueManager.initializeQueue(listOf(track))
        playTrack(track)
    }
    
    override fun onDestroy() {
        queueManager.cleanup()
        super.onDestroy()
    }
}
```

## How The System Works (Step by Step)

### 1. User Starts Playing a Song
```
User plays "Bohemian Rhapsody" by Queen
↓
QueueManager initializes with this track
↓
Queue size = 1 (≤2), triggers recommendation fetch
```

### 2. Fetching Recommendations
```
QueueManager.fetchAndQueueRecommendations()
↓
Gets YouTube video ID for "Bohemian Rhapsody Queen"
↓
Calls YouTube Music API with video ID
↓
Receives ~50 recommended songs (similar to Bohemian Rhapsody)
```

### 3. Validation with Spotify
```
For each of the 50 recommendations:
  ↓
  1. Check if it's spam (remix, cover, karaoke, etc.) → Skip if spam
  ↓
  2. Search Spotify for the song
  ↓
  3. Calculate match confidence (title + artist similarity)
  ↓
  4. If confidence > 70% → Add to validated list
  ↓
  5. Check for official keywords → Prioritize official releases
  ↓
Select top 10 validated recommendations
```

### 4. Background Downloads
```
Top 10 validated songs added to download queue
↓
QueueManager starts downloading (max 2 concurrent)
↓
As each download completes:
  - Save to database
  - Add to playback queue
  - Start next download
↓
Next 2 songs always ready for playback
```

### 5. Continuous Loop
```
When queue drops to ≤2 songs:
↓
Fetch recommendations based on currently playing song
↓
Repeat validation → download → queue process
↓
Infinite music stream!
```

## Configuration

### Spam Keywords (Filtered Out)
The system filters out these types of songs:
- Remixes, covers, karaoke versions
- Slowed/reverb/8D audio variants
- Live performances, acoustic versions
- Tutorials, reactions, mashups

### Official Keywords (Prioritized)
The system prioritizes songs with these markers:
- "official", "official video", "official music video"
- "VEVO", "official audio"
- "from the album", "remastered"
- "anniversary edition", "deluxe edition"

### Download Settings
- **Max concurrent downloads**: 2
- **Pre-download buffer**: Next 2 songs
- **Retry logic**: Up to 5 retries with exponential backoff
- **Validation threshold**: 70% confidence minimum

## API Flow Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                     User Plays Song                          │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│              QueueManager.initializeQueue()                  │
│              (Queue size ≤ 2? → Fetch recommendations)       │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│         RecommenderApi.getBestVideoMatch()                   │
│         Search YouTube: "Song Title Artist"                  │
│         → Returns YouTube video ID                           │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│         RecommenderApi.fetchFullRadioQueue()                 │
│                                                              │
│  Step 1: POST to YouTube Music API                          │
│          → Get radio playlist ID                             │
│                                                              │
│  Step 2: POST with playlist ID                              │
│          → Get full queue (50 songs)                         │
│          → Parse titles & artists                            │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│    RecommenderApi.validateAndFilterWithSpotify()            │
│                                                              │
│    For each song:                                            │
│    1. Filter spam keywords                                   │
│    2. Search Spotify API                                     │
│    3. Calculate similarity score                             │
│    4. If score > 70% → Validated!                            │
│                                                              │
│    → Returns top 10 validated songs with Spotify links       │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│         QueueManager.processNextDownload()                   │
│                                                              │
│    For each validated song:                                  │
│    1. Check if already downloaded → Add to queue             │
│    2. Search Spotify for full track details                  │
│    3. MusicService.smartDownloadAndIndex()                   │
│       - Download MP3 from Spotdown                           │
│       - Fetch lyrics from LRCLib                             │
│       - Download thumbnail                                   │
│       - Save to database                                     │
│    4. Add to playback queue                                  │
│                                                              │
│    → Max 2 concurrent downloads                              │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│              Queue Ready for Playback!                       │
│              - Next 2 songs pre-downloaded                   │
│              - Auto-refetch when ≤2 songs remain             │
└─────────────────────────────────────────────────────────────┘
```

## Error Handling

The system includes robust error handling:

1. **Network failures**: Automatic retry with exponential backoff
2. **Missing YouTube video ID**: Falls back to getBestVideoMatch()
3. **No Spotify results**: Skips to next recommendation
4. **Download failures**: Retries up to 5 times, then moves to next song
5. **Invalid files**: Validates MP3 format before saving

## Performance Considerations

- **Lazy loading**: Only downloads what's needed
- **Concurrent limits**: Max 2 simultaneous downloads to avoid overwhelming network
- **Database caching**: Checks for existing tracks before downloading
- **StateFlow**: Reactive updates prevent UI blocking
- **Background processing**: All heavy work happens in IO dispatcher

## Testing

To test the recommendation system:

```kotlin
// Test with a single song
val track = Track(
    uuid = "test-123",
    title = "Bohemian Rhapsody",
    artist = "Queen",
    durationSec = 354,
    ytVideoId = "fJ9rUzIMcZQ"
)

queueManager.initializeQueue(listOf(track))

// Watch the logs to see:
// - YouTube Music API calls
// - Recommendation validation
// - Download progress
// - Queue updates
```

## Dependencies Required

Make sure these are in your `app/build.gradle.kts`:

```kotlin
dependencies {
    // Ktor HTTP client
    implementation("io.ktor:ktor-client-android:2.3.7")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.7")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.7")
    implementation("io.ktor:ktor-client-logging:2.3.7")
    
    // Gson for JSON parsing (YouTube Music API)
    implementation("com.google.code.gson:gson:2.10.1")
    
    // Kotlin serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
```

## Future Enhancements

Potential improvements:
- [ ] User preference learning (like/dislike feedback)
- [ ] Genre-based filtering
- [ ] Mood-based recommendations
- [ ] Time-of-day optimization
- [ ] Collaborative filtering with other users
- [ ] Cache popular recommendations
- [ ] Configurable queue size thresholds
- [ ] Download quality settings
