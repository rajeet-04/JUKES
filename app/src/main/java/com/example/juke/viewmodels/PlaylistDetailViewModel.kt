package com.example.juke.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.juke.models.SpotifyPlaylist
import com.example.juke.models.SpotifyTrack
import com.example.juke.network.SpotifyApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlaylistDetailUiState(
    val playlist: SpotifyPlaylist? = null,
    val tracks: List<SpotifyTrack> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class PlaylistDetailViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(PlaylistDetailUiState())
    val uiState: StateFlow<PlaylistDetailUiState> = _uiState.asStateFlow()
    
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
}
