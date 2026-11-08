package com.example.juke.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.juke.database.PlaylistEntity
import com.example.juke.models.Track
import com.example.juke.ui.components.AddToPlaylistDialog
import com.example.juke.ui.components.CreatePlaylistDialog
import com.example.juke.ui.icons.JukeIcons
import com.example.juke.ui.screens.BlacklistPickerDialog
import com.example.juke.ui.screens.SleepTimerDialog
import com.example.juke.utils.BlacklistManager
import com.example.juke.utils.LyricsRomanizer
import com.example.juke.viewmodels.LibraryViewModel
import com.example.juke.viewmodels.MusicViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * The lyrics the screen should show: the track itself, or, with Romanized Lyrics on, a copy whose
 * lyrics are transliterated (same retry and save-once-complete rules as the phone player).
 */
@Composable
fun rememberLyricsTrack(track: Track, vm: MusicViewModel): Track {
    val romanize by vm.isRomanizedLyricsEnabled.collectAsStateWithLifecycle()
    var romanized by remember(track.uuid, romanize, track.syncedLyrics, track.plainLyrics, track.romanizedSyncedLyrics, track.romanizedPlainLyrics) { mutableStateOf<Track?>(null) }
    LaunchedEffect(track.uuid, track.syncedLyrics, track.plainLyrics, track.romanizedSyncedLyrics, track.romanizedPlainLyrics, romanize) {
        if (!romanize) { romanized = null; return@LaunchedEffect }
        val complete = LyricsRomanizer::isFullyRomanized
        for (attempt in 0..3) {
            if (attempt > 0) delay((5_000L * attempt).milliseconds)
            val synced = track.romanizedSyncedLyrics?.takeIf(complete) ?: track.syncedLyrics?.let { LyricsRomanizer.romanizeSyncedLyrics(it) }
            val plain = track.romanizedPlainLyrics?.takeIf(complete) ?: track.plainLyrics?.let { LyricsRomanizer.romanizeText(it) }
            romanized = track.copy(syncedLyrics = synced ?: track.syncedLyrics, plainLyrics = plain ?: track.plainLyrics, romanizedSyncedLyrics = synced, romanizedPlainLyrics = plain)
            if (synced?.let(complete) == false || plain?.let(complete) == false) continue
            if (synced != track.romanizedSyncedLyrics || plain != track.romanizedPlainLyrics) vm.persistRomanizedLyrics(track, synced, plain)
            break
        }
    }
    return if (romanize) romanized ?: track else track
}

/** Three-dots button plus its menu and the dialogs the entries open. */
@Composable
fun PlayerOptionsButton(
    track: Track,
    vm: MusicViewModel,
    onGoToAlbum: (String) -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 44.dp,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val library = viewModel<LibraryViewModel>()
    val libraryState by library.uiState.collectAsStateWithLifecycle()
    val ui by vm.uiState.collectAsStateWithLifecycle()
    val speed by vm.playbackSpeed.collectAsStateWithLifecycle()
    val romanize by vm.isRomanizedLyricsEnabled.collectAsStateWithLifecycle()
    val timer by vm.sleepTimerRemaining.collectAsStateWithLifecycle()
    val pending by vm.pendingSongs.collectAsStateWithLifecycle()

    var open by remember { mutableStateOf(false) }
    var sleepDialog by remember { mutableStateOf(false) }
    var saveQueue by remember { mutableStateOf(false) }
    var blockDialog by remember { mutableStateOf(false) }
    var playlistDialog by remember { mutableStateOf(false) }
    var newPlaylist by remember { mutableStateOf(false) }
    var memberOf by remember { mutableStateOf<List<PlaylistEntity>>(emptyList()) }
    LaunchedEffect(playlistDialog, track.uuid) { if (playlistDialog) memberOf = library.getPlaylistsForTrack(track.uuid) }

    Box(modifier) {
        Tip(JukeIcons.More, "Player options", { open = true }, size = size, tint = MaterialTheme.colorScheme.onSurface)
        DropdownMenu(
            expanded = open, onDismissRequest = { open = false },
            shape = RoundedCornerShape(14.dp), containerColor = Color(0xFF1B1B1B), tonalElevation = 0.dp, shadowElevation = 16.dp,
            modifier = Modifier.width(320.dp),
        ) {
            val close = { open = false }
            Group {
                Entry(Icons.Outlined.Speed, "Playback speed", trailing = { Value(if (speed % 1f == 0f) "${speed.toInt()}×" else "$speed×") }) { vm.cyclePlaybackSpeed() }
                Entry(Icons.Outlined.Timer, "Sleep timer", trailing = { timer?.let { Value("${((it + 59_999) / 60_000).coerceAtLeast(1)} min") } }) { close(); sleepDialog = true }
            }
            Divider()
            Group {
                Entry(Icons.Outlined.Translate, "Romanized lyrics", trailing = { Switch(checked = romanize, onCheckedChange = null, modifier = Modifier.height(24.dp)) }) { vm.toggleRomanizedLyrics() }
                Entry(JukeIcons.Refresh, "Refresh lyrics") { close(); vm.refreshLyrics(track) }
                if (track.spotifyId != null) Entry(JukeIcons.Refresh, "Wrong song? Refetch") { close(); vm.refetchTrack(track) }
            }
            Divider()
            Group {
                Entry(JukeIcons.PlaylistAdd, "Add to playlist") { close(); playlistDialog = true }
                Entry(JukeIcons.PlaylistAdd, "Save queue as playlist") { close(); saveQueue = true }
                track.albumSpotifyId?.let { id -> Entry(JukeIcons.Album, "Go to album") { close(); onGoToAlbum(id) } }
            }
            if (track.artist.isNotBlank()) {
                Divider()
                val blocked = remember(track.artist, open) { BlacklistManager.containsBlacklistedArtist(context, track.artist) }
                Group { Entry(Icons.Outlined.Block, if (blocked) "Manage blocked artists" else "Block artist", danger = true) { close(); blockDialog = true } }
            }
        }
    }

    if (sleepDialog) SleepTimerDialog(
        currentTimerRemaining = timer, onDismiss = { sleepDialog = false },
        onSetTimer = { m -> if (m > 0) vm.startSleepTimer(m) else vm.cancelSleepTimer(); sleepDialog = false },
        onCancelTimer = { vm.cancelSleepTimer(); sleepDialog = false },
    )
    if (saveQueue) com.example.juke.ui.components.player.SaveQueueDialog(
        initialName = remember { vm.suggestedQueuePlaylistName() },
        readyCount = ui.queue.distinctBy { it.uuid }.size, waitingCount = pending.size,
        onDismiss = { saveQueue = false },
        onSave = { name, includeWaiting -> saveQueue = false; vm.saveQueueAsPlaylist(name, includeWaiting) },
    )
    if (blockDialog) BlacklistPickerDialog(artistString = track.artist, onDismiss = { blockDialog = false })
    if (playlistDialog) AddToPlaylistDialog(
        playlists = libraryState.playlists, tracks = listOf(track), trackPlaylists = memberOf,
        onDismiss = { playlistDialog = false; memberOf = emptyList() },
        onAddToPlaylist = { p -> scope.launch { library.addToPlaylist(p, track); memberOf = library.getPlaylistsForTrack(track.uuid) } },
        onRemoveFromPlaylist = { p -> scope.launch { library.removeFromPlaylist(p, track); memberOf = library.getPlaylistsForTrack(track.uuid) } },
        onCreatePlaylist = { newPlaylist = true },
    )
    if (newPlaylist) CreatePlaylistDialog(onDismiss = { newPlaylist = false }, onCreate = { library.createPlaylist(it); newPlaylist = false })
}

@Composable private fun Group(content: @Composable ColumnScope.() -> Unit) = Column(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), content = content)
@Composable private fun Divider() = HorizontalDivider(Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
@Composable private fun Value(text: String) = Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)

/** One menu row: icon, label, optional trailing state. Stays a plain Row so hover and keyboard focus stay ours. */
@Composable
private fun Entry(icon: ImageVector, label: String, danger: Boolean = false, trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    val tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(if (hovered) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f) else Color.Transparent)
            .hoverable(src).clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = if (danger) tint else tint.copy(alpha = 0.72f))
        Spacer(Modifier.width(14.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = tint)
        trailing?.invoke()
    }
}
