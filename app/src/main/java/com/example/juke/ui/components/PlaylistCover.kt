package com.example.juke.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.juke.database.MusicDatabase
import com.example.juke.database.PlaylistEntity
import com.example.juke.ui.icons.JukeIcons
import kotlinx.coroutines.flow.map

/**
 * Playlist artwork. A playlist with its own cover shows it; otherwise the cover is made from its
 * songs: one song fills it, two split it, three are one large and two stacked, four make a 2x2
 * mosaic. An empty playlist gets a tinted tile with a note instead of a blank square. Updates as
 * songs are added (e.g. while a playlist imports).
 */
@Composable
fun PlaylistCover(
    playlist: PlaylistEntity,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
) {
    val own = playlist.thumbnailUri?.takeIf { it.isNotBlank() }
    Box(modifier.clip(shape)) {
        if (own != null) {
            AsyncImage(own, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            val context = LocalContext.current
            val artwork by remember(playlist.id) {
                MusicDatabase.getDatabase(context).playlistDao().getPlaylistArtwork(playlist.id)
                    .map { it.distinct().take(4) } // songs from one album share art: show different art
            }.collectAsStateWithLifecycle(emptyList())
            ArtworkMosaic(artwork, seed = playlist.name)
        }
    }
}

@Composable
private fun ArtworkMosaic(art: List<String>, seed: String) {
    val gap = 1.dp
    @Composable
    fun Tile(uri: String, modifier: Modifier) =
        AsyncImage(uri, null, modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentScale = ContentScale.Crop)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            // A faint sheen from the top-left ties the tiles into one cover.
            .drawWithContent {
                drawContent()
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.10f), Color.Transparent, Color.Black.copy(alpha = 0.18f))
                    )
                )
            }
    ) {
        when (art.size) {
            0 -> EmptyCover(seed)
            1 -> Tile(art[0], Modifier.fillMaxSize())
            2 -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                Tile(art[0], Modifier.weight(1f).fillMaxHeight())
                Tile(art[1], Modifier.weight(1f).fillMaxHeight())
            }
            3 -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                Tile(art[0], Modifier.weight(1f).fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                    Tile(art[1], Modifier.weight(1f).fillMaxWidth())
                    Tile(art[2], Modifier.weight(1f).fillMaxWidth())
                }
            }
            else -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    Tile(art[0], Modifier.weight(1f).fillMaxHeight())
                    Tile(art[1], Modifier.weight(1f).fillMaxHeight())
                }
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    Tile(art[2], Modifier.weight(1f).fillMaxHeight())
                    Tile(art[3], Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

/** No songs yet: a deep tile tinted by the playlist's name, with a note. */
@Composable
private fun EmptyCover(seed: String) {
    val hue = ((seed.hashCode() and 0x7fffffff) % 360).toFloat()
    val top = Color.hsv(hue, 0.55f, 0.42f)
    val bottom = Color.hsv((hue + 40f) % 360f, 0.65f, 0.18f)
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(top, bottom))),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            JukeIcons.MusicNote,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.fillMaxSize(0.42f)
        )
    }
}
