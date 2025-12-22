package com.example.juke.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.juke.database.MusicDatabase
import com.example.juke.models.Track
import com.example.juke.services.QueueManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Example PlayerViewModel demonstrating smart recommendation queue integration.
 * 
 * This ViewModel shows how to:
 * 1. Initialize the QueueManager
 * 2. Observe queue and download states
 * 3. Trigger recommendations automatically
 * 4. Handle playback transitions
 */
class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    
    private val TAG = "PlayerViewModel"
    private val database = MusicDatabase.getDatabase(application)
    private val queueManager = QueueManager(application)
    
    // UI State
    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()
    
    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queue: StateFlow<List<Track>> = _queue.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _downloadingTracks = MutableStateFlow<Set<String>>(emptySet())
    val downloadingTracks: StateFlow<Set<String>> = _downloadingTracks.asStateFlow()
    
    init {
        // Observe queue changes
        viewModelScope.launch {
            queueManager.currentQueue.collect { queueList ->
                _queue.value = queueList
                Log.d(TAG, "Queue updated: ${queueList.size} tracks")
                
                // Update UI with track titles
                queueList.forEachIndexed { index, track ->
                    Log.d(TAG, "  [$index] ${track.title} by ${track.artist}")
                }
            }
        }
        
        // Observe downloading tracks
        viewModelScope.launch {
            queueManager.downloadingTracks.collect { downloading ->
                _downloadingTracks.value = downloading
                Log.d(TAG, "Currently downloading: ${downloading.size} tracks")
            }
        }
    }
    
    /**
     * Start playing a track with automatic recommendations.
     * 
     * This will:
     * 1. Initialize the queue with the track
     * 2. Automatically fetch recommendations (queue size is 1, which is ≤2)
     * 3. Download top 10 validated songs in background
     * 4. Keep next 2 songs ready for playback
     * 
     * @param track Track to start playing
     */
    fun playWithRecommendations(track: Track) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                _currentTrack.value = track
                
                Log.d(TAG, "Starting playback with recommendations: ${track.title}")
                
                // Initialize queue - this will automatically trigger recommendations
                // because the queue size (1) is <= 2
                queueManager.initializeQueue(listOf(track))
                
                Log.d(TAG, "Queue initialized. Recommendations will be fetched automatically.")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error starting playback: ${e.message}", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Skip to the next track in queue.
     * 
     * This will:
     * 1. Move to the next track
     * 2. Automatically fetch more recommendations if queue drops to ≤2
     * 3. Pre-download next 2 songs
     */
    fun skipToNext() {
        viewModelScope.launch {
            try {
                val nextTrack = queueManager.moveToNext()
                
                if (nextTrack != null) {
                    _currentTrack.value = nextTrack
                    Log.d(TAG, "Skipped to: ${nextTrack.title}")
                    
                    // Queue manager automatically handles:
                    // - Fetching more recommendations if needed
                    // - Ensuring next 2 songs are downloaded
                } else {
                    Log.d(TAG, "No more tracks in queue")
                    _currentTrack.value = null
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error skipping to next: ${e.message}", e)
            }
        }
    }
    
    /**
     * Add a track to the end of the queue.
     * 
     * @param track Track to add
     */
    fun addToQueue(track: Track) {
        queueManager.addToQueue(track)
        Log.d(TAG, "Added to queue: ${track.title}")
    }
    
    /**
     * Remove a track from the queue.
     * 
     * @param trackId Track UUID to remove
     */
    fun removeFromQueue(trackId: String) {
        queueManager.removeFromQueue(trackId)
        Log.d(TAG, "Removed from queue: $trackId")
    }
    
    /**
     * Manually fetch more recommendations for the current track.
     * 
     * Useful if you want to refresh recommendations or get more options.
     */
    fun fetchMoreRecommendations() {
        viewModelScope.launch {
            try {
                val track = _currentTrack.value ?: return@launch
                
                _isLoading.value = true
                Log.d(TAG, "Manually fetching recommendations for: ${track.title}")
                
                queueManager.manuallyFetchRecommendations(track)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching recommendations: ${e.message}", e)
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Clear the entire queue.
     */
    fun clearQueue() {
        queueManager.clearQueue()
        Log.d(TAG, "Queue cleared")
    }
    
    /**
     * Get the queue size.
     * 
     * @return Number of tracks in queue
     */
    fun getQueueSize(): Int {
        return queueManager.getQueueSize()
    }
    
    /**
     * Check if a track is currently downloading.
     * 
     * @param title Track title
     * @return True if downloading
     */
    fun isTrackDownloading(title: String): Boolean {
        return _downloadingTracks.value.contains(title)
    }
    
    override fun onCleared() {
        super.onCleared()
        queueManager.cleanup()
        Log.d(TAG, "ViewModel cleared")
    }
}

/**
 * USAGE EXAMPLE IN COMPOSE UI:
 * 
 * @Composable
 * fun PlayerScreen(viewModel: PlayerViewModel = viewModel()) {
 *     val currentTrack by viewModel.currentTrack.collectAsState()
 *     val queue by viewModel.queue.collectAsState()
 *     val isLoading by viewModel.isLoading.collectAsState()
 *     val downloadingTracks by viewModel.downloadingTracks.collectAsState()
 *     
 *     Column {
 *         // Current Track
 *         currentTrack?.let { track ->
 *             Text("Now Playing: ${track.title}")
 *             Text("Artist: ${track.artist}")
 *             
 *             Button(onClick = { viewModel.skipToNext() }) {
 *                 Text("Next")
 *             }
 *         }
 *         
 *         // Queue
 *         Text("Queue (${queue.size} tracks)")
 *         LazyColumn {
 *             items(queue) { track ->
 *                 Row {
 *                     Text(track.title)
 *                     
 *                     // Show downloading indicator
 *                     if (downloadingTracks.contains(track.title)) {
 *                         CircularProgressIndicator(modifier = Modifier.size(20.dp))
 *                     }
 *                     
 *                     IconButton(onClick = { viewModel.removeFromQueue(track.uuid) }) {
 *                         Icon(Icons.Default.Close, "Remove")
 *                     }
 *                 }
 *             }
 *         }
 *         
 *         // Loading indicator
 *         if (isLoading) {
 *             CircularProgressIndicator()
 *         }
 *         
 *         // Refresh recommendations button
 *         Button(onClick = { viewModel.fetchMoreRecommendations() }) {
 *             Text("Get More Recommendations")
 *         }
 *     }
 * }
 */
