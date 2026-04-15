package com.example.juke.ui.components

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.juke.models.SpotifyAlbum
import com.example.juke.utils.rememberJukeHaptics

@Composable
fun AlbumCard(
    album: SpotifyAlbum,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = rememberJukeHaptics()
    Card(
        modifier = modifier
            .width(140.dp)
            .clickable {
                haptic.click()
                onClick()
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Album Cover
            AsyncImage(
                model = album.images.firstOrNull()?.url ?: "",
                contentDescription = album.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                contentScale = ContentScale.Crop
            )

            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                // Album Name
                Text(
                    text = album.name,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .basicMarquee(),
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Artist Name and Track Count
                Text(
                    text = "${album.artists.firstOrNull()?.name ?: "Unknown Artist"} • ${album.totalTracks} tracks",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .basicMarquee(),
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
            }
        }
    }
}