package com.example.juke.utils

import com.example.juke.models.Track
import java.text.Normalizer

/**
 * Forgiving search over saved tracks: every query word must hit the title, artist or lyrics
 * (plain, synced and romanized), so "linkin numb" or a line from mid-chorus finds the song.
 * Typos are tolerated (edit distance scales with word length), accents are ignored, and results
 * are ranked: title > artist > lyrics, exact > prefix > fuzzy.
 */
object TrackSearch {
    private val timestamps = Regex("\\[\\d+:\\d+(?:[.:]\\d+)?]|<\\d+:\\d+(?:[.:]\\d+)?>")
    private val marks = Regex("\\p{Mn}+")
    private val junk = Regex("[^\\p{L}\\p{N}]+")

    fun normalize(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(marks, "").replace(junk, " ").trim()

    /** Allowed typos for a query word: none under 3 letters, 1 up to 7 letters, 2 beyond. */
    private fun budget(len: Int) = when { len < 3 -> 0; len < 8 -> 1; else -> 2 }

    /** Optimal-string-alignment distance (counts a swapped pair as one typo); gives up above [max]. */
    internal fun distance(a: String, b: String, max: Int): Int {
        if (kotlin.math.abs(a.length - b.length) > max) return max + 1
        var prev2 = IntArray(b.length + 1)
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            var rowMin = cur[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var v = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) v = minOf(v, prev2[j - 2] + 1)
                cur[j] = v
                if (v < rowMin) rowMin = v
            }
            if (rowMin > max) return max + 1
            val t = prev2; prev2 = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }

    /** A track's searchable text, normalized once. */
    private class Index(val key: Int, val title: String, val artist: String, val lyrics: String) {
        val titleWords = title.split(' ').filter { it.isNotEmpty() }
        val artistWords = artist.split(' ').filter { it.isNotEmpty() }
        val lyricWords: Set<String> by lazy { lyrics.split(' ').filterTo(HashSet()) { it.isNotEmpty() } }
    }

    private val cache = object : LinkedHashMap<String, Index>(256, 0.75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<String, Index>) = size > 4000
    }

    private fun index(t: Track): Index {
        val key = listOf(t.title, t.artist, t.plainLyrics, t.syncedLyrics, t.romanizedPlainLyrics, t.romanizedSyncedLyrics).hashCode()
        synchronized(cache) { cache[t.uuid]?.takeIf { it.key == key }?.let { return it } }
        fun lyric(s: String?) = s?.replace(timestamps, " ").orEmpty()
        val lyrics = normalize(listOf(t.plainLyrics, lyric(t.syncedLyrics), t.romanizedPlainLyrics, lyric(t.romanizedSyncedLyrics)).joinToString(" ") { it.orEmpty() })
        return Index(key, normalize(t.title), normalize(t.artist), lyrics).also { synchronized(cache) { cache[t.uuid] = it } }
    }

    /** 0 = no match; a word matching in several fields keeps its best score. */
    private fun wordScore(q: String, ix: Index): Int {
        val allowed = budget(q.length)
        var best = 0
        fun take(s: Int) { if (s > best) best = s }
        // exact / prefix / mid-word
        if (ix.titleWords.any { it == q }) take(100) else if (ix.titleWords.any { it.startsWith(q) }) take(85) else if (q.length >= 2 && ix.title.contains(q)) take(70)
        if (ix.artistWords.any { it == q }) take(90) else if (ix.artistWords.any { it.startsWith(q) }) take(75) else if (q.length >= 2 && ix.artist.contains(q)) take(60)
        if (best >= 60) return best
        if (q.length >= 2 && ix.lyrics.contains(q)) take(30)
        if (best > 0 || allowed == 0) return best
        // typos: against whole words and against a same-length prefix (half-typed words)
        fun near(w: String) = distance(q, w, allowed) <= allowed || (w.length > q.length && distance(q, w.substring(0, q.length), allowed) <= allowed)
        if (ix.titleWords.any(::near)) return 55
        if (ix.artistWords.any(::near)) return 45
        if (q.length >= 4 && ix.lyricWords.any(::near)) return 15
        return 0
    }

    /** Matching tracks, best first; with a blank query, [tracks] unchanged. */
    fun rank(tracks: List<Track>, query: String): List<Track> {
        val words = normalize(query).split(' ').filter { it.isNotEmpty() }.distinct()
        if (words.isEmpty()) return tracks
        val phrase = words.joinToString(" ")
        return tracks.mapNotNull { t ->
            val ix = index(t)
            var total = 0
            for (w in words) { val s = wordScore(w, ix); if (s == 0) return@mapNotNull null; total += s }
            // a multi-word run quoted from the lyrics, or the title/artist, beats words scattered about
            if (words.size > 1) {
                if (ix.lyrics.contains(phrase)) total += 40
                if ("${ix.artist} ${ix.title}".contains(phrase) || "${ix.title} ${ix.artist}".contains(phrase)) total += 60
            }
            t to total
        }.sortedByDescending { it.second }.map { it.first }
    }
}
