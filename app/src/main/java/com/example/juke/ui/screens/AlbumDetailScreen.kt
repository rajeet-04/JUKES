package com.example.juke.ui.screens

import com.example.juke.ui.icons.JukeIcons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import com.example.juke.models.findOfflineTrack
import com.example.juke.viewmodels.DownloadStatus
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.juke.models.SpotifyAlbum
import com.example.juke.models.SpotifySimplifiedTrack
import com.example.juke.ui.components.FlatTrackRow
import com.example.juke.ui.components.GlassTopAppBar
import com.example.juke.ui.components.MediaDetailSkeleton
import com.example.juke.ui.components.SwipeToAddNextContainer
import com.example.juke.viewmodels.AlbumDetailViewModel
import com.example.juke.viewmodels.MusicViewModel
import kotlinx.coroutines.launch

@Composable
fun AlbumDetailScreen(
    albumDetailViewModel: AlbumDetailViewModel = viewModel(),
    musicViewModel: MusicViewModel = viewModel(),
    onNavigateBack: () -> Unit,
    bottomPadding: Dp = 0.dp
) {
    val uiState by albumDetailViewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val album = uiState.album
    val offlineTracks by musicViewModel.offlineTracks.collectAsStateWithLifecycle()
    val downloads by musicViewModel.uiState.collectAsStateWithLifecycle()
    val savedAlbums by musicViewModel.offlineAlbums.albums.collectAsStateWithLifecycle()
    val savedCount = uiState.tracks.count { it.findOfflineTrack(offlineTracks) != null }
    val allSaved = uiState.tracks.isNotEmpty() && savedCount == uiState.tracks.size
    val isSaving = album != null && (downloads.downloadQueue + listOfNotNull(downloads.currentDownload))
        .any { it.song.albumSpotifyId == album.id && it.status in listOf(DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING) }
    val wasRequested = savedAlbums.any { it.album.id == album?.id }


    Scaffold(
        topBar = {
            GlassTopAppBar(
                title = {
                    Text(
                        album?.name ?: "Album",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(JukeIcons.Back, "Back")
                    }
                }
            )
        },
        containerColor = androidx.compose.ui.graphics.Color.Transparent
    ) { paddingValues ->
        if (uiState.isLoading || (album == null && uiState.error == null)) {
            MediaDetailSkeleton(
                modifier = Modifier.padding(paddingValues),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    top = 16.dp,
                    end = 20.dp,
                    bottom = 16.dp + bottomPadding
                )
            )
        } else if (album == null) {
            Column(Modifier.padding(paddingValues).padding(20.dp)) {
                Text(uiState.error ?: "Unable to load album", color = MaterialTheme.colorScheme.error)
            }
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
                // Album Header
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AsyncImage(
                            model = album.images.firstOrNull()?.url ?: "",
                            contentDescription = album.name,
                            modifier = Modifier
                                .size(200.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = album.name,
                            style = MaterialTheme.typography.headlineMedium
                        )

                        Text(
                            text = album.artists.joinToString(", ") { it.name },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text(
                                text = album.albumType?.replaceFirstChar {
                                    if (it.isLowerCase()) it.titlecase(
                                        java.util.Locale.getDefault()
                                    ) else it.toString()
                                } ?: "Album",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Text(
                                text = album.releaseDate?.take(4) ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Text(
                                text = "${album.totalTracks ?: 0} tracks",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        uiState.error?.let { error ->
                            Text(error, color = MaterialTheme.colorScheme.error)
                            Button(onClick = { albumDetailViewModel.loadAlbumDetails(album) }) { Text("Retry loading tracks") }
                        }
                        Button(
                            onClick = { musicViewModel.saveAlbumOffline(album, uiState.tracks) },
                            enabled = uiState.tracks.isNotEmpty() && !(allSaved && wasRequested) && !isSaving,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(if (allSaved) JukeIcons.Check else JukeIcons.Download, null,
                                Modifier.size(20.dp))
                            Text(
                                when {
                                    allSaved && wasRequested -> "Saved offline"
                                    isSaving -> "Saving offline…"
                                    wasRequested -> "Retry missing tracks"
                                    else -> "Save offline"
                                }, Modifier.padding(start = 8.dp)
                            )
                        }
                        if (isSaving) LinearProgressIndicator(
                            progress = { savedCount.toFloat() / uiState.tracks.size.coerceAtLeast(1) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (wasRequested || savedCount > 0) Text(
                            "$savedCount of ${uiState.tracks.size} tracks available offline",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                                    val local = track.findOfflineTrack(offlineTracks)
                                    if (local != null) musicViewModel.addNext(local)
                                    else musicViewModel.queueSimplifiedTrackNext(track, album)
                                }
                            }
                        ) {
                            TrackItem(
                                track = track,
                                album = album,
                                isOffline = track.findOfflineTrack(offlineTracks) != null,
                                onClick = {
                                    scope.launch {
                                        // Queue this track and all tracks below it from the album
                                        val index = uiState.tracks.indexOf(track)
                                        if (track.findOfflineTrack(offlineTracks) != null) {
                                            musicViewModel.setQueue(uiState.tracks.drop(index)
                                                .mapNotNull { it.findOfflineTrack(offlineTracks) })
                                        } else {
                                            musicViewModel.setQueueFromSimplifiedTracks(uiState.tracks, album, index)
                                        }
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
    track: SpotifySimplifiedTrack,
    album: SpotifyAlbum,
    isOffline: Boolean,
    onClick: () -> Unit
) {
    FlatTrackRow(
        imageUrl = album.images.lastOrNull()?.url,
        title = track.name,
        subtitle = track.artists.joinToString(", ") { it.name } + if (isOffline) " • Offline" else "",
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
