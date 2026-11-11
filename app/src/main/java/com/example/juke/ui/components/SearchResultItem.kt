package com.example.juke.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import com.example.juke.ui.theme.pressScale
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.example.juke.ui.icons.JukeIcons
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.juke.models.SpotifyTrack
import com.example.juke.utils.rememberJukeHaptics

@Composable
fun SearchResultItemM3(
    track: SpotifyTrack,
    isDownloading: Boolean,
    onPlayNext: () -> Unit,
    onClick: () -> Unit
) {
    val haptic = rememberJukeHaptics()
    val context = LocalContext.current
    val open = LocalOpenMedia.current
    var menuOpen by remember { mutableStateOf(false) }
    val share = LocalShare.current
    // Prefer the smallest image that is still at least 64px wide so the 48dp slot
    // doesn't upscale a tiny thumbnail. Fall back to the last entry if none qualify.
    val thumbnailUrl = track.album.images
        .filter { (it.width ?: 0) >= 64 }
        .minByOrNull { it.width ?: Int.MAX_VALUE }?.url
        ?: track.album.images.lastOrNull()?.url

    val rowSource = remember { MutableInteractionSource() }
    if (menuOpen) TrackActionSheet(
        title = track.name, subtitle = track.artists.joinToString(", ") { it.name }, artUrl = thumbnailUrl,
        onDismiss = { menuOpen = false },
        actions = listOfNotNull(
            SheetAction("Play now", JukeIcons.Play) { menuOpen = false; onClick() },
            SheetAction("Play next", JukeIcons.Queue) { menuOpen = false; onPlayNext() },
            SheetAction("Add to playlist", JukeIcons.PlaylistAdd) { menuOpen = false; PlaylistAdder.open(track) },
            track.id?.let { id -> SheetAction("Share", JukeIcons.Share) { menuOpen = false; share.link(spotifyLink("track", id)) } },
            track.album.id?.let { id -> SheetAction("Go to album", JukeIcons.Album) { menuOpen = false; open.album(id) } },
        ) + track.artists.take(3).mapNotNull { a -> // ponytail: first 3 credited artists
            a.id?.let { id -> SheetAction("Go to ${a.name}", JukeIcons.MusicNote) { menuOpen = false; open.artist(id) } }
        }
    )
    ListItem(
        modifier = Modifier
            .focusRing()
            .onMenuKey { if (!isDownloading) menuOpen = true }
            .pressScale(rowSource)
            .onRightClick { if (!isDownloading) menuOpen = true }
            .combinedClickable(
                interactionSource = rowSource,
                indication = androidx.compose.foundation.LocalIndication.current,
                enabled = !isDownloading,
                onLongClick = { haptic.heavyClick(); menuOpen = true }
            ) {
                haptic.click()
                onClick()
            }
            .padding(horizontal = 8.dp),
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = MaterialTheme.colorScheme.onBackground,
            supportingColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        ),
        headlineContent = {
            Text(
                text = track.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge
            )
        },
        supportingContent = {
            Text(
                text = track.artists.joinToString(", ") { it.name },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                // Explicit cache keys let Coil serve from memory/disk immediately when
                // the list re-renders (e.g. after scrolling back), avoiding late loads.
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(thumbnailUrl)
                        .memoryCacheKey(thumbnailUrl)
                        .diskCacheKey(thumbnailUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "${track.name} Album Art",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
                if (isDownloading) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    }
                }
            }
        },
        trailingContent = {
            if (isDownloading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(JukeIcons.More, "Options for ${track.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    )
}

