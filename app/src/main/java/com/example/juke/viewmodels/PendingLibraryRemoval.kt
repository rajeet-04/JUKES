package com.example.juke.viewmodels

import com.example.juke.database.PlaylistEntity
import com.example.juke.models.Track

/** Files and memberships stay intact until the Undo window expires. */
data class PendingLibraryRemoval(
    val tracks: List<Track>,
    val playlist: PlaylistEntity? = null,
    val startedAt: Long = System.currentTimeMillis(),
) {
    val trackIds: Set<String> = tracks.map { it.uuid }.toSet()

    fun hides(trackId: String, visiblePlaylistId: String?): Boolean =
        trackId in trackIds && (playlist == null || playlist.id == visiblePlaylistId)

    fun merge(other: PendingLibraryRemoval): PendingLibraryRemoval {
        require(playlist?.id == other.playlist?.id)
        return copy(tracks = (tracks + other.tracks).distinctBy { it.uuid }, startedAt = other.startedAt)
    }
}
