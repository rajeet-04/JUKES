package com.example.juke.viewmodels

import android.app.Application
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

data class MusicUiState(
    val currentTrack: Track? = null,
    val queue: List<Track> = emptyList(),
    val queueIndex: Int = -1,
    val isPlaying: Boolean = false,
    val position: Long = 0,
    val duration: Long = 0,
    val isLoading: Boolean = false,
    val error: String? = null
)

class MusicViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = MusicDatabase.getDatabase(application)
    private val trackDao = database.trackDao()
    private val musicService = MusicService(application)
    val playbackManager = PlaybackManager(application)
    
    private val _uiState = MutableStateFlow(MusicUiState())
    val uiState: StateFlow<MusicUiState> = _uiState.asStateFlow()
    
    init {
        playbackManager.initialize()
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
        _uiState.update { it.copy(isLoading = true, error = null) }
        try {
            val existingTrack = trackDao.findTrackByTitleArtist(song.title, song.artist)
            
            val track = if (existingTrack != null && existingTrack.localUri != null) {
                existingTrack.toTrack()
            } else {
                musicService.smartDownloadAndIndex(song)
            }
            
            playTrack(track)
            _uiState.update { it.copy(isLoading = false) }
        } catch (e: Exception) {
            _uiState.update { 
                it.copy(
                    isLoading = false,
                    error = e.message ?: "Download failed"
                )
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        playbackManager.release()
    }
}
