package com.example.juke.viewmodels

import android.app.Application
import androidx.core.net.toUri
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SortOption {
    RECENTLY_ADDED,
    TITLE,
    ARTIST,
    LAST_PLAYED,
    MOST_PLAYED
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
    val showSortSheet: Boolean = false,
    val pendingDeleteTrack: Track? = null,
    val isSelectionMode: Boolean = false,
    val selectedTrackUuids: Set<String> = emptySet()
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

            // Self-healing: Check if track count matches
            if (playlist != null && playlist.trackCount != tracks.size) {
                // Update DB
                val updatedPlaylist = playlist.copy(trackCount = tracks.size)
                playlistDao.updatePlaylist(updatedPlaylist)
                
                // Update local object for UI
                _uiState.value = _uiState.value.copy(
                    tracks = sortedTracks,
                    showFavoritesOnly = false,
                    selectedPlaylist = updatedPlaylist,
                    isLoading = false
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    tracks = sortedTracks,
                    showFavoritesOnly = false,
                    selectedPlaylist = playlist,
                    isLoading = false
                )
            }
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

    private var deletionJob: kotlinx.coroutines.Job? = null

    fun deleteTrack(trackUuid: String) {
        viewModelScope.launch {
            // If there's a pending delete, commit it immediately before starting a new one
            if (deletionJob?.isActive == true) {
                val pending = _uiState.value.pendingDeleteTrack
                // If we are deleting a different track, commit the previous one immediately
                if (pending != null && pending.uuid != trackUuid) {
                    deletionJob?.cancel()
                    // Immediately commit the pending delete
                    musicService.deleteTrackAndFiles(pending)
                    _uiState.value = _uiState.value.copy(pendingDeleteTrack = null)
                }
            }

            // Get the track from current state
            val track = _uiState.value.tracks.find { it.uuid == trackUuid }
            if (track != null) {
                // Store pending track for undo
                _uiState.value = _uiState.value.copy(pendingDeleteTrack = track)

                // Optimistic remove from UI
                val updatedTracks = _uiState.value.tracks.filter { it.uuid != trackUuid }
                _uiState.value = _uiState.value.copy(tracks = updatedTracks)

                // Start timer
                deletionJob?.cancel()
                deletionJob = launch {
                    kotlinx.coroutines.delay(5000) // 5 seconds wait
                    commitDelete()
                }
            }
        }
    }

    private fun commitDelete() {
        val pendingTrack = _uiState.value.pendingDeleteTrack ?: return

        viewModelScope.launch {
            musicService.deleteTrackAndFiles(pendingTrack)

            // Clear pending state
            _uiState.value = _uiState.value.copy(pendingDeleteTrack = null)

            // Refresh silent logic (kept from original)
            if (_uiState.value.selectedPlaylist != null) {
                loadPlaylistTracks(_uiState.value.selectedPlaylist!!.id, silent = true)
            } else {
                loadTracks(silent = true)
            }
        }
    }

    fun undoDelete() {
        if (deletionJob?.isActive == true) {
            deletionJob?.cancel()
            val pendingTrack = _uiState.value.pendingDeleteTrack

            if (pendingTrack != null) {
                // Restore track to UI
                // We need to figure out where to insert it, or just reload to be safe and simple
                // Simple append for now or reload? Reloading might lose scroll position but ensures correct sort.
                // Let's try to just insert it back to `uiState` for instant feedback if possible,
                // but sort order matters.

                // Easiest correct way: add back to list and re-sort
                val currentTracks = _uiState.value.tracks.toMutableList()
                currentTracks.add(pendingTrack)
                val sorted =
                    sortTracks(filterTracksBySearch(currentTracks), _uiState.value.sortOption)

                _uiState.value = _uiState.value.copy(
                    tracks = sorted,
                    pendingDeleteTrack = null
                )
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
            SortOption.MOST_PLAYED -> tracks.sortedByDescending { it.playCount }
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

    fun createPlaylist(name: String, onCreated: ((String) -> Unit)? = null) {
        viewModelScope.launch {
            val uuid = java.util.UUID.randomUUID().toString()
            val newPlaylist = PlaylistEntity(
                id = uuid,
                name = name,
                trackCount = 0,
                createdAt = System.currentTimeMillis()
            )
            playlistDao.insertPlaylist(newPlaylist)
            onCreated?.invoke(uuid)
        }
    }

    fun updatePlaylist(playlist: PlaylistEntity, newName: String, newThumbnailUriString: String?) {
        viewModelScope.launch {
            var finalUriString = newThumbnailUriString

            // If URI changed and is a content URI, copy it to internal storage
            if (newThumbnailUriString != null && newThumbnailUriString != playlist.thumbnailUri) {
                val uri = newThumbnailUriString.toUri()
                if (uri.scheme == "content") {
                    val context = getApplication<Application>()
                    try {
                        val inputStream = context.contentResolver.openInputStream(uri)
                        val filename =
                            "playlist_cover_${playlist.id}_${System.currentTimeMillis()}.jpg"
                        val file = java.io.File(context.filesDir, "covers")
                        if (!file.exists()) file.mkdirs()
                        val destFile = java.io.File(file, filename)

                        inputStream?.use { input ->
                            java.io.FileOutputStream(destFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        finalUriString = destFile.toURI().toString()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        // On error, fallback to null or keep original if valid? 
                        // For now we keep what was passed, but it might fail to load later if permission lost.
                    }
                }
            }

            val updatedPlaylist = playlist.copy(
                name = newName,
                thumbnailUri = finalUriString
            )
            playlistDao.updatePlaylist(updatedPlaylist)

            // Refresh if selected
            if (_uiState.value.selectedPlaylist?.id == playlist.id) {
                loadPlaylistTracks(playlist.id, silent = true)
            }
        }
    }

    fun deletePlaylist(playlist: PlaylistEntity) {
        viewModelScope.launch {
            playlistDao.deletePlaylist(playlist.id)
            if (_uiState.value.selectedPlaylist?.id == playlist.id) {
                // Return to all tracks if the deleted playlist was selected
                loadAllTracks()
            }
        }
    }


    suspend fun addToPlaylist(playlist: PlaylistEntity, track: Track) {
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

    suspend fun removeFromPlaylist(
        playlist: PlaylistEntity,
        track: Track
    ) {
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

    // Selection Mode Logic

    fun toggleSelectionMode(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(
            isSelectionMode = enabled,
            selectedTrackUuids = if (!enabled) emptySet() else _uiState.value.selectedTrackUuids
        )
    }

    fun toggleTrackSelection(trackUuid: String) {
        val currentSelection = _uiState.value.selectedTrackUuids.toMutableSet()
        if (currentSelection.contains(trackUuid)) {
            currentSelection.remove(trackUuid)
            // If last item deselected, exit selection mode
            if (currentSelection.isEmpty()) {
                toggleSelectionMode(false)
            } else {
                _uiState.value = _uiState.value.copy(selectedTrackUuids = currentSelection)
            }
        } else {
            currentSelection.add(trackUuid)
            // Enable selection mode if not already enabled (e.g. on long press first item)
            _uiState.value.isSelectionMode
            _uiState.value = _uiState.value.copy(
                selectedTrackUuids = currentSelection,
                isSelectionMode = true
            )
        }
    }

    fun clearSelection() {
        toggleSelectionMode(false)
    }

    fun selectAll() {
        val allTrackIds = _uiState.value.tracks.map { it.uuid }.toSet()
        _uiState.value = _uiState.value.copy(
            selectedTrackUuids = allTrackIds,
            isSelectionMode = true
        )
    }

    fun deleteSelectedTracks() {
        val selectedIds = _uiState.value.selectedTrackUuids
        if (selectedIds.isEmpty()) return
        
        viewModelScope.launch {
            val tracksToDelete = _uiState.value.tracks.filter { selectedIds.contains(it.uuid) }
            
            if (tracksToDelete.isNotEmpty()) {
                musicService.deleteTracksAndFiles(tracksToDelete)
            }
            
            clearSelection()
            
            if (_uiState.value.selectedPlaylist != null) {
                loadPlaylistTracks(_uiState.value.selectedPlaylist!!.id, silent = true)
            } else {
                loadTracks(silent = true)
            }
        }
    }

    fun addTracksToPlaylist(playlist: PlaylistEntity, tracks: List<Track>) {
        if (tracks.isEmpty()) return

        viewModelScope.launch {
            val existingTracks = playlistDao.getPlaylistTracks(playlist.id)
            val existingUuids = existingTracks.map { it.uuid }.toSet()

            // Filter out duplicates
            val newTracks = tracks.filter { !existingUuids.contains(it.uuid) }
            if (newTracks.isEmpty()) return@launch

            // Batch insert
            var position = existingTracks.size
            val playlistTracks = newTracks.map { track ->
                com.example.juke.database.PlaylistTrackEntity(
                    playlistId = playlist.id,
                    trackUuid = track.uuid,
                    position = position++,
                    addedAt = System.currentTimeMillis()
                )
            }

            // Insert all (assuming DAO has batch insert, if not loop)
            // playlistDao.insertPlaylistTracks(playlistTracks) -> Need to check DAO
            // If no batch insert, loop:
            playlistTracks.forEach { playlistDao.insertPlaylistTrack(it) }

            // Update track count
            val newCount = playlist.trackCount + newTracks.size
            playlistDao.updatePlaylistTrackCount(playlist.id, newCount)

            // Refresh
            if (_uiState.value.selectedPlaylist?.id == playlist.id) {
                loadPlaylistTracks(playlist.id, silent = true)
            }
        }
    }

    fun addPlaylistToQueue(playlist: PlaylistEntity, musicViewModel: MusicViewModel) {
        viewModelScope.launch {
            var tracks = playlistDao.getPlaylistTracks(playlist.id).map { it.toTrack() }
            
            // Respect the current sort option (maintain order seen in UI)
            // Even if not currently viewing this playlist, using the global sort option is a reasonable default
            // consistent with the "maintain order" request which implies "order I expect".
            tracks = sortTracks(tracks, _uiState.value.sortOption)

            if (tracks.isNotEmpty()) {
                // If shuffle is enabled, shuffle the tracks before adding
                if (musicViewModel.uiState.value.isShuffleEnabled) {
                    tracks = tracks.shuffled()
                }
                musicViewModel.addToQueue(tracks)
            }
        }
    }

    fun importAudioFiles(uris: List<android.net.Uri>) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }
            val context = getApplication<Application>()
            val importedTracks = mutableListOf<com.example.juke.database.TrackEntity>()

            uris.forEach { uri ->
                try {
                    // 1. Copy file to internal storage
                    val returnCursor = context.contentResolver.query(uri, null, null, null, null)
                    val nameIndex =
                        returnCursor?.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    returnCursor?.moveToFirst()
                    val fileName = returnCursor?.getString(nameIndex ?: 0)
                        ?: "imported_${System.currentTimeMillis()}.mp3"
                    returnCursor?.close()

                    val importDir = java.io.File(context.filesDir, "imported_music")
                    if (!importDir.exists()) importDir.mkdirs()

                    val destFile = java.io.File(importDir, fileName)

                    // Copy stream
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        java.io.FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }

                    // 2. Extract Metadata
                    val retriever = android.media.MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(destFile.absolutePath)
                        val title =
                            retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)
                                ?: fileName.substringBeforeLast(".")
                        val artist =
                            retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)
                                ?: "Unknown Artist"
                        val durationStr =
                            retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                        val durationSec = (durationStr?.toLongOrNull() ?: 0L) / 1000
                        retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM)

                        // Extract Art
                        var thumbnailUri: String? = null
                        val artBytes = retriever.embeddedPicture
                        if (artBytes != null) {
                            val artFile = java.io.File(context.filesDir, "covers")
                            if (!artFile.exists()) artFile.mkdirs()
                            val artFileName =
                                "cover_${destFile.name}_${System.currentTimeMillis()}.jpg"
                            val destArt = java.io.File(artFile, artFileName)
                            java.io.FileOutputStream(destArt).use { it.write(artBytes) }
                            thumbnailUri = destArt.toURI().toString()
                        }

                        // 3. Create Track Entity
                        val track = com.example.juke.database.TrackEntity(
                            uuid = java.util.UUID.randomUUID().toString(),
                            title = title,
                            artist = artist,
                            thumbnailUri = thumbnailUri,
                            durationSec = durationSec.toInt(),
                            localUri = destFile.toURI().toString(),
                            isFavourite = false,
                            downloadedAt = System.currentTimeMillis() // Treat as downloaded
                        )
                        importedTracks.add(track)

                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        retriever.release()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 4. Insert into DB
            if (importedTracks.isNotEmpty()) {
                trackDao.insertTracks(importedTracks)
            }

            _uiState.update { it.copy(isLoading = false) }

            // Force reload
            // if we are in "All Tracks" it observes flow so it should update automatically
        }
    }
}