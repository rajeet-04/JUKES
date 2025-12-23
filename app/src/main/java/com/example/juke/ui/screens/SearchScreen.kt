package com.example.juke.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.juke.models.SpotifyArtist
import com.example.juke.models.SpotifyPlaylist
import com.example.juke.ui.components.ArtistCard
import com.example.juke.ui.components.PlaylistCard
import com.example.juke.ui.components.SwipeToAddNextContainer
import com.example.juke.viewmodels.MusicViewModel
import com.example.juke.viewmodels.SearchViewModel
import com.example.juke.network.SpotifyApi
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    musicViewModel: MusicViewModel,
    searchViewModel: SearchViewModel = viewModel(),
    onNavigateToArtist: (SpotifyArtist) -> Unit = {},
    onNavigateToPlaylist: (SpotifyPlaylist) -> Unit = {}
) {
    val uiState by searchViewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    
    LaunchedEffect(Unit) {
        searchViewModel.updateQuery("")
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = { searchViewModel.updateQuery(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search for songs, artists, playlists...") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = ImeAction.Search
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSearch = {
                        if (uiState.query.isNotBlank() && !uiState.isSearching) {
                            keyboardController?.hide()
                            searchViewModel.search(uiState.query)
                        }
                    }
                ),
                trailingIcon = {
                    if (uiState.isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Button(
                onClick = {
                    keyboardController?.hide()
                    searchViewModel.search(uiState.query)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isSearching && uiState.query.isNotBlank()
            ) {
                Text("Search")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Show import playlist button if playlist URL detected
            if (uiState.isPlaylistUrl && uiState.playlists.isNotEmpty() && !uiState.isImportingPlaylist) {
                val playlist = uiState.playlists.first()
                Button(
                    onClick = {
                        scope.launch {
                            searchViewModel.importPlaylist(uiState.playlistId!!) { track ->
                                musicViewModel.downloadSong(SpotifyApi.spotifyTrackToSong(track))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Text("Import Playlist (${playlist.tracks.total} tracks)")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Show import progress
            if (uiState.isImportingPlaylist) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            "Importing Playlist...",
                            style = MaterialTheme.typography.titleMedium
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
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            if (uiState.error != null) {
                Text(
                    uiState.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            val hasResults = uiState.tracks.isNotEmpty() || 
                           uiState.artists.isNotEmpty() || 
                           uiState.playlists.isNotEmpty()
            
            if (!hasResults && !uiState.isSearching && uiState.query.isNotBlank()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No results found")
                }
            } else if (hasResults) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
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
                                            searchViewModel.setDownloading(track.id)
                                            try {
                                                musicViewModel.queueSpotifyTrackNext(track)
                                            } finally {
                                                searchViewModel.setDownloading(null)
                                            }
                                        }
                                    }
                                ) {
                                    TrackItem(
                                        track = track,
                                        isDownloading = uiState.downloadingId == track.id,
                                        onClick = {
                                            scope.launch {
                                                searchViewModel.setDownloading(track.id)
                                                musicViewModel.downloadAndPlay(
                                                    SpotifyApi.spotifyTrackToSong(track)
                                                )
                                                searchViewModel.setDownloading(null)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        // Artists Section
                        if (uiState.artists.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Artists",
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }

                            item {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(uiState.artists) { artist ->
                                        ArtistCard(
                                            artist = artist,
                                            onClick = { onNavigateToArtist(artist) }
                                        )
                                    }
                                }
                            }
                        }

                        // Playlists Section
                        if (uiState.playlists.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Playlists",
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }

                            item {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(uiState.playlists) { playlist ->
                                        PlaylistCard(
                                            playlist = playlist,
                                            onClick = {
                                                onNavigateToPlaylist(playlist)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                }
            }
        }
    }
}

@Composable
private fun TrackItem(
    track: com.example.juke.models.SpotifyTrack,
    isDownloading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                coil.compose.AsyncImage(
                    model = track.album.images.lastOrNull()?.url ?: "",
                    contentDescription = track.name,
                    modifier = Modifier.size(60.dp),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
                
                if (isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(60.dp)
                            .align(Alignment.Center)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                
                Text(
                    text = track.artists.joinToString(", ") { it.name },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                
                Text(
                    text = track.album.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            
            Text(
                text = formatDuration(track.durationMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatDuration(durationMs: Int): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

