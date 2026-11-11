package com.example.juke.services

import com.example.juke.models.Track

internal class StreamSeekHandoff {
    private val files = mutableMapOf<String, Track>()
    private var pending: Pair<String, Long>? = null

    fun fileFor(uuid: String): Track? = files[uuid]
    fun trackChanged(uuid: String?) {
        if (pending?.first != uuid) pending = null
    }
    fun defer(uuid: String, positionMs: Long) { pending = uuid to positionMs.coerceAtLeast(0) }
    fun completed(track: Track): Long? {
        files[track.uuid] = track
        return pending?.takeIf { it.first == track.uuid }?.second?.also { pending = null }
    }
}
