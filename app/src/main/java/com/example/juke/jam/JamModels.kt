package com.example.juke.jam

import kotlinx.serialization.Serializable

/** A song as it travels in a Jam: enough for every phone to find and stream it, nothing device-local. */
const val MAX_QUEUE = 200

@Serializable
data class JamTrack(
    val spotifyId: String,
    val title: String,
    val artist: String,
    val durationSec: Int = 0,
    val thumb: String? = null
)

/** One queue entry; [id] is chosen by whoever added it, so a retried add can't duplicate the song. */
@Serializable
data class JamItem(
    val id: String,
    val spotifyId: String,
    val title: String,
    val artist: String,
    val durationSec: Int = 0,
    val thumb: String? = null,
    val addedBy: String = ""
)

@Serializable
data class JamMember(val clientId: String, val name: String)

/** What is playing as an anchor (position at [anchorServerMs]); the live position is derived from it. */
@Serializable
data class JamPlayback(val itemId: String, val playing: Boolean, val anchorPosMs: Long, val anchorServerMs: Long) {
    fun positionAt(serverNowMs: Long): Long = if (playing) anchorPosMs + (serverNowMs - anchorServerMs) else anchorPosMs
}

sealed interface JamOp {
    data class Add(val id: String, val track: JamTrack, val afterId: String? = null) : JamOp
    /** A run of songs (an album, a playlist) after [afterId], as one change. */
    data class AddMany(val items: List<Pair<String, JamTrack>>, val afterId: String? = null, val label: String? = null) : JamOp
    data class Remove(val id: String) : JamOp
    data class RemoveMany(val ids: List<String>) : JamOp
    data class Move(val id: String, val beforeId: String? = null) : JamOp
}

/** Mirror of `applyOp` in the Worker's jam-logic.ts: the server orders ops, every phone applies them the same way. */
fun List<JamItem>.applied(op: JamOp, by: String): List<JamItem> = when (op) {
    is JamOp.Add -> {
        if (any { it.id == op.id }) this
        else {
            val item = with(op.track) { JamItem(op.id, spotifyId, title, artist, durationSec, thumb, by) }
            val at = op.afterId?.let { id -> indexOfFirst { it.id == id } } ?: -1
            if (at < 0) this + item else take(at + 1) + item + drop(at + 1)
        }
    }
    is JamOp.AddMany -> {
        val fresh = op.items.filter { (id, _) -> none { it.id == id } }
        val room = MAX_QUEUE - size
        if (fresh.isEmpty() || room <= 0) this
        else {
            val who = if (op.label == "Radio") "Radio" else by // the host's radio top-up is credited to the radio
            val added = fresh.take(room).map { (id, t) -> JamItem(id, t.spotifyId, t.title, t.artist, t.durationSec, t.thumb, who) } // as many as fit
            val at = op.afterId?.let { id -> indexOfFirst { it.id == id } } ?: -1
            if (at < 0) this + added else take(at + 1) + added + drop(at + 1)
        }
    }
    is JamOp.Remove -> filter { it.id != op.id }
    is JamOp.RemoveMany -> { val gone = op.ids.toSet(); filter { it.id !in gone } }
    is JamOp.Move -> {
        val moving = firstOrNull { it.id == op.id }
        if (moving == null) this
        else {
            val rest = filter { it.id != op.id }
            val before = op.beforeId?.let { id -> rest.indexOfFirst { it.id == id } } ?: -1
            if (before < 0) rest + moving else rest.take(before) + moving + rest.drop(before)
        }
    }
}
