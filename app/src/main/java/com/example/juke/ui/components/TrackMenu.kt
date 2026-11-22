package com.example.juke.ui.components

import android.widget.Toast
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.composed
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import com.example.juke.home.FeedSong
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.juke.models.SpotifyTrack
import com.example.juke.models.findOfflineTrack
import com.example.juke.models.Track
import com.example.juke.network.SpotifyApi
import com.example.juke.viewmodels.MusicViewModel

/** A right (secondary) mouse click: the desktop's long press. Does nothing on touch. */
fun Modifier.onRightClick(onClick: () -> Unit): Modifier = composed {
    val current by rememberUpdatedState(onClick)
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val e = awaitPointerEvent(PointerEventPass.Initial)
                if (e.type == PointerEventType.Press && e.buttons.isSecondaryPressed) {
                    e.changes.forEach { it.consume() }
                    current()
                }
            }
        }
    }
}

/** One entry of a long-press menu. */
class MenuAction(val label: String, val run: () -> Unit)

/** Long-press menu shown next to the pressed card or row. */
@Composable
fun ActionMenu(expanded: Boolean, onDismiss: () -> Unit, actions: List<MenuAction>) {
    if (actions.isEmpty()) return
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        actions.forEach { action ->
            DropdownMenuItem(text = { Text(action.label) }, onClick = { onDismiss(); action.run() })
        }
    }
}

/** "Go to album" / "Go to <artist>" entries, only for ids the song actually carries. */
@Composable
fun mediaActions(albumId: String?, artists: List<Pair<String, String>>): List<MenuAction> {
    val open = com.example.juke.ui.components.LocalOpenMedia.current
    return listOfNotNull(albumId?.let { MenuAction("Go to album") { open.album(it) } }) +
        artists.map { (name, id) -> MenuAction("Go to $name") { open.artist(id) } }
}

/** Play now, play next, add to queue for a song that isn't in the library yet. */
@Composable
fun spotifyTrackActions(track: SpotifyTrack, musicViewModel: MusicViewModel, play: (() -> Unit)? = null): List<MenuAction> {
    val context = LocalContext.current
    fun say(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    val owned by musicViewModel.offlineTracks.collectAsStateWithLifecycle()
    return listOfNotNull(
        MenuAction("Play") { play?.invoke() ?: musicViewModel.playInstant(SpotifyApi.spotifyTrackToSong(track)) },
        MenuAction("Play next") { musicViewModel.queueSpotifyTrack(track, next = true); say("${track.name} will play next") },
        MenuAction("Add to queue") { musicViewModel.queueSpotifyTrack(track, next = false); say("${track.name} added to queue end") },
        MenuAction("Add to playlist") { com.example.juke.ui.components.PlaylistAdder.open(track) },
        // Already saved: nothing to download.
        if (track.findOfflineTrack(owned) == null) MenuAction("Download") { downloadSpotifyTrack(musicViewModel, track); say("Downloading ${track.name}") } else null
    ) + shareAction("track", track.id) + mediaActions(track.album.id, track.artists.take(3).mapNotNull { a -> a.id?.let { a.name to it } })
}

/** Play now, play next, add to queue for a song already in the library. */
@Composable
fun libraryTrackActions(track: Track, musicViewModel: MusicViewModel, play: () -> Unit): List<MenuAction> {
    val context = LocalContext.current
    fun say(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    return listOf(
        MenuAction("Play", play),
        MenuAction("Play next") { musicViewModel.addNext(track); say("${track.title} will play next") },
        MenuAction("Add to queue") { musicViewModel.addToQueue(listOf(track)); say("${track.title} added to queue end") },
        MenuAction("Add to playlist") { com.example.juke.ui.components.PlaylistAdder.open(track) }
    ) + listOfNotNull(
        // Only a streamed song has something to save; a downloaded one already plays offline.
        if (track.isStream) MenuAction("Download") { musicViewModel.promoteTrackToDownload(track); say("Downloading ${track.title}") } else null
    ) + shareAction("track", track.spotifyId) + mediaActions(track.albumSpotifyId, artistLinks(track.artist, track.artistSpotifyIds))
}

/** Download from any song menu (#34): the download queue skips songs that are already saved. */
fun downloadSpotifyTrack(musicViewModel: MusicViewModel, track: SpotifyTrack) =
    musicViewModel.addToDownloadQueue(SpotifyApi.spotifyTrackToSong(track))

/**
 * Pairs a library song's artist names (stored joined with ", ") with its Spotify artist ids, in
 * order. When the two don't line up, only the first artist is linked rather than guessing.
 */
fun artistLinks(names: String, ids: List<String>?): List<Pair<String, String>> {
    if (ids.isNullOrEmpty()) return emptyList()
    val split = names.split(", ").map { it.trim() }.filter { it.isNotEmpty() }
    if (split.size == ids.size) return split.zip(ids)
    return listOf((split.firstOrNull() ?: names) to ids.first())
}

/**
 * Long-press menu for a Home feed song. The song only has a YouTube id, so opening the menu matches
 * it on Spotify (once; the view model caches it) and then shows the same entries as any song,
 * with one "Go to" per artist.
 */
@Composable
fun FeedSongMenu(expanded: Boolean, onDismiss: () -> Unit, song: FeedSong, musicViewModel: MusicViewModel) {
    if (!expanded) return
    val match by produceState<Pair<Boolean, SpotifyTrack?>>(false to null, song.videoId) {
        value = true to musicViewModel.matchFeedSong(song)
    }
    val (done, track) = match
    val play = MenuAction("Play") { musicViewModel.playRec(song) }
    val actions = when {
        track != null -> spotifyTrackActions(track, musicViewModel, play = { musicViewModel.playRec(song) })
        !done -> listOf(play, MenuAction("Finding song…") {})
        // No match: the song leaves Home (and this menu with it); say why once.
        else -> emptyList()
    }
    if (done && track == null) {
        val context = LocalContext.current
        androidx.compose.runtime.LaunchedEffect(song.videoId) {
            Toast.makeText(context, "Couldn't find ${song.title}, so it's off Home now", Toast.LENGTH_SHORT).show()
            onDismiss()
        }
    }
    ActionMenu(true, onDismiss, actions)
}
