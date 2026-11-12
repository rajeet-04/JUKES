package com.example.juke.ui.components.player

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.key
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.juke.models.Track
import com.example.juke.ui.icons.JukeIcons
import com.example.juke.ui.screens.parseSyncedLyrics
import com.example.juke.ui.theme.GlassCard
import com.example.juke.utils.rememberJukeHaptics
import com.example.juke.viewmodels.MusicViewModel
import java.util.Locale
import kotlin.math.roundToInt

/** Last scroll position of plain lyrics per song (first visible item, offset), kept across overlay closes. */
private object PlainLyricsScroll {
    val positions = HashMap<String, Pair<Int, Int>>()
}

/** Resting opacity of lines already sung, and of lines still to come (the active line is 1). */
private const val SUNG_ALPHA = 0.30f
private const val AHEAD_ALPHA = 0.52f

/**
 * Masks the lyrics so they dissolve at both ends. Nothing shows in the top [clear] band, where the
 * sync and close buttons sit, so a line never runs under them; it fades in over the next [fade].
 */
private fun Modifier.fadingEdges(
    clear: Dp = 52.dp,
    fade: Dp = 56.dp,
    bottomFraction: Float = 0.22f
): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        val top = (clear.toPx() / size.height).coerceIn(0f, 0.3f)
        val gradient = Brush.verticalGradient(
            0f to Color.Transparent,
            top to Color.Transparent,
            (top + fade.toPx() / size.height).coerceIn(top, 0.5f) to Color.Black,
            (1f - bottomFraction) to Color.Black,
            1f to Color.Transparent
        )
        drawRect(brush = gradient, blendMode = BlendMode.DstIn)
    }

/**
 * Applies a radial alpha mask so blur falls off smoothly toward edges
 * instead of ending with a hard rectangular transition.
 */
@Composable
fun LyricsOverlay(
    currentTrack: Track,
    currentPosition: Long,
    musicViewModel: MusicViewModel,
    isTablet: Boolean,
    onDismiss: () -> Unit
) {
    val density = LocalDensity.current
    val haptic = rememberJukeHaptics()
    val syncedLyrics = currentTrack.syncedLyrics?.takeIf { it.isNotBlank() }
    val plainLyrics = currentTrack.plainLyrics?.takeIf { it.isNotBlank() }
    var localOffsetMs by remember(currentTrack.uuid) {
        mutableFloatStateOf(currentTrack.lyricsOffsetMs.toFloat())
    }
    var lastTick by remember(currentTrack.uuid) {
        mutableIntStateOf((currentTrack.lyricsOffsetMs / 100L).toInt())
    }
    var showSyncControls by remember(currentTrack.uuid) { mutableStateOf(false) }
    // Loading only while a lookup really runs (the server may take a while to align lyrics);
    // once it finishes empty, say so.
    val searching by musicViewModel.lyricsSearching.collectAsState()
    val lyricsPending = syncedLyrics == null && plainLyrics == null && currentTrack.uuid in searching

    // Measure the actual overlay height to compute center padding dynamically
    var overlayHeightPx by remember { mutableIntStateOf(0) }
    // Active line rests ~34% from the top: clear of the buttons, with room for the lines to come
    val topPadding = with(density) { (overlayHeightPx * 0.34f).toDp() }
    val bottomPadding = with(density) { (overlayHeightPx * 0.66f).toDp() }

    // Premium full-bleed frosted background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { overlayHeightPx = it.height }
    ) {
        if (!currentTrack.thumbnailUri.isNullOrBlank()) {
            val blurDp = if (isTablet) 92.dp else 72.dp
            AsyncImage(
                model = currentTrack.thumbnailUri,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(1.2f)
                    .blur(blurDp, edgeTreatment = BlurredEdgeTreatment.Unbounded),
                contentScale = ContentScale.Crop
            )
        }

        // The artwork gives the card its colour only: under this scrim its light and dark patches
        // even out, so every line has the same contrast wherever it sits.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.50f),
                            Color.Black.copy(alpha = 0.56f),
                            Color.Black.copy(alpha = 0.68f)
                        )
                    )
                )
        )

        val mode = if (syncedLyrics != null) 0 else if (lyricsPending) 1 else 2
        Crossfade(targetState = mode, animationSpec = tween(220), label = "lyricsMode") { shown ->
        if (shown == 0 && syncedLyrics != null) {
            val lyricLines = remember(syncedLyrics, localOffsetMs) {
                parseSyncedLyrics(syncedLyrics, localOffsetMs.toLong())
            }
            val listState = rememberLazyListState()
            val playing = musicViewModel.uiState.collectAsState().value.isPlaying
            // Before the first timestamp the first line is the active one.
            val currentLineIndex = remember(lyricLines, currentPosition) {
                lyricLines.indexOfLast { it.timeMs <= currentPosition }.coerceAtLeast(0)
            }
            var isFirstScroll by remember(currentTrack.uuid) { mutableStateOf(true) }

            // Keyed on the line, not the position: a per-tick key cancels the glide mid-flight and
            // leaves the active line stranded away from its resting spot.
            LaunchedEffect(currentLineIndex, lyricLines) {
                if (lyricLines.isEmpty()) return@LaunchedEffect
                if (isFirstScroll) {
                    listState.scrollToItem(index = currentLineIndex, scrollOffset = 0)
                    isFirstScroll = false
                } else {
                    listState.glideTo(currentLineIndex)
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .fadingEdges(),
                contentPadding = PaddingValues(top = topPadding, bottom = bottomPadding),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                items(lyricLines.size) { index ->
                    val line = lyricLines[index]
                    val isCurrentLine = index == currentLineIndex

                    // The active line is full size and opaque. Lines already sung fall further
                    // back than the ones still to come, so the eye is led down the page. Only layer
                    // properties animate, so the text never re-wraps or recomposes per frame.
                    val emphasis = animateFloatAsState(
                        targetValue = if (isCurrentLine) 1f else 0f,
                        animationSpec = spring(dampingRatio = 1f, stiffness = 260f),
                        label = "lyricEmphasis"
                    )
                    val rest = animateFloatAsState(
                        targetValue = if (index < currentLineIndex) SUNG_ALPHA else AHEAD_ALPHA,
                        animationSpec = spring(dampingRatio = 1f, stiffness = 120f),
                        label = "lyricRest"
                    )

                    // Word-synced lyrics are sung word by word on the active line; its upcoming
                    // words are as dim as the lines still to come.
                    val text = if (isCurrentLine && line.words.isNotEmpty()) {
                        val now = rememberFrameClock(currentPosition, playing)
                        karaokeText(line, now(), Color.White, dimAlpha = AHEAD_ALPHA)
                    } else androidx.compose.ui.text.AnnotatedString(line.text)
                    Text(
                        text = text,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 25.sp,
                            lineHeight = 32.sp,
                            letterSpacing = (-0.4).sp
                        ),
                        color = Color.White,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                val e = emphasis.value
                                alpha = rest.value + (1f - rest.value) * e
                                val scale = 0.94f + 0.06f * e
                                scaleX = scale
                                scaleY = scale
                                transformOrigin = TransformOrigin(0f, 0.5f)
                            }
                            .clickable(onClick = {
                                musicViewModel.seekTo(line.timeMs)
                            }),
                        textAlign = TextAlign.Start,
                        fontWeight = FontWeight.Bold,
                        overflow = TextOverflow.Clip,
                        softWrap = true
                    )
                }
            }
        } else if (shown == 1) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp
                    )
                    Text(
                        text = "Loading lyrics...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.72f),
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
        } else {
            // Plain lyrics (no syncing): same left-aligned reading style as the synced view, and the
            // scroll position is remembered per song so closing and reopening resumes in place.
            if (plainLyrics == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No lyrics available",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.72f)
                    )
                }
            } else key(currentTrack.uuid) {
                val rows = remember(plainLyrics) { plainLyrics.trim().lines().map { it.trim() } }
                val saved = PlainLyricsScroll.positions[currentTrack.uuid]
                val listState = rememberLazyListState(saved?.first ?: 0, saved?.second ?: 0)
                DisposableEffect(Unit) {
                    onDispose {
                        if (PlainLyricsScroll.positions.size > 50) PlainLyricsScroll.positions.clear()
                        PlainLyricsScroll.positions[currentTrack.uuid] =
                            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                    }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp)
                        .fadingEdges(bottomFraction = 0.14f),
                    // Top clears the sync/close buttons; bottom lets the last line scroll to mid-card.
                    contentPadding = PaddingValues(top = 84.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(rows.size) { i ->
                        val line = rows[i]
                        if (line.isEmpty()) {
                            Spacer(Modifier.height(16.dp))
                        } else {
                            Text(
                                text = line,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontSize = 21.sp,
                                    lineHeight = 30.sp,
                                    letterSpacing = (-0.2).sp
                                ),
                                color = Color.White.copy(alpha = 0.86f),
                                textAlign = TextAlign.Start,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
        }


        // Sync controls use a dense glass surface so lyric text cannot bleed through.
        if (syncedLyrics != null && showSyncControls) {
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f))
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                LyricsSyncPanel(
                    offsetMs = localOffsetMs,
                    onOffsetChange = { value ->
                        val step = (value / 100f).roundToInt()
                        if (step != lastTick) lastTick = step
                        localOffsetMs = (step * 100f).coerceIn(-60000f, 60000f)
                    },
                    onSave = { musicViewModel.saveLyricsOffset(currentTrack, localOffsetMs.toLong()) },
                    onReset = {
                        haptic.click()
                        localOffsetMs = 0f
                        lastTick = 0
                        musicViewModel.saveLyricsOffset(currentTrack, 0L)
                    },
                    onDone = {
                        haptic.click()
                        musicViewModel.saveLyricsOffset(currentTrack, localOffsetMs.toLong())
                        showSyncControls = false
                    }
                )
            }
        }

        // Top action buttons
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (syncedLyrics != null) {
                        IconButton(
                            onClick = { showSyncControls = !showSyncControls },
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    Color.White.copy(alpha = if (showSyncControls) 0.18f else 0.10f),
                                    CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = JukeIcons.Equalizer,
                                contentDescription = "Adjust lyrics timing",
                                modifier = Modifier.size(20.dp),
                                tint = Color.White.copy(alpha = 0.86f)
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.White.copy(alpha = 0.10f), CircleShape)
                ) {
                    Icon(
                        imageVector = JukeIcons.Close,
                        contentDescription = "Close lyrics",
                        modifier = Modifier.size(20.dp),
                        tint = Color.White.copy(alpha = 0.86f)
                    )
                }
            }
        }
    }
}

/** User adjustment is relative to the fixed internal render lead; zero remains the default. */
@Composable
private fun LyricsSyncPanel(
    offsetMs: Float,
    onOffsetChange: (Float) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    val foreground = Color(0xFFF4F4F5)
    val secondary = Color(0xFFB7B7BD)
    val buttonColors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = foreground)
    GlassCard(
        modifier = Modifier.fillMaxWidth()
            .clip(shape)
            .background(Color(0xFF18181B).copy(alpha = 0.96f)),
        shape = shape,
        level = com.example.juke.ui.theme.GlassLevel.Thin
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Lyrics sync", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, color = foreground)
                val sign = if (offsetMs > 0f) "+" else ""
                Text("$sign${String.format(Locale.US, "%.1f", offsetMs / 1000f)} s",
                    style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.SemiBold, color = foreground)
            }
            Text("Adjust when each line appears", style = MaterialTheme.typography.bodySmall, color = secondary)
            Column {
                Slider(
                    value = offsetMs.coerceIn(-60000f, 60000f),
                    onValueChange = onOffsetChange,
                    onValueChangeFinished = onSave,
                    valueRange = -60000f..60000f,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Lyrics timing offset" },
                    colors = SliderDefaults.colors(thumbColor = foreground, activeTrackColor = foreground,
                        inactiveTrackColor = Color.White.copy(alpha = 0.18f))
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("−60 s · Earlier", style = MaterialTheme.typography.labelSmall, color = secondary)
                    Text("Later · +60 s", style = MaterialTheme.typography.labelSmall, color = secondary)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                androidx.compose.material3.OutlinedButton(
                    onClick = { onOffsetChange((offsetMs - 500f).coerceAtLeast(-60000f)); onSave() },
                    enabled = offsetMs > -60000f,
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = buttonColors
                ) { Text("−0.5 s") }
                androidx.compose.material3.OutlinedButton(
                    onClick = { onOffsetChange((offsetMs + 500f).coerceAtMost(60000f)); onSave() },
                    enabled = offsetMs < 60000f,
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = buttonColors
                ) { Text("+0.5 s") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onReset) { Text("Reset", color = secondary) }
                androidx.compose.material3.Button(onClick = onDone, shape = RoundedCornerShape(12.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = foreground, contentColor = Color(0xFF18181B))) { Text("Done") }
            }
        }
    }
}

/** Glide the line to its resting position on a critically damped spring (no snap, no bounce). */
private suspend fun androidx.compose.foundation.lazy.LazyListState.glideTo(index: Int) {
    val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    if (item == null) {
        animateScrollToItem(index, 0)
    } else {
        val delta = item.offset - layoutInfo.beforeContentPadding
        if (delta != 0) animateScrollBy(delta.toFloat(), spring(dampingRatio = 1f, stiffness = 180f))
    }
}
