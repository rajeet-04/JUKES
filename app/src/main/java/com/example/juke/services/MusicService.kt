package com.example.juke.services

import android.content.Context
import android.util.Log
import com.example.juke.database.MusicDatabase
import com.example.juke.database.toEntity
import com.example.juke.models.SpotdownSong
import com.example.juke.models.Track
import com.example.juke.network.RecommenderApi
import com.example.juke.network.SpotifyApi
import com.example.juke.network.ApiClient
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.delay
import java.io.File
import java.util.*

/**
 * Music Service for downloading and indexing tracks.
 */
class MusicService(private val context: Context) {
    
    private val TAG = "MusicService"
    private val database = MusicDatabase.getDatabase(context)
    private val trackDao = database.trackDao()
    
    private fun generateUUID(): String {
        return UUID.randomUUID().toString().replace("-", "")
    }
    
    private suspend fun <T> retryWithBackoff(
        maxRetries: Int = 5,
        operationName: String = "operation",
        block: suspend () -> T
    ): T {
        var lastError: Exception? = null
        
        for (attempt in 1..maxRetries) {
            try {
                return block()
            } catch (e: Exception) {
                lastError = e
                
                val isRetryable = e.message?.contains("500") == true ||
                                 e.message?.contains("network") == true ||
                                 e.message?.contains("timeout") == true
                
                if (attempt < maxRetries && isRetryable) {
                    val delayMs = minOf(1000L * (1 shl (attempt - 1)), 10000L)
                    Log.d(TAG, "[Retry $attempt/$maxRetries] $operationName failed, retrying in ${delayMs}ms...")
                    delay(delayMs)
                } else if (attempt >= maxRetries) {
                    Log.e(TAG, "[Retry] $operationName failed after $maxRetries attempts")
                    break
                } else {
                    throw e
                }
            }
        }
        
        throw lastError ?: Exception("Operation failed")
    }
    
    suspend fun smartDownloadAndIndex(
        song: SpotdownSong
    ): Track {
        // Check if track already exists in database
        val existingTrack = trackDao.findTrackByTitleArtist(song.title, song.artist)
        if (existingTrack != null && existingTrack.localUri != null) {
            Log.d(TAG, "Track already exists in database: ${song.title} by ${song.artist}")
            return Track(
                uuid = existingTrack.uuid,
                title = existingTrack.title,
                artist = existingTrack.artist,
                thumbnailUri = existingTrack.thumbnailUri,
                durationSec = existingTrack.durationSec,
                localUri = existingTrack.localUri,
                ytVideoId = existingTrack.ytVideoId,
                syncedLyrics = existingTrack.syncedLyrics,
                plainLyrics = existingTrack.plainLyrics,
                isFavourite = existingTrack.isFavourite,
                playCount = existingTrack.playCount,
                lastPlayedAt = existingTrack.lastPlayedAt
            )
        }
        
        val uuid = generateUUID()
        val durationSec = SpotifyApi.parseDuration(song.duration)
        val musicDir = File(context.filesDir, "music")
        if (!musicDir.exists()) musicDir.mkdirs()
        
        val audioFile = File(musicDir, "$uuid.mp3")
        
        try {
            Log.d(TAG, "Validating Spotify URL: ${song.url}")
            if (!song.url.startsWith("https://open.spotify.com/track/")) {
                throw Exception("Invalid Spotify URL format")
            }
            
            Log.d(TAG, "Checking if song is cached: ${song.title}")
            val cacheStatus = SpotifyApi.checkDirectDownload(song.url)
            val isCached = cacheStatus["cached"] ?: false
            
            Log.d(TAG, "Cache status: ${if (isCached) "CACHED" else "NOT CACHED"}")
            
            if (isCached) {
                Log.d(TAG, "Song is cached, downloading immediately: ${song.title}")
            } else {
                Log.d(TAG, "Song not cached, requesting download (may take 30-50 seconds): ${song.title}")
            }
            
            val audioData = SpotifyApi.downloadSong(song.url)
            Log.d(TAG, "Downloaded audio buffer, size: ${audioData.size} bytes")
            
            if (audioData.isEmpty()) {
                throw Exception("Downloaded file is empty")
            }
            
            if (audioData.size < 100_000) {
                throw Exception("Downloaded file is too small to be a valid MP3")
            }
            
            audioFile.writeBytes(audioData)
            Log.d(TAG, "Wrote file to: ${audioFile.absolutePath}")
            
            if (!audioFile.exists() || audioFile.length() == 0L) {
                throw Exception("Failed to write audio file")
            }
            
            val lyricsResult = SpotifyApi.searchLyrics(song.title, song.artist, song.album, durationSec)
            val ytVideoId = RecommenderApi.getBestVideoMatch("${song.title} ${song.artist}")
            
            var thumbnailUri: String? = null
            if (song.thumbnail.isNotBlank()) {
                try {
                    val thumbnailFile = File(musicDir, "${uuid}_thumb.jpg")
                    // Download thumbnail bytes with retry
                    val imageBytes: ByteArray = try {
                        retryWithBackoff(maxRetries = 3, operationName = "download thumbnail") {
                            ApiClient.httpClient.get(song.thumbnail).body()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Thumbnail download failed: ${e.message}", e)
                        ByteArray(0)
                    }

                    if (imageBytes.isNotEmpty()) {
                        thumbnailFile.writeBytes(imageBytes)
                        thumbnailUri = thumbnailFile.absolutePath
                        Log.d(TAG, "Thumbnail saved to: ${thumbnailFile.absolutePath}")
                    } else {
                        Log.d(TAG, "No thumbnail bytes downloaded for ${song.title}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving thumbnail: ${e.message}", e)
                }
            }
            
            val track = Track(
                uuid = uuid,
                title = song.title,
                artist = song.artist,
                thumbnailUri = thumbnailUri,
                durationSec = durationSec,
                localUri = audioFile.absolutePath,
                ytVideoId = ytVideoId,
                syncedLyrics = lyricsResult?.syncedLyrics,
                plainLyrics = lyricsResult?.plainLyrics,
                isFavourite = false,
                playCount = 0,
                lastPlayedAt = null
            )
            
            trackDao.insertTrack(track.toEntity())
            
            Log.d(TAG, "Successfully downloaded and indexed: ${track.title}")
            return track
            
        } catch (e: Exception) {
            if (audioFile.exists()) {
                audioFile.delete()
            }
            Log.e(TAG, "Error in smartDownloadAndIndex: ${e.message}", e)
            throw e
        }
    }


    suspend fun deleteTrackAndFiles(track: Track) {
        try {
            track.localUri?.let { uri ->
                File(uri).delete()
            }
            
            track.thumbnailUri?.let { uri ->
                File(uri).delete()
            }
            
            trackDao.deleteTrack(track.uuid)
            
            Log.d(TAG, "Deleted track: ${track.title}")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting track: ${e.message}", e)
        }
    }

}
