# Quick Start Guide - Smart Recommendation System

## What Was Implemented

A complete recommendation system that automatically generates an infinite music queue based on what you're listening to.

## Files Created/Modified

### New Files
1. **QueueManager.kt** - `app/src/main/java/com/example/juke/services/QueueManager.kt`
   - Smart queue management
   - Automatic recommendation fetching
   - Background downloads with concurrency control
   - Pre-downloads next 2 songs

2. **PlayerViewModel.kt** - `app/src/main/java/com/example/juke/viewmodels/PlayerViewModel.kt`
   - Example integration showing how to use QueueManager
   - Reactive UI updates with StateFlow
   - Complete usage examples

3. **RECOMMENDATION_SYSTEM.md** - Comprehensive documentation
   - System architecture
   - API flow diagrams
   - Configuration options
   - Testing guide

### Modified Files
1. **RecommenderApi.kt** - `app/src/main/java/com/example/juke/network/RecommenderApi.kt`
   - Added `fetchFullRadioQueue()` - Gets 50 recommendations from YouTube Music
   - Added `validateAndFilterWithSpotify()` - Validates with Spotify
   - Added spam filtering and official keyword detection
   - Improved similarity algorithm

2. **libs.versions.toml** - Added Gson dependency
3. **app/build.gradle.kts** - Added Gson implementation

## How It Works (Simple Explanation)

```
1. User plays a song
   ↓
2. System gets YouTube video ID
   ↓
3. Calls YouTube Music API → Gets 50 similar songs
   ↓
4. Validates each song with Spotify (filters spam/remixes)
   ↓
5. Selects top 10 high-confidence matches
   ↓
6. Downloads songs in background (max 2 at a time)
   ↓
7. Adds to queue for seamless playback
   ↓
8. When queue drops to ≤2 songs → Repeat from step 2
   ↓
9. Infinite music! 🎵
```

## Quick Integration

### Step 1: Initialize in Your Activity/Fragment

```kotlin
class MainActivity : ComponentActivity() {
    private lateinit var queueManager: QueueManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize queue manager
        queueManager = QueueManager(applicationContext)
        
        setContent {
            // Your Compose UI
        }
    }
    
    override fun onDestroy() {
        queueManager.cleanup()
        super.onDestroy()
    }
}
```

### Step 2: Use in ViewModel

```kotlin
class YourViewModel(app: Application) : AndroidViewModel(app) {
    private val queueManager = QueueManager(app)
    
    fun playTrack(track: Track) {
        // This automatically fetches recommendations!
        queueManager.initializeQueue(listOf(track))
    }
    
    fun skipNext() {
        val nextTrack = queueManager.moveToNext()
        // Play next track
    }
}
```

### Step 3: Observe Queue in UI

```kotlin
@Composable
fun PlayerScreen(viewModel: YourViewModel) {
    val queue by viewModel.queueManager.currentQueue.collectAsState()
    val downloading by viewModel.queueManager.downloadingTracks.collectAsState()
    
    Column {
        Text("Queue: ${queue.size} songs")
        
        queue.forEach { track ->
            Row {
                Text(track.title)
                if (downloading.contains(track.title)) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}
```

## Key Features

✅ **Automatic Recommendation Fetching**
   - Triggers when queue ≤ 2 songs
   - Based on currently playing track

✅ **Smart Spotify Validation**
   - 70% minimum confidence match
   - Filters out remixes, covers, karaoke
   - Prioritizes official releases

✅ **Background Downloads**
   - Max 2 concurrent downloads
   - Pre-downloads next 2 songs
   - Retry logic with exponential backoff

✅ **Reactive State Management**
   - StateFlow for UI updates
   - Real-time queue changes
   - Download progress tracking

✅ **Database Integration**
   - Checks for existing downloads
   - Saves metadata and lyrics
   - Prevents duplicate downloads

## Configuration

All settings are in RecommenderApi.kt:

```kotlin
// Spam keywords (filtered out)
private val SPAM_KEYWORDS = listOf(
    "remix", "cover", "karaoke", "instrumental", 
    "8d audio", "slowed", "reverb", ...
)

// Official keywords (prioritized)
private val OFFICIAL_KEYWORDS = listOf(
    "official", "music video", "vevo", 
    "official audio", "remastered", ...
)
```

In QueueManager.kt:

```kotlin
// Max concurrent downloads
if (downloadJobs.size >= 2) { ... }

// Validation threshold (70%)
if (confidence > 0.7) { ... }

// Top N recommendations
maxResults = 10
```

## Testing

```kotlin
// Test with any track
val testTrack = Track(
    uuid = "test-123",
    title = "Stairway to Heaven",
    artist = "Led Zeppelin",
    durationSec = 482,
    ytVideoId = "QkF3oxziUI4"
)

queueManager.initializeQueue(listOf(testTrack))

// Watch logs for:
// - "Fetching recommendations for: ..."
// - "Got X recommendations"
// - "Validated X out of Y recommendations"
// - "Starting download: ..."
```

## Troubleshooting

**No recommendations appearing?**
- Check YouTube video ID exists for the song
- Check internet connection
- Look for errors in logcat with tag "RecommenderApi"

**Downloads failing?**
- Check Spotify credentials in local.properties
- Verify SPOTIFY_CLIENT_ID and SPOTIFY_CLIENT_SECRET
- Check logcat for "SpotifyApi" errors

**Queue not auto-refilling?**
- Ensure queue size drops to ≤2
- Check that moveToNext() is being called
- Verify queueManager.cleanup() isn't called too early

## Dependencies

Required in `app/build.gradle.kts`:

```kotlin
dependencies {
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.gson)  // NEW - Added for YouTube Music API
    implementation(libs.kotlinx.coroutines.android)
}
```

Already added to your project! ✅

## Next Steps

1. **Test the system**
   ```kotlin
   playWithRecommendations(yourTrack)
   ```

2. **Monitor logs**
   - Filter by "RecommenderApi"
   - Filter by "QueueManager"
   - Watch for recommendations and downloads

3. **Customize settings**
   - Adjust spam keywords
   - Change max concurrent downloads
   - Modify validation threshold

4. **Integrate with UI**
   - Add queue display
   - Show downloading indicators
   - Add manual refresh button

## Example Full Implementation

See `PlayerViewModel.kt` for a complete working example with:
- State management
- Error handling
- UI integration examples
- Compose UI snippets

## Support

For detailed documentation, see:
- `RECOMMENDATION_SYSTEM.md` - Complete architecture guide
- `PlayerViewModel.kt` - Working example
- `QueueManager.kt` - Full API reference

---

**That's it! Your music app now has an infinite, smart recommendation queue! 🎉**
