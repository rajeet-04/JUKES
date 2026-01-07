package com.example.juke.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.juke.models.Track
import com.example.juke.ui.components.AddToPlaylistDialog
import com.example.juke.ui.components.CreatePlaylistDialog
import com.example.juke.ui.components.DownloadingTrackItem
import com.example.juke.ui.components.EditPlaylistDialog
import com.example.juke.ui.components.LibraryTrackItem
import com.example.juke.ui.components.SwipeToAddNextContainer
import com.example.juke.viewmodels.LibraryViewModel
import com.example.juke.viewmodels.MusicViewModel
import com.example.juke.viewmodels.SortOption
import kotlinx.coroutines.launch

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

    LocalContext.current
    val view = LocalView.current

    // Helper for haptics
    fun performHapticFeedback() {
        view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
    }

    // Handle back press to exit selection mode
    androidx.activity.compose.BackHandler(enabled = uiState.isSelectionMode) {
        libraryViewModel.toggleSelectionMode(false)
    }

    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showEditPlaylistDialog by remember { mutableStateOf(false) }

    // Changed to support multiple tracks
    var tracksForPlaylistDialog by remember { mutableStateOf<List<Track>?>(null) }

    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            libraryViewModel.importAudioFiles(uris)
        }
    }
    var trackPlaylists by remember {
        mutableStateOf<List<com.example.juke.database.PlaylistEntity>>(
            emptyList()
        )
    }

    // Fetch playlists for the selected track when dialog opens
    LaunchedEffect(tracksForPlaylistDialog) {
        tracksForPlaylistDialog?.let { tracks ->
            if (tracks.size == 1) {
                trackPlaylists = libraryViewModel.getPlaylistsForTrack(tracks.first().uuid)
            } else {
                trackPlaylists = emptyList() // or intersection if needed
            }
        }
    }

    LaunchedEffect(Unit) {
        // Initial load is handled by the flow in ViewModel
    }

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search and Sort Row OR Selection Top Bar
            if (uiState.isSelectionMode) {
                // Selection Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { libraryViewModel.toggleSelectionMode(false) }) {
                        Icon(Icons.Default.Clear, contentDescription = "Close selection")
                    }

                    Text(
                        text = "${uiState.selectedTrackUuids.size}", // Shortened for space
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                    )

                    // Actions Row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(onClick = {
                            val selectedTracks =
                                uiState.tracks.filter { uiState.selectedTrackUuids.contains(it.uuid) }
                            musicViewModel.addNext(selectedTracks)
                            libraryViewModel.clearSelection()
                            performHapticFeedback()
                        }) {
                            Icon(
                                Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = "Play Next",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        IconButton(onClick = {
                            val selectedTracks =
                                uiState.tracks.filter { uiState.selectedTrackUuids.contains(it.uuid) }
                            musicViewModel.addToQueue(selectedTracks)
                            libraryViewModel.clearSelection()
                            performHapticFeedback()
                        }) {
                            // Use differen icon if possible, or same
                            Icon(
                                Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = "Add to Queue"
                            )
                        }

                        IconButton(onClick = {
                            val selectedTracks =
                                uiState.tracks.filter { uiState.selectedTrackUuids.contains(it.uuid) }
                            if (selectedTracks.isNotEmpty()) {
                                tracksForPlaylistDialog = selectedTracks
                                libraryViewModel.clearSelection() // Optionally keep selection?
                            }
                        }) {
                            Icon(Icons.Default.AddCircle, contentDescription = "Add to Playlist")
                        }
                    }

                    // Select All / Menu
                    var showMenu by remember { mutableStateOf(false) }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }

                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(if (uiState.selectedTrackUuids.size == uiState.tracks.size && uiState.tracks.isNotEmpty()) "Deselect All" else "Select All") },
                            onClick = {
                                showMenu = false
                                if (uiState.selectedTrackUuids.size == uiState.tracks.size && uiState.tracks.isNotEmpty()) {
                                    libraryViewModel.clearSelection()
                                } else {
                                    libraryViewModel.selectAll()
                                }
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                showMenu = false
                                if (uiState.selectedTrackUuids.isNotEmpty()) {
                                    // Need to lift state out or re-implement dialog here.
                                    // Existing code had showDeleteDialog inside the row.
                                    // We can reuse that approach by exposing a state or just handling it here.
                                    // For simplicity, let's keep the delete dialog logic separate or simplified.
                                }
                            }
                        )
                    }

                    var showDeleteDialog by remember { mutableStateOf(false) }
                    // Re-add Delete Button (optional if in menu, but user might prefer direct access)
                    if (uiState.selectedTrackUuids.isNotEmpty()) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    if (showDeleteDialog) {
                        AlertDialog(
                            onDismissRequest = { showDeleteDialog = false },
                            title = { Text("Delete ${uiState.selectedTrackUuids.size} tracks?") },
                            text = { Text("This action cannot be undone.") },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        performHapticFeedback()
                                        libraryViewModel.deleteSelectedTracks()
                                        showDeleteDialog = false
                                    },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Delete")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    showDeleteDialog = false
                                }) { Text("Cancel") }
                            }
                        )
                    }
                }
            } else {
                // Search and Sort Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Search Field (70%)
                    TextField(
                        value = uiState.searchQuery,
                        onValueChange = { libraryViewModel.updateSearchQuery(it) },
                        modifier = Modifier.weight(0.7f),
                        placeholder = { Text("Search Anything...") },
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
                    Button(
                        onClick = { libraryViewModel.toggleSortSheet() },
                        modifier = Modifier
                            .weight(0.1f)
                            .heightIn(min = 56.dp),
                        contentPadding = PaddingValues(0.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        elevation = ButtonDefaults.buttonElevation(0.dp)
                    ) {
                        Text(
                            "Sort",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1
                        )
                    }
                }
            }
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
                            {
                                Icon(
                                    Icons.Default.LibraryMusic,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
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
                            {
                                Icon(
                                    Icons.Filled.Favorite,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else {
                            {
                                Icon(
                                    Icons.Outlined.FavoriteBorder,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    )
                }

                // 3. Playlists (Dynamic)
                // 3. Playlists (Dynamic)
                items(uiState.playlists, key = { it.id }) { playlist ->
                    var showMenu by remember { mutableStateOf(false) }
                    var showDeleteDialog by remember { mutableStateOf(false) }

                    FilterChip(
                        selected = uiState.selectedPlaylist?.id == playlist.id,
                        onClick = { libraryViewModel.loadPlaylistTracks(playlist.id) },
                        label = { Text(playlist.name) },
                        leadingIcon = if (uiState.selectedPlaylist?.id == playlist.id) {
                            {
                                Icon(
                                    Icons.AutoMirrored.Filled.QueueMusic,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else null,
                        trailingIcon = {
                            Box {
                                IconButton(
                                    onClick = { showMenu = true },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.MoreVert,
                                        contentDescription = "Options",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Add to Queue") },
                                        onClick = {
                                            showMenu = false
                                            libraryViewModel.addPlaylistToQueue(
                                                playlist,
                                                musicViewModel
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.AutoMirrored.Filled.QueueMusic,
                                                contentDescription = null
                                            )
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete") },
                                        onClick = {
                                            showMenu = false
                                            showDeleteDialog = true
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = null
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    )

                    if (showDeleteDialog) {
                        AlertDialog(
                            onDismissRequest = { showDeleteDialog = false },
                            title = { Text("Delete Playlist") },
                            text = { Text("Are you sure you want to delete '${playlist.name}'?") },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        libraryViewModel.deletePlaylist(playlist)
                                        showDeleteDialog = false
                                    }
                                ) {
                                    Text("Delete", color = MaterialTheme.colorScheme.error)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDeleteDialog = false }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }
                }

                // 4. Import Button
                item {
                    AssistChip(
                        onClick = {
                            importLauncher.launch(arrayOf("audio/*"))
                        },
                        label = { Text("Import") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.AddCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }

                // 5. Create Playlist Button
                item {
                    AssistChip(
                        onClick = { showCreatePlaylistDialog = true },
                        label = { Text("New") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }

            // Header with track count and controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Track count or Playlist Name
                Column(verticalArrangement = Arrangement.Center) {
                    if (uiState.selectedPlaylist != null) {
                        Text(
                            text = uiState.selectedPlaylist!!.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${uiState.tracks.size} songs",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "${uiState.tracks.size} songs",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Controls row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Edit Button (Rename)
                    if (uiState.selectedPlaylist != null) {
                        var showRenameDialog by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = { showRenameDialog = true }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Rename Playlist",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (showRenameDialog) {
                            var newName by remember { mutableStateOf(uiState.selectedPlaylist!!.name) }
                            AlertDialog(
                                onDismissRequest = { showRenameDialog = false },
                                title = { Text("Rename Playlist") },
                                text = {
                                    OutlinedTextField(
                                        value = newName,
                                        onValueChange = { newName = it },
                                        label = { Text("Name") },
                                        singleLine = true
                                    )
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            if (newName.isNotBlank()) {
                                                libraryViewModel.updatePlaylist(
                                                    uiState.selectedPlaylist!!,
                                                    newName,
                                                    uiState.selectedPlaylist!!.thumbnailUri
                                                )
                                                showRenameDialog = false
                                            }
                                        }
                                    ) {
                                        Text("Save")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showRenameDialog = false }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }
                    }

                    // Shuffle button
                    IconButton(
                        onClick = { musicViewModel.toggleShuffle() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (musicUiState.isShuffleEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }

                    // Add to Queue button
                    IconButton(
                        onClick = {
                            if (uiState.tracks.isNotEmpty()) {
                                val tracksToAdd = if (musicUiState.isShuffleEnabled) {
                                    uiState.tracks.shuffled()
                                } else {
                                    uiState.tracks
                                }
                                musicViewModel.addToQueue(tracksToAdd)
                            }
                        },
                        enabled = uiState.tracks.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = "Add all to Queue",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Play button
                    FilledTonalButton(
                        onClick = {
                            if (uiState.tracks.isNotEmpty()) {
                                val tracksToPlay = if (musicUiState.isShuffleEnabled) {
                                    uiState.tracks.shuffled()
                                } else {
                                    uiState.tracks
                                }
                                musicViewModel.setQueue(tracksToPlay, startIndex = 0)
                            }
                        },
                        enabled = uiState.tracks.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play all",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Play")
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
                            PlaylistHeader(
                                playlist = playlist,
                                onEditClick = { showEditPlaylistDialog = true }
                            )
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
                    itemsIndexed(
                        items = uiState.tracks,
                        key = { index, track -> "${track.uuid}_$index" }
                    ) { index, track ->
                        SwipeToAddNextContainer(
                            onAddNext = { musicViewModel.addNext(track) },
                            onDelete = { libraryViewModel.deleteTrack(track.uuid) }
                        ) {
                            LibraryTrackItem(
                                track = track,
                                isSelectionMode = uiState.isSelectionMode,
                                isSelected = uiState.selectedTrackUuids.contains(track.uuid),
                                onPlay = {
                                    if (uiState.isSelectionMode) {
                                        performHapticFeedback()
                                        libraryViewModel.toggleTrackSelection(track.uuid)
                                    } else {
                                        musicViewModel.setQueue(
                                            uiState.tracks,
                                            uiState.tracks.indexOf(track)
                                        )
                                    }
                                },
                                onLongClick = {
                                    performHapticFeedback()
                                    libraryViewModel.toggleTrackSelection(track.uuid)
                                },
                                onToggleFavorite = {
                                    libraryViewModel.toggleFavorite(track.uuid)
                                },
                                trailingIcon = if (uiState.selectedPlaylist != null) Icons.Default.RemoveCircle else Icons.Default.AddCircle,
                                onTrailingIconClick = {
                                    if (uiState.selectedPlaylist != null) {
                                        // Direct remove if in playlist view
                                        coroutineScope.launch {
                                            libraryViewModel.removeFromPlaylist(
                                                uiState.selectedPlaylist!!,
                                                track
                                            )
                                        }
                                    } else {
                                        // Open dialog if in All Tracks / Favorites
                                        tracksForPlaylistDialog = listOf(track)
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

    if (showEditPlaylistDialog && uiState.selectedPlaylist != null) {
        val playlist = uiState.selectedPlaylist!!
        EditPlaylistDialog(
            initialName = playlist.name,
            initialThumbnailUri = playlist.thumbnailUri,
            onDismiss = { showEditPlaylistDialog = false },
            onConfirm = { newName, newUri ->
                libraryViewModel.updatePlaylist(playlist, newName, newUri)
                showEditPlaylistDialog = false
            }
        )
    }

    tracksForPlaylistDialog?.let { tracks ->
        AddToPlaylistDialog(
            playlists = uiState.playlists,
            tracks = tracks,
            trackPlaylists = trackPlaylists,
            onDismiss = {
                tracksForPlaylistDialog = null
                trackPlaylists = emptyList()
            },
            onAddToPlaylist = { playlist ->
                coroutineScope.launch {
                    libraryViewModel.addTracksToPlaylist(playlist, tracks)
                    // Refresh list logic would need to handle multiple tracks or just clear
                    tracksForPlaylistDialog = null
                }
            },
            onRemoveFromPlaylist = { playlist ->
                coroutineScope.launch {
                    // Logic for remove from playlist with list? 
                    // AddToPlaylistDialog hides remove if multiple. 
                    if (tracks.size == 1) {
                        libraryViewModel.removeFromPlaylist(playlist, tracks.first())
                        trackPlaylists = libraryViewModel.getPlaylistsForTrack(tracks.first().uuid)
                    }
                }
            },
            onCreatePlaylist = { showCreatePlaylistDialog = true },
            onRemoveFromCurrentPlaylist = if (uiState.selectedPlaylist != null) {
                { playlist ->
                    if (playlist.id == uiState.selectedPlaylist?.id) {
                        coroutineScope.launch {
                            // Handle removal
                            if (tracks.size == 1) {
                                libraryViewModel.removeFromPlaylist(playlist, tracks.first())
                            }
                            // For multiple, simple remove loop?
                            // LibraryViewModel.removeFromPlaylist is single.
                            // Add batch remove if needed, but not critical for this specific callback context which usually is invoked by clicking 'Remove' on list item.
                            // Dialog logic hides 'Remove' for multiple so this might be unreachable for >1.
                            tracksForPlaylistDialog = null
                        }
                    }
                }
            } else null,
            currentPlaylist = uiState.selectedPlaylist
        )
    }

    // Sort Bottom Sheet
    if (uiState.showSortSheet) {
        ModalBottomSheet(
            onDismissRequest = { libraryViewModel.toggleSortSheet() },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = "Sort by",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                )

                val sortOptions = listOf(
                    SortOption.RECENTLY_ADDED to "Recently Added",
                    SortOption.TITLE to "Title",
                    SortOption.ARTIST to "Artist",
                    SortOption.LAST_PLAYED to "Last Played"
                )

                sortOptions.forEach { (option, label) ->
                    ListItem(
                        headlineContent = { Text(label) },
                        leadingContent = {
                            if (uiState.sortOption == option) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { libraryViewModel.updateSortOption(option) }
                    )
                }
            }
        }
    }


    // Undo Delete Popup
    uiState.pendingDeleteTrack?.let { track ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 16.dp + bottomPadding),
            contentAlignment = Alignment.BottomCenter
        ) {
            val progress = remember { androidx.compose.animation.core.Animatable(1f) }

            LaunchedEffect(track) {
                progress.snapTo(1f)
                progress.animateTo(
                    targetValue = 0f,
                    animationSpec = androidx.compose.animation.core.tween(
                        durationMillis = 5000,
                        easing = androidx.compose.animation.core.LinearEasing
                    )
                )
            }

            Card(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(
                            horizontal = 12.dp,
                            vertical = 8.dp
                        ) // Reduced padding for compactness
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        // Circular Countdown
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = { progress.value },
                                modifier = Modifier.size(28.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                strokeWidth = 3.dp,
                            )
                            Text(
                                text = kotlin.math.ceil(progress.value * 5).toInt().coerceAtLeast(1)
                                    .toString(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Text(
                            text = "Deleted \"${track.title}\"",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    TextButton(
                        onClick = { libraryViewModel.undoDelete() },
                        // Reducing visual weight of button to emphasize the countdown/content
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("Undo")
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistHeader(
    playlist: com.example.juke.database.PlaylistEntity,
    onEditClick: () -> Unit
) {
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

                // Edit Button
                FilledTonalButton(
                    onClick = onEditClick,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Playlist",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Edit", style = MaterialTheme.typography.labelMedium)
                }
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




