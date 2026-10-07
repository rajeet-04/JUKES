package com.example.juke.ui.components

import com.example.juke.ui.icons.JukeIcons

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.juke.models.Track
import com.example.juke.ui.theme.GlassLevel
import com.example.juke.ui.theme.GlassShapes
import com.example.juke.ui.theme.glassFloat
import com.example.juke.ui.theme.glassPane
import com.example.juke.utils.rememberJukeHaptics
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

@Composable
fun HeroTrackCard(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = rememberJukeHaptics()

    val hazeState = remember { HazeState() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.5f)
            .glassPane(GlassShapes.Card, GlassLevel.Thick)
            .clickable(onClickLabel = "Play ${track.title}", role = Role.Button) {
                haptic.click()
                onClick()
            }
    ) {
        Box(modifier = Modifier.fillMaxSize().hazeSource(hazeState)) {
            if (track.thumbnailUri != null) {
                AsyncImage(
                    model = track.thumbnailUri,
                    contentDescription = track.title,
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
                        JukeIcons.Play,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Caption lens: the artwork blurs through the plate, a dark layer keeps the text above AA.
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
                .fillMaxWidth()
                .glassFloat(GlassShapes.Control, GlassLevel.Regular, source = hazeState)
                .background(Color.Black.copy(alpha = 0.32f))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.86f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (track.playCount > 0) {
            Text(
                text = "${track.playCount} plays",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .glassFloat(GlassShapes.Pill, GlassLevel.Thin, source = hazeState)
                    .background(Color.Black.copy(alpha = 0.32f))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
    }
}
