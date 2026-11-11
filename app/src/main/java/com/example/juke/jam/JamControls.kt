package com.example.juke.jam

/**
 * In a Jam the normal player controls (player screen, mini player, notification, headset) drive the room
 * instead of this phone's player: each call returns true when the Jam took the command, false when the
 * caller should carry on as usual (not in a Jam, not connected, or nothing in the room to act on).
 * The phone's player then follows the room like everyone else's.
 */
object JamControls {
    private fun live() = JamSession.state.value.takeIf { it.status == JamSession.Status.CONNECTED }

    fun togglePlayPause(): Boolean {
        val st = live() ?: return false
        val pb = st.playback
        return when {
            pb?.playing == true -> JamSession.pause()
            pb != null -> JamSession.play()
            st.queue.isNotEmpty() -> JamSession.play(st.queue.first().id)
            else -> false
        }
    }

    fun seekTo(positionMs: Long): Boolean {
        if (live()?.playback == null) return false
        return JamSession.seek(positionMs.coerceAtLeast(0))
    }

    fun next(): Boolean {
        val st = live() ?: return false
        val pb = st.playback ?: return false
        val i = st.queue.indexOfFirst { it.id == pb.itemId }
        if (i >= 0 && i + 1 < st.queue.size) JamSession.play(st.queue[i + 1].id)
        return true // at the end of the queue there is nothing to skip to; don't let this phone wander off alone
    }

    /** Like the normal button: past the first 3 seconds it restarts the song, otherwise goes back one. */
    fun previous(): Boolean {
        val st = live() ?: return false
        val pb = st.playback ?: return false
        val i = st.queue.indexOfFirst { it.id == pb.itemId }
        if (pb.positionAt(JamSession.serverNow()) > 3000 || i <= 0) JamSession.seek(0) else JamSession.play(st.queue[i - 1].id)
        return true
    }

    // --- Starting songs and editing the queue: in a Jam these go to the room, not to this phone's player ---

    /** Runs [block] on the connected room and returns true; false (do it locally) if not in a Jam. */
    private inline fun routed(block: (JamSession.UiState) -> Unit): Boolean {
        val st = JamSession.state.value
        if (!st.active) return false
        if (st.status != JamSession.Status.CONNECTED) {
            JamSession.toast("Reconnecting to the Jam…") // not alone-and-local: the room would pull this phone back
            return true
        }
        block(st)
        return true
    }

    private fun currentItemId(st: JamSession.UiState): String? = st.playback?.itemId?.takeIf { id -> st.queue.any { it.id == id } }

    /**
     * Resolves [picks] to Jam songs (matching ones without a Spotify id), then runs [action] with the room as it is then.
     * Songs that can't be matched are skipped, and the user is told.
     */
    private fun withSongs(picks: List<JamPick>, action: (JamSession.UiState, List<JamTrack>) -> Unit) {
        JamSession.launch {
            val resolved = JamResolver.resolveAll(picks)
            val songs = resolved.filterNotNull()
            when {
                songs.isEmpty() -> JamSession.toast(if (picks.size == 1) "Couldn't find this song to share in the Jam" else "Couldn't find these songs to share in the Jam")
                else -> {
                    action(JamSession.state.value, songs)
                    val missed = picks.size - songs.size
                    if (missed > 0) JamSession.toast("$missed song${if (missed == 1) "" else "s"} couldn't be shared and ${if (missed == 1) "was" else "were"} skipped")
                }
            }
        }
    }

    /**
     * A tap that should start [itemId] has to actually do so: if the room hasn't switched to it after a few seconds
     * (a message lost on a bad connection), say it again. Repeating is harmless; the same song just carries on.
     */
    private fun ensurePlaying(itemId: String, regenerate: Boolean) {
        JamSession.launch {
            repeat(2) {
                kotlinx.coroutines.delay(3000)
                val st = JamSession.state.value
                if (!st.active || st.playback?.itemId == itemId || st.queue.none { it.id == itemId }) return@launch
                android.util.Log.w("JamControls", "play for $itemId did not land; sending it again")
                JamSession.play(itemId, regenerate = regenerate)
            }
        }
    }

    /** Play now: the songs go in after the current one and the first starts for everyone. */
    fun playNow(picks: List<JamPick>): Boolean = routed { _ ->
        withSongs(picks) { st, songs ->
            val existing = if (songs.size == 1) st.queue.firstOrNull { it.spotifyId == songs[0].spotifyId } else null
            if (existing != null) { // already in the room: just play that one
                JamSession.play(existing.id)
                ensurePlaying(existing.id, regenerate = false)
            } else {
                JamSession.addMany(songs, currentItemId(st)).firstOrNull()?.let { // a new song: new radio after it
                    JamSession.play(it, regenerate = true)
                    ensurePlaying(it, regenerate = true)
                }
            }
        }
    }

    /** Play next: right after the current song, in the order given. */
    fun addNext(picks: List<JamPick>): Boolean = routed { _ ->
        withSongs(picks) { st, songs ->
            if (JamSession.addMany(songs, currentItemId(st)).isNotEmpty())
                JamSession.toast(if (songs.size == 1) "Plays next in the Jam" else "${songs.size} songs play next in the Jam")
        }
    }

    fun addToEnd(picks: List<JamPick>): Boolean = routed { _ ->
        withSongs(picks) { _, songs ->
            if (JamSession.addMany(songs, null).isNotEmpty())
                JamSession.toast(if (songs.size == 1) "Added to the Jam" else "${songs.size} songs added to the Jam")
        }
    }

    /** The queue sheet's "item at [from] now sits at [to]", as a Jam move. */
    fun move(from: Int, to: Int): Boolean {
        val q = JamSession.state.value.queue
        val item = q.getOrNull(from) ?: return false
        val before = if (to > from) q.getOrNull(to + 1)?.id else q.getOrNull(to)?.id // null puts it last
        return JamSession.move(item.id, before)
    }

    /** Mix: a fresh radio from the current song for everyone. */
    fun mix(): Boolean = routed { st -> if (st.playback != null) JamSession.mix() else JamSession.toast("Play a song first") }

    /** Play the room's own entry [itemId] (a tap or swipe on the room's queue). False if not in a Jam or it isn't there. */
    fun playItem(itemId: String): Boolean {
        val st = JamSession.state.value
        if (!st.active || st.queue.none { it.id == itemId }) return false
        if (st.status != JamSession.Status.CONNECTED) { JamSession.toast("Reconnecting to the Jam…"); return true }
        JamSession.play(itemId)
        ensurePlaying(itemId, regenerate = false)
        return true
    }
}
