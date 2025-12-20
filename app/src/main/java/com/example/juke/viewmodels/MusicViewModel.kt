package com.example.juke.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.juke.database.MusicDatabase
import com.example.juke.database.toTrack
import com.example.juke.models.SpotdownSong
import com.example.juke.models.Track
import com.example.juke.network.SpotifyApi
import com.example.juke.services.MusicService
import com.example.juke.services.PlaybackManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    val currentDownload: DownloadItem? = null
)

class MusicViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = MusicDatabase.getDatabase(application)
    private val trackDao = database.trackDao()
    private val musicService = MusicService(application)
    val playbackManager = PlaybackManager(application)
    
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
    }
    
    fun playTrack(track: Track) {
        viewModelScope.launch {
            playbackManager.playTrack(track)
            _uiState.update { 
                it.copy(
                    currentTrack = track,
                    isPlaying = true,
                    duration = track.durationSec.toLong() * 1000
                )
            }
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
        _uiState.update { it.copy(isPlaying = playbackManager.isPlaying()) }
    }
    
    fun skipToNext() {
        playbackManager.skipToNext()
        val newIndex = (_uiState.value.queueIndex + 1).coerceIn(0, _uiState.value.queue.size - 1)
        _uiState.update { 
            it.copy(
                queueIndex = newIndex,
                currentTrack = _uiState.value.queue.getOrNull(newIndex)
            )
        }
    }
    
    fun skipToPrevious() {
        playbackManager.skipToPrevious()
        val newIndex = (_uiState.value.queueIndex - 1).coerceAtLeast(0)
        _uiState.update { 
            it.copy(
                queueIndex = newIndex,
                currentTrack = _uiState.value.queue.getOrNull(newIndex)
            )
        }
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
        addToDownloadQueue(downloadItem.song, downloadItem.shouldPlayAfterDownload)
    }
    
    override fun onCleared() {
        super.onCleared()
        playbackManager.release()
    }
}
