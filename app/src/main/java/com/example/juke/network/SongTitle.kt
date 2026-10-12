package com.example.juke.network

import kotlin.math.abs

/**
 * Reads a song title the way a listener would: what's in brackets (or after a Spotify-style
 * " - " suffix) is either
 * - noise: upload labels that never make a different song ("Official Video", "Lyrics", "HD",
 *   "From \"Movie\"", "Remastered 2011", "Radio Edit", "prod. X", a year);
 * - a version: a genuinely different recording ("Remix", "Slowed + Reverb", "Live", "Acoustic",
 *   "Female Version", "Lo-fi", "8D", ...), which stays part of the song's identity;
 * - a credit: "feat. X", "ft. X", "with X". A credit alone can't say whether it is the same
 *   recording (usually) or a new one with an added verse ("Pal Pal (with Talwiinder)"); length
 *   decides that (see [SongIndex]);
 * - anything else: an alternate or sub-title ("Mahishasura Mardini Stotram (Aigiri Nandini)"),
 *   treated like noise, with length again separating two different songs.
 */
object SongTitle {

    data class Parts(
        /** Title without brackets or labels, lower-case letters and digits only. */
        val core: String,
        /** Normalised version tags, e.g. "femaleversion" → "female", "encoreabjremix". */
        val versions: List<String>,
        /** Artists credited in the title ("feat. A & B" → [a, b]). */
        val guests: List<String>,
    )

    private val BRACKET = Regex("""[(\[{]([^)\]}]*)[)\]}]""")
    private val DASH_SUFFIX = Regex("""\s+[-–—]\s+([^-–—]+)$""")
    private val CREDIT = Regex("""^(?:feat\.?|ft\.?|featuring|with|w/)\s+(.+)$""")
    private val PRODUCER = Regex("""^(?:prod\.?|produced by|prod by)\b.*""")
    private val NOISE = Regex(
        """^(?:official(?:\s+\w+)*|lyrics?(?:\s+video)?|lyrical(?:\s+video)?|(?:music\s+)?video|audio|mv|""" +
            """hd|hq|4k|8k|\d{3,4}p|explicit|clean|remaster(?:ed)?(?:\s+\d{4})?|\d{4}\s+remaster(?:ed)?|""" +
            """radio\s+edit|single\s+version|album\s+version|original\s+mix|original|""" +
            """full\s+(?:song|video|audio)|(?:audio\s+)?visuali[sz]er|from\s+.+|ost|""" +
            """(?:original\s+)?(?:motion\s+picture\s+)?soundtrack|(?:19|20)\d\d)$"""
    )
    /** Words that mark a different recording of the song. */
    private val VERSION_WORDS = listOf(
        "remix", "remixed", "mix", "mixed", "slowed", "reverb", "sped up", "speed up", "nightcore", "lofi", "lo-fi", "lo fi",
        "8d", "live", "acoustic", "unplugged", "reprise", "instrumental", "karaoke", "cover", "mashup",
        "extended", "version", "edit", "demo", "acapella", "a cappella", "piano", "orchestral", "rework",
        "flip", "bootleg", "bass boosted", "chopped", "screwed", "jhankar", "unreleased"
    ).map { Regex("""\b${Regex.escape(it)}\b""") }
    private val NON_ALNUM = Regex("""[^\p{L}\p{N}]+""")
    private val ARTIST_SPLIT = Regex("""\s*(?:,|&|\+|/|\bx\b|\band\b|\bfeat\.?|\bft\.?|\bwith\b)\s*""", RegexOption.IGNORE_CASE)

    private fun alnum(text: String) = NON_ALNUM.replace(text.lowercase(), "")

    private sealed interface Segment {
        data object Noise : Segment
        data class Version(val tag: String) : Segment
        data class Credit(val names: List<String>) : Segment
        data object Other : Segment
    }

    private fun classify(raw: String): Segment {
        val s = raw.lowercase().trim().replace(Regex("""\s+"""), " ")
        if (s.isEmpty()) return Segment.Noise
        CREDIT.matchEntire(s)?.let { m ->
            return Segment.Credit(m.groupValues[1].split(ARTIST_SPLIT).map(::alnum).filter { it.isNotEmpty() })
        }
        if (PRODUCER.matches(s) || NOISE.matches(s)) return Segment.Noise
        if (s == "original mix" || s == "radio edit") return Segment.Noise
        if (VERSION_WORDS.any { it.containsMatchIn(s) }) {
            val tag = alnum(s.replace(Regex("""\b(?:version|ver\.?|the)\b"""), ""))
            return Segment.Version(tag.ifEmpty { alnum(s) })
        }
        return Segment.Other
    }

    fun parse(title: String): Parts {
        val versions = mutableListOf<String>()
        val guests = mutableListOf<String>()
        fun take(segment: Segment) {
            when (segment) {
                is Segment.Version -> versions += segment.tag
                is Segment.Credit -> guests += segment.names
                else -> Unit
            }
        }
        BRACKET.findAll(title).forEach { take(classify(it.groupValues[1])) }
        var core = BRACKET.replace(title, " ")
        // A Spotify-style " - Remastered 2011" / " - Female Version" suffix is a label or a version;
        // anything else after a dash ("Artist - Song" uploads) stays part of the title.
        DASH_SUFFIX.find(core)?.let { m ->
            val segment = classify(m.groupValues[1])
            if (segment != Segment.Other) {
                take(segment)
                core = core.substring(0, m.range.first)
            }
        }
        // "Song feat. X" without brackets.
        Regex("""\s(?:feat\.?|ft\.?|featuring)\s+(.+)$""", RegexOption.IGNORE_CASE).find(core)?.let { m ->
            guests += m.groupValues[1].split(ARTIST_SPLIT).map(::alnum).filter { it.isNotEmpty() }
            core = core.substring(0, m.range.first)
        }
        return Parts(alnum(core).ifEmpty { alnum(title) }, versions.sorted().distinct(), guests.distinct())
    }

    /** The first credited artist, however the list is joined ("A & B", "A, B", "A x B", "A feat. B"). */
    fun primaryArtist(artist: String): String =
        artist.split(ARTIST_SPLIT).map(::alnum).firstOrNull { it.isNotEmpty() }.orEmpty()

    /**
     * Identity of a song across YouTube and Spotify spellings: core title, version tags and first
     * artist. Two different recordings with the same key (a credit-only difference, an alternate
     * title) are told apart by length in [SongIndex].
     */
    fun key(title: String, artist: String): String {
        val parts = parse(title)
        val version = if (parts.versions.isEmpty()) "" else "~" + parts.versions.joinToString("+")
        return "${parts.core}$version|${primaryArtist(artist)}"
    }

    /** True when the two titles name different recordings by their version tags. */
    fun differentVersion(a: String, b: String): Boolean = parse(a).versions != parse(b).versions

    /** "3:05" / "1:02:03" → seconds, or null. */
    fun clockSeconds(text: String?): Int? {
        val parts = text?.trim()?.split(":")?.map { it.toIntOrNull() ?: return null } ?: return null
        if (parts.isEmpty() || parts.size > 3) return null
        return parts.fold(0) { acc, n -> acc * 60 + n }.takeIf { it > 0 }
    }
}

/**
 * A set of songs where "the same song" means the same [SongTitle.key] and lengths within
 * [toleranceSec] of each other (or a length unknown on either side). So "Pal Pal" (2:28) and
 * "Pal Pal (with Talwiinder)" (a longer recording) are two songs, while a credits-only or
 * labels-only respelling of one recording is still one.
 */
class SongIndex(private val toleranceSec: Int = 8) {
    private val lengths = HashMap<String, MutableList<Int?>>()

    @Synchronized
    fun contains(key: String, durationSec: Int?): Boolean {
        val known = lengths[key] ?: return false
        return known.any { it == null || durationSec == null || abs(it - durationSec) <= toleranceSec }
    }

    /** Add the song; returns false when it was already there. */
    @Synchronized
    fun add(key: String, durationSec: Int?): Boolean {
        if (contains(key, durationSec)) return false
        lengths.getOrPut(key) { mutableListOf() } += durationSec?.takeIf { it > 0 }
        return true
    }

    @Synchronized
    fun addAll(other: SongIndex) {
        val snapshot = synchronized(other) { other.lengths.mapValues { it.value.toList() } }
        snapshot.forEach { (key, list) -> list.forEach { add(key, it) } }
    }

    @Synchronized
    fun clear() = lengths.clear()
}
