package com.example.juke.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.juke.database.MusicDatabase
import com.example.juke.database.toTrack
import com.example.juke.models.SpotdownSong
import com.example.juke.models.SpotifyTrack
import com.example.juke.models.Track
import com.example.juke.models.SpotifyAlbum
import com.example.juke.models.SpotifySimplifiedTrack
import com.example.juke.network.SpotifyApi
import com.example.juke.services.MusicService
import com.example.juke.services.PlaybackManager
import com.example.juke.services.QueueManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    COMPLETED,
    FAILED
}

data class DownloadItem(
    val id: String = UUID.randomUUID().toString(),
    val song: SpotdownSong,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val error: String? = null,
    val shouldPlayAfterDownload: Boolean = false
)

data class MusicUiState(
    val currentTrack: Track? = null,
    val queue: List<Track> = emptyList(),
    val queueIndex: Int = -1,
    val isPlaying: Boolean = false,
    val position: Long = 0,
    val duration: Long = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
    val downloadQueue: List<DownloadItem> = emptyList(),
    val currentDownload: DownloadItem? = null,
    val isQueueOperationInProgress: Boolean = false
)

class MusicViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = MusicDatabase.getDatabase(application)
    private val trackDao = database.trackDao()
    private val musicService = MusicService(application)
    val playbackManager = PlaybackManager(application)
    private val queueManager = QueueManager(application)
    
    private val _uiState = MutableStateFlow(MusicUiState())
    val uiState: StateFlow<MusicUiState> = _uiState.asStateFlow()
    
    private var isProcessingQueue = false
    
    init {
        playbackManager.initialize()
        // Observe playback state changes coming from the MediaController (notifications/external)
        viewModelScope.launch {
            playbackManager.isPlayingFlow.collect { playing ->
                _uiState.update { it.copy(isPlaying = playing) }
            }
        }
        
        // Observe current track changes from PlaybackManager
        viewModelScope.launch {
            playbackManager.currentTrackIdFlow.collect { trackId ->
                trackId?.let { id ->
                    Log.d("MusicViewModel", "Current track changed to: $id")
                    
                    // Find the track in the current queue
                    val currentQueue = _uiState.value.queue
                    val trackIndex = currentQueue.indexOfFirst { it.uuid == id }
                    
                    if (trackIndex >= 0) {
                        val track = currentQueue[trackIndex]
                        _uiState.update { 
                            it.copy(
                                currentTrack = track,
                                queueIndex = trackIndex
                            )
                        }
                        Log.d("MusicViewModel", "Updated UI state - Current track: ${track.title}, Index: $trackIndex")
                        
                        // Check if we need more recommendations (queue getting low)
                        val remainingTracks = currentQueue.size - trackIndex - 1
                        if (remainingTracks <= 2) {
                            Log.d("MusicViewModel", "Queue low, fetching recommendations for: ${track.title}")
                            queueManager.fetchAndQueueRecommendations(track)
                        }
                    } else {
                        Log.w("MusicViewModel", "Track with ID $id not found in current queue")
                    }
                }
            }
        }
        
        // Observe recommendation queue changes
        viewModelScope.launch {
            queueManager.currentQueue.collect { recommendedTracks ->
                Log.d("MusicViewModel", "QueueManager queue updated: ${recommendedTracks.size} tracks")
                
                // Add recommended tracks to the main queue if they're not already there
                val currentQueue = _uiState.value.queue
                val newTracks = recommendedTracks.filter { recommended ->
                    !currentQueue.any { existing -> existing.uuid == recommended.uuid }
                }
                
                if (newTracks.isNotEmpty()) {
                    val updatedQueue = currentQueue + newTracks
                    _uiState.update { it.copy(queue = updatedQueue) }
                    
                    // Add new tracks to the playback queue without interrupting current playback
                    playbackManager.addToQueue(newTracks)
                    
                    Log.d("MusicViewModel", "Added ${newTracks.size} recommended tracks to main queue. Total queue size: ${updatedQueue.size}")
                } else {
                    Log.d("MusicViewModel", "No new tracks to add from recommendations")
                }
            }
        }
        
        // Observe downloading tracks from recommendations
        viewModelScope.launch {
            queueManager.downloadingTracks.collect { downloading ->
                Log.d("MusicViewModel", "Recommendation downloads in progress: ${downloading.size} tracks")
            }
        }
    }
    
    fun playTrack(track: Track) {
        viewModelScope.launch {
            // Set up the queue with the current track
            _uiState.update { 
                it.copy(
                    currentTrack = track,
                    queue = listOf(track), // Initialize queue with current track
                    queueIndex = 0, // Current track is at index 0
                    isPlaying = true,
                    duration = track.durationSec.toLong() * 1000
                )
            }
            
            // Set the queue in the playback manager (starts playing automatically)
            playbackManager.setQueue(listOf(track), 0)
            
            // Initialize recommendation queue for this track
            Log.d("MusicViewModel", "Playing track: ${track.title}, initializing recommendations")
            queueManager.initializeQueue(listOf(track))
        }
    }
    
    fun setQueue(tracks: List<Track>, startIndex: Int = 0) {
        viewModelScope.launch {
            playbackManager.setQueue(tracks, startIndex)
            _uiState.update { 
                it.copy(
                    queue = tracks,
                    queueIndex = startIndex,
                    currentTrack = tracks.getOrNull(startIndex),
                    isPlaying = true
                )
            }
        }
    }
    
    fun togglePlayPause() {
        playbackManager.togglePlayPause()
        // rely on playbackManager.isPlayingFlow to update UI via collector
    }

    /**
     * Insert a track so it plays immediately after the current track.
     */
    fun addNext(track: Track) {
        viewModelScope.launch {
            _uiState.update { it.copy(isQueueOperationInProgress = true) }

            try {
                val currentState = _uiState.value
                val currentQueue = currentState.queue.toMutableList()

                if (currentQueue.isEmpty() || currentState.queueIndex < 0) {
                    // Nothing playing yet; start a queue with this track
                    setQueue(listOf(track), 0)
                } else {
                    val insertIndex = (currentState.queueIndex + 1)
                        .coerceAtMost(currentQueue.size)
                    currentQueue.add(insertIndex, track)

                    val inserted = playbackManager.addToQueueAt(track, insertIndex)
                    if (!inserted) {
                        // Fallback: reset full queue to keep UI and player in sync
                        playbackManager.setQueue(currentQueue, currentState.queueIndex)
                    }

                    _uiState.update {
                        it.copy(
                            queue = currentQueue
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Failed to add next: ${e.message}", e)
            } finally {
                _uiState.update { it.copy(isQueueOperationInProgress = false) }
            }
        }
    }

    /**
     * Download a Spotify track (if needed) and queue it to play next.
     */
    fun queueSpotifyTrackNext(spotifyTrack: SpotifyTrack) {
        viewModelScope.launch {
            _uiState.update { it.copy(isQueueOperationInProgress = true) }

            try {
                val spotdownSong = SpotifyApi.spotifyTrackToSong(spotifyTrack)
                val track = withContext(Dispatchers.IO) {
                    musicService.smartDownloadAndIndex(spotdownSong)
                }
                addNext(track)
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Failed to queue Spotify track next: ${e.message}", e)
                _uiState.update { it.copy(isQueueOperationInProgress = false) }
            } finally {
                _uiState.update { it.copy(isQueueOperationInProgress = false) }
            }
        }
    }

    /**
     * Download a simplified Spotify track (album context) and queue it to play next.
     */
    fun queueSimplifiedTrackNext(track: SpotifySimplifiedTrack, album: SpotifyAlbum) {
        viewModelScope.launch {
            _uiState.update { it.copy(isQueueOperationInProgress = true) }

            try {
                val spotdownSong = SpotifyApi.simplifiedTrackToSong(track, album)
                val downloaded = withContext(Dispatchers.IO) {
                    musicService.smartDownloadAndIndex(spotdownSong)
                }
                addNext(downloaded)
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Failed to queue simplified track next: ${e.message}", e)
            } finally {
                _uiState.update { it.copy(isQueueOperationInProgress = false) }
            }
        }
    }
    
    fun skipToNext() {
        playbackManager.skipToNext()
        // UI state will be updated automatically via currentTrackIdFlow
    }
    
    fun skipToPrevious() {
        playbackManager.skipToPrevious()
        // UI state will be updated automatically via currentTrackIdFlow
    }
    
    fun seekTo(positionMs: Long) {
        playbackManager.seekTo(positionMs)
        _uiState.update { it.copy(position = positionMs) }
    }
    
    fun updateProgress() {
        val currentPos = playbackManager.getCurrentPosition()
        val durationMs = playbackManager.getDuration()
        _uiState.update { state ->
            state.copy(
                position = currentPos,
                duration = if (durationMs > 0) durationMs else state.duration
            )
        }
    }
    
    suspend fun downloadAndPlay(song: SpotdownSong) {
        // Check if already exists
        val existingTrack = trackDao.findTrackByTitleArtist(song.title, song.artist)
        
        if (existingTrack != null && existingTrack.localUri != null) {
            // Already downloaded, play immediately
            playTrack(existingTrack.toTrack())
        } else {
            // Add to queue with play flag
            addToDownloadQueue(song, shouldPlayAfterDownload = true)
        }
    }
    
    fun addToDownloadQueue(song: SpotdownSong, shouldPlayAfterDownload: Boolean = false) {
        viewModelScope.launch {
            // Check if already in queue or downloading
            val currentState = _uiState.value
            val alreadyQueued = currentState.downloadQueue.any { 
                it.song.title == song.title && it.song.artist == song.artist 
            }
            val currentlyDownloading = currentState.currentDownload?.let {
                it.song.title == song.title && it.song.artist == song.artist
            } ?: false
            
            if (alreadyQueued || currentlyDownloading) {
                Log.d("MusicViewModel", "Song already in queue or downloading: ${song.title}")
                return@launch
            }
            
            // Check if already exists in database
            val existingTrack = trackDao.findTrackByTitleArtist(song.title, song.artist)
            if (existingTrack != null && existingTrack.localUri != null) {
                Log.d("MusicViewModel", "Song already downloaded: ${song.title}")
                if (shouldPlayAfterDownload) {
                    playTrack(existingTrack.toTrack())
                }
                return@launch
            }
            
            val downloadItem = DownloadItem(
                song = song,
                status = DownloadStatus.QUEUED,
                shouldPlayAfterDownload = shouldPlayAfterDownload
            )
            
            _uiState.update { state ->
                state.copy(
                    downloadQueue = state.downloadQueue + downloadItem
                )
            }
            
            Log.d("MusicViewModel", "Added to queue: ${song.title} (Queue size: ${_uiState.value.downloadQueue.size})")
            
            processDownloadQueue()
        }
    }
    
    private fun processDownloadQueue() {
        if (isProcessingQueue) {
            Log.d("MusicViewModel", "Already processing queue")
            return
        }
        
        viewModelScope.launch {
            isProcessingQueue = true
            
            while (_uiState.value.downloadQueue.isNotEmpty()) {
                val nextItem = _uiState.value.downloadQueue.first()
                
                // Move from queue to current download
                _uiState.update { state ->
                    state.copy(
                        downloadQueue = state.downloadQueue.drop(1),
                        currentDownload = nextItem.copy(status = DownloadStatus.DOWNLOADING)
                    )
                }
                
                Log.d("MusicViewModel", "Starting download: ${nextItem.song.title}")
                
                try {
                    val track = musicService.smartDownloadAndIndex(nextItem.song)
                    
                    // Download successful
                    _uiState.update { state ->
                        state.copy(
                            currentDownload = nextItem.copy(status = DownloadStatus.COMPLETED)
                        )
                    }
                    
                    Log.d("MusicViewModel", "Download completed: ${nextItem.song.title}")
                    
                    // Play if requested
                    if (nextItem.shouldPlayAfterDownload) {
                        playTrack(track)
                    }
                    
                    // Clear current download after a brief delay
                    kotlinx.coroutines.delay(1000)
                    _uiState.update { state ->
                        state.copy(currentDownload = null)
                    }
                    
                } catch (e: Exception) {
                    Log.e("MusicViewModel", "Download failed: ${nextItem.song.title} - ${e.message}")
                    
                    // Mark as failed
                    _uiState.update { state ->
                        state.copy(
                            currentDownload = nextItem.copy(
                                status = DownloadStatus.FAILED,
                                error = e.message
                            )
                        )
                    }
                    
                    // Clear failed download after delay
                    kotlinx.coroutines.delay(3000)
                    _uiState.update { state ->
                        state.copy(currentDownload = null)
                    }
                }
            }
            
            isProcessingQueue = false
            Log.d("MusicViewModel", "Queue processing completed")
        }
    }
    
    fun cancelDownload(downloadId: String) {
        _uiState.update { state ->
            state.copy(
                downloadQueue = state.downloadQueue.filter { it.id != downloadId }
            )
        }
    }
    
    fun retryFailedDownload(downloadItem: DownloadItem) {
        viewModelScope.launch {
            // Reset the download item status and add back to queue
            val resetItem = downloadItem.copy(
                status = DownloadStatus.QUEUED,
                error = null
            )
            
            _uiState.update { state ->
                state.copy(
                    downloadQueue = listOf(resetItem) + state.downloadQueue
                )
            }
            
            Log.d("MusicViewModel", "Retrying failed download: ${downloadItem.song.title}")
            
            // Process the queue to start the retry
            processDownloadQueue()
        }
    }
    
    fun removeFromQueue(trackId: String) {
        viewModelScope.launch {
            // Set loading state
            _uiState.update { it.copy(isQueueOperationInProgress = true) }
            
            val currentState = _uiState.value
            val currentQueue = currentState.queue
            val currentIndex = currentState.queueIndex
            
            // Find the track to remove
            val trackIndex = currentQueue.indexOfFirst { it.uuid == trackId }
            if (trackIndex == -1) {
                _uiState.update { it.copy(isQueueOperationInProgress = false) }
                return@launch
            }
            
            // Remove from playback queue first
            val playbackResult = playbackManager.removeFromQueue(trackId)
            
            if (playbackResult) {
                // Keep recommendation queue in sync so deleted tracks don't reappear
                queueManager.removeFromQueue(trackId)

                // Remove from UI queue
                val newQueue = currentQueue.toMutableList().apply { removeAt(trackIndex) }
                
                // Calculate new queue index
                val newQueueIndex = when {
                    trackIndex < currentIndex -> currentIndex - 1 // Track before current, shift index down
                    trackIndex == currentIndex -> currentIndex // Removing current track, keep same index (will be next track)
                    else -> currentIndex // Track after current, index unchanged
                }.coerceIn(0, newQueue.size - 1)
                
                // Update UI state
                _uiState.update { 
                    it.copy(
                        queue = newQueue,
                        queueIndex = newQueueIndex,
                        currentTrack = newQueue.getOrNull(newQueueIndex),
                        isQueueOperationInProgress = false
                    )
                }
                
                Log.d("MusicViewModel", "Removed track $trackId from queue")
            } else {
                _uiState.update { it.copy(isQueueOperationInProgress = false) }
                Log.w("MusicViewModel", "Failed to remove track $trackId from playback queue")
            }
        }
    }
    
    fun moveInQueue(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            // Set loading state
            _uiState.update { it.copy(isQueueOperationInProgress = true) }
            
            val currentState = _uiState.value
            val currentQueue = currentState.queue
            val currentQueueIndex = currentState.queueIndex
            
            if (fromIndex < 0 || fromIndex >= currentQueue.size || 
                toIndex < 0 || toIndex >= currentQueue.size) {
                _uiState.update { it.copy(isQueueOperationInProgress = false) }
                return@launch
            }
            
            // Move in playback queue first
            val playbackResult = playbackManager.moveInQueue(fromIndex, toIndex)
            
            if (playbackResult) {
                // Create new queue with item moved
                val newQueue = currentQueue.toMutableList().apply {
                    val item = removeAt(fromIndex)
                    add(toIndex, item)
                }
                
                // Calculate new queue index
                var newQueueIndex = currentQueueIndex
                if (fromIndex == currentQueueIndex) {
                    newQueueIndex = toIndex
                } else if (fromIndex < currentQueueIndex && toIndex >= currentQueueIndex) {
                    newQueueIndex = currentQueueIndex - 1
                } else if (fromIndex > currentQueueIndex && toIndex <= currentQueueIndex) {
                    newQueueIndex = currentQueueIndex + 1
                }
                
                // Update UI state
                _uiState.update { 
                    it.copy(
                        queue = newQueue,
                        queueIndex = newQueueIndex,
                        currentTrack = newQueue.getOrNull(newQueueIndex),
                        isQueueOperationInProgress = false
                    )
                }
                
                Log.d("MusicViewModel", "Moved track from index $fromIndex to $toIndex")
            } else {
                _uiState.update { it.copy(isQueueOperationInProgress = false) }
                Log.w("MusicViewModel", "Failed to move track in playback queue")
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        playbackManager.release()
        queueManager.cleanup()
    }
    
    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            trackDao.updateTrackFavourite(track.uuid, !track.isFavourite)
            
            // Update UI state if it's the current track
            _uiState.update { state ->
                if (state.currentTrack?.uuid == track.uuid) {
                    state.copy(currentTrack = track.copy(isFavourite = !track.isFavourite))
                } else {
                    state
                }
            }
        }
    }
}
