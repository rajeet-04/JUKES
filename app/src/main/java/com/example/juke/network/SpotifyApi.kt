package com.example.juke.network

import android.util.Base64
import android.util.Log
import android.util.Log.e
import com.example.juke.BuildConfig
import com.example.juke.models.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs

/**
 * Official Spotify Web API Service.
 * 
 * This service handles:
 * 1. OAuth authentication with Client Credentials flow
 * 2. Searching for songs on Spotify (US market)
 * 3. Downloading MP3 files from Spotdown (fallback)
 * 4. Fetching lyrics from LRCLib
 */
object SpotifyApi {
    
    private const val TAG = "SpotifyApi"
    private const val SPOTIFY_API_BASE_URL = "https://api.spotify.com/v1"
    private const val SPOTIFY_ACCOUNTS_URL = "https://accounts.spotify.com/api/token"
    private const val SPOTDOWN_BASE_URL = "https://spotdown.org/api"
    private const val SPOTDOWN_API_KEY = "b7dced12866eeef7ada4537c3fa952135e6c9680b0b332bcad99866823b6199b"
    private const val SPOTMATE_BASE_URL = "https://spotmate.online"
    private const val LRCLIB_BASE_URL = "https://lrclib.meek.workers.dev"
    
    private var accessToken: String? = null
    private var tokenExpiryTime: Long = 0
    private val tokenMutex = Mutex()
    
    // Dynamic market code (set from app preferences)
    var defaultMarket: String = "US"
        private set
    
    fun setDefaultMarket(marketCode: String) {
        defaultMarket = marketCode.uppercase().take(2)
        Log.d(TAG, "Default market set to: $defaultMarket")
    }

    private var json: Json
        get() = Json { ignoreUnknownKeys = true }
        set(value) {
            TODO()
        }

    /**
     * Get a valid OAuth access token.
     * Uses Client Credentials flow with automatic refresh.
     */
    private suspend fun getAccessToken(): String {
        tokenMutex.withLock {
            // Check if current token is still valid (with 5 minute buffer)
            if (accessToken != null && System.currentTimeMillis() < tokenExpiryTime - 300000) {
                return accessToken!!
            }
            
            Log.d(TAG, "Requesting new Spotify OAuth token")
            
            val clientId = BuildConfig.SPOTIFY_CLIENT_ID
            val clientSecret = BuildConfig.SPOTIFY_CLIENT_SECRET
            
            if (clientId.isEmpty() || clientSecret.isEmpty()) {
                throw Exception("Spotify credentials not configured. Please add SPOTIFY_CLIENT_ID and SPOTIFY_CLIENT_SECRET to local.properties")
            }
            
            // Encode credentials in Base64
            val credentials = "$clientId:$clientSecret"
            val encodedCredentials = Base64.encodeToString(
                credentials.toByteArray(),
                Base64.NO_WRAP
            )
            
            try {
                Log.d(TAG, "Client ID: ${clientId.take(10)}...")
                Log.d(TAG, "Credentials length: ${encodedCredentials.length}")
                
                val response: HttpResponse = ApiClient.httpClient.post(SPOTIFY_ACCOUNTS_URL) {
                    header("Authorization", "Basic $encodedCredentials")
                    // Use form-encoded body for token request
                    setBody(FormDataContent(Parameters.build {
                        append("grant_type", "client_credentials")
                    }))
                }

                val statusCode = response.status.value
                val raw = response.bodyAsText()
                
                Log.d(TAG, "Spotify token response status: $statusCode")
                Log.d(TAG, "Spotify token raw response (first 1000 chars): ${raw.take(1000)}")

                if (statusCode != 200) {
                    Log.e(TAG, "Spotify auth failed with status $statusCode")
                    throw Exception("Spotify returned status $statusCode: ${raw.take(200)}")
                }

                val tokenResponse: SpotifyTokenResponse = try {
                    json.decodeFromString(raw)
                } catch (serEx: Exception) {
                    Log.e(TAG, "Failed to parse token response: ${serEx.message}")
                    Log.e(TAG, "Full response body: $raw")
                    throw Exception("Invalid token response format. Status: $statusCode, Body: ${raw.take(200)}")
                }

                accessToken = tokenResponse.accessToken
                tokenExpiryTime = System.currentTimeMillis() + (tokenResponse.expiresIn * 1000L)

                Log.d(TAG, "Successfully obtained access token (expires in ${tokenResponse.expiresIn}s)")

                return accessToken!!

            } catch (e: Exception) {
                Log.e(TAG, "Error obtaining OAuth token: ${e.message}", e)
                throw Exception("Failed to authenticate with Spotify: ${e.message}")
            }
        }
    }
    
    /**
     * Search Spotify for tracks, artists, playlists, and albums.
     * 
     * @param query Search query
     * @param types Types to search (track, artist, playlist, album)
     * @param market Market code (default PK for Nepal)
     * @return SpotifySearchResponse with all requested types
     */
    suspend fun search(
        query: String,
        types: List<String> = listOf("track", "artist", "playlist", "album"),
        market: String = defaultMarket
    ): SpotifySearchResponse {
        Log.d(TAG, "Searching Spotify for: $query (types: ${types.joinToString(",")})")
        
        try {
            val token = getAccessToken()
            
            val response: HttpResponse = ApiClient.httpClient.get("$SPOTIFY_API_BASE_URL/search") {
                header("Authorization", "Bearer $token")
                parameter("q", query)
                parameter("type", types.joinToString(","))
                parameter("market", market)
                parameter("limit", 10)
            }
            
            val searchResponse: SpotifySearchResponse = response.body()
            
            Log.d(TAG, "Found ${searchResponse.tracks?.items?.size ?: 0} tracks, " +
                      "${searchResponse.artists?.items?.size ?: 0} artists, " +
                      "${searchResponse.playlists?.items?.filterNotNull()?.size ?: 0} playlists, " +
                      "${searchResponse.albums?.items?.size ?: 0} albums")
            
            return searchResponse
            
        } catch (e: Exception) {
            Log.e(TAG, "Error searching Spotify: ${e.message}", e)
            throw e
        }
    }

    /**
     * Get artist's albums.
     * 
     * @param artistId Spotify artist ID
     * @param market Market code
     * @param limit Number of albums to fetch
     */
    suspend fun getArtistAlbums(
        artistId: String,
        market: String = defaultMarket,
        limit: Int = 50
    ): SpotifyAlbumsResponse {
        Log.d(TAG, "Fetching albums for artist: $artistId")
        
        try {
            val token = getAccessToken()
            val actualLimit = limit.coerceIn(1, 50)
            
            val response: HttpResponse = ApiClient.httpClient.get(
                "$SPOTIFY_API_BASE_URL/artists/$artistId/albums"
            ) {
                header("Authorization", "Bearer $token")
                parameter("market", market)
                parameter("limit", actualLimit)
            }
            
            val statusCode = response.status.value
            if (statusCode != 200) {
                val raw = response.bodyAsText()
                Log.e(TAG, "Spotify API failed with status $statusCode: ${raw.take(200)}")
                throw Exception("Spotify returned status $statusCode: ${raw.take(200)}")
            }
            
            return response.body()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching artist albums: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Get artist's top tracks.
     * 
     * @param artistId Spotify artist ID
     * @param market Market code
     */
    suspend fun getArtistTopTracks(
        artistId: String,
        market: String = defaultMarket
    ): SpotifyTopTracksResponse {
        Log.d(TAG, "Fetching top tracks for artist: $artistId")
        
        try {
            val token = getAccessToken()
            
            val response: HttpResponse = ApiClient.httpClient.get(
                "$SPOTIFY_API_BASE_URL/artists/$artistId/top-tracks"
            ) {
                header("Authorization", "Bearer $token")
                parameter("market", market)
            }
            
            return response.body()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching artist top tracks: ${e.message}", e)
            throw e
        }
    }

    /**
     * Get album tracks.
     * 
     * @param albumId Spotify album ID
     * @param market Market code
     * @param limit Number of tracks to fetch
     */
    suspend fun getAlbumTracks(
        albumId: String,
        market: String = defaultMarket,
        limit: Int = 50
    ): SpotifyAlbumTracksResponse {
        Log.d(TAG, "Fetching album tracks: $albumId")
        
        try {
            val token = getAccessToken()
            val actualLimit = limit.coerceIn(1, 50)
            
            val response: HttpResponse = ApiClient.httpClient.get(
                "$SPOTIFY_API_BASE_URL/albums/$albumId/tracks"
            ) {
                header("Authorization", "Bearer $token")
                parameter("market", market)
                parameter("limit", actualLimit)
            }
            
            val statusCode = response.status.value
            if (statusCode != 200) {
                val raw = response.bodyAsText()
                Log.e(TAG, "Spotify API failed with status $statusCode: ${raw.take(200)}")
                throw Exception("Spotify returned status $statusCode: ${raw.take(200)}")
            }
            
            return response.body()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching album tracks: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Get a single track by ID.
     * 
     * @param trackId Spotify track ID
     * @param market Market code
     */
    suspend fun getTrack(
        trackId: String,
        market: String = defaultMarket
    ): SpotifyTrack {
        Log.d(TAG, "Fetching track: $trackId")
        
        try {
            val token = getAccessToken()
            
            val response: HttpResponse = ApiClient.httpClient.get(
                "$SPOTIFY_API_BASE_URL/tracks/$trackId"
            ) {
                header("Authorization", "Bearer $token")
                parameter("market", market)
            }
            
            return response.body()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching track: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Get a single artist by ID.
     * 
     * @param artistId Spotify artist ID
     */
    suspend fun getArtist(
        artistId: String
    ): SpotifyArtist {
        Log.d(TAG, "Fetching artist: $artistId")
        
        try {
            val token = getAccessToken()
            
            val response: HttpResponse = ApiClient.httpClient.get(
                "$SPOTIFY_API_BASE_URL/artists/$artistId"
            ) {
                header("Authorization", "Bearer $token")
            }
            
            return response.body()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching artist: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Get a single playlist by ID.
     * 
     * @param playlistId Spotify playlist ID
     * @param market Market code
     */
    suspend fun getPlaylist(
        playlistId: String,
        market: String = defaultMarket
    ): SpotifyPlaylist {
        Log.d(TAG, "Fetching playlist: $playlistId")
        
        try {
            val token = getAccessToken()
            
            val response: HttpResponse = ApiClient.httpClient.get(
                "$SPOTIFY_API_BASE_URL/playlists/$playlistId"
            ) {
                header("Authorization", "Bearer $token")
                parameter("market", market)
            }
            
            val statusCode = response.status.value
            if (statusCode != 200) {
                val raw = response.bodyAsText()
                Log.e(TAG, "Spotify API failed with status $statusCode: ${raw.take(200)}")
                throw Exception("The playlist is either private or does not exist.")
            }
            
            return response.body()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching playlist: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Get a single album by ID.
     * 
     * @param albumId Spotify album ID
     * @param market Market code
     */
    suspend fun getAlbum(
        albumId: String,
        market: String = defaultMarket
    ): SpotifyAlbum {
        Log.d(TAG, "Fetching album: $albumId")
        
        try {
            val token = getAccessToken()
            
            val response: HttpResponse = ApiClient.httpClient.get(
                "$SPOTIFY_API_BASE_URL/albums/$albumId"
            ) {
                header("Authorization", "Bearer $token")
                parameter("market", market)
            }
            
            return response.body()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching album: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Get playlist tracks with pagination support.
     * Fetches all tracks from the playlist by handling pagination automatically.
     *
     * @param playlistId Spotify playlist ID
     * @param market Market code
     * @return SpotifyPlaylistTracksResponse with all tracks (items will contain all tracks)
     */
    suspend fun getPlaylistTracks(
        playlistId: String,
        market: String = defaultMarket
    ): SpotifyPlaylistTracksResponse {
        Log.d(TAG, "Fetching all playlist tracks: $playlistId")

        try {
            val token = getAccessToken()
            val allItems = mutableListOf<SpotifyPlaylistItem>()
            var offset = 0
            val limit = 100 // Maximum allowed by Spotify API

            while (true) {
                val response: HttpResponse = ApiClient.httpClient.get(
                    "$SPOTIFY_API_BASE_URL/playlists/$playlistId/tracks"
                ) {
                    header("Authorization", "Bearer $token")
                    parameter("market", market)
                    parameter("limit", limit)
                    parameter("offset", offset)
                }

                val statusCode = response.status.value
                if (statusCode != 200) {
                    val raw = response.bodyAsText()
                    Log.e(TAG, "Spotify API failed with status $statusCode: ${raw.take(200)}")
                    throw Exception("The playlist is either private or does not exist.")
                }

                val pageResponse: SpotifyPlaylistTracksResponse = response.body()
                
                // Filter out local tracks and tracks with missing data
                val validItems = pageResponse.items.filter { item ->
                    val track = item.track
                    track != null && !track.isLocal && track.id != null && track.externalUrls.spotify != null
                }
                
                allItems.addAll(validItems)

                // Check if there are more pages
                if (pageResponse.next == null) {
                    // No more pages, return combined response
                    return SpotifyPlaylistTracksResponse(
                        href = pageResponse.href,
                        limit = pageResponse.limit,
                        next = null,
                        offset = 0,
                        previous = null,
                        total = pageResponse.total,
                        items = allItems
                    )
                }

                offset += limit
                Log.d(TAG, "Fetched ${allItems.size}/${pageResponse.total} tracks for playlist $playlistId")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error fetching playlist tracks: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Search for songs on Spotify using official Web API.
     * 
     * @param query Search query (song name, artist, or both)
     * @return List of Spotify tracks with metadata
     * @throws Exception if search fails
     */
    @Deprecated("Use search() instead", ReplaceWith("search(query, listOf(\"track\")).tracks?.items ?: emptyList()"))
    suspend fun searchSongsOld(query: String): List<SpotifyTrack> {
        Log.d(TAG, "Searching Spotify for: $query")
        
        try {
            val token = getAccessToken()
            
            val response: HttpResponse = ApiClient.httpClient.get("$SPOTIFY_API_BASE_URL/search") {
                header("Authorization", "Bearer $token")
                parameter("q", query)
                parameter("type", "track")
                parameter("market", defaultMarket)
                parameter("limit", 20)
            }
            
            val searchResponse: SpotifySearchResponse = response.body()
            val tracks = searchResponse.tracks?.items ?: emptyList()
            
            Log.d(TAG, "Found ${tracks.size} tracks")
            
            return tracks
            
        } catch (e: Exception) {
            Log.e(TAG, "Error searching Spotify: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Check if a Spotify song is cached on Spotdown for faster download.
     * 
     * @param spotifyUrl Spotify track URL (e.g., https://open.spotify.com/track/...)
     * @return SpotdownCheckResponse with cached boolean and status
     */
    suspend fun checkDirectDownload(spotifyUrl: String): SpotdownCheckResponse {
        return try {
            val response = ApiClient.httpClient.get("$SPOTDOWN_BASE_URL/check-direct-download") {
                parameter("url", spotifyUrl)
                header("x-api-key", SPOTDOWN_API_KEY)
            }
            
            try {
                response.body()
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing checkDirectDownload response: ${e.message}")
                // If parsing fails (e.g. error message structure), assume not cached but log it
                SpotdownCheckResponse(cached = false, success = false, message = "Parsing error: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking direct download: ${e.message}", e)
            SpotdownCheckResponse(cached = false, success = false, message = "Network error: ${e.message}")
        }
    }
    
    /**
     * Download an MP3 file from Spotdown using Spotify URL.
     * 
     * This method includes:
     * - Retry logic with exponential backoff (up to 3 retries)
     * - MP3 file validation (checks for ID3 tags or MP3 frame sync)
     * - 2-minute timeout
     * 
     * @param spotifyUrl Spotify track URL (e.g., https://open.spotify.com/track/...)
     * @param retryAttempt Current retry attempt (internal use)
     * @return ByteArray of MP3 file data
     * @throws Exception if download fails after all retries
     */
    suspend fun downloadSong(
        spotifyUrl: String,
        retryAttempt: Int = 0
    ): ByteArray {
        val maxRetries = 3
        val retryDelays = listOf(2000L, 4000L, 8000L) // 2s, 4s, 8s
        
        return try {
            Log.d(TAG, "Making download request for URL: $spotifyUrl (attempt ${retryAttempt + 1})")
            
            val response = ApiClient.httpClient.post("$SPOTDOWN_BASE_URL/download") {
                contentType(ContentType.Application.Json)
                header("x-api-key", SPOTDOWN_API_KEY)
                setBody(mapOf("url" to spotifyUrl))
            }
            
            val audioData: ByteArray = response.body()
            Log.d(TAG, "Download response data size: ${audioData.size} bytes")
            
            // Validate MP3 file
            if (audioData.size < 3) {
                throw Exception("Downloaded file is too small")
            }
            
            val isID3 = audioData[0] == 0x49.toByte() && 
                       audioData[1] == 0x44.toByte() && 
                       audioData[2] == 0x33.toByte() // "ID3"
            
            val isMP3Frame = audioData[0] == 0xFF.toByte() && 
                            (audioData[1].toInt() and 0xE0) == 0xE0
            
            Log.d(TAG, "First 3 bytes: ${audioData.take(3).joinToString(" ") { "0x%02X".format(it) }}")
            Log.d(TAG, "Is ID3 tag: $isID3")
            Log.d(TAG, "Is MP3 frame: $isMP3Frame")
            
            if (!isID3 && !isMP3Frame) {
                val textResponse = audioData.take(500).toByteArray().decodeToString()
                Log.e(TAG, "Received non-MP3 response: $textResponse")
                throw Exception("Downloaded file is not a valid MP3")
            }
            
            audioData
            
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading song: ${e.message}", e)
            
            // Retry on 500 errors
            if (retryAttempt < maxRetries) {
                val delay = retryDelays[retryAttempt]
                Log.d(TAG, "[Download Retry] Error, retrying in ${delay}ms (attempt ${retryAttempt + 1}/$maxRetries)...")
                delay(delay)
                return downloadSong(spotifyUrl, retryAttempt + 1)
            }
            
            throw e
        }
    }

    /**
     * Download from Spotmate (Fallback Source).
     *
     * @param spotifyUrl Spotify track URL
     * @return ByteArray of MP3 file data
     */
    /**
     * Get the direct download/stream URL from Spotmate.
     * Useful for instant playback.
     * 
     * @param spotifyUrl Spotify track URL
     * @return Direct MP3 URL
     */
    suspend fun getSpotmateStreamUrl(spotifyUrl: String): String {
        Log.d(TAG, "Fetching Spotmate stream URL for: $spotifyUrl")
        
        try {
            // 1. GET request to fetch cookies and CSRF token
            Log.d(TAG, "Spotmate Initial GET: $SPOTMATE_BASE_URL/en1")
            val initialResponse: HttpResponse = ApiClient.httpClient.get("$SPOTMATE_BASE_URL/en1")
            val initialStatus = initialResponse.status.value
            val body = initialResponse.bodyAsText()
            Log.d(TAG, "Spotmate Initial Response ($initialStatus): ${body.take(500)}")
            val setCookieHeaders = initialResponse.headers.getAll("Set-Cookie") ?: emptyList()
            
            // 2. Extract Cookies
            var xsrfToken = ""
            var spotSession = ""
            var siteTotalId = ""
            
            for (cookie in setCookieHeaders) {
                if (cookie.contains("XSRF-TOKEN=")) {
                    xsrfToken = cookie.substringAfter("XSRF-TOKEN=").substringBefore(";")
                }
                if (cookie.contains("spotmateonline_session=")) {
                    spotSession = cookie.substringAfter("spotmateonline_session=").substringBefore(";")
                }
                if (cookie.contains("SITE_TOTAL_ID=")) {
                    siteTotalId = cookie.substringAfter("SITE_TOTAL_ID=").substringBefore(";")
                }
            }
            
            // 3. Extract CSRF from Meta Tag
            val csrfPattern = Regex("<meta name=\"csrf-token\" content=\"([^\"]+)\"")
            val xCsrfToken = csrfPattern.find(body)?.groupValues?.get(1) ?: ""
            Log.d(TAG, "Spotmate Extracted: XSRF-TOKEN=${xsrfToken.take(10)}..., Session=${spotSession.take(10)}..., MetaCSRF=${xCsrfToken.take(10)}...")
            
            // 4. POST request to convert using JSON body
            Log.d(TAG, "Spotmate POST /convert for: $spotifyUrl")
            val postResponse: HttpResponse = ApiClient.httpClient.post("$SPOTMATE_BASE_URL/convert") {
                header("Origin", SPOTMATE_BASE_URL)
                header("Referer", "$SPOTMATE_BASE_URL/en1")
                header("Sec-Fetch-Site", "same-site")
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0")
                header("X-CSRF-TOKEN", xCsrfToken)
                header("Cookie", "XSRF-TOKEN=$xsrfToken; spotmateonline_session=$spotSession; SITE_TOTAL_ID=$siteTotalId;")
                contentType(ContentType.Application.Json)
                
                setBody(mapOf("urls" to spotifyUrl))
            }
            
            val postStatus = postResponse.status.value
            val postBody = postResponse.bodyAsText()
            Log.d(TAG, "Spotmate Convert Response ($postStatus): ${postBody.take(1000)}")
            
            // 5. Extract URL from JSON
            // Handle potentially escaped forward slashes and surrounding quotes/spaces
            val urlPattern = Regex("\"url\"\\s*:\\s*\"([^\"]+)\"")
            val downloadUrlMatch = urlPattern.find(postBody)
            
            if (downloadUrlMatch != null) {
                // Remove escaped slashes if present (e.g. \/ -> /)
                return downloadUrlMatch.groupValues[1].replace("\\/", "/")
            } else {
                Log.e(TAG, "Failed to extract URL from Spotmate response. Body length: ${postBody.length}")
                Log.e(TAG, "Response Body Quote: ${postBody.take(1000)}")
                throw Exception("Failed to extract URL from Spotmate response")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting Spotmate URL: ${e.message}", e)
            throw e
        }
    }

    /**
     * Download from Spotmate (Fallback Source).
     *
     * @param spotifyUrl Spotify track URL
     * @return ByteArray of MP3 file data
     */
    suspend fun downloadSongFromSpotmate(spotifyUrl: String): ByteArray {
        Log.d(TAG, "Attempting fallback download from Spotmate for: $spotifyUrl")
        
        try {
            val downloadUrl = getSpotmateStreamUrl(spotifyUrl)
            Log.d(TAG, "Extracted Spotmate download URL: $downloadUrl")
            
            // Download the file
            val response: HttpResponse = ApiClient.httpClient.get(downloadUrl)
            val audioData: ByteArray = response.body()
            
            if (audioData.size < 100_000) {
                 throw Exception("Spotmate download too small")
            }
            
            return audioData
            
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading from Spotmate: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Search for lyrics on LRCLib using specific query parameters.
     * 
     * LRCLib provides both plain text and synced (LRC format) lyrics.
     * Uses track_name, artist_name, and album_name parameters for exact matching.
     * Validates lyrics match against provided track details.
     * 
     * @param title Song title
     * @param artist Artist name
     * @param album Album name (optional)
     * @param duration Optional duration in seconds for better matching
     * @return LRCLibResult or null if not found or doesn't match
     */
    suspend fun searchLyrics(
        title: String,
        artist: String,
        album: String = "",
        duration: Int? = null
    ): LRCLibResult? {
        return try {
            // 1. Clean Title to remove (From ...) or (feat ...) metadata
            val cleanedTitle = cleanSongTitle(title)
            Log.d(TAG, "LRCLib Search: $LRCLIB_BASE_URL?track=$cleanedTitle&artist=$artist (Original: $title)")
            
            val response = ApiClient.httpClient.get(LRCLIB_BASE_URL) {
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0")
                parameter("track", cleanedTitle)
                parameter("artist", artist)
                if (album.isNotBlank()) {
                    parameter("album", album)
                }
            }
            
            val statusCode = response.status.value
            val raw = response.bodyAsText()
            Log.d(TAG, "LRCLib Response ($statusCode): ${raw.take(1000)}")
            
            val results: List<LRCLibResult> = try {
                json.decodeFromString(raw)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decode LRCLib response: ${e.message}")
                emptyList()
            }
            
            // Find the best matching result based on validation score AND duration proximity AND synced lyrics availability
            var bestMatch = if (results.isNotEmpty()) {
                results
                    .map { result -> 
                        val score = validateLyricsMatch(result, title, artist, duration)
                        val durationDiff = if (duration != null) abs(result.duration - duration) else Double.MAX_VALUE
                        val hasSynced = !result.syncedLyrics.isNullOrBlank()
                        Triple(result, score, durationDiff)
                    }
                    .filter { it.second > 0 } // Only consider results with some match
                    .sortedWith(
                        compareByDescending<Triple<LRCLibResult, Int, Double>> { it.second } // 1. Highest Score
                        .thenByDescending { it.first.syncedLyrics?.isNotBlank() == true }    // 2. Has Synced Lyrics
                        .thenBy { it.third } // 3. Lowest Duration Difference
                    )
                    .firstOrNull()
                    ?.first
            } else null

            // 2. Fallback Search (Individual Artists)
            if (bestMatch == null) {
                val separators = charArrayOf(',', '&')
                val individualArtists = artist.split(*separators)
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && !it.equals(artist, ignoreCase = true) }

                if (individualArtists.isNotEmpty()) {
                    Log.d(TAG, "Primary lyrics search failed. Attempting fallback for artists: $individualArtists")
                    
                    for (singleArtist in individualArtists) {
                        try {
                            val fallbackResponse = ApiClient.httpClient.get(LRCLIB_BASE_URL) {
                                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0")
                                parameter("track", cleanedTitle)
                                parameter("artist", singleArtist)
                            }
                            
                            val fallbackResults: List<LRCLibResult> = fallbackResponse.body()
                            
                            // Strict Validation for Fallback
                            // Duration must be within 5% tolerance
                            // Find ALL valid candidates, then pick based on criteria
                            val validFallback = fallbackResults
                                .filter { result ->
                                    val resultTitle = result.trackName.lowercase().trim()
                                    val normalizedTitle = title.lowercase().trim()
                                    val titleMatch = resultTitle.contains(normalizedTitle) || normalizedTitle.contains(resultTitle)
                                    
                                    val durationMatch = if (duration != null && duration > 0) {
                                        val tolerance = duration * 0.05 // 5% tolerance
                                        val diff = abs(result.duration - duration)
                                        diff <= tolerance
                                    } else {
                                        true 
                                    }
    
                                    titleMatch && durationMatch
                                }
                                .sortedWith(
                                    compareByDescending<LRCLibResult> { !it.syncedLyrics.isNullOrBlank() } // 1. Has Synced Lyrics
                                    .thenBy { if (duration != null) abs(it.duration - duration) else 0.0 } // 2. Closest Duration
                                )
                                .firstOrNull()

                            if (validFallback != null) {
                                Log.d(TAG, "Fallback lyrics found with artist '$singleArtist'")
                                bestMatch = validFallback
                                break // Stop if we found a good match
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Fallback search failed for artist '$singleArtist': ${e.message}")
                        }
                    }
                }
            }
            
            bestMatch
            
        } catch (e: Exception) {
            Log.e(TAG, "Error searching lyrics: ${e.message}", e)
            null
        }
    }

    /**
     * Validate if lyrics result matches the track details.
     * Returns a score from 0-3 based on artist, title, and duration match.
     * 
     * @param result LRCLib result to validate
     * @param expectedTitle Expected track title
     * @param expectedArtist Expected artist name
     * @param expectedDuration Expected duration in seconds (optional)
     * @return Match score (0-3)
     */
    private fun validateLyricsMatch(
        result: LRCLibResult,
        expectedTitle: String,
        expectedArtist: String,
        expectedDuration: Int?
    ): Int {
        var score = 0
        
        // Normalize strings for comparison (lowercase, trim)
        val normalizedTitle = expectedTitle.lowercase().trim()
        val normalizedArtist = expectedArtist.lowercase().trim()
        val resultTitle = result.trackName.lowercase().trim()
        val resultArtist = result.artistName.lowercase().trim()
        
        // Artist match (1 point)
        if (resultArtist.contains(normalizedArtist) || normalizedArtist.contains(resultArtist)) {
            score += 1
        }
        
        // Title match (1 point)
        if (resultTitle.contains(normalizedTitle) || normalizedTitle.contains(resultTitle)) {
            score += 1
        }
        
        // Duration match (1 point) - within 15 seconds tolerance
        if (expectedDuration != null) {
            val durationDiff = abs(result.duration - expectedDuration)
            if (durationDiff < 15) {
                score += 1
            }
        } else {
            // If no duration provided, give partial credit
            score += 1
        }
        
        Log.d(TAG, "Lyrics validation - Title: '$normalizedTitle' vs '$resultTitle', " +
                   "Artist: '$normalizedArtist' vs '$resultArtist', " +
                   "Duration: $expectedDuration vs ${result.duration}, Score: $score")
        
        return score
    }

    /**
     * Parse duration string from "MM:SS" format to seconds.
     * 
     * @param durationStr Duration string (e.g., "3:45")
     * @return Duration in seconds
     */
    fun parseDuration(durationStr: String): Int {
        val parts = durationStr.split(":")
        if (parts.size == 2) {
            val minutes = parts[0].toIntOrNull() ?: 0
            val seconds = parts[1].toIntOrNull() ?: 0
            return minutes * 60 + seconds
        }
        return 0
    }

    /**
     * Cleans song title by removing metadata in parentheses like (From ...), (feat ...), etc.
     */
    private fun cleanSongTitle(title: String): String {
        // Regex to match content in parentheses starting with specific keywords
        // Matches: (From ...), (Feat ...), (Ft ...), (With ...), (Live ...), (Remaster ...)
        // Case insensitive (?i)
        // \s* matches optional leading whitespace
        // \( matches opening parenthesis
        // (?i) makes the group case-insensitive
        // (?:...) is a non-capturing group for the keywords
        // .*? matches any character non-greedily
        // \) matches closing parenthesis
        val regex = Regex("""\s*\((?i)(?:from|feat\.?|ft\.?|with|live|remaster).*?\)""")
        
        return regex.replace(title, "").trim()
    }
    
    /**
     * Convert SpotifyTrack to SpotdownSong for compatibility.
     * 
     * @param track Spotify track from official API
     * @return SpotdownSong format
     */
    fun spotifyTrackToSong(track: SpotifyTrack): SpotdownSong {
        val durationMs = track.durationMs
        val durationSec = durationMs / 1000
        val minutes = durationSec / 60
        val seconds = durationSec % 60
        val durationStr = "%d:%02d".format(minutes, seconds)
        
        // Get highest quality thumbnail (first image is 640x640)
        val thumbnail = track.album.images.firstOrNull()?.url ?: ""
        
        // Get artist names
        val artistNames = track.artists.joinToString(", ") { it.name }
        
        return SpotdownSong(
            title = track.name,
            artist = artistNames,
            album = track.album.name,
            url = track.externalUrls.spotify ?: "",
            thumbnail = thumbnail,
            duration = durationStr,
            cached = false, // Will check separately if needed
            spotifyId = track.id,
            albumSpotifyId = track.album.id,
            artistSpotifyIds = track.artists.mapNotNull { it.id }
        )
    }
    
    /**
     * Convert SpotifySimplifiedTrack to SpotdownSong for album tracks.
     * 
     * @param track Simplified Spotify track from album/playlist endpoints
     * @param album Album information for thumbnail and URL context
     * @return SpotdownSong format
     */
    fun simplifiedTrackToSong(track: SpotifySimplifiedTrack, album: SpotifyAlbum): SpotdownSong {
        val durationMs = track.durationMs
        val durationSec = durationMs / 1000
        val minutes = durationSec / 60
        val seconds = durationSec % 60
        val durationStr = "%d:%02d".format(minutes, seconds)
        
        // Use album thumbnail
        val thumbnail = album.images.firstOrNull()?.url ?: ""
        
        // Get artist names
        val artistNames = track.artists.joinToString(", ") { it.name }
        
        return SpotdownSong(
            title = track.name,
            artist = artistNames,
            album = album.name,
            url = track.externalUrls.spotify ?: "",
            thumbnail = thumbnail,
            duration = durationStr,
            cached = false,
            spotifyId = track.id,
            albumSpotifyId = album.id,
            artistSpotifyIds = track.artists.mapNotNull { it.id }
        )
    }
}