package com.example.juke.ui.screens

import com.example.juke.ui.components.GlassAlertDialog
import androidx.compose.material3.ModalBottomSheet
import com.example.juke.ui.components.GlassIconButton
import com.example.juke.ui.components.GlassModalBottomSheet
import com.example.juke.ui.theme.GlassBackdrop
import com.example.juke.ui.theme.isGlassDark
import com.example.juke.ui.theme.GlassLevel
import com.example.juke.ui.theme.GlassShapes
import com.example.juke.ui.theme.GlassSurface
import com.example.juke.ui.theme.JUKETheme
import com.example.juke.ui.theme.glassSheetColor
import androidx.compose.ui.draw.alpha
import com.example.juke.ui.theme.GlassCard
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import android.annotation.SuppressLint
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.surfaceColorAtElevation
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.juke.R
import com.example.juke.database.PlaylistEntity
import com.example.juke.models.Track
import com.example.juke.ui.components.AddToPlaylistDialog
import com.example.juke.ui.components.CreatePlaylistDialog
import com.example.juke.ui.components.PlayerSkeleton
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
    val uiState by musicViewModel.uiState.collectAsStateWithLifecycle()
    val currentTrack = uiState.currentTrack
    var showQueue by remember { mutableStateOf(false) }
    val playbackSpeed by musicViewModel.playbackSpeed.collectAsStateWithLifecycle()
    var showLyrics by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showArtistSelectionSheet by remember { mutableStateOf(false) }
    var showBlacklistPicker by remember { mutableStateOf(false) }
    val romanizeLyrics by musicViewModel.isRomanizedLyricsEnabled.collectAsStateWithLifecycle()
    val sleepTimerRemaining by musicViewModel.sleepTimerRemaining.collectAsStateWithLifecycle()
    val haptic = rememberJukeHaptics()

    var showAddToPlaylistDialog by remember { mutableStateOf<Track?>(null) }
    var showNewPlaylistDialog by remember { mutableStateOf(false) }
    var trackPlaylists by remember {
        mutableStateOf<List<PlaylistEntity>>(emptyList())
    }
    val libraryUiState by libraryViewModel.uiState.collectAsStateWithLifecycle()

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

    val lifecycleOwner = LocalLifecycleOwner.current
    // Poll the position only while playing; paused, one read is enough (no 300 ms wakeups).
    LaunchedEffect(lifecycleOwner, uiState.isPlaying) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            musicViewModel.updateProgress()
            while (uiState.isPlaying) {
                delay(300)
                musicViewModel.updateProgress()
            }
        }
    }

    if (currentTrack == null) {
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                PlayerSkeleton()
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No track playing")
            }
        }
        return
    }

    var romanizedTrack by remember(
        currentTrack.uuid,
        romanizeLyrics,
        currentTrack.syncedLyrics,
        currentTrack.plainLyrics,
        currentTrack.romanizedSyncedLyrics,
        currentTrack.romanizedPlainLyrics
    ) { mutableStateOf<Track?>(null) }

    LaunchedEffect(
        currentTrack.uuid,
        currentTrack.syncedLyrics,
        currentTrack.plainLyrics,
        currentTrack.romanizedSyncedLyrics,
        currentTrack.romanizedPlainLyrics,
        romanizeLyrics
    ) {
        if (!romanizeLyrics) {
            romanizedTrack = null
            return@LaunchedEffect
        }

        // A saved result that still has non-Latin lines came from a failed request: redo it.
        val complete = LyricsRomanizer::isFullyRomanized
        // Failed lines (offline, rate-limited, network blocked in background) are retried a few
        // times; successful lines are cached in memory, so a retry only re-asks for the failures.
        for (attempt in 0..3) {
            if (attempt > 0) delay(5_000L * attempt)
            val romanizedSynced = currentTrack.romanizedSyncedLyrics?.takeIf(complete)
                ?: currentTrack.syncedLyrics?.let { LyricsRomanizer.romanizeSyncedLyrics(it) }
            val romanizedPlain = currentTrack.romanizedPlainLyrics?.takeIf(complete)
                ?: currentTrack.plainLyrics?.let { LyricsRomanizer.romanizeText(it) }
            romanizedTrack = currentTrack.copy(
                syncedLyrics = romanizedSynced ?: currentTrack.syncedLyrics,
                plainLyrics = romanizedPlain ?: currentTrack.plainLyrics,
                romanizedSyncedLyrics = romanizedSynced,
                romanizedPlainLyrics = romanizedPlain
            )

            val allDone = romanizedSynced?.let(complete) != false && romanizedPlain?.let(complete) != false
            if (!allDone) continue
            // Only a complete result is saved, so one bad fetch can't stick to the track forever.
            if (romanizedSynced != currentTrack.romanizedSyncedLyrics ||
                romanizedPlain != currentTrack.romanizedPlainLyrics
            ) {
                musicViewModel.persistRomanizedLyrics(
                    track = currentTrack,
                    romanizedSyncedLyrics = romanizedSynced,
                    romanizedPlainLyrics = romanizedPlain
                )
            }
            break
        }
    }

    val displayTrack = if (romanizeLyrics) romanizedTrack ?: currentTrack else currentTrack

    // The host owns expansion so artwork and player share one coordinate space.
    Box(Modifier.fillMaxSize()) {
        JUKETheme(darkTheme = true, extractedColors = uiState.extractedColors) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Plain black backdrop; the artwork is the only color on the screen.
            Box(Modifier.fillMaxSize().background(GlassBackdrop.color(isGlassDark())))
            // Content — fully responsive, adapts to screen height
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
            ) {
                val screenH = maxHeight
                // Scale breakpoints: tight (<640dp), normal (640-800dp), large (>800dp)
                val isCompact = screenH < 640.dp
                val spacerSm = if (isCompact) 8.dp else if (screenH < 800.dp) 16.dp else 24.dp
                if (isCompact) 12.dp else if (screenH < 800.dp) 24.dp else 36.dp
                val actionIconSize = if (isCompact) 18.dp else 24.dp
                val actionBtnSize = 48.dp
                // Fixed sizes for the bottom bar so they never shrink too small
                val actionBarIconSize = if (isCompact) 26.dp else 32.dp
                val ctrlPlaySize = if (isCompact) 60.dp else if (isTablet) 88.dp else 72.dp
                val ctrlBtnSize = if (isCompact) 48.dp else if (isTablet) 72.dp else 56.dp
                val ctrlIconSize = if (isCompact) 28.dp else if (isTablet) 56.dp else 40.dp
                val ctrlSmallIconSize = if (isCompact) 18.dp else if (isTablet) 32.dp else 24.dp
                val artworkFraction =
                    if (isCompact) 0.38f else if (screenH < 800.dp) 0.42f else 0.45f
                val titleStyle =
                    if (isCompact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium
                val subtitleStyle =
                    if (isCompact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium
                // Keep a safe bottom padding to avoid nav bar overlap
                val bottomPadding = if (isCompact) 16.dp else 24.dp

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
                        onRefetchSong = { musicViewModel.refetchTrack(currentTrack) },
                        canRefetchSong = currentTrack.spotifyId != null,
                        onToggleRomanizedLyrics = { musicViewModel.toggleRomanizedLyrics() },
                        isRomanizedLyricsEnabled = romanizeLyrics,
                        showMenuOption = true,
                        isAlbumAvailable = currentTrack.albumSpotifyId != null,
                        currentArtist = currentTrack.artist,
                        onShowBlacklistPicker = { showBlacklistPicker = true },
                        playbackSpeed = playbackSpeed,
                        onCycleSpeed = { musicViewModel.cyclePlaybackSpeed() }
                    )

                    Spacer(modifier = Modifier.height(spacerSm))

                    // Artwork — fills a portion of screen height
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(vertical = if (showLyrics) 0.dp else 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        PlayerArtwork(
                            queue = uiState.queue,
                            queueIndex = uiState.queueIndex,
                            currentTrack = displayTrack,
                            currentPosition = uiState.position,
                            showLyrics = showLyrics,
                            musicViewModel = musicViewModel,
                            isTablet = isTablet,
                            onToggleLyrics = { showLyrics = !showLyrics }
                        )
                    }

                    Spacer(modifier = Modifier.height(spacerSm))

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
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                modifier = Modifier.basicMarquee()
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = currentTrack.artist,
                                style = subtitleStyle,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
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
                                        tint = MaterialTheme.colorScheme.onSurface,
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
                                    tint = MaterialTheme.colorScheme.onSurface,
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
                                    tint = if (currentTrack.isFavourite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
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

                    // Bottom actions: bare icons, no container
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = bottomPadding)
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    if (dragAmount.y < -50) showQueue = true
                                }
                            },
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        PlayerAction(
                            icon = rememberVectorPainter(Icons.Outlined.Lyrics),
                            label = "Lyrics",
                            active = showLyrics,
                            iconSize = actionBarIconSize
                        ) { haptic.click(); showLyrics = !showLyrics }
                        PlayerAction(
                            icon = rememberVectorPainter(Icons.AutoMirrored.Filled.List),
                            label = "Queue",
                            iconSize = actionBarIconSize
                        ) { haptic.click(); showQueue = true }
                        PlayerAction(
                            icon = painterResource(id = R.drawable.baseline_mix),
                            label = "Mix",
                            iconSize = actionBarIconSize
                        ) { haptic.click(); musicViewModel.startRadio() }
                        if (currentTrack.spotifyId != null) {
                            PlayerAction(
                                icon = rememberVectorPainter(Icons.Filled.Share),
                                label = "Share",
                                iconSize = actionBarIconSize
                            ) { haptic.click(); onShareTrack(currentTrack.spotifyId) }
                        }
                    }
                }
            }
        }
        }
    }

    // Queue Sheet
    if (showQueue) {
        GlassModalBottomSheet(
            onDismissRequest = { showQueue = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            modifier = Modifier.fillMaxSize(),
            showHandle = false
        ) {
            val rec by musicViewModel.recStatus.collectAsStateWithLifecycle()
            QueueBottomSheetContent(
                statusText = when {
                    rec.resolving > 0 -> "Finding next songs… (${rec.resolving})"
                    rec.reserve > 0 -> "${rec.reserve} more songs ready from the radio"
                    else -> null
                },
                currentTrack = currentTrack,
                queue = uiState.queue,
                queueIndex = uiState.queueIndex,
                uiState = uiState,
                onClose = { showQueue = false },
                onMoveTrack = { from, to -> musicViewModel.moveInQueue(from, to) },
                onRemoveTrack = { id -> musicViewModel.removeFromQueue(id) },
                onPlayTrack = { track -> musicViewModel.playTrackFromQueue(track) },
                onShuffleUpcoming = { musicViewModel.applyQueueTool(com.example.juke.viewmodels.MusicViewModel.QueueTool.SHUFFLE_UPCOMING) },
                onSortUpcoming = { musicViewModel.applyQueueTool(com.example.juke.viewmodels.MusicViewModel.QueueTool.SORT_UPCOMING) },
                onClearPlayed = { musicViewModel.applyQueueTool(com.example.juke.viewmodels.MusicViewModel.QueueTool.CLEAR_PLAYED) },
                onSaveAsPlaylist = { musicViewModel.saveQueueAsPlaylist() }
            )
        }
    }

    // Artist Selection Sheet
    if (showArtistSelectionSheet) {
        GlassModalBottomSheet(
            onDismissRequest = { showArtistSelectionSheet = false },
            sheetState = rememberModalBottomSheetState()
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
    if (showBlacklistPicker) {
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
    onRefetchSong: () -> Unit = {},
    canRefetchSong: Boolean = false,
    onToggleRomanizedLyrics: () -> Unit,
    isRomanizedLyricsEnabled: Boolean,
    showMenuOption: Boolean,
    isAlbumAvailable: Boolean,
    currentArtist: String = "",
    onShowBlacklistPicker: () -> Unit = {},
    playbackSpeed: Float = 1f,
    onCycleSpeed: () -> Unit = {}
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassIconButton(onClick = onDismiss, contentDescription = "Close") {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(28.dp)
            )
        }

        Text(
            "Now Playing",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
        )

        if (showMenuOption) {
            var showMenu by remember { mutableStateOf(false) }
            Box {
                GlassIconButton(onClick = { showMenu = true }, contentDescription = "Menu") {
                    Icon(Icons.Default.MoreVert, contentDescription = null)
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    shape = GlassShapes.Control,
                    containerColor = glassSheetColor(),
                    tonalElevation = 0.dp,
                    shadowElevation = 12.dp
                ) {
                    DropdownMenuItem(
                        text = { Text("Speed ${if (playbackSpeed % 1f == 0f) playbackSpeed.toInt().toString() else playbackSpeed.toString()}×") },
                        leadingIcon = {
                            Icon(Icons.Outlined.Speed, contentDescription = null)
                        },
                        onClick = onCycleSpeed
                    )
                    DropdownMenuItem(
                        text = { Text("Sleep Timer") },
                        leadingIcon = {
                            Icon(Icons.Outlined.Timer, contentDescription = null)
                        },
                        onClick = {
                            showMenu = false
                            onShowSleepTimer()
                        }
                    )
                    if (isAlbumAvailable) {
                        DropdownMenuItem(
                            text = { Text("Go to Album") },
                            leadingIcon = {
                                Icon(Icons.Outlined.Album, contentDescription = null)
                            },
                            onClick = {
                                showMenu = false
                                onNavigateToAlbum()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Refresh Lyrics") },
                        leadingIcon = {
                            Icon(Icons.Outlined.Refresh, contentDescription = null)
                        },
                        onClick = {
                            showMenu = false
                            onRefreshLyrics()
                        }
                    )
                    if (canRefetchSong) {
                        DropdownMenuItem(
                            text = { Text("Wrong song? Refetch") },
                            leadingIcon = {
                                Icon(Icons.Outlined.Refresh, contentDescription = null)
                            },
                            onClick = {
                                showMenu = false
                                onRefetchSong()
                            }
                        )
                    }
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
                        leadingIcon = {
                            Icon(Icons.Outlined.Translate, contentDescription = null)
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
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.Block,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
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

    GlassAlertDialog(
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

                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
        accent = MaterialTheme.colorScheme.primaryContainer) {
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

    GlassAlertDialog(
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
                                    Toast.makeText(context, "$name blocked", Toast.LENGTH_SHORT)
                                        .show()
                                } else {
                                    BlacklistManager.removeArtist(context, name)
                                    Toast.makeText(context, "$name unblocked", Toast.LENGTH_SHORT)
                                        .show()
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

@Composable
private fun PlayerAction(
    icon: androidx.compose.ui.graphics.painter.Painter,
    label: String,
    iconSize: androidx.compose.ui.unit.Dp,
    active: Boolean = false,
    onClick: () -> Unit
) {
    val tint = if (active) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 64.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(iconSize))
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
    }
}
