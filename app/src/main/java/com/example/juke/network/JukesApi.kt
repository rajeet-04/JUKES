package com.example.juke.network

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.juke.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.header
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

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
    private val NO_RETRY_CODES = setOf("extraction_failed", "authentication_required")
    private val POLL_DELAYS_MS = longArrayOf(1_000, 2_000, 3_000) // then every 3 s
    /** The server holds a job poll up to this long and answers the moment the job finishes. */
    private const val LONG_POLL_SEC = 10
    /** How long prepare may hold the request: a cold song is ready in ~1 s, an age-gated one in ~12 s (then the job poll takes over). */
    private const val PREPARE_WAIT_SEC = 10

    /**
     * Audio settings toggle (on by default): for immediate playback, ask for the server's progressive HLS
     * stream and start on it while the file is still downloading. Downloads keep the completed file.
     */
    @Volatile var progressiveEnabled = true

    @Serializable
    data class Prepared(
        @SerialName("video_id") val videoId: String,
        val title: String = "",
        val artist: String = "",
        @SerialName("duration_ms") val durationMs: Long? = null,
        @SerialName("job_id") val jobId: String,
        val status: String,
        @SerialName("audio_url") val audioUrl: String? = null,
        val streamable: Boolean = false,
        @SerialName("stream_url") val streamUrl: String? = null,
        /** "warmup" when a warmup produced this file, "requested" otherwise. */
        val pool: String? = null,
        /** Exact title and artist, but a longer upload (music videos carry intros): skip our length check. */
        @SerialName("length_relaxed") val lengthRelaxed: Boolean = false,
        /** Right song and length, but the singer is credited instead of the composer (or vice versa): skip our artist check. */
        @SerialName("artist_relaxed") val artistRelaxed: Boolean = false,
    )

    @Serializable
    data class Job(
        @SerialName("job_id") val jobId: String,
        val status: String,
        @SerialName("audio_url") val audioUrl: String? = null,
        val error: JobError? = null,
        val streamable: Boolean = false,
        @SerialName("stream_url") val streamUrl: String? = null,
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
    private data class Radio(
        val tracks: List<RadioTrack> = emptyList(),
        @SerialName("personalization_status") val status: String = "",
    )

    @Serializable
    private data class Issued(val token: String)

    // -- Optional personalisation: the user's YouTube Music session lives on the backend, tied to
    // an installation token kept here. ponytail: token in app-private prefs, Keystore if it matters.
    enum class Session { OFF, CONNECTED, RECONNECT }

    private var prefs: SharedPreferences? = null
    private val _session = MutableStateFlow(Session.OFF)
    val session: StateFlow<Session> = _session.asStateFlow()

    /** Settings toggle: backend (signed-in account) radio first instead of anonymous direct radio. */
    @Volatile var personalisedRadio = false
        private set
    private val token get() = prefs?.getString("token", null)

    fun init(context: Context) {
        val p = context.getSharedPreferences("jukes_identity", Context.MODE_PRIVATE).also { prefs = it }
        personalisedRadio = p.getBoolean("personalised", false)
        _session.value = runCatching { Session.valueOf(p.getString("session", "OFF")!!) }.getOrDefault(Session.OFF)
    }

    fun setPersonalisedRadio(on: Boolean) {
        personalisedRadio = on
        prefs?.edit()?.putBoolean("personalised", on)?.apply()
    }

    private fun setSession(s: Session) {
        _session.value = s
        prefs?.edit()?.putString("session", s.name)?.apply()
    }

    /** Sends the Cookie header captured from music.youtube.com to the backend. Throws [JukesException]. */
    suspend fun connectYoutube(cookie: String) {
        val t = token ?: call<Issued>(ApiClient.httpClient, HttpMethod.Post, "/v1/installations").token
            .also { prefs?.edit()?.putString("token", it)?.apply() }
        check(t.isNotBlank())
        call<Unit>(
            ApiClient.httpClient, HttpMethod.Put, "/v1/me/youtube",
            buildJsonObject { put("cookie", cookie); put("account_index", 0) }, auth = true
        )
        setSession(Session.CONNECTED)
    }

    /** Revokes the installation: deletes the stored session and token on the backend. */
    suspend fun disconnectYoutube() {
        if (token != null) call<Unit>(ApiClient.httpClient, HttpMethod.Delete, "/v1/me", auth = true)
        forgetInstallation()
    }

    private fun forgetInstallation() {
        prefs?.edit()?.remove("token")?.apply()
        setSession(Session.OFF)
    }

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

    /** Songs the listener is waiting on right now. The link to YouTube is shared, so background fills hold back meanwhile. */
    private val foreground = MutableStateFlow(0)

    /** Returns once no listener-chosen song is loading, or after [maxMs] so fills never starve. */
    suspend fun awaitForegroundIdle(maxMs: Long = 20_000) {
        kotlinx.coroutines.withTimeoutOrNull(maxMs) { foreground.first { it == 0 } }
    }

    /** Opens the TLS connection to the backend (a new one costs ~1.2 s) so the first real call reuses it. */
    fun preconnect() {
        warmScope.launch {
            runCatching {
                ApiClient.okHttp.newCall(okhttp3.Request.Builder().head().url(base).build()).execute().close()
            }
        }
    }

    private val warmScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private suspend inline fun <reified T> call(
        client: HttpClient,
        method: HttpMethod,
        path: String,
        body: JsonObject? = null,
        auth: Boolean = false,
    ): T {
        val response = client.request("$base$path") {
            this.method = method
            if (auth) token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            if (body != null) {
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }
        }
        val text = response.bodyAsText()
        val status = response.status.value
        if (status in 200..299) {
            @Suppress("UNCHECKED_CAST")
            return if (text.isBlank()) Unit as T else json.decodeFromString(text)
        }
        if (auth && status == 401) forgetInstallation()
        val error = runCatching { json.decodeFromString<ErrorBody>(text).error }.getOrNull()
        // An unknown route is a JSON 404 `not_found` on new servers and an HTML page on old ones.
        if (status == 404 && (error == null || error.code == "not_found")) throw V1MissingException()
        throw JukesException(
            http = status,
            code = error?.code ?: "http_$status",
            retryable = error?.retryable == true,
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
     *
     * [progressive]: also accept the server's HLS stream while the file is still downloading
     * (returned with [SpotifyApi.DirectDownloadRequest.awaitFile] for the completed file).
     */
    suspend fun requestForPlayback(
        title: String,
        artist: String,
        durationSec: Int?,
        showPreparing: Boolean,
        client: HttpClient = ApiClient.httpClient,
        progressive: Boolean = false,
        videoId: String? = null,
    ): SpotifyApi.DirectDownloadRequest {
        val opt = if (progressive) "progressive=1" else ""
        // wait=10: the server holds prepare up to 10 s and answers "ready" with audio_url for a cold song,
        // so one request replaces prepare + poll (and progressive HLS) for the usual case.
        val prepareQuery = "?" + listOfNotNull(opt.ifEmpty { null }, "wait=$PREPARE_WAIT_SEC").joinToString("&")
        val started = System.nanoTime()
        fun elapsed() = (System.nanoTime() - started) / 1_000_000
        // Title + main artist: every featured artist in the query pulls in their other songs.
        // A known video id skips the server's 1.5-2 s title search and starts the download at once.
        val body = if (videoId != null) buildJsonObject { put("video_id", videoId) }
        else track(title, artist.split(", ").first(), durationSec?.let { it * 1000L })
        val deadline = System.currentTimeMillis() + PREPARE_BUDGET_MS
        if (showPreparing) { _preparing.value = title; foreground.update { it + 1 } }
        try {
            var prepares = 0
            prepare@ while (true) {
                if (System.currentTimeMillis() >= deadline) error("Backend still preparing '$title'")
                prepares++
                val prepared = try {
                    call<Prepared>(client, HttpMethod.Post, "/v1/audio/prepare$prepareQuery", body)
                } catch (e: JukesException) {
                    // 429 / 502 / 503 (pending, capacity): wait as told, then ask again.
                    if (prepares < MAX_PREPARES && (e.retryable || e.http in setOf(429, 502, 503))) {
                        waitBefore(e.retryAfterSec, deadline)
                        continue@prepare
                    }
                    throw e
                }
                wlog("prepare '$title' -> ${prepared.status} pool=${prepared.pool} video=${prepared.videoId} after ${elapsed()}ms")
                reportPrepare(title, artist, prepared, elapsed())
                // The server picks the best match; the app makes the final call.
                // An id we sent was already validated by the radio/Spotify match; only title searches need our check.
                // artist_relaxed: the title must still match, only the credited artist may differ.
                val expectedArtist = if (prepared.artistRelaxed) prepared.artist else artist
                check(videoId != null || AlexaBackendApi.isSameSong(prepared.title, prepared.artist, title, expectedArtist)) {
                    "Backend match '${prepared.title}' by ${prepared.artist} is not '$title'"
                }
                val foundSec = ((prepared.durationMs ?: 0L) / 1000).toInt()
                check(videoId != null || prepared.lengthRelaxed || isLengthOk(foundSec, durationSec)) {
                    "Backend match ${prepared.videoId} is ${foundSec}s, expected ${durationSec}s"
                }
                prepared.audioUrl?.takeIf { prepared.status == "ready" }?.let {
                    if (progressive) logProgressive("'$title' file ready at prepare (${elapsed()}ms)")
                    return audio(it)
                }
                if (progressive && prepared.streamable) prepared.streamUrl?.let {
                    logProgressive("'$title' stream ready at prepare (${elapsed()}ms)")
                    return stream(it, prepared.jobId, title, started, client)
                }

                // Long-poll the job; network errors (airplane mode, a cell handover) just mean "poll
                // again". A server without long polls answers at once, so then back off 1 s, 2 s, 3 s.
                var i = 0
                suspend fun backOff() = delay(POLL_DELAYS_MS.getOrElse(i++) { 3_000L }.milliseconds)
                while (System.currentTimeMillis() < deadline) {
                    val asked = System.currentTimeMillis()
                    val job = try {
                        call<Job>(client, HttpMethod.Get, "/v1/jobs/${prepared.jobId}?wait=$LONG_POLL_SEC" + (if (progressive) "&$opt" else ""))
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
                        "ready" -> job.audioUrl?.let {
                            if (progressive) logProgressive("'$title' file ready before stream (${elapsed()}ms)")
                            return audio(it)
                        }
                        "failed" -> {
                            val err = job.error
                            // A video that can't be extracted won't be better a moment later: fall through to the next source.
                            if (err?.retryable == true && err.code !in NO_RETRY_CODES && prepares < MAX_PREPARES) {
                                delay((prepares * 1_000L).milliseconds) // 1 s, then 2 s
                                continue@prepare
                            }
                            throw JukesException(422, err?.code ?: "job_failed", false, null)
                        }
                        else -> if (progressive && job.streamable) job.streamUrl?.let {
                            logProgressive("'$title' stream ready (${elapsed()}ms)")
                            return stream(it, prepared.jobId, title, started, client)
                        }
                    }
                    if (System.currentTimeMillis() - asked < 1_000) backOff()
                }
                error("Backend still preparing '$title'")
            }
        } finally {
            if (showPreparing) {
                _preparing.update { if (it == title) null else it }
                foreground.update { it - 1 }
            }
        }
    }

    private fun audio(url: String) = SpotifyApi.DirectDownloadRequest(
        url = if (url.startsWith("/")) "$base$url" else url,
        probeRanges = true, // a finished file: Content-Length and byte ranges
    )

    /** The HLS playlist to play now; [SpotifyApi.DirectDownloadRequest.awaitFile] gives the file later. */
    private fun stream(url: String, jobId: String, title: String, started: Long, client: HttpClient) =
        SpotifyApi.DirectDownloadRequest(
            url = if (url.startsWith("/")) "$base$url" else url,
            probeRanges = false,
            awaitFile = { awaitFile(jobId, title, started, client) },
        )

    /** Long-poll [jobId] (without progressive) until the completed file is ready. */
    private suspend fun awaitFile(jobId: String, title: String, started: Long, client: HttpClient): SpotifyApi.DirectDownloadRequest {
        val deadline = System.currentTimeMillis() + PREPARE_BUDGET_MS
        var i = 0
        while (System.currentTimeMillis() < deadline) {
            val asked = System.currentTimeMillis()
            val job = try {
                call<Job>(client, HttpMethod.Get, "/v1/jobs/$jobId?wait=$LONG_POLL_SEC")
            } catch (e: JukesException) {
                if (e.retryable || e.http == 429 || e.http == 503) {
                    waitBefore(e.retryAfterSec, deadline); continue
                }
                throw e
            } catch (e: CancellationException) {
                throw e
            } catch (e: V1MissingException) {
                throw e
            } catch (_: Exception) {
                delay(POLL_DELAYS_MS.getOrElse(i++) { 3_000L }.milliseconds); continue
            }
            when (job.status) {
                "ready" -> job.audioUrl?.let {
                    logProgressive("'$title' file ready (${(System.nanoTime() - started) / 1_000_000}ms after request)")
                    return audio(it)
                }
                "failed", "evicted" -> throw JukesException(422, job.error?.code ?: "job_${job.status}", false, null)
            }
            if (System.currentTimeMillis() - asked < 1_000) delay(POLL_DELAYS_MS.getOrElse(i++) { 3_000L }.milliseconds)
        }
        error("Backend still preparing '$title'")
    }

    /** Timing lines for comparing progressive playback on a device (`adb logcat -s JukesApi`). */
    /** Warmup behaviour, one line per event: `adb logcat -s JukesWarmup`. */
    private fun wlog(message: String) {
        try {
            Log.println(Log.INFO, "JukesWarmup", message) // not Log.i: release R8 strips Log.i
        } catch (_: RuntimeException) {
        }
    }

    private fun logProgressive(message: String) {
        try {
            Log.d(TAG, "Progressive: $message")
        } catch (_: RuntimeException) {
            // android.util.Log is not available in JVM unit tests
        }
    }

    /** True for this backend's progressive HLS playlist URLs. */
    fun isStreamPlaylist(url: String?): Boolean = url != null && "/v1/streams/" in url && ".m3u8" in url

    /** Honor Retry-After (1–30 s), never past the deadline. */
    private suspend fun waitBefore(retryAfterSec: Int?, deadline: Long) {
        val ms = (retryAfterSec ?: 2).coerceIn(1, 30) * 1000L
        val left = deadline - System.currentTimeMillis()
        if (left <= 0) return
        delay(minOf(ms, left).milliseconds)
    }

    /** Same tolerance as the server: within max(8 s, 7 %). 0 or unknown = can't tell, accept. */
    internal fun isLengthOk(foundSec: Int, expectedSec: Int?): Boolean {
        return expectedSec == null || expectedSec <= 0 || foundSec <= 0 || abs(foundSec - expectedSec) <= maxOf(8, expectedSec * 7 / 100)
    }

    /**
     * Speculative: the user will probably play this soon. The server starts downloading right away;
     * a later prepare promotes it. Fire-and-forget: failures are ignored, and a 429 pauses warmups
     * for its Retry-After.
     */
    fun warmup(title: String, artist: String, durationMs: Long?, source: String? = null, resolveOnly: Boolean = false) {
        val mainArtist = artist.split(", ").first()
        warm("t:${title.trim().lowercase()}|${mainArtist.trim().lowercase()}" + if (resolveOnly) ":r" else "",
            track(title, mainArtist, durationMs), source, resolveOnly)
    }

    /** The query names this title (plus maybe the artist): worth a full download, not just a resolve. */
    internal fun confidentMatch(query: String, title: String): Boolean {
        fun norm(t: String) = t.lowercase().replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()
        val q = norm(query); val t = norm(title)
        return t.isNotEmpty() && (q == t || q.startsWith("$t "))
    }

    fun warmupVideo(videoId: String, source: String? = null, resolveOnly: Boolean = false) =
        warm("v:$videoId" + if (resolveOnly) ":r" else "", buildJsonObject { put("video_id", videoId) }, source, resolveOnly)

    /** Server limit is 30 warmups/min per IP; stay well under it and never warm a song twice in a row. */
    private const val WARMUPS_PER_MINUTE = 20
    private const val REWARM_AFTER_MS = 30 * 60_000L
    private val warmed = LinkedHashMap<String, Long>()
    private val recentWarmups = ArrayDeque<Long>()
    @Volatile private var warmPausedUntil = 0L

    /** Whether a warmup for [key] may be sent now; records it when it may. */
    @Synchronized
    internal fun admitWarmup(key: String, now: Long = System.currentTimeMillis(), counted: Boolean = true): Boolean {
        if (now < warmPausedUntil) return false
        warmed[key]?.let { if (now - it < REWARM_AFTER_MS) return false }
        while (recentWarmups.isNotEmpty() && now - recentWarmups.first() >= 60_000L) recentWarmups.removeFirst()
        if (counted) {  // resolve-only warmups download nothing, so they stay off the download budget
            if (recentWarmups.size >= WARMUPS_PER_MINUTE) return false
            recentWarmups.addLast(now)
        }
        warmed[key] = now
        if (warmed.size > 500) warmed.remove(warmed.keys.first())
        return true
    }

    @Synchronized
    internal fun resetWarmups() {
        warmed.clear(); recentWarmups.clear(); warmPausedUntil = 0L
    }

    /** What this app instance warmed, to join with the prepare that follows (permanent record: PostHog `warmup_prepare`). */
    private class WarmInfo(val source: String?, val resolveOnly: Boolean, val at: Long)
    private val warmInfo = LinkedHashMap<String, WarmInfo>()
    private val warmSent = java.util.concurrent.atomic.AtomicInteger()
    private val warmSkipped = java.util.concurrent.atomic.AtomicInteger()

    @Synchronized private fun noteWarm(key: String, info: WarmInfo) {
        warmInfo[key] = info
        if (warmInfo.size > 500) warmInfo.remove(warmInfo.keys.first())
    }

    @Synchronized private fun warmFor(key: String): WarmInfo? = warmInfo[key] ?: warmInfo["$key:r"]

    private fun reportPrepare(title: String, artist: String, prepared: Prepared, ms: Long) {
        val info = warmFor("t:${title.trim().lowercase()}|${artist.split(", ").first().trim().lowercase()}")
        com.example.juke.analytics.AnalyticsManager.log("warmup_prepare", mapOf(
                "status" to prepared.status,
                "pool" to (prepared.pool ?: "unknown"),
                "ms_to_prepare_response" to ms,
                "warm_source" to (info?.source ?: if (info != null) "untagged" else "none"),
                "warm_mode" to (if (info == null) "none" else if (info.resolveOnly) "resolve" else "full"),
                "warm_age_s" to (info?.let { (System.currentTimeMillis() - it.at) / 1000 } ?: -1L),
                "warms_sent" to warmSent.get(),
                "warms_skipped" to warmSkipped.get(),
        ))
    }

    private fun warm(key: String, body: JsonObject, source: String? = null, resolveOnly: Boolean = false) {
        if (!AlexaBackendApi.isConfigured || !BuildConfig.JUKE_BACKEND_V1) return
        if (!admitWarmup(key, counted = !resolveOnly)) { warmSkipped.incrementAndGet(); wlog("warm skip ($key) src=$source: deduped or over budget"); return }
        warmSent.incrementAndGet()
        noteWarm(key, WarmInfo(source, resolveOnly, System.currentTimeMillis()))
        val query = listOfNotNull(source?.let { "source=$it" }, if (resolveOnly) "resolve_only=1" else null)
            .joinToString("&").let { if (it.isEmpty()) "" else "?$it" }
        warmScope.launch {
            val t0 = System.nanoTime()
            try {
                val r = call<Prepared>(ApiClient.httpClient, HttpMethod.Post, "/v1/warmup$query", body)
                wlog("warm sent ($key) src=$source resolveOnly=$resolveOnly -> ${r.status} video=${r.videoId} ${(System.nanoTime() - t0) / 1_000_000}ms")
            } catch (e: JukesException) {
                wlog("warm failed ($key) src=$source http=${e.http} code=${e.code}")
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
            path.startsWith("/audio/") || path.startsWith("/v1/streams/") ->
                Regex("""[?&]video_id=([\w-]+)""").find(path)?.groupValues?.get(1)
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
        // Signed-in users only: anonymous backend radio is heavy and not wanted.
        if (token == null || _session.value == Session.OFF) return null
        return try {
            val radio = call<Radio>(
                ApiClient.httpClient, HttpMethod.Post, "/v1/recommendations",
                buildJsonObject { put("video_id", videoId); put("limit", limit.coerceIn(1, 100)) },
                auth = true
            )
            Log.d(TAG, "Backend radio for $videoId: status=${radio.status} tracks=${radio.tracks.size}")
            if (radio.status == "reconnect_required") setSession(Session.RECONNECT)
            if (radio.status != "connected") return null
            radio.tracks.filter { it.videoId.isNotBlank() && it.title.isNotBlank() }.map {
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

    /** Radio for [videoId]: signed-in backend first when personalised, else direct YouTube Music first. */
    suspend fun fullRadio(videoId: String): List<RecommenderApi.YouTubeRecommendation> {
        val direct: suspend () -> List<RecommenderApi.YouTubeRecommendation>? =
            { RecommenderApi.fetchFullRadioQueue(videoId) }
        val backend: suspend () -> List<RecommenderApi.YouTubeRecommendation>? = { radio(videoId) }
        val order = if (personalisedRadio) "backend,direct" else "direct,backend"
        return (if (personalisedRadio) listOf(backend, direct) else listOf(direct, backend)).firstNonEmpty()
            .also { Log.d(TAG, "Radio for $videoId: order=$order tracks=${it.size}") }
    }

    internal suspend fun <T> List<suspend () -> List<T>?>.firstNonEmpty(): List<T> {
        for (source in this) source()?.takeIf { it.isNotEmpty() }?.let { return it }
        return emptyList()
    }
}
