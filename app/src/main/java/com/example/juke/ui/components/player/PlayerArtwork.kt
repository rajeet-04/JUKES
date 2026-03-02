package com.example.juke.ui.components.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.juke.models.Track
import com.example.juke.viewmodels.MusicViewModel

@Composable
fun PlayerArtwork(
    currentTrack: Track,
    currentPosition: Long,
    showLyrics: Boolean,
    musicViewModel: MusicViewModel,
    isTablet: Boolean,
    onToggleLyrics: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Use fillMaxHeight so the artwork is bounded by the parent Box's height constraint,
    // then use aspectRatio(1f) to ensure it stays square. This prevents overflow on small screens.
    val artworkModifier = modifier
        .fillMaxHeight(if (isTablet) 1f else 0.94f)
        .aspectRatio(1f)

    Box(
        modifier = artworkModifier
            .shadow(
                elevation = 24.dp,
                shape = RoundedCornerShape(24.dp),
                clip = false,
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            )
            .clip(RoundedCornerShape(24.dp))
            .clickable { onToggleLyrics() },
        contentAlignment = Alignment.Center
    ) {
        if (currentTrack.thumbnailUri != null) {
            AsyncImage(
                model = currentTrack.thumbnailUri,
                contentDescription = currentTrack.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (showLyrics) {
            if (currentTrack.syncedLyrics != null || currentTrack.plainLyrics != null) {
                LyricsOverlay(
                    currentTrack = currentTrack,
                    currentPosition = currentPosition,
                    musicViewModel = musicViewModel,
                    isTablet = isTablet,
                    isLandscape = false,
                    onDismiss = onToggleLyrics
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.75f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No lyrics available",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
