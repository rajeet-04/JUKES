package com.example.juke.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.ConcurrentHashMap

private val lrcTimestampRegex = """^(\s*\[\d{2}:\d{2}\.\d{1,3}]\s*)(.*)$""".toRegex()

/**
 * Romanizes lyrics through Google Translate's transliteration (`dt=rm`).
 *
 * A song is sent as one request per script (all its Devanagari lines together, all its Gurmukhi
 * lines together, ...), not one request per line: per-line bursts got the `gtx` client rate-limited
 * (HTTP 429 "Sorry" page), which left lyrics unromanized. Lines of one script share a language, so
 * a batch reads the same as single lines; mixing scripts makes Google pick one language for all.
 */
object LyricsRomanizer {
    private const val BASE_URL = "https://translate.googleapis.com/translate_a/single"
    private const val DEFAULT_SOURCE_LANGUAGE = "auto"
    private const val TARGET_LANGUAGE = "en"
    /** `dict-chrome-ex` answers when `gtx` is rate-limited; same response shape. */
    private val CLIENTS = listOf("dict-chrome-ex", "gtx")
    private const val BATCH_LINES = 80
    private const val RATE_LIMIT_PAUSE_MS = 60_000L

    private val client: OkHttpClient by lazy { OkHttpClient() }
    private val lineCache = ConcurrentHashMap<String, String>()
    /** The player and mini player romanize the same song at once; the second waits and hits the cache. */
    private val batchLock = Mutex()
    @Volatile private var pausedUntil = 0L

    // Checks if text has characters outside Latin blocks (e.g., Devanagari, Hangul)
    private fun needsRomanization(text: String): Boolean {
        return text.any { ch ->
            ch.code > 0x02AF && Character.isLetter(ch.code)
        }
    }

    private fun scriptOf(text: String): Character.UnicodeScript? = text.codePoints()
        .filter { it > 0x02AF && Character.isLetter(it) }
        .findFirst()
        .let { if (it.isPresent) Character.UnicodeScript.of(it.asInt) else null }

    /**
     * True when no line is left in a non-Latin script. A failed request (offline, rate-limited,
     * background network blocked) falls back to the original line, so such a result must not be
     * saved as the track's romanized lyrics.
     */
    fun isFullyRomanized(lyrics: String): Boolean = lyrics.lineSequence().none { line ->
        val text = lrcTimestampRegex.find(line)?.groupValues?.get(2) ?: line
        needsRomanization(text.trim()) || text.any { it in ISO_MARKS }
    }

    /**
     * The romanization to show for [original]: [saved] when it is complete, else a new one from
     * [romanize]. A saved Urdu romanization is redone once per run of the app, since it may be
     * from before the vowels came from Hindi; it stays if redoing fails (offline, rate-limited).
     */
    suspend fun current(original: String?, saved: String?, romanize: suspend (String) -> String): String? {
        val usable = saved?.takeIf(::isFullyRomanized)
        if (usable != null && !mayPredateVowels(original)) return usable
        val fresh = original?.let { romanize(it) }
        return if (usable != null && fresh?.let(::isFullyRomanized) != true) usable else fresh
    }

    /** True when [original] has an Arabic-script line this run of the app has not romanized yet. */
    private fun mayPredateVowels(original: String?): Boolean = original.orEmpty().lineSequence()
        .map { line -> wordStamp.replace(lrcTimestampRegex.find(line)?.groupValues?.get(2) ?: line, "").trim() }
        .firstOrNull { scriptOf(it) == Character.UnicodeScript.ARABIC }
        ?.let { lineCache["$DEFAULT_SOURCE_LANGUAGE::$it"] == null } ?: false

    /** Google's ISO 15919 Punjabi ("Sōcāṁ"); a stored romanization with these is redone by [Gurmukhi]. */
    private const val ISO_MARKS = "āīūēōṁṇṭḍḻṛśṣẖḵ"


    /** The romanized text of a `translate_a/single` response, or null. */
    internal fun parseRomanization(body: String): String? {
        val segments = JSONArray(body).optJSONArray(0) ?: return null
        val out = StringBuilder()
        for (i in 0 until segments.length()) {
            val segment = segments.optJSONArray(i) ?: continue
            val text = when {
                segment.length() > 2 && !segment.isNull(2) -> segment.getString(2)
                segment.length() > 3 && !segment.isNull(3) -> segment.getString(3)
                else -> continue
            }
            if (out.isNotEmpty() && !out.last().isWhitespace() && text.firstOrNull()?.isWhitespace() == false) {
                out.append(' ')
            }
            out.append(text)
        }
        return out.toString().ifBlank { null }
    }

    /** Google's answer to a Hindi translation request: both romanizations and the language it read. */
    internal class Vowelled(val language: String, val source: String, val hindi: String)

    internal fun parseVowelled(body: String): Vowelled? {
        val root = JSONArray(body)
        val segments = root.optJSONArray(0) ?: return null
        val source = StringBuilder()
        val hindi = StringBuilder()
        for (i in 0 until segments.length()) {
            val segment = segments.optJSONArray(i) ?: continue
            if (segment.length() > 2 && !segment.isNull(2)) hindi.append(segment.getString(2))
            if (segment.length() > 3 && !segment.isNull(3)) source.append(segment.getString(3))
        }
        return if (source.isBlank()) null else Vowelled(root.optString(2), source.toString(), hindi.toString())
    }

    private val SPACES = Regex("\\s+")
    private val NOT_LETTERS = Regex("[^\\p{L}]+")

    /**
     * Arabic-script lines romanized with one request that also asks for Hindi: the lines, then
     * each of their words on its own line. When Google reads the text as Urdu, the Hindi supplies
     * the short vowels Urdu leaves out ([Colloquial.revowel]): a word's own translation, the
     * words of its line's translation, then every word of the song. Null when the request fails.
     */
    private fun romanizeArabicScript(chunk: List<String>, sourceLanguage: String): Map<String, String>? {
        val words = chunk.flatMap { it.split(SPACES) }.map(Colloquial::bare).filter { it.isNotEmpty() && needsRomanization(it) }.distinct()
        val answer = fetch((chunk + words).joinToString("\n"), sourceLanguage, "tl=hi&dt=t&dt=rm", ::parseVowelled) ?: return null
        val source = answer.source.split("\n")
        if (source.size != chunk.size + words.size) return null
        val hindi = answer.hindi.split("\n").takeIf { answer.language == "ur" && it.size == source.size }
            ?.map { line -> line.lowercase().split(NOT_LETTERS).filter { it.isNotEmpty() } }
            ?: return chunk.indices.associate { chunk[it] to tidy(Colloquial.polish(chunk[it], source[it])) }
        val own = words.indices.associate { words[it] to hindi[chunk.size + it] }
        val song = hindi.take(chunk.size).flatten().distinct()
        return chunk.indices.associate { i ->
            // The Hindi word at the same place in the line, when the two lines have as many words.
            val aligned = hindi[i].takeIf { it.size == chunk[i].trim().split(SPACES).size }.orEmpty()
            chunk[i] to tidy(Colloquial.polish(chunk[i], source[i]) { index, word ->
                listOfNotNull(aligned.getOrNull(index)) + hindi[i] + own[word].orEmpty() + song
            })
        }
    }

    private fun request(text: String, sourceLanguage: String): String? =
        fetch(text, sourceLanguage, "tl=$TARGET_LANGUAGE&dt=rm", ::parseRomanization)

    /** One POST (no URL length limit) trying each client; null on failure or while rate-limited. */
    private fun <T> fetch(text: String, sourceLanguage: String, query: String, parse: (String) -> T?): T? {
        if (System.currentTimeMillis() < pausedUntil) return null
        var rateLimited = 0
        for (name in CLIENTS) {
            val request = Request.Builder()
                .url("$BASE_URL?client=$name&sl=$sourceLanguage&$query")
                .post(FormBody.Builder().add("q", text).build())
                .header("User-Agent", "Mozilla/5.0")
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    when {
                        response.code == 429 -> rateLimited++
                        response.isSuccessful -> response.body?.string()?.let(parse)?.let { return it }
                    }
                }
            } catch (_: Exception) {
                // Offline or a malformed answer: try the next client.
            }
        }
        if (rateLimited == CLIENTS.size) pausedUntil = System.currentTimeMillis() + RATE_LIMIT_PAUSE_MS
        return null
    }

    private fun tidy(romanized: String) = romanized.trim().replaceFirstChar { it.uppercase() }

    suspend fun getRomanization(
        text: String,
        sourceLanguage: String = DEFAULT_SOURCE_LANGUAGE
    ): String? = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return@withContext ""
        val cacheKey = "$sourceLanguage::$trimmed"
        lineCache[cacheKey]?.let { return@withContext it }
        request(trimmed, sourceLanguage)?.let { tidy(Colloquial.polish(trimmed, it)) }?.takeIf { it.isNotBlank() }
            ?.also { lineCache[cacheKey] = it }
    }

    /** Romanizations of the non-Latin [lines] (trimmed text -> romanized), batched per script. */
    private suspend fun romanizeAll(
        lines: Collection<String>,
        sourceLanguage: String
    ): Map<String, String> = withContext(Dispatchers.IO) {
        batchLock.withLock {
            val wanted = lines.map { it.trim() }.filter { it.isNotEmpty() && needsRomanization(it) }.distinct()
            val result = HashMap<String, String>()
            val missing = wanted.filter { line ->
                val cached = lineCache["$sourceLanguage::$line"]
                if (cached != null) result[line] = cached
                cached == null
            }
            val groups = missing.groupBy(::scriptOf)
            // Punjabi is transliterated on the device: Google's answer is ISO 15919 and drops the addak and tippi.
            groups[Character.UnicodeScript.GURMUKHI]?.forEach { line ->
                Gurmukhi.toLatin(line).let(::tidy).takeIf { it.isNotBlank() }?.let { result[line] = it; lineCache["$sourceLanguage::$line"] = it }
            }
            for ((script, group) in groups) {
                if (script == Character.UnicodeScript.GURMUKHI) continue
                for (chunk in group.chunked(BATCH_LINES)) {
                    val vowelled = if (script == Character.UnicodeScript.ARABIC) romanizeArabicScript(chunk, sourceLanguage) else null
                    if (vowelled != null) {
                        vowelled.forEach { (line, value) -> if (value.isNotBlank()) { result[line] = value; lineCache["$sourceLanguage::$line"] = value } }
                        continue
                    }
                    val parts = request(chunk.joinToString("\n"), sourceLanguage)?.split("\n")
                    if (parts != null && parts.size == chunk.size) {
                        chunk.zip(parts).forEach { (line, romanized) ->
                            val value = tidy(Colloquial.polish(line, romanized))
                            if (value.isNotBlank()) {
                                result[line] = value
                                lineCache["$sourceLanguage::$line"] = value
                            }
                        }
                    } else if (System.currentTimeMillis() >= pausedUntil) {
                        // The batch came back with a different line count: go line by line.
                        chunk.forEach { line -> getRomanization(line, sourceLanguage)?.let { result[line] = it } }
                    }
                }
            }
            result
        }
    }

    suspend fun processMixedLyrics(
        fullLyrics: String,
        sourceLanguage: String = DEFAULT_SOURCE_LANGUAGE
    ): String {
        val lines = fullLyrics.split("\n")
        val romanized = romanizeAll(lines, sourceLanguage)
        // Latin lines are kept exactly as they are.
        return lines.joinToString("\n") { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) "" else romanized[trimmed] ?: trimmed
        }
    }

    suspend fun romanizeText(
        text: String,
        sourceLanguage: String = DEFAULT_SOURCE_LANGUAGE
    ): String {
        return processMixedLyrics(text, sourceLanguage)
    }

    private val wordStamp = Regex("""<\d{1,2}:\d{2}(?:[.:]\d{1,3})?>""")

    /**
     * [romanized] with the word stamps of [body] put back, word for word. When romanizing changed
     * the number of words, the line keeps only its line timing.
     */
    internal fun restampWords(body: String, romanized: String): String {
        val stamps = wordStamp.findAll(body).map { it.value }.toList()
        if (stamps.isEmpty()) return romanized
        val words = romanized.trim().split(Regex("\\s+"))
        val original = wordStamp.split(body).drop(1).filter { it.isNotBlank() }
        if (words.size != stamps.size || original.size != stamps.size) return romanized
        return stamps.zip(words).joinToString(" ") { (stamp, word) -> stamp + word }
    }

    suspend fun romanizeSyncedLyrics(
        syncedLyrics: String,
        sourceLanguage: String = DEFAULT_SOURCE_LANGUAGE
    ): String {
        val lines = syncedLyrics.split("\n")
        // Word stamps (<mm:ss.xx>) are taken off before romanizing and put back after.
        val texts = lines.map { line -> wordStamp.replace(lrcTimestampRegex.find(line)?.groupValues?.get(2) ?: line, "") }
        val romanized = romanizeAll(texts, sourceLanguage)
        return lines.joinToString("\n") { line ->
            val match = lrcTimestampRegex.find(line)
            if (match != null) {
                val prefix = match.groupValues[1]
                val body = match.groupValues[2].trim()
                val lyricText = wordStamp.replace(body, "").trim()
                val latin = romanized[lyricText]
                // Latin lyrics keep their casing, punctuation and word timings.
                if (lyricText.isEmpty()) prefix else if (latin == null) prefix + body else prefix + restampWords(body, latin)
            } else {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) "" else romanized[trimmed] ?: trimmed
            }
        }
    }
}
