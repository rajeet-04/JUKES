package com.example.juke.home

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Pages through one mood's shelves, three sections at a time, each filled from the shelf's
 * playlist (the next one if the first is thin). No song repeats across the mood.
 */
class MoodPager(private val playlist: suspend (playlistId: String) -> List<FeedSong>) {
    private val mutex = Mutex()
    private val shelves = ArrayDeque<YtmMoods.Shelf>()
    private val seen = HashSet<String>()
    private var produced = 0

    val exhausted: Boolean get() = shelves.isEmpty()

    fun reset(shelves: List<YtmMoods.Shelf>) {
        this.shelves.clear(); this.shelves.addAll(shelves)
        seen.clear(); produced = 0
    }

    suspend fun nextPage(): FeedPager.Page = mutex.withLock {
        val out = mutableListOf<HomeSection>()
        val taken = mutableListOf<YtmMoods.Shelf>()
        var attempts = 0
        var errors = 0
        while (out.size < PAGE_SIZE && attempts < MAX_ATTEMPTS) {
            val batch = (0 until minOf(PAGE_SIZE - out.size, MAX_ATTEMPTS - attempts)).mapNotNull { shelves.removeFirstOrNull() }
            if (batch.isEmpty()) break
            taken += batch
            attempts += batch.size
            val results = coroutineScope { batch.map { async { fill(it) } }.awaitAll() }
            batch.zip(results).forEach { (shelf, songs) ->
                if (songs == null) errors++ else build(shelf, songs)?.let { out += it }
            }
        }
        if (out.isEmpty() && errors > 0 && errors == attempts) {
            taken.asReversed().forEach { shelves.addFirst(it) }
            return@withLock FeedPager.Page(emptyList(), failed = true)
        }
        FeedPager.Page(out, failed = false)
    }

    /** Songs of the shelf's first playlist with enough of them; null when every fetch failed. */
    private suspend fun fill(shelf: YtmMoods.Shelf): List<FeedSong>? {
        if (shelf.songs.isNotEmpty()) return shelf.songs
        var failed = 0
        val tried = shelf.playlists.take(2)
        for (id in tried) {
            val songs = try {
                playlist(id)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                failed++; continue
            }
            if (songs.count { it.key !in seen } >= MIN_SONGS) return songs
        }
        return if (tried.isNotEmpty() && failed == tried.size) null else emptyList()
    }

    private fun build(shelf: YtmMoods.Shelf, songs: List<FeedSong>): HomeSection? {
        val n = produced
        val limit = when { n == 0 -> 20; n % 3 == 2 -> 10; else -> 16 }
        val picked = songs.filter { it.key !in seen }.distinctBy { it.key }.take(limit)
        if (picked.size < MIN_SONGS) return null
        seen += picked.map { it.key }
        produced++
        val id = "m$n-${shelf.playlists.firstOrNull() ?: shelf.title}"
        return when {
            n == 0 -> HomeSection.QuickPicks(id, shelf.title, picked, seed = null)
            n % 3 == 2 -> HomeSection.Ranked(id, shelf.title, picked)
            else -> HomeSection.SongRow(id, shelf.title, picked, seed = null)
        }
    }

    private companion object {
        const val PAGE_SIZE = 3
        const val MAX_ATTEMPTS = 6
        const val MIN_SONGS = 4
    }
}
