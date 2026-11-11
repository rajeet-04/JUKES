package com.example.juke.jam

import android.util.Log
import com.example.juke.network.SpotifyApi
import com.example.juke.services.QueueManager
import com.example.juke.viewmodels.MusicViewModel
import kotlinx.coroutines.delay

/**
 * The room's radio: in a normal queue the next songs come from a YouTube Music radio seeded by what is
 * playing. In a Jam one phone runs it for everybody (the host, so five phones don't each add their own),
 * taking songs from the same radio and rules as the normal queue and adding them to the shared queue,
 * tagged "Radio". Anyone can still remove or reorder them. If the host leaves, the next host carries on.
 */
object JamRadio {
    private const val TAG = "JamRadio"
    private const val TICK_MS = 2000L
    private const val AFTER_ADD_MS = 4000L
    private const val AFTER_EMPTY_MS = 30_000L
    private const val NEAR_FULL = MAX_QUEUE - 5

    /** How many songs the radio should add so [want] are queued after the current one; 0 if enough already. */
    fun needed(queueSize: Int, currentIndex: Int, want: Int): Int =
        if (currentIndex < 0 || queueSize >= NEAR_FULL) 0 else (want - (queueSize - currentIndex - 1)).coerceAtLeast(0)

    suspend fun run(vm: MusicViewModel) {
        val queueManager = QueueManager.getInstance(vm.getApplication())
        var quietUntil = 0L
        // Radio songs handed out so far, under the names YouTube gave them. The room stores Spotify's spelling and artist
        // credits, which don't always match, so remembering these is what stops the radio (and a Mix) from repeating itself.
        val handedOut = ArrayDeque<com.example.juke.models.Track>()
        try {
            while (true) {
                delay(TICK_MS)
                val st = JamSession.state.value
                val mixAsked = JamSession.mixRequested.tryReceive().isSuccess
                val isHost = st.status == JamSession.Status.CONNECTED && st.hostId != null && st.hostId == st.you
                if (mixAsked && isHost) {
                    queueManager.restartRadio() // a fresh radio from the playing song; what was handed out stays blocked
                    quietUntil = 0L
                    Log.d(TAG, "Mix: fresh radio, skipping ${handedOut.size} songs already handed out")
                }
                if (!isHost) continue
                val pb = st.playback ?: continue
                val idx = st.queue.indexOfFirst { it.id == pb.itemId }
                val current = vm.uiState.value.currentTrack
                if (idx < 0 || current == null || !JamFollower.sameSong(current, st.queue[idx])) continue // still loading the song

                queueManager.externalQueued = st.queue.map { it.toTrack() } + handedOut // the radio must not repeat anything in the room
                val need = needed(st.queue.size, idx, queueManager.radioLookahead())
                if (need == 0 || System.currentTimeMillis() < quietUntil) continue

                val recs = queueManager.takeRadio(current, need)
                if (recs.isEmpty()) { quietUntil = System.currentTimeMillis() + AFTER_EMPTY_MS; continue }
                recs.forEach {
                    handedOut.addLast(com.example.juke.models.Track(
                        uuid = "radio-${it.id}", title = it.title, artist = it.artist,
                        durationSec = it.duration?.let { d -> SpotifyApi.parseDuration(d) } ?: 0
                    ))
                }
                while (handedOut.size > 300) handedOut.removeFirst()
                val picks = recs.map { JamPick(it.title, it.artist, it.duration?.let { d -> SpotifyApi.parseDuration(d) } ?: 0, null, null) }
                val songs = JamResolver.resolveAll(picks).filterNotNull().distinctBy { it.spotifyId }
                    .filter { s -> st.queue.none { it.spotifyId == s.spotifyId } }
                if (songs.isNotEmpty()) JamSession.addMany(songs, afterId = null, label = "Radio")
                quietUntil = System.currentTimeMillis() + AFTER_ADD_MS
            }
        } finally {
            queueManager.externalQueued = emptyList()
        }
    }
}
