package com.example.juke.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.juke.models.SpotifyTrack
import com.example.juke.ui.components.FlatTrackRow
import com.example.juke.ui.components.GlassButton
import com.example.juke.ui.components.GlassTopAppBar
import com.example.juke.ui.components.MediaDetailSkeleton
import com.example.juke.ui.components.SwipeToAddNextContainer
import com.example.juke.viewmodels.MusicViewModel
import com.example.juke.viewmodels.PlaylistDetailViewModel
import kotlinx.coroutines.launch

@Composable
fun PlaylistDetailScreen(
    playlistDetailViewModel: PlaylistDetailViewModel = viewModel(),
    musicViewModel: MusicViewModel = viewModel(),
    onNavigateBack: () -> Unit,
    bottomPadding: Dp = 0.dp
) {
    val uiState by playlistDetailViewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val playlist = uiState.playlist

    Scaffold(
        topBar = {
            GlassTopAppBar(
                title = {
                    Text(
                        playlist?.name ?: "Playlist",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        },
        containerColor = androidx.compose.ui.graphics.Color.Transparent
    ) { paddingValues ->
        if (uiState.isLoading || playlist == null) {
            MediaDetailSkeleton(
                modifier = Modifier.padding(paddingValues),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    top = 16.dp,
                    end = 20.dp,
                    bottom = 16.dp + bottomPadding
                )
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    top = 16.dp,
                    end = 20.dp,
                    bottom = 16.dp + bottomPadding
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Playlist Header
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AsyncImage(
                            model = playlist.images.firstOrNull()?.url ?: "",
                            contentDescription = playlist.name,
                            modifier = Modifier
                                .size(200.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = playlist.name,
                            style = MaterialTheme.typography.headlineMedium
                        )

                        playlist.owner.displayName?.let {
                            Text(
                                text = "By $it",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = "${playlist.tracks?.total ?: 0} tracks",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        playlist.description?.let { desc ->
                            if (desc.isNotBlank()) {
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }

                        // Save Playlist Offline Button
                        if (!uiState.isImportingPlaylist) {
                            Spacer(modifier = Modifier.height(16.dp))
                            GlassButton(
                                onClick = { playlistDetailViewModel.importPlaylistOffline() },
                                modifier = Modifier.fillMaxWidth(0.8f)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Save Playlist Offline")
                            }
                        }

                        // Import Progress Indicator
                        if (uiState.isImportingPlaylist) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth(0.8f)
                            ) {
                                Text(
                                    "Saving playlist… keeps going if you close the app",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = {
                                        if (uiState.importTotal > 0) {
                                            uiState.importProgress.toFloat() / uiState.importTotal.toFloat()
                                        } else 0f
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "${uiState.importProgress} / ${uiState.importTotal} tracks",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Tracks Section
                if (uiState.tracks.isNotEmpty()) {
                    item {
                        Text(
                            text = "Tracks",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }

                    items(uiState.tracks) { track ->
                        SwipeToAddNextContainer(
                            onAddNext = {
                                scope.launch {
                                    musicViewModel.queueSpotifyTrackNext(track)
                                }
                            }
                        ) {
                            TrackItem(
                                track = track,
                                onClick = {
                                    scope.launch {
                                        // Queue this track and all tracks below it from the playlist
                                        musicViewModel.setQueueFromSpotifyTracks(
                                            uiState.tracks,
                                            uiState.tracks.indexOf(track)
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackItem(
    track: SpotifyTrack,
    onClick: () -> Unit
) {
    FlatTrackRow(
        imageUrl = track.album.images.lastOrNull()?.url,
        title = track.name,
        subtitle = "${track.artists.joinToString(", ") { it.name }} · ${track.album.name}",
        duration = formatDuration(track.durationMs),
        onClick = onClick
    )
}

private fun formatDuration(durationMs: Int): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
