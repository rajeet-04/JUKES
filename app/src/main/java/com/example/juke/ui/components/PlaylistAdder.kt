package com.example.juke.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.juke.database.MusicDatabase
import com.example.juke.database.PlaylistEntity
import com.example.juke.database.toEntity
import com.example.juke.database.toTrack
import com.example.juke.jam.Connect
import com.example.juke.models.SpotifyTrack
import com.example.juke.models.Track
import com.example.juke.viewmodels.LibraryViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * "Add to playlist" from any song menu (Search, Home), without playing the song first.
 * Menus call [open]; one [PlaylistAdderHost] at the app root shows the picker.
 * A song that isn't saved yet is added to the library as a stream entry (fetched when it plays) so a playlist can hold it.
 */
object PlaylistAdder {
    private val request = MutableStateFlow<(suspend (Context) -> Track?)?>(null)

    fun open(track: Track) { request.value = { track } }

    fun open(song: SpotifyTrack) {
        request.value = { c -> saved(c, song) }
    }

    /** The library's row for [song], adding a stream entry if it has none. */
    private suspend fun saved(c: Context, song: SpotifyTrack): Track? {
        val id = song.id ?: return null
        val dao = MusicDatabase.getDatabase(c).trackDao()
        (dao.getAllTracks() + dao.getStreamTracks()).firstOrNull { it.spotifyId == id }?.let { return it.toTrack() }
        val thumb = song.album.images.maxByOrNull { it.width ?: 0 }?.url
        val track = Connect.pendingStream(c, java.util.UUID.randomUUID().toString(), song.name, song.artists.joinToString(", ") { it.name },
            song.durationMs / 1000, thumb, id, null).copy(albumSpotifyId = song.album.id, artistSpotifyIds = song.artists.mapNotNull { it.id })
        dao.insertTrack(track.toEntity())
        return track
    }

    @Composable
    fun PlaylistAdderHost(libraryViewModel: LibraryViewModel) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val pending by request.collectAsStateWithLifecycle()
        var track by remember { mutableStateOf<Track?>(null) }
        var inPlaylists by remember { mutableStateOf<List<PlaylistEntity>>(emptyList()) }
        var creating by remember { mutableStateOf(false) }
        val libraryUi by libraryViewModel.uiState.collectAsStateWithLifecycle()

        LaunchedEffect(pending) {
            val load = pending ?: return@LaunchedEffect
            track = load(context)
            request.value = null
            track?.let { inPlaylists = libraryViewModel.getPlaylistsForTrack(it.uuid) }
                ?: android.widget.Toast.makeText(context, "Couldn't add this song", android.widget.Toast.LENGTH_SHORT).show()
        }

        val t = track ?: return
        AddToPlaylistDialog(
            playlists = libraryUi.playlists,
            tracks = listOf(t),
            trackPlaylists = inPlaylists,
            onDismiss = { track = null; inPlaylists = emptyList() },
            onAddToPlaylist = { p -> scope.launch { libraryViewModel.addToPlaylist(p, t); inPlaylists = libraryViewModel.getPlaylistsForTrack(t.uuid) } },
            onRemoveFromPlaylist = { p -> scope.launch { libraryViewModel.removeFromPlaylist(p, t); inPlaylists = libraryViewModel.getPlaylistsForTrack(t.uuid) } },
            onCreatePlaylist = { creating = true }
        )
        if (creating) CreatePlaylistDialog(onDismiss = { creating = false }, onCreate = { name -> libraryViewModel.createPlaylist(name); creating = false })
    }
}
