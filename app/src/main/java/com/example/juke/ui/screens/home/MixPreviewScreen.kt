package com.example.juke.ui.screens.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.juke.home.FeedSong
import com.example.juke.ui.components.FeedSongMenu
import com.example.juke.ui.components.GlassPillButton
import com.example.juke.ui.components.GlassTopAppBar
import com.example.juke.ui.icons.JukeIcons
import com.example.juke.viewmodels.MusicViewModel

/** The mix last opened from Home (title and songs), read by [MixPreviewScreen]. */
object MixPreview {
    var title: String = ""
    var songs: List<FeedSong> = emptyList()
}

/** A Home mix or recommendation row opened as a list, so its songs can be seen before playing. */
@Composable
fun MixPreviewScreen(musicViewModel: MusicViewModel, onNavigateBack: () -> Unit, bottomPadding: Dp = 0.dp) {
    val songs = MixPreview.songs
    val loadingId by musicViewModel.recLoading.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            GlassTopAppBar(
                title = { Text(MixPreview.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(JukeIcons.Back, "Back") } },
            )
        },
        containerColor = Color.Transparent,
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 16.dp + bottomPadding),
        ) {
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text("${songs.size} songs", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    GlassPillButton(text = "Play", onClick = { songs.firstOrNull()?.let(musicViewModel::playRec) })
                }
            }
            items(songs, key = { it.videoId }) { song ->
                SongLine(song, loadingId == song.videoId, leading = null, { s, open, close -> FeedSongMenu(open, close, s, musicViewModel) }) {
                    musicViewModel.playRec(song)
                }
            }
        }
    }
}
