package com.example.juke.viewmodels

import android.app.Application
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.core.content.edit
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.juke.database.MusicDatabase
import com.example.juke.database.toEntity
import com.example.juke.database.toTrack
import com.example.juke.models.SpotdownSong
import com.example.juke.models.SpotifyAlbum
import com.example.juke.models.SpotifySimplifiedTrack
import com.example.juke.models.SpotifyTrack
import com.example.juke.models.Track
import com.example.juke.network.RecommenderApi
import com.example.juke.network.SpotifyApi
import com.example.juke.services.MusicService
import com.example.juke.services.PlaybackManager
import com.example.juke.services.QueueManager
import com.example.juke.ui.theme.ExtractedColors
import com.example.juke.utils.DatabaseMigrationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID


enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    COMPLETED,
    FAILED
}

data class DownloadItem(
    val id: String = UUID.randomUUID().toString(),
    val song: SpotdownSong,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val error: String? = null,
    val shouldPlayAfterDownload: Boolean = false
)

data class MusicUiState(
    val currentTrack: Track? = null,
    val queue: List<Track> = emptyList(),
    val queueIndex: Int = -1,
    val isPlaying: Boolean = false,
    val position: Long = 0,
    val duration: Long = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
    val downloadQueue: List<DownloadItem> = emptyList(),
    val currentDownload: DownloadItem? = null,
    val isQueueOperationInProgress: Boolean = false,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: Int = androidx.media3.common.Player.REPEAT_MODE_OFF,
    val extractedColors: ExtractedColors? = null
)

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val database = MusicDatabase.getDatabase(application)
    private val trackDao = database.trackDao()
    private val musicService = MusicService(application)
    val playbackManager = PlaybackManager.getInstance(application)
    private val queueManager = QueueManager.getInstance(application)

    private val audioPrefs = application.getSharedPreferences(
        "audio_effects_prefs",
        android.content.Context.MODE_PRIVATE
    )

    // Skip Silence State
    private val _isSkipSilenceEnabled =
        MutableStateFlow(audioPrefs.getBoolean("skip_silence_enabled", false))
    val isSkipSilenceEnabled: StateFlow<Boolean> = _isSkipSilenceEnabled.asStateFlow()

    fun toggleSkipSilence(enabled: Boolean) {
        _isSkipSilenceEnabled.value = enabled
        audioPrefs.edit().putBoolean("skip_silence_enabled", enabled).apply()
    }

    // Stream Mode State
    private val _isStreamMode = MutableStateFlow(queueManager.isStreamMode)
    val isStreamMode: StateFlow<Boolean> = _isStreamMode.asStateFlow()

    // Guard: tracks Spotify IDs (or "title-artist" keys) for which a stream is already in progress.
    // Prevents double-tapping from launching duplicate stream downloads.
    private val activeStreamRequests = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    fun toggleStreamMode(enabled: Boolean) {
        queueManager.isStreamMode = enabled
        _isStreamMode.value = enabled
    }


    private val _uiState = MutableStateFlow(MusicUiState())
    val uiState: StateFlow<MusicUiState> = _uiState.asStateFlow()

    private var isProcessingQueue = false
    private val pendingQueueOperations =
        java.util.Collections.synchronizedSet(mutableSetOf<String>())

    fun saveLyricsOffset(track: Track, offsetMs: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            trackDao.updateLyricsOffset(track.uuid, offsetMs)

            _uiState.update { state ->
                val updatedCurrentTrack = if (state.currentTrack?.uuid == track.uuid) {
                    state.currentTrack.copy(lyricsOffsetMs = offsetMs)
                } else {
                    state.currentTrack
                }

                val updatedQueue = state.queue.map { queuedTrack ->
                    if (queuedTrack.uuid == track.uuid) {
                        queuedTrack.copy(lyricsOffsetMs = offsetMs)
                    } else {
                        queuedTrack
                    }
                }

                state.copy(
                    currentTrack = updatedCurrentTrack,
                    queue = updatedQueue
                )
            }
        }
    }

    init {
        playbackManager.initialize()

        // Run database migration helper to fix download timestamps
        viewModelScope.launch {
            DatabaseMigrationHelper.fixDownloadTimestamps(application)
        }

        // Purge stale stream entries from DB, but keep stream tracks that are in the saved queue
        viewModelScope.launch(Dispatchers.IO) {
            val prefs = application.getSharedPreferences("playback_state_prefs", android.content.Context.MODE_PRIVATE)
            val savedIds = prefs.getString("queue_track_ids", "")
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?.toSet() ?: emptySet()
            musicService.purgeStaleStreamEntries(preserveUuids = savedIds)
        }

        // Observe restored state and update UI with saved queue
        viewModelScope.launch {
            playbackManager.hasRestoredState.collect { hasState ->
                if (hasState) {
                    Log.d("MusicViewModel", "Playback state restored, updating UI")
                    // Load the restored queue from PlaybackManager
                    loadRestoredQueue()

                    // No need to call checkAndFetchRecommendations() here
                    // PlaybackService.restorePlaybackState() already initialized QueueManager
                    // and it will auto-fetch recommendations if queue size <= 2
                }
            }
        }

        // Observe playback state changes coming from the MediaController (notifications/external)
        viewModelScope.launch {
            playbackManager.isPlayingFlow.collect { playing ->
                _uiState.update { it.copy(isPlaying = playing) }
            }
        }

        // Observe shuffle state changes
        viewModelScope.launch {
            playbackManager.isShuffleEnabledFlow.collect { shuffleEnabled ->
                _uiState.update { it.copy(isShuffleEnabled = shuffleEnabled) }
            }
        }

        // Observe repeat mode changes
        viewModelScope.launch {
            playbackManager.repeatModeFlow.collect { mode ->
                _uiState.update { it.copy(repeatMode = mode) }
            }
        }

        // Observe favourite changes from PlaybackManager (e.g. from Notification or other UI parts)
        viewModelScope.launch {
            playbackManager.favouriteChangedFlow.collect { (uuid, isFavourite) ->
                // Update current track if it matches
                _uiState.update { state ->
                    val updatedCurrentTrack = if (state.currentTrack?.uuid == uuid) {
                        state.currentTrack.copy(isFavourite = isFavourite)
                    } else {
                        state.currentTrack
                    }

                    // Update the track in the queue list if present
                    val updatedQueue = state.queue.map { track ->
                        if (track.uuid == uuid) {
                            track.copy(isFavourite = isFavourite)
                        } else {
                            track
                        }
                    }

                    state.copy(
                        currentTrack = updatedCurrentTrack,
                        queue = updatedQueue
                    )
                }
            }
        }


        // Observe current track changes from PlaybackManager
        viewModelScope.launch {
            playbackManager.currentTrackIdFlow.collect { trackId ->
                trackId?.let { id ->
                    Log.d("MusicViewModel", "Current track ID changed to: $id")
                    // Note: Actual UI state update is now handled by currentQueueIndexFlow
                    // to support duplicate tracks correctly.
                }
            }
        }

        // Observe current track changes to extract colors
        viewModelScope.launch {
            _uiState.map { it.currentTrack?.thumbnailUri }
                .distinctUntilChanged()
                .collect { thumbnailUri ->
                    extractColors(thumbnailUri)
                }
        }


        // Synch UI queue with PlaybackManager source of truth
        viewModelScope.launch {
            playbackManager.queueFlow.collect { queueIds ->
                if (queueIds.isEmpty()) return@collect

                withContext(Dispatchers.IO) {
                    // Optimized sync: reuse existing objects, fetch only if missing
                    val currentTrackMap = _uiState.value.queue.associateBy { it.uuid }
                    val currentQueueIds = _uiState.value.queue.map { it.uuid }

                    if (currentQueueIds != queueIds) {
                        val newQueue = queueIds.mapNotNull { id ->
                            currentTrackMap[id] ?: try {
                                trackDao.getTrackByUuid(id)?.toTrack()
                            } catch (e: Exception) {
                                null
                            }
                        }

                        _uiState.update { state ->
                            val currentIndex = state.queueIndex
                            // If index is valid in new queue, update current track because the track at this index might have changed
                            // (e.g. when the current track is deleted and the next one immediately takes its place)
                            val newCurrentTrack =
                                if (currentIndex >= 0 && currentIndex < newQueue.size) {
                                    newQueue[currentIndex]
                                } else {
                                    state.currentTrack
                                }

                            state.copy(
                                queue = newQueue,
                                currentTrack = newCurrentTrack
                            )
                        }
                        Log.d(
                            "MusicViewModel",
                            "Synced UI queue with PlaybackManager: ${newQueue.size} tracks"
                        )
                    }
                }
            }
        }

        // Observe current queue index changes from PlaybackManager
        // This is the source of truth for "what is playing" to handle duplicate tracks
        viewModelScope.launch {
            playbackManager.currentQueueIndexFlow.collect { index ->
                val currentState = _uiState.value
                val currentQueue = currentState.queue

                if (index >= 0 && index < currentQueue.size) {
                    val track = currentQueue[index]

                    // Only update if something changed
                    if (currentState.queueIndex != index || currentState.currentTrack?.uuid != track.uuid) {
                        _uiState.update {
                            it.copy(
                                currentTrack = track,
                                queueIndex = index
                            )
                        }
                        Log.d(
                            "MusicViewModel",
                            "Updated UI state - Index: $index, Track: ${track.title} (deduced from index)"
                        )

                        // Check if we need more recommendations (queue getting low)
                        val remainingTracks = currentQueue.size - index - 1
                        if (remainingTracks <= 2) {
                            Log.d(
                                "MusicViewModel",
                                "Queue low, fetching recommendations for: ${track.title}"
                            )
                            queueManager.fetchAndQueueRecommendations(track)
                        }
                    }
                } else if (currentQueue.isNotEmpty()) {
                    Log.w(
                        "MusicViewModel",
                        "Queue index $index out of bounds (size: ${currentQueue.size})"
                    )
                }
            }
        }

        // Observe recommendation queue changes
        viewModelScope.launch {
            queueManager.currentQueue.collect { recommendedTracks ->
                Log.d(
                    "MusicViewModel",
                    "QueueManager queue updated: ${recommendedTracks.size} tracks"
                )

                // Add recommended tracks to the main queue if they're not already there
                val currentQueue = _uiState.value.queue

                // If current queue is empty (e.g. app restart), sync with QueueManager but DON'T add to PlaybackManager
                // because PlaybackManager/Restoration logic is what populated QueueManager in the first place.
                if (currentQueue.isEmpty()) {
                    if (recommendedTracks.isNotEmpty()) {
                        Log.d(
                            "MusicViewModel",
                            "Syncing UI queue from QueueManager (Startup/Restoration)"
                        )
                        _uiState.update { it.copy(queue = recommendedTracks) }
                    }
                    return@collect
                }

                val newTracks = recommendedTracks.filter { recommended ->
                    !currentQueue.any { existing -> existing.uuid == recommended.uuid }
                }

                if (newTracks.isNotEmpty()) {
                    val updatedQueue = currentQueue + newTracks
                    _uiState.update { it.copy(queue = updatedQueue) }

                    // Add new tracks to the playback queue without interrupting current playback
                    playbackManager.addToQueue(newTracks)

                    Log.d(
                        "MusicViewModel",
                        "Added ${newTracks.size} recommended tracks to main queue. Total queue size: ${updatedQueue.size}"
                    )
                } else {
                    Log.d("MusicViewModel", "No new tracks to add from recommendations")
                }
            }
        }

        // Observe downloading tracks from recommendations
        viewModelScope.launch {
            queueManager.downloadingTracks.collect { downloading ->
                Log.d(
                    "MusicViewModel",
                    "Recommendation downloads in progress: ${downloading.size} tracks"
                )
            }
        }
    }

    private fun extractColors(thumbnailUri: String?) {
        if (thumbnailUri == null) {
            _uiState.update { it.copy(extractedColors = null) }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val loader = ImageLoader(getApplication())
                val request = ImageRequest.Builder(getApplication())
                    .data(thumbnailUri)
                    .allowHardware(false) // Palette needs software bitmap
                    .build()

                val result = (loader.execute(request) as? SuccessResult)?.drawable
                val bitmap = (result as? android.graphics.drawable.BitmapDrawable)?.bitmap

                if (bitmap != null) {
                    val palette = Palette.from(bitmap).generate()
                    // Robust color extraction with fallbacks
                    val vibrant = palette.vibrantSwatch
                    val lightVibrant = palette.lightVibrantSwatch
                    val darkVibrant = palette.darkVibrantSwatch
                    val dominant = palette.dominantSwatch
                    val muted = palette.mutedSwatch

                    // Primary color priority: Vibrant -> Light Vibrant -> Dark Vibrant -> Dominant -> Muted -> Default Purple
                    val primaryInt = vibrant?.rgb
                        ?: lightVibrant?.rgb
                        ?: darkVibrant?.rgb
                        ?: dominant?.rgb
                        ?: muted?.rgb
                        ?: 0xFF6650a4.toInt()

                    // Ensure primary has enough luminance to be visible against a dark background
                    val finalPrimaryInt = if (ColorUtils.calculateLuminance(primaryInt) < 0.15) {
                        lightVibrant?.rgb
                            ?: vibrant?.rgb
                            ?: palette.lightMutedSwatch?.rgb
                            ?: 0xFF6650a4.toInt()
                    } else {
                        primaryInt
                    }
                    val finalOnPrimary = if (ColorUtils.calculateLuminance(finalPrimaryInt) > 0.35) {
                        android.graphics.Color.BLACK
                    } else {
                        vibrant?.bodyTextColor ?: android.graphics.Color.WHITE
                    }

                    // Secondary color priority: Dark Vibrant -> Muted -> Dark Muted -> Dominant -> Default
                    val secondaryInt = darkVibrant?.rgb
                        ?: muted?.rgb
                        ?: palette.darkMutedSwatch?.rgb
                        ?: dominant?.rgb
                        ?: 0xFF625b71.toInt()

                    // Tertiary color priority: Light Vibrant -> Light Muted -> Dominant -> Default
                    val tertiaryInt = lightVibrant?.rgb
                        ?: palette.lightMutedSwatch?.rgb
                        ?: dominant?.rgb
                        ?: 0xFF7D5260.toInt()

                    val extracted = ExtractedColors(
                        primary = Color(finalPrimaryInt),
                        secondary = Color(secondaryInt),
                        tertiary = Color(tertiaryInt),
                        background = Color.Black,
                        surface = Color.Black,
                        onPrimary = Color(finalOnPrimary),
                        onSecondary = Color.White,
                        onTertiary = Color.White,
                        onBackground = Color.White,
                        onSurface = Color.White
                    )
                    _uiState.update { it.copy(extractedColors = extracted) }
                } else {
                    _uiState.update { it.copy(extractedColors = null) }
                }
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Failed to extract colors", e)
                _uiState.update { it.copy(extractedColors = null) }
            }
        }
    }


    fun playTrack(track: Track) {
        viewModelScope.launch {
            // Set up the queue with the current track
            _uiState.update {
                it.copy(
                    currentTrack = track,
                    queue = listOf(track), // Initialize queue with current track
                    queueIndex = 0, // Current track is at index 0
                    isPlaying = true,
                    duration = track.durationSec.toLong() * 1000
                )
            }

            // Set the queue in the playback manager (starts playing automatically)
            playbackManager.setQueue(listOf(track), 0)

            // Initialize recommendation queue for this track
            // This automatically cancels any pending recommendations from the previous song
            // and starts fetching fresh recommendations for the new track
            Log.d("MusicViewModel", "Playing track: ${track.title}, initializing recommendations")
            queueManager.initializeQueue(listOf(track))
        }
    }

    fun startRadio() {
        val current = _uiState.value.currentTrack ?: return
        viewModelScope.launch {
            Log.d("MusicViewModel", "Starting radio for: ${current.title}")

            // 1. Remove all other tracks from playback queue to avoid interrupting current song
            playbackManager.keepOnlyCurrentTrack()

            // 2. Clear QueueManager and re-initialize with just this song
            // This triggers the recommendation fetch
            queueManager.initializeQueue(listOf(current))

            // 3. Update UI state immediately
            _uiState.update {
                it.copy(
                    queue = listOf(current),
                    queueIndex = 0
                )
            }
        }
    }

    fun playTrackFromQueue(track: Track) {
        viewModelScope.launch {
            val currentState = _uiState.value
            val queue = currentState.queue

            // Find the track index in the current queue
            val trackIndex = queue.indexOfFirst { it.uuid == track.uuid }
            if (trackIndex == -1) {
                Log.w("MusicViewModel", "Track ${track.title} not found in current queue")
                return@launch
            }

            // Seek to the track in the playback manager
            playbackManager.seekToIndex(trackIndex)

            // Update UI state to reflect the new current track
            _uiState.update {
                it.copy(
                    currentTrack = track,
                    queueIndex = trackIndex,
                    isPlaying = true,
                    duration = track.durationSec.toLong() * 1000
                )
            }

            Log.d("MusicViewModel", "Playing track from queue: ${track.title} at index $trackIndex")

            // Sync QueueManager to the new position while preserving session history
            val remainingTracks = queue.drop(trackIndex)
            val historyTracks = queue.take(trackIndex)
            if (remainingTracks.isNotEmpty()) {
                queueManager.updateHistory(historyTracks)
                queueManager.initializeQueue(remainingTracks, isRadioMode = false, preserveHistory = true)
            }
        }
    }

    fun setQueue(tracks: List<Track>, startIndex: Int = 0) {
        viewModelScope.launch {
            playbackManager.setQueue(tracks, startIndex)
            _uiState.update {
                it.copy(
                    queue = tracks,
                    queueIndex = startIndex,
                    currentTrack = tracks.getOrNull(startIndex),
                    isPlaying = true
                )
            }

            // Initialize recommendation queue - check if we need more tracks
            if (tracks.size <= 2) {
                val currentTrack = tracks.getOrNull(startIndex)
                currentTrack?.let {
                    Log.d(
                        "MusicViewModel",
                        "Queue size ${tracks.size}, initializing recommendations for: ${it.title}"
                    )
                    // Initialize recommendations with correct history for ensemble context
                    val remainingTracks = tracks.drop(startIndex)
                    val historyTracks = tracks.take(startIndex)
                    if (remainingTracks.isNotEmpty()) {
                        queueManager.updateHistory(historyTracks)
                        queueManager.initializeQueue(remainingTracks, isRadioMode = false, preserveHistory = true)
                    }
                }
            }
        }
    }

    fun togglePlayPause() {
        playbackManager.togglePlayPause()
        // rely on playbackManager.isPlayingFlow to update UI via collector
    }

    fun toggleShuffle() {
        playbackManager.toggleShuffle()
        // rely on playbackManager.isShuffleEnabledFlow to update UI via collector
    }

    fun toggleRepeat() {
        playbackManager.toggleRepeatMode()
    }


    /**
     * Insert a track so it plays immediately after the current track.
     */
    fun addNext(track: Track) {
        addNext(listOf(track))
    }

    /**
     * Insert a list of tracks so they play immediately after the current track.
     *
     * Incoming tracks are deduplicated by `uuid` against the existing queue (except the
     * currently playing item), using a set-based filter to avoid repeated linear scans.
     */
    fun addNext(tracks: List<Track>) {
        if (tracks.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isQueueOperationInProgress = true) }

            try {
                val currentState = _uiState.value
                val currentQueue = currentState.queue.toMutableList()
                val currentTrack = currentState.currentTrack
                val currentPosition = playbackManager.getCurrentPosition()

                // Maintain single entry per track UUID while preserving the currently playing track
                if (tracks.isNotEmpty()) {
                    val incomingUuids = tracks.map { it.uuid }.toSet()
                    currentQueue.removeAll { item ->
                        incomingUuids.contains(item.uuid) && item.uuid != currentTrack?.uuid
                    }
                }

                if (currentQueue.isEmpty() || currentState.queueIndex < 0) {
                    // Nothing playing yet; start a queue with these tracks
                    setQueue(tracks, 0)
                } else {
                    val currentQueueIndex = currentState.queueIndex

                    // Simple insert for batch to avoid complex index shifting with moves
                    // We just insert them right after current

                    val insertIndex = (currentQueueIndex + 1).coerceAtMost(currentQueue.size)
                    currentQueue.addAll(insertIndex, tracks)

                    Log.d(
                        "MusicViewModel",
                        "Inserting ${tracks.size} tracks at index $insertIndex"
                    )

                    val inserted = playbackManager.addToQueueAt(tracks, insertIndex)
                    if (inserted) {
                        // Also insert into QueueManager if it's within the range it cares about
                        val queueManagerIndex = insertIndex - currentQueueIndex
                        if (queueManagerIndex >= 0) {
                            // QueueManager might not support batch insert yet? 
                            // It does not seem to have batch insert based on previous reads, but we can loop.
                            // Actually QueueManager logic in addNext(Track) calls insertQueueItem.
                            // We should probably add batch support there too or loop. 
                            // looping is fine for small batches.
                            tracks.forEachIndexed { i, track ->
                                queueManager.insertQueueItem(queueManagerIndex + i, track)
                            }
                        }
                    } else {
                        // Fallback: reset full queue to keep UI and player in sync.
                        // keepShuffleMode=false: ExoPlayer's native shuffle is always off;
                        // shuffle ordering is handled by pre-shuffling before calling setQueue.
                        playbackManager.setQueue(
                            currentQueue,
                            currentState.queueIndex,
                            currentPosition,
                            keepShuffleMode = false
                        )
                    }

                    _uiState.update {
                        it.copy(
                            queue = currentQueue
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Failed to add next batch: ${e.message}", e)
            } finally {
                _uiState.update { it.copy(isQueueOperationInProgress = false) }
            }
        }
    }

    /**
     * Appends tracks to the queue while preserving current playback position.
     *
     * Incoming tracks are de-duplicated by UUID and the currently playing item is retained
     * exactly once to avoid playback jumps after queue mutation.
     * Deduplication uses a UUID set (`trackUuidsToAdd`) so queue cleanup is O(q + n)
     * rather than repeated O(q * n) membership checks (q queue size, n incoming size).
     */
    fun addToQueue(tracks: List<Track>) {
        if (tracks.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isQueueOperationInProgress = true) }
            try {
                // Update local UI state
                val currentState = _uiState.value
                val currentQueue = currentState.queue.toMutableList()
                val currentTrack = currentState.currentTrack
                val currentTrackUuid = currentTrack?.uuid

                // Get current position before modification to maintain playback continuity
                val currentPosition = playbackManager.getCurrentPosition()
                // Use current index from simple calculation or reliable flow source if needed
                // But since we are modifying structure, we must rely on UUID to find playing track location

                // 1. Filter out the currently playing track from the incoming list
                val tracksToAdd = tracks.filter { track ->
                    currentTrackUuid == null || track.uuid != currentTrackUuid
                }

                if (tracksToAdd.isEmpty()) return@launch

                // 2. Remove existing instances of these tracks from the current queue
                // User requirement: "keep single entry of each song uuid not repetation"
                val trackUuidsToAdd = tracksToAdd.map { it.uuid }.toSet()
                currentQueue.removeAll { it.uuid in trackUuidsToAdd }

                // 3. Add the tracks to the end of the queue
                currentQueue.addAll(tracksToAdd)

                // 4. Update UI State immediately
                _uiState.update { it.copy(queue = currentQueue) }

                // 5. Update PlaybackManager
                // Calculate new index of the currently playing track in the modified queue
                val newIndex = if (currentTrackUuid != null) {
                    val index = currentQueue.indexOfFirst { it.uuid == currentTrackUuid }
                    if (index != -1) index else currentState.queueIndex.coerceIn(
                        0,
                        currentQueue.size.coerceAtLeast(1) - 1
                    )
                } else {
                    currentState.queueIndex
                }

                // Use setQueue with explicit position maintenance to prevent restarts or random jumps.
                // keepShuffleMode=false: ExoPlayer's native shuffle is always off;
                // shuffle ordering is handled by pre-shuffling before calling setQueue.
                playbackManager.setQueue(
                    currentQueue,
                    newIndex,
                    currentPosition,
                    keepShuffleMode = false
                )

            } catch (e: Exception) {
                Log.e("MusicViewModel", "Failed to add to queue batch: ${e.message}", e)
            } finally {
                _uiState.update { it.copy(isQueueOperationInProgress = false) }
            }
        }
    }

    private suspend fun resolveQueueTrack(spotdownSong: SpotdownSong, useStreamMode: Boolean): Track {
        return if (useStreamMode) {
            Log.d(
                "MusicViewModel",
                "Stream mode enabled, queueing stream track: ${spotdownSong.title}"
            )

            // Keep current queue entries pinned so stream cache eviction does not remove
            // files that are about to be played.
            val pinnedUuids = _uiState.value.queue.map { it.uuid }.toSet()
            musicService.streamTrack(
                song = spotdownSong,
                pinnedUuids = pinnedUuids
            ).also { streamedTrack ->
                trackDao.insertTrack(streamedTrack.toEntity())
            }
        } else {
            Log.d(
                "MusicViewModel",
                "Stream mode disabled, queueing permanent download: ${spotdownSong.title}"
            )
            musicService.smartDownloadAndIndex(spotdownSong)
        }
    }

    /**
     * Queue a Spotify track to play next.
     * Honors stream mode immediately at the time of request.
     *
     * Duplicate suppression key: `"title-artist"` (lower-level request coalescing while
     * operations are in-flight), plus active download-state checks.
     */
    fun queueSpotifyTrackNext(
        spotifyTrack: SpotifyTrack,
        useStreamMode: Boolean = queueManager.isStreamMode
    ) {
        val spotdownSong = SpotifyApi.spotifyTrackToSong(spotifyTrack)
        val key = "${spotdownSong.title}-${spotdownSong.artist}"

        // Check pending operations
        if (pendingQueueOperations.contains(key)) {
            Log.d("MusicViewModel", "Ignoring duplicate queue request for: $key")
            return
        }

        // Check active download queue
        val currentState = _uiState.value
        val alreadyDownloading = currentState.downloadQueue.any {
            it.song.title == spotdownSong.title && it.song.artist == spotdownSong.artist
        } || (currentState.currentDownload?.song?.title == spotdownSong.title &&
                currentState.currentDownload?.song?.artist == spotdownSong.artist)

        if (alreadyDownloading) {
            Log.d("MusicViewModel", "Song already downloading (queueSpotifyTrackNext): $key")
            return
        }

        viewModelScope.launch {
            pendingQueueOperations.add(key)
            _uiState.update { it.copy(isQueueOperationInProgress = true) }

            try {
                // notify QueueManager to prevent double download
                val trackToNotify = Track(
                    uuid = UUID.randomUUID().toString(),
                    title = spotdownSong.title,
                    artist = spotdownSong.artist,
                    localUri = null,
                    durationSec = 0
                )
                queueManager.notifyDownloadStarted(trackToNotify)

                val track = withContext(Dispatchers.IO) {
                    resolveQueueTrack(spotdownSong, useStreamMode)
                }
                addNext(track)
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Failed to queue Spotify track next: ${e.message}", e)
                _uiState.update { it.copy(isQueueOperationInProgress = false) }
            } finally {
                queueManager.removeDownloadTracking(spotdownSong.title, spotdownSong.artist)
                pendingQueueOperations.remove(key)
                _uiState.update { it.copy(isQueueOperationInProgress = false) }
            }
        }
    }

    /**
     * Play a Spotify track instantly (Stream) and download in background.
     */
    fun playInstant(spotifyTrack: SpotifyTrack) {
        val spotdownSong = SpotifyApi.spotifyTrackToSong(spotifyTrack)
        playInstant(spotdownSong)
    }

    /**
     * Overload for SpotdownSong (used by Search/Artist/Album screens via downloadAndPlay)
     */
    fun playInstant(song: SpotdownSong) {
        // De-duplicate: if a stream request is already live for this song, ignore the tap.
        val requestKey = song.spotifyId?.takeIf { it.isNotBlank() }
            ?: "${song.title.lowercase().trim()}-${song.artist.lowercase().trim()}"
        if (!activeStreamRequests.add(requestKey)) {
            Log.d("MusicViewModel", "playInstant ignored — already in progress for: ${song.title}")
            return
        }

        viewModelScope.launch {
            try {
                // 1. Check if already downloaded
                val durationSec = SpotifyApi.parseDuration(song.duration)
                val candidates = trackDao.findTracksByTitleAndDuration(song.title, durationSec)
                val existingTrack = candidates.find {
                    com.example.juke.utils.ArtistUtils.areArtistsEqual(it.artist, song.artist)
                }

                if (existingTrack != null && existingTrack.localUri != null) {
                    Log.d(
                        "MusicViewModel",
                        "Track exists locally, playing from storage: ${song.title}"
                    )
                    playTrack(existingTrack.toTrack())
                    return@launch
                }

                // 2. Not downloaded -> Stream instant via Spotdown-backed local stream file.
                // For the first stream triggered from search, always use Spotmate (faster response).
                Log.d(
                    "MusicViewModel",
                    "Track not local, starting instant stream (Spotmate-first): ${song.title}"
                )
                _uiState.update { it.copy(isLoading = true) }

                try {
                    val tempTrack = withContext(Dispatchers.IO) {
                        musicService.streamTrack(
                            song,
                            forceSpotmateFirst = true,
                            fetchLyricsSynchronously = false,
                            fetchYtVideoIdSynchronously = false
                        ).also {
                            trackDao.insertTrack(it.toEntity())
                        }
                    }

                    // Play immediately
                    playTrack(tempTrack)

                    // Hydrate lyrics off the critical playback path.
                    if (tempTrack.syncedLyrics.isNullOrBlank() && tempTrack.plainLyrics.isNullOrBlank()) {
                        refreshLyrics(tempTrack)
                    }

                    // Hydrate YT video ID off the critical playback path.
                    if (tempTrack.ytVideoId.isNullOrBlank()) {
                        refreshYtVideoId(tempTrack)
                    }

                    _uiState.update { it.copy(isLoading = false) }

                    // Notify QueueManager so it doesn't try to recommend/download this
                    // Stream tracks are NOW saved to database — they exist in the
                    // playback queue and LRU disk cache until explicitly promoted to download.
                    queueManager.notifyDownloadStarted(tempTrack, addToUi = false)

                    Log.d("MusicViewModel", "Spotdown stream prepared and playing: ${song.title}")

                } catch (e: Exception) {
                    Log.e("MusicViewModel", "Instant play failed (stream fetch): ${e.message}", e)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Failed to stream: ${e.message}"
                        )
                    }

                    // Fallback: Queue normal download (will play if user waits or clicks again)
                    addToDownloadQueue(song, shouldPlayAfterDownload = true)
                }

            } catch (e: Exception) {
                Log.e("MusicViewModel", "Instant play failed: ${e.message}", e)
                _uiState.update { it.copy(isLoading = false) }
            } finally {
                // Always release the guard so the user can retry after a failure
                activeStreamRequests.remove(requestKey)
            }
        }
    }

    /**
     * Download a simplified Spotify track (album context) and queue it to play next.
     *
     * Duplicate suppression key: `"title-artist"` while the operation is pending, with
     * additional checks against active download state to avoid parallel duplicates.
     */
    fun queueSimplifiedTrackNext(track: SpotifySimplifiedTrack, album: SpotifyAlbum) {
        val spotdownSong = SpotifyApi.simplifiedTrackToSong(track, album)
        val key = "${spotdownSong.title}-${spotdownSong.artist}"

        if (pendingQueueOperations.contains(key)) {
            Log.d("MusicViewModel", "Ignoring duplicate queue request for: $key")
            return
        }

        // Check active download queue
        val currentState = _uiState.value
        val alreadyDownloading = currentState.downloadQueue.any {
            it.song.title == spotdownSong.title && it.song.artist == spotdownSong.artist
        } || (currentState.currentDownload?.song?.title == spotdownSong.title &&
                currentState.currentDownload?.song?.artist == spotdownSong.artist)

        if (alreadyDownloading) {
            Log.d("MusicViewModel", "Song already downloading (queueSimplifiedTrackNext): $key")
            return
        }

        viewModelScope.launch {
            pendingQueueOperations.add(key)
            _uiState.update { it.copy(isQueueOperationInProgress = true) }

            try {
                // Skip download if already in library with a valid local file
                val existing = withContext(Dispatchers.IO) {
                    val durationSec = track.durationMs / 1000
                    trackDao.findTracksByTitleAndDuration(spotdownSong.title, durationSec)
                        .find {
                            val localPath = it.localUri
                            com.example.juke.utils.ArtistUtils.areArtistsEqual(
                                it.artist,
                                spotdownSong.artist
                            ) &&
                                    localPath != null &&
                                    !localPath.startsWith("http", ignoreCase = true) &&
                                    File(localPath).exists()
                        }
                        ?.toTrack()
                }

                if (existing != null) {
                    Log.d(
                        "MusicViewModel",
                        "Album track already downloaded, inserting without re-download: ${existing.title}"
                    )
                    addNext(existing)
                    return@launch
                }

                // spotdownSong created above
                val trackToQueue = withContext(Dispatchers.IO) {
                    // Notify QueueManager to prevent duplicate processing.
                    queueManager.notifyDownloadStarted(
                        Track(
                            uuid = UUID.randomUUID().toString(),
                            title = spotdownSong.title,
                            artist = spotdownSong.artist,
                            localUri = null,
                            durationSec = 0
                        )
                    )
                    resolveQueueTrack(
                        spotdownSong,
                        useStreamMode = queueManager.isStreamMode
                    )
                }
                addNext(trackToQueue)
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Failed to queue simplified track next: ${e.message}", e)
            } finally {
                queueManager.removeDownloadTracking(spotdownSong.title, spotdownSong.artist)
                pendingQueueOperations.remove(key)
                _uiState.update { it.copy(isQueueOperationInProgress = false) }
            }
        }
    }

    fun skipToNext() {
        playbackManager.skipToNext()
        // UI state will be updated automatically via currentTrackIdFlow
    }

    fun skipToPrevious() {
        playbackManager.skipToPrevious()
        // UI state will be updated automatically via currentTrackIdFlow
    }

    fun seekTo(positionMs: Long) {
        playbackManager.seekTo(positionMs)
        _uiState.update { it.copy(position = positionMs) }
    }

    fun updateProgress() {
        val currentPos = playbackManager.getCurrentPosition()
        val durationMs = playbackManager.getDuration()
        _uiState.update { state ->
            state.copy(
                position = currentPos,
                duration = if (durationMs > 0) durationMs else state.duration
            )
        }
    }

    suspend fun downloadAndPlay(song: SpotdownSong) {
        // Redirect to instant play logic
        playInstant(song)
    }

    suspend fun downloadSong(song: SpotdownSong): Track {
        // Check if already exists
        val durationSec = SpotifyApi.parseDuration(song.duration)
        val candidates = trackDao.findTracksByTitleAndDuration(song.title, durationSec)
        val existingTrack = candidates.find {
            com.example.juke.utils.ArtistUtils.areArtistsEqual(it.artist, song.artist)
        }

        return if (existingTrack != null && existingTrack.localUri != null) {
            // Already downloaded
            existingTrack.toTrack()
        } else {
            // Download directly
            musicService.smartDownloadAndIndex(song)
        }
    }

    /**
     * Schedules a song for download with de-duplication against active and pending work.
     *
     * Duplicate suppression key: `"title-artist"` for in-memory queue/download tracking.
     * The method intentionally re-checks queue/download state after DB lookup to reduce
     * race-condition duplicates between concurrent requests.
     * If the track already exists locally, download is skipped and optional immediate playback
     * is handled without queuing a redundant job.
     */
    fun addToDownloadQueue(song: SpotdownSong, shouldPlayAfterDownload: Boolean = false) {
        viewModelScope.launch {
            // Check if already in queue or downloading
            val currentState = _uiState.value
            val alreadyQueued = currentState.downloadQueue.any {
                it.song.title == song.title && it.song.artist == song.artist
            }
            val currentlyDownloading = currentState.currentDownload?.let {
                it.song.title == song.title && it.song.artist == song.artist
            } ?: false

            if (alreadyQueued || currentlyDownloading) {
                Log.d("MusicViewModel", "Song already in queue or downloading: ${song.title}")
                return@launch
            }

            // Check pending operations from other paths
            val key = "${song.title}-${song.artist}"
            if (pendingQueueOperations.contains(key)) {
                Log.d("MusicViewModel", "Song overlap with pending operation: ${song.title}")
                return@launch
            }

            // Check if already exists in database
            val durationSec = SpotifyApi.parseDuration(song.duration)
            val candidates = trackDao.findTracksByTitleAndDuration(song.title, durationSec)
            val existingTrack = candidates.find {
                com.example.juke.utils.ArtistUtils.areArtistsEqual(it.artist, song.artist)
            }
            if (existingTrack != null && existingTrack.localUri != null) {
                Log.d("MusicViewModel", "Song already downloaded: ${song.title}")
                if (shouldPlayAfterDownload) {
                    playTrack(existingTrack.toTrack())
                }
                return@launch
            }

            // Re-check if already in queue or downloading (Race condition fix)
            val updatedState = _uiState.value
            val alreadyQueuedRecheck = updatedState.downloadQueue.any {
                it.song.title == song.title && it.song.artist == song.artist
            }
            val currentlyDownloadingRecheck = updatedState.currentDownload?.let {
                it.song.title == song.title && it.song.artist == song.artist
            } ?: false

            if (alreadyQueuedRecheck || currentlyDownloadingRecheck) {
                Log.d("MusicViewModel", "Song appeared in queue during DB check: ${song.title}")
                return@launch
            }

            // Re-check pending operations
            if (pendingQueueOperations.contains(key)) {
                Log.d(
                    "MusicViewModel",
                    "Song overlap with pending operation during DB check: ${song.title}"
                )
                return@launch
            }

            val downloadItem = DownloadItem(
                song = song,
                status = DownloadStatus.QUEUED,
                shouldPlayAfterDownload = shouldPlayAfterDownload
            )

            _uiState.update { state ->
                state.copy(
                    downloadQueue = state.downloadQueue + downloadItem
                )
            }

            Log.d(
                "MusicViewModel",
                "Added to queue: ${song.title} (Queue size: ${_uiState.value.downloadQueue.size})"
            )

            processDownloadQueue()
        }
    }

    private fun processDownloadQueue() {
        if (isProcessingQueue) {
            Log.d("MusicViewModel", "Already processing queue")
            return
        }

        viewModelScope.launch {
            isProcessingQueue = true

            while (_uiState.value.downloadQueue.isNotEmpty()) {
                val nextItem = _uiState.value.downloadQueue.first()

                // Move from queue to current download
                _uiState.update { state ->
                    state.copy(
                        downloadQueue = state.downloadQueue.drop(1),
                        currentDownload = nextItem.copy(status = DownloadStatus.DOWNLOADING)
                    )
                }

                // Add to QueueManager tracking
                queueManager.addDownloadTracking(
                    nextItem.song.title,
                    nextItem.song.artist,
                    "manual"
                )

                Log.d("MusicViewModel", "Starting download: ${nextItem.song.title}")

                try {
                    val track = musicService.smartDownloadAndIndex(nextItem.song)

                    // Remove from QueueManager tracking
                    queueManager.removeDownloadTracking(
                        nextItem.song.title,
                        nextItem.song.artist
                    )

                    // Download successful
                    _uiState.update { state ->
                        state.copy(
                            currentDownload = nextItem.copy(status = DownloadStatus.COMPLETED)
                        )
                    }

                    Log.d("MusicViewModel", "Download completed: ${nextItem.song.title}")

                    // Check if this track is currently in the queue (streaming version)
                    // Since we preserve UUIDs, the track object already has the correct UUID
                    _uiState.value.currentTrack
                    val isInQueue = _uiState.value.queue.any { it.uuid == track.uuid }

                    if (isInQueue) {
                        Log.d(
                            "MusicViewModel",
                            "Downloaded track is in queue, updating UI and playback: ${track.title}"
                        )

                        // Update UI queue with downloaded version
                        val updatedQueue = _uiState.value.queue.map {
                            if (it.uuid == track.uuid) track else it
                        }
                        _uiState.update { state ->
                            state.copy(
                                queue = updatedQueue,
                                currentTrack = if (state.currentTrack?.uuid == track.uuid) track else state.currentTrack
                            )
                        }

                        // Update playback manager queue with new local file path
                        playbackManager.replaceTrackInQueue(track.uuid, track, seamlessIfPlaying = true)
                    }

                    // Play if requested
                    if (nextItem.shouldPlayAfterDownload) {
                        playTrack(track)
                    }

                    // Clear current download after a brief delay
                    kotlinx.coroutines.delay(1000)
                    _uiState.update { state ->
                        state.copy(currentDownload = null)
                    }

                } catch (e: Exception) {
                    Log.e(
                        "MusicViewModel",
                        "Download failed: ${nextItem.song.title} - ${e.message}"
                    )

                    // Remove from QueueManager tracking on error
                    queueManager.removeDownloadTracking(
                        nextItem.song.title,
                        nextItem.song.artist
                    )

                    // Mark as failed
                    _uiState.update { state ->
                        state.copy(
                            currentDownload = nextItem.copy(
                                status = DownloadStatus.FAILED,
                                error = e.message
                            )
                        )
                    }

                    // Clear failed download after delay
                    kotlinx.coroutines.delay(3000)
                    _uiState.update { state ->
                        state.copy(currentDownload = null)
                    }
                }
            }

            isProcessingQueue = false
            Log.d("MusicViewModel", "Queue processing completed")
        }
    }

    fun cancelDownload(downloadId: String) {
        _uiState.update { state ->
            state.copy(
                downloadQueue = state.downloadQueue.filter { it.id != downloadId }
            )
        }
    }

    fun retryFailedDownload(downloadItem: DownloadItem) {
        viewModelScope.launch {
            // Reset the download item status and add back to queue
            val resetItem = downloadItem.copy(
                status = DownloadStatus.QUEUED,
                error = null
            )

            _uiState.update { state ->
                state.copy(
                    downloadQueue = listOf(resetItem) + state.downloadQueue
                )
            }

            Log.d("MusicViewModel", "Retrying failed download: ${downloadItem.song.title}")

            // Process the queue to start the retry
            processDownloadQueue()
        }
    }

    fun removeFromQueue(trackId: String) {
        viewModelScope.launch {
            // Set loading state
            _uiState.update { it.copy(isQueueOperationInProgress = true) }

            val currentState = _uiState.value
            val currentQueue = currentState.queue
            val currentIndex = currentState.queueIndex

            // Find the track to remove
            val trackIndex = currentQueue.indexOfFirst { it.uuid == trackId }
            if (trackIndex == -1) {
                _uiState.update { it.copy(isQueueOperationInProgress = false) }
                return@launch
            }

            // Remove from playback queue first
            val playbackResult = playbackManager.removeFromQueue(trackId)

            if (playbackResult) {
                // Keep recommendation queue in sync so deleted tracks don't reappear
                queueManager.removeFromQueue(trackId)

                // Remove from UI queue
                val newQueue = currentQueue.toMutableList().apply { removeAt(trackIndex) }

                // Calculate new queue index
                val newQueueIndex = when {
                    trackIndex < currentIndex -> currentIndex - 1 // Track before current, shift index down
                    trackIndex == currentIndex -> currentIndex // Removing current track, keep same index (will be next track)
                    else -> currentIndex // Track after current, index unchanged
                }.coerceIn(0, newQueue.size - 1)

                // Update UI state
                _uiState.update {
                    it.copy(
                        queue = newQueue,
                        queueIndex = newQueueIndex,
                        currentTrack = newQueue.getOrNull(newQueueIndex),
                        isQueueOperationInProgress = false
                    )
                }

                Log.d("MusicViewModel", "Removed track $trackId from queue")
            } else {
                _uiState.update { it.copy(isQueueOperationInProgress = false) }
                Log.w("MusicViewModel", "Failed to remove track $trackId from playback queue")
            }
        }
    }

    fun moveInQueue(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            // Set loading state
            _uiState.update { it.copy(isQueueOperationInProgress = true) }

            val currentState = _uiState.value
            val currentQueue = currentState.queue
            val currentQueueIndex = currentState.queueIndex

            if (fromIndex < 0 || fromIndex >= currentQueue.size ||
                toIndex < 0 || toIndex >= currentQueue.size
            ) {
                _uiState.update { it.copy(isQueueOperationInProgress = false) }
                return@launch
            }

            // Move in playback queue first
            val playbackResult = playbackManager.moveInQueue(fromIndex, toIndex)

            if (playbackResult) {
                // Create new queue with item moved
                val newQueue = currentQueue.toMutableList().apply {
                    val item = removeAt(fromIndex)
                    add(toIndex, item)
                }

                // Calculate new queue index
                var newQueueIndex = currentQueueIndex
                if (fromIndex == currentQueueIndex) {
                    newQueueIndex = toIndex
                } else if (currentQueueIndex in (fromIndex + 1)..toIndex) {
                    newQueueIndex = currentQueueIndex - 1
                } else if (currentQueueIndex in toIndex..<fromIndex) {
                    newQueueIndex = currentQueueIndex + 1
                }

                // Update UI state
                _uiState.update {
                    it.copy(
                        queue = newQueue,
                        queueIndex = newQueueIndex,
                        currentTrack = newQueue.getOrNull(newQueueIndex),
                        isQueueOperationInProgress = false
                    )
                }

                // Sync with QueueManager
                // Pass only relevant future tracks to avoid desync
                val queueManagerTracks = newQueue.drop(newQueueIndex)
                if (queueManagerTracks.isNotEmpty()) {
                    queueManager.initializeQueue(queueManagerTracks)
                }

                Log.d("MusicViewModel", "Moved track from index $fromIndex to $toIndex")
            } else {
                _uiState.update { it.copy(isQueueOperationInProgress = false) }
                Log.w("MusicViewModel", "Failed to move track in playback queue")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        playbackManager.release()
        queueManager.cleanup()
    }

    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            val newStatus = !track.isFavourite
            trackDao.updateTrackFavourite(track.uuid, newStatus)

            // Emit to PlaybackManager so everyone stays in sync (including ourselves via the flow above)
            playbackManager.emitFavouriteChanged(track.uuid, newStatus)

            // We don't need to manually update _uiState here anymore because 
            // the collector above will handle it for both local and remote changes.
        }
    }

    // Sleep Timer
    val sleepTimerRemaining = playbackManager.sleepTimerRemaining

    fun startSleepTimer(minutes: Int) {
        playbackManager.startSleepTimer(minutes)
    }

    fun cancelSleepTimer() {
        playbackManager.cancelSleepTimer()
    }


    fun refreshLyrics(track: Track) {
        viewModelScope.launch {
            Log.d("MusicViewModel", "Refreshing lyrics for: ${track.title}")
            try {
                // Fetch lyrics from LRCLib
                val result = withContext(Dispatchers.IO) {
                    SpotifyApi.searchLyrics(
                        title = track.title,
                        artist = track.artist,
                        duration = track.durationSec,
                        ytVideoId = track.ytVideoId
                    )
                }

                if (result != null) {
                    Log.d("MusicViewModel", "New lyrics found for: ${track.title}")

                    // Update track with new lyrics
                    val updatedTrack = track.copy(
                        syncedLyrics = result.syncedLyrics,
                        plainLyrics = result.plainLyrics
                    )

                    // Update database
                    trackDao.insertTrack(updatedTrack.toEntity())

                    // Update UI State (Current Track + Queue)
                    _uiState.update { state ->
                        val updatedQueue = state.queue.map {
                            if (it.uuid == track.uuid) updatedTrack else it
                        }

                        state.copy(
                            queue = updatedQueue,
                            currentTrack = if (state.currentTrack?.uuid == track.uuid) updatedTrack else state.currentTrack
                        )
                    }

                    // No need to explicitly update PlaybackManager queue as it's primarily used for playback context
                    // and doesn't display lyrics. The UI observes currentTrackId and pulls from UI queue.

                } else {
                    Log.d("MusicViewModel", "No lyrics found for: ${track.title}")
                }
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Failed to refresh lyrics: ${e.message}", e)
            }
        }
    }

    fun refreshYtVideoId(track: Track) {
        if (!track.ytVideoId.isNullOrBlank()) return

        viewModelScope.launch {
            Log.d("MusicViewModel", "Async fetching YT video ID for: ${track.title}")
            try {
                val ytVideoId = withContext(Dispatchers.IO) {
                    RecommenderApi.getBestVideoMatch("${track.title} ${track.artist}")
                }

                if (ytVideoId.isNullOrBlank()) {
                    Log.d("MusicViewModel", "No YT video ID found for: ${track.title}")
                    return@launch
                }

                Log.d("MusicViewModel", "New YT video ID found for: ${track.title} -> $ytVideoId")

                // Persist with freshest DB snapshot to avoid overwriting newly hydrated metadata.
                val persistedTrack = withContext(Dispatchers.IO) {
                    val latest = trackDao.getTrackByUuid(track.uuid)?.toTrack() ?: track
                    val updated = latest.copy(ytVideoId = ytVideoId)
                    trackDao.insertTrack(updated.toEntity())
                    updated
                }

                _uiState.update { state ->
                    val updatedQueue = state.queue.map { queuedTrack ->
                        if (queuedTrack.uuid == track.uuid) queuedTrack.copy(ytVideoId = ytVideoId)
                        else queuedTrack
                    }

                    val updatedCurrentTrack = if (state.currentTrack?.uuid == track.uuid) {
                        state.currentTrack.copy(ytVideoId = ytVideoId)
                    } else {
                        state.currentTrack
                    }

                    state.copy(
                        queue = updatedQueue,
                        currentTrack = updatedCurrentTrack
                    )
                }

                // Keep QueueManager in sync so recommendation seeding can use hydrated video IDs.
                queueManager.replaceTrackInQueue(track.uuid, persistedTrack)

            } catch (e: Exception) {
                Log.e("MusicViewModel", "Failed to async fetch YT video ID: ${e.message}", e)
            }
        }
    }

    val equalizerBands = playbackManager.audioEffectController.equalizerBands
    val isEqualizerEnabled = playbackManager.audioEffectController.isEqualizerEnabled

    fun toggleEqualizer(enabled: Boolean) {
        playbackManager.audioEffectController.setEqualizerEnabled(enabled)
    }

    fun setEqualizerBand(bandIndex: Int, level: Int) {
        playbackManager.audioEffectController.setEqualizerBandLevel(bandIndex, level)
    }

    fun resetEqualizer() {
        playbackManager.audioEffectController.resetEqualizer()
    }

    fun getEqualizerLevelRange(): Pair<Int, Int> {
        return playbackManager.audioEffectController.getEqualizerBandLevelRange()
    }

    // Volume Booster
    val boosterLevel = playbackManager.audioEffectController.boosterLevel
    val isBoosterEnabled = playbackManager.audioEffectController.isBoosterEnabled

    fun toggleVolumeBooster(enabled: Boolean) {
        playbackManager.audioEffectController.setBoosterEnabled(enabled)
    }

    fun setVolumeBoosterLevel(level: Int) {
        playbackManager.audioEffectController.setBoosterLevel(level)
    }

    // Volume Normalization
    val isNormalizationEnabled = playbackManager.audioEffectController.isNormalizationEnabled

    fun toggleVolumeNormalization(enabled: Boolean) {
        playbackManager.audioEffectController.setNormalizationEnabled(enabled)
    }

    // Recommendation Settings
    private val settingsPrefs = getApplication<Application>().getSharedPreferences(
        "music_settings_prefs",
        android.content.Context.MODE_PRIVATE
    )

    // Add Romanized Lyrics persistent state here:
    private val _isRomanizedLyricsEnabled = MutableStateFlow(
        settingsPrefs.getBoolean("romanized_lyrics_enabled", false)
    )
    val isRomanizedLyricsEnabled: StateFlow<Boolean> = _isRomanizedLyricsEnabled.asStateFlow()

    private val _isMiniPlayerLyricsEnabled = MutableStateFlow(
        settingsPrefs.getBoolean("miniplayer_lyrics_enabled", true)
    )
    val isMiniPlayerLyricsEnabled: StateFlow<Boolean> = _isMiniPlayerLyricsEnabled.asStateFlow()

    fun toggleRomanizedLyrics() {
        val enabled = !_isRomanizedLyricsEnabled.value
        _isRomanizedLyricsEnabled.value = enabled
        settingsPrefs.edit { putBoolean("romanized_lyrics_enabled", enabled) }
    }

    fun toggleMiniPlayerLyrics(enabled: Boolean) {
        _isMiniPlayerLyricsEnabled.value = enabled
        settingsPrefs.edit { putBoolean("miniplayer_lyrics_enabled", enabled) }
        Log.d("MusicViewModel", "Mini-Player Lyrics set to $enabled")
    }

    private val _recommendationCount =
        MutableStateFlow(settingsPrefs.getInt("recommendation_count", 5))
    val recommendationCount: StateFlow<Int> = _recommendationCount.asStateFlow()

    fun setRecommendationCount(count: Int) {
        val clampedCount = count.coerceIn(3, 15)
        _recommendationCount.value = clampedCount
        settingsPrefs.edit { putInt("recommendation_count", clampedCount) }
        Log.d("MusicViewModel", "Recommendation count set to $clampedCount")
    }

    // Market Code Settings (ISO 3166-1 alpha-2)
    private val _marketCode = MutableStateFlow(
        settingsPrefs.getString("spotify_market_code", "IN") ?: "IN"
    )
    val marketCode: StateFlow<String> = _marketCode.asStateFlow()

    fun setMarketCode(code: String) {
        // Validate it's a 2-letter code
        val validCode = code.uppercase().take(2)
        _marketCode.value = validCode
        settingsPrefs.edit { putString("spotify_market_code", validCode) }
        Log.d("MusicViewModel", "Market code set to $validCode")
    }


    private suspend fun loadRestoredQueue() {
        try {
            // Get track IDs from SharedPreferences
            val prefs = getApplication<Application>().getSharedPreferences(
                "playback_state_prefs",
                android.content.Context.MODE_PRIVATE
            )
            val trackIds = prefs.getString("queue_track_ids", "") ?: ""
            val savedIndex = prefs.getInt("queue_start_index", 0)

            if (trackIds.isEmpty()) return

            val ids = trackIds.split(",")
            val tracks = withContext(Dispatchers.IO) {
                ids.mapNotNull { id ->
                    try {
                        trackDao.getTrackByUuid(id)?.toTrack()
                    } catch (e: Exception) {
                        null
                    }
                }
            }

            if (tracks.isNotEmpty()) {
                val currentTrack = tracks.getOrNull(savedIndex)
                _uiState.update {
                    it.copy(
                        queue = tracks,
                        queueIndex = savedIndex,
                        currentTrack = currentTrack,
                        duration = currentTrack?.durationSec?.toLong()?.times(1000) ?: 0L
                    )
                }

                // DO NOT call queueManager.initializeQueue() here!
                // PlaybackService.restorePlaybackState() already initialized QueueManager
                // Calling it again causes duplicate tracks

                Log.d(
                    "MusicViewModel",
                    "Restored queue with ${tracks.size} tracks, current index: $savedIndex"
                )
            }
        } catch (e: Exception) {
            Log.e("MusicViewModel", "Failed to load restored queue: ${e.message}", e)
        }
    }

    /**
     * Set queue from Spotify tracks (for Artist, Playlist screens)
     * Downloads the first track and queues the remaining tracks sequentially
     */
    fun setQueueFromSpotifyTracks(spotifyTracks: List<SpotifyTrack>, startIndex: Int = 0) {
        viewModelScope.launch {
            try {
                if (spotifyTracks.isEmpty() || startIndex >= spotifyTracks.size) return@launch

                // Download and play the first track
                downloadAndPlay(
                    SpotifyApi.spotifyTrackToSong(spotifyTracks[startIndex])
                )

                // Wait for the first track to start playing before queuing others
                // This ensures proper queue order and avoids parallel download crashes
                kotlinx.coroutines.delay(800)

                // Queue the remaining tracks sequentially - one at a time with proper delays
                // This prevents download parallelization and maintains queue order
                for (i in (startIndex + 1) until spotifyTracks.size) {
                    // Add delay to prevent overwhelming the download system
                    kotlinx.coroutines.delay(200)

                    // Queue each track individually to the QueueManager's normal flow
                    // The queueSpotifyTrackNext will add them to queue one by one
                    queueSpotifyTrackNext(spotifyTracks[i])

                    // Wait for the queue operation to complete before adding next
                    // The isQueueOperationInProgress flag ensures sequential queueing
                    while (_uiState.value.isQueueOperationInProgress) {
                        kotlinx.coroutines.delay(50)
                    }
                }
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Error setting queue from Spotify tracks: ${e.message}", e)
            }
        }
    }

    /**
     * Set queue from Simplified tracks (for Album screen)
     * Downloads the first track and queues the remaining tracks sequentially
     */
    fun setQueueFromSimplifiedTracks(
        simplifiedTracks: List<SpotifySimplifiedTrack>,
        album: SpotifyAlbum,
        startIndex: Int = 0
    ) {
        viewModelScope.launch {
            try {
                if (simplifiedTracks.isEmpty() || startIndex >= simplifiedTracks.size) return@launch

                // Download and play the first track
                downloadAndPlay(
                    SpotifyApi.simplifiedTrackToSong(simplifiedTracks[startIndex], album)
                )

                // Wait for the first track to start playing before queuing others
                // This ensures proper queue order and avoids parallel download crashes
                kotlinx.coroutines.delay(800)

                // Queue the remaining tracks sequentially - one at a time with proper delays
                // This prevents download parallelization and maintains queue order
                for (i in (startIndex + 1) until simplifiedTracks.size) {
                    // Add delay to prevent overwhelming the download system
                    kotlinx.coroutines.delay(200)

                    // Queue each track individually to the QueueManager's normal flow
                    // The queueSimplifiedTrackNext will add them to queue one by one
                    queueSimplifiedTrackNext(simplifiedTracks[i], album)

                    // Wait for the queue operation to complete before adding next
                    // The isQueueOperationInProgress flag ensures sequential queueing
                    while (_uiState.value.isQueueOperationInProgress) {
                        kotlinx.coroutines.delay(50)
                    }
                }
            } catch (e: Exception) {
                Log.e(
                    "MusicViewModel",
                    "Error setting queue from simplified tracks: ${e.message}",
                    e
                )
            }
        }
    }


    suspend fun getPurgeableTracks(): List<Track> {
        return withContext(Dispatchers.IO) {
            val calendar = java.util.Calendar.getInstance()

            // 14 days ago for last played
            calendar.add(java.util.Calendar.DAY_OF_YEAR, -14)
            val lastPlayedThresholdDate = calendar.time
            val lastPlayedThreshold = java.text.SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                java.util.Locale.US
            ).format(lastPlayedThresholdDate)

            // Reset and go back 30 days for downloads
            calendar.time = java.util.Date()
            calendar.add(java.util.Calendar.DAY_OF_YEAR, -30)
            val downloadedThreshold = calendar.timeInMillis

            val candidates = trackDao.getPurgeableTracks(lastPlayedThreshold, downloadedThreshold)

            // Map to Track model
            // Note: Broken files (ghost tracks) are not explicitly searched for here to avoid
            // scanning the entire library file system, but they will be included if they match the SQL criteria.
            candidates.map { it.toTrack() }
        }
    }

    fun purgeTracks(tracks: List<Track>) {
        viewModelScope.launch(Dispatchers.IO) {
            musicService.deleteTracksAndFiles(tracks)
        }
    }

    fun promoteTrackToDownload(track: Track) {
        if (!track.isStream) return

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                val updatedTrack = withContext(Dispatchers.IO) {
                    musicService.promoteStreamToDownload(track)
                }

                // Replace in PlaybackManager's MediaController queue so ExoPlayer uses the new file
                playbackManager.replaceTrackInQueue(track.uuid, updatedTrack, seamlessIfPlaying = true)

                // Replace in QueueManager's internal queue so pre-fetch doesn't see stale paths
                queueManager.replaceTrackInQueue(track.uuid, updatedTrack)

                // Update UI with the new downloaded track
                _uiState.update { state ->
                    val newQueue = state.queue.map {
                        if (it.uuid == track.uuid) updatedTrack else it
                    }
                    state.copy(
                        currentTrack = if (state.currentTrack?.uuid == track.uuid) updatedTrack else state.currentTrack,
                        queue = newQueue,
                        isLoading = false
                    )
                }
                Log.d("MusicViewModel", "Promoted track to download: ${track.title}")
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Failed to promote track: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Download failed: ${e.message}"
                    )
                }
            }
        }
    }
}
