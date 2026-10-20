package com.example.juke.services

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.example.juke.database.MusicDatabase
import com.example.juke.database.toEntity
import com.example.juke.database.toTrack
import com.example.juke.models.Track
import com.example.juke.network.OfflineException
import com.example.juke.network.RecommenderApi
import com.example.juke.network.SpotifyApi
import com.example.juke.utils.ArtistUtils
import com.example.juke.utils.BlacklistManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

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

        /** Default plays in one day after which a song counts as "on repeat" and is no longer held back. */
        const val DEFAULT_INDULGE_PLAYS = 2
        /**
         * A radio song heard this often lately (freshness below this) waits behind the fresh ones
         * instead of keeping its radio position. Favourites and songs on repeat today are exempt.
         */
        private const val STALE_FRESHNESS = 0.5

        fun getInstance(context: Context): QueueManager {
            return instance ?: synchronized(this) {
                instance ?: QueueManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val tag = "QueueManager"
    private val database = MusicDatabase.getDatabase(context)
    private val trackDao = database.trackDao()
    private val exposureDao = database.songExposureDao()
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
            Log.d(tag, "Stream mode set to: $value")
        }

    // Coroutine scope for background tasks
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Queue state
    private val _currentQueue = MutableStateFlow<List<Track>>(emptyList())
    val currentQueue: StateFlow<List<Track>> = _currentQueue.asStateFlow()

    /**
     * Songs the recommendation engine just added, one emission per batch. The player queue is
     * extended from this, never by diffing [currentQueue]: that list keeps the whole session, so a
     * diff re-appended played songs after "Clear played" and songs still in flight to the player.
     */
    private val _recommendationsAdded = MutableSharedFlow<Added>(extraBufferCapacity = 64)
    val recommendationsAdded: SharedFlow<Added> = _recommendationsAdded.asSharedFlow()

    /** Songs to append to the player. [allowDuplicates]: the listener also queued a copy of it. */
    data class Added(val tracks: List<Track>, val allowDuplicates: Boolean = false)

    /** What the recommendation engine is doing: songs being resolved now and songs waiting in reserve. */
    data class RecStatus(val resolving: Int = 0, val reserve: Int = 0)
    private val _recStatus = MutableStateFlow(RecStatus())
    val recStatus: StateFlow<RecStatus> = _recStatus.asStateFlow()

    private val _downloadingTracks = MutableStateFlow<List<DownloadInfo>>(emptyList())
    val downloadingTracks: StateFlow<List<DownloadInfo>> = _downloadingTracks.asStateFlow()

    private val playedTracksHistory = java.util.LinkedList<Track>()

    // Per-day play counts by song key. Survives restarts and resets itself when the date changes,
    // so recommendations can tell "heard once earlier today" from "on repeat today".
    private val dailyPrefs = context.getSharedPreferences("daily_plays", Context.MODE_PRIVATE)

    /** Plays per day after which a song counts as "on repeat" (Power tools → Advanced). */
    private val indulgePlays: Int
        get() = settingsPrefs.getInt("repeat_threshold", DEFAULT_INDULGE_PLAYS).coerceIn(2, 6)

    private fun today(): String = java.time.LocalDate.now().toString()

    @Synchronized
    private fun dailyPlayCount(key: String): Int =
        if (dailyPrefs.getString("date", null) == today()) dailyPrefs.getInt(key, 0) else 0

    /** Count one qualifying play (the caller decides what qualifies) of the track [uuid] today. */
    @Synchronized
    fun recordQualifiedPlay(uuid: String) {
        val track = (_currentQueue.value + playedTracksHistory).firstOrNull { it.uuid == uuid } ?: return
        val key = songKey(track)
        val day = today()
        val fresh = dailyPrefs.getString("date", null) != day
        val count = if (fresh) 0 else dailyPrefs.getInt(key, 0)
        dailyPrefs.edit {
            if (fresh) clear()
            putString("date", day)
            putInt(key, count + 1)
        }
        recordExposure(key, Variety.Event.PLAY)
    }

    /** The listener skipped [uuid] early: it should come back less often. */
    fun recordSkip(uuid: String) {
        val known = (_currentQueue.value + playedTracksHistory).firstOrNull { it.uuid == uuid }
        serviceScope.launch {
            val track = known ?: trackDao.getTrackByUuid(uuid)?.toTrack() ?: return@launch
            Log.d(tag, "Early skip: ${track.title}")
            recordExposure(songKey(track), Variety.Event.SKIP)
        }
    }

    private val exposureMutex = Mutex()

    /** Persist one exposure event for a song (play, early skip, or "was recommended"). */
    private fun recordExposure(key: String, event: Variety.Event) {
        serviceScope.launch {
            try {
                exposureMutex.withLock {
                    exposureDao.upsert(Variety.record(exposureDao.get(key), key, event, System.currentTimeMillis()))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(tag, "Could not record $event for $key: ${e.message}")
            }
        }
    }

    /** Current exposure rows for [keys] (SQLite caps bound args, so query in chunks). */
    private suspend fun exposureFor(keys: Collection<String>): Map<String, com.example.juke.database.SongExposureEntity> =
        keys.chunked(500).flatMap { exposureDao.getAll(it) }.associateBy { it.songKey }

    /** Song keys of favourites: their repeat penalty is waived. */
    private suspend fun favouriteKeys(): Set<String> =
        trackDao.getFavouriteTitleArtists().mapTo(HashSet()) { RecommenderApi.songKey(it.title, it.artist) }

    /** First credited artist, as normalised inside a song key. */
    private fun artistKey(songKey: String) = songKey.substringAfter('|')

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
        Log.d(tag, "Notified of external download: ${track.title} (Key: $key)")

        // Also add to download tracking for UI if requested
        if (addToUi) {
            addDownloadTracking(track.title, track.artist, "manual")
        }

        // Auto-remove after 5 minutes to prevent permanent blocking in case of failure
        serviceScope.launch {
            kotlinx.coroutines.delay((5 * 60 * 1000L).milliseconds)
            _externalDownloads.remove(key)
        }
    }

    /**
     * Initialize the queue with a list of tracks.
     * This should be called when a user selects a new song from search or another screen.
     *
     * IMPORTANT: This cancels all pending recommendation downloads from the previous song
     * to prevent queue pollution with old recommendations. When preserveHistory=false (the
     * default), this also clears all session-scoped state (history, recent artists, session
     * dedup set) so a fresh session does not get poisoned by the previous one. Pass
     * preserveHistory=true ONLY when re-initializing within the same logical session
     * (e.g. clicking a track in the existing queue to reseat the cursor).
     *
     * @param tracks Initial queue
     * @param isRadioMode If true, recommendations use only the seed track (no ensemble)
     * @param preserveHistory If true, keep playedTracksHistory / recentArtists / the
     *                       recommendation reserve across the call
     */
    fun initializeQueue(tracks: List<Track>, isRadioMode: Boolean = false, preserveHistory: Boolean = false) {
        // A new session drops the old reserve and in-flight resolves so nothing from the previous
        // song leaks in. Re-seating the cursor inside the same session keeps the reserve.
        if (!preserveHistory) cancelPendingRecommendationDownloads()

        _currentQueue.value = tracks.toMutableList()

        if (!preserveHistory) {
            playedTracksHistory.clear()
            // A fresh session must also reset recency- and dedup-tracking, otherwise
            // ensemble seeds, the offline scorer's "recently heard artist" penalty, and
            // the session dedup set all reflect the previous session's tastes.
            recentArtists.clear()
        }

        Log.d(
            tag,
            "Queue initialized with ${tracks.size} tracks (cancelled previous recommendations, preserveHistory: $preserveHistory)"
        )

        // Top the lookahead window up from the radio when the queue is shorter than it.
        tracks.firstOrNull()?.let { first ->
            if (isRadioMode) fetchAndQueueRecommendations(first, true)
            else requestFill(first, tracks.size - 1)
        }
    }

    /**
     * Updates the played tracks history (e.g. from MusicViewModel when seeking in an existing queue)
     * so that ensemble recommendations have accurate context.
     *
     * Deduplication key is track `uuid`. Existing history UUIDs are materialized into a set so
     * merge cost is O(h + n) (h existing, n incoming) instead of repeated linear checks.
     * 
     * @param history The list of tracks that were already played
     */
    fun updateHistory(history: List<Track>) {
        if (history.isNotEmpty()) {
            val toKeep = history.takeLast(50) // Manage arbitrary history size
            // Merge with existing avoiding duplicates
            val existingIds = playedTracksHistory.map { it.uuid }.toSet()
            val newTracks = toKeep.filter { it.uuid !in existingIds }
            playedTracksHistory.addAll(newTracks)
            while (playedTracksHistory.size > 100) playedTracksHistory.removeFirst()
        }
    }

    /**
     * Add a track to the end of the queue.
     *
     * Deduplication is two-layered:
     * 1. By `uuid` — if the same track instance already exists, it is moved to the end.
     * 2. By normalized (title, artist) — if a different UUID for the same song exists
     *    (can happen when two parallel recommendation downloads of the same song race
     *    each other before either one indexes into the DB), the existing entry stays
     *    and the new one is dropped, preventing duplicate poisoning.
     *
     * @param track Track to add
     * @return true when the track was newly added (not moved or dropped as a duplicate)
     */
    fun addToQueue(track: Track): Boolean {
        val currentList = _currentQueue.value.toMutableList()

        // Check if track already exists (Move operation by uuid)
        val existingIndex = currentList.indexOfFirst { it.uuid == track.uuid }
        if (existingIndex != -1) {
            currentList.removeAt(existingIndex)
            currentList.add(track)
            _currentQueue.value = currentList
            Log.d(tag, "Moved existing track to end of queue: ${track.title}")
            return false
        }

        // Same song under a different UUID — drop to prevent duplicate poisoning.
        val sameSongExists = currentList.any {
            it.title.equals(track.title, ignoreCase = true) &&
                    it.artist.equals(track.artist, ignoreCase = true)
        }
        if (sameSongExists) {
            Log.d(
                tag,
                "Skipping duplicate addToQueue for same title+artist: ${track.title} by ${track.artist}"
            )
            return false
        }

        currentList.add(track)
        _currentQueue.value = currentList
        Log.d(tag, "Added to queue: ${track.title}")
        return true
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
        Log.d(tag, "Removed from queue: $trackId")

        // The window refill is single-flight and a no-op when the lookahead is already satisfied.
        currentList.firstOrNull()?.let { requestFill(it, currentList.size - 1) }
    }

    /**
     * Replace a track in the queue with an updated version (e.g. stream promoted to download).
     * Keeps the same position in the queue.
     *
     * @param trackId UUID of the track to replace
     * @param updatedTrack The new Track object with updated metadata/paths
     */
    fun replaceTrackInQueue(trackId: String, updatedTrack: Track) {
        val currentList = _currentQueue.value.toMutableList()
        val index = currentList.indexOfFirst { it.uuid == trackId }
        if (index != -1) {
            currentList[index] = updatedTrack
            _currentQueue.value = currentList
            Log.d(tag, "Replaced track in queue at index $index: ${updatedTrack.title}")
        }
    }

    // Recent artists tracking for recommendation variety
    private val recentArtists = java.util.LinkedList<String>()

    /**
     * Clear the entire queue.
     */


    /**
     * Maintains a recency-ordered artist list used to diversify future recommendations.
     */
    private fun addToRecentArtists(artist: String) {
        // Handle multiple artists (split by comma, &, etc. if needed, but simple addition is fine for now)
        // We want to track distinct artist names
        if (artist.isNotBlank()) {
            recentArtists.remove(artist) // Move to end if exists
            recentArtists.add(artist)
            if (recentArtists.size > 50) {
                recentArtists.removeFirst()
            }
            Log.d(tag, "Updated recent artists. Count: ${recentArtists.size}. Latest: $artist")
        }
    }

    // ── Recommendation engine ────────────────────────────────────────────────────────────
    // One YouTube Music radio (~49 songs) is kept in `reserve`. Only `lookahead` songs ahead of
    // the playing track are resolved (Spotify match + stream/download) and put in the queue;
    // whenever a song starts, the window is topped back up from the reserve. When the reserve
    // runs dry it is refilled from a radio seeded by the last queued song.
    //
    // Identity: YouTube video id plus a normalised song key (brackets/punctuation stripped, first
    // artist), checked against the queue, history and everything already seen this session, so the
    // same song can't enter twice under different spellings.

    private val reserve = java.util.concurrent.ConcurrentLinkedDeque<RecommenderApi.YouTubeRecommendation>()
    private val seen: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    private val claimed: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    /** Songs seen this session (title key + length, so two recordings of one title stay two). */
    private val seenSongs = com.example.juke.network.SongIndex()
    private val fillMutex = Mutex()
    private val resolveSlots = Semaphore(3)
    private val sessionGen = java.util.concurrent.atomic.AtomicInteger(0)
    @Volatile private var sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var seedVideoId: String? = null
    /** Radio seeds already pulled this session; a radio returns the same list, so never repeat one. */
    private val usedSeeds: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    /** Position of each reserve song (by video id) in the radio it came from; drives relevance. */

    /** An Up Next song picked from the radio but not fetched yet (shown greyed in the queue). */
    data class PendingSong(
        val id: String,
        val title: String,
        val artist: String,
        val thumbnailUrl: String,
        val duration: String?,
        val loading: Boolean
    )

    /** Picked songs waiting to be fetched, in queue order. */
    private val pendingRecs = java.util.concurrent.ConcurrentLinkedDeque<RecommenderApi.YouTubeRecommendation>()
    /** Fetches in progress by video id; a tap and the background fill share one fetch. */
    private val inFlight = java.util.concurrent.ConcurrentHashMap<String, Deferred<Track?>>()
    /** Video ids already delivered to the queue this session. */
    private val delivered: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    private val _pending = MutableStateFlow<List<PendingSong>>(emptyList())
    val pending: StateFlow<List<PendingSong>> = _pending.asStateFlow()

    private fun publishPending() {
        _pending.value = pendingRecs.map { rec -> pendingSong(rec, loading = inFlight.containsKey(rec.id)) }
    }

    /**
     * "Play next" copies the listener asked for while the song was still waiting: shown greyed
     * right after the playing song until fetched, then put there in the player.
     */
    private val _heldNext = MutableStateFlow<List<PendingSong>>(emptyList())
    val heldNext: StateFlow<List<PendingSong>> = _heldNext.asStateFlow()
    /** A held copy finished loading: the player should put it right after the playing song. */
    private val _playNextReady = MutableSharedFlow<Track>(extraBufferCapacity = 16)
    val playNextReady: SharedFlow<Track> = _playNextReady.asSharedFlow()
    /** A held copy could not be fetched (title, for the message). */
    private val _playNextFailed = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val playNextFailed: SharedFlow<String> = _playNextFailed.asSharedFlow()
    /** Fetched tracks of waiting songs the listener copied, by video id; the original reuses it. */
    private val copies = java.util.concurrent.ConcurrentHashMap<String, Track>()

    private fun pendingSong(rec: RecommenderApi.YouTubeRecommendation, loading: Boolean) = PendingSong(
        id = rec.id,
        title = rec.title,
        artist = rec.artist,
        thumbnailUrl = "https://i.ytimg.com/vi/${rec.id}/mqdefault.jpg",
        duration = rec.duration,
        loading = loading
    )

    /**
     * The listener swiped a waiting song to play next. The original keeps its place in Up Next;
     * a copy is fetched now (ahead of everything else) and put after whatever is playing once it
     * is ready, so playback carries on with ready songs while it loads.
     */
    fun copyPendingNext(id: String, current: Track): Boolean {
        val rec = pendingRecs.firstOrNull { it.id == id } ?: return false
        if (_heldNext.value.any { it.id == id }) return false
        _heldNext.update { listOf(pendingSong(rec, loading = true)) + it }
        val gen = sessionGen.get()
        sessionScope.launch {
            val track = try {
                copies[rec.id] ?: inFlight[rec.id]?.await() ?: resolveRecommendation(rec, current, allowQueued = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(tag, "Play next fetch failed for ${rec.title}: ${e.message}")
                null
            } finally {
                _heldNext.update { list -> list.filterNot { it.id == id } }
            }
            if (gen != sessionGen.get()) return@launch
            if (track == null) {
                _playNextFailed.tryEmit(rec.title)
                return@launch
            }
            copies[rec.id] = track
            _playNextReady.tryEmit(track)
        }
        return true
    }

    private fun recLength(rec: RecommenderApi.YouTubeRecommendation) = com.example.juke.network.SongTitle.clockSeconds(rec.duration)

    private fun pendingKeys(): com.example.juke.network.SongIndex = com.example.juke.network.SongIndex().also { index ->
        pendingRecs.forEach { index.add(RecommenderApi.songKey(it.title, it.artist), recLength(it)) }
    }

    init {
        // Exposure rows untouched for ~2 months have decayed to nothing.
        serviceScope.launch {
            try {
                exposureDao.pruneOlderThan(System.currentTimeMillis() - Variety.PRUNE_AFTER_MS)
            } catch (e: Exception) {
                Log.w(tag, "Exposure prune failed: ${e.message}")
            }
        }
    }

    /** How many resolved songs to keep ahead of the playing one (Audio Settings). */
    private fun lookahead(): Int = settingsPrefs.getInt("recommendation_count", 5).coerceIn(1, 49)

    /** Start a fresh recommendation session: drops the reserve, seen-set and any in-flight work. */
    private fun resetRecommendationSession() {
        sessionGen.incrementAndGet()
        sessionScope.cancel()
        sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        reserve.clear()
        seen.clear()
        claimed.clear()
        seenSongs.clear()
        usedSeeds.clear()
        pendingRecs.clear()
        inFlight.clear()
        delivered.clear()
        copies.clear()
        _heldNext.value = emptyList()
        publishPending()
        seedVideoId = null
        _recStatus.value = RecStatus()
        _downloadingTracks.update { list -> list.filterNot { it.source == "recommendation" } }
    }

    private fun songKey(track: Track) = RecommenderApi.songKey(track.title, track.artist)

    /**
     * Keys that must not be queued again: the queue and the playing song always, and recently
     * played songs unless the listener has replayed them today (then they may come back).
     */
    private fun queuedKeys(current: Track): com.example.juke.network.SongIndex {
        val index = com.example.juke.network.SongIndex()
        (_currentQueue.value + current).forEach { index.add(songKey(it), it.durationSec) }
        playedTracksHistory.toList().forEach {
            val key = songKey(it)
            if (dailyPlayCount(key) < indulgePlays) index.add(key, it.durationSec)
        }
        return index
    }

    /** Songs queued after [current], using the larger of our own count and the caller's. */
    private fun upcomingAfter(current: Track, reported: Int): Int {
        val queue = _currentQueue.value
        val idx = queue.indexOfFirst { it.uuid == current.uuid }
        val own = if (idx >= 0) queue.size - 1 - idx else 0
        return maxOf(own, reported, 0)
    }

    /**
     * Called every time a song starts. [remaining] is how many songs the player still has queued
     * after it. Tops the resolved window back up to the user's lookahead.
     */
    fun onPlaybackAdvanced(track: Track, remaining: Int) = requestFill(track, remaining)

    /**
     * Ask for recommendations based on [currentTrack]. [isRadioMode] (manual refresh) starts a
     * new radio from this song and adds a full lookahead of fresh songs.
     */
    fun fetchAndQueueRecommendations(currentTrack: Track, isRadioMode: Boolean = false) {
        if (isRadioMode) {
            resetRecommendationSession()
            requestFill(currentTrack, remaining = 0, ignoreQueue = true)
        } else {
            requestFill(currentTrack, _currentQueue.value.size - 1)
        }
    }

    private fun requestFill(current: Track, remaining: Int, ignoreQueue: Boolean = false) {
        val gen = sessionGen.get()
        sessionScope.launch {
            fillMutex.withLock {
                if (gen != sessionGen.get()) return@withLock
                try {
                    fillWindow(current, remaining, ignoreQueue, gen)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(tag, "Error filling recommendation window: ${e.message}", e)
                }
            }
        }
    }

    /**
     * Up Next shows the whole radio, in YouTube Music's order, as waiting songs; the first
     * [lookahead] of them (the listener's setting) are fetched and in the player. Fetched songs
     * join the queue one after another, in radio order, as each becomes ready.
     */
    private suspend fun fillWindow(current: Track, reported: Int, ignoreQueue: Boolean, gen: Int) {
        val ready = lookahead()
        val baseline = if (ignoreQueue) upcomingAfter(current, 0) else 0
        fun upcoming() = if (ignoreQueue) upcomingAfter(current, 0) - baseline else upcomingAfter(current, reported)
        var added = 0
        var round = 0

        while (round++ < 6 && gen == sessionGen.get()) {
            // Queue already holds the lookahead (e.g. restored on app start): no radio fetch needed.
            if (ready - upcoming() <= 0) break

            // Running low on waiting songs: continue the radio.
            if (pendingRecs.size < ready) {
                if (reserve.isEmpty()) refillReserve(current)
                moveReserveToPending(current)
            }

            val need = ready - upcoming()
            if (need <= 0) break
            val batch = pendingRecs.filterNot { inFlight.containsKey(it.id) }.take(need)
            if (batch.isEmpty()) break

            Log.d(tag, "Fetching ${batch.size} of $ready ahead (waiting: ${pendingRecs.size})")
            _recStatus.value = RecStatus(batch.size, pendingRecs.size)
            try {
                coroutineScope {
                    val jobs = batch.map { rec -> async { fetchPending(rec, current, priority = false) } }
                    // Fetch in parallel, deliver in radio order: each song joins the queue as soon
                    // as it and the ones before it are ready.
                    batch.zip(jobs).forEach { (rec, job) ->
                        val track = job.await() ?: return@forEach
                        if (gen != sessionGen.get()) return@forEach
                        deliver(rec, track)
                        added++
                    }
                }
            } finally {
                if (gen == sessionGen.get()) _recStatus.value = RecStatus(0, pendingRecs.size)
            }
        }

        if (added == 0 && pendingRecs.isEmpty() && gen == sessionGen.get() && ready - upcoming() > 0) {
            Log.d(tag, "Online radio produced nothing. Falling back to offline library.")
            fetchOfflineRecommendations(current, ready - upcoming())
        }
    }

    /**
     * Move the reserve into Up Next in radio order, dropping anything already queued. Songs the
     * listener has heard, skipped or been shown a lot lately move behind the fresh ones; nothing
     * else is reordered, so the queue follows YouTube Music's radio.
     */
    private suspend fun moveReserveToPending(current: Track) {
        if (reserve.isEmpty()) return
        val blocked = queuedKeys(current).also { it.addAll(pendingKeys()) }
        val incoming = mutableListOf<Pair<String, RecommenderApi.YouTubeRecommendation>>()
        while (true) {
            val rec = reserve.pollFirst() ?: break
            val key = RecommenderApi.songKey(rec.title, rec.artist)
            if (blocked.add(key, recLength(rec))) incoming += key to rec
        }
        if (incoming.isEmpty()) return
        val now = System.currentTimeMillis()
        val exposure = try { exposureFor(incoming.mapTo(HashSet()) { it.first }) } catch (e: Exception) { emptyMap() }
        val favourites = try { favouriteKeys() } catch (e: Exception) { emptySet() }
        val (fresh, stale) = incoming.partition { (key, _) ->
            key in favourites || dailyPlayCount(key) >= indulgePlays ||
                Variety.freshness(exposure[key], now) >= STALE_FRESHNESS
        }
        pendingRecs.addAll(fresh.map { it.second } + stale.map { it.second })
        publishPending()
        Log.d(tag, "Up Next +${incoming.size} from the radio (${stale.size} heard lately moved back)")
    }

    /**
     * Fetch one waiting song, or join the fetch already running for it. [priority] fetches (a tap)
     * skip the background concurrency limit. The song leaves the waiting list either way.
     */
    private suspend fun fetchPending(
        rec: RecommenderApi.YouTubeRecommendation,
        current: Track,
        priority: Boolean
    ): Track? {
        // The listener already fetched it as a "play next" copy: reuse that.
        copies[rec.id]?.let { track ->
            pendingRecs.removeIf { it.id == rec.id }
            publishPending()
            return track
        }
        val job = inFlight.computeIfAbsent(rec.id) {
            sessionScope.async {
                if (priority) resolveRecommendation(rec, current)
                else resolveSlots.withPermit { resolveRecommendation(rec, current) }
            }
        }
        publishPending()
        return try {
            job.await()
        } finally {
            inFlight.remove(rec.id, job)
            pendingRecs.removeIf { it.id == rec.id }
            publishPending()
        }
    }

    /** Add a fetched song to the queue once (a tap and the fill may both finish it). */
    @Synchronized
    private fun deliver(rec: RecommenderApi.YouTubeRecommendation, track: Track): Track {
        if (!delivered.add(rec.id)) {
            return _currentQueue.value.firstOrNull { songKey(it) == songKey(track) } ?: track
        }
        seenSongs.add(songKey(track), track.durationSec)
        // The listener also queued a copy of this song: the original still takes its own place.
        val copied = copies.containsKey(rec.id)
        if (copied) {
            _currentQueue.update { it + track }
        } else if (!addToQueue(track)) {
            // Same song already queued under another entry: play that one.
            return _currentQueue.value.firstOrNull { songKey(it) == songKey(track) } ?: track
        }
        recordExposure(songKey(track), Variety.Event.RECOMMENDED)
        seedVideoId = rec.id
        Log.d(tag, "Recommended: ${track.title}")
        _recommendationsAdded.tryEmit(Added(listOf(track), allowDuplicates = copied))
        return track
    }

    /**
     * The listener tapped a waiting Up Next song: fetch it now (ahead of the background fill) and
     * return the queued track to play, or null when it can't be fetched.
     */
    suspend fun fetchPendingNow(id: String, current: Track): Track? {
        val rec = pendingRecs.firstOrNull { it.id == id } ?: return null
        val gen = sessionGen.get()
        val track = fetchPending(rec, current, priority = true) ?: return null
        if (gen != sessionGen.get()) return null
        // Jumping ahead skips the waiting songs above it, as in any queue.
        val skipped = pendingRecs.takeWhile { it.id != id }.filterNot { inFlight.containsKey(it.id) }
        if (skipped.isNotEmpty()) {
            pendingRecs.removeAll(skipped.toSet())
            publishPending()
        }
        return deliver(rec, track)
    }

    /**
     * Spotify tracks for the waiting songs [ids], in order, for saving the queue as a playlist.
     * Songs without a confident Spotify match are left out.
     */
    suspend fun spotifyTracksForPending(ids: List<String>): List<com.example.juke.models.SpotifyTrack> {
        val recs = ids.mapNotNull { id -> pendingRecs.firstOrNull { it.id == id } }
        val slots = Semaphore(4)
        return coroutineScope {
            recs.map { rec ->
                async {
                    slots.withPermit {
                        try {
                            val match = RecommenderApi.matchOnSpotify(rec) ?: return@withPermit null
                            val spotifyId = match.spotifyUrl.substringAfterLast("/").substringBefore("?")
                            SpotifyApi.getTrack(spotifyId)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w(tag, "No Spotify match to save for ${rec.title}: ${e.message}")
                            null
                        }
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }

    /** The listener removed a waiting song from Up Next. */
    fun dropPending(id: String) {
        if (pendingRecs.removeIf { it.id == id && !inFlight.containsKey(id) }) publishPending()
    }

    /**
     * Refill the reserve with one YouTube Music radio, like YouTube Music continuing a radio: the
     * last song queued from the radio seeds it, then the playing song, then recent queue and
     * history. Only one radio per refill, and only once the previous one ran out, so a stray radio
     * (one seeded by an unrelated upload) can't mix into a good one. Each seed is used once per
     * session. Returns how many new songs were added.
     */
    private suspend fun refillReserve(current: Track): Int {
        seenSongs.addAll(queuedKeys(current))
        current.ytVideoId?.let { seen.add("yt:$it") }

        val blacklist = BlacklistManager.getBlacklistedArtists(context)
        seedVideoId?.let { if (usedSeeds.add(it)) pullRadio(it, current, blacklist).let { n -> if (n > 0) return n } }

        val candidates = (listOf(current) + _currentQueue.value.asReversed().take(4) +
                playedTracksHistory.toList().asReversed().take(10)).distinctBy { it.uuid }
        for (track in candidates) {
            val id = track.ytVideoId
                ?: RecommenderApi.getBestVideoMatch("${track.title} ${track.artist}", track.durationSec, track.artist)
                ?: continue
            if (!usedSeeds.add(id)) continue
            val added = pullRadio(id, current, blacklist)
            if (added > 0) return added
        }
        Log.d(tag, "Reserve refill: no seed produced new songs")
        return 0
    }

    /** One radio for [seedId] into the reserve, skipping everything already seen or queued. */
    private suspend fun pullRadio(seedId: String, current: Track, blacklist: Set<String>): Int {
        seen.add("yt:$seedId")
        // Direct YouTube Music and the user's signed-in backend radio (order: personalised setting).
        // If neither source supplies usable tracks, fillWindow falls back to the local library.
        val radio = com.example.juke.network.JukesApi.fullRadio(seedId)
        val blocked = queuedKeys(current)
        var added = 0
        for (rec in radio) {
            if (RecommenderApi.isSpamTitle(rec.title)) continue
            if (blacklist.isNotEmpty() &&
                (BlacklistManager.containsBlacklistedArtist(context, rec.artist, blacklist) ||
                        BlacklistManager.titleContainsBlacklistedArtist(context, rec.title, blacklist))
            ) continue
            // Both ids must be new: same video, or same song under another upload.
            // Songs replayed today are on repeat, so the session's seen-set no longer blocks them
            // (the queue itself still does, via queuedKeys).
            val key = RecommenderApi.songKey(rec.title, rec.artist)
            val onRepeat = dailyPlayCount(key) >= indulgePlays
            val newVideo = seen.add("yt:${rec.id}")
            val newSong = seenSongs.add(key, recLength(rec))
            if ((!newVideo || !newSong) && !onRepeat) continue
            if (blocked.contains(key, recLength(rec))) continue
            reserve.addLast(rec)
            added++
        }
        Log.d(tag, "Radio for seed $seedId: ${radio.size} items, $added new")
        return added
    }

    /** Spotify match, then stream or download. Null means skip this song. */
    private suspend fun resolveRecommendation(
        rec: RecommenderApi.YouTubeRecommendation,
        current: Track,
        allowQueued: Boolean = false
    ): Track? {
        val validated = try {
            RecommenderApi.matchOnSpotify(rec)
        } catch (_: OfflineException) {
            return null
        } ?: return null

        val key = RecommenderApi.songKey(validated.title, validated.artist)
        val spotifyId = validated.spotifyUrl.substringAfterLast("/").substringBefore("?")
        val inQueue = _currentQueue.value.any { it.spotifyId == spotifyId } ||
            queuedKeys(current).contains(key, validated.durationSec)
        // [allowQueued]: a "play next" copy the listener asked for, even though it is queued.
        // Claimed by Spotify id: two versions of one title are two different tracks.
        if (!allowQueued && (inQueue || !claimed.add(spotifyId))) {
            Log.d(tag, "Skipping already-queued song: ${validated.title}")
            return null
        }

        // Already in the library → reuse it instead of downloading again.
        val candidates = trackDao.findTracksByTitleAndDuration(validated.title, validated.durationSec)
        candidates.find { ArtistUtils.areArtistsEqual(it.artist, validated.artist) && it.localUri != null }
            ?.let { return it.toTrack() }

        val info = DownloadInfo(validated.title, validated.artist, "recommendation")
        _downloadingTracks.update { it + info }
        try {
            val song = SpotifyApi.spotifyTrackToSong(SpotifyApi.getTrack(spotifyId))
            return if (isStreamMode) {
                // Pin queued uuids so cache eviction never removes a file about to be played.
                val pinned = _currentQueue.value.map { it.uuid }.toSet()
                musicService.streamTrack(song, pinnedUuids = pinned).also {
                    trackDao.insertTrack(it.toEntity())
                }
            } else {
                musicService.smartDownloadAndIndex(song)
            }
        } catch (e: CancellationException) {
            if (!allowQueued) claimed.remove(spotifyId)
            throw e
        } catch (e: Exception) {
            Log.e(tag, "Error resolving ${validated.title}: ${e.message}", e)
            if (!allowQueued) claimed.remove(spotifyId)
            return null
        } finally {
            _downloadingTracks.update { list ->
                val i = list.indexOf(info)
                if (i >= 0) list.toMutableList().apply { removeAt(i) } else list
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
     * - up to +25: play count, log-scaled so a few plays already count and a thousand don't dominate
     * - up to +15: artist affinity, from total plays of that artist across the library
     * - +6 / +3: played within the last 2 weeks / 2 months (current rotation); +4 never played (explore)
     * - +5 replayed today (on repeat); -10 heard once today or in the last 24 hours
     * - -20: Artist was recently played (last 10 tracks) — prevents same-artist flooding
     * - small random jitter, and picks are spread out so one artist can't fill the batch
     *
     * Tracks already in the queue or with no local file are excluded.
     * If no tracks score above 0, falls back to favourites / most-played.
     *
     * @param currentTrack The currently playing track to seed the queue from
     */
    private suspend fun fetchOfflineRecommendations(currentTrack: Track, limit: Int) {
        try {
            val allDownloaded = trackDao.getDownloadedTracks()

            if (allDownloaded.isEmpty()) {
                Log.d(tag, "Offline fallback: library is empty, nothing to queue")
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

            // Total plays per primary artist, so favourite artists rank above one-off plays.
            val artistPlays = HashMap<String, Int>()
            allDownloaded.forEach { e ->
                val a = parseArtistNames(e.artist).firstOrNull() ?: return@forEach
                artistPlays[a] = (artistPlays[a] ?: 0) + e.playCount
            }
            val blocked = queuedKeys(currentTrack)
            val exposure = try {
                exposureFor(allDownloaded.map { RecommenderApi.songKey(it.title, it.artist) })
            } catch (e: Exception) { emptyMap() }

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

                    // Play count, log-scaled: 5 plays ≈ +11, 20 ≈ +18, 100 ≈ +25 (capped)
                    score += minOf(25, (kotlin.math.ln(1.0 + track.playCount) * 6).toInt())

                    // Artist affinity from the whole library's listening
                    val primary = trackArtistNames.firstOrNull()
                    if (primary != null) {
                        score += minOf(15, (kotlin.math.ln(1.0 + (artistPlays[primary] ?: 0)) * 3).toInt())
                    }

                    // Current rotation vs. forgotten vs. never heard
                    val lastMs = track.lastPlayedAt?.toLongOrNull()
                    if (track.playCount == 0) {
                        score += 4
                    } else if (lastMs != null) {
                        val days = (now - lastMs) / oneDayMs
                        if (days in 1..13) score += 6 else if (days in 14..60) score += 3
                    }

                    // Variety: the same top songs must not win every time
                    score += kotlin.random.Random.nextInt(0, 5)

                    // Replayed today (on repeat): +5. Otherwise up to -25 by recent exposure across
                    // sessions (plays, early skips, times recommended); favourites are exempt.
                    val key = songKey(track)
                    val playsToday = dailyPlayCount(key)
                    val lastPlayedMs = track.lastPlayedAt?.toLongOrNull()
                    val seen = exposure[key]
                    if (playsToday >= indulgePlays) {
                        score += 5
                    } else if (seen != null && !track.isFavourite) {
                        score -= ((1.0 - Variety.freshness(seen, now)) * 25).toInt()
                    } else if (playsToday > 0 || (lastPlayedMs != null && (now - lastPlayedMs) < oneDayMs)) {
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
                .filter { it.score > 0 && !blocked.contains(songKey(it.track), it.track.durationSec) }
                .sortedByDescending { it.score }

            Log.d(
                tag,
                "Offline fallback: scored ${scored.size} candidates from ${allDownloaded.size} library tracks"
            )

            val selected = if (scored.isNotEmpty()) {
                // Greedy pick: each song already chosen by the same artist costs 8 points.
                val pool = scored.toMutableList()
                val picked = mutableListOf<Track>()
                while (picked.size < limit && pool.isNotEmpty()) {
                    val best = pool.maxByOrNull { c ->
                        c.score - 8 * picked.count { ArtistUtils.areArtistsEqual(it.artist, c.track.artist) }
                    }!!
                    picked += best.track
                    pool.remove(best)
                }
                picked
            } else {
                // Last resort: favourites first, then most played
                Log.d(tag, "Offline fallback: no scored candidates, using favourites/most-played")
                allDownloaded
                    .filter {
                        it.uuid != currentTrack.uuid && !currentQueueUuids.contains(it.uuid) && isLocalFilePlayable(
                            it.localUri
                        )
                                && !BlacklistManager.containsBlacklistedArtist(
                            context,
                            it.artist,
                            blacklist
                        )
                    }
                    .map { it.toTrack() }
                    .sortedWith(compareByDescending<Track> { it.isFavourite }.thenByDescending { it.playCount })
                    .take(limit)
            }

            if (selected.isEmpty()) {
                Log.d(tag, "Offline fallback: no tracks available to queue")
                return
            }

            Log.d(tag, "Offline fallback: queuing ${selected.size} tracks")
            val fresh = selected.filter { track ->
                addToQueue(track).also { if (it) Log.d(tag, "Offline queued: ${track.title} by ${track.artist}") }
            }
            fresh.forEach { recordExposure(songKey(it), Variety.Event.RECOMMENDED) }
            if (fresh.isNotEmpty()) _recommendationsAdded.tryEmit(Added(fresh))

        } catch (e: Exception) {
            Log.e(tag, "Error in offline fallback: ${e.message}", e)
        }
    }

    /**
     * Returns whether a local URI is immediately playable (exists, readable, and non-empty).
     */
    private fun isLocalFilePlayable(uri: String?): Boolean {
        if (uri == null) return false
        return try {
            val file = File(uri)
            file.exists() && file.canRead() && file.length() > 0
        } catch (e: Exception) {
            Log.e(tag, "isLocalFilePlayable failed for uri=$uri", e)
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
     * Ensure the next 2 songs in queue are downloaded.
     * 
     * This pre-downloads upcoming tracks to ensure smooth playback.
     */
    /**
     * Ensure the upcoming 3 songs in queue are ready (downloaded or valid stream URL).
     * Validates local file existence and stream URL expiry.
     * Removes tracks if offline and unavailable to prevent playback stoppage.
     */
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun ensureUpcomingTracksReady() {
        serviceScope.launch {
            val queue = _currentQueue.value
            val upcoming = queue.take(3) // Pre-check the next 3 tracks

            val isOffline = run {
                val cm =
                    context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
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
                        Log.e(tag, "File missing or empty for ${track.title}: ${track.localUri}")
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
                                Log.d(
                                    tag,
                                    "Stream URL for ${track.title} is expiring soon, scheduling refresh."
                                )
                                needsRefresh = true
                            }
                        } else if (url.contains("googleusercontent.com/spotify.com")) {
                            // Dummy URL that hasn't been resolved to a real spotmate stream URL yet
                            needsRefresh = true
                        }
                    } catch (e: Exception) {
                        Log.w(tag, "Error checking stream expiry for ${track.title}: ${e.message}")
                    }
                }

                if (needsRefresh) {
                    if (isOffline) {
                        // Offline and missing file or needing refresh -> remove from queue to prevent playback stoppage
                        Log.w(
                            tag,
                            "Device is offline and track ${track.title} is unavailable. Removing from queue."
                        )
                        removeFromQueue(track.uuid)
                        withContext(Dispatchers.Main) {
                            PlaybackManager.getInstance(context).removeDeletedTrackFromQueue(track.uuid)
                        }
                    } else {
                        // Online -> Prepare/Refresh
                        Log.d(
                            tag,
                            "Track not ready/expired: ${track.title}, triggering preparation/refresh"
                        )
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
                                val pinnedUuids = _currentQueue.value.map { it.uuid }.toSet()
                                val updatedTrack = if (isStreamMode || track.isStream) {
                                    musicService.streamTrack(s, preferredUuid = track.uuid, pinnedUuids = pinnedUuids)
                                } else {
                                    musicService.smartDownloadAndIndex(s)
                                }

                                // Update it in the PlaybackManager's queue silently (must be on main thread)
                                withContext(Dispatchers.Main) {
                                    PlaybackManager.getInstance(context)
                                        .replaceTrackInQueue(track.uuid, updatedTrack)
                                }

                                // Update in our local queue representation
                                val currentList = _currentQueue.value.toMutableList()
                                val qIndex = currentList.indexOfFirst { it.uuid == track.uuid }
                                if (qIndex != -1) {
                                    currentList[qIndex] = updatedTrack
                                    _currentQueue.value = currentList
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(tag, "Error emergency preparing ${track.title}: ${e.message}", e)
                            // Remove from queue if recovery completely fails to avoid blocking playback
                            removeFromQueue(track.uuid)
                            withContext(Dispatchers.Main) {
                                PlaybackManager.getInstance(context)
                                    .removeDeletedTrackFromQueue(track.uuid)
                            }
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
            Log.d(tag, "Pre-fetch triggered (Time remaining: ${timeRemaining}ms)")
            ensureUpcomingTracksReady() // <-- Updated from ensureNext2Ready()
        }
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
        resetRecommendationSession()
        Log.d(tag, "Recommendation session reset (reserve, seen-set and in-flight resolves cleared)")
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
     * Cancel all downloads and cleanup.
     */
    fun cleanup() {
        cancelPendingRecommendationDownloads()
        sessionScope.cancel()
        serviceScope.cancel()
        instance = null
        Log.d(tag, "QueueManager cleaned up and instance reset")
    }
}
