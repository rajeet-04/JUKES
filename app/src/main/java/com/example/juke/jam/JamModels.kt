package com.example.juke.jam

import kotlinx.serialization.Serializable

/** A song as it travels in a Jam: enough for every phone to find and stream it, nothing device-local. */
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

sealed interface JamOp {
    data class Add(val id: String, val track: JamTrack, val afterId: String? = null) : JamOp
    data class Remove(val id: String) : JamOp
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
    is JamOp.Remove -> filter { it.id != op.id }
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
