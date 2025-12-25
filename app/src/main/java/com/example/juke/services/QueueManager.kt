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
     * 
     * @param tracks Initial queue
     */
    fun initializeQueue(tracks: List<Track>) {
        _currentQueue.value = tracks.toMutableList()
        Log.d(TAG, "Queue initialized with ${tracks.size} tracks")
        
        // Check if we need to fetch recommendations
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
        currentList.add(track)
        _currentQueue.value = currentList
        Log.d(TAG, "Added to queue: ${track.title}")
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
    fun moveToNext(): Track? {
        val currentList = _currentQueue.value.toMutableList()
        if (currentList.isEmpty()) return null
        
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
                
                // Validate with Spotify and get top 5
                val validatedRecs = RecommenderApi.validateAndFilterWithSpotify(
                    recommendations,
                    maxResults = 5
                )
                
                if (validatedRecs.isEmpty()) {
                    Log.e(TAG, "No validated recommendations found")
                    return@launch
                }
                
                Log.d(TAG, "Got ${validatedRecs.size} validated recommendations")
                
                // Add to pending queue
                validatedRecs.forEach { rec ->
                    pendingRecommendations.offer(rec)
                    Log.d(TAG, "Queued for download: ${rec.title} by ${rec.artist}")
                }
                
                // Start downloading
                processNextDownload()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching recommendations: ${e.message}", e)
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
            
            // Check if already downloaded or downloading
            val existingTrack = trackDao.findTrackByTitleArtist(rec.title, rec.artist)
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
                    _downloadingTracks.value = _downloadingTracks.value + downloadInfo
                    
                    // Search Spotify again to get full track details
                    val searchResponse = SpotifyApi.search("${rec.title} ${rec.artist}", listOf("track"))
                    val spotifyResults = searchResponse.tracks?.items ?: emptyList()
                    if (spotifyResults.isEmpty()) {
                        Log.e(TAG, "No Spotify results for: ${rec.title}")
                        return@launch
                    }
                    
                    val spotifyTrack = spotifyResults.first()
                    val song = SpotifyApi.spotifyTrackToSong(spotifyTrack)
                    
                    // Download and index
                    val track = musicService.smartDownloadAndIndex(song)
                    
                    Log.d(TAG, "Successfully downloaded: ${track.title}")
                    
                    // Add to queue
                    addToQueue(track)
                    
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
        pendingRecommendations.clear()
        downloadJobs.values.forEach { it.cancel() }
        downloadJobs.clear()
        _downloadingTracks.value = emptyList()
        Log.d(TAG, "Queue cleared")
    }
    
    /**
     * Add download info for external tracking (e.g., playlist imports)
     */
    fun addDownloadTracking(title: String, artist: String, source: String = "manual") {
        val downloadInfo = DownloadInfo(title, artist, source)
        _downloadingTracks.value = _downloadingTracks.value + downloadInfo
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
        downloadJobs.values.forEach { it.cancel() }
        downloadJobs.clear()
        serviceScope.cancel()
        Log.d(TAG, "QueueManager cleaned up")
    }
}
