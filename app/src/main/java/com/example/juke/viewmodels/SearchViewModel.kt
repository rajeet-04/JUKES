package com.example.juke.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.juke.models.*
import com.example.juke.network.SpotifyApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val tracks: List<SpotifyTrack> = emptyList(),
    val artists: List<SpotifyArtist> = emptyList(),
    val playlists: List<SpotifyPlaylist> = emptyList(),
    val isSearching: Boolean = false,
    val downloadingId: String? = null,
    val error: String? = null
)

data class ArtistDetailUiState(
    val artist: SpotifyArtist? = null,
    val albums: List<SpotifyAlbum> = emptyList(),
    val topTracks: List<SpotifyTrack> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class SearchViewModel(application: Application) : AndroidViewModel(application) {
    
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()
    
    private val _artistDetailState = MutableStateFlow(ArtistDetailUiState())
    val artistDetailState: StateFlow<ArtistDetailUiState> = _artistDetailState.asStateFlow()
    
    fun updateQuery(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
    }
    
    fun search(query: String) {
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(
                tracks = emptyList(),
                artists = emptyList(),
                playlists = emptyList()
            )
            return
        }
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearching = true, error = null)
            
            try {
                val response = SpotifyApi.search(query)
                
                _uiState.value = _uiState.value.copy(
                    tracks = response.tracks?.items ?: emptyList(),
                    artists = response.artists?.items ?: emptyList(),
                    playlists = response.playlists?.items?.filterNotNull() ?: emptyList(),
                    isSearching = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    error = e.message ?: "Search failed"
                )
            }
        }
    }
    
    fun loadArtistDetails(artist: SpotifyArtist) {
        viewModelScope.launch {
            _artistDetailState.value = ArtistDetailUiState(
                artist = artist,
                isLoading = true
            )
            
            try {
                val albums = SpotifyApi.getArtistAlbums(artist.id)
                val topTracks = SpotifyApi.getArtistTopTracks(artist.id)
                
                _artistDetailState.value = _artistDetailState.value.copy(
                    albums = albums.items,
                    topTracks = topTracks.tracks,
                    isLoading = false
                )
            } catch (e: Exception) {
                _artistDetailState.value = _artistDetailState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load artist details"
                )
            }
        }
    }
    
    fun setDownloading(songId: String?) {
        _uiState.value = _uiState.value.copy(downloadingId = songId)
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    fun clearArtistDetail() {
        _artistDetailState.value = ArtistDetailUiState()
    }
}
