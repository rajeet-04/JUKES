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

enum class SortOption {
    RECENTLY_ADDED,
    TITLE,
    ARTIST,
    LAST_PLAYED
}

data class LibraryUiState(
    val tracks: List<Track> = emptyList(),
    val playlists: List<PlaylistEntity> = emptyList(),
    val showFavoritesOnly: Boolean = false,
    val isLoading: Boolean = false,
    val selectedPlaylist: PlaylistEntity? = null,
    val searchQuery: String = "",
    val downloadingTracks: Set<String> = emptySet(),
    val recommendationDownloads: List<DownloadInfo> = emptyList(),
    val sortOption: SortOption = SortOption.RECENTLY_ADDED,
    val showSortSheet: Boolean = false
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
                    val tracks = trackEntities.map { it.toTrack() }
                    val filteredTracks = filterTracksBySearch(tracks)
                    val sortedTracks = sortTracks(filteredTracks, currentState.sortOption)
                    
                    _uiState.value = currentState.copy(
                        tracks = sortedTracks,
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
    
    fun loadTracks(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) {
                _uiState.value = _uiState.value.copy(isLoading = true)
            }
            
            val tracks = if (_uiState.value.showFavoritesOnly) {
                trackDao.getFavourites().map { it.toTrack() }
            } else {
                trackDao.getDownloadedTracks().map { it.toTrack() }
            }
            
            val filteredTracks = filterTracksBySearch(tracks)
            val sortedTracks = sortTracks(filteredTracks, _uiState.value.sortOption)
            
            _uiState.value = _uiState.value.copy(
                tracks = sortedTracks,
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
    
    fun loadPlaylistTracks(playlistId: String, silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) {
                _uiState.value = _uiState.value.copy(isLoading = true)
            }

            val playlist = playlistDao.getPlaylist(playlistId)
            val tracks = playlistDao.getPlaylistTracks(playlistId).map { it.toTrack() }
            val filteredTracks = filterTracksBySearch(tracks)
            val sortedTracks = sortTracks(filteredTracks, _uiState.value.sortOption)
            
            _uiState.value = _uiState.value.copy(
                tracks = sortedTracks,
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
                // Optimistic update
                val updatedTracks = _uiState.value.tracks.map {
                    if (it.uuid == trackUuid) it.copy(isFavourite = !it.isFavourite) else it
                }
                _uiState.value = _uiState.value.copy(tracks = updatedTracks)
                
                trackDao.updateTrackFavourite(track.uuid, !track.isFavourite)
                
                // Refresh silently to sync with database
                if (_uiState.value.selectedPlaylist != null) {
                    loadPlaylistTracks(_uiState.value.selectedPlaylist!!.id, silent = true)
                } else {
                    loadTracks(silent = true)
                }
            }
        }
    }
    
    fun deleteTrack(trackUuid: String) {
        viewModelScope.launch {
            // Get the track from current state to ensure we have the latest data
            val track = _uiState.value.tracks.find { it.uuid == trackUuid }
            if (track != null) {
                // Optimistic update
                val updatedTracks = _uiState.value.tracks.filter { it.uuid != trackUuid }
                _uiState.value = _uiState.value.copy(tracks = updatedTracks)
                
                musicService.deleteTrackAndFiles(track)
                
                // Refresh silently to sync with database
                if (_uiState.value.selectedPlaylist != null) {
                    loadPlaylistTracks(_uiState.value.selectedPlaylist!!.id, silent = true)
                } else {
                    loadTracks(silent = true)
                }
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
    
    private fun sortTracks(tracks: List<Track>, sortOption: SortOption): List<Track> {
        return when (sortOption) {
            SortOption.RECENTLY_ADDED -> tracks.sortedByDescending { it.downloadedAt ?: 0L }
            SortOption.TITLE -> tracks.sortedBy { it.title.lowercase() }
            SortOption.ARTIST -> tracks.sortedBy { it.artist.lowercase() }
            SortOption.LAST_PLAYED -> tracks.sortedByDescending { it.lastPlayedAt }
        }
    }
    
    fun updateSortOption(option: SortOption) {
        _uiState.value = _uiState.value.copy(sortOption = option, showSortSheet = false)
        if (_uiState.value.selectedPlaylist != null) {
            loadPlaylistTracks(_uiState.value.selectedPlaylist!!.id)
        } else {
            loadTracks()
        }
    }
    
    fun toggleSortSheet() {
        _uiState.value = _uiState.value.copy(showSortSheet = !_uiState.value.showSortSheet)
    }
    fun createPlaylist(name: String) {
        viewModelScope.launch {
            val uuid = java.util.UUID.randomUUID().toString()
            val newPlaylist = com.example.juke.database.PlaylistEntity(
                id = uuid,
                name = name,
                trackCount = 0,
                createdAt = System.currentTimeMillis()
            )
            playlistDao.insertPlaylist(newPlaylist)
        }
    }
    
    fun deletePlaylist(playlist: com.example.juke.database.PlaylistEntity) {
        viewModelScope.launch {
            playlistDao.deletePlaylist(playlist.id)
            if (_uiState.value.selectedPlaylist?.id == playlist.id) {
                // Return to all tracks if the deleted playlist was selected
                loadAllTracks()
            }
        }
    }
    

    suspend fun addToPlaylist(playlist: com.example.juke.database.PlaylistEntity, track: Track) {
        // Check if track is already in playlist
        val existingTracks = playlistDao.getPlaylistTracks(playlist.id)
        if (existingTracks.any { it.uuid == track.uuid }) {
            return
        }
        
        // Add to playlist_tracks
        val position = existingTracks.size // Add to end
        val playlistTrack = com.example.juke.database.PlaylistTrackEntity(
            playlistId = playlist.id,
            trackUuid = track.uuid,
            position = position,
            addedAt = System.currentTimeMillis()
        )
        playlistDao.insertPlaylistTrack(playlistTrack)
        
        // Update track count
        val newCount = playlist.trackCount + 1
        playlistDao.updatePlaylistTrackCount(playlist.id, newCount)
        
        // If this is the selected playlist, refresh silently
        if (_uiState.value.selectedPlaylist?.id == playlist.id) {
             loadPlaylistTracks(playlist.id, silent = true)
        }
    }
    
    suspend fun removeFromPlaylist(playlist: com.example.juke.database.PlaylistEntity, track: Track) {
        playlistDao.removeTrackFromPlaylist(playlist.id, track.uuid)
        
        // Update track count
        val newCount = (playlist.trackCount - 1).coerceAtLeast(0)
        playlistDao.updatePlaylistTrackCount(playlist.id, newCount)
        
        // If this is the selected playlist, refresh silently
        if (_uiState.value.selectedPlaylist?.id == playlist.id) {
             loadPlaylistTracks(playlist.id, silent = true)
        }
    }
    
    fun shufflePlay(tracks: List<Track>, musicViewModel: MusicViewModel) {
        val shuffled = tracks.shuffled()
        musicViewModel.setQueue(shuffled, 0)
    }
    
    suspend fun getPlaylistsForTrack(trackUuid: String): List<PlaylistEntity> {
        return playlistDao.getPlaylistsForTrack(trackUuid)
    }
}
