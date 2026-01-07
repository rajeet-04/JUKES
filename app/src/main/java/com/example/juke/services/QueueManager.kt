package com.example.juke.services

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.example.juke.database.MusicDatabase
import com.example.juke.database.toTrack
import com.example.juke.models.Track
import com.example.juke.network.RecommenderApi
import com.example.juke.network.SpotifyApi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Download information for UI display
 */
data class DownloadInfo(
    val title: String,
    val artist: String,
    val source: String = "recommendation" // or "manual", "playlist"
)

/**
 * Queue Manager for smart music recommendations and downloads.
 * 
 * This service handles:
 * 1. Fetching recommendations from YouTube Music based on current song
 * 2. Validating recommendations with Spotify
 * 3. Smart queue management (keeps next 2 songs ready)
 * 4. Automatic downloads when queue is low (<=2 songs)
 * 5. Background processing and caching
 */
class QueueManager private constructor(private val context: Context) {
    
    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: QueueManager? = null
        
        fun getInstance(context: Context): QueueManager {
            return instance ?: synchronized(this) {
                instance ?: QueueManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    private val TAG = "QueueManager"
    private val database = MusicDatabase.getDatabase(context)
    private val trackDao = database.trackDao()
    private val musicService = MusicService(context)
    
    // Settings for user-defined recommendation count
    private val settingsPrefs = context.getSharedPreferences(
        "music_settings_prefs",
        Context.MODE_PRIVATE
    )
    
    // Coroutine scope for background tasks
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Queue state
    private val _currentQueue = MutableStateFlow<List<Track>>(emptyList())
    val currentQueue: StateFlow<List<Track>> = _currentQueue.asStateFlow()
    
    private val _downloadingTracks = MutableStateFlow<List<DownloadInfo>>(emptyList())
    val downloadingTracks: StateFlow<List<DownloadInfo>> = _downloadingTracks.asStateFlow()
    
    // Expose pending recommendations count for UI
    fun getPendingDownloadsCount(): Int = pendingRecommendations.size + downloadJobs.size
    
    // Pending recommendations to download
    private val pendingRecommendations = ConcurrentLinkedQueue<RecommenderApi.ValidatedRecommendation>()
    
    // Currently downloading jobs
    private val downloadJobs = mutableMapOf<String, Job>()
    
    // Track if we're currently fetching recommendations (to prevent multiple concurrent fetches)
    private var isRecommendationFetchInProgress = false
    
    // External downloads tracking (downloaded outside QueueManager, e.g. Instant Play)
    // Key: "Title-Artist" to prevent adding them as recommendations
    private val _externalDownloads = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    
    /**
     * Notify QueueManager that a download has started externally (e.g. from Instant Play).
     * This prevents QueueManager from fetching the same song as a recommendation.
     * 
     * @param track The track being downloaded
     */
    fun notifyDownloadStarted(track: Track) {
        val key = "${track.title.lowercase()}-${track.artist.lowercase()}"
        _externalDownloads.add(key)
        Log.d(TAG, "Notified of external download: ${track.title} (Key: $key)")
        
        // Also add to download tracking for UI
        addDownloadTracking(track.title, track.artist, "manual")
        
        // Auto-remove after 5 minutes to prevent permanent blocking in case of failure
        serviceScope.launch {
            kotlinx.coroutines.delay(5 * 60 * 1000L)
            _externalDownloads.remove(key)
        }
    }
    
    /**
     * Check if we need to fetch recommendations and start downloading if queue is low.
     * This should be called whenever playback starts or resumes.
     */
    fun checkAndFetchRecommendations() {
        val currentQueueSize = _currentQueue.value.size
        Log.d(TAG, "Checking recommendations: queue size = $currentQueueSize")
        
        if (currentQueueSize <= 2) {
            val currentTrack = _currentQueue.value.firstOrNull()
            currentTrack?.let { 
                Log.d(TAG, "Queue size <= 2, fetching recommendations for: ${it.title}")
                fetchAndQueueRecommendations(it) 
            }
        }
    }
    
    /**
     * Initialize the queue with a list of tracks.
     * This should be called when a user selects a new song from search or another screen.
     * 
     * IMPORTANT: This cancels all pending recommendation downloads from the previous song
     * to prevent queue pollution with old recommendations.
     * 
     * @param tracks Initial queue
     */
    fun initializeQueue(tracks: List<Track>) {
        // Cancel any pending downloads from the previous song
        // This prevents old recommendation downloads from being added to the queue
        cancelPendingRecommendationDownloads()
        
        _currentQueue.value = tracks.toMutableList()
        Log.d(TAG, "Queue initialized with ${tracks.size} tracks (cancelled previous recommendations)")
        
        // Check if we need to fetch recommendations for the new song
        if (tracks.size <= 2) {
            val currentTrack = tracks.firstOrNull()
            currentTrack?.let { fetchAndQueueRecommendations(it) }
        }
    }
    
    /**
     * Add a track to the end of the queue.
     * 
     * @param track Track to add
     */
    fun addToQueue(track: Track) {
        val currentList = _currentQueue.value.toMutableList()
        
        // Check if track already exists (Move operation)
        // We remove the old instance so the new one "moves" to the end
        val existingIndex = currentList.indexOfFirst { it.uuid == track.uuid }
        if (existingIndex != -1) {
            currentList.removeAt(existingIndex)
            Log.d(TAG, "Moved existing track to end of queue: ${track.title}")
        }
        
        currentList.add(track)
        _currentQueue.value = currentList
        Log.d(TAG, "Added to queue: ${track.title}")
    }

    /**
     * Insert a track at a specific index in the queue.
     *
     * @param index Index to insert at (0-based)
     * @param track Track to insert
     */
    fun insertQueueItem(index: Int, track: Track) {
        val currentList = _currentQueue.value.toMutableList()
        val safeIndex = index.coerceIn(0, currentList.size)
        currentList.add(safeIndex, track)
        _currentQueue.value = currentList
        Log.d(TAG, "Inserted track into queue at index $safeIndex: ${track.title}")

        // Ensure next 2 songs are downloaded if we modified near the top
        if (safeIndex <= 2) {
            ensureNext2Downloaded()
        }
    }
    
    /**
     * Remove a track from the queue.
     * 
     * @param trackId Track UUID to remove
     */
    fun removeFromQueue(trackId: String) {
        val currentList = _currentQueue.value.toMutableList()
        currentList.removeAll { it.uuid == trackId }
        _currentQueue.value = currentList
        Log.d(TAG, "Removed from queue: $trackId")
        
        // Check if we need more recommendations
        if (currentList.size <= 2) {
            val currentTrack = currentList.firstOrNull()
            currentTrack?.let { fetchAndQueueRecommendations(it) }
        }
    }
    
    /**
     * Move to the next track in queue.
     * 
     * @return Next track or null if queue is empty
     */
    // Recent artists tracking for recommendation variety
    private val recentArtists = java.util.LinkedList<String>()
    
    /**
     * Clear the entire queue.
     */

    
    /**
     * Move to the next track in queue.
     * 
     * @return Next track or null if queue is empty
     */
    fun moveToNext(): Track? {
        val currentList = _currentQueue.value.toMutableList()
        if (currentList.isEmpty()) return null
        
        // Get the track being removed (just played) and add to recent artists
        val playedTrack = currentList[0]
        addToRecentArtists(playedTrack.artist)
        
        // Remove first track
        currentList.removeAt(0)
        _currentQueue.value = currentList
        
        Log.d(TAG, "Moved to next track. Queue size: ${currentList.size}")
        
        // Check if we need to fetch more recommendations
        if (currentList.size <= 2) {
            val currentTrack = currentList.firstOrNull()
            currentTrack?.let { fetchAndQueueRecommendations(it) }
        }
        
        // Ensure next 2 songs are downloaded
        ensureNext2Downloaded()
        
        return currentList.firstOrNull()
    }
    
    private fun addToRecentArtists(artist: String) {
        // Handle multiple artists (split by comma, &, etc if needed, but simple addition is fine for now)
        // We want to track distinct artist names
        if (artist.isNotBlank()) {
            recentArtists.remove(artist) // Move to end if exists
            recentArtists.add(artist)
            if (recentArtists.size > 50) {
                recentArtists.removeFirst()
            }
            Log.d(TAG, "Updated recent artists. Count: ${recentArtists.size}. Latest: $artist")
        }
    }
    
    /**
     * Fetch recommendations based on current track and add to queue.
     * 
     * This is the main recommendation engine. It:
     * 1. Gets YouTube video ID for current song
     * 2. Fetches full radio queue from YouTube Music
     * 3. Validates recommendations with Spotify
     * 4. Adds top 5 validated tracks to download queue
     * 
     * @param currentTrack Track to base recommendations on
     */
    fun fetchAndQueueRecommendations(currentTrack: Track) {
        serviceScope.launch {
            // Prevent multiple concurrent recommendation fetches
            if (isRecommendationFetchInProgress) {
                Log.d(TAG, "Recommendation fetch already in progress, skipping")
                return@launch
            }
            
            isRecommendationFetchInProgress = true
            try {
                Log.d(TAG, "Fetching recommendations for: ${currentTrack.title} by ${currentTrack.artist}")
                
                // Get YouTube video ID
                val videoId = currentTrack.ytVideoId ?: run {
                    val query = "${currentTrack.title} ${currentTrack.artist}"
                    RecommenderApi.getBestVideoMatch(query)
                }
                
                if (videoId == null) {
                    Log.e(TAG, "Could not find YouTube video ID for: ${currentTrack.title}")
                    return@launch
                }
                
                Log.d(TAG, "Using YouTube video ID: $videoId")
                
                // Fetch full radio queue (index 1 to ~49)
                val recommendations = RecommenderApi.fetchFullRadioQueue(videoId)
                
                if (recommendations.isEmpty()) {
                    Log.e(TAG, "No recommendations found")
                    return@launch
                }
                
                Log.d(TAG, "Got ${recommendations.size} raw recommendations")
                
                // Construct artist context: Current track artist + Recent artists
                // This helps the validater prioritize tracks that match the user's recent listening history
                val contextArtists = (listOf(currentTrack.artist) + recentArtists).joinToString(", ")
                
                // Get user-defined recommendation count from settings (default: 5)
                val targetCount = settingsPrefs.getInt("recommendation_count", 5)
                Log.d(TAG, "Target recommendation count: $targetCount")
                
                // Validate with Spotify - fetch 2x the target to allow for filtering duplicates
                // This ensures we can still meet the target even after removing library duplicates
                val validatedRecs = RecommenderApi.validateAndFilterWithSpotify(
                    recommendations,
                    originalArtists = contextArtists,
                    maxResults = targetCount * 2
                )
                
                if (validatedRecs.isEmpty()) {
                    Log.e(TAG, "No validated recommendations found")
                    return@launch
                }
                
                Log.d(TAG, "Got ${validatedRecs.size} validated recommendations")
                
                // CRITICAL: Filter out tracks already in current queue BEFORE categorization
                // This prevents adding songs the user is already listening to
                val currentQueueTitles = _currentQueue.value.map { it.title.lowercase() to it.artist.lowercase() }
                
                // Also filter out the seed track (current playing track) explicitly
                val seedTrackPair = currentTrack.title.lowercase() to currentTrack.artist.lowercase()
                
                val filteredRecs = validatedRecs.filter { rec ->
                    val trackPair = rec.title.lowercase() to rec.artist.lowercase()
                    val key = "${rec.title.lowercase()}-${rec.artist.lowercase()}"
                    
                    val inQueue = currentQueueTitles.contains(trackPair)
                    val isSeed = trackPair == seedTrackPair
                    val isExternallyDownloading = _externalDownloads.contains(key)
                    
                    if (isExternallyDownloading) {
                        Log.d(TAG, "Filtered out external download: ${rec.title}")
                    }
                    
                    !inQueue && !isSeed && !isExternallyDownloading
                }
                
                Log.d(TAG, "After queue filtering: ${filteredRecs.size} recommendations (removed ${validatedRecs.size - filteredRecs.size} already in queue)")
                
                if (filteredRecs.isEmpty()) {
                    Log.e(TAG, "No new recommendations after filtering current queue")
                    return@launch
                }
                
                // Separate recommendations into "New" (not in library) and "Library" (already downloaded)
                val newRecs = mutableListOf<RecommenderApi.ValidatedRecommendation>()
                val libraryRecs = mutableListOf<RecommenderApi.ValidatedRecommendation>()
                
                for (rec in filteredRecs) {
                    // Check if track already exists in database
                    val candidates = trackDao.findTracksByTitleAndDuration(rec.title, rec.durationSec)
                    val existsInLibrary = candidates.any { 
                        com.example.juke.utils.ArtistUtils.areArtistsEqual(it.artist, rec.artist) && it.localUri != null
                    }
                    
                    if (existsInLibrary) {
                        libraryRecs.add(rec)
                    } else {
                        newRecs.add(rec)
                    }
                }
                
                Log.d(TAG, "Categorized: ${newRecs.size} new tracks, ${libraryRecs.size} library tracks")
                
                // Build final list: prioritize new tracks, fill remainder with library tracks
                val finalRecs = mutableListOf<RecommenderApi.ValidatedRecommendation>()
                finalRecs.addAll(newRecs.take(targetCount))
                
                // If we have fewer than targetCount, add library tracks to meet the target
                if (finalRecs.size < targetCount) {
                    val remaining = targetCount - finalRecs.size
                    finalRecs.addAll(libraryRecs.take(remaining))
                    Log.d(TAG, "Added ${libraryRecs.take(remaining).size} library tracks to meet target count")
                }
                
                Log.d(TAG, "Final selection: ${finalRecs.size} tracks (${newRecs.take(targetCount).size} new, ${finalRecs.size - newRecs.take(targetCount).size} library)")
                
                if (finalRecs.isEmpty()) {
                    Log.e(TAG, "No recommendations to queue")
                    return@launch
                }
                
                // Add to pending queue
                finalRecs.forEach { rec ->
                    pendingRecommendations.offer(rec)
                    Log.d(TAG, "Queued for download: ${rec.title} by ${rec.artist}")
                }
                
                // Start downloading
                processNextDownload()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching recommendations: ${e.message}", e)
            } finally {
                isRecommendationFetchInProgress = false
            }
        }
    }
    
    /**
     * Process the next download from pending recommendations.
     * 
     * This method ensures that only a limited number of downloads
     * happen simultaneously to avoid overwhelming the system.
     */
    private fun processNextDownload() {
        serviceScope.launch {
            // Limit concurrent downloads to 2
            if (downloadJobs.size >= 2) {
                Log.d(TAG, "Already downloading 2 tracks, waiting...")
                return@launch
            }
            
            val rec = pendingRecommendations.poll() ?: return@launch
            
            // CHECK 1: Check if already in current queue (Prevent Duplicates)
            // This prevents adding the same song multiple times to the queue
            if (_currentQueue.value.any { it.title.equals(rec.title, ignoreCase = true) && it.artist.equals(rec.artist, ignoreCase = true) }) {
                Log.d(TAG, "Skipping duplicate recommendation (already in queue): ${rec.title}")
                processNextDownload()
                return@launch
            }
            
            // CHECK 2: Check if already downloaded (Database check)
            val candidates = trackDao.findTracksByTitleAndDuration(rec.title, rec.durationSec)
            val existingTrack = candidates.find { 
                com.example.juke.utils.ArtistUtils.areArtistsEqual(it.artist, rec.artist) 
            }
            if (existingTrack != null && existingTrack.localUri != null) {
                Log.d(TAG, "Track already exists: ${rec.title}")
                
                // Add to queue
                addToQueue(existingTrack.toTrack())
                
                // Process next
                processNextDownload()
                return@launch
            }
            
            if (_downloadingTracks.value.any { it.title == rec.title && it.artist == rec.artist }) {
                Log.d(TAG, "Track already downloading: ${rec.title}")
                processNextDownload()
                return@launch
            }
            
            // Start download
            val downloadJob = launch {
                try {
                    Log.d(TAG, "Starting download: ${rec.title} by ${rec.artist}")
                    
                    // Mark as downloading
                    val downloadInfo = DownloadInfo(rec.title, rec.artist, "recommendation")
                    _downloadingTracks.value += downloadInfo
                    
                    // Use the pre-validated Spotify URL from recommendation validation
                    // This ensures we download the exact song that was matched during validation
                    val trackId = rec.spotifyUrl.substringAfterLast("/").substringBefore("?")
                    val spotifyTrack = SpotifyApi.getTrack(trackId)
                    val song = SpotifyApi.spotifyTrackToSong(spotifyTrack)
                    
                    // Download and index
                    val track = musicService.smartDownloadAndIndex(song)
                    
                    Log.d(TAG, "Successfully downloaded: ${track.title}")
                    
                    // Add to queue
                    addToQueue(track)
                    
                    // Add to recent artists immediately upon adding to queue? 
                    // No, likely better to wait until played, or maybe now is fine.
                    // Following user instruction: "as you add them" -> but usually variety is about history.
                    // Let's stick to adding on playback completion (moveToNext) for history tracking.
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error downloading ${rec.title}: ${e.message}", e)
                } finally {
                    // Remove from downloading
                    _downloadingTracks.value = _downloadingTracks.value.filterNot { 
                        it.title == rec.title && it.artist == rec.artist 
                    }
                    downloadJobs.remove(rec.title)
                    
                    // Process next download
                    processNextDownload()
                }
            }
            
            downloadJobs[rec.title] = downloadJob
        }
    }
    /**
     * Ensure the next 2 songs in queue are downloaded.
     * 
     * This pre-downloads upcoming tracks to ensure smooth playback.
     */
    private fun ensureNext2Downloaded() {
        serviceScope.launch {
            val queue = _currentQueue.value
            
            // Get next 2 tracks
            val next2 = queue.take(2)
            
            next2.forEach { track ->
                if (track.localUri == null) {
                    Log.d(TAG, "Track not downloaded: ${track.title}, triggering download")
                    
                    // This track needs to be downloaded
                    // In practice, this should rarely happen because we pre-fetch
                    // But this is a safety mechanism
                    
                    // Try to download it
                    try {
                        val query = "${track.title} ${track.artist}"
                        val searchResponse = SpotifyApi.search(query, listOf("track"))
                        val spotifyResults = searchResponse.tracks?.items ?: emptyList()
                        
                        if (spotifyResults.isNotEmpty()) {
                            val spotifyTrack = spotifyResults.first()
                            val song = SpotifyApi.spotifyTrackToSong(spotifyTrack)
                            
                            musicService.smartDownloadAndIndex(song)
                            Log.d(TAG, "Emergency downloaded: ${track.title}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error emergency downloading ${track.title}: ${e.message}", e)
                    }
                } else {
                    // Verify file exists
                    val file = File(track.localUri)
                    if (!file.exists()) {
                        Log.e(TAG, "File missing for ${track.title}: ${track.localUri}")
                    }
                }
            }
        }
    }
    
    /**
     * Get the current queue size.
     * 
     * @return Number of tracks in queue
     */
    fun getQueueSize(): Int {
        return _currentQueue.value.size
    }
    
    /**
     * Get the next track without removing it.
     * 
     * @return Next track or null
     */
    fun peekNext(): Track? {
        return _currentQueue.value.firstOrNull()
    }
    
    /**
     * Clear the entire queue.
     */
    fun clearQueue() {
        _currentQueue.value = emptyList()
        cancelPendingRecommendationDownloads()
        Log.d(TAG, "Queue cleared")
    }
    
    /**
     * Cancel all pending recommendation downloads.
     * 
     * This is called when:
     * 1. User selects a new song from search/another screen (initializeQueue)
     * 2. Queue is explicitly cleared
     * 3. App is shutting down (cleanup)
     * 
     * This prevents old recommendations from being mixed with new ones.
     */
    private fun cancelPendingRecommendationDownloads() {
        // Clear pending queue
        val pendingCount = pendingRecommendations.size
        pendingRecommendations.clear()
        
        // Cancel all in-progress downloads
        downloadJobs.values.forEach { job ->
            if (!job.isCompleted) {
                job.cancel()
            }
        }
        downloadJobs.clear()
        
        // Clear downloading tracks display
        _downloadingTracks.value = emptyList()
        
        if (pendingCount > 0 || downloadJobs.isNotEmpty()) {
            Log.d(TAG, "Cancelled $pendingCount pending recommendations and ${downloadJobs.size} active downloads")
        }
    }
    
    /**
     * Add download info for external tracking (e.g., playlist imports)
     */
    fun addDownloadTracking(title: String, artist: String, source: String = "manual") {
        val downloadInfo = DownloadInfo(title, artist, source)
        _downloadingTracks.value += downloadInfo
    }
    
    /**
     * Remove download tracking
     */
    fun removeDownloadTracking(title: String, artist: String) {
        _downloadingTracks.value = _downloadingTracks.value.filterNot { 
            it.title == title && it.artist == artist 
        }
    }
    
    /**
     * Get current queue as a list.
     * 
     * @return Copy of current queue
     */
    fun getCurrentQueueList(): List<Track> {
        return _currentQueue.value.toList()
    }
    
    /**
     * Manually trigger recommendation fetch for a specific track.
     * 
     * @param track Track to base recommendations on
     */
    fun manuallyFetchRecommendations(track: Track) {
        fetchAndQueueRecommendations(track)
    }
    
    /**
     * Cancel all downloads and cleanup.
     */
    fun cleanup() {
        cancelPendingRecommendationDownloads()
        serviceScope.cancel()
        instance = null
        Log.d(TAG, "QueueManager cleaned up and instance reset")
    }
}
