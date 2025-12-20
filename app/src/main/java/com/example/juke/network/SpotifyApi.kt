package com.example.juke.network

import android.util.Log
import com.example.juke.models.LRCLibResult
import com.example.juke.models.SpotdownSearchResponse
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.delay

/**
 * Spotify/Spotdown API Service for song search and download.
 */
object SpotifyApi {
    
    private const val TAG = "SpotifyApi"
    private const val SPOTDOWN_BASE_URL = "https://spotdown.org/api"
    private const val LRCLIB_BASE_URL = "https://lrclib.net/api"
    
    suspend fun searchSongs(query: String): SpotdownSearchResponse {
        Log.d(TAG, "Searching for songs with query: $query")
        
        return try {
            val response = ApiClient.httpClient.get("$SPOTDOWN_BASE_URL/song-details") {
                parameter("url", query)
            }
            
            val searchResponse: SpotdownSearchResponse = response.body()
            Log.d(TAG, "Search response received with ${searchResponse.songs.size} songs")
            
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
    
    suspend fun downloadSong(
        spotifyUrl: String,
        retryAttempt: Int = 0
    ): ByteArray {
        val maxRetries = 3
        val retryDelays = listOf(2000L, 4000L, 8000L)
        
        return try {
            Log.d(TAG, "Making download request for URL: $spotifyUrl (attempt ${retryAttempt + 1})")
            
            val response = ApiClient.httpClient.post("$SPOTDOWN_BASE_URL/download") {
                contentType(ContentType.Application.Json)
                setBody(mapOf("url" to spotifyUrl))
            }
            
            val audioData: ByteArray = response.body()
            Log.d(TAG, "Download response data size: ${audioData.size} bytes")
            
            if (audioData.size < 3) {
                throw Exception("Downloaded file is too small")
            }
            
            val isID3 = audioData[0] == 0x49.toByte() && 
                       audioData[1] == 0x44.toByte() && 
                       audioData[2] == 0x33.toByte()
            
            val isMP3Frame = audioData[0] == 0xFF.toByte() && 
                            (audioData[1].toInt() and 0xE0) == 0xE0
            
            Log.d(TAG, "First 3 bytes: ${audioData.take(3).joinToString(" ") { "0x%02X".format(it) }}")
            Log.d(TAG, "Is ID3 tag: $isID3, Is MP3 frame: $isMP3Frame")
            
            if (!isID3 && !isMP3Frame) {
                val textResponse = audioData.take(500).toByteArray().decodeToString()
                Log.e(TAG, "Received non-MP3 response: $textResponse")
                throw Exception("Downloaded file is not a valid MP3")
            }
            
            audioData
            
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading song: ${e.message}", e)
            
            if (retryAttempt < maxRetries) {
                val delay = retryDelays[retryAttempt]
                Log.d(TAG, "[Download Retry] Error, retrying in ${delay}ms (attempt ${retryAttempt + 1}/$maxRetries)...")
                delay(delay)
                return downloadSong(spotifyUrl, retryAttempt + 1)
            }
            
            throw e
        }
    }
    
    suspend fun searchLyrics(
        title: String,
        artist: String,
        duration: Int? = null
    ): LRCLibResult? {
        return try {
            val query = "$title $artist"
            Log.d(TAG, "Searching lyrics for: $query, duration: $duration")
            
            val response = ApiClient.httpClient.get("$LRCLIB_BASE_URL/search") {
                parameter("q", query)
            }
            
            val results: List<LRCLibResult> = response.body()
            Log.d(TAG, "Found ${results.size} lyrics results")
            
            if (results.isEmpty()) {
                return null
            }
            
            // If duration is provided, find best match by duration (within 5 seconds)
            if (duration != null) {
                val durationMatches = results.filter { result ->
                    kotlin.math.abs(result.duration.toInt() - duration) < 5
                }
                
                // Prefer results with synced lyrics
                val bestMatch = durationMatches.firstOrNull { it.syncedLyrics != null }
                    ?: durationMatches.firstOrNull()
                
                if (bestMatch != null) {
                    Log.d(TAG, "Found duration match: ${bestMatch.trackName} by ${bestMatch.artistName} (${bestMatch.duration}s)")
                    return bestMatch
                }
            }
            
            // Fallback: prefer any result with synced lyrics
            val withSyncedLyrics = results.firstOrNull { it.syncedLyrics != null }
            if (withSyncedLyrics != null) {
                Log.d(TAG, "Using result with synced lyrics: ${withSyncedLyrics.trackName}")
                return withSyncedLyrics
            }
            
            // Last resort: return first result
            Log.d(TAG, "Using first result: ${results.first().trackName}")
            results.first()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error searching lyrics: ${e.message}", e)
            null
        }
    }
    
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
