package com.example.juke.ui.icons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.juke.ui.theme.JUKETheme

@Preview(name = "JUKE glyphs · light", showBackground = true)
@Composable
private fun LightIconPreview() = IconSheet(dark = false)

@Preview(name = "JUKE glyphs · dark", showBackground = true)
@Composable
private fun DarkIconPreview() = IconSheet(dark = true)

@Preview(name = "JUKE glyphs · RTL", showBackground = true)
@Composable
private fun RtlIconPreview() {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        IconSheet(dark = false)
    }
}

@Composable
private fun IconSheet(dark: Boolean) {
    val icons = listOf(
        "Home" to JukeIcons.Home, "Selected" to JukeIcons.HomeSelected,
        "Search" to JukeIcons.Search, "Library" to JukeIcons.Library,
        "Selected" to JukeIcons.LibrarySelected, "Like" to JukeIcons.Heart,
        "Liked" to JukeIcons.HeartSelected, "Play" to JukeIcons.Play,
        "Pause" to JukeIcons.Pause, "Previous" to JukeIcons.Previous,
        "Next" to JukeIcons.Next, "Shuffle" to JukeIcons.Shuffle,
        "Repeat" to JukeIcons.Repeat, "Repeat 1" to JukeIcons.RepeatOne,
        "Queue" to JukeIcons.Queue, "Playlist" to JukeIcons.PlaylistAdd,
        "Lyrics" to JukeIcons.Lyrics, "Download" to JukeIcons.Download,
        "Back" to JukeIcons.Back, "Forward" to JukeIcons.Forward,
        "Down" to JukeIcons.ChevronDown, "Up" to JukeIcons.ChevronUp,
        "Close" to JukeIcons.Close, "Add" to JukeIcons.Add,
        "Add circle" to JukeIcons.AddCircle, "Check" to JukeIcons.Check,
        "Delete" to JukeIcons.Delete, "More" to JukeIcons.More,
        "More" to JukeIcons.MoreHorizontal, "Equalizer" to JukeIcons.Equalizer,
        "Music" to JukeIcons.MusicNote, "Share" to JukeIcons.Share,
        "Refresh" to JukeIcons.Refresh, "Album" to JukeIcons.Album,
        "List" to JukeIcons.List
    )
    JUKETheme(darkTheme = dark) {
        Surface {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                icons.chunked(5).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { (label, vector) ->
                            Column(Modifier.size(64.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(vector, contentDescription = label, modifier = Modifier.size(24.dp))
                                Text(label, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
