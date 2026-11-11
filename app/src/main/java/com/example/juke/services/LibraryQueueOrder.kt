package com.example.juke.services

import com.example.juke.models.Track

/** Move existing songs rather than duplicating them; never move the currently playing song. */
internal object LibraryQueueOrder {
    fun arrange(queue: List<Track>, currentId: String?, tracks: List<Track>, next: Boolean): List<Track> {
        val incoming = tracks.distinctBy { it.uuid }.filterNot { it.uuid == currentId }
        val ids = incoming.map { it.uuid }.toSet()
        val remaining = queue.filterNot { it.uuid in ids }.toMutableList()
        val currentIndex = remaining.indexOfFirst { it.uuid == currentId }
        val insertion = if (next && currentIndex >= 0) currentIndex + 1 else remaining.size
        remaining.addAll(insertion, incoming)
        return remaining
    }
}
