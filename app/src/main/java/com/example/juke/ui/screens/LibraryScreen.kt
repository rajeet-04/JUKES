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
        libraryViewModel.loadTracks()
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !uiState.showFavoritesOnly,
                    onClick = { 
                        if (uiState.showFavoritesOnly) {
                            libraryViewModel.toggleFavoritesFilter()
                        }
                    },
                    label = { Text("All Tracks") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = uiState.showFavoritesOnly,
                    onClick = { 
                        if (!uiState.showFavoritesOnly) {
                            libraryViewModel.toggleFavoritesFilter()
                        }
                    },
                    label = { Text("Favourites") },
                    modifier = Modifier.weight(1f)
                )
            }
            
            // Playlists section
            if (uiState.playlists.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        "Playlists",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.playlists) { playlist ->
                            ImportedPlaylistCard(
                                playlist = playlist,
                                onClick = {
                                    libraryViewModel.loadPlaylistTracks(playlist.id)
                                }
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
private fun ImportedPlaylistCard(
    playlist: com.example.juke.database.PlaylistEntity,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(150.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            AsyncImage(
                model = playlist.thumbnailUri,
                contentDescription = playlist.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "${playlist.trackCount} tracks",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}