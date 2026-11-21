package com.example.juke.viewmodels

import com.example.juke.models.SpotifyPlaylist
import com.example.juke.models.Track
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The list the queue was started from. The player shows "Playing from <name>" and opens the list on tap,
 * and the list's own Play button pauses and resumes instead of starting over. It only counts while the
 * song playing belongs to the list ([keys]), so a song picked elsewhere quietly drops it.
 */
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

    companion object {
        fun keysOf(tracks: List<Track>): Set<String> =
            tracks.flatMapTo(HashSet()) { listOfNotNull(it.uuid, it.spotifyId) }

        /** Asks the Library tab to open one of its playlists (set by the player, taken by the Library screen). */
        val openLibraryPlaylist = MutableStateFlow<String?>(null)
    }
}
