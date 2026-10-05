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
        needsRomanization(text.trim())
    }

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

    /** One POST (no URL length limit) trying each client; null on failure or while rate-limited. */
    private fun request(text: String, sourceLanguage: String): String? {
        if (System.currentTimeMillis() < pausedUntil) return null
        var rateLimited = 0
        for (name in CLIENTS) {
            val request = Request.Builder()
                .url("$BASE_URL?client=$name&sl=$sourceLanguage&tl=$TARGET_LANGUAGE&dt=rm")
                .post(FormBody.Builder().add("q", text).build())
                .header("User-Agent", "Mozilla/5.0")
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    when {
                        response.code == 429 -> rateLimited++
                        response.isSuccessful -> response.body?.string()?.let(::parseRomanization)?.let { return it }
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
        request(trimmed, sourceLanguage)?.let(::tidy)?.takeIf { it.isNotBlank() }
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
            for (group in missing.groupBy(::scriptOf).values) {
                for (chunk in group.chunked(BATCH_LINES)) {
                    val parts = request(chunk.joinToString("\n"), sourceLanguage)?.split("\n")
                    if (parts != null && parts.size == chunk.size) {
                        chunk.zip(parts).forEach { (line, romanized) ->
                            val value = tidy(romanized)
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

    suspend fun romanizeSyncedLyrics(
        syncedLyrics: String,
        sourceLanguage: String = DEFAULT_SOURCE_LANGUAGE
    ): String {
        val lines = syncedLyrics.split("\n")
        val texts = lines.map { line -> lrcTimestampRegex.find(line)?.groupValues?.get(2) ?: line }
        val romanized = romanizeAll(texts, sourceLanguage)
        return lines.joinToString("\n") { line ->
            val match = lrcTimestampRegex.find(line)
            if (match != null) {
                val prefix = match.groupValues[1]
                val lyricText = match.groupValues[2].trim()
                // Latin lyrics keep their casing and punctuation.
                if (lyricText.isEmpty()) prefix else prefix + (romanized[lyricText] ?: lyricText)
            } else {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) "" else romanized[trimmed] ?: trimmed
            }
        }
    }
}
