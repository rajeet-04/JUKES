package com.example.juke.jam

import com.example.juke.network.JukesApi

/**
 * Songs about to play get a head start: the backend begins fetching them before anyone asks, so when the room
 * moves on each phone's stream is ready sooner. Fire-and-forget; JukesApi skips songs it warmed recently and
 * stays under the server's rate limit.
 */
object JamWarmup {
    const val COUNT = 4

    /** The next [count] songs after [currentId]; from the top of the queue if nothing is playing yet. */
    fun nextUp(queue: List<JamItem>, currentId: String?, count: Int = COUNT): List<JamItem> {
        val at = queue.indexOfFirst { it.id == currentId } // -1 when there is no current song, which starts the list at the top
        return queue.drop(at + 1).take(count)
    }

    fun warm(items: List<JamItem>) = items.forEach { JukesApi.warmup(it.title, it.artist, it.durationSec * 1000L) }
}
