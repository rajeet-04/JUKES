package com.example.juke.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.juke.database.MusicDatabase
import com.example.juke.database.PlaylistEntity
import com.example.juke.database.toTrack
import com.example.juke.models.Track
import com.example.juke.services.DownloadInfo
import com.example.juke.services.MusicService
import com.example.juke.services.QueueManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LibraryUiState(
    val tracks: List<Track> = emptyList(),
    val playlists: List<PlaylistEntity> = emptyList(),
    val showFavoritesOnly: Boolean = false,
    val isLoading: Boolean = false,
    val selectedPlaylist: PlaylistEntity? = null,
    val searchQuery: String = "",
    val downloadingTracks: Set<String> = emptySet(),
    val recommendationDownloads: List<DownloadInfo> = emptyList()
)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = MusicDatabase.getDatabase(application)
    private val trackDao = database.trackDao()
    private val playlistDao = database.playlistDao()
    private val musicService = MusicService(application)
    private val queueManager = QueueManager.getInstance(application)
    
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()
    
    init {
        loadPlaylists()
        loadAllTracks()
        
        // Observe downloaded tracks for real-time updates when in all tracks mode
        viewModelScope.launch {
            trackDao.getDownloadedTracksFlow().collect { trackEntities ->
                val currentState = _uiState.value
                if (!currentState.showFavoritesOnly && currentState.selectedPlaylist == null) {
                    _uiState.value = currentState.copy(
                        tracks = trackEntities.map { it.toTrack() },
                        isLoading = false
                    )
                }
            }
        }
        
        // Observe QueueManager downloads for recommendations
        viewModelScope.launch {
            queueManager.downloadingTracks.collect { downloadInfoList ->
                _uiState.value = _uiState.value.copy(recommendationDownloads = downloadInfoList)
            }
        }
    }
    
    fun loadAllTracks() {
        _uiState.value = _uiState.value.copy(showFavoritesOnly = false)
        loadTracks()
    }
    
    fun loadTracks() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            val tracks = if (_uiState.value.showFavoritesOnly) {
                trackDao.getFavourites().map { it.toTrack() }
            } else {
                trackDao.getDownloadedTracks().map { it.toTrack() }
            }
            
            val filteredTracks = filterTracksBySearch(tracks)
            
            _uiState.value = _uiState.value.copy(
                tracks = filteredTracks,
                isLoading = false,
                selectedPlaylist = null
            )
        }
    }
    
    private fun loadPlaylists() {
        viewModelScope.launch {
            playlistDao.getAllPlaylists().collect { playlists ->
                _uiState.value = _uiState.value.copy(playlists = playlists)
            }
        }
    }
    
    fun loadPlaylistTracks(playlistId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val playlist = playlistDao.getPlaylist(playlistId)
            val tracks = playlistDao.getPlaylistTracks(playlistId).map { it.toTrack() }
            val filteredTracks = filterTracksBySearch(tracks)
            
            _uiState.value = _uiState.value.copy(
                tracks = filteredTracks,
                showFavoritesOnly = false,
                selectedPlaylist = playlist,
                isLoading = false
            )
        }
    }
    
    fun toggleFavoritesFilter() {
        _uiState.value = _uiState.value.copy(
            showFavoritesOnly = !_uiState.value.showFavoritesOnly
        )
        loadTracks()
    }
    
    fun toggleFavorite(trackUuid: String) {
        viewModelScope.launch {
            // Get the track from current state to ensure we have the latest data
            val track = _uiState.value.tracks.find { it.uuid == trackUuid }
            if (track != null) {
                trackDao.updateTrackFavourite(track.uuid, !track.isFavourite)
                loadTracks()
            }
        }
    }
    
    fun deleteTrack(trackUuid: String) {
        viewModelScope.launch {
            // Get the track from current state to ensure we have the latest data
            val track = _uiState.value.tracks.find { it.uuid == trackUuid }
            if (track != null) {
                musicService.deleteTrackAndFiles(track)
                loadTracks()
            }
        }
    }
    
    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        loadTracks()
    }
    
    fun clearSearch() {
        _uiState.value = _uiState.value.copy(searchQuery = "")
        loadTracks()
    }
    
    private fun filterTracksBySearch(tracks: List<Track>): List<Track> {
        val query = _uiState.value.searchQuery.trim()
        if (query.isEmpty()) return tracks
        
        val lowerQuery = query.lowercase()
        return tracks.filter { track ->
            // Search in title
            track.title.lowercase().contains(lowerQuery) ||
            // Search in artist
            track.artist.lowercase().contains(lowerQuery) ||
            // Search in plain lyrics
            (track.plainLyrics?.lowercase()?.contains(lowerQuery) == true) ||
            // Search in synced lyrics (remove timestamps for search)
            (track.syncedLyrics?.lowercase()?.replace(Regex("\\[\\d+:\\d+\\.\\d+]"), "")
                ?.replace(Regex("\\[\\d+:\\d+]"), "")?.trim()?.contains(lowerQuery) == true)
        }
    }
}
