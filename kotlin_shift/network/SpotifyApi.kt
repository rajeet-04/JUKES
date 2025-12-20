package com.juke.network

import android.util.Log
import com.juke.models.LRCLibResult
import com.juke.models.SpotdownSearchResponse
import com.juke.models.SpotdownSong
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.pow

/**
 * Spotify/Spotdown API Service for song search and download.
 * 
 * This service handles:
 * 1. Searching for songs on Spotify
 * 2. Checking if songs are cached for faster download
 * 3. Downloading MP3 files from Spotify
 * 4. Fetching lyrics from LRCLib
 */
object SpotifyApi {
    
    private const val TAG = "SpotifyApi"
    private const val SPOTDOWN_BASE_URL = "https://spotdown.org/api"
    private const val LRCLIB_BASE_URL = "https://lrclib.net/api"
    
    /**
     * Search for songs on Spotify.
     * 
     * @param query Search query (song name, artist, or both)
     * @return Search response with list of matching songs
     * @throws Exception if search fails or returns invalid data
     */
    suspend fun searchSongs(query: String): SpotdownSearchResponse {
        Log.d(TAG, "Searching for songs with query: $query")
        
        return try {
            val response = ApiClient.httpClient.get("$SPOTDOWN_BASE_URL/song-details") {
                parameter("url", query)
            }
            
            val searchResponse: SpotdownSearchResponse = response.body()
            Log.d(TAG, "Search response received with ${searchResponse.songs.size} songs")
            
            // Validate songs have required fields
            val validSongs = searchResponse.songs.filter { song ->
                song.title.isNotEmpty() &&
                song.artist.isNotEmpty() &&
                song.url.isNotEmpty() &&
                song.thumbnail.isNotEmpty() &&
                song.duration.isNotEmpty() &&
                song.url.startsWith("https://open.spotify.com/track/")
            }
            
            Log.d(TAG, "Valid songs after filtering: ${validSongs.size}")
            
            searchResponse.copy(songs = validSongs)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error searching songs: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Check if a Spotify song is cached for faster download.
     * 
     * Cached songs download immediately, uncached take 30-50 seconds.
     * 
     * @param spotifyUrl Spotify track URL
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
     * Download an MP3 file from Spotify.
     * 
     * This method includes:
     * - Retry logic with exponential backoff (up to 3 retries)
     * - MP3 file validation (checks for ID3 tags or MP3 frame sync)
     * - 2-minute timeout
     * 
     * @param spotifyUrl Spotify track URL
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
     * Search for lyrics on LRCLib.
     * 
     * LRCLib provides both plain text and synced (LRC format) lyrics.
     * 
     * @param title Song title
     * @param artist Artist name
     * @param duration Optional duration in seconds for better matching
     * @return LRCLibResult or null if not found
     */
    suspend fun searchLyrics(
        title: String,
        artist: String,
        duration: Int? = null
    ): LRCLibResult? {
        return try {
            val query = "$title $artist"
            val response = ApiClient.httpClient.get("$LRCLIB_BASE_URL/search") {
                parameter("q", query)
            }
            
            val results: List<LRCLibResult> = response.body()
            
            if (results.isEmpty()) {
                return null
            }
            
            // If duration provided, find best match within ±5 seconds
            if (duration != null) {
                val bestMatch = results.find { result ->
                    kotlin.math.abs(result.duration - duration) < 5
                }
                if (bestMatch != null) {
                    return bestMatch
                }
            }
            
            // Otherwise return first result
            results.firstOrNull()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error searching lyrics: ${e.message}", e)
            null
        }
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
}
