package com.example.juke.home

import com.example.juke.models.Track
import com.example.juke.network.RecommenderApi
import com.example.juke.network.SongTitle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The endless part of Home. Each page turns the next seeds into three radio sections, never
 * repeating a song anywhere in the feed (or one already in the library). When the listener's own
 * seeds run out, the first song of every section shown so far becomes a seed, so the feed keeps
 * going into new territory instead of ending.
 */
class FeedPager(
    private val radio: suspend (videoId: String) -> List<RecommenderApi.YouTubeRecommendation>,
    private val isSpam: (String) -> Boolean = RecommenderApi::isSpamTitle,
) {
    data class Page(val sections: List<HomeSection>, val failed: Boolean)

    private class Seed(val track: Track, val derived: Boolean)

    private val mutex = Mutex()
    private val seeds = ArrayDeque<Seed>()
    private val derivedPool = ArrayDeque<Seed>()
    private val seen = HashSet<String>()
    private var produced = 0

    val exhausted: Boolean get() = seeds.isEmpty() && derivedPool.isEmpty()

    fun reset(seeds: List<Track>, knownKeys: Set<String>) {
        this.seeds.clear()
        derivedPool.clear()
        seen.clear()
        produced = 0
        seen += knownKeys
        seeds.filter { it.ytVideoId != null }.distinctBy { it.uuid }
            .forEach { this.seeds.addLast(Seed(it, derived = false)) }
    }

    /** One page at a time: a second caller waits and then gets the page after. */
    suspend fun nextPage(): Page = mutex.withLock {
        val out = mutableListOf<HomeSection>()
        val taken = mutableListOf<Seed>()
        var attempts = 0
        var errors = 0
        while (out.size < PAGE_SIZE && attempts < MAX_ATTEMPTS) {
            val batch = (0 until minOf(PAGE_SIZE - out.size, MAX_ATTEMPTS - attempts))
                .mapNotNull { seeds.removeFirstOrNull() ?: derivedPool.removeFirstOrNull() }
            if (batch.isEmpty()) break
            taken += batch
            attempts += batch.size
            val results = coroutineScope {
                batch.map { s -> async { fetch(s.track.ytVideoId!!) } }.awaitAll()
            }
            // Assembled in seed order so the feed reads the same however the fetches finish.
            batch.zip(results).forEach { (s, recs) ->
                if (recs == null) errors++ else build(s, recs)?.let { out += it }
            }
        }
        if (out.isEmpty() && errors > 0 && errors == attempts) {
            // Nothing landed (offline): put the seeds back so a retry asks for the same page.
            taken.asReversed().forEach { if (it.derived) derivedPool.addFirst(it) else seeds.addFirst(it) }
            return@withLock Page(emptyList(), failed = true)
        }
        Page(out, failed = false)
    }

    private suspend fun fetch(videoId: String) = try {
        radio(videoId)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private fun build(seed: Seed, recs: List<RecommenderApi.YouTubeRecommendation>): HomeSection? {
        val n = produced
        val limit = if (n == 0) QUICK_PICKS else ROW_MAX
        val seedKey = SongTitle.key(seed.track.title, seed.track.artist)
        val picked = ArrayList<FeedSong>()
        for (rec in recs) {
            if (picked.size >= limit) break
            if (rec.id.isBlank() || isSpam(rec.title)) continue
            val song = FeedSong.from(rec)
            val key = song.key
            if (key == seedKey || key in seen || picked.any { it.key == key }) continue
            picked += song
        }
        if (picked.size < MIN_SONGS) return null
        seen += picked.map { it.key }
        produced++
        picked.first().let {
            derivedPool.addLast(
                Seed(Track(uuid = "yt:${it.videoId}", title = it.title, artist = it.artist, durationSec = 0, ytVideoId = it.videoId), derived = true)
            )
        }
        val id = "s$n-${seed.track.uuid}"
        val t = seed.track
        return when {
            n == 0 -> HomeSection.QuickPicks(id, "Quick picks", picked, t)
            n % 4 == 3 -> HomeSection.HeroMix(id, "Mixed for you", "Based on ${t.artist}", picked, t)
            seed.derived -> HomeSection.SongRow(id, "Fresh finds", picked, t)
            else -> HomeSection.SongRow(id, "Because you played ${t.title}", picked, t)
        }
    }

    private companion object {
        const val PAGE_SIZE = 3
        const val MAX_ATTEMPTS = 6
        const val QUICK_PICKS = 20
        const val ROW_MAX = 16
        const val MIN_SONGS = 4
    }
}
