package com.example.juke.network

import android.util.Log
import com.example.juke.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.abs

/**
 * The JUKES backend's `/v1` API (docs: API_handbook/JUKES_API.md and JUKES_APP_HANDBOOK.md).
 *
 * The server downloads the whole file before serving it, so playback is prepare, then poll the job
 * until it is ready, then play `audio_url` (a complete file with Content-Length and byte ranges).
 * Anonymous: no key or token is sent.
 */
object JukesApi {
    private const val TAG = "JukesApi"
    private val json = Json { ignoreUnknownKeys = true }
    private val base get() = BuildConfig.JUKE_BACKEND_URL.trimEnd('/')

    /** How long a play or download waits for the server to finish the file (handbook: ~2 min). */
    const val PREPARE_BUDGET_MS = 120_000L
    private const val MAX_PREPARES = 3
    private val POLL_DELAYS_MS = longArrayOf(1_000, 2_000, 3_000) // then every 3 s
    /** The server holds a job poll up to this long and answers the moment the job finishes. */
    private const val LONG_POLL_SEC = 10

    @Serializable
    data class Prepared(
        @SerialName("video_id") val videoId: String,
        val title: String = "",
        val artist: String = "",
        @SerialName("duration_ms") val durationMs: Long? = null,
        @SerialName("job_id") val jobId: String,
        val status: String,
        @SerialName("audio_url") val audioUrl: String? = null,
    )

    @Serializable
    data class Job(
        @SerialName("job_id") val jobId: String,
        val status: String,
        @SerialName("audio_url") val audioUrl: String? = null,
        val error: JobError? = null,
    )

    @Serializable
    data class JobError(val code: String = "", val retryable: Boolean = false)

    @Serializable
    data class RadioTrack(
        @SerialName("video_id") val videoId: String,
        val title: String = "",
        val artist: String = "",
        @SerialName("duration_ms") val durationMs: Long? = null,
    )

    @Serializable
    private data class Radio(val tracks: List<RadioTrack> = emptyList())

    @Serializable
    private data class ErrorBody(val error: Detail) {
        @Serializable
        data class Detail(val code: String = "", val retryable: Boolean = false)
    }

    /** A non-2xx answer. [code] is the server's error code, or `http_<status>` when it had none. */
    class JukesException(
        val http: Int,
        val code: String,
        val retryable: Boolean,
        val retryAfterSec: Int?,
    ) : Exception("JUKES backend HTTP $http $code")

    /** The server has no `/v1` routes (an older deployment): use the legacy `/audio/` endpoint. */
    class V1MissingException : Exception("Backend has no /v1 API")

    /** Title of the song the server is preparing for playback right now, for the UI. */
    private val _preparing = MutableStateFlow<String?>(null)
    val preparing: StateFlow<String?> = _preparing.asStateFlow()

    private val warmScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private suspend inline fun <reified T> call(
        client: HttpClient,
        method: HttpMethod,
        path: String,
        body: JsonObject? = null,
    ): T {
        val response = client.request("$base$path") {
            this.method = method
            if (body != null) {
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }
        }
        val text = response.bodyAsText()
        val status = response.status.value
        if (status in 200..299) return json.decodeFromString(text)
        val error = runCatching { json.decodeFromString<ErrorBody>(text).error }.getOrNull()
        // An unknown route is a JSON 404 `not_found` on new servers and an HTML page on old ones.
        if (status == 404 && (error == null || error.code == "not_found")) throw V1MissingException()
        throw JukesException(
            http = status,
            code = error?.code ?: "http_$status",
            retryable = error?.retryable ?: false,
            retryAfterSec = response.headers[HttpHeaders.RetryAfter]?.toIntOrNull(),
        )
    }

    private fun track(title: String, artist: String, durationMs: Long?) = buildJsonObject {
        put("title", title)
        put("artist", artist)
        durationMs?.takeIf { it > 0 }?.let { put("duration_ms", it) }
    }

    /**
     * Audio for the song, once the server has the whole file. Throws [JukesException] for a song
     * this source can't serve (no match, wrong version, unavailable video, ...) so the next provider
     * takes over, and [V1MissingException] when the server predates `/v1`.
     */
    suspend fun requestForPlayback(
        title: String,
        artist: String,
        durationSec: Int?,
        showPreparing: Boolean,
        client: HttpClient = ApiClient.httpClient,
    ): SpotifyApi.DirectDownloadRequest {
        // Title + main artist: every featured artist in the query pulls in their other songs.
        val body = track(title, artist.split(", ").first(), durationSec?.let { it * 1000L })
        val deadline = System.currentTimeMillis() + PREPARE_BUDGET_MS
        if (showPreparing) _preparing.value = title
        try {
            var prepares = 0
            prepare@ while (true) {
                if (System.currentTimeMillis() >= deadline) error("Backend still preparing '$title'")
                prepares++
                val prepared = try {
                    call<Prepared>(client, HttpMethod.Post, "/v1/audio/prepare", body)
                } catch (e: JukesException) {
                    // 429 / 502 / 503 (pending, capacity): wait as told, then ask again.
                    if (prepares < MAX_PREPARES && (e.retryable || e.http in setOf(429, 502, 503))) {
                        waitBefore(e.retryAfterSec, deadline)
                        continue@prepare
                    }
                    throw e
                }
                // The server picks the best match; the app makes the final call.
                check(AlexaBackendApi.isSameSong(prepared.title, prepared.artist, title, artist)) {
                    "Backend match '${prepared.title}' by ${prepared.artist} is not '$title'"
                }
                val foundSec = ((prepared.durationMs ?: 0L) / 1000).toInt()
                check(isLengthOk(foundSec, durationSec)) {
                    "Backend match ${prepared.videoId} is ${foundSec}s, expected ${durationSec}s"
                }
                prepared.audioUrl?.takeIf { prepared.status == "ready" }?.let { return audio(it) }

                // Long-poll the job; network errors (airplane mode, a cell handover) just mean "poll
                // again". A server without long polls answers at once, so then back off 1 s, 2 s, 3 s.
                var i = 0
                suspend fun backOff() = delay(POLL_DELAYS_MS.getOrElse(i++) { 3_000L })
                while (System.currentTimeMillis() < deadline) {
                    val asked = System.currentTimeMillis()
                    val job = try {
                        call<Job>(client, HttpMethod.Get, "/v1/jobs/${prepared.jobId}?wait=$LONG_POLL_SEC")
                    } catch (e: JukesException) {
                        when {
                            // The job aged out; that is not "audio deleted". Prepare again (idempotent).
                            e.http == 404 -> continue@prepare
                            e.retryable || e.http == 429 || e.http == 503 -> {
                                waitBefore(e.retryAfterSec, deadline); continue
                            }
                            else -> throw e
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: V1MissingException) {
                        throw e
                    } catch (_: Exception) {
                        backOff(); continue // offline for a moment
                    }
                    when (job.status) {
                        "ready" -> job.audioUrl?.let { return audio(it) }
                        "failed" -> {
                            val err = job.error
                            if (err?.retryable == true && prepares < MAX_PREPARES) continue@prepare
                            throw JukesException(422, err?.code ?: "job_failed", false, null)
                        }
                    }
                    if (System.currentTimeMillis() - asked < 1_000) backOff()
                }
                error("Backend still preparing '$title'")
            }
        } finally {
            if (showPreparing) _preparing.update { if (it == title) null else it }
        }
    }

    private fun audio(url: String) = SpotifyApi.DirectDownloadRequest(
        url = if (url.startsWith("/")) "$base$url" else url,
        probeRanges = true, // a finished file: Content-Length and byte ranges
    )

    /** Honour Retry-After (1–30 s), never past the deadline. */
    private suspend fun waitBefore(retryAfterSec: Int?, deadline: Long) {
        val ms = (retryAfterSec ?: 2).coerceIn(1, 30) * 1000L
        val left = deadline - System.currentTimeMillis()
        if (left <= 0) return
        delay(minOf(ms, left))
    }

    /** Same tolerance as the server: within max(8 s, 7 %). 0 or unknown = can't tell, accept. */
    internal fun isLengthOk(foundSec: Int, expectedSec: Int?): Boolean {
        if (expectedSec == null || expectedSec <= 0 || foundSec <= 0) return true
        return abs(foundSec - expectedSec) <= maxOf(8, expectedSec * 7 / 100)
    }

    /**
     * Speculative: the user will probably play this soon. The server starts downloading right away;
     * a later prepare promotes it. Fire-and-forget: failures are ignored, and a 429 pauses warmups
     * for its Retry-After.
     */
    fun warmup(title: String, artist: String, durationMs: Long?) {
        val mainArtist = artist.split(", ").first()
        warm("t:${title.trim().lowercase()}|${mainArtist.trim().lowercase()}", track(title, mainArtist, durationMs))
    }

    fun warmupVideo(videoId: String) = warm("v:$videoId", buildJsonObject { put("video_id", videoId) })

    /** Server limit is 30 warmups/min per IP; stay well under it and never warm a song twice in a row. */
    private const val WARMUPS_PER_MINUTE = 20
    private const val REWARM_AFTER_MS = 30 * 60_000L
    private val warmed = LinkedHashMap<String, Long>()
    private val recentWarmups = ArrayDeque<Long>()
    @Volatile private var warmPausedUntil = 0L

    /** Whether a warmup for [key] may be sent now; records it when it may. */
    @Synchronized
    internal fun admitWarmup(key: String, now: Long = System.currentTimeMillis()): Boolean {
        if (now < warmPausedUntil) return false
        warmed[key]?.let { if (now - it < REWARM_AFTER_MS) return false }
        while (recentWarmups.isNotEmpty() && now - recentWarmups.first() >= 60_000L) recentWarmups.removeFirst()
        if (recentWarmups.size >= WARMUPS_PER_MINUTE) return false
        recentWarmups.addLast(now)
        warmed[key] = now
        if (warmed.size > 500) warmed.remove(warmed.keys.first())
        return true
    }

    @Synchronized
    internal fun resetWarmups() {
        warmed.clear(); recentWarmups.clear(); warmPausedUntil = 0L
    }

    private fun warm(key: String, body: JsonObject) {
        if (!AlexaBackendApi.isConfigured || !BuildConfig.JUKE_BACKEND_V1) return
        if (!admitWarmup(key)) return
        warmScope.launch {
            try {
                call<Prepared>(ApiClient.httpClient, HttpMethod.Post, "/v1/warmup", body)
            } catch (e: JukesException) {
                if (e.http == 429) {
                    warmPausedUntil = System.currentTimeMillis() + (e.retryAfterSec ?: 30).coerceIn(5, 120) * 1000L
                }
            } catch (_: Exception) {
            }
        }
    }

    /** The video id in one of this backend's audio URLs (`/v1/audio/<id>` or `/audio/?video_id=`). */
    fun videoIdOf(url: String): String? {
        if (base.isBlank() || !url.startsWith(base)) return null
        val path = url.removePrefix(base)
        return when {
            path.startsWith("/v1/audio/") -> path.removePrefix("/v1/audio/").substringBefore('?')
            path.startsWith("/audio/") -> Regex("""[?&]video_id=([\w-]+)""").find(path)?.groupValues?.get(1)
            else -> null
        }?.takeIf { it.isNotBlank() }
    }

    /**
     * YouTube Music radio for [videoId], seed first, in the shape the queue engine already filters,
     * dedupes and reseeds. Used when the direct YouTube Music radio fetch returns nothing.
     * Null when the backend can't answer.
     */
    suspend fun radio(videoId: String, limit: Int = 50): List<RecommenderApi.YouTubeRecommendation>? {
        if (!AlexaBackendApi.isConfigured || !BuildConfig.JUKE_BACKEND_V1) return null
        return try {
            call<Radio>(
                ApiClient.httpClient, HttpMethod.Post, "/v1/recommendations",
                buildJsonObject { put("video_id", videoId); put("limit", limit.coerceIn(1, 100)) }
            ).tracks.filter { it.videoId.isNotBlank() && it.title.isNotBlank() }.map {
                RecommenderApi.YouTubeRecommendation(
                    id = it.videoId,
                    title = it.title,
                    artist = it.artist,
                    duration = it.durationMs?.takeIf { ms -> ms > 0 }?.let { ms ->
                        val sec = ms / 1000
                        "%d:%02d".format(sec / 60, sec % 60)
                    }
                )
            }.takeIf { it.isNotEmpty() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Backend radio for $videoId failed: ${e.message}")
            null
        }
    }
}
