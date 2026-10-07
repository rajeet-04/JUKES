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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

        fun getInstance(context: Context): QueueManager {
            return instance ?: synchronized(this) {
                instance ?: QueueManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val tag = "QueueManager"
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
            Log.d(tag, "Stream mode set to: $value")
        }

    // Coroutine scope for background tasks
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Queue state
    private val _currentQueue = MutableStateFlow<List<Track>>(emptyList())
    val currentQueue: StateFlow<List<Track>> = _currentQueue.asStateFlow()

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
    }

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
     */
    fun addToQueue(track: Track) {
        val currentList = _currentQueue.value.toMutableList()

        // Check if track already exists (Move operation by uuid)
        val existingIndex = currentList.indexOfFirst { it.uuid == track.uuid }
        if (existingIndex != -1) {
            currentList.removeAt(existingIndex)
            currentList.add(track)
            _currentQueue.value = currentList
            Log.d(tag, "Moved existing track to end of queue: ${track.title}")
            return
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
            return
        }

        currentList.add(track)
        _currentQueue.value = currentList
        Log.d(tag, "Added to queue: ${track.title}")
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
    private val fillMutex = Mutex()
    private val resolveSlots = Semaphore(3)
    private val sessionGen = java.util.concurrent.atomic.AtomicInteger(0)
    @Volatile private var sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var seedVideoId: String? = null
    /** Radio seeds already pulled this session; a radio returns the same list, so never repeat one. */
    private val usedSeeds: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

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
        usedSeeds.clear()
        seedVideoId = null
        _recStatus.value = RecStatus()
        _downloadingTracks.update { list -> list.filterNot { it.source == "recommendation" } }
    }

    private fun songKey(track: Track) = RecommenderApi.songKey(track.title, track.artist)

    /**
     * Keys that must not be queued again: the queue and the playing song always, and recently
     * played songs unless the listener has replayed them today (then they may come back).
     */
    private fun queuedKeys(current: Track): Set<String> {
        val keys = (_currentQueue.value + current).mapTo(HashSet()) { songKey(it) }
        playedTracksHistory.toList().forEach {
            val key = songKey(it)
            if (dailyPlayCount(key) < indulgePlays) keys += key
        }
        return keys
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

    private suspend fun fillWindow(current: Track, reported: Int, ignoreQueue: Boolean, gen: Int) {
        val target = lookahead()
        var added = 0
        var round = 0
        val baseline = if (ignoreQueue) upcomingAfter(current, 0) else 0

        while (round++ < 4 && gen == sessionGen.get()) {
            val upcoming = if (ignoreQueue) upcomingAfter(current, 0) - baseline
            else upcomingAfter(current, reported)
            val need = target - upcoming
            if (need <= 0) return

            if (reserve.isEmpty() && refillReserve(current) == 0) break

            val queued = queuedKeys(current)
            val batch = mutableListOf<RecommenderApi.YouTubeRecommendation>()
            val batchKeys = mutableSetOf<String>()
            val heardToday = mutableListOf<RecommenderApi.YouTubeRecommendation>()
            while (batch.size < need) {
                val rec = reserve.pollFirst() ?: break
                val key = RecommenderApi.songKey(rec.title, rec.artist)
                if (key in queued) continue
                // Heard once earlier today: hold it back, use it only if nothing fresh is left.
                if (dailyPlayCount(key) in 1 until indulgePlays) { heardToday += rec; continue }
                if (!batchKeys.add(key)) continue
                batch += rec
            }
            for (rec in heardToday) {
                if (batch.size < need && batchKeys.add(RecommenderApi.songKey(rec.title, rec.artist))) batch += rec
                else reserve.addLast(rec) // back to the bottom of the reserve
            }
            if (batch.isEmpty()) continue // reserve drained by duplicates → refill next round

            Log.d(tag, "Resolving ${batch.size} of $target lookahead (reserve left: ${reserve.size})")
            _recStatus.value = RecStatus(batch.size, reserve.size)
            val resolved = try {
                coroutineScope {
                    batch.map { rec -> async { resolveSlots.withPermit { resolveRecommendation(rec, current) } } }
                        .awaitAll()
                }
            } finally {
                if (gen == sessionGen.get()) _recStatus.value = RecStatus(0, reserve.size)
            }
            // Add in radio order so the queue follows YouTube's ranking.
            batch.zip(resolved).forEach { (rec, track) ->
                if (track == null || gen != sessionGen.get()) return@forEach
                seen.add("k:${songKey(track)}")
                addToQueue(track)
                seedVideoId = rec.id
                added++
            }
        }

        if (added == 0 && gen == sessionGen.get() && target - upcomingAfter(current, reported) > 0) {
            Log.d(tag, "Online radio produced nothing. Falling back to offline library.")
            fetchOfflineRecommendations(current, target - upcomingAfter(current, reported))
        }
    }

    /**
     * Refill the reserve from YouTube Music radios. Starts from the newest seed (the last song we
     * queued) and, when that radio has nothing new, reseeds from the last few songs in the queue
     * and then recent history, so a drained radio is continued from where the listening is now
     * instead of falling back to the library. Each seed is used once per session.
     * Returns how many new songs were added.
     */
    private suspend fun refillReserve(current: Track): Int {
        queuedKeys(current).forEach { seen.add("k:$it") }
        current.ytVideoId?.let { seen.add("yt:$it") }

        val blacklist = BlacklistManager.getBlacklistedArtists(context)
        var added = 0

        seedVideoId?.let { if (usedSeeds.add(it)) added += pullRadio(it, current, blacklist) }

        val candidates = (listOf(current) + _currentQueue.value.asReversed().take(4) +
                playedTracksHistory.toList().asReversed().take(10)).distinctBy { it.uuid }
        for (track in candidates) {
            if (added >= lookahead()) break
            val id = track.ytVideoId
                ?: RecommenderApi.getBestVideoMatch("${track.title} ${track.artist}", track.durationSec, track.artist)
                ?: continue
            if (!usedSeeds.add(id)) continue
            added += pullRadio(id, current, blacklist)
        }
        Log.d(tag, "Reserve refill: $added new songs → reserve ${reserve.size}")
        return added
    }

    /** One radio for [seedId] into the reserve, skipping everything already seen or queued. */
    private suspend fun pullRadio(seedId: String, current: Track, blacklist: Set<String>): Int {
        seen.add("yt:$seedId")
        // Prefer direct YouTube Music radio; use the shared backend only when it returns nothing.
        // If neither source supplies usable tracks, fillWindow falls back to the local library.
        val radio = RecommenderApi.fetchFullRadioQueue(seedId).takeIf { it.isNotEmpty() }
            ?: com.example.juke.network.JukesApi.radio(seedId)
            ?: emptyList()
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
            val newSong = seen.add("k:$key")
            if ((!newVideo || !newSong) && !onRepeat) continue
            if (key in blocked) continue
            reserve.addLast(rec)
            added++
        }
        Log.d(tag, "Radio for seed $seedId: ${radio.size} items, $added new")
        return added
    }

    /** Spotify match, then stream or download. Null means skip this song. */
    private suspend fun resolveRecommendation(
        rec: RecommenderApi.YouTubeRecommendation,
        current: Track
    ): Track? {
        val validated = try {
            RecommenderApi.matchOnSpotify(rec)
        } catch (_: OfflineException) {
            return null
        } ?: return null

        val key = RecommenderApi.songKey(validated.title, validated.artist)
        val spotifyId = validated.spotifyUrl.substringAfterLast("/").substringBefore("?")
        val inQueue = _currentQueue.value.any { it.spotifyId == spotifyId } || key in queuedKeys(current)
        if (inQueue || !claimed.add(key)) {
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
            claimed.remove(key)
            throw e
        } catch (e: Exception) {
            Log.e(tag, "Error resolving ${validated.title}: ${e.message}", e)
            claimed.remove(key)
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

                    // Replayed today (on repeat): +5. Otherwise -10 if heard today or in the last 24 hours.
                    val playsToday = dailyPlayCount(songKey(track))
                    val lastPlayedMs = track.lastPlayedAt?.toLongOrNull()
                    if (playsToday >= indulgePlays) {
                        score += 5
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
                .filter { it.score > 0 && songKey(it.track) !in blocked }
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
            selected.forEach { track ->
                addToQueue(track)
                Log.d(tag, "Offline queued: ${track.title} by ${track.artist}")
            }

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
