package com.example.juke.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.juke.database.PlaylistEntity
import com.example.juke.models.Track

@Composable
fun AddToPlaylistDialog(
    playlists: List<PlaylistEntity>,
    tracks: List<Track>,
    trackPlaylists: List<PlaylistEntity>, // trackPlaylists might be ambiguous for multiple tracks. 
    // If multiple, we might pass emptyList or intersection? 
    // Let's make it optional or just ignore usage if tracks.size > 1
    onDismiss: () -> Unit,
    onAddToPlaylist: (PlaylistEntity) -> Unit,
    onRemoveFromPlaylist: (PlaylistEntity) -> Unit,
    onCreatePlaylist: () -> Unit,
    onRemoveFromCurrentPlaylist: ((PlaylistEntity) -> Unit)? = null,
    currentPlaylist: PlaylistEntity? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (tracks.size > 1) "Add ${tracks.size} tracks to Playlist" else "Add to Playlist") },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // If we are in a playlist context, we might want to show "Remove from current" prominently, 
                // but per user request, we are handling that with the minus icon.
                // However, if the modal IS opened (though unlikely in playlist view due to minus icon), we keep logic generic.

                item {
                    ListItem(
                        headlineContent = { Text("Create new playlist") },
                        leadingContent = {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Create new playlist",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onCreatePlaylist() }
                    )
                }

                items(playlists) { playlist ->
                    // Logic for single track
                    val isAlreadyAdded = if (tracks.size == 1) trackPlaylists.any { it.id == playlist.id } else false
                    
                    ListItem(
                        headlineContent = { Text(playlist.name) },
                        supportingContent = {
                            if (isAlreadyAdded && tracks.size == 1) Text(
                                "Already added",
                                color = MaterialTheme.colorScheme.primary
                            )
                            else Text("${playlist.trackCount} tracks")
                        },
                        leadingContent = {
                            Icon(
                                Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = null
                            )
                        },
                        trailingContent = {
                            if (isAlreadyAdded && tracks.size == 1) {
                                IconButton(onClick = { onRemoveFromPlaylist(playlist) }) {
                                    Icon(
                                        Icons.Default.RemoveCircle,
                                        contentDescription = "Remove",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            } else {
                                IconButton(onClick = { onAddToPlaylist(playlist) }) {
                                    Icon(
                                        Icons.Default.AddCircle,
                                        contentDescription = "Add",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        },
                        modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    )
                }

                if (playlists.isEmpty()) {
                    item {
                        Text(
                            "No playlists available",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
