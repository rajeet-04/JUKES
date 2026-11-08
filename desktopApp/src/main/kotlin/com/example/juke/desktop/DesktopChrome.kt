package com.example.juke.desktop

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.example.juke.ui.components.PlaylistCover
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
fun DesktopTopBar(titleInset: Dp, onHome: () -> Unit, onSearch: () -> Unit, onJam: () -> Unit, onSettings: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp)) {
        Spacer(Modifier.width(titleInset))
        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(panelColor).clickable(onClick = onHome), contentAlignment = Alignment.Center) { Icon(JukeIcons.Home, "Home", Modifier.size(22.dp)) }
            Row(Modifier.width(460.dp).height(44.dp).clip(CircleShape).background(panelColor).clickable(onClick = onSearch).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(JukeIcons.Search, null, Modifier.size(20.dp), tint = Dim)
                Text("What do you want to play?", color = Dim, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Row(Modifier.align(Alignment.CenterEnd), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Tip(JukeIcons.Share, "Jam", onJam, size = 40.dp)
            Tip(JukeIcons.Equalizer, "Settings", onSettings, size = 40.dp)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Library sidebar
// ---------------------------------------------------------------------------------------------
@Composable
fun LibrarySidebar(libraryViewModel: LibraryViewModel, onOpenSongs: (favourites: Boolean) -> Unit, onOpenPlaylist: (PlaylistEntity) -> Unit, modifier: Modifier = Modifier) {
    val state by libraryViewModel.uiState.collectAsStateWithLifecycle()
    Panel(modifier) {
        Row(Modifier.padding(start = 18.dp, end = 12.dp, top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(JukeIcons.LibrarySelected, null, Modifier.size(22.dp), tint = Dim)
            Spacer(Modifier.width(10.dp))
            Text("Your Library", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp)) {
            item { SideRow("Liked Songs", "Playlist", null, MaterialTheme.colorScheme.primary) { onOpenSongs(true) } }
            item { SideRow("All songs", "${state.tracks.size} downloaded or streamed", null, MaterialTheme.colorScheme.secondary) { onOpenSongs(false) } }
            items(state.playlists, key = { it.id }) { p -> SideRow(p.name, "Playlist", p) { onOpenPlaylist(p) } }
        }
    }
}

@Composable
private fun SideRow(title: String, subtitle: String, playlist: PlaylistEntity?, tile: Color = Color.Gray, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (hovered) MaterialTheme.colorScheme.onSurface.copy(0.07f) else Color.Transparent)
        .hoverable(src).clickable(interactionSource = src, indication = null, onClick = onClick).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (playlist != null) PlaylistCover(playlist, Modifier.size(48.dp), RoundedCornerShape(8.dp))
        else Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(Brush.linearGradient(listOf(tile, tile.copy(alpha = 0.45f)))), contentAlignment = Alignment.Center) {
            Icon(if (title == "Liked Songs") JukeIcons.HeartSelected else JukeIcons.MusicNote, null, Modifier.size(22.dp), tint = Color.White)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
fun NowPlayingPanel(state: MusicUiState, musicViewModel: MusicViewModel, onClose: () -> Unit, onOpenFullscreen: () -> Unit, onGoToAlbum: (String) -> Unit = {}, modifier: Modifier = Modifier) {
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
                PlayerOptionsButton(track, musicViewModel, onGoToAlbum = onGoToAlbum, size = 36.dp)
                Tip(JukeIcons.Close, "Close panel", onClose)
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item {
                    AsyncImage(track.thumbnailUri, track.title, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpenFullscreen), contentScale = ContentScale.Crop)
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(track.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(track.artist, style = MaterialTheme.typography.bodyMedium, color = Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Tip(if (track.isFavourite) JukeIcons.HeartSelected else JukeIcons.Heart, "Favourite", { musicViewModel.toggleFavorite(track) }, size = 40.dp, tint = if (track.isFavourite) MaterialTheme.colorScheme.primary else Dim)
                    }
                }
                item {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.34f)).padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Lyrics", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text("Show all", style = MaterialTheme.typography.labelMedium, color = Dim, modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onOpenFullscreen).padding(4.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                        LyricsView(track, state.position, big = false, onSeek = musicViewModel::seekTo, musicViewModel = musicViewModel, modifier = Modifier.fillMaxWidth().height(250.dp))
                    }
                }
                val next = state.queue.drop((state.queueIndex + 1).coerceAtLeast(0)).take(4)
                if (next.isNotEmpty()) item {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.06f)).padding(14.dp)) {
                        Text("Next in queue", style = MaterialTheme.typography.titleSmall); Spacer(Modifier.height(8.dp))
                        next.forEach { t ->
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { musicViewModel.playTrackFromQueue(t) }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
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
}


// ---------------------------------------------------------------------------------------------
// Bottom player bar
// ---------------------------------------------------------------------------------------------
@Composable
fun DesktopPlayerBar(state: MusicUiState, musicViewModel: MusicViewModel, panelOpen: Boolean, onTogglePanel: () -> Unit, onTogglePanelLyrics: () -> Unit, onFullscreen: () -> Unit, modifier: Modifier = Modifier) {
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
    Row(modifier.fillMaxWidth().height(84.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        // left: what's playing
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            if (track != null) {
                AsyncImage(track.thumbnailUri, null, Modifier.size(58.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onFullscreen), contentScale = ContentScale.Crop)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f, fill = false)) {
                    Text(track.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, style = MaterialTheme.typography.bodySmall, color = Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Tip(if (track.isFavourite) JukeIcons.HeartSelected else JukeIcons.Heart, "Favourite", { musicViewModel.toggleFavorite(track) }, tint = if (track.isFavourite) MaterialTheme.colorScheme.primary else Dim)
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

private fun fmt(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return "%d:%02d".format(s / 60, s % 60) }

// ---------------------------------------------------------------------------------------------
// Full player: art + controls on the left, big lyrics on the right
// ---------------------------------------------------------------------------------------------
@Composable
fun DesktopPlayerScreen(state: MusicUiState, musicViewModel: MusicViewModel, onDismiss: () -> Unit, onNavigateToArtist: (String) -> Unit, onGoToAlbum: (String) -> Unit = {}, modifier: Modifier = Modifier) {
    androidx.activity.compose.BackHandler(onBack = onDismiss)
    val track = state.currentTrack
    val tint = state.extractedColors?.primary ?: MaterialTheme.colorScheme.primary
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
    Box(modifier.fillMaxSize().background(Color(0xFF0B0B0B))) {
        if (track?.thumbnailUri != null) AsyncImage(track.thumbnailUri, null, Modifier.fillMaxSize().scale(1.3f).blur(110.dp), contentScale = ContentScale.Crop, alpha = 0.55f)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.62f), Color.Black.copy(alpha = 0.82f)))))
        if (track == null) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Nothing playing", color = Dim) }; return@Box }
        Row(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 40.dp), horizontalArrangement = Arrangement.spacedBy(64.dp)) {
            Column(Modifier.weight(0.85f).fillMaxHeight(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.Start) {
                AsyncImage(track.thumbnailUri, track.title, Modifier.widthIn(max = 460.dp).fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(18.dp)), contentScale = ContentScale.Crop)
                Spacer(Modifier.height(28.dp))
                Row(Modifier.widthIn(max = 460.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(track.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(track.artist, style = MaterialTheme.typography.titleMedium, color = Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Tip(if (track.isFavourite) JukeIcons.HeartSelected else JukeIcons.Heart, "Favourite", { musicViewModel.toggleFavorite(track) }, size = 44.dp, tint = if (track.isFavourite) MaterialTheme.colorScheme.primary else Dim)
                }
                Spacer(Modifier.height(18.dp))
                Column(Modifier.widthIn(max = 460.dp).fillMaxWidth()) {
                    PlaybackSeekSlider(state.position, if (state.duration > 0) state.duration else musicViewModel.playbackManager.getDuration(), musicViewModel::seekTo)
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(fmt(state.position), style = MaterialTheme.typography.labelMedium, color = Dim); Text(fmt(state.duration), style = MaterialTheme.typography.labelMedium, color = Dim) }
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
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
            LyricsView(track, state.position, big = true, onSeek = musicViewModel::seekTo, musicViewModel = musicViewModel, modifier = Modifier.weight(1.15f).fillMaxHeight())
        }
        Tip(JukeIcons.ChevronDown, "Close", onDismiss, Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 12.dp).padding(start = titleInsetDp()), size = 44.dp, tint = MaterialTheme.colorScheme.onSurface)
        if (track != null) PlayerOptionsButton(track, musicViewModel, onGoToAlbum = onGoToAlbum, modifier = Modifier.align(Alignment.TopEnd).padding(end = 20.dp, top = 12.dp))
    }
    }
}

/** Extra left padding for macOS traffic lights when content runs under the title bar. */
@Composable fun titleInsetDp(): Dp = LocalTitleInset.current
val LocalTitleInset = staticCompositionLocalOf { 0.dp }
