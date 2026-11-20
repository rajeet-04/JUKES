package com.example.juke.home

import com.example.juke.home.YtmCharts.path
import com.example.juke.home.YtmCharts.str
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import java.util.Locale

/**
 * YouTube Music's own "Moods & moments": each mood is a page of curated shelves ("Coffee shop
 * blends", "Peaceful bedtime"), each shelf a handful of playlists. That is what makes a mood chip
 * return songs of that mood, where a text search for "Relax" returns songs named Relax.
 */
object YtmMoods {
    data class Mood(val title: String, val params: String)
    data class Shelf(val title: String, val playlists: List<String>)

    private val country get() = Locale.getDefault().country.ifBlank { "US" }
    @Volatile private var moodsCache: List<Mood>? = null

    suspend fun moods(): List<Mood> = moodsCache ?: safe {
        parseMoods(YtmCharts.browse("FEmusic_moods_and_genres", country))
    }.also { if (it.isNotEmpty()) moodsCache = it }

    suspend fun shelves(mood: Mood): List<Shelf> = safe {
        parseCategory(YtmCharts.browse("FEmusic_moods_and_genres_category", country, mood.params))
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

    /** The "Moods & moments" grid; the genres grid that follows it is left out. */
    internal fun parseMoods(json: String): List<Mood> {
        val grids = mutableListOf<Pair<String, List<Mood>>>()
        YtmCharts.walk(Json.parseToJsonElement(json)) { o ->
            val g = o["gridRenderer"] as? JsonObject ?: return@walk
            val title = YtmCharts.runsText(g.path("header", "gridHeaderRenderer", "title"))
            val moods = (g["items"] as? JsonArray).orEmpty().mapNotNull { item ->
                val b = (item as? JsonObject)?.get("musicNavigationButtonRenderer") as? JsonObject ?: return@mapNotNull null
                val params = b.path("clickCommand", "browseEndpoint", "params")?.str() ?: return@mapNotNull null
                Mood(YtmCharts.runsText(b["buttonText"]), params)
            }
            grids += title to moods
        }
        return (grids.firstOrNull { "mood" in it.first.lowercase() } ?: grids.firstOrNull())?.second.orEmpty()
    }

    internal fun parseCategory(json: String): List<Shelf> {
        val shelves = mutableListOf<Shelf>()
        YtmCharts.walk(Json.parseToJsonElement(json)) { o ->
            val c = o["musicCarouselShelfRenderer"] as? JsonObject ?: return@walk
            val title = YtmCharts.runsText(c.path("header", "musicCarouselShelfBasicHeaderRenderer", "title"))
            val lists = (c["contents"] as? JsonArray).orEmpty().mapNotNull { item ->
                val r = (item as? JsonObject)?.get("musicTwoRowItemRenderer") as? JsonObject ?: return@mapNotNull null
                r.path("navigationEndpoint", "browseEndpoint", "browseId")?.str()
                    ?.takeIf { it.startsWith("VL") }?.removePrefix("VL")
            }
            if (title.isNotBlank() && lists.isNotEmpty()) shelves += Shelf(title, lists)
        }
        return shelves
    }
}
