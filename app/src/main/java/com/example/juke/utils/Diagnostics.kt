package com.example.juke.utils

import android.content.Context
import android.os.Build
import com.example.juke.BuildConfig
import com.example.juke.database.MusicDatabase
import com.example.juke.services.SourceMemory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Library numbers shown in Power tools → Diagnostics. */
data class ListeningStats(
    val tracks: Int,
    val totalPlays: Int,
    val favourites: Int,
    val topArtists: List<Pair<String, Int>>
)

private val exportedPrefs = listOf("music_settings_prefs", "ui_prefs", "power_prefs")

object Diagnostics {

    suspend fun stats(context: Context): ListeningStats = withContext(Dispatchers.IO) {
        val tracks = MusicDatabase.getDatabase(context).trackDao().getAllTracks()
        val top = tracks.filter { it.playCount > 0 }
            .groupBy { it.artist.split(',').first().trim() }
            .mapValues { (_, v) -> v.sumOf { it.playCount } }
            .entries.sortedByDescending { it.value }.take(5).map { it.key to it.value }
        ListeningStats(
            tracks = tracks.size,
            totalPlays = tracks.sumOf { it.playCount },
            favourites = tracks.count { it.isFavourite },
            topArtists = top
        )
    }

    /** Plain-text report for bug reports: versions, settings, library numbers and recent app log lines. */
    suspend fun report(context: Context): String = withContext(Dispatchers.IO) {
        val stats = stats(context)
        val memory = SourceMemory(context)
        buildString {
            appendLine("JUKE diagnostics")
            appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine()
            appendLine("Library: ${stats.tracks} tracks, ${stats.totalPlays} plays, ${stats.favourites} favourites")
            appendLine("Top artists: " + stats.topArtists.joinToString { "${it.first} (${it.second})" })
            appendLine("Preferred source: ${memory.preferred ?: "Auto"}; songs with a rejected source: ${memory.rejectedSongCount()}")
            appendLine()
            appendLine("Settings:")
            exportedPrefs.forEach { name ->
                context.getSharedPreferences(name, Context.MODE_PRIVATE).all.forEach { (k, v) -> appendLine("  $name.$k = $v") }
            }
            appendLine()
            appendLine("Recent log lines:")
            appendLine(recentLog())
        }.take(200_000)
    }

    /** Settings, playlists and favourites as JSON. Contains no credentials. */
    suspend fun backupJson(context: Context): String = withContext(Dispatchers.IO) {
        val db = MusicDatabase.getDatabase(context)
        val root = JSONObject()
        root.put("app", "JUKE")
        root.put("version", BuildConfig.VERSION_NAME)
        root.put("exportedAt", System.currentTimeMillis())

        val settings = JSONObject()
        exportedPrefs.forEach { name ->
            val o = JSONObject()
            context.getSharedPreferences(name, Context.MODE_PRIVATE).all.forEach { (k, v) -> o.put(k, v) }
            settings.put(name, o)
        }
        root.put("settings", settings)

        fun trackJson(title: String, artist: String, spotifyId: String?) =
            JSONObject().put("title", title).put("artist", artist).put("spotifyId", spotifyId ?: JSONObject.NULL)

        val playlists = JSONArray()
        db.playlistDao().getAllPlaylists().first().forEach { p ->
            val tracks = JSONArray()
            db.playlistDao().getPlaylistTracks(p.id).forEach { tracks.put(trackJson(it.title, it.artist, it.spotifyId)) }
            playlists.put(JSONObject().put("name", p.name).put("tracks", tracks))
        }
        root.put("playlists", playlists)

        val favourites = JSONArray()
        db.trackDao().getFavourites().forEach { favourites.put(trackJson(it.title, it.artist, it.spotifyId)) }
        root.put("favourites", favourites)
        root.toString(2)
    }

    private fun recentLog(): String = try {
        val process = Runtime.getRuntime().exec(
            arrayOf("logcat", "-d", "-t", "300", "--pid=${android.os.Process.myPid()}")
        )
        process.inputStream.bufferedReader().use { it.readText() }
    } catch (e: Exception) {
        "(log unavailable: ${e.message})"
    }
}
