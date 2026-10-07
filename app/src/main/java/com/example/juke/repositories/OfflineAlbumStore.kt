package com.example.juke.repositories

import android.content.Context
import androidx.core.content.edit
import com.example.juke.models.SpotifyAlbum
import com.example.juke.models.SpotifySimplifiedTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class OfflineAlbum(val album: SpotifyAlbum, val tracks: List<SpotifySimplifiedTrack>)

/** Album metadata survives restarts; audio remains managed by the existing Room library. */
class OfflineAlbumStore private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("offline_albums", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val state = MutableStateFlow(
        prefs.all.values.mapNotNull { value ->
            runCatching { json.decodeFromString<OfflineAlbum>(value as String) }.getOrNull()
        }.sortedBy { it.album.name }
    )
    val albums = state.asStateFlow()

    fun find(id: String): OfflineAlbum? = state.value.find { it.album.id == id }

    @Synchronized
    fun save(album: SpotifyAlbum, tracks: List<SpotifySimplifiedTrack>) {
        val id = album.id ?: return
        if (tracks.isEmpty()) return
        val entry = OfflineAlbum(album, tracks)
        prefs.edit { putString(id, json.encodeToString(entry)) }
        state.value = (state.value.filterNot { it.album.id == id } + entry).sortedBy { it.album.name }
    }

    companion object {
        @Volatile private var instance: OfflineAlbumStore? = null
        fun get(context: Context): OfflineAlbumStore = instance ?: synchronized(this) {
            instance ?: OfflineAlbumStore(context).also { instance = it }
        }
    }
}
