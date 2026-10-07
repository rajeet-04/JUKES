package com.example.juke.viewmodels

import android.util.Log
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.juke.repositories.OfflineAlbumStore
import com.example.juke.models.collectAlbumTracks
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import androidx.lifecycle.viewModelScope
import com.example.juke.models.SpotifyAlbum
import com.example.juke.models.SpotifySimplifiedTrack
import com.example.juke.network.JukesApi
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

class AlbumDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val offlineAlbums = OfflineAlbumStore.get(application)
    private var loadJob: Job? = null
    private val _uiState = MutableStateFlow(AlbumDetailUiState())
    val uiState: StateFlow<AlbumDetailUiState> = _uiState.asStateFlow()

    fun loadAlbumDetails(album: SpotifyAlbum) {
        loadJob?.cancel()
        offlineAlbums.find(album.id.orEmpty())?.let {
            _uiState.value = AlbumDetailUiState(album = it.album, tracks = it.tracks)
            return
        }
        loadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                album = album,
                tracks = emptyList(),
                isLoading = true,
                error = null
            )

            try {
                if (album.id != null) {
                    val tracks = collectAlbumTracks { offset -> SpotifyApi.getAlbumTracks(album.id, offset = offset) }

                    _uiState.value = _uiState.value.copy(
                        tracks = tracks,
                        isLoading = false
                    )
                    // Opening an album usually means playing it from the top: warm its first songs.
                    tracks.take(2).forEach { track ->
                        JukesApi.warmup(track.name, track.artists.joinToString(", ") { it.name }, track.durationMs.toLong())
                    }

                    Log.d(
                        "AlbumDetailViewModel",
                        "Loaded ${tracks.size} tracks for album ${album.name}"
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Invalid album ID"
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("AlbumDetailViewModel", "Error loading album details: ${e.message}", e)
                _uiState.update {
                    it.copy(isLoading = false, error = e.message)
                }
            }
        }
    }

    fun loadAlbumDetailsById(albumId: String) {
        offlineAlbums.find(albumId)?.let { loadAlbumDetails(it.album); return }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                // Fetch album details first
                val album = SpotifyApi.getAlbum(albumId)
                loadAlbumDetails(album)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message)
                }
            }
        }
    }

    fun clearAlbumDetail() {
        loadJob?.cancel()
        _uiState.value = AlbumDetailUiState()
    }
}