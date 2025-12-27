package com.example.juke.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.clickable
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.example.juke.models.Track
import com.example.juke.ui.components.DownloadingTrackItem
import com.example.juke.ui.components.LibraryTrackItem
import com.example.juke.ui.components.SwipeToAddNextContainer
import com.example.juke.viewmodels.LibraryViewModel
import com.example.juke.viewmodels.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    musicViewModel: MusicViewModel,
    libraryViewModel: LibraryViewModel = viewModel(),
    bottomPadding: Dp = 0.dp
) {
    val coroutineScope = rememberCoroutineScope()
    val uiState by libraryViewModel.uiState.collectAsState()
    val musicUiState by musicViewModel.uiState.collectAsState()
    
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }

    var showAddToPlaylistDialog by remember { mutableStateOf<Track?>(null) }
    var trackPlaylists by remember { mutableStateOf<List<com.example.juke.database.PlaylistEntity>>(emptyList()) }
    
    // Fetch playlists for the selected track when dialog opens
    LaunchedEffect(showAddToPlaylistDialog) {
        showAddToPlaylistDialog?.let { track ->
            trackPlaylists = libraryViewModel.getPlaylistsForTrack(track.uuid)
        }
    }
    
    LaunchedEffect(Unit) {
        // Initial load is handled by the flow in ViewModel
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library") },
                actions = {
                    IconButton(onClick = { libraryViewModel.shufflePlay(uiState.tracks, musicViewModel) }) {
                        Icon(Icons.Default.Shuffle, contentDescription = "Shuffle Play")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Enhanced Search Field
            TextField(
                value = uiState.searchQuery,
                onValueChange = { libraryViewModel.updateSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 16.dp),
                placeholder = { Text("Search tracks, artists, lyrics...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search"
                    )
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { libraryViewModel.clearSearch() }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear search"
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                )
            )
            
            // Enhanced Filter Row
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // 1. All Tracks
                item {
                    FilterChip(
                        selected = uiState.selectedPlaylist == null && !uiState.showFavoritesOnly,
                        onClick = { libraryViewModel.loadAllTracks() },
                        label = { Text("All Tracks") },
                        leadingIcon = if (uiState.selectedPlaylist == null && !uiState.showFavoritesOnly) {
                            { Icon(Icons.Default.LibraryMusic, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else null
                    )
                }
                
                // 2. Favourites
                item {
                    FilterChip(
                        selected = uiState.showFavoritesOnly,
                        onClick = { libraryViewModel.toggleFavoritesFilter() },
                        label = { Text("Favourites") },
                        leadingIcon = if (uiState.showFavoritesOnly) {
                            { Icon(Icons.Filled.Favorite, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else {
                            { Icon(Icons.Outlined.FavoriteBorder, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        }
                    )
                }
                
                // 3. Playlists (Dynamic)
                items(uiState.playlists, key = { it.id }) { playlist ->
                    FilterChip(
                        selected = uiState.selectedPlaylist?.id == playlist.id,
                        onClick = { libraryViewModel.loadPlaylistTracks(playlist.id) },
                        label = { Text(playlist.name) },
                        leadingIcon = if (uiState.selectedPlaylist?.id == playlist.id) {
                            { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else null
                    )
                }
                
                // 4. Create Playlist Button
                item {
                    AssistChip(
                        onClick = { showCreatePlaylistDialog = true },
                        label = { Text("New") },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
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
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Icon(
                                imageVector = if (uiState.searchQuery.isNotEmpty()) Icons.Outlined.SearchOff
                                    else if (uiState.showFavoritesOnly) Icons.Outlined.FavoriteBorder
                                    else Icons.Outlined.LibraryMusic,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (uiState.searchQuery.isNotEmpty()) "No tracks found"
                                    else if (uiState.showFavoritesOnly) "No favourite tracks yet" 
                                    else "No downloaded tracks yet",
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (uiState.searchQuery.isNotEmpty()) "Try a different search term"
                                    else if (uiState.showFavoritesOnly) "Mark tracks as favourites to see them here"
                                    else "Search and download tracks to build your library",
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        top = 8.dp,
                        end = 20.dp,
                        bottom = 16.dp + bottomPadding
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
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
                    
                    // Show QueueManager recommendation downloads
                    itemsIndexed(
                        items = uiState.recommendationDownloads,
                        key = { index, downloadInfo -> "rec_${index}_${downloadInfo.title}_${downloadInfo.artist}_${downloadInfo.source}" }
                    ) { index, downloadInfo ->
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(40.dp),
                                    strokeWidth = 3.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = downloadInfo.title,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = downloadInfo.artist,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = when (downloadInfo.source) {
                                            "recommendation" -> "Downloading recommendation..."
                                            "playlist" -> "Importing from playlist..."
                                            else -> "Downloading..."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                    
                    // Show downloaded tracks
                    items(
                        items = uiState.tracks,
                        key = { track -> track.uuid }
                    ) { track ->
                        SwipeToAddNextContainer(
                            onAddNext = { musicViewModel.addNext(track) },
                            onDelete = { libraryViewModel.deleteTrack(track.uuid) }
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
                                    libraryViewModel.toggleFavorite(track.uuid)
                                },
                                trailingIcon = if (uiState.selectedPlaylist != null) Icons.Default.RemoveCircle else Icons.Default.AddCircle,
                                onTrailingIconClick = {
                                    if (uiState.selectedPlaylist != null) {
                                        // Direct remove if in playlist view
                                        coroutineScope.launch {
                                            libraryViewModel.removeFromPlaylist(uiState.selectedPlaylist!!, track)
                                        }
                                    } else {
                                        // Open dialog if in All Tracks / Favorites
                                        showAddToPlaylistDialog = track
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    
    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylistDialog = false },
            onCreate = { name ->
                libraryViewModel.createPlaylist(name)
                showCreatePlaylistDialog = false
            }
        )
    }
    
    showAddToPlaylistDialog?.let { track ->
        AddToPlaylistDialog(
            playlists = uiState.playlists,
            track = track,
            trackPlaylists = trackPlaylists,
            onDismiss = { 
                showAddToPlaylistDialog = null
                trackPlaylists = emptyList()
            },
            onAddToPlaylist = { playlist ->
                coroutineScope.launch {
                    libraryViewModel.addToPlaylist(playlist, track)
                    // Refresh list of playlists for this track
                    trackPlaylists = libraryViewModel.getPlaylistsForTrack(track.uuid)
                }
            },
            onRemoveFromPlaylist = { playlist ->
                 coroutineScope.launch {
                     libraryViewModel.removeFromPlaylist(playlist, track)
                     // Refresh list of playlists for this track
                     trackPlaylists = libraryViewModel.getPlaylistsForTrack(track.uuid)
                 }
            },
            onRemoveFromCurrentPlaylist = if (uiState.selectedPlaylist != null) {
                { playlist ->
                    if (playlist.id == uiState.selectedPlaylist?.id) {
                         coroutineScope.launch {
                             libraryViewModel.removeFromPlaylist(playlist, track)
                             showAddToPlaylistDialog = null
                         }
                    }
                }
            } else null,
            currentPlaylist = uiState.selectedPlaylist
        )
    }
}

@Composable
private fun PlaylistHeader(playlist: com.example.juke.database.PlaylistEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.size(96.dp),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                AsyncImage(
                    model = playlist.thumbnailUri,
                    contentDescription = playlist.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${playlist.trackCount} tracks",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}


@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Playlist") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Playlist Name") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name) },
                enabled = name.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AddToPlaylistDialog(
    playlists: List<com.example.juke.database.PlaylistEntity>,
    track: com.example.juke.models.Track,
    trackPlaylists: List<com.example.juke.database.PlaylistEntity>,
    onDismiss: () -> Unit,
    onAddToPlaylist: (com.example.juke.database.PlaylistEntity) -> Unit,
    onRemoveFromPlaylist: (com.example.juke.database.PlaylistEntity) -> Unit,
    onRemoveFromCurrentPlaylist: ((com.example.juke.database.PlaylistEntity) -> Unit)? = null,
    currentPlaylist: com.example.juke.database.PlaylistEntity? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to Playlist") },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // If we are in a playlist context, we might want to show "Remove from current" prominently, 
                // but per user request, we are handling that with the minus icon.
                // However, if the modal IS opened (though unlikely in playlist view due to minus icon), we keep logic generic.
                
                items(playlists) { playlist ->
                    val isAlreadyAdded = trackPlaylists.any { it.id == playlist.id }
                    
                    ListItem(
                        headlineContent = { Text(playlist.name) },
                        supportingContent = { 
                            if (isAlreadyAdded) Text("Already added", color = MaterialTheme.colorScheme.primary)
                            else Text("${playlist.trackCount} tracks") 
                        },
                        leadingContent = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null) },
                        trailingContent = {
                            if (isAlreadyAdded) {
                                IconButton(onClick = { onRemoveFromPlaylist(playlist) }) {
                                    Icon(Icons.Default.RemoveCircle, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                                }
                            } else {
                                IconButton(onClick = { onAddToPlaylist(playlist) }) {
                                    Icon(Icons.Default.AddCircle, contentDescription = "Add", tint = MaterialTheme.colorScheme.primary)
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