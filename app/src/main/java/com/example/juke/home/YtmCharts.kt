package com.example.juke.home

import com.example.juke.network.ApiClient
import com.example.juke.network.RecommenderApi
import com.example.juke.network.SongTitle
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.Locale

/**
 * Trending songs from YouTube Music's public charts, no sign-in. The charts page only links to
 * chart playlists, so this is two browse calls: charts → the daily top videos playlist → its songs.
 */
object YtmCharts {
    private const val BROWSE = "https://music.youtube.com/youtubei/v1/browse?prettyPrint=false"
    private const val CLIENT_VERSION = "1.20260414.05.00"
    private const val TTL_MS = 6 * 60 * 60 * 1000L

    @Volatile private var cache: Pair<Long, List<FeedSong>>? = null

    suspend fun trending(country: String = Locale.getDefault().country.ifBlank { "US" }): List<FeedSong> {
        cache?.let { (at, songs) -> if (System.currentTimeMillis() - at < TTL_MS) return songs }
        return try {
            val playlist = parseChartPlaylistId(browse("FEmusic_charts", country)) ?: return emptyList()
            parsePlaylist(browse(playlist, country))
                .filterNot { RecommenderApi.isSpamTitle(it.title) }
                .also { if (it.isNotEmpty()) cache = System.currentTimeMillis() to it }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** One unauthenticated YouTube Music browse call (also used by [YtmMoods]). */
    internal suspend fun browse(browseId: String, country: String, params: String? = null): String =
        ApiClient.httpClient.post(BROWSE) {
            contentType(ContentType.Application.Json)
            val extra = params?.let { ""","params":"$it"""" }.orEmpty()
            setBody("""{"context":{"client":{"clientName":"WEB_REMIX","clientVersion":"$CLIENT_VERSION","hl":"en","gl":"$country"}},"browseId":"$browseId"$extra}""")
        }.bodyAsText()

    /** The chart playlist to use: daily top videos, else the top 100, else whichever comes first. */
    internal fun parseChartPlaylistId(json: String): String? {
        val lists = mutableListOf<Pair<String, String>>() // title to browseId
        walk(Json.parseToJsonElement(json)) { o ->
            val r = o["musicTwoRowItemRenderer"] as? JsonObject ?: return@walk
            val id = r.path("navigationEndpoint", "browseEndpoint", "browseId")?.str() ?: return@walk
            if (id.startsWith("VL")) lists += runsText(r["title"]) to id
        }
        return (lists.firstOrNull { "Daily Top" in it.first } ?: lists.firstOrNull { "Top 100" in it.first } ?: lists.firstOrNull())?.second
    }

    internal fun parsePlaylist(json: String): List<FeedSong> {
        val songs = mutableListOf<FeedSong>()
        walk(Json.parseToJsonElement(json)) { o ->
            val r = o["musicResponsiveListItemRenderer"] as? JsonObject ?: return@walk
            val id = r.path("playlistItemData", "videoId")?.str() ?: return@walk
            val cols = (r["flexColumns"] as? JsonArray).orEmpty().map {
                runsText((it as JsonObject).path("musicResponsiveListItemFlexColumnRenderer", "text"))
            }
            val title = cols.getOrNull(0)?.let(::displayTitle).orEmpty()
            val artist = cols.getOrNull(1).orEmpty().substringBefore(" • ").trim()
            if (title.isBlank() || artist.isBlank()) return@walk
            val length = (r["fixedColumns"] as? JsonArray)?.firstOrNull()?.let {
                runsText((it as JsonObject).path("musicResponsiveListItemFixedColumnRenderer", "text"))
            }
            songs += FeedSong(id, title, artist, FeedSong.art(id), SongTitle.clockSeconds(length))
        }
        return songs.distinctBy { it.videoId }
    }

    /** Chart entries are video titles: keep the song name before " | ", " - Official…" or "(Official…)". */
    internal fun displayTitle(raw: String): String =
        raw.substringBefore(" | ")
            .replace(Regex("""\s*[-–]\s*Official.*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*[(\[]\s*Official[^)\]]*[)\]]""", RegexOption.IGNORE_CASE), "")
            .trim()
            .ifBlank { raw.trim() }

    internal fun walk(e: JsonElement, visit: (JsonObject) -> Unit) {
        when (e) {
            is JsonObject -> { visit(e); e.values.forEach { walk(it, visit) } }
            is JsonArray -> e.forEach { walk(it, visit) }
            else -> Unit
        }
    }

    internal fun JsonObject.path(vararg keys: String): JsonElement? =
        keys.fold(this as JsonElement?) { acc, k -> (acc as? JsonObject)?.get(k) }

    internal fun JsonElement.str(): String? = (this as? JsonPrimitive)?.contentOrNull

    internal fun runsText(text: JsonElement?): String =
        ((text as? JsonObject)?.get("runs") as? JsonArray).orEmpty()
            .joinToString("") { ((it as JsonObject)["text"] as? JsonPrimitive)?.contentOrNull.orEmpty() }
}
