package com.example.juke.viewmodels

import android.app.Application
import androidx.core.net.toUri
import androidx.room.withTransaction
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
    val pendingRemoval: PendingLibraryRemoval? = null,
    val isRemovalCommitting: Boolean = false,
    val actionMessage: String? = null,
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
                    val filteredTracks = filterTracksBySearch(tracks, null)
                    val sortedTracks = sortTracks(filteredTracks, currentState.sortOption)

                    _uiState.value = currentState.copy(
                        tracks = sortedTracks,
                        isLoading = false
                    ).withVisibleSelection()
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

            val filteredTracks = filterTracksBySearch(tracks, null)
            val sortedTracks = sortTracks(filteredTracks, _uiState.value.sortOption)

            _uiState.value = _uiState.value.copy(
                tracks = sortedTracks,
                isLoading = false,
                selectedPlaylist = null
            ).withVisibleSelection()
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
            val filteredTracks = filterTracksBySearch(tracks, playlistId)
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
                ).withVisibleSelection()
            } else {
                _uiState.value = _uiState.value.copy(
                    tracks = sortedTracks,
                    showFavoritesOnly = false,
                    selectedPlaylist = playlist,
                    isLoading = false
                ).withVisibleSelection()
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

    private val removalUndo = LibraryRemovalUndo(
        scope = viewModelScope,
        commit = { pending ->
            if (pending.playlist == null) {
                musicService.deleteTracksAndFiles(pending.tracks, reportErrors = true)
            } else {
                database.withTransaction {
                    pending.tracks.forEach { playlistDao.removeTrackFromPlaylist(pending.playlist.id, it.uuid) }
                    playlistDao.updatePlaylistTrackCount(
                        pending.playlist.id, playlistDao.getPlaylistTrackCount(pending.playlist.id)
                    )
                }
            }
        },
        onChanged = { pending, committing ->
            _uiState.update { state ->
                state.copy(
                    pendingRemoval = pending,
                    isRemovalCommitting = committing,
                    tracks = state.tracks.filterNot { pending?.hides(it.uuid, state.selectedPlaylist?.id) == true },
                    isSelectionMode = state.isSelectionMode && (pending == null || committing),
                    selectedTrackUuids = if (pending != null && !committing) emptySet() else state.selectedTrackUuids
                )
            }
            if (pending == null) refreshVisibleTracks()
        },
        onFailure = { pending ->
            _uiState.update {
                it.copy(
                    isSelectionMode = true,
                    selectedTrackUuids = it.selectedTrackUuids + pending.trackIds,
                    actionMessage = "Couldn’t finish removing songs. Check your library and try again."
                )
            }
        }
    )

    fun showActionMessage(message: String) {
        _uiState.update { it.copy(actionMessage = message) }
    }

    fun consumeActionMessage(message: String) {
        _uiState.update { if (it.actionMessage == message) it.copy(actionMessage = null) else it }
    }

    fun stageRemoval(tracks: List<Track>, playlist: PlaylistEntity? = null) {
        viewModelScope.launch {
            if (!removalUndo.stage(PendingLibraryRemoval(tracks.distinctBy { it.uuid }, playlist))) {
                showActionMessage("Another removal is waiting for Undo. Undo it or try again in a few seconds.")
            }
        }
    }

    fun undoDelete() {
        if (removalUndo.undo()) showActionMessage("Removal undone")
    }

    private fun refreshVisibleTracks() {
        val playlist = _uiState.value.selectedPlaylist
        if (playlist != null) loadPlaylistTracks(playlist.id, silent = true)
        else loadTracks(silent = true)
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        refreshVisibleTracks()
    }

    private fun LibraryUiState.withVisibleSelection(): LibraryUiState {
        val visibleIds = tracks.map { it.uuid }.toSet()
        val selection = selectedTrackUuids.intersect(visibleIds)
        return copy(selectedTrackUuids = selection, isSelectionMode = isSelectionMode && selection.isNotEmpty())
    }

    private fun filterTracksBySearch(tracks: List<Track>, playlistId: String? = _uiState.value.selectedPlaylist?.id): List<Track> {
        val state = _uiState.value
        val visibleTracks = tracks.filterNot { state.pendingRemoval?.hides(it.uuid, playlistId) == true }
        val query = state.searchQuery.trim()
        if (query.isEmpty()) return visibleTracks

        val lowerQuery = query.lowercase()
        return visibleTracks.filter { track ->
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
                        // For now, we keep what was passed, but it might fail to load later if permission lost.
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


    /**
     * Adds a track to a playlist if it is not already present.
     *
     * Deduplication is keyed by track `uuid` via a single scan of current playlist entries
     * (O(m), where m is playlist size).
     * The method keeps playlist ordering stable (append-only) and updates `trackCount`
     * in the same flow so UI counts stay consistent.
     */
    suspend fun addToPlaylist(playlist: PlaylistEntity, track: Track) {
        addTracksToPlaylist(playlist, listOf(track))
    }

    suspend fun removeFromPlaylist(playlist: PlaylistEntity, track: Track) {
        database.withTransaction {
            playlistDao.removeTrackFromPlaylist(playlist.id, track.uuid)
            playlistDao.updatePlaylistTrackCount(playlist.id, playlistDao.getPlaylistTrackCount(playlist.id))
        }
        if (_uiState.value.selectedPlaylist?.id == playlist.id) refreshVisibleTracks()
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

    /** Membership counts let the picker distinguish partial and complete batch additions. */
    suspend fun playlistMembershipCounts(tracks: List<Track>): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        database.withTransaction {
            tracks.distinctBy { it.uuid }.forEach { track ->
                playlistDao.getPlaylistsForTrack(track.uuid).distinctBy { it.id }.forEach { playlist ->
                    counts[playlist.id] = (counts[playlist.id] ?: 0) + 1
                }
            }
        }
        return counts
    }

    /** Atomic append: skip existing songs and use persisted positions and counts. */
    suspend fun addTracksToPlaylist(playlist: PlaylistEntity, tracks: List<Track>): Int {
        val added = database.withTransaction {
            checkNotNull(playlistDao.getPlaylist(playlist.id)) { "This playlist no longer exists." }
            val existing = playlistDao.getPlaylistTracks(playlist.id).map { it.uuid }.toSet()
            val newTracks = tracks.distinctBy { it.uuid }.filterNot { it.uuid in existing }
            var position = playlistDao.getNextPlaylistPosition(playlist.id)
            playlistDao.insertPlaylistTracks(newTracks.map { track ->
                com.example.juke.database.PlaylistTrackEntity(playlist.id, track.uuid, position++)
            })
            playlistDao.updatePlaylistTrackCount(playlist.id, playlistDao.getPlaylistTrackCount(playlist.id))
            newTracks.size
        }
        if (_uiState.value.selectedPlaylist?.id == playlist.id) refreshVisibleTracks()
        return added
    }

    suspend fun createPlaylistWithTracks(name: String, tracks: List<Track>): Int = database.withTransaction {
        require(name.isNotBlank())
        val playlist = PlaylistEntity(id = java.util.UUID.randomUUID().toString(), name = name.trim())
        playlistDao.insertPlaylist(playlist)
        addTracksToPlaylist(playlist, tracks)
    }

    /**
     * Enqueues a playlist using the active library sort option so queue order matches UI intent.
     */
    fun addPlaylistToQueue(playlist: PlaylistEntity, musicViewModel: MusicViewModel) {
        viewModelScope.launch {
            var tracks = playlistDao.getPlaylistTracks(playlist.id).map { it.toTrack() }
            
            // Respect the current sort option (maintain order seen in UI)
            // Even if not currently viewing this playlist, using the global sort option is a reasonable default
            // consistent with the "maintain order" request which implies "order I expect".
            tracks = sortTracks(tracks, _uiState.value.sortOption)

            if (tracks.isNotEmpty()) {
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
                    val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
                    } ?: "Imported audio"

                    val importDir = java.io.File(context.filesDir, "imported_music")
                    if (!importDir.exists()) importDir.mkdirs()

                    // Provider names are display metadata, never paths. Keep only a safe extension.
                    val extension = fileName.substringAfterLast('.', "").takeIf {
                        it.matches(Regex("[A-Za-z0-9]{1,10}"))
                    } ?: "mp3"
                    val destFile = java.io.File(importDir, "${java.util.UUID.randomUUID()}.$extension")

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
