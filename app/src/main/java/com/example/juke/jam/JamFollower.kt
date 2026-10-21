package com.example.juke.jam

import android.util.Log
import com.example.juke.models.SpotifyTrack
import com.example.juke.network.JukesApi
import com.example.juke.network.SpotifyApi
import com.example.juke.repositories.SharedTrack
import com.example.juke.repositories.toSpotifyTrack
import com.example.juke.viewmodels.MusicViewModel
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs

/**
 * Follows the room's playback anchor on this phone: starts the room's song, corrects drift (seek or small
 * speed nudges), and reports when a song ends. The drift numbers feed the debug readout in the Jam sheet.
 */
object JamFollower {
    private const val TAG = "JamFollower"
    private const val SEEK_ABOVE_MS = 300L
    private const val NUDGE_ABOVE_MS = 40L
    private const val MIN_SEEK_GAP_MS = 3000L
    private const val LOAD_TIMEOUT_MS = 60_000L

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
    suspend fun run(vm: MusicViewModel) {
        val pm = vm.playbackManager
        var startedFor: String? = null
        var startedAt = 0L
        var gaveUpOn: String? = null
        var lastSeekAt = 0L
        var seeks = 0
        var justSeeked = false
        var lastLanded: Long? = null
        var seekBias = 0L // seeks land this far past the target (negative: short); aim off by it
        var worst = 0L
        _debug.value = Debug()
        val queueManager = com.example.juke.services.QueueManager.getInstance(vm.getApplication())
        var endedReportedFor: String? = null
        var lastWarmKey = ""
        var artFixedFor: String? = null
        queueManager.fillSuspended = true // the room decides what plays next, not radio recommendations
        try {
            while (true) {
                withTimeoutOrNull(1000) { JamSession.playbackChanged.receive() } // a room change wakes us at once
                val st = JamSession.state.value
                val pb = st.playback
                val item = pb?.let { p -> st.queue.firstOrNull { it.id == p.itemId } }
                val cur = vm.uiState.value.currentTrack

                // Head start for what plays next, as soon as the queue is built and whenever it or the current song changes
                val upNext = JamWarmup.nextUp(st.queue, pb?.itemId)
                val warmKey = upNext.joinToString { it.id }
                if (warmKey != lastWarmKey) { lastWarmKey = warmKey; JamWarmup.warm(upNext) }

                if (pb == null || item == null) continue

                if (!sameSong(cur, item)) {
                    if (startedFor == pb.itemId && gaveUpOn != pb.itemId && System.currentTimeMillis() - startedAt > LOAD_TIMEOUT_MS) {
                        gaveUpOn = pb.itemId // others keep listening; this phone can skip to the next song
                        JamSession.toast("Couldn't load \"${item.title}\" on this phone")
                    }
                    if (startedFor != pb.itemId) {
                        startedFor = pb.itemId
                        startedAt = System.currentTimeMillis()
                        // Items added without artwork (or by an older build) get theirs from Spotify here, so this phone shows it too
                        val withArt = if (item.thumb.isNullOrBlank()) JamResolver.artworkFor(listOf(item.spotifyId))[item.spotifyId] else item.thumb
                        val track: SpotifyTrack? = SharedTrack(item.title, item.artist, item.durationSec, item.spotifyId, null, withArt).toSpotifyTrack()
                        if (track != null) vm.playInstantLocal(SpotifyApi.spotifyTrackToSong(track))
                        Log.d(TAG, "Starting '${item.title}' for the Jam")
                    }
                    _debug.value = _debug.value.copy(line = "loading ${item.title}…")
                    continue
                }
                startedFor = pb.itemId

                // Already on this song but it has no artwork here: take the room's (or Spotify's)
                if (cur != null && cur.thumbnailUri.isNullOrBlank() && artFixedFor != pb.itemId) {
                    artFixedFor = pb.itemId
                    val url = item.thumb?.takeIf { it.isNotBlank() } ?: JamResolver.artworkFor(listOf(item.spotifyId))[item.spotifyId]
                    if (url != null) vm.adoptArtwork(cur, url)
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
                if (duration > 0 && position >= duration - 500 && endedReportedFor != pb.itemId) {
                    endedReportedFor = pb.itemId
                    JamSession.ended(pb.itemId) // the room moves everyone on; reporting twice is harmless
                }
                val drift = position - target
                val stream = JukesApi.isStreamPlaylist(cur?.localUri)
                if (justSeeked) {
                    lastLanded = drift; justSeeked = false
                    seekBias = ((seekBias + drift) / 2).coerceIn(-500L, 500L) // smooth, and never trust one odd seek too far
                }
                worst = maxOf(worst, abs(drift))

                var speed = 1f
                if (abs(drift) > SEEK_ABOVE_MS) {
                    if (now - lastSeekAt > MIN_SEEK_GAP_MS) {
                        apply(pm) { pm.seekTo(target - seekBias) }
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
