package com.example.juke.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.juke.ui.components.DownloadingTrackItem
import com.example.juke.ui.components.LibraryTrackItem
import com.example.juke.ui.components.SwipeToAddNextContainer
import com.example.juke.viewmodels.LibraryViewModel
import com.example.juke.viewmodels.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    musicViewModel: MusicViewModel,
    libraryViewModel: LibraryViewModel = viewModel()
) {
    val uiState by libraryViewModel.uiState.collectAsState()
    val musicUiState by musicViewModel.uiState.collectAsState()
    
    LaunchedEffect(Unit) {
        // Initial load is handled by the flow in ViewModel
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Filter chips
            // Filter chips and playlist selectors
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = uiState.selectedPlaylist == null && !uiState.showFavoritesOnly,
                        onClick = { libraryViewModel.loadAllTracks() },
                        label = { Text("All Tracks") }
                    )
                    FilterChip(
                        selected = uiState.showFavoritesOnly,
                        onClick = { libraryViewModel.toggleFavoritesFilter() },
                        label = { Text("Favourites") }
                    )
                }

                if (uiState.playlists.isNotEmpty()) {
                    Text(
                        "Playlists",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.playlists, key = { it.id }) { playlist ->
                            FilterChip(
                                selected = uiState.selectedPlaylist?.id == playlist.id,
                                onClick = { libraryViewModel.loadPlaylistTracks(playlist.id) },
                                label = { Text(playlist.name) }
                            )
                        }
                    }
                }
            }
            
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.tracks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            if (uiState.showFavoritesOnly) "No favourite tracks yet" 
                            else "No downloaded tracks yet",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            if (uiState.showFavoritesOnly) "Mark tracks as favourites to see them here"
                            else "Search and download tracks to build your library",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Playlist hero if one is selected
                    uiState.selectedPlaylist?.let { playlist ->
                        item(key = "playlist_header_${playlist.id}") {
                            PlaylistHeader(playlist)
                        }
                    }

                    // Show current download
                    musicUiState.currentDownload?.let { download ->
                        item(key = "current_${download.id}") {
                            DownloadingTrackItem(
                                downloadItem = download,
                                onRetry = {
                                    musicViewModel.retryFailedDownload(download)
                                }
                            )
                        }
                    }
                    
                    // Show download queue
                    items(
                        items = musicUiState.downloadQueue,
                        key = { it.id }
                    ) { download ->
                        DownloadingTrackItem(
                            downloadItem = download,
                            onCancel = {
                                musicViewModel.cancelDownload(download.id)
                            },
                            onRetry = {
                                musicViewModel.retryFailedDownload(download)
                            }
                        )
                    }
                    
                    // Show downloaded tracks
                    items(uiState.tracks) { track ->
                        SwipeToAddNextContainer(
                            onAddNext = { musicViewModel.addNext(track) }
                        ) {
                            LibraryTrackItem(
                                track = track,
                                onPlay = {
                                    musicViewModel.setQueue(
                                        uiState.tracks,
                                        uiState.tracks.indexOf(track)
                                    )
                                },
                                onToggleFavorite = {
                                    libraryViewModel.toggleFavorite(track)
                                },
                                onDelete = {
                                    libraryViewModel.deleteTrack(track)
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
private fun PlaylistHeader(playlist: com.example.juke.database.PlaylistEntity) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AsyncImage(
                model = playlist.thumbnailUri,
                contentDescription = playlist.name,
                modifier = Modifier
                    .size(72.dp)
                    .aspectRatio(1f),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${playlist.trackCount} tracks",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}