package com.example.juke.ui.screens.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.juke.home.FeedSong
import com.example.juke.home.HomeSection
import com.example.juke.models.SpotifyAlbum
import com.example.juke.models.Track
import com.example.juke.ui.components.ActionMenu
import com.example.juke.ui.components.ArrowRow
import com.example.juke.ui.components.GlassPillButton
import com.example.juke.ui.components.MenuAction
import com.example.juke.ui.components.libraryTrackActions
import com.example.juke.ui.components.onRightClick
import com.example.juke.ui.components.shareAction
import com.example.juke.ui.icons.JukeIcons
import com.example.juke.ui.theme.GlassLevel
import com.example.juke.ui.theme.GlassShapes
import com.example.juke.ui.theme.glassPane
import com.example.juke.utils.rememberJukeHaptics
import com.example.juke.viewmodels.MusicViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** One Home section, drawn in the shape its type calls for. Every surface is liquid glass. */
@Composable
fun HomeSectionView(
    section: HomeSection,
    musicViewModel: MusicViewModel,
    onAlbumClick: (SpotifyAlbum) -> Unit,
    onSeeAll: () -> Unit,
) {
    val loadingId by musicViewModel.recLoading.collectAsStateWithLifecycle()
    val play: (FeedSong) -> Unit = { musicViewModel.playRec(it) }
    when (section) {
        is HomeSection.QuickPicks -> {
            SectionHeader(section.title, action = "Play all") { section.songs.firstOrNull()?.let(play) }
            QuickPicksPager(section.songs, loadingId, play)
        }
        is HomeSection.SpeedDial -> {
            SectionHeader(section.title)
            SpeedDialGrid(section.tracks, musicViewModel)
        }
        is HomeSection.SongRow -> {
            SectionHeader(section.title)
            ArrowRow {
                items(section.songs, key = { it.videoId }) { song ->
                    CoverCard(
                        title = song.title, subtitle = song.artist, imageUrl = song.art,
                        loading = loadingId == song.videoId,
                        description = "${song.title} by ${song.artist}",
                        actions = listOf(MenuAction("Play") { play(song) }),
                    ) { play(song) }
                }
            }
        }
        is HomeSection.LocalRow -> {
            SectionHeader(section.title, action = if (section.id == "rotation") "See all" else null, onAction = onSeeAll)
            ArrowRow {
                itemsIndexed(section.tracks, key = { _, t -> t.uuid }) { index, track ->
                    val start = { musicViewModel.setQueue(section.tracks, index, jamSingle = true) }
                    CoverCard(
                        title = track.title, subtitle = track.artist, imageUrl = track.thumbnailUri,
                        description = "${track.title} by ${track.artist}",
                        actions = libraryTrackActions(track, musicViewModel, start),
                        onClick = start,
                    )
                }
            }
        }
        is HomeSection.Releases -> {
            SectionHeader(section.title)
            ArrowRow {
                items(section.albums, key = { it.id.orEmpty() }) { album ->
                    ReleaseCard(
                        album = album,
                        onClick = { onAlbumClick(album) },
                        actions = listOf(
                            MenuAction("Open album") { onAlbumClick(album) },
                            MenuAction("Play") { musicViewModel.playAlbum(album, shuffle = false) },
                            MenuAction("Shuffle") { musicViewModel.playAlbum(album, shuffle = true) }
                        ) + shareAction("album", album.id)
                    )
                }
            }
        }
        is HomeSection.HeroMix -> {
            SectionHeader(section.title)
            HeroMixCard(section, loadingId, play)
        }
        is HomeSection.Ranked -> {
            SectionHeader(section.title, action = "Play all") { section.songs.firstOrNull()?.let(play) }
            RankedPane(section.songs, loadingId, play)
        }
    }
}

@Composable
internal fun SectionHeader(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (action != null) {
            TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                Text(action, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * Quick picks: songs in glass pages of four rows. Each page is a little narrower than the screen
 * so the next one peeks in, and a fling settles on a page edge.
 */
@Composable
private fun QuickPicksPager(songs: List<FeedSong>, loadingId: String?, play: (FeedSong) -> Unit) {
    val pages = remember(songs) { songs.chunked(4) }
    val state = rememberLazyListState()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val pageWidth = maxWidth - 56.dp
        LazyRow(
            state = state,
            flingBehavior = rememberSnapFlingBehavior(state),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(pages, key = { it.first().videoId }) { page ->
                Column(
                    Modifier
                        .width(pageWidth)
                        .glassPane(GlassShapes.Card, GlassLevel.Regular)
                        .padding(vertical = 6.dp)
                ) {
                    page.forEach { song -> SongLine(song, loadingId == song.videoId, leading = null) { play(song) } }
                }
            }
        }
    }
}

/** Numbered chart rows in one glass pane. */
@Composable
private fun RankedPane(songs: List<FeedSong>, loadingId: String?, play: (FeedSong) -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .glassPane(GlassShapes.Card, GlassLevel.Regular)
            .padding(vertical = 6.dp)
    ) {
        songs.forEachIndexed { i, song -> SongLine(song, loadingId == song.videoId, leading = "${i + 1}") { play(song) } }
    }
}

@Composable
private fun SongLine(song: FeedSong, loading: Boolean, leading: String?, onClick: () -> Unit) {
    val haptic = rememberJukeHaptics()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium), label = "lineScale")
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .combinedClickable(interactionSource = interaction, indication = null, role = Role.Button, onClickLabel = "Play ${song.title}") {
                haptic.click(); onClick()
            }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Text(
                leading,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(28.dp),
            )
        }
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))) {
            ArtImage(song.art, Modifier.fillMaxSize())
            if (loading) LoadingVeil()
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(song.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Speed dial: the nine songs played most, as square tiles on one glass panel. */
@Composable
private fun SpeedDialGrid(tracks: List<Track>, musicViewModel: MusicViewModel) {
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .glassPane(GlassShapes.Card, GlassLevel.Regular)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tracks.chunked(3).forEachIndexed { row, chunk ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                chunk.forEachIndexed { col, track ->
                    val index = row * 3 + col
                    DialTile(track, Modifier.weight(1f)) { musicViewModel.setQueue(tracks, index, jamSingle = true) }
                }
                repeat(3 - chunk.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DialTile(track: Track, modifier: Modifier, onClick: () -> Unit) {
    val haptic = rememberJukeHaptics()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium), label = "dialScale")
    Box(
        modifier
            .aspectRatio(1f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(14.dp))
            .semantics(mergeDescendants = true) { contentDescription = "${track.title} by ${track.artist}" }
            .combinedClickable(interactionSource = interaction, indication = null, role = Role.Button, onClickLabel = "Play ${track.title}") {
                haptic.click(); onClick()
            }
    ) {
        ArtImage(track.thumbnailUri, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f))))
        Text(
            track.title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
        )
    }
}

/**
 * One mix as a big glass card. Its own artwork, blurred and dimmed, colours the glass from inside,
 * so each mix reads as a different object rather than another row.
 */
@Composable
private fun HeroMixCard(section: HomeSection.HeroMix, loadingId: String?, play: (FeedSong) -> Unit) {
    val songs = section.songs
    Box(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .glassPane(GlassShapes.Card, GlassLevel.Thick)
    ) {
        AsyncImage(
            model = songs.first().art,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize().graphicsLayer { alpha = 0.35f; scaleX = 1.4f; scaleY = 1.4f }.blur(48.dp),
        )
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(112.dp).clip(RoundedCornerShape(16.dp))) {
                    Column {
                        songs.take(4).chunked(2).forEach { pair ->
                            Row { pair.forEach { ArtImage(it.art, Modifier.size(56.dp)) } }
                        }
                    }
                }
                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                    Text(section.subtitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${songs.size} songs", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    androidx.compose.foundation.layout.Spacer(Modifier.height(10.dp))
                    GlassPillButton(text = "Play", onClick = { play(songs.first()) })
                }
            }
            androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
            songs.drop(4).take(3).forEach { song -> SongLine(song, loadingId == song.videoId, leading = null) { play(song) } }
        }
    }
}

@Composable
private fun LoadingVeil() {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
    }
}

private val releaseDateFormat = DateTimeFormatter.ofPattern("MMM d")

@Composable
private fun ReleaseCard(album: SpotifyAlbum, onClick: () -> Unit, actions: List<MenuAction>) {
    val haptic = rememberJukeHaptics()
    var menuOpen by remember { mutableStateOf(false) }
    val date = remember(album.releaseDate) {
        album.releaseDate?.let { runCatching { LocalDate.parse(it).format(releaseDateFormat) }.getOrNull() }
    }
    val artist = album.artists.joinToString(", ") { it.name }
    val kind = if (album.albumType == "single") "Single" else "Album"
    Column(
        modifier = Modifier
            .width(148.dp)
            .glassPane(GlassShapes.Card, GlassLevel.Regular)
            .semantics(mergeDescendants = true) {
                contentDescription = "${album.name}, $kind by $artist${date?.let { ", released $it" }.orEmpty()}"
            }
            .onRightClick { menuOpen = true }
            .combinedClickable(
                role = Role.Button,
                onClickLabel = "Open ${album.name}",
                onLongClickLabel = "More options for ${album.name}",
                onLongClick = { haptic.heavyClick(); menuOpen = true }
            ) { haptic.click(); onClick() }
    ) {
        ActionMenu(menuOpen, { menuOpen = false }, actions)
        ArtImage(album.images.firstOrNull()?.url, Modifier.fillMaxWidth().height(148.dp))
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(album.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(artist, date).joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Square art with a caption over a dark fade, on a glass card; the shared shape for song lanes. */
@Composable
private fun CoverCard(
    title: String,
    subtitle: String,
    imageUrl: String?,
    description: String,
    loading: Boolean = false,
    actions: List<MenuAction> = emptyList(),
    onClick: () -> Unit,
) {
    val haptic = rememberJukeHaptics()
    var menuOpen by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium), label = "coverCardScale")
    Box(
        modifier = Modifier
            .size(148.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .glassPane(GlassShapes.Card, GlassLevel.Regular)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .onRightClick { if (actions.isNotEmpty()) menuOpen = true }
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = "Play $title",
                onLongClickLabel = if (actions.isEmpty()) null else "More options for $title",
                onLongClick = if (actions.isEmpty()) null else ({ haptic.heavyClick(); menuOpen = true })
            ) { haptic.click(); onClick() }
    ) {
        ActionMenu(menuOpen, { menuOpen = false }, actions)
        ArtImage(imageUrl, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)), startY = 160f)))
        Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.78f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (loading) LoadingVeil()
    }
}

@Composable
internal fun ArtImage(url: String?, modifier: Modifier) {
    if (url != null) {
        AsyncImage(model = url, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(JukeIcons.MusicNote, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        }
    }
}
