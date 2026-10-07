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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
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
import com.example.juke.ui.components.GlassIconButton
import com.example.juke.ui.components.GlassModalBottomSheet
import com.example.juke.ui.components.LibraryPlaylistSheet
import com.example.juke.ui.components.LibrarySelectionActions
import com.example.juke.ui.components.LibraryTrackActionsSheet
import com.example.juke.ui.components.LibraryTrackItem
import com.example.juke.ui.components.SearchHeader
import com.example.juke.ui.components.SwipeToAddNextContainer
import com.example.juke.ui.components.TrackListSkeleton
import com.example.juke.ui.icons.JukeIcons
import com.example.juke.ui.theme.GlassCard
import com.example.juke.ui.theme.glassPane
import com.example.juke.utils.rememberJukeHaptics
import com.example.juke.viewmodels.DownloadItem
import com.example.juke.viewmodels.LibraryViewModel
import com.example.juke.viewmodels.MusicViewModel
import com.example.juke.viewmodels.SortOption
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    musicViewModel: MusicViewModel,
    libraryViewModel: LibraryViewModel = viewModel(),
    bottomPadding: Dp = 0.dp,
    onAlbumClick: (com.example.juke.models.SpotifyAlbum) -> Unit = {}
) {
    val uiState by libraryViewModel.uiState.collectAsStateWithLifecycle()
    val offlineAlbums by musicViewModel.offlineAlbums.albums.collectAsStateWithLifecycle()
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
    var librarySection by rememberSaveable { mutableStateOf(0) }
    var showSongsFilter by remember { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showLibraryMenu by remember { mutableStateOf(false) }
    var playlistToDelete by remember {
        mutableStateOf<com.example.juke.database.PlaylistEntity?>(
            null
        )
    }
    androidx.activity.compose.BackHandler(enabled = librarySection == 1 && uiState.selectedPlaylist != null && !uiState.isSelectionMode) {
        libraryViewModel.loadAllTracks()
    }
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
    val selectedTracks = uiState.tracks.filter { it.uuid in uiState.selectedTrackUuids }
    val actionsEnabled = !queueActionBusy && !uiState.isRemovalCommitting

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = {
            if (!uiState.isSelectionMode) {
                SearchHeader(
                    title = "Library",
                    query = uiState.searchQuery,
                    onQueryChange = { libraryViewModel.updateSearchQuery(it) },
                    open = searchOpen || uiState.searchQuery.isNotEmpty(),
                    onOpenChange = { searchOpen = it },
                    placeholder = "Search your library"
                ) {
                    Box {
                        IconButton(onClick = { showAddMenu = true }) {
                            Icon(
                                JukeIcons.Add,
                                "Add to library"
                            )
                        }
                        DropdownMenu(
                            expanded = showAddMenu,
                            onDismissRequest = { showAddMenu = false }) {
                            DropdownMenuItem(text = { Text("Import audio") }, onClick = {
                                showAddMenu = false
                                importLauncher.launch(arrayOf("audio/*"))
                            })
                            DropdownMenuItem(text = { Text("Create playlist") }, onClick = {
                                showAddMenu = false
                                showCreatePlaylistDialog = true
                            })
                        }
                    }
                    Box {
                        IconButton(onClick = { showLibraryMenu = true }) {
                            Icon(
                                JukeIcons.More,
                                "Library options"
                            )
                        }
                        DropdownMenu(
                            expanded = showLibraryMenu,
                            onDismissRequest = { showLibraryMenu = false }) {
                            if (librarySection == 0 || (librarySection == 1 && uiState.selectedPlaylist != null)) {
                                DropdownMenuItem(
                                    text = { Text("${uiState.tracks.size} songs") },
                                    enabled = false,
                                    onClick = {})
                                DropdownMenuItem(text = { Text("Sort songs") }, onClick = {
                                    showLibraryMenu = false
                                    libraryViewModel.toggleSortSheet()
                                })
                                DropdownMenuItem(
                                    text = { Text("Play all") },
                                    enabled = uiState.tracks.isNotEmpty(),
                                    onClick = {
                                        showLibraryMenu = false
                                        musicViewModel.setQueue(uiState.tracks, startIndex = 0)
                                    })
                                DropdownMenuItem(
                                    text = { Text("Shuffle all") },
                                    enabled = uiState.tracks.isNotEmpty(),
                                    onClick = {
                                        showLibraryMenu = false
                                        musicViewModel.setQueue(
                                            uiState.tracks.shuffled(),
                                            startIndex = 0
                                        )
                                    })
                                DropdownMenuItem(
                                    text = { Text("Add all to queue") },
                                    enabled = uiState.tracks.isNotEmpty(),
                                    onClick = {
                                        showLibraryMenu = false
                                        queueSongs(uiState.tracks, false)
                                    })
                            } else {
                                DropdownMenuItem(
                                    text = { Text(if (librarySection == 1) "${uiState.playlists.size} playlists" else "${offlineAlbums.size} saved albums") },
                                    enabled = false,
                                    onClick = {})
                            }
                        }
                    }
                }
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GlassIconButton(
                        onClick = { libraryViewModel.clearSelection() },
                        contentDescription = "Close selection", enabled = actionsEnabled
                    ) { Icon(JukeIcons.Close, null) }
                    Text("${uiState.selectedTrackUuids.size} selected",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { liveRegion = LiveRegionMode.Polite })
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
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = if (visible) 12.dp + bottomPadding else 0.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SnackbarHost(snackbarHost) { data ->
                    GlassCard(Modifier.fillMaxWidth()) {
                        Text(data.visuals.message, Modifier
                            .padding(16.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite })
                    }
                }
                uiState.pendingRemoval?.let { pending ->
                    GlassCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (pending.playlist != null) "${pending.tracks.size} ${if (pending.tracks.size == 1) "song" else "songs"} removed from ${pending.playlist.name}"
                                else "${pending.tracks.size} ${if (pending.tracks.size == 1) "song" else "songs"} removed from library",
                                Modifier
                                    .weight(1f)
                                    .semantics { liveRegion = LiveRegionMode.Polite },
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


            if (!uiState.isSelectionMode) {
                SecondaryTabRow(
                    selectedTabIndex = librarySection,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .glassPane(
                            com.example.juke.ui.theme.GlassShapes.Pill,
                            com.example.juke.ui.theme.GlassLevel.Thin
                        ),
                    containerColor = Color.Transparent,
                    divider = {}
                ) {
                    Tab(selected = librarySection == 0, onClick = {
                        if (librarySection == 0) showSongsFilter = true
                        else {
                            librarySection = 0
                            libraryViewModel.clearSelection()
                            libraryViewModel.loadAllTracks()
                        }
                    }, text = {
                        Box {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (uiState.showFavoritesOnly && librarySection == 0) "Favourites" else "Songs",
                                    maxLines = 1
                                )
                                Icon(Icons.Default.ArrowDropDown, null, Modifier.size(18.dp))
                            }
                            DropdownMenu(
                                expanded = showSongsFilter,
                                onDismissRequest = { showSongsFilter = false }) {
                                DropdownMenuItem(text = { Text("All songs") }, onClick = {
                                    showSongsFilter = false
                                    libraryViewModel.loadAllTracks()
                                })
                                DropdownMenuItem(text = { Text("Favourites") }, onClick = {
                                    showSongsFilter = false
                                    if (!uiState.showFavoritesOnly) libraryViewModel.toggleFavoritesFilter()
                                })
                            }
                        }
                    })
                    Tab(selected = librarySection == 1, onClick = {
                        librarySection = 1
                        libraryViewModel.clearSelection()
                        libraryViewModel.loadAllTracks()
                    }, text = { Text("Playlists") })
                    Tab(selected = librarySection == 2, onClick = {
                        librarySection = 2
                        libraryViewModel.clearSelection()
                        libraryViewModel.loadAllTracks()
                    }, text = { Text("Albums") })
                }
            }

            if (librarySection == 1 && uiState.selectedPlaylist == null) {
                val playlists = uiState.playlists.filter {
                    it.name.contains(
                        uiState.searchQuery,
                        ignoreCase = true
                    )
                }
                if (playlists.isEmpty()) {
                    LibraryCollectionEmpty(
                        title = if (uiState.searchQuery.isNotEmpty()) "No playlists found" else "No playlists yet",
                        description = if (uiState.searchQuery.isNotEmpty()) "Try another search." else "Create a playlist to keep your songs together.",
                        action = if (uiState.searchQuery.isEmpty()) "Create playlist" else null,
                        onAction = { showCreatePlaylistDialog = true }
                    )
                } else LazyColumn(contentPadding = PaddingValues(bottom = 24.dp + bottomPadding)) {
                    items(playlists, key = { it.id }) { playlist ->
                        var menuOpen by remember { mutableStateOf(false) }
                        ListItem(
                            headlineContent = {
                                Text(
                                    playlist.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            supportingContent = {
                                Text(importStatus[playlist.id]?.let { "Importing ${it.done}/${it.total}" }
                                    ?: "${playlist.trackCount} songs")
                            },
                            leadingContent = {
                                AsyncImage(
                                    model = playlist.thumbnailUri,
                                    contentDescription = null,
                                    modifier = Modifier.size(52.dp),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    placeholder = androidx.compose.ui.graphics.painter.ColorPainter(
                                        MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    error = androidx.compose.ui.graphics.painter.ColorPainter(
                                        MaterialTheme.colorScheme.surfaceVariant
                                    )
                                )
                            },
                            trailingContent = {
                                Box {
                                    IconButton(onClick = { menuOpen = true }) {
                                        Icon(
                                            JukeIcons.More,
                                            "Options for ${playlist.name}"
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = menuOpen,
                                        onDismissRequest = { menuOpen = false }) {
                                        DropdownMenuItem(
                                            text = { Text("Add to queue") },
                                            onClick = {
                                                menuOpen = false
                                                libraryViewModel.addPlaylistToQueue(
                                                    playlist,
                                                    musicViewModel
                                                )
                                            })
                                        DropdownMenuItem(
                                            text = { Text("Delete playlist") },
                                            onClick = {
                                                menuOpen = false
                                                playlistToDelete = playlist
                                            })
                                    }
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable {
                                libraryViewModel.loadPlaylistTracks(
                                    playlist.id
                                )
                            }
                        )
                    }
                }
            } else if (librarySection == 2) {
                val albums = offlineAlbums.filter {
                    it.album.name.contains(
                        uiState.searchQuery,
                        ignoreCase = true
                    )
                }
                if (albums.isEmpty()) LibraryCollectionEmpty(
                    title = if (uiState.searchQuery.isNotEmpty()) "No albums found" else "No saved albums yet",
                    description = if (uiState.searchQuery.isNotEmpty()) "Try another search." else "Albums you save for offline listening will appear here."
                ) else LazyColumn(contentPadding = PaddingValues(bottom = 24.dp + bottomPadding)) {
                    items(albums) { saved ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    saved.album.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            supportingContent = {
                                Text(
                                    saved.album.artists.joinToString(", ") { it.name },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingContent = {
                                AsyncImage(
                                    model = saved.album.images.firstOrNull()?.url,
                                    contentDescription = null,
                                    modifier = Modifier.size(52.dp),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { onAlbumClick(saved.album) }
                        )
                    }
                }
            } else if (uiState.isLoading) {
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


    playlistToDelete?.let { playlist ->
        GlassAlertDialog(
            onDismissRequest = { playlistToDelete = null },
            title = { Text("Delete playlist?") },
            text = { Text("Delete '${playlist.name}'? Your songs stay in the library.") },
            confirmButton = {
                TextButton(onClick = {
                    libraryViewModel.deletePlaylist(playlist); playlistToDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { playlistToDelete = null }) { Text("Cancel") } }
        )
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
        Column(Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)) {
            Text(
                if (importStatus.waitingForNetwork) "Importing ${importStatus.done}/${importStatus.total} · waiting for network…"
                else "Importing ${importStatus.done}/${importStatus.total}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            androidx.compose.material3.LinearProgressIndicator(
                progress = { if (importStatus.total > 0) importStatus.done.toFloat() / importStatus.total else 0f },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            )
        }
    }
    }
}

@Composable
private fun LibraryCollectionEmpty(
    title: String,
    description: String,
    action: String? = null,
    onAction: () -> Unit = {}
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            description, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
        )
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}
