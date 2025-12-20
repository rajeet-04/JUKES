# JUKE API Documentation

## Table of Contents
1. [Spotify/Spotdown API](#spotifyspotdown-api)
2. [YouTube Music Recommender API](#youtube-music-recommender-api)
3. [LRCLib Lyrics API](#lrclib-lyrics-api)
4. [API Integration Examples](#api-integration-examples)

---

## Spotify/Spotdown API

### Base URL
```
https://spotdown.org/api
```

### Authentication
No authentication required. Public API.

### Endpoints

#### 1. Search Songs

**Endpoint:** `GET /song-details`

**Description:** Search for songs on Spotify

**Parameters:**
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| url | string | Yes | Search query (song name, artist, or both) |

**Example Request:**
```kotlin
val response = httpClient.get("https://spotdown.org/api/song-details") {
    parameter("url", "Bohemian Rhapsody Queen")
}
```

**Example Response:**
```json
{
  "songs": [
    {
      "title": "Bohemian Rhapsody - Remastered 2011",
      "artist": "Queen",
      "thumbnail": "https://i.scdn.co/image/ab67616d0000b2731d56d63f6e9a0e7b7a9e2f7e",
      "url": "https://open.spotify.com/track/4u7EnebtmKWzUH433cf5Qv",
      "duration": "5:55"
    }
  ],
  "contentType": "application/json"
}
```

**Response Schema:**
```kotlin
data class SpotdownSearchResponse(
    val songs: List<SpotdownSong>,
    val contentType: String
)

data class SpotdownSong(
    val title: String,
    val artist: String,
    val thumbnail: String,
    val url: String,
    val duration: String  // Format: "MM:SS"
)
```

---

#### 2. Check Direct Download

**Endpoint:** `GET /check-direct-download`

**Description:** Check if a song is cached on the server for immediate download

**Parameters:**
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| url | string | Yes | Spotify track URL |

**Example Request:**
```kotlin
val response = httpClient.get("https://spotdown.org/api/check-direct-download") {
    parameter("url", "https://open.spotify.com/track/4u7EnebtmKWzUH433cf5Qv")
}
```

**Example Response:**
```json
{
  "cached": true
}
```

**Cache Status:**
- `cached: true` → Download will be instant
- `cached: false` → Download will take 30-50 seconds (server processes on demand)

---

#### 3. Download Song

**Endpoint:** `POST /download`

**Description:** Download MP3 file for a Spotify track

**Request Body:**
```json
{
  "url": "https://open.spotify.com/track/4u7EnebtmKWzUH433cf5Qv"
}
```

**Example Request:**
```kotlin
val response = httpClient.post("https://spotdown.org/api/download") {
    contentType(ContentType.Application.Json)
    setBody(mapOf("url" to spotifyUrl))
}

val audioData: ByteArray = response.body()
```

**Response:**
- Content-Type: `audio/mpeg`
- Body: Raw MP3 file data (binary)

**File Validation:**
```kotlin
// Check for ID3 tag
val isID3 = audioData[0] == 0x49.toByte() && 
           audioData[1] == 0x44.toByte() && 
           audioData[2] == 0x33.toByte()

// Check for MP3 frame sync
val isMP3Frame = audioData[0] == 0xFF.toByte() && 
                (audioData[1].toInt() and 0xE0) == 0xE0
```

**Error Handling:**
```kotlin
suspend fun downloadSong(spotifyUrl: String, retryAttempt: Int = 0): ByteArray {
    val maxRetries = 3
    val retryDelays = listOf(2000L, 4000L, 8000L)
    
    return try {
        val response = httpClient.post("$SPOTDOWN_BASE_URL/download") {
            setBody(mapOf("url" to spotifyUrl))
        }
        response.body()
    } catch (e: Exception) {
        if (retryAttempt < maxRetries) {
            delay(retryDelays[retryAttempt])
            return downloadSong(spotifyUrl, retryAttempt + 1)
        }
        throw e
    }
}
```

---

## YouTube Music Recommender API

### Overview
Uses two APIs:
1. **mp3juice** for YouTube search
2. **YouTube Music internal API** for recommendations

### 1. Search YouTube Videos

**Base URL:** `https://mp3juice3.ninja/api/yt-data`

**Endpoint:** `POST /api/yt-data`

**Description:** Search for YouTube videos matching a song

**Request Body:**
```json
{
  "query": "Bohemian Rhapsody Queen"
}
```

**Headers:**
```kotlin
val headers = mapOf(
    "Content-Type" to "application/json",
    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
)
```

**Example Request:**
```kotlin
val response = httpClient.post("https://mp3juice3.ninja/api/yt-data") {
    contentType(ContentType.Application.Json)
    setBody(SearchRequest(query = "Bohemian Rhapsody Queen"))
}
```

**Example Response:**
```json
{
  "items": [
    {
      "id": "fJ9rUzIMcZQ",
      "title": "Queen – Bohemian Rhapsody (Official Video Remastered)"
    },
    {
      "id": "k2tU2X0h9_I",
      "title": "Bohemian Rhapsody"
    }
  ]
}
```

**Matching Algorithm:**

```kotlin
// Step 1: Look for official keywords
val OFFICIAL_KEYWORDS = listOf(
    "official", "music video", "official video",
    "vevo", "official audio", "remastered"
)

fun getOfficialScore(title: String): Double {
    var score = 0.0
    OFFICIAL_KEYWORDS.forEach { keyword ->
        if (title.lowercase().contains(keyword)) {
            score += 1.0
        }
    }
    return score
}

// Step 2: If no official found, use similarity matching
fun calculateSimilarity(s1: String, s2: String): Double {
    // Levenshtein distance algorithm
    // Returns 0.0 to 1.0 (higher = more similar)
}
```

---

### 2. Get Recommendations

**Base URL:** `https://music.youtube.com/youtubei/v1/next?prettyPrint=true`

**Description:** Get personalized music recommendations based on a YouTube video

**Two-Step Process:**

#### Step 1: Get Radio Playlist ID

**Request Body:**
```json
{
  "videoId": "fJ9rUzIMcZQ",
  "context": {
    "client": {
      "hl": "en-IN",
      "gl": "IN",
      "userAgent": "Mozilla/5.0 ...",
      "clientName": "WEB_REMIX",
      "clientVersion": "1.20241220.01.00"
    }
  }
}
```

**Example Request:**
```kotlin
val context = YTMusicContext(
    client = YTMusicClient(
        hl = "en-IN",
        gl = "IN",
        clientName = "WEB_REMIX",
        clientVersion = "1.20241220.01.00"
    )
)

val payload = YTMusicRequestStep1(
    videoId = "fJ9rUzIMcZQ",
    context = context
)

val response = httpClient.post(YT_MUSIC_URL) {
    contentType(ContentType.Application.Json)
    setBody(payload)
}
```

**Extract Radio Playlist ID:**
```kotlin
val data: JsonObject = response.body()

val radioPlaylistId = data["contents"]?.jsonObject
    ?.get("singleColumnMusicWatchNextResultsRenderer")?.jsonObject
    ?.get("tabbedRenderer")?.jsonObject
    ?.get("watchNextTabbedResultsRenderer")?.jsonObject
    ?.get("tabs")?.jsonArray
    ?.get(0)?.jsonObject
    ?.get("tabRenderer")?.jsonObject
    ?.get("content")?.jsonObject
    ?.get("musicQueueRenderer")?.jsonObject
    ?.get("content")?.jsonObject
    ?.get("playlistPanelRenderer")?.jsonObject
    ?.get("contents")?.jsonArray
    ?.get(0)?.jsonObject
    ?.get("playlistPanelVideoRenderer")?.jsonObject
    ?.get("menu")?.jsonObject
    ?.get("menuRenderer")?.jsonObject
    ?.get("items")?.jsonArray
    ?.get(0)?.jsonObject
    ?.get("menuNavigationItemRenderer")?.jsonObject
    ?.get("navigationEndpoint")?.jsonObject
    ?.get("watchEndpoint")?.jsonObject
    ?.get("playlistId")?.jsonPrimitive?.content
```

#### Step 2: Get Full Track List

**Request Body:**
```json
{
  "tunerSettingValue": "AUTOMIX_SETTING_NORMAL",
  "videoId": "fJ9rUzIMcZQ",
  "playlistId": "RDAMVM...",
  "isAudioOnly": true,
  "context": { ... }
}
```

**Example Request:**
```kotlin
val payload = YTMusicRequestStep2(
    tunerSettingValue = "AUTOMIX_SETTING_NORMAL",
    videoId = videoId,
    playlistId = radioPlaylistId,
    isAudioOnly = true,
    context = context
)

val response = httpClient.post(YT_MUSIC_URL) {
    setBody(payload)
}
```

**Parse Recommendations:**
```kotlin
val fullQueue = data2["contents"]?.jsonObject
    ?.get("singleColumnMusicWatchNextResultsRenderer")?.jsonObject
    ?.get("tabbedRenderer")?.jsonObject
    ?.get("watchNextTabbedResultsRenderer")?.jsonObject
    ?.get("tabs")?.jsonArray
    ?.get(0)?.jsonObject
    ?.get("tabRenderer")?.jsonObject
    ?.get("content")?.jsonObject
    ?.get("musicQueueRenderer")?.jsonObject
    ?.get("content")?.jsonObject
    ?.get("playlistPanelRenderer")?.jsonObject
    ?.get("contents")?.jsonArray

val recommendations = mutableListOf<YouTubeRecommendation>()

fullQueue?.forEach { item ->
    val node = item.jsonObject["playlistPanelVideoRenderer"]?.jsonObject
    if (node != null) {
        val videoId = node["videoId"]?.jsonPrimitive?.content
        val title = node["title"]?.jsonObject
            ?.get("runs")?.jsonArray
            ?.get(0)?.jsonObject
            ?.get("text")?.jsonPrimitive?.content
        val artist = node["longBylineText"]?.jsonObject
            ?.get("runs")?.jsonArray
            ?.get(0)?.jsonObject
            ?.get("text")?.jsonPrimitive?.content
        
        if (videoId != null && title != null) {
            recommendations.add(YouTubeRecommendation(videoId, title, artist ?: "Unknown"))
        }
    }
}

// Return top 3 (skip first which is the seed song)
return recommendations.drop(1).take(3)
```

---

## LRCLib Lyrics API

### Base URL
```
https://lrclib.net/api
```

### Authentication
No authentication required.

### Get Lyrics

**Endpoint:** `GET /search`

**Description:** Search for song lyrics (both synced and plain)

**Parameters:**
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| q | string | Yes | Search query (title + artist) |

**Example Request:**
```kotlin
val query = "Bohemian Rhapsody Queen"
val response = httpClient.get("https://lrclib.net/api/search") {
    parameter("q", query)
}
```

**Example Response:**
```json
[
  {
    "id": 123456,
    "name": "Bohemian Rhapsody",
    "trackName": "Bohemian Rhapsody",
    "artistName": "Queen",
    "albumName": "A Night at the Opera",
    "duration": 355,
    "instrumental": false,
    "plainLyrics": "Is this the real life?\nIs this just fantasy?...",
    "syncedLyrics": "[00:00.00] Is this the real life?\n[00:03.50] Is this just fantasy?..."
  }
]
```

**Response Schema:**
```kotlin
data class LRCLibResult(
    val id: Int,
    val name: String,
    val trackName: String,
    val artistName: String,
    val albumName: String,
    val duration: Int,          // Duration in seconds
    val instrumental: Boolean,
    val plainLyrics: String,
    val syncedLyrics: String    // LRC format with timestamps
)
```

**Duration Matching:**
```kotlin
suspend fun searchLyrics(
    title: String,
    artist: String,
    duration: Int? = null
): LRCLibResult? {
    val query = "$title $artist"
    val response = httpClient.get("$LRCLIB_BASE_URL/search") {
        parameter("q", query)
    }
    
    val results: List<LRCLibResult> = response.body()
    
    if (results.isEmpty()) return null
    
    // Find best match within ±5 seconds
    if (duration != null) {
        val bestMatch = results.find { result ->
            kotlin.math.abs(result.duration - duration) < 5
        }
        if (bestMatch != null) return bestMatch
    }
    
    // Otherwise return first result
    return results.firstOrNull()
}
```

**LRC Format Example:**
```
[00:00.00] Is this the real life?
[00:03.50] Is this just fantasy?
[00:06.00] Caught in a landslide
[00:08.50] No escape from reality
```

---

## API Integration Examples

### Complete Song Download Flow

```kotlin
suspend fun downloadAndIndexSong(songQuery: String): Track {
    // 1. Search Spotify
    val searchResult = SpotifyApi.searchSongs(songQuery)
    val song = searchResult.songs.firstOrNull() 
        ?: throw Exception("No results found")
    
    // 2. Check cache status
    val cacheStatus = SpotifyApi.checkDirectDownload(song.url)
    val isCached = cacheStatus["cached"] ?: false
    
    Log.d(TAG, "Song cached: $isCached")
    
    // 3. Download MP3
    val audioData = SpotifyApi.downloadSong(song.url)
    
    // 4. Validate MP3
    val isValid = audioData[0] == 0x49.toByte() && 
                 audioData[1] == 0x44.toByte() && 
                 audioData[2] == 0x33.toByte()
    
    if (!isValid) throw Exception("Invalid MP3 file")
    
    // 5. Save to file
    val uuid = UUID.randomUUID().toString()
    val file = File(context.filesDir, "music/$uuid.mp3")
    file.writeBytes(audioData)
    
    // 6. Fetch metadata in parallel
    val durationSec = SpotifyApi.parseDuration(song.duration)
    val lyrics = async { SpotifyApi.searchLyrics(song.title, song.artist, durationSec) }
    val ytVideoId = async { RecommenderApi.getBestVideoMatch("${song.title} ${song.artist}") }
    
    // 7. Create track
    val track = Track(
        uuid = uuid,
        title = song.title,
        artist = song.artist,
        durationSec = durationSec,
        localUri = file.absolutePath,
        ytVideoId = ytVideoId.await(),
        syncedLyrics = lyrics.await()?.syncedLyrics,
        plainLyrics = lyrics.await()?.plainLyrics,
        isFavourite = false,
        playCount = 0
    )
    
    // 8. Save to database
    database.trackDao().insertTrack(track.toEntity())
    
    return track
}
```

### Auto-Play Recommendations

```kotlin
suspend fun getAndDownloadRecommendations(currentTrack: Track): List<Track> {
    // 1. Get YouTube video ID from current track
    val videoId = currentTrack.ytVideoId 
        ?: return emptyList()
    
    // 2. Fetch recommendations
    val recs = RecommenderApi.getRecommendations(videoId)
    
    // 3. Download top 3 recommendations
    val downloadedTracks = recs.mapNotNull { rec ->
        try {
            // Search Spotify
            val searchResult = SpotifyApi.searchSongs("${rec.title} ${rec.artist}")
            val song = searchResult.songs.firstOrNull() ?: return@mapNotNull null
            
            // Download and index
            val track = downloadAndIndexSong("${song.title} ${song.artist}")
            track
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download ${rec.title}: ${e.message}")
            null
        }
    }
    
    return downloadedTracks
}
```

### Error Handling Best Practices

```kotlin
// Retry logic with exponential backoff
suspend fun <T> retryWithBackoff(
    maxRetries: Int = 5,
    operationName: String,
    block: suspend () -> T
): T {
    var lastError: Exception? = null
    
    for (attempt in 1..maxRetries) {
        try {
            return block()
        } catch (e: Exception) {
            lastError = e
            
            val isRetryable = e.message?.contains("500") == true ||
                             e.message?.contains("timeout") == true
            
            if (attempt < maxRetries && isRetryable) {
                val delayMs = minOf(1000L * (1 shl (attempt - 1)), 10000L)
                Log.d(TAG, "[$operationName] Retry $attempt/$maxRetries in ${delayMs}ms")
                delay(delayMs)
            } else {
                throw e
            }
        }
    }
    
    throw lastError ?: Exception("Operation failed")
}

// Usage
val track = retryWithBackoff(
    maxRetries = 5,
    operationName = "Download ${song.title}"
) {
    downloadAndIndexSong(song.title)
}
```

---

## Rate Limiting

### Spotdown API
- **No explicit rate limits documented**
- Recommended: Max 10 requests/second
- Use exponential backoff on 429 errors

### YouTube Music API
- **No explicit rate limits documented**
- Recommended: Max 5 requests/second
- Use same User-Agent as browser

### LRCLib API
- **No explicit rate limits documented**
- Recommended: Max 20 requests/second
- Cache results to minimize requests

---

## API Status Codes

### Common Status Codes

| Code | Meaning | Action |
|------|---------|--------|
| 200 | Success | Process response |
| 400 | Bad Request | Check parameters |
| 404 | Not Found | Resource doesn't exist |
| 429 | Too Many Requests | Implement backoff |
| 500 | Server Error | Retry with backoff |
| 503 | Service Unavailable | Wait and retry |

---

## Testing APIs

### Using cURL

```bash
# Search songs
curl -X GET "https://spotdown.org/api/song-details?url=Bohemian%20Rhapsody"

# Check cache
curl -X GET "https://spotdown.org/api/check-direct-download?url=https://open.spotify.com/track/4u7EnebtmKWzUH433cf5Qv"

# YouTube search
curl -X POST "https://mp3juice3.ninja/api/yt-data" \
  -H "Content-Type: application/json" \
  -d '{"query":"Bohemian Rhapsody Queen"}'

# Lyrics search
curl -X GET "https://lrclib.net/api/search?q=Bohemian%20Rhapsody%20Queen"
```

### Using Postman

Import this collection:
```json
{
  "info": {
    "name": "JUKE APIs"
  },
  "item": [
    {
      "name": "Search Songs",
      "request": {
        "method": "GET",
        "url": "https://spotdown.org/api/song-details?url=Bohemian Rhapsody"
      }
    }
  ]
}
```

---

## Troubleshooting

### Common Issues

#### 1. Download Returns Non-MP3 Data
**Cause:** Server returned error page as HTML
**Solution:** Check first 3 bytes, validate file format

#### 2. Recommendations Return Empty List
**Cause:** Invalid video ID or API structure changed
**Solution:** Log full JSON response, update parsing logic

#### 3. Lyrics Not Found
**Cause:** Song not in LRCLib database
**Solution:** Gracefully handle null, show "No lyrics available"

#### 4. Timeout Errors
**Cause:** Slow network or server processing
**Solution:** Increase timeout, implement retry logic

---

## API Updates

### Monitoring for Changes
- YouTube Music API structure may change without notice
- Test recommendation parsing regularly
- Implement version checking if possible
- Log full responses for debugging

### Fallback Strategies
1. If Spotdown fails → Try alternative APIs
2. If LRCLib fails → Continue without lyrics
3. If YouTube Music fails → Use simpler search API

---

## License & Terms

- **Spotdown:** Public API, terms not specified
- **YouTube Music:** Use responsibly, respect ToS
- **LRCLib:** Free and open, attribution appreciated

**Disclaimer:** These are third-party APIs. JUKE is not responsible for their availability or data accuracy.
