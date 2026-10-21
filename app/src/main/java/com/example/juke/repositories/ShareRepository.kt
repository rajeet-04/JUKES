package com.example.juke.repositories

import android.util.Log
import com.example.juke.BuildConfig
import com.example.juke.database.PlaylistDao
import com.example.juke.database.PlaylistEntity
import com.example.juke.models.SpotifyAlbum
import com.example.juke.models.SpotifyArtist
import com.example.juke.models.SpotifyExternalUrls
import com.example.juke.models.SpotifyImage
import com.example.juke.models.SpotifyTrack
import com.example.juke.network.ApiClient
import com.example.juke.network.SpotifyApi
import kotlinx.coroutines.CancellationException
import com.example.juke.services.PlaylistImportManager
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import java.util.UUID

/** One song in a shared playlist: just enough to find it again, no local files or uuids. */
@Serializable
data class SharedTrack(
    val title: String,
    val artist: String,
    val durationSec: Int = 0,
    val spotifyId: String? = null,
    val albumSpotifyId: String? = null,
    val thumb: String? = null
)

@Serializable
data class SharedPlaylist(
    // No defaults: the JSON encoder omits default values, and the Worker rejects a share without these.
    val v: Int,
    val kind: String,
    val name: String,
    val description: String? = null,
    val tracks: List<SharedTrack>
)

@Serializable
private data class ShareCreated(val id: String)

/**
 * Snapshot links: a playlist is uploaded once as JSON and anyone with the link can save a copy.
 * No accounts; the unguessable id is the access.
 */
class ShareRepository(private val playlistDao: PlaylistDao) {

    /** Uploads [playlistId] and returns the link, or null if it has nothing shareable. */
    suspend fun createPlaylistLink(playlistId: String): String? {
        val playlist = playlistDao.getPlaylist(playlistId) ?: return null
        // Songs without a Spotify id can't be re-found on another device, so they are left out.
        val tracks = playlistDao.getPlaylistTracks(playlistId).mapNotNull { t ->
            // thumbnailUri is a path on this phone for downloaded songs; only a real URL means anything elsewhere.
            t.spotifyId?.let { SharedTrack(t.title, t.artist, t.durationSec, it, t.albumSpotifyId, t.thumbnailUri?.takeIf { u -> u.startsWith("http") }) }
        }
        if (tracks.isEmpty()) return null
        val response = ApiClient.httpClient.post("$BASE/share") {
            header("X-App-Secret", BuildConfig.SPOTIFY_TOKEN_KEY)
            setBody(SharedPlaylist(v = 1, kind = "playlist", name = playlist.name, description = playlist.description, tracks = tracks))
        }
        if (!response.status.isSuccess()) error("Share failed (${response.status.value})")
        return "$BASE/p/${response.body<ShareCreated>().id}"
    }

    suspend fun fetchPlaylist(id: String): SharedPlaylist? {
        val response = ApiClient.httpClient.get("$BASE/share/$id")
        return if (response.status.isSuccess()) response.body<SharedPlaylist>() else null
    }

    /** Saves a copy to the library; the songs download in the background like a Spotify import. */
    suspend fun savePlaylist(shared: SharedPlaylist, importManager: PlaylistImportManager): String {
        val entity = PlaylistEntity(id = UUID.randomUUID().toString(), name = shared.name, description = shared.description)
        // Look the songs up on Spotify for the real artwork, album and artists; the snapshot only has titles and ids.
        val full = HashMap<String, SpotifyTrack>()
        shared.tracks.mapNotNull { it.spotifyId }.distinct().chunked(50).forEach { ids ->
            try {
                SpotifyApi.getTracks(ids).forEach { t -> t.id?.let { full[it] = t } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("ShareRepository", "Spotify lookup failed, importing without artwork: ${e.message}")
            }
        }
        importManager.enqueue(entity, shared.tracks.mapNotNull { it.spotifyId?.let { id -> full[id] } ?: it.toSpotifyTrack() })
        return entity.id
    }

    private fun SharedTrack.toSpotifyTrack(): SpotifyTrack? {
        val id = spotifyId ?: return null
        val noUrls = SpotifyExternalUrls(spotify = "https://open.spotify.com/track/$id")
        return SpotifyTrack(
            album = SpotifyAlbum(
                artists = emptyList(),
                externalUrls = SpotifyExternalUrls(),
                id = albumSpotifyId,
                images = listOfNotNull(thumb?.let { SpotifyImage(null, null, it) }),
                name = ""
            ),
            artists = artist.split(", ").map { SpotifyArtist(externalUrls = SpotifyExternalUrls(), name = it) },
            durationMs = durationSec * 1000,
            explicit = false,
            externalUrls = noUrls,
            id = id,
            name = title,
            uri = "spotify:track:$id"
        )
    }

    companion object {
        private val BASE = BuildConfig.SHARE_BASE_URL.trimEnd('/')

        /** The id from `https://<host>/p/<id>` or `juke://p/<id>`, else null. */
        fun parsePlaylistId(link: String): String? =
            LINK.find(link)?.groupValues?.get(1)

        private val LINK = Regex("""(?:${Regex.escape(BuildConfig.SHARE_BASE_URL.substringAfter("://").trimEnd('/'))}|juke:/)/p/([0-9A-Za-z]{10})\b""")
    }
}
