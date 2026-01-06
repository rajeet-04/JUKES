package com.example.juke.ui.screens

import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.juke.models.Track
import com.example.juke.ui.components.player.PlayerArtwork
import com.example.juke.ui.components.player.PlayerControls
import com.example.juke.ui.components.player.PlayerProgress
import com.example.juke.ui.components.player.QueueBottomSheetContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.juke.database.PlaylistEntity
import com.example.juke.ui.components.AddToPlaylistDialog
import com.example.juke.ui.components.CreatePlaylistDialog
import com.example.juke.viewmodels.LibraryViewModel
import com.example.juke.viewmodels.MusicViewModel
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.AddCircle
import kotlinx.coroutines.delay

// Re-export LyricLine for compatibility if needed elsewhere, 
// though it should ideally be in a model file.
data class LyricLine(
    val timeMs: Long,
    val text: String
)

// Helper moved to top level or util file, keeping here for now to avoid breaking changes if used elsewhere
fun parseSyncedLyrics(syncedLyrics: String): List<LyricLine> {
    val lines = mutableListOf<LyricLine>()
    val regex = """\[(\d{2}):(\d{2})\.(\d{1,3})]\s*(.*)""".toRegex()

    syncedLyrics.lines().forEach { line ->
        regex.find(line)?.let { match ->
            val minutes = match.groupValues[1].toLongOrNull() ?: 0L
            val seconds = match.groupValues[2].toLongOrNull() ?: 0L
            val frac = match.groupValues[3]
            var text = match.groupValues[4]

            // Basic cleanup of lrc artifact chars
            text = text.trim()

            val millisFromFrac = when (frac.length) {
                3 -> frac.toLongOrNull() ?: 0L
                2 -> (frac.toLongOrNull() ?: 0L) * 10L
                1 -> (frac.toLongOrNull() ?: 0L) * 100L
                else -> frac.padEnd(3, '0').take(3).toLongOrNull() ?: 0L
            }

            val timeMs = (minutes * 60 * 1000) + (seconds * 1000) + millisFromFrac
            if (text.isNotBlank()) {
                lines.add(LyricLine(timeMs, text))
            }
        }
    }

    return lines.sortedBy { it.timeMs }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    musicViewModel: MusicViewModel,
    libraryViewModel: LibraryViewModel = viewModel(),
    onDismiss: () -> Unit,
    onNavigateToArtist: (String) -> Unit,
    onNavigateToAlbum: (String) -> Unit,
    onShareTrack: (String) -> Unit
) {
    BackHandler(onBack = onDismiss)

    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    val uiState by musicViewModel.uiState.collectAsState()
    val currentTrack = uiState.currentTrack
    var showQueue by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showArtistSelectionSheet by remember { mutableStateOf(false) }
    val sleepTimerRemaining by musicViewModel.sleepTimerRemaining.collectAsState()

    var showAddToPlaylistDialog by remember { mutableStateOf<Track?>(null) }
    var showNewPlaylistDialog by remember { mutableStateOf(false) }
    var trackPlaylists by remember {
        mutableStateOf<List<PlaylistEntity>>(emptyList())
    }
    val libraryUiState by libraryViewModel.uiState.collectAsState()

    // Fetch playlists for the selected track when dialog opens
    LaunchedEffect(showAddToPlaylistDialog) {
        showAddToPlaylistDialog?.let { track ->
            trackPlaylists = libraryViewModel.getPlaylistsForTrack(track.uuid)
        }
    }

    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    configuration.screenHeightDp.dp
    val isTablet = screenWidth >= 600.dp

    LaunchedEffect(Unit) {
        while (true) {
            musicViewModel.updateProgress()
            delay(300)
        }
    }

    if (currentTrack == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No track playing")
        }
        return
    }

    // Modal Sheet for Player
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.Transparent, // Transparent to show immersive background
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxSize(),
        dragHandle = null
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    var totalDrag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { totalDrag = 0f },
                        onDragEnd = {
                            if (totalDrag < -150f) {
                                musicViewModel.skipToNext()
                            } else if (totalDrag > 150f) {
                                musicViewModel.skipToPrevious()
                            }
                            totalDrag = 0f
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        totalDrag += dragAmount
                    }
                }
        ) {
            // Immersive Background
            if (currentTrack.thumbnailUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(currentTrack.thumbnailUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(50.dp),
                    contentScale = ContentScale.Crop
                )
            }
            // Gradient Overlay for readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.3f),
                                Color.Black.copy(alpha = 0.7f),
                                Color.Black.copy(alpha = 0.9f)
                            )
                        )
                    )
            )

            // Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                PlayerHeader(
                    onDismiss = onDismiss,
                    onShowSleepTimer = { showSleepTimerDialog = true },
                    onNavigateToAlbum = {
                        if (currentTrack.albumSpotifyId != null) onNavigateToAlbum(
                            currentTrack.albumSpotifyId
                        )
                    },
                    onRefreshLyrics = { musicViewModel.refreshLyrics(currentTrack) },
                    showMenuOption = true,
                    isAlbumAvailable = currentTrack.albumSpotifyId != null
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Artwork
                PlayerArtwork(
                    currentTrack = currentTrack,
                    currentPosition = uiState.position,
                    showLyrics = showLyrics,
                    musicViewModel = musicViewModel,
                    isTablet = isTablet,
                    onToggleLyrics = { showLyrics = !showLyrics },
                    modifier = Modifier.weight(1f, fill = false)
                )

                Spacer(modifier = Modifier.height(48.dp))

                // Track Info & Favorite
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentTrack.title,
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = currentTrack.artist,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            modifier = Modifier
                                .basicMarquee()
                                .clickable {
                                    val ids = currentTrack.artistSpotifyIds
                                    if (!ids.isNullOrEmpty()) {
                                        if (ids.size == 1) onNavigateToArtist(ids[0])
                                        else showArtistSelectionSheet = true
                                    }
                                }
                        )
                    }

                    // Favorite Button (Right side)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = { showAddToPlaylistDialog = currentTrack },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddCircle,
                                contentDescription = "Add to Playlist",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        IconButton(
                            onClick = { musicViewModel.toggleFavorite(currentTrack) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = if (currentTrack.isFavourite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = if (currentTrack.isFavourite) "Remove from favorites" else "Add to favorites",
                                tint = if (currentTrack.isFavourite) MaterialTheme.colorScheme.primary else Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Progress
                PlayerProgress(
                    currentPosition = uiState.position,
                    uiState = uiState,
                    musicViewModel = musicViewModel
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Controls
                PlayerControls(
                    uiState = uiState,
                    musicViewModel = musicViewModel,
                    isLarge = isTablet
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Bottom Action Row (Queue & Share)
                // Wrap in Box to capture swipe gestures
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 48.dp)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                if (dragAmount.y < -50) { // Swipe up
                                    showQueue = true
                                }
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .clickable {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    showQueue = true
                                }
                                .padding(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.List,
                                contentDescription = "Queue",
                                tint = Color.White
                            )
                            Text(
                                "Queue",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                        }

                        if (currentTrack.spotifyId != null) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .clickable {
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        onShareTrack(currentTrack.spotifyId)
                                    }
                                    .padding(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Share,
                                    contentDescription = "Share",
                                    tint = Color.White
                                )
                                Text(
                                    "Share",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Queue Sheet
    if (showQueue) {
        ModalBottomSheet(
            onDismissRequest = { showQueue = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxSize(),
            dragHandle = null
        ) {
            QueueBottomSheetContent(
                currentTrack = currentTrack,
                queue = uiState.queue,
                queueIndex = uiState.queueIndex,
                uiState = uiState,
                onClose = { showQueue = false },
                onMoveTrack = { from, to -> musicViewModel.moveInQueue(from, to) },
                onRemoveTrack = { id -> musicViewModel.removeFromQueue(id) },
                onPlayTrack = { track -> musicViewModel.playTrackFromQueue(track) }
            )
        }
    }

    // Artist Selection Sheet
    if (showArtistSelectionSheet) {
        ModalBottomSheet(
            onDismissRequest = { showArtistSelectionSheet = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            ArtistSelectionContent(
                currentTrack = currentTrack,
                onArtistSelected = { id ->
                    showArtistSelectionSheet = false
                    onNavigateToArtist(id)
                }
            )
        }
    }

    // Sleep Timer Dialog
    if (showSleepTimerDialog) {
        SleepTimerDialog(
            currentTimerRemaining = sleepTimerRemaining,
            onDismiss = { showSleepTimerDialog = false },
            onSetTimer = { minutes ->
                if (minutes > 0) musicViewModel.startSleepTimer(minutes)
                else musicViewModel.cancelSleepTimer()
                showSleepTimerDialog = false
            },
            onCancelTimer = {
                musicViewModel.cancelSleepTimer()
                showSleepTimerDialog = false
            }
        )
    }

    showAddToPlaylistDialog?.let { track ->
        AddToPlaylistDialog(
            playlists = libraryUiState.playlists, // Use playlists from LibraryViewModel state
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
            onCreatePlaylist = { showNewPlaylistDialog = true }
        )
    }

    if (showNewPlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { showNewPlaylistDialog = false },
            onCreate = { name ->
                libraryViewModel.createPlaylist(name)
                showNewPlaylistDialog = false
            }
        )
    }
}

@Composable
fun PlayerHeader(
    onDismiss: () -> Unit,
    onShowSleepTimer: () -> Unit,
    onNavigateToAlbum: () -> Unit,
    onRefreshLyrics: () -> Unit,
    showMenuOption: Boolean,
    isAlbumAvailable: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Close",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }

        Text(
            "Now Playing",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.9f)
        )

        if (showMenuOption) {
            var showMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        "Menu",
                        tint = Color.White
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Sleep Timer") },
                        onClick = {
                            showMenu = false
                            onShowSleepTimer()
                        }
                    )
                    if (isAlbumAvailable) {
                        DropdownMenuItem(
                            text = { Text("Go to Album") },
                            onClick = {
                                showMenu = false
                                onNavigateToAlbum()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Refresh Lyrics") },
                        onClick = {
                            showMenu = false
                            onRefreshLyrics()
                        }
                    )
                }
            }
        } else {
            Spacer(modifier = Modifier.size(48.dp))
        }
    }
}

@Composable
fun ArtistSelectionContent(
    currentTrack: Track,
    onArtistSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp)
    ) {
        Text(
            text = "Select Artist",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
        )

        val artistNames =
            remember(currentTrack.artist) { currentTrack.artist.split(", ").map { it.trim() } }
        val ids = currentTrack.artistSpotifyIds ?: emptyList()

        ids.forEachIndexed { index, id ->
            val name = artistNames.getOrElse(index) { "Artist ${index + 1}" }
            ListItem(
                headlineContent = { Text(name) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onArtistSelected(id) }
            )
        }
    }
}

@Composable
fun SleepTimerDialog(
    currentTimerRemaining: Long?,
    onDismiss: () -> Unit,
    onSetTimer: (Int) -> Unit,
    onCancelTimer: () -> Unit
) {
    var sliderValue by remember {
        mutableFloatStateOf(currentTimerRemaining?.let { it / 60000f } ?: 0f)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep Timer") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (currentTimerRemaining != null) {
                    val minutes = (currentTimerRemaining / 1000 / 60).toInt()
                    val seconds = ((currentTimerRemaining / 1000) % 60).toInt()

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Playback will pause in:",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                String.format(
                                    java.util.Locale.getDefault(),
                                    "%02d:%02d",
                                    minutes,
                                    seconds
                                ),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                } else {
                    Text(
                        if (sliderValue > 0) "${sliderValue.toInt()} minutes" else "Timer off",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        valueRange = 0f..180f,
                        steps = 179,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            if (currentTimerRemaining != null) {
                TextButton(onClick = onCancelTimer) { Text("Cancel Timer") }
            } else {
                TextButton(
                    onClick = { onSetTimer(sliderValue.toInt()) },
                    enabled = sliderValue > 0
                ) { Text("Set Timer") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (currentTimerRemaining != null) "Close" else "Cancel")
            }
        }
    )
}