package com.example.juke.network

import android.util.Base64
import android.util.Log
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
    private const val LRCLIB_BASE_URL = "https://lrclib.net/api"
    
    private var accessToken: String? = null
    private var tokenExpiryTime: Long = 0
    private val tokenMutex = Mutex()

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
     * Search Spotify for tracks, artists, and playlists.
     * 
     * @param query Search query
     * @param types Types to search (track, artist, playlist)
     * @param market Market code (default NP for Nepal)
     * @return SpotifySearchResponse with all requested types
     */
    suspend fun search(
        query: String,
        types: List<String> = listOf("track", "artist", "playlist"),
        market: String = "NP"
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
                      "${searchResponse.playlists?.items?.filterNotNull()?.size ?: 0} playlists")
            
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
        market: String = "NP",
        limit: Int = 50
    ): SpotifyAlbumsResponse {
        Log.d(TAG, "Fetching albums for artist: $artistId")
        
        try {
            val token = getAccessToken()
            
            val response: HttpResponse = ApiClient.httpClient.get(
                "$SPOTIFY_API_BASE_URL/artists/$artistId/albums"
            ) {
                header("Authorization", "Bearer $token")
                parameter("market", market)
                parameter("limit", limit)
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
        market: String = "NP"
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
        market: String = "NP",
        limit: Int = 50
    ): SpotifyAlbumTracksResponse {
        Log.d(TAG, "Fetching album tracks: $albumId")
        
        try {
            val token = getAccessToken()
            
            val response: HttpResponse = ApiClient.httpClient.get(
                "$SPOTIFY_API_BASE_URL/albums/$albumId/tracks"
            ) {
                header("Authorization", "Bearer $token")
                parameter("market", market)
                parameter("limit", limit)
            }
            
            return response.body()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching album tracks: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Get playlist tracks.
     * 
     * @param playlistId Spotify playlist ID
     * @param market Market code
     * @param limit Number of tracks to fetch
     */
    suspend fun getPlaylistTracks(
        playlistId: String,
        market: String = "NP",
        limit: Int = 50
    ): SpotifyPlaylistTracksResponse {
        Log.d(TAG, "Fetching playlist tracks: $playlistId")
        
        try {
            val token = getAccessToken()
            
            val response: HttpResponse = ApiClient.httpClient.get(
                "$SPOTIFY_API_BASE_URL/playlists/$playlistId/tracks"
            ) {
                header("Authorization", "Bearer $token")
                parameter("market", market)
                parameter("limit", limit)
            }
            
            return response.body()
            
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
                parameter("market", "NP")
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
     * @return Map with "cached" boolean key
     */
    suspend fun checkDirectDownload(spotifyUrl: String): Map<String, Boolean> {
        return try {
            val response = ApiClient.httpClient.get("$SPOTDOWN_BASE_URL/check-direct-download") {
                parameter("url", spotifyUrl)
            }
            response.body()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking direct download: ${e.message}", e)
            throw e
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
            val response = ApiClient.httpClient.get("$LRCLIB_BASE_URL/search") {
                parameter("track_name", title)
                parameter("artist_name", artist)
                if (album.isNotBlank()) {
                    parameter("album_name", album)
                }
            }
            
            val results: List<LRCLibResult> = response.body()
            
            if (results.isEmpty()) {
                return null
            }
            
            // Find the best matching result based on validation score
            val bestMatch = results
                .map { result -> result to validateLyricsMatch(result, title, artist, duration) }
                .filter { it.second > 0 } // Only consider results with some match
                .maxByOrNull { it.second }
                ?.first
            
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
            url = track.externalUrls.spotify,
            thumbnail = thumbnail,
            duration = durationStr,
            cached = false // Will check separately if needed
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
            url = track.externalUrls.spotify,
            thumbnail = thumbnail,
            duration = durationStr,
            cached = false
        )
    }
}
