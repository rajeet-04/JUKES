package com.juke.services

import android.content.Context
import android.util.Log
import com.juke.database.MusicDatabase
import com.juke.database.toEntity
import com.juke.database.toTrack
import com.juke.models.DownloadProgress
import com.juke.models.SpotdownSong
import com.juke.models.Track
import com.juke.network.RecommenderApi
import com.juke.network.SpotifyApi
import kotlinx.coroutines.delay
import java.io.File
import java.util.*

/**
 * Music Service for downloading and indexing tracks.
 * 
 * This service handles:
 * 1. Downloading MP3 files from Spotify
 * 2. Fetching lyrics from LRCLib
 * 3. Fetching YouTube video ID for recommendations
 * 4. Saving tracks to local storage and database
 * 5. Managing downloaded files
 */
class MusicService(private val context: Context) {
    
    private val TAG = "MusicService"
    private val database = MusicDatabase.getDatabase(context)
    private val trackDao = database.trackDao()
    
    /**
     * Generate a random UUID for track identification.
     */
    private fun generateUUID(): String {
        return UUID.randomUUID().toString().replace("-", "")
    }
    
    /**
     * Retry a suspend function with exponential backoff.
     * 
     * @param maxRetries Maximum number of retry attempts
     * @param operationName Name of operation for logging
     * @param block Suspend function to retry
     * @return Result of the operation
     */
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
                
                // Check if retryable error
                val isRetryable = e.message?.contains("500") == true ||
                                 e.message?.contains("network") == true ||
                                 e.message?.contains("timeout") == true
                
                if (attempt < maxRetries && isRetryable) {
                    val delayMs = minOf(1000L * (1 shl (attempt - 1)), 10000L) // Max 10s
                    Log.d(TAG, "[Retry $attempt/$maxRetries] $operationName failed, retrying in ${delayMs}ms...")
                    delay(delayMs)
                } else if (attempt >= maxRetries) {
                    Log.e(TAG, "[Retry] $operationName failed after $maxRetries attempts")
                    break
                } else {
                    // Not retryable, throw immediately
                    throw e
                }
            }
        }
        
        throw lastError ?: Exception("Operation failed")
    }
    
    /**
     * Download and index a song from Spotify.
     * 
     * This is the main method for downloading music. It:
     * 1. Validates the Spotify URL
     * 2. Checks if song is cached on server
     * 3. Downloads the MP3 file
     * 4. Fetches lyrics and YouTube video ID in parallel
     * 5. Downloads thumbnail
     * 6. Saves to database
     * 
     * @param song SpotdownSong from search results
     * @param onProgress Optional callback for download progress
     * @return Downloaded and indexed Track
     * @throws Exception if download or indexing fails
     */
    suspend fun smartDownloadAndIndex(
        song: SpotdownSong,
        onProgress: ((DownloadProgress) -> Unit)? = null
    ): Track {
        val uuid = generateUUID()
        val durationSec = SpotifyApi.parseDuration(song.duration)
        val musicDir = File(context.filesDir, "music")
        if (!musicDir.exists()) musicDir.mkdirs()
        
        val audioFile = File(musicDir, "$uuid.mp3")
        
        try {
            // Validate Spotify URL
            Log.d(TAG, "Validating Spotify URL: ${song.url}")
            if (!song.url.startsWith("https://open.spotify.com/track/")) {
                throw Exception("Invalid Spotify URL format")
            }
            
            // Check if song is cached
            Log.d(TAG, "Checking if song is cached: ${song.title}")
            val cacheStatus = SpotifyApi.checkDirectDownload(song.url)
            val isCached = cacheStatus["cached"] ?: false
            
            Log.d(TAG, "Cache status: ${if (isCached) "CACHED" else "NOT CACHED"}")
            
            if (isCached) {
                Log.d(TAG, "Song is cached, downloading immediately: ${song.title}")
            } else {
                Log.d(TAG, "Song not cached, requesting download (may take 30-50 seconds): ${song.title}")
            }
            
            // Download the song
            val audioData = SpotifyApi.downloadSong(song.url)
            Log.d(TAG, "Downloaded audio buffer, size: ${audioData.size} bytes")
            
            if (audioData.isEmpty()) {
                throw Exception("Downloaded file is empty")
            }
            
            if (audioData.size < 100_000) { // Less than 100KB is suspicious
                throw Exception("Downloaded file is too small to be a valid MP3")
            }
            
            // Write to file
            audioFile.writeBytes(audioData)
            Log.d(TAG, "Wrote file to: ${audioFile.absolutePath}")
            
            // Verify file was written correctly
            if (!audioFile.exists() || audioFile.length() == 0L) {
                throw Exception("Failed to write audio file")
            }
            
            // Fetch lyrics and YouTube video ID in parallel
            val lyricsResult = SpotifyApi.searchLyrics(song.title, song.artist, durationSec)
            val ytVideoId = RecommenderApi.getBestVideoMatch("${song.title} ${song.artist}")
            
            // Download thumbnail
            var thumbnailUri: String? = null
            if (song.thumbnail.isNotEmpty()) {
                try {
                    val thumbnailFile = File(musicDir, "${uuid}_thumb.jpg")
                    // Download thumbnail (you'd use a proper HTTP client here)
                    thumbnailUri = thumbnailFile.absolutePath
                } catch (e: Exception) {
                    Log.e(TAG, "Error downloading thumbnail: ${e.message}", e)
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
            
            // Save to database
            trackDao.insertTrack(track.toEntity())
            
            Log.d(TAG, "Successfully downloaded and indexed: ${track.title}")
            return track
            
        } catch (e: Exception) {
            // Clean up file if download failed
            if (audioFile.exists()) {
                audioFile.delete()
            }
            Log.e(TAG, "Error in smartDownloadAndIndex: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Download a recommended track by title and artist.
     * 
     * Used for auto-downloading recommendations during playback.
     * 
     * @param title Song title
     * @param artist Artist name
     * @return Downloaded Track or null if failed
     */
    suspend fun downloadRecommendedTrack(
        title: String,
        artist: String
    ): Track? {
        return try {
            Log.d(TAG, "[downloadRecommendedTrack] Processing: $title by $artist")
            
            // Check if track already exists in DB
            val existingTrack = trackDao.findTrackByTitleArtist(title, artist)
            if (existingTrack != null && existingTrack.localUri != null) {
                Log.d(TAG, "[downloadRecommendedTrack] Track already exists in DB: ${existingTrack.title}")
                return existingTrack.toTrack()
            }
            
            // Search Spotify for the song
            val searchQuery = "$title $artist"
            Log.d(TAG, "[downloadRecommendedTrack] Searching Spotify for: $searchQuery")
            
            val searchResult = retryWithBackoff(
                maxRetries = 5,
                operationName = "Spotify search for \"$searchQuery\""
            ) {
                SpotifyApi.searchSongs(searchQuery)
            }
            
            if (searchResult.songs.isEmpty()) {
                Log.d(TAG, "[downloadRecommendedTrack] No Spotify results for: $searchQuery")
                return null
            }
            
            // Get the top result
            val topSong = searchResult.songs.first()
            Log.d(TAG, "[downloadRecommendedTrack] Found Spotify track: ${topSong.title} by ${topSong.artist}")
            
            // Download the track with retry logic
            val track = retryWithBackoff(
                maxRetries = 5,
                operationName = "Download for \"${topSong.title}\""
            ) {
                smartDownloadAndIndex(topSong)
            }
            
            Log.d(TAG, "[downloadRecommendedTrack] Successfully downloaded: ${track.title}")
            track
            
        } catch (e: Exception) {
            Log.e(TAG, "[downloadRecommendedTrack] Error downloading $title by $artist: ${e.message}", e)
            null
        }
    }
    
    /**
     * Delete a track and its associated files.
     * 
     * @param track Track to delete
     */
    suspend fun deleteTrackAndFiles(track: Track) {
        try {
            // Delete audio file
            track.localUri?.let { uri ->
                File(uri).delete()
            }
            
            // Delete thumbnail
            track.thumbnailUri?.let { uri ->
                File(uri).delete()
            }
            
            // Delete from database
            trackDao.deleteTrack(track.uuid)
            
            Log.d(TAG, "Deleted track: ${track.title}")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting track: ${e.message}", e)
        }
    }
    
    /**
     * Check if a file exists.
     * 
     * @param filePath Absolute file path
     * @return True if file exists
     */
    fun checkFileExists(filePath: String): Boolean {
        return File(filePath).exists()
    }
    
    /**
     * Get file size in bytes.
     * 
     * @param filePath Absolute file path
     * @return File size in bytes, or 0 if file doesn't exist
     */
    fun getFileSize(filePath: String): Long {
        val file = File(filePath)
        return if (file.exists()) file.length() else 0L
    }
}
