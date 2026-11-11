package com.example.juke.desktop

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.*
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.example.juke.ui.theme.GlassLevel
import com.example.juke.ui.theme.glassPane
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.example.juke.database.PlaylistEntity
import com.example.juke.models.Track
import com.example.juke.network.JukesApi
import com.example.juke.ui.components.ActionMenu
import com.example.juke.ui.components.MenuAction
import com.example.juke.ui.components.PlaylistCover
import com.example.juke.ui.components.libraryTrackActions
import com.example.juke.ui.components.onRightClick
import com.example.juke.ui.components.playerArtworkEndpoint
import com.example.juke.ui.components.playerBarEndpoint
import com.example.juke.ui.components.playerStage
import com.example.juke.ui.components.player.PlaybackSeekSlider
import com.example.juke.ui.icons.JukeIcons
import com.example.juke.ui.screens.parseSyncedLyrics
import com.example.juke.viewmodels.LibraryViewModel
import com.example.juke.viewmodels.MusicUiState
import com.example.juke.viewmodels.MusicViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val PanelShape = RoundedCornerShape(14.dp)
private val panelColor @Composable get() = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.055f)
private val Dim @Composable get() = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) =
    Column(modifier.clip(PanelShape).background(panelColor), content = content)

@Composable
internal fun Tip(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 36.dp, tint: Color = Dim, selected: Boolean = false) {
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    Box(modifier.size(size).clip(CircleShape).background(if (hovered) MaterialTheme.colorScheme.onSurface.copy(0.10f) else Color.Transparent)
        .hoverable(src).clickable(interactionSource = src, indication = null, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = if (selected) MaterialTheme.colorScheme.primary else if (hovered) MaterialTheme.colorScheme.onSurface else tint, modifier = Modifier.size(size * 0.55f))
    }
}

/** Thin draggable bar: volume, and anything else 0..1. Grows a knob on hover like the desktop players do. */
@Composable
fun ThinSlider(value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier, accent: Color = MaterialTheme.colorScheme.onSurface) {
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    var width by remember { mutableStateOf(1) }
    Box(modifier.height(20.dp).hoverable(src).onSizeChanged { width = it.width.coerceAtLeast(1) }
        .pointerInput(Unit) { detectTapGestures { onChange((it.x / width).coerceIn(0f, 1f)) } }
        .pointerInput(Unit) { detectDragGestures { c, _ -> onChange((c.position.x / width).coerceIn(0f, 1f)) } },
        contentAlignment = Alignment.CenterStart) {
        Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(0.22f)))
        Box(Modifier.fillMaxWidth(value.coerceIn(0f, 1f)).height(4.dp).clip(CircleShape).background(if (hovered) MaterialTheme.colorScheme.primary else accent))
        if (hovered) Box(Modifier.padding(start = ((width * value) / 2.6f).dp.coerceAtLeast(0.dp)).size(0.dp))
    }
}

// ---------------------------------------------------------------------------------------------
// Top bar
// ---------------------------------------------------------------------------------------------
@Composable
fun DesktopTopBar(titleInset: Dp, query: String, focusTrigger: Int, onQueryChange: (String) -> Unit, onSubmit: () -> Unit, onHome: () -> Unit, onJam: () -> Unit, onSettings: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp)) {
        // With the title bar hidden this strip is the title bar: drag to move, double-click to fill the screen.
        LocalWindowScope.current?.let { ws ->
            Box(Modifier.matchParentSize()
                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { toggleMaximize(ws.window) }) }
                .pointerInput(Unit) {
                    var grab = java.awt.Point(); var origin = java.awt.Point()
                    detectDragGestures(
                        onDragStart = { grab = java.awt.MouseInfo.getPointerInfo().location; origin = ws.window.location },
                        onDrag = { change, _ -> change.consume(); val p = java.awt.MouseInfo.getPointerInfo().location; ws.window.setLocation(origin.x + p.x - grab.x, origin.y + p.y - grab.y) },
                    )
                })
        }
        Spacer(Modifier.width(titleInset))
        Row(Modifier.align(Alignment.Center).widthIn(max = 560.dp).fillMaxWidth(0.7f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(panelColor).clickable(onClick = onHome), contentAlignment = Alignment.Center) { Icon(JukeIcons.Home, "Home", Modifier.size(22.dp)) }
            TopSearchField(query, focusTrigger, onQueryChange, onSubmit, Modifier.weight(1f))
        }
        Row(Modifier.align(Alignment.CenterEnd), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Tip(JukeIcons.Share, "Jam", onJam, size = 40.dp)
            Tip(JukeIcons.Equalizer, "Settings", onSettings, size = 40.dp)
        }
    }
}

/** Drag the gutter to resize a side pane; the centre takes the remaining width. */
@Composable
fun DesktopPaneDivider(enabled: Boolean = true, onDelta: (Float) -> Unit, onFinished: () -> Unit) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val dragState = androidx.compose.foundation.gestures.rememberDraggableState { pixels ->
        onDelta(pixels / density.density)
    }
    Box(Modifier.width(8.dp).fillMaxHeight()
        .then(if (enabled) Modifier
            .hoverable(interaction)
            .pointerHoverIcon(androidx.compose.ui.input.pointer.PointerIcon(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.E_RESIZE_CURSOR)))
            .draggable(dragState, androidx.compose.foundation.gestures.Orientation.Horizontal, onDragStopped = { onFinished() })
        else Modifier), contentAlignment = Alignment.Center) {
        if (enabled && hovered) Box(Modifier.width(2.dp).fillMaxHeight(0.12f).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)))
    }
}

// ---------------------------------------------------------------------------------------------
// Library sidebar
// ---------------------------------------------------------------------------------------------
@Composable
fun LibrarySidebar(libraryViewModel: LibraryViewModel, collapsed: Boolean, onToggle: () -> Unit, onOpenSongs: (favourites: Boolean) -> Unit, onOpenPlaylist: (PlaylistEntity) -> Unit, modifier: Modifier = Modifier) {
    val state by libraryViewModel.uiState.collectAsStateWithLifecycle()
    // labels fade in after the pane has started widening and out before it narrows, so nothing reflows mid-animation
    val labelAlpha by androidx.compose.animation.core.animateFloatAsState(if (collapsed) 0f else 1f, androidx.compose.animation.core.tween(if (collapsed) 90 else 200, delayMillis = if (collapsed) 0 else 110), label = "libraryLabels")
    Panel(modifier) {
        Row(Modifier.padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Tip(JukeIcons.LibrarySelected, if (collapsed) "Expand your library" else "Collapse your library", onToggle, size = 44.dp)
            if (labelAlpha > 0f) {
                Spacer(Modifier.width(4.dp))
                Text("Your Library", style = MaterialTheme.typography.titleSmall, maxLines = 1, softWrap = false, modifier = Modifier.weight(1f).graphicsLayer { alpha = labelAlpha })
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp)) {
            item { SideRow("Liked Songs", "Playlist", null, labelAlpha, MaterialTheme.colorScheme.primary) { onOpenSongs(true) } }
            item { SideRow("All songs", "${state.tracks.size} downloaded or streamed", null, labelAlpha, MaterialTheme.colorScheme.secondary) { onOpenSongs(false) } }
            items(state.playlists, key = { it.id }) { p -> SideRow(p.name, "Playlist", p, labelAlpha) { onOpenPlaylist(p) } }
        }
    }
}

/** Right-click menu for a song in a queue list: the usual song actions, plus taking it out of the queue (not during a Jam). */
@Composable
private fun QueueRowMenu(open: Boolean, onDismiss: () -> Unit, t: Track, index: Int, vm: MusicViewModel) {
    val remove = if (com.example.juke.jam.JamSession.state.value.active) emptyList() else listOf(MenuAction("Remove from queue") { vm.removeFromQueue(t.uuid, index) })
    ActionMenu(open, onDismiss, libraryTrackActions(t, vm) { vm.playTrackFromQueue(t, index) } + remove)
}

@Composable
private fun SideRow(title: String, subtitle: String, playlist: PlaylistEntity?, labelAlpha: Float, tile: Color = Color.Gray, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (hovered) MaterialTheme.colorScheme.onSurface.copy(0.07f) else Color.Transparent)
        .hoverable(src).clickable(interactionSource = src, indication = null, onClick = onClick).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (playlist != null) PlaylistCover(playlist, Modifier.size(48.dp), RoundedCornerShape(8.dp))
        else Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(Brush.linearGradient(listOf(tile, tile.copy(alpha = 0.45f)))), contentAlignment = Alignment.Center) {
            Icon(if (title == "Liked Songs") JukeIcons.HeartSelected else JukeIcons.MusicNote, null, Modifier.size(22.dp), tint = Color.White)
        }
        if (labelAlpha > 0f) {
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).graphicsLayer { alpha = labelAlpha }) {
                Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Lyrics: one view for the side panel (compact) and the full player (big)
// ---------------------------------------------------------------------------------------------
@Composable
fun LyricsView(track: Track, position: Long, big: Boolean, onSeek: (Long) -> Unit, musicViewModel: MusicViewModel, modifier: Modifier = Modifier) {
    val shown = rememberLyricsTrack(track, musicViewModel)
    val synced = shown.syncedLyrics?.takeIf { it.isNotBlank() }
    val plain = shown.plainLyrics?.takeIf { it.isNotBlank() }
    var triedFetch by remember(track.uuid) { mutableStateOf(false) }
    LaunchedEffect(track.uuid, synced, plain) { if (synced == null && plain == null && !triedFetch) { triedFetch = true; musicViewModel.refreshLyrics(track) } }

    when {
        synced != null -> {
            val lines = remember(synced, track.lyricsOffsetMs) { parseSyncedLyrics(synced, track.lyricsOffsetMs) }
            val active = remember(lines, position) { lines.indexOfLast { it.timeMs <= position }.coerceAtLeast(0) }
            val state = rememberLazyListState()
            var viewport by remember { mutableStateOf(0) }
            LaunchedEffect(active, viewport) { if (viewport > 0) state.animateScrollToItem(active, scrollOffset = -(viewport * if (big) 0.30f else 0.12f).toInt()) }
            val activeSize = if (big) 38.sp else 19.sp
            LazyColumn(modifier.onSizeChanged { viewport = it.height }, state = state, contentPadding = PaddingValues(top = 4.dp, bottom = with(androidx.compose.ui.platform.LocalDensity.current) { (viewport * 0.6f).toDp() }), verticalArrangement = Arrangement.spacedBy(if (big) 20.dp else 10.dp)) {
                itemsIndexed(lines, key = { i, l -> "$i-${l.timeMs}" }) { i, l ->
                    val dist = kotlin.math.abs(i - active)
                    val target = when { i == active -> 1f; dist == 1 -> 0.5f; else -> 0.3f }
                    val a by androidx.compose.animation.core.animateFloatAsState(target, androidx.compose.animation.core.tween(280), label = "lyric")
                    Text(l.text, Modifier.fillMaxWidth().alpha(a).clip(RoundedCornerShape(8.dp)).clickable { onSeek(l.timeMs) }.padding(vertical = 2.dp),
                        style = MaterialTheme.typography.headlineMedium.copy(fontSize = activeSize, lineHeight = activeSize * 1.22f, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
                        color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        plain != null -> LazyColumn(modifier, contentPadding = PaddingValues(vertical = 12.dp)) {
            item { Text(plain, style = MaterialTheme.typography.bodyLarge.copy(fontSize = if (big) 22.sp else 15.sp, lineHeight = if (big) 34.sp else 22.sp, fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface.copy(0.85f)) }
        }
        else -> Box(modifier, contentAlignment = Alignment.Center) {
            Text(if (triedFetch) "Looking for lyrics…" else "No lyrics for this song", color = Dim, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Now-playing panel (right)
// ---------------------------------------------------------------------------------------------
@Composable
fun NowPlayingPanel(state: MusicUiState, musicViewModel: MusicViewModel, onClose: () -> Unit, onOpenFullscreen: () -> Unit, onGoToAlbum: (String) -> Unit = {}, onGoToArtist: (String) -> Unit = {}, modifier: Modifier = Modifier) {
    val track = state.currentTrack
    val tint = state.extractedColors?.primary ?: MaterialTheme.colorScheme.primary
    Box(modifier.clip(PanelShape).background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.55f), Color(0xFF161616), Color(0xFF111111))))) {
        if (track == null) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(JukeIcons.MusicNote, null, Modifier.size(40.dp), tint = Dim); Spacer(Modifier.height(12.dp))
                Text("Nothing playing", style = MaterialTheme.typography.titleMedium); Text("Pick a song and it shows up here, with lyrics.", color = Dim, style = MaterialTheme.typography.bodySmall)
            }
            return@Box
        }
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(start = 18.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Now playing", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                PlayerOptionsButton(track, musicViewModel, onGoToAlbum = onGoToAlbum, onGoToArtist = onGoToArtist, size = 36.dp)
                Tip(JukeIcons.Close, "Close panel", onClose)
            }
            // no item spacing: the queue's rows sit flush inside one card; the blocks above space themselves
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                item {
                    AsyncImage(track.thumbnailUri, track.title, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpenFullscreen), contentScale = ContentScale.Crop)
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(track.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            ArtistLinks(track, MaterialTheme.typography.bodyMedium, Dim, onGoToArtist)
                        }
                        Tip(if (track.isFavourite) JukeIcons.HeartSelected else JukeIcons.Heart, "Favourite", { musicViewModel.toggleFavorite(track) }, size = 40.dp, tint = if (track.isFavourite) MaterialTheme.colorScheme.primary else Dim)
                        if (track.isStream) Tip(JukeIcons.Download, "Download", { musicViewModel.promoteTrackToDownload(track) }, size = 40.dp)
                    }
                    Spacer(Modifier.height(14.dp))
                }
                item {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.34f)).padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Lyrics", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text("Show all", style = MaterialTheme.typography.labelMedium, color = Dim, modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onOpenFullscreen).padding(4.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        LyricsView(track, state.position, big = false, onSeek = { ms -> musicViewModel.playbackManager.seekPrecise(ms); musicViewModel.updateProgress() }, musicViewModel = musicViewModel, modifier = Modifier.fillMaxWidth().height(250.dp))
                    }
                    Spacer(Modifier.height(14.dp))
                }
                // the whole upcoming queue, one lazy row each so a long queue scrolls with the panel; rows share one card
                val start = (state.queueIndex + 1).coerceAtLeast(0)
                val next = state.queue.drop(start)
                val card = Color.White.copy(alpha = 0.06f)
                if (next.isNotEmpty()) item(key = "queue_header") {
                    Text("Next in queue · ${next.size}", style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)).background(card).padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 6.dp))
                }
                itemsIndexed(next, key = { i, t -> "q_${start + i}_${t.uuid}" }) { i, t ->
                    val last = i == next.lastIndex
                    var menu by remember { mutableStateOf(false) }
                    Box(Modifier.fillMaxWidth()
                        .clip(if (last) RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp) else RoundedCornerShape(0.dp)).background(card)
                        .padding(start = 14.dp, end = 14.dp, bottom = if (last) 10.dp else 0.dp)) {
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).onRightClick { menu = true }.clickable { musicViewModel.playTrackFromQueue(t, start + i) }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            QueueRowMenu(menu, { menu = false }, t, start + i, musicViewModel)
                            AsyncImage(t.thumbnailUri, null, Modifier.size(42.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
                            Spacer(Modifier.width(10.dp))
                            Column { Text(t.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(t.artist, style = MaterialTheme.typography.bodySmall, color = Dim, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                }
            }
        }
    }
}


// ---------------------------------------------------------------------------------------------
// Bottom player bar
// ---------------------------------------------------------------------------------------------
@Composable
fun DesktopPlayerBar(state: MusicUiState, musicViewModel: MusicViewModel, panelOpen: Boolean, onTogglePanel: () -> Unit, onTogglePanelLyrics: () -> Unit, onFullscreen: () -> Unit, onGoToArtist: (String) -> Unit = {}, modifier: Modifier = Modifier) {
    val track = state.currentTrack
    val pm = musicViewModel.playbackManager
    val volume by pm.volumeFlow.collectAsStateWithLifecycle()
    val preparing by JukesApi.preparing.collectAsStateWithLifecycle()
    var lastVolume by remember { mutableStateOf(0.6f) }
    // The bar is always on screen in desktop mode, so it drives the progress for every view.
    LaunchedEffect(state.isPlaying, track?.uuid) {
        musicViewModel.updateProgress()
        while (state.isPlaying) { kotlinx.coroutines.delay(250); musicViewModel.updateProgress() }
    }
    Row(modifier.fillMaxWidth().height(100.dp).playerBarEndpoint().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        // left: what's playing
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            if (track != null) {
                AsyncImage(track.thumbnailUri, null, Modifier.size(58.dp).playerArtworkEndpoint(expanded = false).clip(RoundedCornerShape(8.dp)).clickable(onClick = onFullscreen), contentScale = ContentScale.Crop)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f, fill = false)) {
                    Text(track.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    ArtistLinks(track, MaterialTheme.typography.bodySmall, Dim, onGoToArtist)
                }
                Tip(if (track.isFavourite) JukeIcons.HeartSelected else JukeIcons.Heart, "Favourite", { musicViewModel.toggleFavorite(track) }, tint = if (track.isFavourite) MaterialTheme.colorScheme.primary else Dim)
                if (track.isStream) Tip(JukeIcons.Download, "Download", { musicViewModel.promoteTrackToDownload(track) })
            }
        }
        // centre: transport + seek
        Column(Modifier.widthIn(max = 640.dp).weight(1.6f), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tip(JukeIcons.Shuffle, "Shuffle", musicViewModel::toggleShuffle, selected = state.isShuffleEnabled)
                Tip(JukeIcons.Previous, "Previous", musicViewModel::skipToPrevious, tint = MaterialTheme.colorScheme.onSurface)
                Box(Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface).clickable(onClick = musicViewModel::togglePlayPause), contentAlignment = Alignment.Center) {
                    Icon(if (state.isPlaying) JukeIcons.Pause else JukeIcons.Play, if (state.isPlaying) "Pause" else "Play", Modifier.size(22.dp), tint = MaterialTheme.colorScheme.background)
                }
                Tip(JukeIcons.Next, "Next", musicViewModel::skipToNext, tint = MaterialTheme.colorScheme.onSurface)
                Tip(if (state.repeatMode == Player.REPEAT_MODE_ONE) JukeIcons.RepeatOne else JukeIcons.Repeat, "Repeat", musicViewModel::toggleRepeat, selected = state.repeatMode != Player.REPEAT_MODE_OFF)
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val dur = if (state.duration > 0) state.duration else pm.getDuration()
                Text(fmt(state.position), style = MaterialTheme.typography.labelSmall, color = Dim, modifier = Modifier.width(38.dp))
                if (preparing != null) Text("Preparing ${preparing.orEmpty()}…", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                else PlaybackSeekSlider(state.position, dur, musicViewModel::seekTo, Modifier.weight(1f))
                Text(fmt(dur), style = MaterialTheme.typography.labelSmall, color = Dim, modifier = Modifier.width(38.dp))
            }
        }
        // right: panels + volume
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
            Tip(JukeIcons.Lyrics, "Lyrics", onTogglePanelLyrics, selected = panelOpen)
            Tip(JukeIcons.Queue, "Now playing view", onTogglePanel, selected = panelOpen)
            Tip(if (volume <= 0.001f) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp, "Mute", { if (volume > 0.001f) { lastVolume = volume; pm.setVolume(0f) } else pm.setVolume(lastVolume) })
            ThinSlider(volume, pm::setVolume, Modifier.width(104.dp))
            Spacer(Modifier.width(6.dp))
            Tip(JukeIcons.ChevronUp, "Full screen player", onFullscreen)
        }
    }
}

/** The song's artists as separate links: each name opens its own artist page (names are matched to the artist ids by position). */
@Composable
internal fun ArtistLinks(track: Track, style: androidx.compose.ui.text.TextStyle, color: Color, onArtist: (String) -> Unit, modifier: Modifier = Modifier) {
    val ids = track.artistSpotifyIds.orEmpty()
    val names = track.artist.split(", ").map { it.trim() }.filter { it.isNotEmpty() }
    if (ids.isEmpty() || names.size != ids.size) { // can't tell which name is which: the whole line opens the first artist
        Text(track.artist, modifier.clickable(enabled = ids.isNotEmpty()) { onArtist(ids.first()) }, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        return
    }
    Row(modifier) {
        names.forEachIndexed { i, n ->
            val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            val hover by src.collectIsHoveredAsState()
            Text(n, Modifier.weight(1f, fill = false).hoverable(src).clickable(src, null) { onArtist(ids[i]) },
                style = style, color = if (hover) MaterialTheme.colorScheme.onSurface else color, maxLines = 1, overflow = TextOverflow.Ellipsis,
                textDecoration = if (hover) androidx.compose.ui.text.style.TextDecoration.Underline else null)
            if (i < names.lastIndex) Text(", ", style = style, color = color, maxLines = 1)
        }
    }
}

private fun fmt(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return "%d:%02d".format(s / 60, s % 60) }

private enum class PlayerTab { Lyrics, Queue }

/** The Mix (radio) glyph the phone uses: broadcast arcs around a play triangle. */
private val MixIcon: ImageVector by lazy {
    ImageVector.Builder("Mix", 24.dp, 24.dp, 18f, 18f).addPath(
        androidx.compose.ui.graphics.vector.PathParser().parsePathString("M3.166 3.161a.75.75 0 0 1 1.06 1.06 6.753 6.753 0 0 0 0 9.548.75.75 0 0 1-1.06 1.06 8.253 8.253 0 0 1 0-11.668m10.607 0a.75.75 0 0 1 1.061 0 8.25 8.25 0 0 1 0 11.668.75.75 0 0 1-1.06-1.06 6.75 6.75 0 0 0 0-9.547.75.75 0 0 1 0-1.06Zm-2.122 2.126a.75.75 0 0 1 1.06 0 5.25 5.25 0 0 1 0 7.424.75.75 0 0 1-1.06-1.06 3.75 3.75 0 0 0 0-5.303.75.75 0 0 1 0-1.06Zm-6.363-.004a.75.75 0 1 1 1.06 1.06 3.75 3.75 0 0 0-.813 4.088c.189.454.466.867.814 1.216a.75.75 0 0 1-1.06 1.06A5.25 5.25 0 0 1 3.75 8.995a5.25 5.25 0 0 1 1.538-3.712m5.962 3.712-3.75 2.25v-4.5z").toNodes(), fill = androidx.compose.ui.graphics.SolidColor(Color.White)).build()
}

/** A glass pill: icon + label, used for the player pane's tabs and actions. */
@Composable
private fun PlayerChip(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    val fill by androidx.compose.animation.animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onSurface.copy(0.16f) else if (hovered) MaterialTheme.colorScheme.onSurface.copy(0.09f) else Color.Transparent,
        androidx.compose.animation.core.tween(140), label = "chip")
    Row(Modifier.height(36.dp).clip(CircleShape).background(fill).hoverable(src).clickable(interactionSource = src, indication = null, onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Icon(icon, null, Modifier.size(18.dp), tint = if (selected || hovered) MaterialTheme.colorScheme.onSurface else Dim)
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = if (selected || hovered) MaterialTheme.colorScheme.onSurface else Dim, maxLines = 1)
    }
}

/** The whole queue: what is playing is marked, the rest plays on click. Opens scrolled to the current song. */
@Composable
private fun PlayerQueue(state: MusicUiState, musicViewModel: MusicViewModel) {
    val cur = state.queueIndex
    var showTools by remember { mutableStateOf(false) }
    var saveQueue by remember { mutableStateOf(false) }
    val pending by musicViewModel.pendingSongs.collectAsStateWithLifecycle()
    val jam by com.example.juke.jam.JamSession.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.align(Alignment.End)) {
            IconButton(onClick = { showTools = true }) { Icon(Icons.Rounded.MoreVert, "Queue options") }
            DropdownMenu(expanded = showTools, onDismissRequest = { showTools = false }) {
                listOf("Shuffle upcoming" to MusicViewModel.QueueTool.SHUFFLE_UPCOMING,
                    "Sort upcoming A–Z" to MusicViewModel.QueueTool.SORT_UPCOMING,
                    "Clear played" to MusicViewModel.QueueTool.CLEAR_PLAYED).forEach { (label, tool) ->
                    DropdownMenuItem(text = { Text(label) }, enabled = !jam.active, onClick = { showTools = false; musicViewModel.applyQueueTool(tool) })
                }
                DropdownMenuItem(text = { Text("Save queue as playlist") }, enabled = !jam.active, onClick = { showTools = false; saveQueue = true })
            }
        }
        if (saveQueue) com.example.juke.ui.components.player.SaveQueueDialog(
            initialName = remember { musicViewModel.suggestedQueuePlaylistName() },
            readyCount = state.queue.distinctBy { it.uuid }.size, waitingCount = pending.size,
            onDismiss = { saveQueue = false },
            onSave = { name, includeWaiting -> saveQueue = false; musicViewModel.saveQueueAsPlaylist(name, includeWaiting) })
    if (state.queue.isEmpty()) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("The queue is empty", color = Dim) }; return }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val rowHeightPx = with(density) { 66.dp.toPx() }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (cur - 1).coerceIn(0, state.queue.lastIndex))
    LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp), list, contentPadding = PaddingValues(bottom = 16.dp)) {
        itemsIndexed(state.queue, key = { i, t -> "pq_${i}_${t.uuid}" }) { i, t ->
            val playing = i == cur
            val src = remember { MutableInteractionSource() }
            val hovered by src.collectIsHoveredAsState()
            var menu by remember { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(if (playing) MaterialTheme.colorScheme.primary.copy(0.16f) else if (hovered) MaterialTheme.colorScheme.onSurface.copy(0.07f) else Color.Transparent)
                .hoverable(src).onRightClick { menu = true }.clickable(interactionSource = src, indication = null) { musicViewModel.playTrackFromQueue(t, i) }.padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically) {
                QueueRowMenu(menu, { menu = false }, t, i, musicViewModel)
                AsyncImage(t.thumbnailUri, null, Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.title, style = MaterialTheme.typography.bodyLarge, fontWeight = if (playing) FontWeight.Bold else FontWeight.Medium, color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(t.artist, style = MaterialTheme.typography.bodySmall, color = Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (playing) Icon(JukeIcons.Play, "Playing", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                else if (!jam.active) {
                    Tip(Icons.Rounded.Delete, "Remove from queue", { musicViewModel.removeFromQueue(t.uuid, i) })
                    var drag by remember(i, t.uuid) { mutableFloatStateOf(0f) }
                    Icon(Icons.Rounded.DragHandle, "Drag to reorder", Modifier.size(36.dp).pointerInput(i, state.queue.size, cur) {
                        detectDragGestures(onDragStart = { drag = 0f }, onDragCancel = { drag = 0f }, onDragEnd = {
                            val target = (i + kotlin.math.round(drag / rowHeightPx).toInt()).coerceIn(0, state.queue.lastIndex)
                            if (target != i && (i < cur) == (target < cur) && target != cur) musicViewModel.moveInQueue(i, target)
                            drag = 0f
                        }) { change, amount -> change.consume(); drag += amount.y }
                    }, tint = Dim)
                }
            }
        }
    }
    }
}

// ---------------------------------------------------------------------------------------------
// Full player: art + controls on the left, a glass pane with lyrics / up next on the right
// ---------------------------------------------------------------------------------------------
@Composable
fun DesktopPlayerScreen(state: MusicUiState, musicViewModel: MusicViewModel, onDismiss: () -> Unit, onNavigateToArtist: (String) -> Unit, onGoToAlbum: (String) -> Unit = {}, onGoToArtist: (String) -> Unit = {}, modifier: Modifier = Modifier) {
    androidx.activity.compose.BackHandler(onBack = onDismiss)
    val track = state.currentTrack
    val tint = state.extractedColors?.primary ?: MaterialTheme.colorScheme.primary
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
    Box(modifier.fillMaxSize().background(Color(0xFF0B0B0B))) {
        if (track?.thumbnailUri != null) AsyncImage(track.thumbnailUri, null, Modifier.fillMaxSize().scale(1.3f).blur(110.dp), contentScale = ContentScale.Crop, alpha = 0.55f)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.62f), Color.Black.copy(alpha = 0.82f)))))
        if (track == null) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Nothing playing", color = Dim) }; return@Box }
        var tab by remember { mutableStateOf(PlayerTab.Lyrics) }
        var copied by remember { mutableStateOf(false) }
        LaunchedEffect(copied) { if (copied) { kotlinx.coroutines.delay(1800); copied = false } }
        // art takes whatever height the controls leave; the glass pane beside it takes the rest of the width
        Row(Modifier.fillMaxSize().playerStage(0.25f, 0.8f, 0.dp).padding(start = 32.dp, end = 32.dp, top = 68.dp, bottom = 36.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(0.9f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().widthIn(max = 760.dp), contentAlignment = Alignment.Center) {
                    val artworkSize = minOf(maxWidth, maxHeight)
                    AsyncImage(track.thumbnailUri, track.title, Modifier.size(artworkSize).playerArtworkEndpoint(expanded = true)
                        .glassPane(RoundedCornerShape(22.dp), GlassLevel.Thick).clip(RoundedCornerShape(22.dp)), contentScale = ContentScale.Crop)
                }
                Spacer(Modifier.height(22.dp))
                Column(Modifier.widthIn(max = 760.dp).fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(track.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            ArtistLinks(track, MaterialTheme.typography.titleMedium, Dim, onNavigateToArtist)
                        }
                        Tip(if (track.isFavourite) JukeIcons.HeartSelected else JukeIcons.Heart, "Favourite", { musicViewModel.toggleFavorite(track) }, size = 44.dp, tint = if (track.isFavourite) MaterialTheme.colorScheme.primary else Dim)
                    }
                    Spacer(Modifier.height(10.dp))
                    PlaybackSeekSlider(state.position, if (state.duration > 0) state.duration else musicViewModel.playbackManager.getDuration(), musicViewModel::seekTo)
                    Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(fmt(state.position), style = MaterialTheme.typography.labelMedium, color = Dim); Text(fmt(state.duration), style = MaterialTheme.typography.labelMedium, color = Dim) }
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Tip(JukeIcons.Shuffle, "Shuffle", musicViewModel::toggleShuffle, size = 48.dp, selected = state.isShuffleEnabled)
                        Tip(JukeIcons.Previous, "Previous", musicViewModel::skipToPrevious, size = 56.dp, tint = MaterialTheme.colorScheme.onSurface)
                        Box(Modifier.size(68.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface).clickable(onClick = musicViewModel::togglePlayPause), contentAlignment = Alignment.Center) {
                            Icon(if (state.isPlaying) JukeIcons.Pause else JukeIcons.Play, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.background)
                        }
                        Tip(JukeIcons.Next, "Next", musicViewModel::skipToNext, size = 56.dp, tint = MaterialTheme.colorScheme.onSurface)
                        Tip(if (state.repeatMode == Player.REPEAT_MODE_ONE) JukeIcons.RepeatOne else JukeIcons.Repeat, "Repeat", musicViewModel::toggleRepeat, size = 48.dp, selected = state.repeatMode != Player.REPEAT_MODE_OFF)
                    }
                }
            }
            Column(Modifier.weight(1.1f).fillMaxHeight().glassPane(RoundedCornerShape(26.dp), GlassLevel.Regular)) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 14.dp, end = 14.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PlayerChip(JukeIcons.Lyrics, "Lyrics", tab == PlayerTab.Lyrics) { tab = PlayerTab.Lyrics }
                    PlayerChip(JukeIcons.Queue, "Up next", tab == PlayerTab.Queue) { tab = PlayerTab.Queue }
                    if (track.isStream) PlayerChip(JukeIcons.Download, "Download", false) { musicViewModel.promoteTrackToDownload(track) }
                    PlayerChip(MixIcon, "Mix", false) { musicViewModel.startRadio(); tab = PlayerTab.Queue }
                    if (track.spotifyId != null) PlayerChip(if (copied) JukeIcons.Check else JukeIcons.Share, if (copied) "Link copied" else "Share", copied) {
                        java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection("https://open.spotify.com/track/${track.spotifyId}"), null)
                        copied = true
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 28.dp)) {
                    when (tab) {
                        PlayerTab.Lyrics -> LyricsView(track, state.position, big = true, onSeek = { ms -> musicViewModel.playbackManager.seekPrecise(ms); musicViewModel.updateProgress() }, musicViewModel = musicViewModel, modifier = Modifier.fillMaxSize().padding(top = 14.dp))
                        PlayerTab.Queue -> PlayerQueue(state, musicViewModel)
                    }
                }
            }
        }
        Tip(JukeIcons.ChevronDown, "Close", onDismiss, Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 12.dp).padding(start = titleInsetDp()), size = 44.dp, tint = MaterialTheme.colorScheme.onSurface)
        if (track != null) PlayerOptionsButton(track, musicViewModel, onGoToAlbum = onGoToAlbum, onGoToArtist = onNavigateToArtist, modifier = Modifier.align(Alignment.TopEnd).padding(end = 20.dp, top = 12.dp))
    }
    }
}

/** Extra left padding for macOS traffic lights when content runs under the title bar. */
@Composable fun titleInsetDp(): Dp = LocalTitleInset.current
val LocalTitleInset = staticCompositionLocalOf { 0.dp }

val LocalWindowScope = staticCompositionLocalOf<androidx.compose.ui.window.WindowScope?> { null }

private var restoreBounds: java.awt.Rectangle? = null

private var animating: javax.swing.Timer? = null

/** Fill the screen's usable area (menu bar and Dock stay visible); a second call puts the window back. Eased, not snapped. */
fun toggleMaximize(window: java.awt.Window) {
    val gc = window.graphicsConfiguration ?: return
    val ins = java.awt.Toolkit.getDefaultToolkit().getScreenInsets(gc)
    val b = gc.bounds
    val full = java.awt.Rectangle(b.x + ins.left, b.y + ins.top, b.width - ins.left - ins.right, b.height - ins.top - ins.bottom)
    animating?.stop() // a new click retargets from wherever the window is now
    val from = window.bounds
    val atFull = from.width >= full.width - 2 && from.height >= full.height - 2
    val to = if (atFull) (restoreBounds ?: java.awt.Rectangle(full.x + full.width / 8, full.y + full.height / 8, full.width * 3 / 4, full.height * 3 / 4))
             else { restoreBounds = from; full }
    val start = System.nanoTime(); val durationMs = 240.0
    animating = javax.swing.Timer(8) { e ->
        val t = ((System.nanoTime() - start) / 1e6 / durationMs).coerceIn(0.0, 1.0)
        val k = 1 - Math.pow(1 - t, 4.0) // ease-out quart: quick start, soft landing
        fun lerp(x: Int, y: Int) = (x + (y - x) * k).toInt()
        window.setBounds(lerp(from.x, to.x), lerp(from.y, to.y), lerp(from.width, to.width), lerp(from.height, to.height))
        if (t >= 1.0) { window.bounds = to; (e.source as javax.swing.Timer).stop() }
    }.also { it.start() }
}

/** Shared with the window's key handler: Cmd/Ctrl+K focuses the bar; while typing, Space and Esc belong to the field. */
object TopSearch {
    var focusTrigger by mutableIntStateOf(0)
    @Volatile var typing = false
}

/** The one search bar: a real field in the top bar. Typing opens Search and drives it; Enter runs the full search. */
@Composable
private fun TopSearchField(query: String, focusTrigger: Int, onQueryChange: (String) -> Unit, onSubmit: () -> Unit, modifier: Modifier = Modifier) {
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    DisposableEffect(Unit) { onDispose { TopSearch.typing = false } }
    var focused by remember { mutableStateOf(false) }
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    LaunchedEffect(focusTrigger) { if (focusTrigger > 0) runCatching { focus.requestFocus() } }
    val ring by androidx.compose.animation.animateColorAsState(
        if (focused) MaterialTheme.colorScheme.onSurface else if (hovered) MaterialTheme.colorScheme.onSurface.copy(0.35f) else Color.Transparent,
        androidx.compose.animation.core.tween(160),
    )
    val fill by androidx.compose.animation.animateColorAsState(if (focused) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f) else if (hovered) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.09f) else panelColor, androidx.compose.animation.core.tween(160))
    Row(modifier.height(44.dp).clip(CircleShape).background(fill).border(2.dp, ring, CircleShape).hoverable(src).padding(start = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(JukeIcons.Search, null, Modifier.size(20.dp), tint = if (focused) MaterialTheme.colorScheme.onSurface else Dim)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) Text("Search songs, artists, lyrics", color = Dim, style = MaterialTheme.typography.bodyMedium, maxLines = 1, softWrap = false)
            androidx.compose.foundation.text.BasicTextField(
                value = query, onValueChange = onQueryChange, singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.onSurface),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).onFocusChanged { focused = it.isFocused; TopSearch.typing = it.isFocused }
                    .onPreviewKeyEvent { if (it.type == androidx.compose.ui.input.key.KeyEventType.KeyDown && it.key == androidx.compose.ui.input.key.Key.Enter) { onSubmit(); true } else if (it.type == androidx.compose.ui.input.key.KeyEventType.KeyDown && it.key == androidx.compose.ui.input.key.Key.Escape) { focusManager.clearFocus(); true } else false },
            )
        }
        if (query.isNotEmpty()) Tip(JukeIcons.Close, "Clear", { onQueryChange(""); focus.requestFocus() }, size = 32.dp)
        else Text(if (System.getProperty("os.name").orEmpty().startsWith("Mac")) "⌘K" else "Ctrl K", color = Dim, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(end = 10.dp))
    }
}
