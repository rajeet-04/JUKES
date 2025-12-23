package com.example.juke.viewmodels

import android.app.Application
import android.util.Log
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
    val error: String? = null,
    val isPlaylistUrl: Boolean = false,
    val playlistId: String? = null,
    val isImportingPlaylist: Boolean = false,
    val importProgress: Int = 0,
    val importTotal: Int = 0
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
                // Check if query is a Spotify URL
                val urlInfo = parseSpotifyUrl(query)
                
                if (urlInfo != null) {
                    // Handle URL-based search
                    when (urlInfo.type) {
                        "track" -> {
                            val track = SpotifyApi.getTrack(urlInfo.id)
                            _uiState.value = _uiState.value.copy(
                                tracks = listOf(track),
                                artists = emptyList(),
                                playlists = emptyList(),
                                isSearching = false
                            )
                        }
                        "artist" -> {
                            val artist = SpotifyApi.getArtist(urlInfo.id)
                            _uiState.value = _uiState.value.copy(
                                tracks = emptyList(),
                                artists = listOf(artist),
                                playlists = emptyList(),
                                isSearching = false
                            )
                        }
                        "playlist" -> {
                            val playlist = SpotifyApi.getPlaylist(urlInfo.id)
                            _uiState.value = _uiState.value.copy(
                                tracks = emptyList(),
                                artists = emptyList(),
                                playlists = listOf(playlist),
                                isSearching = false,
                                isPlaylistUrl = true,
                                playlistId = urlInfo.id
                            )
                        }
                        "album" -> {
                            val album = SpotifyApi.getAlbum(urlInfo.id)
                            // Convert album to artist for display purposes
                            val artist = album.artists.firstOrNull()
                            _uiState.value = _uiState.value.copy(
                                tracks = emptyList(),
                                artists = if (artist != null) listOf(artist) else emptyList(),
                                playlists = emptyList(),
                                isSearching = false
                            )
                        }
                    }
                } else {
                    // Regular text search
                    val response = SpotifyApi.search(query)
                    
                    _uiState.value = _uiState.value.copy(
                        tracks = response.tracks?.items ?: emptyList(),
                        artists = response.artists?.items ?: emptyList(),
                        playlists = response.playlists?.items?.filterNotNull() ?: emptyList(),
                        isSearching = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    error = e.message ?: "Search failed"
                )
            }
        }
    }
    
    private data class SpotifyUrlInfo(val type: String, val id: String)
    
    private fun parseSpotifyUrl(query: String): SpotifyUrlInfo? {
        // Match Spotify URLs in different formats:
        // https://open.spotify.com/track/6rqhFgbbKwnb9MLmUQDhG6
        // https://open.spotify.com/artist/4Z8W4fKeB5YxbusRsdQVPb
        // https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M
        // https://open.spotify.com/album/6DEjYFkNZh67HP7R9PSZvv
        // spotify:track:6rqhFgbbKwnb9MLmUQDhG6
        
        val httpRegex = """https?://open\.spotify\.com/(track|artist|playlist|album)/([a-zA-Z0-9]+)""".toRegex()
        val uriRegex = """spotify:(track|artist|playlist|album):([a-zA-Z0-9]+)""".toRegex()
        
        httpRegex.find(query)?.let {
            return SpotifyUrlInfo(it.groupValues[1], it.groupValues[2])
        }
        
        uriRegex.find(query)?.let {
            return SpotifyUrlInfo(it.groupValues[1], it.groupValues[2])
        }
        
        return null
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
    
    fun importPlaylist(playlistId: String, onTrackDownloaded: suspend (SpotifyTrack) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isImportingPlaylist = true,
                importProgress = 0,
                importTotal = 0,
                error = null
            )
            
            try {
                // Fetch all playlist tracks
                val response = SpotifyApi.getPlaylistTracks(playlistId, limit = 50)
                val tracks = response.items.mapNotNull { it.track }
                
                _uiState.value = _uiState.value.copy(importTotal = tracks.size)
                
                // Download each track
                tracks.forEachIndexed { index, track ->
                    try {
                        onTrackDownloaded(track)
                        _uiState.value = _uiState.value.copy(importProgress = index + 1)
                    } catch (e: Exception) {
                        Log.e("SearchViewModel", "Failed to download track: ${track.name}", e)
                    }
                }
                
                _uiState.value = _uiState.value.copy(
                    isImportingPlaylist = false,
                    importProgress = 0,
                    importTotal = 0
                )
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isImportingPlaylist = false,
                    error = "Failed to import playlist: ${e.message}"
                )
            }
        }
    }
}
