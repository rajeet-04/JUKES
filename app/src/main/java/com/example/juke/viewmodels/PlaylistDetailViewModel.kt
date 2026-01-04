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
import com.example.juke.network.SpotifyApi
import com.example.juke.services.QueueManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
    
    fun importPlaylistOffline(onTrackDownloaded: suspend (SpotifyTrack) -> Track) {
        val playlist = _uiState.value.playlist ?: return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isImportingPlaylist = true,
                importProgress = 0,
                importTotal = 0,
                error = null
            )

            try {
                // Save playlist to database
                val playlistEntity = PlaylistEntity(
                    id = playlist.id,
                    name = playlist.name,
                    description = playlist.description,
                    thumbnailUri = playlist.images.firstOrNull()?.url,
                    spotifyId = playlist.id,
                    trackCount = playlist.tracks.total
                )
                playlistDao.insertPlaylist(playlistEntity)

                // Get tracks
                val tracks = _uiState.value.tracks
                _uiState.value = _uiState.value.copy(importTotal = tracks.size)

                // Download each track and add to playlist
                val playlistTracks = mutableListOf<PlaylistTrackEntity>()
                tracks.forEachIndexed { index, track ->
                    try {
                        // Add to download tracking
                        queueManager.addDownloadTracking(
                            track.name,
                            track.artists.joinToString(", ") { it.name },
                            "playlist"
                        )

                        val downloadedTrack = onTrackDownloaded(track)

                        // Remove from download tracking
                        queueManager.removeDownloadTracking(
                            track.name,
                            track.artists.joinToString(", ") { it.name }
                        )

                        // Add to playlist tracks
                        val playlistTrack = PlaylistTrackEntity(
                            playlistId = playlist.id,
                            trackUuid = downloadedTrack.uuid,
                            position = index
                        )
                        playlistDao.insertPlaylistTrack(playlistTrack)
                        playlistTracks.add(playlistTrack)
                        _uiState.value = _uiState.value.copy(importProgress = index + 1)
                    } catch (e: Exception) {
                        // Remove from download tracking on error
                        queueManager.removeDownloadTracking(
                            track.name,
                            track.artists.joinToString(", ") { it.name }
                        )
                        Log.e("PlaylistDetailViewModel", "Failed to download track: ${track.name}", e)
                    }
                }

                // Update playlist track count
                playlistDao.updatePlaylistTrackCount(playlist.id, playlistTracks.size)

                _uiState.value = _uiState.value.copy(
                    isImportingPlaylist = false,
                    importProgress = 0,
                    importTotal = 0
                )

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isImportingPlaylist = false,
                    error = "Failed to save playlist offline: ${e.message}"
                )
            }
        }
    }
}
