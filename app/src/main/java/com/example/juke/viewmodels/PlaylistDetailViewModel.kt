package com.example.juke.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.juke.database.MusicDatabase
import com.example.juke.database.PlaylistEntity
import com.example.juke.database.PlaylistTrackEntity
import com.example.juke.models.SpotifyPlaylist
import com.example.juke.models.SpotifyTrack
import com.example.juke.models.Track
import com.example.juke.network.JukesApi
import com.example.juke.network.SpotifyApi
import com.example.juke.services.QueueManager
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger

data class PlaylistDetailUiState(
    val playlist: SpotifyPlaylist? = null,
    val tracks: List<SpotifyTrack> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isImportingPlaylist: Boolean = false,
    val importProgress: Int = 0,
    val importTotal: Int = 0
)

class PlaylistDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val database = MusicDatabase.getDatabase(application)
    private val playlistDao = database.playlistDao()
    private val queueManager = QueueManager.getInstance(application)
    private val importManager = com.example.juke.services.PlaylistImportManager.get(application)
    
    private val _uiState = MutableStateFlow(PlaylistDetailUiState())
    val uiState: StateFlow<PlaylistDetailUiState> = _uiState.asStateFlow()

    init {
        // Same persisted import status as everywhere else, filtered to the playlist on screen.
        viewModelScope.launch {
            importManager.status.collect { map ->
                val st = _uiState.value.playlist?.id?.let { map[it] }
                _uiState.value = _uiState.value.copy(
                    isImportingPlaylist = st != null,
                    importProgress = st?.done ?: 0,
                    importTotal = st?.total ?: 0
                )
            }
        }
    }
    
    fun loadPlaylistDetails(playlist: SpotifyPlaylist) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                playlist = playlist,
                isLoading = true,
                error = null
            )
            
            try {
                val tracksResponse = SpotifyApi.getPlaylistTracks(playlist.id)
                val tracks = tracksResponse.items.mapNotNull { it.track }
                
                _uiState.value = _uiState.value.copy(
                    tracks = tracks,
                    isLoading = false
                )
                // Opening a playlist usually means playing it from the top: warm its first songs.
                tracks.take(2).forEach { track ->
                    JukesApi.warmup(track.name, track.artists.joinToString(", ") { it.name }, track.durationMs.toLong())
                }
                
                Log.d("PlaylistDetailViewModel", "Loaded ${tracks.size} tracks for playlist ${playlist.name}")
            } catch (e: Exception) {
                Log.e("PlaylistDetailViewModel", "Error loading playlist details: ${e.message}", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }
    
    fun clearPlaylistDetail() {
        _uiState.value = PlaylistDetailUiState()
    }
    
    /** Saves the playlist and downloads it in the background (resumable, see PlaylistImportManager). */
    fun importPlaylistOffline() {
        val playlist = _uiState.value.playlist ?: return
        val tracks = _uiState.value.tracks
        viewModelScope.launch {
            try {
                importManager.enqueue(
                    PlaylistEntity(
                        id = playlist.id,
                        name = playlist.name,
                        description = playlist.description,
                        thumbnailUri = playlist.images.firstOrNull()?.url,
                        spotifyId = playlist.id,
                        trackCount = tracks.size
                    ),
                    tracks
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Failed to save playlist offline: ${e.message}")
            }
        }
    }
}
