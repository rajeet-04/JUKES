package com.example.juke.viewmodels

import com.example.juke.models.SpotifyPlaylist
import com.example.juke.models.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The list the queue was started from. The player shows "Playing from <name>" and opens the list on tap,
 * and the list's own Play button pauses and resumes instead of starting over. It only counts while the
 * song playing belongs to the list ([keys]), so a song picked elsewhere quietly drops it. It is saved
 * with the queue, so it survives a restart like the queue does.
 */
@Serializable
data class PlayContext(
    val kind: Kind,
    val id: String,
    val name: String,
    /** uuids and Spotify ids of the list's songs. */
    val keys: Set<String>,
    /** A Spotify playlist is reopened from this, since there is no load-by-id for it. */
    val playlist: SpotifyPlaylist? = null
) {
    enum class Kind(val label: String) {
        LIBRARY_PLAYLIST("Playlist"),
        PLAYLIST("Playlist"),
        ALBUM("Album"),
        ARTIST("Artist"),
        LIBRARY("Library")
    }

    fun owns(track: Track?): Boolean =
        track != null && (track.uuid in keys || track.spotifyId?.let { it in keys } == true)

    fun isList(kind: Kind, id: String?): Boolean = this.kind == kind && this.id == id

    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** The saved form back, or null when there is none or it no longer reads. */
        fun decode(saved: String?): PlayContext? =
            saved?.let { runCatching { json.decodeFromString(serializer(), it) }.getOrNull() }

        fun keysOf(tracks: List<Track>): Set<String> =
            tracks.flatMapTo(HashSet()) { listOfNotNull(it.uuid, it.spotifyId) }

        /** The context of the queue now playing, kept in memory so the player can read it without decoding the saved copy. */
        @Volatile var latest: PlayContext? = null

        /**
         * Where a listen came from, for analytics: jam, radio (the queue's own picks), the kind of list it was
         * started from, or direct for a song picked on its own (search, home, a single tap).
         */
        fun sourceOf(from: PlayContext?, track: Track, radio: Boolean, jam: Boolean): String = when {
            jam -> "jam"
            radio -> "radio"
            else -> when (from?.takeIf { it.owns(track) }?.kind) {
                Kind.LIBRARY_PLAYLIST, Kind.PLAYLIST -> "playlist"
                Kind.ALBUM -> "album"
                Kind.ARTIST -> "artist"
                Kind.LIBRARY -> "library"
                null -> "direct"
            }
        }

        /** Asks the Library tab to open one of its playlists (set by the player, taken by the Library screen). */
        val openLibraryPlaylist = MutableStateFlow<String?>(null)
    }
}
