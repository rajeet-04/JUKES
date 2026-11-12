package com.example.juke.models

/**
 * The same timing rules the server applies (backend docs/LYRICS_API.md), so lyrics found on the
 * device are judged the same way. Rangeen's LRCLIB copies had whole-second stamps up to 57 s off;
 * one claimed 167 s with stamps to 3:32.
 */
internal object LyricsTrust {
    private val STAMP = Regex("""^\s*\[(\d+):(\d{1,2}(?:\.\d{1,3})?)]""")

    fun stampsMs(lrc: String): List<Long> = lrc.lineSequence().mapNotNull { line ->
        STAMP.find(line)?.let { m -> Math.round((m.groupValues[1].toLong() * 60 + m.groupValues[2].toDouble()) * 1000) }
    }.toList()

    /** Why the timing can't be trusted, or null. */
    fun timingProblem(stamps: List<Long>, durationMs: Long?): String? {
        if (stamps.size < 2) return null
        if (stamps.zipWithNext().any { (a, b) -> b < a - 500 }) return "out_of_order"
        if (durationMs != null && durationMs > 0 && stamps.last() > durationMs + 5_000) return "past_end"
        if (stamps.size >= 6 && stamps.all { it % 1000 == 0L }) return "whole_seconds"
        return null
    }

    /** Words only from text in LRC form. */
    fun lrcText(lrc: String): String = lrc.lineSequence()
        .map { it.replace(Regex("""^\s*(\[[^]]*])+"""), "").trim() }
        .filter { it.isNotEmpty() }
        .joinToString("\n")
}

/** This result with untrustworthy timing removed; the words are kept as plain lyrics. */
internal fun LRCLibResult.trusted(durationSec: Int?): LRCLibResult {
    val synced = syncedLyrics?.takeIf { it.isNotBlank() } ?: return this
    val stamps = LyricsTrust.stampsMs(synced)
    val problem = LyricsTrust.timingProblem(stamps, duration?.let { (it * 1000).toLong() })
        ?: LyricsTrust.timingProblem(stamps, durationSec?.let { it * 1000L })
        ?: return this
    return copy(syncedLyrics = null, plainLyrics = plainLyrics?.takeIf { it.isNotBlank() } ?: LyricsTrust.lrcText(synced))
        .also { runCatching { android.util.Log.d("LyricsTrust", "Ignoring $problem timing for $trackName") } } // no Log in JVM tests
}
