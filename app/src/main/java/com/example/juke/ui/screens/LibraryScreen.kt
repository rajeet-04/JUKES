package com.example.juke.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.juke.ui.components.LibraryTrackItem
import com.example.juke.viewmodels.LibraryViewModel
import com.example.juke.viewmodels.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    musicViewModel: MusicViewModel,
    libraryViewModel: LibraryViewModel = viewModel()
) {
    val uiState by libraryViewModel.uiState.collectAsState()
    
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
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
                    items(uiState.tracks) { track ->
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
