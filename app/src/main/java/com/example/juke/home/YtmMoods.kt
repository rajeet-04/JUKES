package com.example.juke.home

import com.example.juke.home.YtmCharts.path
import com.example.juke.home.YtmCharts.str
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.util.Locale

/**
 * Moods from YouTube Music's Home feed chips (Relax, Romance, Energize…), not the editorial mood
 * pages: those are the same global, mostly English shelves in every country, while a Home chip
 * filters the regional Home feed, so India gets Bollywood, Punjabi and Indian indie in the mood.
 * Unauthenticated: no cookie leaves the phone.
 */
object YtmMoods {
    data class Mood(val title: String, val params: String)

    /** One mood section: either songs the feed already lists, or a playlist to fetch. */
    data class Shelf(val title: String, val playlists: List<String>, val songs: List<FeedSong> = emptyList())

    private val country get() = Locale.getDefault().country.ifBlank { "US" }
    @Volatile private var moodsCache: List<Mood>? = null

    suspend fun moods(): List<Mood> = moodsCache ?: safe {
        parseMoods(YtmCharts.browse("FEmusic_home", country))
    }.also { if (it.isNotEmpty()) moodsCache = it }

    suspend fun shelves(mood: Mood): List<Shelf> = safe {
        parseMoodFeed(YtmCharts.browse("FEmusic_home", country, mood.params))
    }

    /** Throws on failure so a pager can tell "offline" from "empty". */
    suspend fun playlistSongs(playlistId: String): List<FeedSong> =
        YtmCharts.parsePlaylist(YtmCharts.browse("VL$playlistId", country))

    private suspend fun <T> safe(block: suspend () -> List<T>): List<T> = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.w("YtmMoods", "Mood browse failed: ${e.javaClass.simpleName}: ${e.message}")
        emptyList()
    }

    /** The Home feed's chips, minus Podcasts. */
    internal fun parseMoods(json: String): List<Mood> {
        val moods = mutableListOf<Mood>()
        YtmCharts.walk(Json.parseToJsonElement(json)) { o ->
            val c = o["chipCloudChipRenderer"] as? JsonObject ?: return@walk
            val e = c.path("navigationEndpoint", "browseEndpoint") as? JsonObject ?: return@walk
            if (e["browseId"]?.str() != "FEmusic_home") return@walk
            val params = e["params"]?.str() ?: return@walk
            val title = YtmCharts.runsText(c["text"])
            if (title.isNotBlank() && !title.equals("Podcasts", ignoreCase = true)) moods += Mood(title, params)
        }
        return moods.distinctBy { it.title }
    }

    /** A song shelf stays one section; a shelf of playlists becomes one section per playlist. */
    internal fun parseMoodFeed(json: String): List<Shelf> {
        val shelves = mutableListOf<Shelf>()
        YtmCharts.walk(Json.parseToJsonElement(json)) { o ->
            val c = o["musicCarouselShelfRenderer"] as? JsonObject ?: return@walk
            val title = YtmCharts.runsText(c.path("header", "musicCarouselShelfBasicHeaderRenderer", "title"))
            val items = (c["contents"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            val songs = YtmCharts.parsePlaylist(JsonObject(mapOf("contents" to JsonArray(items))).toString())
            if (songs.isNotEmpty()) {
                if (title.isNotBlank()) shelves += Shelf(title, emptyList(), songs)
                return@walk
            }
            items.forEach { item ->
                val r = item["musicTwoRowItemRenderer"] as? JsonObject ?: return@forEach
                val id = r.path("navigationEndpoint", "browseEndpoint", "browseId")?.str()
                    ?.takeIf { it.startsWith("VL") }?.removePrefix("VL") ?: return@forEach
                shelves += Shelf(YtmCharts.runsText(r["title"]).trim(), listOf(id))
            }
        }
        return shelves.distinctBy { it.playlists.firstOrNull() ?: it.title }
    }
}
