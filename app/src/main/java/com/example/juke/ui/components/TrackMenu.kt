package com.example.juke.ui.components

import android.widget.Toast
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.composed
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import com.example.juke.models.SpotifyTrack
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
    return listOf(
        MenuAction("Play") { play?.invoke() ?: musicViewModel.playInstant(SpotifyApi.spotifyTrackToSong(track)) },
        MenuAction("Play next") { musicViewModel.queueSpotifyTrack(track, next = true); say("${track.name} will play next") },
        MenuAction("Add to queue") { musicViewModel.queueSpotifyTrack(track, next = false); say("${track.name} added to queue end") },
        MenuAction("Add to playlist") { com.example.juke.ui.components.PlaylistAdder.open(track) }
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
    ) + shareAction("track", track.spotifyId) + mediaActions(track.albumSpotifyId, listOfNotNull(track.artistSpotifyIds?.firstOrNull()?.let { track.artist.substringBefore(", ") to it }))
}
