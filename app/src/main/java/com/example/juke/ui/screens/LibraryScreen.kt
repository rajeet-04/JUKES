package com.example.juke.ui.screens

import com.example.juke.ui.icons.JukeIcons

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.juke.models.Track
import com.example.juke.ui.components.CompactDownloadBanner
import com.example.juke.ui.components.CreatePlaylistDialog
import com.example.juke.ui.components.EditPlaylistDialog
import com.example.juke.ui.components.GlassAlertDialog
import com.example.juke.ui.components.GlassButton
import com.example.juke.ui.components.GlassFilterChip
import com.example.juke.ui.components.GlassIconButton
import com.example.juke.ui.components.GlassModalBottomSheet
import com.example.juke.ui.components.LibraryPlaylistSheet
import com.example.juke.ui.components.LibrarySelectionActions
import com.example.juke.ui.components.LibraryTrackActionsSheet
import com.example.juke.ui.components.LibraryTrackItem
import com.example.juke.ui.components.SearchHeader
import com.example.juke.ui.components.SwipeToAddNextContainer
import com.example.juke.ui.components.TrackListSkeleton
import com.example.juke.ui.theme.GlassCard
import com.example.juke.utils.rememberJukeHaptics
import com.example.juke.viewmodels.DownloadItem
import com.example.juke.viewmodels.LibraryViewModel
import com.example.juke.viewmodels.MusicViewModel
import com.example.juke.viewmodels.SortOption
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Composable
fun LibraryScreen(
    musicViewModel: MusicViewModel,
    libraryViewModel: LibraryViewModel = viewModel(),
    bottomPadding: Dp = 0.dp
) {
    val uiState by libraryViewModel.uiState.collectAsStateWithLifecycle()
    val libraryDownloadBannerState by remember(musicViewModel) {
        musicViewModel.uiState
            .map { state ->
                LibraryDownloadBannerState(
                    currentDownload = state.currentDownload,
                    downloadQueue = state.downloadQueue
                )
            }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(
        initialValue = LibraryDownloadBannerState(currentDownload = null, downloadQueue = emptyList())
    )
    val haptic = rememberJukeHaptics()
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    val importStatus by com.example.juke.services.PlaylistImportManager
        .get(androidx.compose.ui.platform.LocalContext.current).status.collectAsStateWithLifecycle()

    // Helper for haptics
    fun performHapticFeedback() {
        haptic.heavyClick()
    }

    // Handle back press to exit selection mode
    androidx.activity.compose.BackHandler(enabled = uiState.isSelectionMode) {
        libraryViewModel.toggleSelectionMode(false)
    }

    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showEditPlaylistDialog by remember { mutableStateOf(false) }
    var isLibraryShufflePrepared by rememberSaveable { mutableStateOf(false) }
    var preparedLibraryShuffleIds by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }

    // Changed to support multiple tracks
    var tracksForPlaylistDialog by remember { mutableStateOf<List<Track>?>(null) }
    var tracksForActions by remember { mutableStateOf<List<Track>?>(null) }
    var tracksForDelete by remember { mutableStateOf<List<Track>?>(null) }
    var queueActionBusy by remember { mutableStateOf(false) }
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(uiState.actionMessage) {
        uiState.actionMessage?.let { message ->
            try { snackbarHost.showSnackbar(message) }
            finally { libraryViewModel.consumeActionMessage(message) }
        }
    }

    fun queueSongs(tracks: List<Track>, next: Boolean) {
        if (queueActionBusy || tracks.isEmpty()) return
        queueActionBusy = true
        performHapticFeedback()
        val queuedCount = tracks.distinctBy { it.uuid }.count { it.uuid != musicViewModel.uiState.value.currentTrack?.uuid }
        val songs = "$queuedCount ${if (queuedCount == 1) "song" else "songs"}"
        val complete: (Boolean) -> Unit = { success ->
            queueActionBusy = false
            if (success) {
                libraryViewModel.clearSelection()
                tracksForActions = null
                libraryViewModel.showActionMessage(
                    if (next) "$songs will play next" else "$songs added to queue end"
                )
            } else libraryViewModel.showActionMessage("Couldn’t queue songs. Your selection is kept; try again.")
        }
        if (next) musicViewModel.addNext(tracks, complete) else musicViewModel.addToQueue(tracks, complete)
    }

    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            libraryViewModel.importAudioFiles(uris)
        }
    }
    val currentLibraryTrackIds = remember(uiState.tracks) {
        uiState.tracks.map { it.uuid }
    }
    val preparedLibraryTracks = remember(
        uiState.tracks,
        preparedLibraryShuffleIds,
        isLibraryShufflePrepared
    ) {
        if (!isLibraryShufflePrepared) {
            uiState.tracks
        } else {
            val tracksById = uiState.tracks.associateBy { it.uuid }
            val preparedTracks = preparedLibraryShuffleIds.mapNotNull { tracksById[it] }
            if (preparedTracks.size == uiState.tracks.size) {
                preparedTracks
            } else {
                val preparedTrackIds = preparedTracks.map { it.uuid }.toSet()
                preparedTracks + uiState.tracks.filter { it.uuid !in preparedTrackIds }
            }
        }
    }

    LaunchedEffect(currentLibraryTrackIds) {
        if (isLibraryShufflePrepared) {
            val sameMembership =
                preparedLibraryShuffleIds.size == currentLibraryTrackIds.size &&
                        preparedLibraryShuffleIds.toSet() == currentLibraryTrackIds.toSet()

            if (!sameMembership) {
                preparedLibraryShuffleIds = currentLibraryTrackIds.shuffled()
            }
        }
    }

    val selectedTracks = uiState.tracks.filter { it.uuid in uiState.selectedTrackUuids }
    val actionsEnabled = !queueActionBusy && !uiState.isRemovalCommitting

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = {
            if (!uiState.isSelectionMode) {
                SearchHeader(
                    title = "Your Library",
                    query = uiState.searchQuery,
                    onQueryChange = { libraryViewModel.updateSearchQuery(it) },
                    open = searchOpen || uiState.searchQuery.isNotEmpty(),
                    onOpenChange = { searchOpen = it },
                    placeholder = "Search your library"
                ) {
                    IconButton(
                        onClick = {
                            haptic.click()
                            libraryViewModel.toggleSortSheet()
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                    }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GlassIconButton(
                        onClick = { libraryViewModel.clearSelection() },
                        contentDescription = "Close selection", enabled = actionsEnabled
                    ) { Icon(JukeIcons.Close, null) }
                    Text("${uiState.selectedTrackUuids.size} selected",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite })
                    TextButton(onClick = {
                        if (selectedTracks.size == uiState.tracks.size) libraryViewModel.clearSelection()
                        else libraryViewModel.selectAll()
                    }, enabled = actionsEnabled) {
                        Text(if (selectedTracks.size == uiState.tracks.size) "Deselect all" else "Select all")
                    }
                }
            }
        },
        bottomBar = {
            val visible = uiState.isSelectionMode || uiState.pendingRemoval != null || snackbarHost.currentSnackbarData != null
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    .padding(bottom = if (visible) 12.dp + bottomPadding else 0.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SnackbarHost(snackbarHost) { data ->
                    GlassCard(Modifier.fillMaxWidth()) {
                        Text(data.visuals.message, Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite })
                    }
                }
                uiState.pendingRemoval?.let { pending ->
                    GlassCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (pending.playlist != null) "${pending.tracks.size} ${if (pending.tracks.size == 1) "song" else "songs"} removed from ${pending.playlist.name}"
                                else "${pending.tracks.size} ${if (pending.tracks.size == 1) "song" else "songs"} removed from library",
                                Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                                style = MaterialTheme.typography.bodyMedium
                            )
                            TextButton(onClick = { libraryViewModel.undoDelete() },
                                enabled = !uiState.isRemovalCommitting, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text(if (uiState.isRemovalCommitting) "Removing…" else "Undo")
                            }
                        }
                    }
                }
                if (uiState.isSelectionMode) LibrarySelectionActions(
                    enabled = actionsEnabled && selectedTracks.isNotEmpty(),
                    onPlayNext = { queueSongs(selectedTracks, true) },
                    onPlaylist = { tracksForPlaylistDialog = selectedTracks },
                    onMore = { tracksForActions = selectedTracks }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {


            // Enhanced Filter Row
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // 1. All Tracks
                item {
                    GlassFilterChip(
                        selected = uiState.selectedPlaylist == null && !uiState.showFavoritesOnly,
                        onClick = { libraryViewModel.clearSelection(); libraryViewModel.loadAllTracks() },
                        label = { Text("All Tracks") },
                        leadingIcon = if (uiState.selectedPlaylist == null && !uiState.showFavoritesOnly) {
                            {
                                Icon(
                                    JukeIcons.Library,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else null
                    )
                }

                // 2. Favourites
                item {
                    GlassFilterChip(
                        selected = uiState.showFavoritesOnly,
                        onClick = { libraryViewModel.clearSelection(); libraryViewModel.toggleFavoritesFilter() },
                        label = { Text("Favourites") },
                        leadingIcon = if (uiState.showFavoritesOnly) {
                            {
                                Icon(
                                    JukeIcons.HeartSelected,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else {
                            {
                                Icon(
                                    JukeIcons.Heart,
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

                    GlassFilterChip(
                        selected = uiState.selectedPlaylist?.id == playlist.id,
                        onClick = { libraryViewModel.clearSelection(); libraryViewModel.loadPlaylistTracks(playlist.id) },
                        label = {
                            val st = importStatus[playlist.id]
                            Text(if (st != null) "${playlist.name} · ${st.done}/${st.total}" else playlist.name)
                        },
                        modifier = if (importStatus.containsKey(playlist.id)) Modifier.alpha(0.55f) else Modifier,
                        leadingIcon = if (uiState.selectedPlaylist?.id == playlist.id) {
                            {
                                Icon(
                                    JukeIcons.Queue,
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
                                        JukeIcons.More,
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
                                                JukeIcons.Queue,
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
                                                JukeIcons.Delete,
                                                contentDescription = null
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    )

                    if (showDeleteDialog) {
                        GlassAlertDialog(
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
                    GlassFilterChip(selected = false,
                        onClick = {
                            importLauncher.launch(arrayOf("audio/*"))
                        },
                        label = { Text("Import") },
                        leadingIcon = {
                            Icon(
                                JukeIcons.AddCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }

                // 5. Create Playlist Button
                item {
                    GlassFilterChip(selected = false,
                        onClick = { showCreatePlaylistDialog = true },
                        label = { Text("New") },
                        leadingIcon = {
                            Icon(
                                JukeIcons.Add,
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
                            GlassAlertDialog(
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

                    if (!uiState.isSelectionMode) LibraryPlaybackActions(
                        musicViewModel = musicViewModel,
                        tracks = preparedLibraryTracks,
                        isShufflePrepared = isLibraryShufflePrepared,
                        onToggleShuffle = {
                            val shouldEnable = !isLibraryShufflePrepared
                            isLibraryShufflePrepared = shouldEnable
                            preparedLibraryShuffleIds = if (shouldEnable) {
                                currentLibraryTrackIds.shuffled()
                            } else {
                                emptyList()
                            }
                        }
                    )
                }
            }

            if (uiState.isLoading) {
                TrackListSkeleton(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp + bottomPadding)
                )
            } else if (uiState.tracks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Icon(
                                imageVector = if (uiState.searchQuery.isNotEmpty()) Icons.Outlined.SearchOff
                                else if (uiState.showFavoritesOnly) JukeIcons.Heart
                                else JukeIcons.Library,
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
                        bottom = 100.dp + bottomPadding
                    ),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Playlist hero if one is selected
                    uiState.selectedPlaylist?.let { playlist ->
                        item(key = "playlist_header_${playlist.id}") {
                            PlaylistHeader(
                                importStatus = importStatus[playlist.id],
                                playlist = playlist,
                                onEditClick = { showEditPlaylistDialog = true }
                            )
                        }
                    }

                    // Compact download banner — collapses all active downloads into one slim row
                    val hasAnyDownload = libraryDownloadBannerState.currentDownload != null ||
                            libraryDownloadBannerState.downloadQueue.isNotEmpty() ||
                            uiState.recommendationDownloads.isNotEmpty()
                    if (hasAnyDownload) {
                        item(key = "download_banner") {
                            CompactDownloadBanner(
                                currentDownload = libraryDownloadBannerState.currentDownload,
                                downloadQueue = libraryDownloadBannerState.downloadQueue,
                                recommendationDownloads = uiState.recommendationDownloads,
                                onCancelDownload = { id -> musicViewModel.cancelDownload(id) },
                                onRetryDownload = { item -> musicViewModel.retryFailedDownload(item) }
                            )
                        }
                    }

                    // Show downloaded tracks
                    itemsIndexed(
                        items = uiState.tracks,
                        key = { index, track -> "${track.uuid}_$index" }
                    ) { _, track ->
                        SwipeToAddNextContainer(
                            onAddNext = { queueSongs(listOf(track), true) },
                            onDelete = { tracksForDelete = listOf(track) },
                            enabled = !uiState.isSelectionMode && actionsEnabled
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
                                trailingIcon = JukeIcons.More,
                                onTrailingIconClick = { tracksForActions = listOf(track) }

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

    tracksForActions?.let { tracks ->
        LibraryTrackActionsSheet(
            title = if (tracks.size == 1) tracks.first().title else "${tracks.size} songs selected",
            subtitle = if (tracks.size == 1) tracks.first().artist else "Choose an action for these songs",
            playlistName = uiState.selectedPlaylist?.name,
            enabled = actionsEnabled,
            onPlayNext = { queueSongs(tracks, true) },
            onQueueEnd = { queueSongs(tracks, false) },
            onPlaylist = { tracksForActions = null; tracksForPlaylistDialog = tracks },
            onRemoveFromPlaylist = {
                uiState.selectedPlaylist?.let { libraryViewModel.stageRemoval(tracks, it) }
                tracksForActions = null
            },
            onDelete = { tracksForActions = null; tracksForDelete = tracks },
            onDismiss = { if (!queueActionBusy) tracksForActions = null }
        )
    }

    tracksForDelete?.let { tracks ->
        GlassAlertDialog(
            onDismissRequest = { tracksForDelete = null },
            title = { Text("Delete ${tracks.size} ${if (tracks.size == 1) "song" else "songs"} from library?") },
            text = { Text("This removes downloaded files and entries from every playlist. You can undo for 5 seconds before deletion is permanent.") },
            confirmButton = {
                TextButton(onClick = {
                    libraryViewModel.stageRemoval(tracks)
                    tracksForDelete = null
                }, enabled = actionsEnabled,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text("Delete from library")
                }
            },
            dismissButton = { TextButton(onClick = { tracksForDelete = null }) { Text("Cancel") } }
        )
    }

    tracksForPlaylistDialog?.let { tracks ->
        LibraryPlaylistSheet(
            tracks = tracks,
            playlists = uiState.playlists,
            loadMembershipCounts = { libraryViewModel.playlistMembershipCounts(tracks) },
            onAdd = { playlist -> libraryViewModel.addTracksToPlaylist(playlist, tracks) },
            onCreateAndAdd = { name -> libraryViewModel.createPlaylistWithTracks(name, tracks) },
            onSuccess = { name, added ->
                tracksForPlaylistDialog = null
                libraryViewModel.clearSelection()
                val songs = "$added ${if (added == 1) "song" else "songs"}"
                libraryViewModel.showActionMessage(
                    if (added == 0) "Songs are already in $name" else "$songs added to $name"
                )
            },
            onDismiss = { tracksForPlaylistDialog = null }
        )
    }

    // Sort Bottom Sheet
    if (uiState.showSortSheet) {
        SortBottomSheet(
            currentSortStyle = uiState.sortOption,
            onSortSelected = { libraryViewModel.updateSortOption(it) },
            onDismissRequest = { libraryViewModel.toggleSortSheet() }
        )
    }


}

private data class LibraryDownloadBannerState(
    val currentDownload: DownloadItem?,
    val downloadQueue: List<DownloadItem>
)

@Composable
private fun LibraryPlaybackActions(
    musicViewModel: MusicViewModel,
    tracks: List<Track>,
    isShufflePrepared: Boolean,
    onToggleShuffle: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onToggleShuffle
        ) {
            Icon(
                imageVector = JukeIcons.Shuffle,
                contentDescription = "Shuffle",
                tint = if (isShufflePrepared) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }

        IconButton(
            onClick = {
                if (tracks.isNotEmpty()) {
                    musicViewModel.addToQueue(tracks)
                }
            },
            enabled = tracks.isNotEmpty()
        ) {
            Icon(
                imageVector = JukeIcons.Queue,
                contentDescription = "Add all to Queue",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        GlassButton(
            onClick = {
                if (tracks.isNotEmpty()) {
                    musicViewModel.setQueue(tracks, startIndex = 0)
                }
            },
            enabled = tracks.isNotEmpty()
        ) {
            Icon(
                imageVector = JukeIcons.Play,
                contentDescription = "Play all",
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Play")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortBottomSheet(
    currentSortStyle: SortOption,
    onSortSelected: (SortOption) -> Unit,
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    GlassModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Sort by",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                color = MaterialTheme.colorScheme.onSurface
            )

            val sortOptions = listOf(
                SortOption.TITLE to "Title",
                SortOption.RECENTLY_ADDED to "Recently Added",
                SortOption.ARTIST to "Artist",
                SortOption.LAST_PLAYED to "Last Played",
                SortOption.MOST_PLAYED to "Most Played"
            )

            sortOptions.forEach { (option, label) ->
                val isSelected = currentSortStyle == option

                ListItem(
                    headlineContent = {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    trailingContent = {
                        if (isSelected) {
                            Icon(
                                imageVector = JukeIcons.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSortSelected(option) }
                )
            }
        }
    }
}

@Composable
private fun PlaylistHeader(
    importStatus: com.example.juke.services.ImportStatus?,
    playlist: com.example.juke.database.PlaylistEntity,
    onEditClick: () -> Unit
) {
    Column {
    GlassCard(
        modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            GlassCard(
                modifier = Modifier.size(96.dp)) {
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
                GlassButton(
                    onClick = onEditClick,
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
                        imageVector = JukeIcons.Queue,
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
    if (importStatus != null) {
        Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(
                if (importStatus.waitingForNetwork) "Importing ${importStatus.done}/${importStatus.total} · waiting for network…"
                else "Importing ${importStatus.done}/${importStatus.total}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            androidx.compose.material3.LinearProgressIndicator(
                progress = { if (importStatus.total > 0) importStatus.done.toFloat() / importStatus.total else 0f },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )
        }
    }
    }
}
