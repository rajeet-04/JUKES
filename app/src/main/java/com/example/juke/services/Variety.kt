package com.example.juke.services

import com.example.juke.database.SongExposureEntity
import kotlin.math.exp
import kotlin.math.pow
import kotlin.random.Random

/**
 * Cross-session variety for recommendations. Every play, early skip and "was recommended" adds a
 * weight to the song's exposure; the weight halves over a fixed time. A candidate's chance of being
 * picked is relevance (its radio rank) x freshness (exp(-exposure)) x artist spread, and picks are a
 * weighted random draw, so the same seed song no longer yields the same songs every session.
 */
object Variety {
    enum class Event(val weight: Double) { PLAY(1.0), SKIP(1.5), RECOMMENDED(0.4) }

    private const val DAY_MS = 24 * 60 * 60 * 1000L
    const val PLAY_HALF_LIFE_MS = 3 * DAY_MS
    const val SKIP_HALF_LIFE_MS = 7 * DAY_MS
    const val REC_HALF_LIFE_MS = 2 * DAY_MS

    /** After this long untouched every score is below ~1% of its weight; rows can be dropped. */
    const val PRUNE_AFTER_MS = 60 * DAY_MS

    /** Each further pick by an artist already in the batch / just queued multiplies by this. */
    const val SAME_ARTIST_FACTOR = 0.35

    private fun decay(value: Double, elapsedMs: Long, halfLifeMs: Long): Double =
        if (elapsedMs <= 0) value else value * 0.5.pow(elapsedMs.toDouble() / halfLifeMs)

    /** The row's scores decayed to [now]. */
    fun decayed(e: SongExposureEntity, now: Long): SongExposureEntity {
        val dt = now - e.updatedAt
        return e.copy(
            playScore = decay(e.playScore, dt, PLAY_HALF_LIFE_MS),
            skipScore = decay(e.skipScore, dt, SKIP_HALF_LIFE_MS),
            recScore = decay(e.recScore, dt, REC_HALF_LIFE_MS),
            updatedAt = maxOf(now, e.updatedAt)
        )
    }

    /** [existing] (may be null) with one [event] added at [now]. */
    fun record(existing: SongExposureEntity?, key: String, event: Event, now: Long): SongExposureEntity {
        val base = existing?.let { decayed(it, now) } ?: SongExposureEntity(key, updatedAt = now)
        return when (event) {
            Event.PLAY -> base.copy(playScore = base.playScore + event.weight)
            Event.SKIP -> base.copy(skipScore = base.skipScore + event.weight)
            Event.RECOMMENDED -> base.copy(recScore = base.recScore + event.weight)
        }
    }

    /** 1.0 = never heard or shown lately; tends to 0 the more it was played/skipped/recommended. */
    fun freshness(e: SongExposureEntity?, now: Long): Double {
        if (e == null) return 1.0
        val d = decayed(e, now)
        return exp(-(d.playScore + d.skipScore + d.recScore))
    }

    /** YouTube radio order still matters: rank 0 = 1.0, rank 10 = 0.5, rank 40 = 0.2. */
    fun relevance(rank: Int): Double = 1.0 / (1.0 + rank.coerceAtLeast(0) / 10.0)

    /**
     * Draws up to [n] distinct items, each with probability proportional to its weight. After every
     * pick, remaining items by the same artist ([artistOf]) are scaled by [SAME_ARTIST_FACTOR].
     * [recentArtists] start pre-penalised once (artists just queued). Zero/negative weights are never picked.
     */
    fun <T> pick(
        candidates: List<T>,
        n: Int,
        weightOf: (T) -> Double,
        artistOf: (T) -> String,
        recentArtists: Collection<String> = emptyList(),
        random: Random = Random.Default
    ): List<T> {
        val pool = candidates.map { it to weightOf(it) }.filter { it.second > 0.0 }.toMutableList()
        val artistHits = HashMap<String, Int>()
        recentArtists.forEach { artistHits[it] = 1 }
        val picked = ArrayList<T>()
        while (picked.size < n && pool.isNotEmpty()) {
            val weights = pool.map { (item, w) -> w * SAME_ARTIST_FACTOR.pow(artistHits[artistOf(item)] ?: 0) }
            val total = weights.sum()
            if (total <= 0.0) break
            var r = random.nextDouble() * total
            var idx = weights.indices.last
            for (i in weights.indices) {
                r -= weights[i]
                if (r <= 0.0) { idx = i; break }
            }
            val item = pool.removeAt(idx).first
            picked += item
            artistOf(item).let { artistHits[it] = (artistHits[it] ?: 0) + 1 }
        }
        return picked
    }
}
