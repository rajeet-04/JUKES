package com.example.juke.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.juke.models.SpotifyAlbum
import com.example.juke.models.SpotifySimplifiedTrack
import com.example.juke.network.SpotifyApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AlbumDetailUiState(
    val album: SpotifyAlbum? = null,
    val tracks: List<SpotifySimplifiedTrack> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class AlbumDetailViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(AlbumDetailUiState())
    val uiState: StateFlow<AlbumDetailUiState> = _uiState.asStateFlow()

    fun loadAlbumDetails(album: SpotifyAlbum) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                album = album,
                isLoading = true,
                error = null
            )

            try {
                if (album.id != null) {
                    val tracksResponse = SpotifyApi.getAlbumTracks(album.id)

                    _uiState.value = _uiState.value.copy(
                        tracks = tracksResponse.items,
                        isLoading = false
                    )

                    Log.d(
                        "AlbumDetailViewModel",
                        "Loaded ${tracksResponse.items.size} tracks for album ${album.name}"
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Invalid album ID"
                    )
                }
            } catch (e: Exception) {
                Log.e("AlbumDetailViewModel", "Error loading album details: ${e.message}", e)
                _uiState.update {
                    it.copy(isLoading = false, error = e.message)
                }
            }
        }
    }

    fun loadAlbumDetailsById(albumId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                // Fetch album details first
                val album = SpotifyApi.getAlbum(albumId)
                loadAlbumDetails(album)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message)
                }
            }
        }
    }

    fun clearAlbumDetail() {
        _uiState.value = AlbumDetailUiState()
    }
}