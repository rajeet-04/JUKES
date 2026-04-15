package com.example.juke.ui.screens

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Radio
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.juke.database.PlaylistEntity
import com.example.juke.models.Track
import com.example.juke.ui.components.AddToPlaylistDialog
import com.example.juke.ui.components.CreatePlaylistDialog
import com.example.juke.ui.components.player.PlayerArtwork
import com.example.juke.ui.components.player.PlayerControls
import com.example.juke.ui.components.player.PlayerProgress
import com.example.juke.ui.components.player.QueueBottomSheetContent
import com.example.juke.utils.BlacklistManager
import com.example.juke.utils.LyricsRomanizer
import com.example.juke.utils.rememberJukeHaptics
import com.example.juke.viewmodels.LibraryViewModel
import com.example.juke.viewmodels.MusicViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Re-export LyricLine for compatibility if needed elsewhere, 
// though it should ideally be in a model file.
data class LyricLine(
    val timeMs: Long,
    val text: String
)

// Helper moved to top level or util file, keeping here for now to avoid breaking changes if used elsewhere
fun parseSyncedLyrics(syncedLyrics: String, offsetMs: Long = 0L): List<LyricLine> {
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
            val adjustedTimeMs = (timeMs + offsetMs).coerceAtLeast(0L)
            if (text.isNotBlank()) {
                lines.add(LyricLine(adjustedTimeMs, text))
            }
        }
    }

    return lines.sortedBy { it.timeMs }
}

@SuppressLint("ConfigurationScreenWidthHeight")
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
    var showBlacklistPicker by remember { mutableStateOf(false) }
    val romanizeLyrics by musicViewModel.isRomanizedLyricsEnabled.collectAsState()
    val sleepTimerRemaining by musicViewModel.sleepTimerRemaining.collectAsState()
    val haptic = rememberJukeHaptics()

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

    var romanizedTrack by remember(
        currentTrack.uuid,
        romanizeLyrics
    ) { mutableStateOf<Track?>(null) }

    LaunchedEffect(currentTrack, romanizeLyrics) {
        if (!romanizeLyrics) {
            romanizedTrack = null
            return@LaunchedEffect
        }

        val romanizedSynced = currentTrack.syncedLyrics?.let {
            LyricsRomanizer.romanizeSyncedLyrics(it)
        }
        val romanizedPlain = currentTrack.plainLyrics?.let {
            LyricsRomanizer.romanizeText(it)
        }
        romanizedTrack = currentTrack.copy(
            syncedLyrics = romanizedSynced,
            plainLyrics = romanizedPlain
        )
    }

    val displayTrack = if (romanizeLyrics) romanizedTrack ?: currentTrack else currentTrack

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
                .background(Color.Black)
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

            // Content — fully responsive, adapts to screen height
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                val screenH = maxHeight
                // Scale breakpoints: tight (<640dp), normal (640-800dp), large (>800dp)
                val isCompact = screenH < 640.dp
                val spacerSm = if (isCompact) 8.dp else if (screenH < 800.dp) 16.dp else 24.dp
                val spacerMd = if (isCompact) 12.dp else if (screenH < 800.dp) 24.dp else 36.dp
                val actionIconSize = if (isCompact) 18.dp else 24.dp
                val actionBtnSize = if (isCompact) 36.dp else 48.dp
                val ctrlPlaySize = if (isCompact) 60.dp else if (isTablet) 88.dp else 72.dp
                val ctrlBtnSize = if (isCompact) 44.dp else if (isTablet) 72.dp else 56.dp
                val ctrlIconSize = if (isCompact) 28.dp else if (isTablet) 56.dp else 40.dp
                val ctrlSmallIconSize = if (isCompact) 18.dp else if (isTablet) 32.dp else 24.dp
                val artworkFraction =
                    if (isCompact) 0.38f else if (screenH < 800.dp) 0.42f else 0.45f
                val titleStyle =
                    if (isCompact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium
                val subtitleStyle =
                    if (isCompact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium
                val bottomPadding = if (isCompact) 16.dp else 40.dp

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
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
                        onToggleRomanizedLyrics = { musicViewModel.toggleRomanizedLyrics() },
                        isRomanizedLyricsEnabled = romanizeLyrics,
                        showMenuOption = true,
                        isAlbumAvailable = currentTrack.albumSpotifyId != null,
                        currentArtist = currentTrack.artist,
                        onShowBlacklistPicker = { showBlacklistPicker = true }
                    )

                    Spacer(modifier = Modifier.height(spacerSm))

                    // Artwork — fills a portion of screen height
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(screenH * artworkFraction),
                        contentAlignment = Alignment.Center
                    ) {
                        PlayerArtwork(
                            currentTrack = displayTrack,
                            currentPosition = uiState.position,
                            showLyrics = showLyrics,
                            musicViewModel = musicViewModel,
                            isTablet = isTablet,
                            onToggleLyrics = { showLyrics = !showLyrics }
                        )
                    }

                    Spacer(modifier = Modifier.height(spacerMd))

                    // Track Info & Action icons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentTrack.title,
                                style = titleStyle.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                maxLines = 1,
                                modifier = Modifier.basicMarquee()
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = currentTrack.artist,
                                style = subtitleStyle,
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

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (currentTrack.isStream) {
                                IconButton(
                                    onClick = { musicViewModel.promoteTrackToDownload(currentTrack) },
                                    modifier = Modifier.size(actionBtnSize)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = "Download",
                                        tint = Color.White,
                                        modifier = Modifier.size(actionIconSize)
                                    )
                                }
                            }
                            IconButton(
                                onClick = { showAddToPlaylistDialog = currentTrack },
                                modifier = Modifier.size(actionBtnSize)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddCircle,
                                    contentDescription = "Add to Playlist",
                                    tint = Color.White,
                                    modifier = Modifier.size(actionIconSize)
                                )
                            }
                            IconButton(
                                onClick = {
                                    haptic.confirm()
                                    musicViewModel.toggleFavorite(currentTrack)
                                },
                                modifier = Modifier.size(actionBtnSize)
                            ) {
                                Icon(
                                    imageVector = if (currentTrack.isFavourite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = if (currentTrack.isFavourite) "Unfavorite" else "Favorite",
                                    tint = if (currentTrack.isFavourite) MaterialTheme.colorScheme.primary else Color.White,
                                    modifier = Modifier.size(actionIconSize)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(spacerSm))

                    // Progress
                    PlayerProgress(
                        currentPosition = uiState.position,
                        uiState = uiState,
                        musicViewModel = musicViewModel
                    )

                    Spacer(modifier = Modifier.height(spacerSm))

                    // Controls
                    PlayerControls(
                        uiState = uiState,
                        musicViewModel = musicViewModel,
                        isLarge = isTablet,
                        playButtonSize = ctrlPlaySize,
                        buttonSize = ctrlBtnSize,
                        iconSize = ctrlIconSize,
                        smallIconSize = ctrlSmallIconSize
                    )

                    Spacer(modifier = Modifier.height(spacerSm))

                    // Bottom Action Row (Queue, Radio, Share)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = bottomPadding)
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    if (dragAmount.y < -50) showQueue = true
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .clickable {
                                        haptic.click()
                                        showQueue = true
                                    }
                                    .padding(if (isCompact) 8.dp else 12.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.List,
                                    "Queue",
                                    tint = Color.White,
                                    modifier = Modifier.size(actionIconSize)
                                )
                                Text(
                                    "Queue",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .clickable {
                                        haptic.click()
                                        musicViewModel.startRadio()
                                    }
                                    .padding(if (isCompact) 8.dp else 12.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Radio,
                                    "Radio",
                                    tint = Color.White,
                                    modifier = Modifier.size(actionIconSize)
                                )
                                Text(
                                    "Radio",
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
                                            haptic.click()
                                            onShareTrack(currentTrack.spotifyId)
                                        }
                                        .padding(if (isCompact) 8.dp else 12.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Share,
                                        "Share",
                                        tint = Color.White,
                                        modifier = Modifier.size(actionIconSize)
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
            tracks = listOf(track),
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

    // Blacklist Artist Picker Dialog
    if (showBlacklistPicker && currentTrack != null) {
        BlacklistPickerDialog(
            artistString = currentTrack.artist,
            onDismiss = { showBlacklistPicker = false }
        )
    }
}

@Composable
fun PlayerHeader(
    onDismiss: () -> Unit,
    onShowSleepTimer: () -> Unit,
    onNavigateToAlbum: () -> Unit,
    onRefreshLyrics: () -> Unit,
    onToggleRomanizedLyrics: () -> Unit,
    isRomanizedLyricsEnabled: Boolean,
    showMenuOption: Boolean,
    isAlbumAvailable: Boolean,
    currentArtist: String = "",
    onShowBlacklistPicker: () -> Unit = {}
) {
    val context = LocalContext.current
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
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (isRomanizedLyricsEnabled) {
                                    "Romanized Lyrics: On"
                                } else {
                                    "Romanized Lyrics: Off"
                                }
                            )
                        },
                        onClick = {
                            showMenu = false
                            onToggleRomanizedLyrics()
                        }
                    )
                    // Artist Blacklist option
                    if (currentArtist.isNotBlank()) {
                        val hasBlacklisted = remember(currentArtist, showMenu) {
                            BlacklistManager.containsBlacklistedArtist(context, currentArtist)
                        }
                        DropdownMenuItem(
                            text = { Text(if (hasBlacklisted) "Manage Blocked Artists" else "Block Artist") },
                            onClick = {
                                showMenu = false
                                onShowBlacklistPicker()
                            }
                        )
                    }
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

@Composable
fun BlacklistPickerDialog(
    artistString: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    // Parse individual artist names
    val artists = remember(artistString) {
        artistString
            .replace(" feat. ", ", ")
            .replace(" ft. ", ", ")
            .replace(" & ", ", ")
            .replace(" and ", ", ")
            .replace(";", ",")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    // Track blocked state per artist
    var blockedMap by remember(artistString) {
        mutableStateOf(
            artists.associateWith { name ->
                BlacklistManager.containsBlacklistedArtist(context, name)
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Block Artists") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Blocked artists won't appear in recommendations.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                artists.forEach { name ->
                    val isBlocked = blockedMap[name] == true
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        androidx.compose.material3.Switch(
                            checked = isBlocked,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    BlacklistManager.addArtist(context, name)
                                } else {
                                    BlacklistManager.removeArtist(context, name)
                                }
                                blockedMap = blockedMap.toMutableMap().apply {
                                    put(name, checked)
                                }
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}