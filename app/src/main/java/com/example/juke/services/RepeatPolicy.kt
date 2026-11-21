package com.example.juke.services

import com.example.juke.database.SongExposureEntity
import kotlin.math.exp
import kotlin.math.pow
import kotlin.random.Random

/**
 * A listening session: it survives new queues and app restarts and ends only after
 * [RepeatPolicy.SESSION_GAP_MS] with nothing played. [songSeq] counts qualified plays (never
 * reset); [recSeq] counts recommendations placed this session, for the fresh slots.
 */
data class ListeningSession(val start: Long, val lastActivity: Long, val songSeq: Long, val recSeq: Long)

/**
 * Which recommended songs may come back, and how strongly. A song heard or skipped this session
 * is out; a favourite returns after [cooldown] songs, at a quarter of its weight rising to full
 * over another [cooldown]. Every third pick goes to a song never heard, when there is one.
 */
object RepeatPolicy {
    const val SESSION_GAP_MS = 60 * 60 * 1000L
    const val DEFAULT_COOLDOWN = 20
    const val FAVOURITE_BOOST = 2.0
    private const val RAMP_START = 0.25

    /** [s], or a new session when there is none or nothing has played for [SESSION_GAP_MS]. */
    fun current(s: ListeningSession?, now: Long): ListeningSession =
        if (s == null || now - s.lastActivity >= SESSION_GAP_MS) {
            ListeningSession(start = now, lastActivity = now, songSeq = s?.songSeq ?: 0, recSeq = 0)
        } else s

    private fun heardThisSession(e: SongExposureEntity?, s: ListeningSession) =
        e?.lastPlayAt?.let { it >= s.start } == true

    private fun skippedThisSession(e: SongExposureEntity?, s: ListeningSession) =
        e?.lastSkipAt?.let { it >= s.start } == true

    /** Songs since the favourite last played this session, or null when it hasn't. */
    private fun gap(e: SongExposureEntity?, s: ListeningSession): Long? =
        if (heardThisSession(e, s)) s.songSeq - (e?.lastPlaySeq ?: s.songSeq) else null

    fun blocked(e: SongExposureEntity?, favourite: Boolean, s: ListeningSession, cooldown: Int): Boolean {
        if (skippedThisSession(e, s)) return true
        if (!favourite) return heardThisSession(e, s)
        val g = gap(e, s) ?: return false
        return g < cooldown
    }

    /** [base] (relevance or library score) scaled by favourite boost, cooldown ramp and freshness. */
    fun weight(e: SongExposureEntity?, favourite: Boolean, s: ListeningSession, cooldown: Int, now: Long, base: Double): Double {
        if (!favourite) return base * Variety.freshness(e, now)
        val ramp = gap(e, s)?.let { g ->
            if (g < cooldown) 0.0
            else RAMP_START + (1 - RAMP_START) * ((g - cooldown).toDouble() / cooldown).coerceAtMost(1.0)
        } ?: 1.0
        // The cooldown already spaces a favourite's plays; only skips and showings wear it down.
        val worn = e?.let { Variety.decayed(it, now) }?.let { exp(-(it.skipScore + it.recScore)) } ?: 1.0
        return base * FAVOURITE_BOOST * ramp * worn
    }

    /** No play on record here (rows fade after ~60 days) and none in the library. */
    fun neverHeard(e: SongExposureEntity?, libraryPlays: Int = 0): Boolean =
        libraryPlays == 0 && (e == null || (e.playScore <= 0.0 && e.lastPlayAt == null))

    fun isFreshSlot(recSeq: Long) = recSeq % 3 == 2L

    /**
     * All of [items] in pick order: a weighted random draw where each earlier pick by the same
     * artist multiplies by [Variety.SAME_ARTIST_FACTOR]. Fresh slots (by [startRecSeq] + position)
     * draw from [fresh] items, the others from the rest, each falling back to whatever is left.
     * Zero-weight items come last, in their given order.
     */
    fun <T> order(
        items: List<T>,
        startRecSeq: Long,
        weightOf: (T) -> Double,
        fresh: (T) -> Boolean,
        artistOf: (T) -> String,
        random: Random = Random.Default
    ): List<T> {
        val (live, dead) = items.map { it to weightOf(it) }.partition { it.second > 0.0 }
        val pool = live.toMutableList()
        val hits = HashMap<String, Int>()
        val out = ArrayList<T>(items.size)
        while (pool.isNotEmpty()) {
            val wantFresh = isFreshSlot(startRecSeq + out.size)
            val matching = pool.filter { fresh(it.first) == wantFresh }
            val from = matching.ifEmpty { pool }
            val weights = from.map { (item, w) -> w * Variety.SAME_ARTIST_FACTOR.pow(hits[artistOf(item)] ?: 0) }
            var r = random.nextDouble() * weights.sum()
            var idx = from.lastIndex
            for (i in weights.indices) {
                r -= weights[i]
                if (r <= 0.0) { idx = i; break }
            }
            val chosen = from[idx]
            pool.remove(chosen)
            out += chosen.first
            artistOf(chosen.first).let { hits[it] = (hits[it] ?: 0) + 1 }
        }
        return out + dead.map { it.first }
    }
}
