package com.example.juke.network

import android.util.Log
import com.example.juke.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder
import kotlin.math.abs

/**
 * Audio from the JUKES backend (youtube-music-alexa-skill): YouTube Music search plus a yt-dlp
 * download cache.
 *
 * Current servers are used through [JukesApi] (`/v1` prepare + poll). Two older generations are
 * still supported (and `/audio/` is the fallback while `/v1` rolls out, see `JUKE_BACKEND_V1`):
 * - `/audio/` (newer): side-effect-free search (`q` + `duration` picks the matching version) and
 *   audio; `wait=1` returns the finished file with Content-Length and byte ranges.
 * - Older servers without it: `/alexa/search/` to find the video, `/get_stream/` for its `/proxy/`
 *   URL. `/proxy/` is the Echo's own endpoint, so on those servers a fetch also shows up on the
 *   Echo's now-playing card until the server is updated.
 *
 * The key travels in the URL (`key=`) because ExoPlayer streams the returned URL directly and
 * cannot add headers.
 */
object AlexaBackendApi {
    private const val TAG = "AlexaBackendApi"
    private val json = Json { ignoreUnknownKeys = true }

    val isConfigured: Boolean
        get() = BuildConfig.JUKE_BACKEND_URL.isNotBlank() && BuildConfig.JUKE_BACKEND_KEY.isNotBlank()

    private val base get() = BuildConfig.JUKE_BACKEND_URL.trimEnd('/')
    private val key get() = BuildConfig.JUKE_BACKEND_KEY

    /** null = not probed yet, false = server predates `/audio/` (use the older endpoints). */
    @Volatile private var hasAudioEndpoint: Boolean? = null

    /** false = server predates `/v1` (use `/audio/`). */
    @Volatile private var hasV1: Boolean? = null

    /** Uploads that are a different rendition of the song; skipped unless the title asks for them. */
    private val VARIANT_WORDS = listOf(
        "remix", "slowed", "reverb", "sped up", "speed up", "lofi", "lo-fi", "live", "karaoke",
        "instrumental", "cover", "dance mix", "8d", "nightcore", "acoustic", "unplugged", "mashup",
        "reprise", "extended", "version"
    )

    /**
     * A download request for the song. [live] = for immediate playback: a cache miss is streamed
     * as yt-dlp produces it (fast first byte, no length). Otherwise, the server finishes the file
     * first so the download has a length and can be fetched in parallel ranges.
     */
    suspend fun getDownloadRequest(
        title: String,
        artist: String,
        durationSec: Int?,
        live: Boolean,
        client: HttpClient = ApiClient.httpClient
    ): SpotifyApi.DirectDownloadRequest {
        check(isConfigured) { "Backend not configured" }
        // `/v1` prepare + poll; the legacy `/audio/` below stays as the rollout fallback.
        if (BuildConfig.JUKE_BACKEND_V1 && hasV1 != false) {
            try {
                return JukesApi.requestForPlayback(
                    title, artist, durationSec, showPreparing = live, client = client,
                    progressive = live && JukesApi.progressiveEnabled
                )
                    .also { hasV1 = true }
            } catch (_: JukesApi.V1MissingException) {
                Log.i(TAG, "Backend has no /v1 API; using /audio/")
                hasV1 = false
            }
        }
        // Title + main artist: every featured artist in the query pulls in their other songs.
        val mainArtist = artist.split(", ").first()
        val query = "$title $mainArtist"
        val expected = durationSec?.takeIf { it > 0 }

        if (hasAudioEndpoint != false) {
            val response = client.get("$base/audio/") {
                header("X-Api-Key", key)
                parameter("q", query)
                expected?.let { parameter("duration", it) }
                parameter("info", "1")
            }
            val body = response.bodyAsText()
            val isJson = response.contentType()?.match(ContentType.Application.Json) == true
            fun invalidResponse(): Nothing = error("Backend /audio/ HTTP ${response.status.value}: ${body.take(120)}")
            when (response.status.value) {
                200 -> {
                    if (!isJson) invalidResponse()
                    hasAudioEndpoint = true
                    val info = json.parseToJsonElement(body).jsonObject
                    val videoId = info.string("video_id") ?: error("Backend returned no video_id")
                    // The server picks by length only; make sure it is the same song.
                    check(isSameSong(info, title, artist)) {
                        "Backend match '${info.string("title")}' by ${info.string("artist")} is not '$title'"
                    }
                    val foundSec = (info["duration_ms"]?.jsonPrimitive?.intOrNull ?: 0) / 1000
                    checkLength(foundSec, expected, videoId)
                    return audioRequest(videoId, live)
                }
                // A route the server doesn't have comes back as its HTML "Page not found" page.
                404 -> {
                    if (isJson) invalidResponse()
                    Log.i(TAG, "Backend has no /audio/ endpoint; using /alexa/search + /proxy")
                    hasAudioEndpoint = false
                }
                else -> invalidResponse()
            }
        }
        return legacyRequest(query, title, artist, expected, client)
    }

    private fun audioRequest(videoId: String, live: Boolean): SpotifyApi.DirectDownloadRequest {
        val url = buildString {
            append("$base/audio/?video_id=").append(enc(videoId)).append("&key=").append(enc(key))
            if (!live) append("&wait=1")
        }
        // A finished file has Content-Length and ranges; a live stream has neither.
        return SpotifyApi.DirectDownloadRequest(url = url, probeRanges = !live)
    }

    private suspend fun legacyRequest(
        query: String,
        title: String,
        artist: String,
        expected: Int?,
        client: HttpClient
    ): SpotifyApi.DirectDownloadRequest {
        val search = client.get("$base/alexa/search/") {
            header("X-Api-Key", key)
            parameter("q", query)
        }
        check(search.status.value == 200) { "Backend search HTTP ${search.status.value}" }
        val songs = json.parseToJsonElement(search.bodyAsText()).jsonObject["songs"]?.jsonArray
            ?.filterIsInstance<JsonObject>().orEmpty()
        val pick = pickSong(songs.take(20), title, artist, expected)
            ?: error("Backend search has no '$title' by $artist") // next source takes over
        val videoId = pick.string("video_id")!!
        checkLength((pick["duration_ms"]?.jsonPrimitive?.intOrNull ?: 0) / 1000, expected, videoId)

        val stream = client.get("$base/get_stream/") {
            header("X-Api-Key", key)
            parameter("video_id", videoId)
        }
        check(stream.status.value == 200) { "Backend get_stream HTTP ${stream.status.value}" }
        val url = json.parseToJsonElement(stream.bodyAsText()).jsonObject.string("audio_url")
            ?: error("Backend returned no audio_url")
        return SpotifyApi.DirectDownloadRequest(url = url, probeRanges = false)
    }

    /**
     * The search hit that is this song, or null. Search ranking is popularity-driven: other songs
     * by a featured artist, or another song with the same name, often come first. So a hit must
     * carry the Spotify title and at least one of its artists, must not be a remix/slowed/live/...
     * rendition the title didn't ask for, and must not have a clearly different length when the
     * server reports one. Among those, a length match wins, then search order.
     */
    internal fun pickSong(songs: List<JsonObject>, title: String, artist: String, expected: Int?): JsonObject? {
        val wanted = title.lowercase()
        val tolerance = expected?.let { maxOf(8, it * 7 / 100) }
        fun seconds(song: JsonObject) = (song["duration_ms"]?.jsonPrimitive?.intOrNull ?: 0) / 1000
        val matches = songs.filter { song ->
            val name = song.string("title").orEmpty().lowercase()
            val sec = seconds(song)
            !song.string("video_id").isNullOrBlank() &&
                isSameSong(song, title, artist) &&
                VARIANT_WORDS.none { it in name && it !in wanted } &&
                (expected == null || tolerance == null || sec <= 0 || abs(sec - expected) <= tolerance)
        }
        return matches.firstOrNull { tolerance != null && seconds(it) > 0 } ?: matches.firstOrNull()
    }

    internal fun isSameSong(song: JsonObject, title: String, artist: String): Boolean =
        isSameSong(song.string("title").orEmpty(), song.string("artist").orEmpty(), title, artist)

    /**
     * Whether a hit ([hitTitle] by [hitArtist]) is the song [title] by [artist]: same title (ignoring
     * "(feat. …)", "[…]" and punctuation) and at least one shared artist.
     */
    internal fun isSameSong(hitTitle: String, hitArtist: String, title: String, artist: String): Boolean {
        // Spotify's " - Remastered 2011" / " - Radio Edit" suffix is not part of the name. (Only on
        // the Spotify side: uploads use "Artist - Song".)
        val coreTitle = words(stripExtras(title.substringBefore(" - "))).ifBlank { words(title) }
        if (coreTitle.isBlank()) return false
        // Uploads often put the artist in the title ("Artist - Song ft X"), so look at both.
        val hitWords = words(stripExtras(hitTitle))
        if (" $coreTitle " !in " $hitWords ") return false
        val credits = " " + words("$hitArtist $hitTitle") + " "
        return artist.split(", ").map(::words).any { it.length >= 2 && " $it " in credits }
    }

    private val BRACKETS = Regex("""[(\[][^)\]]*[)\]]""")
    private val FEATURING = Regex("""\b(feat|ft|featuring)\b.*""", RegexOption.IGNORE_CASE)
    private val NON_WORD = Regex("""[^\p{L}\p{N}]+""")
    private fun stripExtras(text: String) = FEATURING.replace(BRACKETS.replace(text, " "), " ")
    private fun words(text: String) = NON_WORD.replace(text.lowercase(), " ").trim()

    /** Reject a clearly different version before downloading anything (0 = server didn't say). */
    private fun checkLength(foundSec: Int, expected: Int?, videoId: String) {
        if (expected == null || foundSec <= 0) return
        if (abs(foundSec - expected) > maxOf(8, expected * 7 / 100)) {
            error("Backend match $videoId is ${foundSec}s, expected ${expected}s")
        }
    }

    private fun JsonObject.string(name: String) = this[name]?.jsonPrimitive?.contentOrNull
    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
}
