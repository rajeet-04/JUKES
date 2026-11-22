package com.example.juke.ui.screens

import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import com.example.juke.ui.theme.glassPane
import com.example.juke.ui.theme.liquidGlass
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import com.example.juke.viewmodels.MusicViewModel
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import dev.chrisbanes.haze.hazeSource
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.draw.drawWithContent
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.onPlaced
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.juke.models.Track
import com.example.juke.ui.components.player.rememberFrameClock
import com.example.juke.ui.components.playerArtworkEndpoint
import com.example.juke.ui.icons.JukeIcons
import com.example.juke.ui.theme.ExtractedColors

/** What the phone player's stage shows. The controls under it never move. */
enum class PlayerStage { Art, Lyrics, Queue, Mix }

private val Base = Color(0xFF0E0B0C)

/**
 * The player's backdrop: a dark wash of the artwork's deep colour at the top, fading to near-black
 * where the controls sit. The lyrics view gets a slightly warmer wash so the switch is felt.
 */
@Composable
internal fun ArtBackdrop(colors: ExtractedColors?, stage: PlayerStage, modifier: Modifier = Modifier, artwork: Any? = null) {
    val tint = colors?.secondary ?: Base
    val strength = if (stage == PlayerStage.Lyrics) 0.62f else 0.45f
    val top by animateColorAsState(lerp(Base, tint, strength), tween(450), label = "backdropTop")
    val mid by animateColorAsState(lerp(Base, tint, strength * 0.4f), tween(450), label = "backdropMid")
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(0f to top, 0.5f to mid, 1f to Base))
    ) {
        // Where glass is live, a huge, heavily blurred copy of the artwork gives the player's
        // glass real colour structure to bend and split into a rainbow (a smooth gradient alone
        // refracts to nothing). It fades out toward the controls so they stay legible.
        if (artwork != null && com.example.juke.ui.theme.glassIsLive()) {
            androidx.compose.animation.Crossfade(artwork, animationSpec = tween(600), label = "backdropArt") { art ->
                coil.compose.AsyncImage(
                    model = art,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { scaleX = 1.5f; scaleY = 1.5f; alpha = if (stage == PlayerStage.Art) 0.5f else 0.38f }
                        .blur(64.dp)
                )
            }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 0.55f to Base.copy(alpha = 0.35f), 1f to Base.copy(alpha = 0.85f))))
        }
    }
}

/**
 * The line being sung under the artwork, with the next line dimmed beneath it. The block has a
 * fixed height so the title and controls never shift as lines change; a new line rolls up into it.
 * Tap opens the lyrics view. [position] is read only to find the line, so a tick redraws nothing
 * until the line changes.
 */
@Composable
internal fun LyricTicker(
    syncedLyrics: String,
    offsetMs: Long,
    position: () -> Long,
    playing: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lines = remember(syncedLyrics, offsetMs) { parseSyncedLyrics(syncedLyrics, offsetMs) }
    val index by remember(lines) { androidx.compose.runtime.derivedStateOf { lines.indexOfLast { it.timeMs <= position() } } }
    val clock = if (lines.any { it.words.isNotEmpty() }) rememberFrameClock(position, playing) else null
    val current = lines.getOrNull(index)
    val next = lines.getOrNull(index + 1)
    Row(
        modifier
            .graphicsLayer()
            .fillMaxWidth()
            .height(TICKER_HEIGHT)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClickLabel = "Open lyrics", onClick = onOpen)
            // The text lines up with the artwork; the clip (which makes the roll) stays wider so
            // the glow fades out inside it instead of being cut at the text's edge.
            .padding(horizontal = com.example.juke.ui.components.player.ART_INSET)
    ) {
        AnimatedContent(
            targetState = current to next,
            // Rolls like a drum: the sung line leaves upward as the next comes up from below. Slide
            // only, no fade: fading glowing text re-blurs it in an offscreen layer every frame.
            transitionSpec = {
                val roll = androidx.compose.animation.core.spring<androidx.compose.ui.unit.IntOffset>(dampingRatio = 0.9f, stiffness = 380f)
                // No clip here: the ticker's own (wider) clip contains the roll and leaves room for the glow.
                (androidx.compose.animation.slideInVertically(roll) { it } togetherWith androidx.compose.animation.slideOutVertically(roll) { -it })
                    .using(androidx.compose.animation.SizeTransform(clip = false))
            },
            label = "lyricTicker",
            modifier = Modifier.weight(1f).fillMaxHeight()
        ) { (now, upcoming) ->
            var nowLines by remember(now) { mutableIntStateOf(1) }
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                val style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold)
                if (now == null) Text(
                    upcoming?.text ?: "♪", style = style, color = Color.White.copy(alpha = com.example.juke.ui.components.player.FROST_ALPHA),
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                ) else com.example.juke.ui.components.player.KaraokeLine(
                    now, clock?.takeIf { now.words.isNotEmpty() }, style, maxLines = 2, onLineCount = { nowLines = it }
                )
                // A two-line current line takes the whole block; the next line would only be clipped.
                if (now != null && upcoming != null && nowLines < 2) {
                    Text(
                        upcoming.text,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 18.sp),
                        color = Color.White.copy(alpha = com.example.juke.ui.components.player.FROST_ALPHA),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

internal val TICKER_HEIGHT = 52.dp

/** The current song in one row, shown above the lyrics, queue and mix views. Tap returns to the art. */
@Composable
internal fun StageMiniHeader(
    track: Track,
    onOpenArt: () -> Unit,
    onFavourite: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClickLabel = "Show artwork", onClick = onOpenArt)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(44.dp)
                // The player sheet lands on (and leaves from) this thumbnail while the art is hidden.
                .playerArtworkEndpoint(expanded = true)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.08f))
        ) {
            if (track.thumbnailUri != null) {
                AsyncImage(
                    model = track.thumbnailUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                track.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.68f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onFavourite) {
            Icon(
                if (track.isFavourite) JukeIcons.HeartSelected else JukeIcons.Heart,
                contentDescription = if (track.isFavourite) "Unfavorite" else "Favorite",
                tint = if (track.isFavourite) MaterialTheme.colorScheme.primary else Color.White
            )
        }
    }
}

/**
 * Lyrics, Up next and Mix. Tapping the open tab goes back to the artwork. The open tab sits under
 * a liquid lens that slides between tabs like the main tab bar's: a loose spring, stretched along
 * its travel and squashed across it in proportion to speed, its rim flaring while it moves.
 */
@Composable
internal fun StageDock(
    stage: PlayerStage,
    onSelect: (PlayerStage) -> Unit,
    modifier: Modifier = Modifier
) {
    val live = com.example.juke.ui.theme.glassIsLive()
    val tabs = listOf(PlayerStage.Lyrics to "Lyrics", PlayerStage.Queue to "Up next", PlayerStage.Mix to "Mix")
    val bounds = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateMapOf<Int, Pair<Float, androidx.compose.ui.unit.IntSize>>() }
    val selectedIndex = tabs.indexOfFirst { it.first == stage }
    val pos = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    var placed by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var dragging by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val currentStage by androidx.compose.runtime.rememberUpdatedState(stage)
    val select by androidx.compose.runtime.rememberUpdatedState(onSelect)
    val lensSpring = androidx.compose.animation.core.spring<Float>(dampingRatio = 0.8f, stiffness = 300f)
    val target = bounds[selectedIndex]
    // Back on the artwork no tab is open: the lens fades where it is and reappears at the next tab.
    val shown by androidx.compose.animation.core.animateFloatAsState(
        if (selectedIndex >= 0 || dragging) 1f else 0f, tween(180), label = "lensShown"
    )
    androidx.compose.runtime.LaunchedEffect(target, dragging) {
        if (dragging) return@LaunchedEffect
        val x = target?.first ?: return@LaunchedEffect
        if (!placed || shown < 0.05f) { pos.snapTo(x); placed = true } else pos.animateTo(x, lensSpring)
    }
    // Like the main tab bar: the lens can be dragged and lets go onto the nearest tab.
    val drag = Modifier.pointerInput(Unit) {
        detectHorizontalDragGestures(
            onDragStart = { at ->
                val w = bounds.values.firstOrNull()?.second?.width ?: 0
                val lo = bounds.values.minOfOrNull { it.first } ?: 0f
                val hi = bounds.values.maxOfOrNull { it.first } ?: 0f
                // From the artwork (no lens yet) it appears under the finger.
                if (tabs.none { it.first == currentStage }) scope.launch { pos.snapTo((at.x - w / 2f).coerceIn(lo, hi)) }
                placed = true
                dragging = true
            },
            onDragCancel = { dragging = false },
            onDragEnd = {
                val p = pos.value
                val nearest = bounds.entries.minByOrNull { (_, v) -> kotlin.math.abs(v.first - p) }?.key
                dragging = false
                val tab = nearest?.let { tabs.getOrNull(it)?.first }
                if (tab != null && tab != currentStage) select(tab)
                else nearest?.let { bounds[it] }?.let { scope.launch { pos.animateTo(it.first, lensSpring) } }
            },
            onHorizontalDrag = { change, dx ->
                change.consume()
                val lo = bounds.values.minOfOrNull { it.first } ?: 0f
                val hi = bounds.values.maxOfOrNull { it.first } ?: 0f
                scope.launch { pos.snapTo((pos.value + dx).coerceIn(lo, hi)) }
            }
        )
    }
    val lensSize = (target ?: bounds.values.firstOrNull())?.second
    val dockHaze = androidx.compose.runtime.remember { dev.chrisbanes.haze.HazeState() }

    Box(
        modifier
            .fillMaxWidth()
            .then(
                if (live) Modifier.glassPane(RoundedCornerShape(18.dp), com.example.juke.ui.theme.GlassLevel.Thin)
                else Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.07f))
            )
            .then(drag)
            .padding(4.dp)
    ) {
        // The labels are what the lens refracts: they are its haze source, and the part under the
        // lens is cut out of their normal drawing so only the bent copy shows there.
        val lensCorner = with(androidx.compose.ui.platform.LocalDensity.current) { 14.dp.toPx() }
        Row(
            Modifier
                .fillMaxWidth()
                .then(
                    if (live && lensSize != null && placed) Modifier.drawWithContent {
                        if (shown < 0.5f) { drawContent(); return@drawWithContent }
                        val hole = androidx.compose.ui.graphics.Path().apply {
                            addRoundRect(
                                androidx.compose.ui.geometry.RoundRect(
                                    pos.value, 0f, pos.value + lensSize.width, lensSize.height.toFloat(),
                                    androidx.compose.ui.geometry.CornerRadius(lensCorner)
                                )
                            )
                        }
                        clipPath(hole, androidx.compose.ui.graphics.ClipOp.Difference) { this@drawWithContent.drawContent() }
                    } else Modifier
                )
                .hazeSource(dockHaze),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tabs.forEachIndexed { i, (tab, label) ->
                val on = stage == tab
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .onPlaced { bounds[i] = it.positionInParent().x to it.size }
                        .clip(RoundedCornerShape(12.dp))
                        .semantics { selected = on }
                        .clickable(role = Role.Tab) { onSelect(if (on) PlayerStage.Art else tab) },
                    contentAlignment = Alignment.Center
                ) {
                    val alpha by androidx.compose.animation.core.animateFloatAsState(if (on) 1f else 0.7f, tween(180), label = "dockLabel")
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                        color = Color.White.copy(alpha = alpha)
                    )
                }
            }
        }
        if (lensSize != null && placed) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val speed = { (kotlin.math.abs(pos.velocity) / 4000f).coerceIn(0f, 1f) }
            Box(
                Modifier
                    .offset { androidx.compose.ui.unit.IntOffset(pos.value.toInt(), 0) }
                    .size(with(density) { lensSize.width.toDp() }, with(density) { lensSize.height.toDp() })
                    .graphicsLayer {
                        val v = (kotlin.math.abs(pos.velocity) / 5000f).coerceIn(0f, 1f)
                        val grab = if (dragging) 0.08f else 0f
                        scaleX = 1f + 0.38f * v + grab
                        scaleY = 1f - 0.16f * v + grab * 0.5f
                        alpha = shown
                    }
                    .then(
                        // Pure clear glass over the labels: no tint, no frost; it bends the text
                        // under it, with the rim and its rainbow edge (full while moving or held).
                        if (live) Modifier.liquidGlass(
                            RoundedCornerShape(14.dp), Color.Transparent, blur = 0.dp, rimAlpha = 0.7f, source = dockHaze,
                            // Small, short lens: a gentle bend at the rim, no fold (it mirrored the text).
                            displacement = 14.dp, edge = 10.dp, fold = 0f,
                            strength = { 0.5f + speed() + if (dragging) 0.5f else 0f }
                        )
                        else Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.12f))
                    )
            )
        }
    }
}

/**
 * Start a mix from the current song. The mix is built while this view is open (skeleton rows
 * until it lands) so Start mix swaps Up Next in at once; tapping a song starts the mix there.
 */
@Composable
internal fun MixStage(
    track: Track,
    preview: MusicViewModel.MixPreviewState,
    onStartMix: () -> Unit,
    onPlayPicked: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // A preview for an earlier song counts as loading: the new one is on its way. Idle (in a Jam) is not.
    val loading = preview !is MusicViewModel.MixPreviewState.Idle &&
        (preview is MusicViewModel.MixPreviewState.Loading || preview.trackUuid != track.uuid)
    val songs = (preview as? MusicViewModel.MixPreviewState.Ready)?.takeIf { it.trackUuid == track.uuid }?.songs.orEmpty()
    val failed = preview is MusicViewModel.MixPreviewState.Failed && preview.trackUuid == track.uuid
    LazyColumn(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item(key = "start") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.07f))
                    .padding(18.dp)
            ) {
                Text("Mix from this song", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    "Replaces what's up next with songs that sound like ${track.title}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.68f),
                    modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
                )
                val enabled = !loading
                Box(
                    Modifier
                        .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(enabled = enabled, onClick = onStartMix)
                        .padding(horizontal = 20.dp, vertical = 11.dp)
                ) {
                    Text(
                        if (loading) "Building mix…" else "Start mix",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
        if (loading || failed || songs.isNotEmpty()) item(key = "pickedHeader") {
            Text(
                if (failed) "Couldn't preview this mix. Start mix still builds one." else "Picked for your mix",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 10.dp)
            )
        }
        if (loading) {
            items(6, key = { "skel$it" }) { MixSkeletonRow(it) }
        }
        items(songs, key = { it.videoId }) { song ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClickLabel = "Start the mix with ${song.title}") { onPlayPicked(song.videoId) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = song.art,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.08f))
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, style = MaterialTheme.typography.bodyLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** A placeholder song row that breathes while the mix loads; rows are staggered so it reads as loading, not frozen. */
@Composable
private fun MixSkeletonRow(index: Int) {
    val pulse = rememberInfiniteTransition(label = "mixSkeleton")
    val a by pulse.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.13f,
        animationSpec = infiniteRepeatable(tween(700, delayMillis = index * 90), RepeatMode.Reverse),
        label = "mixSkeletonAlpha"
    )
    val block = Color.White.copy(alpha = a)
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(block))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(Modifier.fillMaxWidth(0.62f - (index % 3) * 0.1f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(block))
            Box(Modifier.fillMaxWidth(0.36f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(block))
        }
    }
}

/** Crossfades the stage between views, with a slight grow on the way in. */
@Composable
internal fun StageSwitcher(
    stage: PlayerStage,
    modifier: Modifier = Modifier,
    content: @Composable (PlayerStage) -> Unit
) {
    AnimatedContent(
        targetState = stage,
        transitionSpec = {
            (fadeIn(tween(240, delayMillis = 60)) + scaleIn(tween(300), initialScale = 0.97f)) togetherWith fadeOut(tween(140))
        },
        label = "playerStage",
        modifier = modifier
    ) { content(it) }
}

/** Square artwork inset from the edges, with the lyric ticker under it when there are synced lyrics. */
@Composable
internal fun ArtSlot(
    hasTicker: Boolean,
    modifier: Modifier = Modifier,
    artwork: @Composable (Modifier) -> Unit,
    ticker: @Composable (Modifier) -> Unit
) {
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val tickerSpace = if (hasTicker) TICKER_HEIGHT + 16.dp else 0.dp
        val side = minOf(maxWidth - 16.dp, maxHeight - tickerSpace).coerceAtLeast(0.dp)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            artwork(Modifier.size(side))
            if (hasTicker) {
                Spacer(Modifier.height(16.dp))
                ticker(Modifier.width(side))
            }
        }
    }
}
