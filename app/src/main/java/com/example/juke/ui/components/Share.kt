package com.example.juke.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.example.juke.ui.icons.JukeIcons

/** Sends a link out: the system share sheet on Android, the clipboard on desktop. Each root provides its own. */
class Share(val link: (String) -> Unit)

val LocalShare = staticCompositionLocalOf { Share {} }

/** Public Spotify link for a [kind] of "track", "album", "artist" or "playlist". */
fun spotifyLink(kind: String, id: String) = "https://open.spotify.com/$kind/$id"

/** The "Share" menu entry; none when the item has no Spotify id to link to. */
@Composable
fun shareAction(kind: String, id: String?): List<MenuAction> {
    val share = LocalShare.current
    return if (id == null) emptyList() else listOf(MenuAction("Share") { share.link(spotifyLink(kind, id)) })
}

/** The Share button for a top app bar; nothing when there is no id to link to. */
@Composable
fun ShareButton(kind: String, id: String?) {
    val share = LocalShare.current
    if (id != null) IconButton(onClick = { share.link(spotifyLink(kind, id)) }) { Icon(JukeIcons.Share, "Share") }
}
