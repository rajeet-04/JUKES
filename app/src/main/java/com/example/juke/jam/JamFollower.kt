package com.example.juke.jam

import android.util.Log
import com.example.juke.models.SpotifyTrack
import com.example.juke.network.JukesApi
import com.example.juke.network.SpotifyApi
import com.example.juke.repositories.SharedTrack
import com.example.juke.repositories.toSpotifyTrack
import com.example.juke.viewmodels.MusicViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

/**
 * Follows the room's playback anchor on this phone: starts the room's song, corrects drift (seek or small
 * speed nudges), and reports when a song ends. The drift numbers feed the debug readout in the Jam sheet.
 */
object JamFollower {
    private const val TAG = "JamFollower"
    private const val SEEK_ABOVE_MS = 300L
    private const val NUDGE_ABOVE_MS = 40L
    private const val MIN_SEEK_GAP_MS = 3000L
    private const val MAX_SEEK_BIAS_MS = 3000L // a stream seek re-buffers; it can land a second or more late
    private const val MAX_MISSED_SEEKS = 4 // per song: past this, drift is better than chopping the audio
    private const val LOAD_TIMEOUT_MS = 60_000L
    /** Upcoming room songs fetched to disk ahead, in parallel, once the playing song is safe. */
    private const val PREFETCH_COUNT = 3
    /** The playing song is safe once it plays from a file, or has this much audio buffered ahead. */
    private const val PREFETCH_BUFFER_MS = 20_000L
    /** A song whose head-start fetch is still running when it comes up: let that finish rather than race it. */
    private const val PREFETCH_FINISH_WAIT_MS = 8_000L

    data class Debug(
        val line: String = "",
        val driftMs: Long = 0,
        val stream: Boolean = false,
        val seeks: Int = 0,
        val lastSeekLandedMs: Long? = null,
        val worstDriftMs: Long = 0
    )

    private val _debug = MutableStateFlow(Debug())
    val debug: StateFlow<Debug> = _debug

    fun sameSong(track: com.example.juke.models.Track?, item: JamItem): Boolean =
        track != null && (
            (track.spotifyId != null && track.spotifyId == item.spotifyId) ||
                (track.title.equals(item.title, true) && track.artist.equals(item.artist, true))
            )

    private inline fun apply(pm: com.example.juke.services.PlaybackManager, block: () -> Unit) {
        pm.jamApplying = true // these commands come from the room; don't send them back to it
        try { block() } finally { pm.jamApplying = false }
    }

    /** Runs until cancelled (when the Jam ends); restores normal speed on the way out. */
    suspend fun run(vm: MusicViewModel) = kotlinx.coroutines.coroutineScope {
        val pm = vm.playbackManager
        // Upcoming room songs already on disk (by Jam item id), and the ones being fetched now
        val prefetched = java.util.concurrent.ConcurrentHashMap<String, com.example.juke.models.Track>()
        val fetching = java.util.Collections.synchronizedSet(HashSet<String>())
        var upSince = 0L // when the room's current song first showed up here
        var upFor: String? = null
        var startedFor: String? = null
        var startedAt = 0L
        var gaveUpOn: String? = null
        var lastSeekAt = 0L
        var seeks = 0
        var justSeeked = false
        var lastLanded: Long? = null
        val seekBias = LongArray(2) // [file, stream]: seeks land this far past the target (negative: short); aim off by it
        var worst = 0L
        var missedSeeks = 0 // seeks in a row on this song that still landed out of sync
        var seekingFor: String? = null
        _debug.value = Debug()
        val queueManager = com.example.juke.services.QueueManager.getInstance(vm.getApplication())
        var endedReportedFor: String? = null
        var lastWarmKey = ""
        var artFixedFor: String? = null
        queueManager.fillSuspended = true // the room decides what plays next, not radio recommendations
        try {
            while (true) {
                withTimeoutOrNull(1000.milliseconds) { JamSession.playbackChanged.receive() } // a room change wakes us at once
                val st = JamSession.state.value
                val pb = st.playback
                val item = pb?.let { p -> st.queue.firstOrNull { it.id == p.itemId } }
                val cur = vm.uiState.value.currentTrack

                // Head start for what plays next, as soon as the queue is built and whenever it or the current song changes
                val upNext = JamWarmup.nextUp(st.queue, pb?.itemId)
                val warmKey = upNext.joinToString { it.id }

                // Songs that left the room's queue don't need their head start any more
                prefetched.keys.retainAll(st.queue.mapTo(HashSet()) { it.id })

                if (pb == null || item == null) continue

                if (!sameSong(cur, item)) {
                    if (startedFor == pb.itemId && gaveUpOn != pb.itemId && System.currentTimeMillis() - startedAt > LOAD_TIMEOUT_MS) {
                        gaveUpOn = pb.itemId // others keep listening; this phone can skip to the next song
                        com.example.juke.analytics.AnalyticsManager.log(com.example.juke.analytics.EventType.JAM_SONG_FAILED)
                        JamSession.toast("Couldn't load \"${item.title}\" on this phone")
                    }
                    if (upFor != pb.itemId) { upFor = pb.itemId; upSince = System.currentTimeMillis() }
                    val headStartRunning = item.id in fetching && System.currentTimeMillis() - upSince < PREFETCH_FINISH_WAIT_MS
                    if (startedFor != pb.itemId && !headStartRunning) {
                        startedFor = pb.itemId
                        startedAt = System.currentTimeMillis()
                        // Nothing waits on the network before the song starts: a head-start copy plays from disk,
                        // otherwise it streams now. Full details (all artists, album) when an up-next lookup already
                        // fetched them, else the room's; missing artwork is filled in once it plays.
                        val ready = prefetched.remove(item.id)?.takeIf { t -> t.localUri?.let { java.io.File(it).isFile } == true }
                        if (ready != null) {
                            vm.playPrefetchedLocal(ready)
                            Log.d(TAG, "Starting '${item.title}' for the Jam from its head-start copy")
                        } else {
                            val track: SpotifyTrack? = JamResolver.cachedFullTrack(item.spotifyId)
                                ?: SharedTrack(item.title, item.artist, item.durationSec, item.spotifyId, null, item.thumb).toSpotifyTrack()
                            if (track != null) vm.playInstantLocal(SpotifyApi.spotifyTrackToSong(track))
                            Log.d(TAG, "Starting '${item.title}' for the Jam")
                        }
                    }
                    _debug.value = _debug.value.copy(line = "loading ${item.title}…")
                    continue
                }
                startedFor = pb.itemId

                // Already on this song but it has no artwork here: take the room's (or Spotify's)
                if (cur != null && cur.thumbnailUri.isNullOrBlank() && artFixedFor != pb.itemId) {
                    artFixedFor = pb.itemId
                    launch {
                        val url = item.thumb?.takeIf { it.isNotBlank() }
                            ?: JamResolver.artworkFor(listOf(item.spotifyId))[item.spotifyId]
                        val current = vm.uiState.value.currentTrack
                        if (url != null && current?.uuid == cur.uuid && current.thumbnailUri.isNullOrBlank()) {
                            vm.adoptArtwork(current, url)
                        }
                    }
                }

                val now = System.currentTimeMillis()
                val target = pb.positionAt(JamSession.serverNow())
                val playing = pm.isPlayingFlow.value

                if (!pb.playing) {
                    if (playing) apply(pm) { pm.togglePlayPause() }
                    if (abs(pm.getCurrentPosition() - target) > SEEK_ABOVE_MS && now - lastSeekAt > 2000) {
                        apply(pm) { pm.seekTo(target) }; lastSeekAt = now
                    }
                    _debug.value = _debug.value.copy(line = "paused at ${target / 1000}s")
                    continue
                }
                if (!playing) {
                    apply(pm) { pm.togglePlayPause() } // the room says play
                    continue
                }

                val position = pm.getCurrentPosition()
                val duration = pm.getDuration()

                // The playing song comes first. Once it is safe (playing from a file, or well buffered), fetch the
                // next few room songs to disk in parallel so each change plays locally, with nothing to re-buffer.
                val safe = cur?.localUri?.startsWith("http") == false || pm.bufferedAheadMs() >= PREFETCH_BUFFER_MS
                if (safe) {
                    if (warmKey != lastWarmKey) { lastWarmKey = warmKey; JamWarmup.warm(upNext) }
                    val wanted = upNext.take(PREFETCH_COUNT).filter { !prefetched.containsKey(it.id) && it.id !in fetching }
                        .take((PREFETCH_COUNT - fetching.size).coerceAtLeast(0))
                    fetching.addAll(wanted.map { it.id })
                    if (wanted.isNotEmpty()) launch {
                        try {
                            JamResolver.artworkFor(wanted.map { it.spotifyId }) // one request: full details for all of them
                            wanted.map { w ->
                                async {
                                    val t = JamResolver.cachedFullTrack(w.spotifyId)
                                        ?: SharedTrack(w.title, w.artist, w.durationSec, w.spotifyId, null, w.thumb).toSpotifyTrack()
                                    val got = t?.let { vm.prefetchStream(SpotifyApi.spotifyTrackToSong(it), prefetched.values.mapTo(HashSet()) { p -> p.uuid }) }
                                    if (got != null) prefetched[w.id] = got
                                }
                            }.awaitAll()
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.w(TAG, "Head start failed: ${e.message}") // never take the follower down with it
                        } finally {
                            fetching.removeAll(wanted.map { it.id }.toSet())
                        }
                    }
                }
                if (duration > 0 && position >= duration - 500 && endedReportedFor != pb.itemId) {
                    endedReportedFor = pb.itemId
                    JamSession.ended(pb.itemId) // the room moves everyone on; reporting twice is harmless
                }
                val drift = position - target
                val stream = JukesApi.isStreamPlaylist(cur?.localUri)
                if (seekingFor != pb.itemId) {
                    seekingFor = pb.itemId
                    missedSeeks = 0
                    justSeeked = false
                    lastSeekAt = 0
                }
                if (justSeeked) {
                    lastLanded = drift; justSeeked = false
                    // drift is what is left after aiming off by seekBias, so add it on. Halving the sum (as
                    // before) settles at half the re-buffer lag: every seek landed ~0.5 s off and the
                    // follower re-seeked every 3 s for the whole song (#38).
                    val b = if (stream) 1 else 0
                    seekBias[b] = (seekBias[b] + drift).coerceIn(-MAX_SEEK_BIAS_MS, MAX_SEEK_BIAS_MS)
                    missedSeeks = if (abs(drift) > SEEK_ABOVE_MS) missedSeeks + 1 else 0
                }
                worst = maxOf(worst, abs(drift))

                var speed = 1f
                if (abs(drift) > SEEK_ABOVE_MS) {
                    if (now - lastSeekAt > MIN_SEEK_GAP_MS && missedSeeks < MAX_MISSED_SEEKS) {
                        apply(pm) { pm.seekTo(target - seekBias[if (stream) 1 else 0]) }
                        lastSeekAt = now; seeks++; justSeeked = true
                    }
                } else if (abs(drift) > NUDGE_ABOVE_MS && !stream) {
                    speed = if (drift > 0) 0.97f else 1.03f // ahead: slow down, behind: speed up
                }
                pm.setPlaybackSpeed(speed)

                _debug.value = Debug(
                    line = "drift ${if (drift >= 0) "+" else ""}$drift ms · ${if (stream) "stream" else "file"} · speed $speed · clock rtt ${JamSession.clockRttMs} ms",
                    driftMs = drift, stream = stream, seeks = seeks, lastSeekLandedMs = lastLanded, worstDriftMs = worst
                )
            }
        } finally {
            pm.setPlaybackSpeed(1f)
            queueManager.fillSuspended = false
        }
    }
}
