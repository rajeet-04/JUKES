package com.example.juke.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

private val lrcTimestampRegex = """^(\s*\[\d{2}:\d{2}\.\d{1,3}]\s*)(.*)$""".toRegex()

object LyricsRomanizer {
    private const val BASE_URL = "https://translate.googleapis.com/translate_a/single"
    private const val DEFAULT_SOURCE_LANGUAGE = "auto"
    private const val TARGET_LANGUAGE = "en"

    private val client: OkHttpClient by lazy { OkHttpClient() }
    private val lineCache = ConcurrentHashMap<String, String>()
    private val requestLimiter = Semaphore(6)

    // Checks if text has characters outside Latin blocks (e.g., Devanagari, Hangul)
    private fun needsRomanization(text: String): Boolean {
        return text.any { ch ->
            ch.code > 0x02AF && Character.isLetter(ch.code)
        }
    }

    suspend fun getRomanization(
        text: String,
        sourceLanguage: String = DEFAULT_SOURCE_LANGUAGE
    ): String? = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return@withContext ""

        val cacheKey = "$sourceLanguage::$trimmed"
        lineCache[cacheKey]?.let { return@withContext it }

        requestLimiter.withPermit {
            try {
                val encodedText = URLEncoder.encode(trimmed, StandardCharsets.UTF_8.name())
                val url =
                    "$BASE_URL?client=gtx&sl=$sourceLanguage&tl=$TARGET_LANGUAGE&dt=rm&q=$encodedText"

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withPermit null

                    val responseBody = response.body?.string() ?: return@withPermit null
                    val rootArray = JSONArray(responseBody)
                    val dataArray = rootArray.optJSONArray(0) ?: return@withPermit null

                    val romanizedResult = StringBuilder()
                    for (i in 0 until dataArray.length()) {
                        val innerArray = dataArray.optJSONArray(i) ?: continue
                        if (innerArray.length() > 2 && !innerArray.isNull(2)) {
                            romanizedResult.append(innerArray.getString(2)).append(' ')
                        } else if (innerArray.length() > 3 && !innerArray.isNull(3)) {
                            romanizedResult.append(innerArray.getString(3)).append(' ')
                        }
                    }

                    val result = romanizedResult.toString().trim().ifBlank { null }
                    if (result != null) {
                        lineCache[cacheKey] = result
                    }
                    result
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    suspend fun processMixedLyrics(
        fullLyrics: String,
        sourceLanguage: String = DEFAULT_SOURCE_LANGUAGE
    ): String = coroutineScope {
        val lines = fullLyrics.split("\n")
        val deferredResults = lines.map { line ->
            async {
                val trimmedLine = line.trim()
                if (trimmedLine.isEmpty()) {
                    ""
                } else if (!needsRomanization(trimmedLine)) {
                    // It's already in English/Latin, keep it exactly as is!
                    trimmedLine
                } else {
                    getRomanization(trimmedLine, sourceLanguage) ?: trimmedLine
                }
            }
        }

        deferredResults.awaitAll().joinToString("\n")
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
    ): String = coroutineScope {
        val lines = syncedLyrics.split("\n")
        val deferredResults = lines.map { line ->
            async {
                val match = lrcTimestampRegex.find(line)
                if (match != null) {
                    val prefix = match.groupValues[1]
                    val lyricText = match.groupValues[2].trim()
                    if (lyricText.isEmpty()) {
                        prefix
                    } else if (!needsRomanization(lyricText)) {
                        // Skip English/Latin lyrics, preserving casing and punctuation
                        prefix + lyricText
                    } else {
                        val romanized = getRomanization(lyricText, sourceLanguage) ?: lyricText
                        prefix + romanized
                    }
                } else {
                    val trimmedLine = line.trim()
                    if (trimmedLine.isEmpty()) {
                        ""
                    } else if (!needsRomanization(trimmedLine)) {
                        // Skip English/Latin lines completely
                        trimmedLine
                    } else {
                        getRomanization(trimmedLine, sourceLanguage) ?: trimmedLine
                    }
                }
            }
        }

        deferredResults.awaitAll().joinToString("\n")
    }
}
