package com.example.juke.services

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.example.juke.database.MusicDatabase
import com.example.juke.database.toTrack
import com.example.juke.models.Track
import com.example.juke.network.OfflineException
import com.example.juke.network.RecommenderApi
import com.example.juke.network.SpotifyApi
import com.example.juke.network.isOffline
import com.example.juke.utils.ArtistUtils
import com.example.juke.utils.BlacklistManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Download information for UI display
 */
data class DownloadInfo(
    val title: String,
    val artist: String,
    val source: String = "recommendation" // or "manual", "playlist"
)

/**
 * Queue Manager for smart music recommendations and downloads.
 * 
 * This service handles:
 * 1. Fetching recommendations from YouTube Music based on current song
 * 2. Validating recommendations with Spotify
 * 3. Smart queue management (keeps next 2 songs ready)
 * 4. Automatic downloads when queue is low (<=2 songs)
 * 5. Background processing and caching
 */

class QueueManager private constructor(private val context: Context) {

    companion object {
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: QueueManager? = null

        fun getInstance(context: Context): QueueManager {
            return instance ?: synchronized(this) {
                instance ?: QueueManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val TAG = "QueueManager"
    private val database = MusicDatabase.getDatabase(context)
    private val trackDao = database.trackDao()
    private val musicService = MusicService(context)

    // Settings for user-defined recommendation count
    private val settingsPrefs = context.getSharedPreferences(
        "music_settings_prefs",
        Context.MODE_PRIVATE
    )

    // Stream Mode preference
    private var _isStreamMode = settingsPrefs.getBoolean("stream_mode", false)
    var isStreamMode: Boolean
        get() = _isStreamMode
        set(value) {
            _isStreamMode = value
            settingsPrefs.edit { putBoolean("stream_mode", value) }
            Log.d(TAG, "Stream mode set to: $value")
        }

    // Coroutine scope for background tasks
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Queue state
    private val _currentQueue = MutableStateFlow<List<Track>>(emptyList())
    val currentQueue: StateFlow<List<Track>> = _currentQueue.asStateFlow()

    private val _downloadingTracks = MutableStateFlow<List<DownloadInfo>>(emptyList())
    val downloadingTracks: StateFlow<List<DownloadInfo>> = _downloadingTracks.asStateFlow()

    // Expose pending recommendations count for UI
    fun getPendingDownloadsCount(): Int = pendingRecommendations.size + downloadJobs.size

    // Pending recommendations to download
    private val pendingRecommendations =
        ConcurrentLinkedQueue<RecommenderApi.ValidatedRecommendation>()

    // Currently downloading jobs
    private val downloadJobs = mutableMapOf<String, Job>()

    // Track if we're currently fetching recommendations (to prevent multiple concurrent fetches)
    private var isRecommendationFetchInProgress = false

    // External downloads tracking (downloaded outside QueueManager, e.g. Instant Play)
    // Key: "Title-Artist" to prevent adding them as recommendations
    private val _externalDownloads = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    /**
     * Notify QueueManager that a download has started externally (e.g. from Instant Play).
     * This prevents QueueManager from fetching the same song as a recommendation.
     * 
     * @param track The track being downloaded
     */
    fun notifyDownloadStarted(track: Track, addToUi: Boolean = true) {
        val key = "${track.title.lowercase()}-${track.artist.lowercase()}"
        _externalDownloads.add(key)
        Log.d(TAG, "Notified of external download: ${track.title} (Key: $key)")

        // Also add to download tracking for UI if requested
        if (addToUi) {
            addDownloadTracking(track.title, track.artist, "manual")
        }

        // Auto-remove after 5 minutes to prevent permanent blocking in case of failure
        serviceScope.launch {
            kotlinx.coroutines.delay(5 * 60 * 1000L)
            _externalDownloads.remove(key)
        }
    }

    /**
     * Check if we need to fetch recommendations and start downloading if queue is low.
     * This should be called whenever playback starts or resumes.
     */
    fun checkAndFetchRecommendations() {
        val currentQueueSize = _currentQueue.value.size
        Log.d(TAG, "Checking recommendations: queue size = $currentQueueSize")

        if (currentQueueSize <= 2) {
            val currentTrack = _currentQueue.value.firstOrNull()
            currentTrack?.let {
                Log.d(TAG, "Queue size <= 2, fetching recommendations for: ${it.title}")
                fetchAndQueueRecommendations(it)
            }
        }
    }

    /**
     * Initialize the queue with a list of tracks.
     * This should be called when a user selects a new song from search or another screen.
     * 
     * IMPORTANT: This cancels all pending recommendation downloads from the previous song
     * to prevent queue pollution with old recommendations.
     * 
     * @param tracks Initial queue
     */
    fun initializeQueue(tracks: List<Track>) {
        // Cancel any pending downloads from the previous song
        // This prevents old recommendation downloads from being added to the queue
        cancelPendingRecommendationDownloads()

        _currentQueue.value = tracks.toMutableList()
        Log.d(
            TAG,
            "Queue initialized with ${tracks.size} tracks (cancelled previous recommendations)"
        )

        // Check if we need to fetch recommendations for the new song
        if (tracks.size <= 2) {
            val currentTrack = tracks.firstOrNull()
            currentTrack?.let { fetchAndQueueRecommendations(it) }
        }
    }

    /**
     * Add a track to the end of the queue.
     * 
     * @param track Track to add
     */
    fun addToQueue(track: Track) {
        val currentList = _currentQueue.value.toMutableList()

        // Check if track already exists (Move operation)
        // We remove the old instance so the new one "moves" to the end
        val existingIndex = currentList.indexOfFirst { it.uuid == track.uuid }
        if (existingIndex != -1) {
            currentList.removeAt(existingIndex)
            Log.d(TAG, "Moved existing track to end of queue: ${track.title}")
        }

        currentList.add(track)
        _currentQueue.value = currentList
        Log.d(TAG, "Added to queue: ${track.title}")
    }

    /**
     * Insert a track at a specific index in the queue.
     *
     * @param index Index to insert at (0-based)
     * @param track Track to insert
     */
    fun insertQueueItem(index: Int, track: Track) {
        val currentList = _currentQueue.value.toMutableList()
        val safeIndex = index.coerceIn(0, currentList.size)
        currentList.add(safeIndex, track)
        _currentQueue.value = currentList
        Log.d(TAG, "Inserted track into queue at index $safeIndex: ${track.title}")

        // Ensure next 3 songs are downloaded/validated if we modified near the top
        if (safeIndex <= 3) {
            ensureUpcomingTracksReady() // <-- Updated from ensureNext2Ready()
        }
    }

    /**
     * Remove a track from the queue.
     * 
     * @param trackId Track UUID to remove
     */
    fun removeFromQueue(trackId: String) {
        val currentList = _currentQueue.value.toMutableList()
        currentList.removeAll { it.uuid == trackId }
        _currentQueue.value = currentList
        Log.d(TAG, "Removed from queue: $trackId")

        // Check if we need more recommendations
        if (currentList.size <= 2) {
            val currentTrack = currentList.firstOrNull()
            currentTrack?.let { fetchAndQueueRecommendations(it) }
        }
    }

    /**
     * Move to the next track in queue.
     * 
     * @return Next track or null if queue is empty
     */
    // Recent artists tracking for recommendation variety
    private val recentArtists = java.util.LinkedList<String>()

    /**
     * Clear the entire queue.
     */


    /**
     * Move to the next track in queue.
     * 
     * @return Next track or null if queue is empty
     */
    fun moveToNext(): Track? {
        val currentList = _currentQueue.value.toMutableList()
        if (currentList.isEmpty()) return null

        // Get the track being removed (just played) and add to recent artists
        val playedTrack = currentList[0]
        addToRecentArtists(playedTrack.artist)

        // Remove first track
        currentList.removeAt(0)
        _currentQueue.value = currentList

        Log.d(TAG, "Moved to next track. Queue size: ${currentList.size}")

        // Check if we need to fetch more recommendations
        if (currentList.size <= 2) {
            val currentTrack = currentList.firstOrNull()
            currentTrack?.let { fetchAndQueueRecommendations(it) }
        }

        // Ensure next 3 songs are downloaded/validated
        ensureUpcomingTracksReady() // <-- Updated from ensureNext2Ready()

        return currentList.firstOrNull()
    }

    private fun addToRecentArtists(artist: String) {
        // Handle multiple artists (split by comma, &, etc. if needed, but simple addition is fine for now)
        // We want to track distinct artist names
        if (artist.isNotBlank()) {
            recentArtists.remove(artist) // Move to end if exists
            recentArtists.add(artist)
            if (recentArtists.size > 50) {
                recentArtists.removeFirst()
            }
            Log.d(TAG, "Updated recent artists. Count: ${recentArtists.size}. Latest: $artist")
        }
    }

    /**
     * Fetch recommendations based on current track and add to queue.
     *
     * Tries online recommendations first (YouTube Music -> Spotify validation).
     * Falls back to offline library-based recommendations if:
     * - Network is unavailable
     * - API returns no results
     * - Any exception occurs
     *
     * @param currentTrack Track to base recommendations on
     */
    fun fetchAndQueueRecommendations(currentTrack: Track) {
        serviceScope.launch {
            // Prevent multiple concurrent recommendation fetches
            if (isRecommendationFetchInProgress) {
                Log.d(TAG, "Recommendation fetch already in progress, skipping")
                return@launch
            }

            isRecommendationFetchInProgress = true
            try {
                Log.d(
                    TAG,
                    "Fetching recommendations for: ${currentTrack.title} by ${currentTrack.artist}"
                )

                var onlineSucceeded = false

                try {
                    // --- ONLINE PATH ---
                    // Get YouTube video ID
                    val videoId = currentTrack.ytVideoId ?: run {
                        val query = "${currentTrack.title} ${currentTrack.artist}"
                        RecommenderApi.getBestVideoMatch(query)
                    }

                    if (videoId == null) {
                        Log.w(
                            TAG,
                            "Could not find YouTube video ID for: ${currentTrack.title}. Falling back to offline."
                        )
                    } else {
                        Log.d(TAG, "Using YouTube video ID: $videoId")

                        // Fetch full radio queue (index 1 to ~49)
                        val rawRecommendations = RecommenderApi.fetchFullRadioQueue(videoId)

                        // ── Artist Blacklist Filter ───────────────────────────
                        val blacklist = BlacklistManager.getBlacklistedArtists(context)
                        val recommendations = if (blacklist.isNotEmpty()) {
                            rawRecommendations.filter { rec ->
                                val blocked = BlacklistManager.containsBlacklistedArtist(
                                    context,
                                    rec.artist,
                                    blacklist
                                )
                                        || BlacklistManager.titleContainsBlacklistedArtist(
                                    context,
                                    rec.title,
                                    blacklist
                                )
                                if (blocked) Log.d(
                                    TAG,
                                    "\uD83D\uDEAB Blacklist filtered: ${rec.title} by ${rec.artist}"
                                )
                                !blocked
                            }
                        } else rawRecommendations
                        if (blacklist.isNotEmpty()) {
                            Log.d(
                                TAG,
                                "Blacklist: removed ${rawRecommendations.size - recommendations.size} of ${rawRecommendations.size} recommendations"
                            )
                        }
                        // ─────────────────────────────────────────────────────

                        if (recommendations.isEmpty()) {
                            Log.w(TAG, "No online recommendations found. Falling back to offline.")
                        } else {
                            Log.d(TAG, "Got ${recommendations.size} raw recommendations")

                            // Construct artist context: Current track artist + Recent artists
                            val contextArtists =
                                (listOf(currentTrack.artist) + recentArtists).joinToString(", ")

                            // Get user-defined recommendation count from settings (default: 5)
                            val targetCount = settingsPrefs.getInt("recommendation_count", 5)
                            Log.d(TAG, "Target recommendation count: $targetCount")

                            // Validate with Spotify - fetch 2x the target to allow for filtering duplicates
                            val validatedRecs = RecommenderApi.validateAndFilterWithSpotify(
                                recommendations,
                                originalArtists = contextArtists,
                                maxResults = targetCount * 2
                            )

                            if (validatedRecs.isEmpty()) {
                                Log.w(
                                    TAG,
                                    "No validated recommendations found. Falling back to offline."
                                )
                            } else {
                                Log.d(TAG, "Got ${validatedRecs.size} validated recommendations")

                                // Filter out tracks already in current queue
                                val currentQueueTitles =
                                    _currentQueue.value.map { it.title.lowercase() to it.artist.lowercase() }
                                val seedTrackPair =
                                    currentTrack.title.lowercase() to currentTrack.artist.lowercase()

                                val filteredRecs = validatedRecs.filter { rec ->
                                    val trackPair = rec.title.lowercase() to rec.artist.lowercase()
                                    val key = "${rec.title.lowercase()}-${rec.artist.lowercase()}"
                                    val inQueue = currentQueueTitles.contains(trackPair)
                                    val isSeed = trackPair == seedTrackPair
                                    val isExternallyDownloading = _externalDownloads.contains(key)
                                    if (isExternallyDownloading) Log.d(
                                        TAG,
                                        "Filtered out external download: ${rec.title}"
                                    )
                                    !inQueue && !isSeed && !isExternallyDownloading
                                }

                                Log.d(
                                    TAG,
                                    "After queue filtering: ${filteredRecs.size} recommendations"
                                )

                                if (filteredRecs.isNotEmpty()) {
                                    // Separate into new (not in library) and library (already downloaded)
                                    val newRecs =
                                        mutableListOf<RecommenderApi.ValidatedRecommendation>()
                                    val libraryRecs =
                                        mutableListOf<RecommenderApi.ValidatedRecommendation>()

                                    for (rec in filteredRecs) {
                                        val candidates = trackDao.findTracksByTitleAndDuration(
                                            rec.title,
                                            rec.durationSec
                                        )
                                        val existsInLibrary = candidates.any {
                                            ArtistUtils.areArtistsEqual(
                                                it.artist,
                                                rec.artist
                                            ) && it.localUri != null
                                        }
                                        if (existsInLibrary) libraryRecs.add(rec) else newRecs.add(
                                            rec
                                        )
                                    }

                                    Log.d(
                                        TAG,
                                        "Categorized: ${newRecs.size} new tracks, ${libraryRecs.size} library tracks"
                                    )

                                    val finalRecs =
                                        mutableListOf<RecommenderApi.ValidatedRecommendation>()
                                    finalRecs.addAll(newRecs.take(targetCount))
                                    if (finalRecs.size < targetCount) {
                                        val remaining = targetCount - finalRecs.size
                                        finalRecs.addAll(libraryRecs.take(remaining))
                                    }

                                    Log.d(TAG, "Final online selection: ${finalRecs.size} tracks")

                                    if (finalRecs.isNotEmpty()) {
                                        finalRecs.forEach { rec ->
                                            pendingRecommendations.offer(rec)
                                            Log.d(
                                                TAG,
                                                "Queued for download: ${rec.title} by ${rec.artist}"
                                            )
                                        }
                                        processNextDownload()
                                        onlineSucceeded = true
                                    }
                                }
                            }
                        }
                    }
                } catch (onlineEx: Exception) {
                    if (onlineEx is OfflineException || onlineEx.isOffline()) {
                        Log.w(
                            TAG,
                            "Device is offline. Triggering offline fallback for recommendations."
                        )
                    } else {
                        Log.e(
                            TAG,
                            "Online recommendation failed: ${onlineEx.message}. Falling back to offline."
                        )
                    }
                }

                // --- OFFLINE FALLBACK ---
                if (!onlineSucceeded) {
                    Log.d(TAG, "Triggering offline fallback for: ${currentTrack.title}")
                    fetchOfflineRecommendations(currentTrack)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error in fetchAndQueueRecommendations: ${e.message}", e)
            } finally {
                isRecommendationFetchInProgress = false
            }
        }
    }

    /**
     * Offline fallback: builds a queue from the local library using a scoring algorithm.
     *
     * Scoring weights per track (relative to the current track):
     * - +10: Same primary artist (mild preference, not dominant)
     * - +5:  Shared collaborating artist (small nudge toward collaborative tracks)
     * - +8:  Same album (albumSpotifyId match)
     * - +10: Is a favourite
     * - +5 per 10 plays: High play count (capped at +25)
     * - -10: Recently played in the last 24 hours
     * - -20: Artist was recently played (last 10 tracks) — prevents same-artist flooding
     *
     * Tracks already in the queue or with no local file are excluded.
     * If no tracks score above 0, falls back to favourites / most-played.
     *
     * @param currentTrack The currently playing track to seed the queue from
     */
    private suspend fun fetchOfflineRecommendations(currentTrack: Track) {
        try {
            val targetCount = settingsPrefs.getInt("recommendation_count", 5)
            val allDownloaded = trackDao.getDownloadedTracks()

            if (allDownloaded.isEmpty()) {
                Log.d(TAG, "Offline fallback: library is empty, nothing to queue")
                return
            }

            // Tracks already in queue (by uuid)
            val currentQueueUuids = _currentQueue.value.map { it.uuid }.toSet()

            // Parse the current track's artist names for comparison
            val currentArtistNames = parseArtistNames(currentTrack.artist)
            val currentArtistIds = currentTrack.artistSpotifyIds?.toSet() ?: emptySet()
            val now = System.currentTimeMillis()
            val oneDayMs = 24 * 60 * 60 * 1000L

            data class ScoredTrack(val track: Track, val score: Int)

            // ── Artist Blacklist Filter (offline) ────────────────
            val blacklist = BlacklistManager.getBlacklistedArtists(context)
            // ─────────────────────────────────────────────────────

            val scored = allDownloaded
                .filter { entity ->
                    // Exclude current track and tracks already in queue
                    entity.uuid != currentTrack.uuid &&
                            !currentQueueUuids.contains(entity.uuid) &&
                            entity.localUri != null &&
                            File(entity.localUri).exists() &&
                            // Blacklist check
                            !BlacklistManager.containsBlacklistedArtist(
                                context,
                                entity.artist,
                                blacklist
                            ) &&
                            !BlacklistManager.titleContainsBlacklistedArtist(
                                context,
                                entity.title,
                                blacklist
                            )
                }
                .map { entity ->
                    val track = entity.toTrack()
                    var score = 0

                    val trackArtistNames = parseArtistNames(track.artist)
                    val trackArtistIds = track.artistSpotifyIds?.toSet() ?: emptySet()

                    // +10: Same primary artist — mild preference, keeps variety intact
                    if (ArtistUtils.areArtistsEqual(track.artist, currentTrack.artist)) {
                        score += 10
                    } else {
                        // +5: Shared collaborating artist (small nudge, not dominant)
                        val sharedByName = currentArtistNames.intersect(trackArtistNames)
                        val sharedById =
                            if (currentArtistIds.isNotEmpty() && trackArtistIds.isNotEmpty()) {
                                currentArtistIds.intersect(trackArtistIds)
                            } else emptySet()

                        if (sharedByName.isNotEmpty() || sharedById.isNotEmpty()) {
                            score += 5
                        }
                    }

                    // +8: Same album
                    if (currentTrack.albumSpotifyId != null &&
                        track.albumSpotifyId != null &&
                        currentTrack.albumSpotifyId == track.albumSpotifyId
                    ) {
                        score += 8
                    }

                    // +10: Is favourite
                    if (track.isFavourite) score += 10

                    // +5 per 10 plays (capped at +25)
                    score += minOf(25, (track.playCount / 10) * 5)

                    // -10: Recently played in last 24 hours
                    val lastPlayedMs = track.lastPlayedAt?.toLongOrNull()
                    if (lastPlayedMs != null && (now - lastPlayedMs) < oneDayMs) {
                        score -= 10
                    }

                    // -20: Artist was recently heard (last 10 played tracks).
                    // Prevents same-artist flooding when the library is small.
                    val recentWindow = recentArtists.takeLast(10)
                    if (recentWindow.any { ArtistUtils.areArtistsEqual(it, track.artist) }) {
                        score -= 20
                    }

                    ScoredTrack(track, score)
                }
                .filter { it.score > 0 }
                .sortedByDescending { it.score }

            Log.d(
                TAG,
                "Offline fallback: scored ${scored.size} candidates from ${allDownloaded.size} library tracks"
            )

            val selected = if (scored.isNotEmpty()) {
                scored.take(targetCount).map { it.track }
            } else {
                // Last resort: favourites first, then most played
                Log.d(TAG, "Offline fallback: no scored candidates, using favourites/most-played")
                allDownloaded
                    .filter {
                        it.uuid != currentTrack.uuid && !currentQueueUuids.contains(it.uuid) && isLocalFilePlayable(it.localUri)
                                && !BlacklistManager.containsBlacklistedArtist(
                            context,
                            it.artist,
                            blacklist
                        )
                    }
                    .map { it.toTrack() }
                    .sortedWith(compareByDescending<Track> { it.isFavourite }.thenByDescending { it.playCount })
                    .take(targetCount)
            }

            if (selected.isEmpty()) {
                Log.d(TAG, "Offline fallback: no tracks available to queue")
                return
            }

            Log.d(TAG, "Offline fallback: queuing ${selected.size} tracks")
            selected.forEach { track ->
                addToQueue(track)
                Log.d(TAG, "Offline queued: ${track.title} by ${track.artist}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in offline fallback: ${e.message}", e)
        }
    }

    /**
     * Returns true if a localUri points to a file that exists, is readable, and has content.
     */
    private fun isLocalFilePlayable(uri: String?): Boolean {
        if (uri == null) return false
        return try {
            val file = File(uri)
            file.exists() && file.canRead() && file.length() > 0
        } catch (e: Exception) {
            Log.e(TAG, "isLocalFilePlayable failed for uri=$uri", e)
            false
        }
    }

    /**
     * Parse a combined artist string into a normalized set of individual artist names.
     * Handles delimiters: comma, ampersand, semicolon, feat., ft.
     */
    private fun parseArtistNames(artist: String): Set<String> {
        return artist.lowercase()
            .replace(" feat. ", ",")
            .replace(" ft. ", ",")
            .replace(" & ", ",")
            .replace(";", ",")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    /**
     * Process the next download from pending recommendations.
     * 
     * This method ensures that only a limited number of downloads
     * happen simultaneously to avoid overwhelming the system.
     */
    private fun processNextDownload() {
        serviceScope.launch {
            while (true) {
                // Check capacity safely
                val shouldStop = synchronized(downloadJobs) {
                    downloadJobs.size >= 6
                }
                if (shouldStop) break

                val rec = pendingRecommendations.poll() ?: break

                // CHECK 1: Check if already in current queue (Prevent Duplicates)
                if (_currentQueue.value.any {
                        it.title.equals(
                            rec.title,
                            ignoreCase = true
                        ) && it.artist.equals(rec.artist, ignoreCase = true)
                    }) {
                    Log.d(TAG, "Skipping duplicate recommendation (already in queue): ${rec.title}")
                    continue
                }

                // CHECK 2: Check if already downloaded (Database check)
                // This is a suspending function, so it must be outside synchronized block
                val candidates = trackDao.findTracksByTitleAndDuration(rec.title, rec.durationSec)
                val existingTrack = candidates.find {
                    ArtistUtils.areArtistsEqual(it.artist, rec.artist)
                }
                if (existingTrack != null && existingTrack.localUri != null) {
                    Log.d(TAG, "Track already exists: ${rec.title}")
                    addToQueue(existingTrack.toTrack())
                    continue
                }

                if (_downloadingTracks.value.any { it.title == rec.title && it.artist == rec.artist }) {
                    Log.d(TAG, "Track already downloading: ${rec.title}")
                    continue
                }

                // Start download
                val downloadJob = launch {
                    try {
                        Log.d(TAG, "Starting download: ${rec.title} by ${rec.artist}")

                        val downloadInfo = DownloadInfo(rec.title, rec.artist, "recommendation")
                        _downloadingTracks.value += downloadInfo

                        val trackId = rec.spotifyUrl.substringAfterLast("/").substringBefore("?")
                        val spotifyTrack = SpotifyApi.getTrack(trackId)
                        val song = SpotifyApi.spotifyTrackToSong(spotifyTrack)

                        val track = if (isStreamMode) {
                            Log.d(
                                TAG,
                                "Stream Mode enabled, resolving stream URL for: ${rec.title}"
                            )
                            musicService.streamTrack(song)
                        } else {
                            Log.d(TAG, "Stream Mode disabled, downloading: ${rec.title}")
                            musicService.smartDownloadAndIndex(song)
                        }

                        Log.d(TAG, "Successfully processed (stream=$isStreamMode): ${track.title}")
                        addToQueue(track)

                    } catch (e: Exception) {
                        Log.e(TAG, "Error downloading ${rec.title}: ${e.message}", e)
                    } finally {
                        _downloadingTracks.value = _downloadingTracks.value.filterNot {
                            it.title == rec.title && it.artist == rec.artist
                        }

                        synchronized(downloadJobs) {
                            downloadJobs.remove(rec.title)
                        }

                        // Process next batch of downloads
                        processNextDownload()
                    }
                }

                // Register job safely
                synchronized(downloadJobs) {
                    downloadJobs[rec.title] = downloadJob
                }
            }
        }
    }
    /**
     * Ensure the next 2 songs in queue are downloaded.
     * 
     * This pre-downloads upcoming tracks to ensure smooth playback.
     */
    /**
     * Ensure the upcoming 3 songs in queue are ready (downloaded or valid stream URL).
     * Validates local file existence and stream URL expiry.
     * Removes tracks if offline and unavailable to prevent playback stoppage.
     */
    private fun ensureUpcomingTracksReady() {
        serviceScope.launch {
            val queue = _currentQueue.value
            val upcoming = queue.take(3) // Pre-check the next 3 tracks

            val isOffline = run {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                val network = cm.activeNetwork
                val caps = cm.getNetworkCapabilities(network)
                caps == null || !caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }

            upcoming.forEach { track ->
                var needsRefresh = false

                if (track.localUri == null) {
                    needsRefresh = true
                } else if (!track.localUri.startsWith("http") && !track.localUri.startsWith("content://")) {
                    // Local file validation
                    val file = File(track.localUri)
                    if (!file.exists() || file.length() <= 0) {
                        Log.e(TAG, "File missing or empty for ${track.title}: ${track.localUri}")
                        needsRefresh = true
                    }
                } else if (track.localUri.startsWith("http")) {
                    // Stream URL validation
                    val url = track.localUri
                    try {
                        val expiresMatch = "expires=(\\d+)".toRegex().find(url)
                        if (expiresMatch != null) {
                            val expiryTimeSec = expiresMatch.groupValues[1].toLong()
                            val currentTimeSec = System.currentTimeMillis() / 1000
                            // Refresh if expiring within 15 minutes (900 seconds) or already expired
                            if (expiryTimeSec - currentTimeSec < 900) {
                                Log.d(TAG, "Stream URL for ${track.title} is expiring soon, scheduling refresh.")
                                needsRefresh = true
                            }
                        } else if (url.contains("googleusercontent.com/spotify.com")) {
                            // Dummy URL that hasn't been resolved to a real spotmate stream URL yet
                            needsRefresh = true
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error checking stream expiry for ${track.title}: ${e.message}")
                    }
                }

                if (needsRefresh) {
                    if (isOffline) {
                        // Offline and missing file or needing refresh -> remove from queue to prevent playback stoppage
                        Log.w(TAG, "Device is offline and track ${track.title} is unavailable. Removing from queue.")
                        removeFromQueue(track.uuid)
                        PlaybackManager.getInstance(context).removeDeletedTrackFromQueue(track.uuid)
                    } else {
                        // Online -> Prepare/Refresh
                        Log.d(TAG, "Track not ready/expired: ${track.title}, triggering preparation/refresh")
                        try {
                            val song = if (track.spotifyId != null) {
                                SpotifyApi.spotifyTrackToSong(SpotifyApi.getTrack(track.spotifyId))
                            } else {
                                val query = "${track.title} ${track.artist}"
                                val searchResponse = SpotifyApi.search(query, listOf("track"))
                                val spotifyResults = searchResponse.tracks?.items ?: emptyList()
                                if (spotifyResults.isNotEmpty()) {
                                    SpotifyApi.spotifyTrackToSong(spotifyResults.first())
                                } else null
                            }

                            song?.let { s ->
                                val updatedTrack = if (isStreamMode || track.isStream) {
                                    musicService.streamTrack(s)
                                } else {
                                    musicService.smartDownloadAndIndex(s)
                                }
                                
                                // Update it in the PlaybackManager's queue silently
                                PlaybackManager.getInstance(context).replaceTrackInQueue(track.uuid, updatedTrack)
                                
                                // Update in our local queue representation
                                val currentList = _currentQueue.value.toMutableList()
                                val qIndex = currentList.indexOfFirst { it.uuid == track.uuid }
                                if (qIndex != -1) {
                                    currentList[qIndex] = updatedTrack
                                    _currentQueue.value = currentList
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error emergency preparing ${track.title}: ${e.message}", e)
                            // Remove from queue if recovery completely fails to avoid blocking playback
                            removeFromQueue(track.uuid)
                            PlaybackManager.getInstance(context).removeDeletedTrackFromQueue(track.uuid)
                        }
                    }
                }
            }
        }
    }

    private var lastPreFetchTime: Long = 0

    /**
     * Check if we need to pre-fetch the next song.
     * Called by PlaybackService during playback.
     */
    fun checkPreFetch(positionMs: Long, durationMs: Long) {
        val now = System.currentTimeMillis()
        if (now - lastPreFetchTime < 5000) return // Throttle checks

        val timeRemaining = durationMs - positionMs
        if (timeRemaining in 1..<15000) { // 15 seconds
            lastPreFetchTime = now
            Log.d(TAG, "Pre-fetch triggered (Time remaining: ${timeRemaining}ms)")
            ensureUpcomingTracksReady() // <-- Updated from ensureNext2Ready()
        }
    }

    /**
     * Get the current queue size.
     * 
     * @return Number of tracks in queue
     */
    fun getQueueSize(): Int {
        return _currentQueue.value.size
    }

    /**
     * Get the next track without removing it.
     * 
     * @return Next track or null
     */
    fun peekNext(): Track? {
        return _currentQueue.value.firstOrNull()
    }

    /**
     * Clear the entire queue.
     */
    fun clearQueue() {
        _currentQueue.value = emptyList()
        cancelPendingRecommendationDownloads()
        Log.d(TAG, "Queue cleared")
    }

    /**
     * Cancel all pending recommendation downloads.
     * 
     * This is called when:
     * 1. User selects a new song from search/another screen (initializeQueue)
     * 2. Queue is explicitly cleared
     * 3. App is shutting down (cleanup)
     * 
     * This prevents old recommendations from being mixed with new ones.
     */
    private fun cancelPendingRecommendationDownloads() {
        // Clear pending queue
        val pendingCount = pendingRecommendations.size
        pendingRecommendations.clear()

        // Cancel all in-progress downloads
        downloadJobs.values.forEach { job ->
            if (!job.isCompleted) {
                job.cancel()
            }
        }
        downloadJobs.clear()

        // Clear downloading tracks display
        _downloadingTracks.value = emptyList()

        if (pendingCount > 0 || downloadJobs.isNotEmpty()) {
            Log.d(
                TAG,
                "Cancelled $pendingCount pending recommendations and ${downloadJobs.size} active downloads"
            )
        }
    }

    /**
     * Add download info for external tracking (e.g., playlist imports)
     */
    fun addDownloadTracking(title: String, artist: String, source: String = "manual") {
        val downloadInfo = DownloadInfo(title, artist, source)
        _downloadingTracks.value += downloadInfo
    }

    /**
     * Remove download tracking
     */
    fun removeDownloadTracking(title: String, artist: String) {
        _downloadingTracks.value = _downloadingTracks.value.filterNot {
            it.title == title && it.artist == artist
        }
    }

    /**
     * Get current queue as a list.
     * 
     * @return Copy of current queue
     */
    fun getCurrentQueueList(): List<Track> {
        return _currentQueue.value.toList()
    }

    /**
     * Manually trigger recommendation fetch for a specific track.
     * 
     * @param track Track to base recommendations on
     */
    fun manuallyFetchRecommendations(track: Track) {
        fetchAndQueueRecommendations(track)
    }

    /**
     * Cancel all downloads and cleanup.
     */
    fun cleanup() {
        cancelPendingRecommendationDownloads()
        serviceScope.cancel()
        instance = null
        Log.d(TAG, "QueueManager cleaned up and instance reset")
    }
}
