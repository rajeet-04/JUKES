package com.example.juke.ui.components

import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import com.example.juke.models.Track
import kotlin.math.abs
import kotlin.math.sign
import androidx.compose.runtime.derivedStateOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.juke.ui.screens.LyricLine
import com.example.juke.ui.theme.GlassLevel
import com.example.juke.ui.theme.GlassShapes
import com.example.juke.ui.theme.glassFloat
import com.example.juke.ui.screens.parseSyncedLyrics
import com.example.juke.utils.LyricsRomanizer
import com.example.juke.utils.rememberJukeHaptics
import com.example.juke.viewmodels.MusicViewModel
import kotlinx.coroutines.delay

/** Returns true if the lyric line contains only musical notation symbols / whitespace.
 *  Such lines signal an instrumental passage and should not be shown in the mini-player. */
private fun isMusicOnlyLine(text: String): Boolean {
    // Strip all recognized music/notation Unicode symbols and whitespace, then check if empty.
    val musicPattern = Regex("[♪♫♬♩𝄞𝄢𝄫𝅗𝅝𝅗𝅥♯♭♮\\s()\\[\\]{}]+")
    return text.replace(musicPattern, "").isEmpty()
}

/**
 * Computes a per-song adaptive gap threshold for the mini-player lyric fallback.
 *
 * Strategy:
 *  1. Collect all inter-line gaps from the parsed lyrics list.
 *  2. Sort them and take the median — the median is naturally outlier-resistant,
 *     meaning a 38-second instrumental silence won't pull the value up.
 *  3. Multiply by 2.2 to get a threshold that covers genuinely long sung lines
 *     (where the LRC timestamp is set once for the start of a phrase that the
 *     singer holds for 10–14 s) while still catching real silences which always
 *     exceed the median by a wide margin.
 *  4. Clamp to [1800 ms, 12 000 ms] to handle degenerate edge-cases.
 */
private fun computeAdaptiveLyricsGapThreshold(lyrics: List<LyricLine>): Long {
    if (lyrics.size < 3) return 4_000L
    val gaps = (0 until lyrics.size - 1)
        .map { i -> lyrics[i + 1].timeMs - lyrics[i].timeMs }
        .filter { it > 0L }
        .sorted()
    if (gaps.size < 2) return 4_000L
    val median = gaps[gaps.size / 2]
    return (median * 2.2).toLong().coerceIn(1_800L, 12_000L)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MiniPlayer(
    musicViewModel: MusicViewModel,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    /** Sharing a row with the folded tab bar: one lyric line, play/pause as the only button. */
    compact: Boolean = false
) {
    val uiState by musicViewModel.uiState.collectAsStateWithLifecycle()
    val currentTrack = uiState.currentTrack
    val currentPosition = uiState.position
    val isPlaying = uiState.isPlaying
    val isRomanizedLyricsEnabled by musicViewModel.isRomanizedLyricsEnabled.collectAsStateWithLifecycle()
    val isMiniPlayerLyricsEnabled by musicViewModel.isMiniPlayerLyricsEnabled.collectAsStateWithLifecycle()
    val haptic = rememberJukeHaptics()
    val context = LocalContext.current
    // Power tools → Gestures: optional double-tap and long-press shortcuts on the mini player.
    val gesturePrefs = remember { context.getSharedPreferences("power_prefs", android.content.Context.MODE_PRIVATE) }

    // Poll for progress updates when playing
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, uiState.isPlaying) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (uiState.isPlaying) {
                musicViewModel.updateProgress()
                delay(300)
            }
        }
    }

    if (currentTrack != null) {
        // Live drag offset of the card; animates off-screen on a committed swipe and back on a cancel.
        val offsetX = remember { Animatable(0f) }
        var cardWidthPx by remember { mutableFloatStateOf(0f) }
        val swipeScope = rememberCoroutineScope()
        val currentUuid by rememberUpdatedState(currentTrack.uuid)
        val queue = uiState.queue
        val queueIdx = uiState.queueIndex
        val nextTrack = queue.getOrNull(queueIdx + 1)
        val prevTrack = queue.getOrNull(queueIdx - 1)

        var romanizedSyncedLyrics by remember(
            currentTrack.uuid,
            isRomanizedLyricsEnabled,
            currentTrack.syncedLyrics,
            currentTrack.romanizedSyncedLyrics
        ) { mutableStateOf<String?>(null) }

        LaunchedEffect(
            currentTrack.uuid,
            currentTrack.syncedLyrics,
            currentTrack.romanizedSyncedLyrics,
            isRomanizedLyricsEnabled
        ) {
            if (!isRomanizedLyricsEnabled) {
                romanizedSyncedLyrics = null
                return@LaunchedEffect
            }
            // Same rules as the full player: a saved result with non-Latin lines left is redone,
            // failed lines are retried, and only a complete result is saved.
            for (attempt in 0..3) {
                if (attempt > 0) delay(5_000L * attempt)
                val result = currentTrack.romanizedSyncedLyrics?.takeIf(LyricsRomanizer::isFullyRomanized)
                    ?: currentTrack.syncedLyrics?.let { LyricsRomanizer.romanizeSyncedLyrics(it) }
                romanizedSyncedLyrics = result
                if (result == null) break
                if (!LyricsRomanizer.isFullyRomanized(result)) continue
                if (result != currentTrack.romanizedSyncedLyrics) {
                    musicViewModel.persistRomanizedLyrics(
                        track = currentTrack,
                        romanizedSyncedLyrics = result,
                        romanizedPlainLyrics = null
                    )
                }
                break
            }
        }

        val syncedLyricsForMini = if (isRomanizedLyricsEnabled) {
            romanizedSyncedLyrics ?: currentTrack.syncedLyrics
        } else {
            currentTrack.syncedLyrics
        }

        // Parse synced lyrics once per source/offset change for smooth real-time updates.
        val parsedLyrics = remember(
            currentTrack.uuid,
            syncedLyricsForMini,
            currentTrack.lyricsOffsetMs
        ) {
            syncedLyricsForMini?.let {
                parseSyncedLyrics(it, currentTrack.lyricsOffsetMs)
            } ?: emptyList()
        }

        // Compute the adaptive gap threshold once per lyric set so it adapts to
        // each song's own pacing without any manual tuning.
        val gapThreshold = remember(parsedLyrics) {
            computeAdaptiveLyricsGapThreshold(parsedLyrics)
        }

        val activeLyric = remember(
            currentPosition,
            parsedLyrics,
            isPlaying,
            isMiniPlayerLyricsEnabled
        ) {
            if (!isMiniPlayerLyricsEnabled || !isPlaying || parsedLyrics.isEmpty()) {
                null
            } else {
                val activeIndex = parsedLyrics.indexOfLast { it.timeMs <= currentPosition }
                if (activeIndex == -1) {
                    null
                } else {
                    val currentLine = parsedLyrics[activeIndex]
                    val nextLine = parsedLyrics.getOrNull(activeIndex + 1)

                    // --- Condition 1: music-symbol-only lines are instrumentation markers ---
                    // Lines like "♪", "(♫)", etc. indicate a musical interlude; treat as blank.
                    if (isMusicOnlyLine(currentLine.text)) return@remember null

                    val shouldShowLine = if (nextLine != null) {
                        val msUntilNext = nextLine.timeMs - currentPosition
                        // --- Condition 2: adaptive gap detection ---
                        // `gapThreshold` is derived from the song's own median inter-line gap
                        // (× 2.2), so it naturally tolerates long sung phrases while still
                        // falling back during genuine instrumental silences.
                        msUntilNext > 0 && (currentPosition - currentLine.timeMs) < gapThreshold
                    } else {
                        // Avoid pinning the final lyric line during long instrumental outros.
                        (currentPosition - currentLine.timeMs) <= 4500L
                    }

                    if (shouldShowLine) currentLine.text.takeIf { it.isNotBlank() } else null
                }
            }
        }

        // The progress poll runs every 300 ms; when the next line starts sooner than that, wake at
        // its exact timestamp so the lyric changes on the beat rather than up to 0.3 s late.
        val playbackSpeed by musicViewModel.playbackSpeed.collectAsStateWithLifecycle()
        val nextLineAt = remember(currentPosition, parsedLyrics) {
            parsedLyrics.firstOrNull { it.timeMs > currentPosition }?.timeMs
        }
        LaunchedEffect(nextLineAt, isPlaying, isMiniPlayerLyricsEnabled) {
            if (nextLineAt == null || !isPlaying || !isMiniPlayerLyricsEnabled) return@LaunchedEffect
            val waitMs = ((nextLineAt - currentPosition) / playbackSpeed.coerceAtLeast(0.25f)).toLong()
            if (waitMs in 1..300) {
                delay(waitMs)
                musicViewModel.updateProgress()
            }
        }

        Box(
            modifier = modifier
                .fillMaxWidth()
                .glassFloat(GlassShapes.Bar, GlassLevel.Regular)
                .combinedClickable(
                    onClickLabel = "Open player",
                    role = Role.Button,
                    onDoubleClick = if (gesturePrefs.getBoolean("mini_double_tap_play_pause", false)) {
                        { haptic.heavyClick(); musicViewModel.togglePlayPause() }
                    } else null,
                    onLongClick = if (gesturePrefs.getBoolean("mini_long_press_favorite", false)) {
                        { haptic.confirm(); musicViewModel.toggleFavorite(currentTrack) }
                    } else null,
                    onClick = {
                        haptic.click()
                        onExpand()
                    }
                )
                .onSizeChanged { cardWidthPx = it.width.toFloat() }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            swipeScope.launch {
                                val w = cardWidthPx
                                val x = offsetX.value
                                val commit = abs(x) > maxOf(100f, w * 0.25f)
                                if (!commit) {
                                    offsetX.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 500f))
                                    return@launch
                                }
                                val toNext = x < 0f
                                val neighbor = if (toNext) nextTrack else prevTrack
                                haptic.click()
                                if (neighbor == null) {
                                    // Nothing to glide to (queue edge): act, then settle back.
                                    if (toNext) musicViewModel.skipToNext() else musicViewModel.skipToPrevious()
                                    offsetX.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 400f))
                                    return@launch
                                }
                                // Glide the current card out; the neighbour card glides in behind it.
                                val before = currentUuid
                                offsetX.animateTo(if (toNext) -w else w, tween(190, easing = FastOutSlowInEasing))
                                if (toNext) musicViewModel.skipToNext() else musicViewModel.skipToPrevious()
                                // Hold the neighbour in place until the real card shows the new track.
                                withTimeoutOrNull(900) { snapshotFlow { currentUuid }.first { it != before } }
                                offsetX.snapTo(0f)
                            }
                        },
                        onDragCancel = { swipeScope.launch { offsetX.animateTo(0f) } },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            // Rubber-band when there is no track on that side.
                            val toNext = offsetX.value + dragAmount < 0f
                            val hasNeighbor = if (toNext) nextTrack != null else prevTrack != null
                            val amount = if (hasNeighbor) dragAmount else dragAmount * 0.35f
                            swipeScope.launch { offsetX.snapTo(offsetX.value + amount) }
                        }
                    )
                }
        ) {
            Box(modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = offsetX.value }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentTrack.thumbnailUri != null) {
                        // Explicit cache keys ensure Coil hits memory/disk cache immediately
                        // when the MiniPlayer re-renders after a track change, preventing
                        // the blank thumbnail flash on already-loaded images.
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(currentTrack.thumbnailUri)
                                .memoryCacheKey(currentTrack.thumbnailUri)
                                .diskCacheKey(currentTrack.thumbnailUri)
                                .crossfade(true)
                                .build(),
                            contentDescription = currentTrack.title,
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(18.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(18.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(com.example.juke.R.drawable.baseline_play_24),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    AnimatedContent(
                        targetState = activeLyric,
                        modifier = Modifier.weight(1f).clipToBounds(),
                        transitionSpec = {
                            if (initialState != null && targetState != null) {
                                // Line to line: the lyric moves forward, out the top, in from below.
                                (slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it / 2 } +
                                    fadeIn(tween(260))) togetherWith
                                    (slideOutVertically(tween(220, easing = FastOutSlowInEasing)) { -it / 2 } +
                                        fadeOut(tween(180)))
                            } else {
                                // Lyric to title (gap, pause) and back: a plain crossfade.
                                fadeIn(animationSpec = tween(300)) togetherWith
                                        fadeOut(animationSpec = tween(300))
                            }
                        },
                        label = "MiniPlayer_Lyrics_Transition"
                    ) { currentLyric ->
                        if (currentLyric != null) {
                            Text(
                                text = currentLyric,
                                style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = if (compact) 1 else 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Column {
                                Text(
                                    text = currentTrack.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = currentTrack.artist,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Favourite button — tinted primary when hearted. Hidden in the merged row, where
                    // the title or lyric needs the width (long-press can still favourite).
                    if (!compact) IconButton(
                        onClick = {
                            haptic.confirm()
                            musicViewModel.toggleFavorite(currentTrack)
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (currentTrack.isFavourite) Icons.Filled.Favorite
                            else Icons.Outlined.FavoriteBorder,
                            contentDescription = if (currentTrack.isFavourite) "Remove from favourites"
                            else "Add to favourites",
                            tint = if (currentTrack.isFavourite) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.86f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Download button — only shown for streamed tracks
                    if (currentTrack.isStream && !compact) {
                        IconButton(
                            onClick = {
                                haptic.click()
                                musicViewModel.promoteTrackToDownload(currentTrack)
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Download track",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.86f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Play / Pause
                    val miniPlayScale = remember { Animatable(1f) }
                    LaunchedEffect(uiState.isPlaying) {
                        miniPlayScale.animateTo(0.85f, tween(90, easing = FastOutSlowInEasing))
                        miniPlayScale.animateTo(1f, tween(150, easing = FastOutSlowInEasing))
                    }
                    IconButton(
                        onClick = {
                            haptic.heavyClick()
                            musicViewModel.togglePlayPause()
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .scale(miniPlayScale.value)
                    ) {
                        Crossfade(
                            targetState = uiState.isPlaying,
                            animationSpec = tween(
                                durationMillis = 180,
                                easing = FastOutSlowInEasing
                            ),
                            label = "miniPlayPauseIcon"
                        ) { isPlaying ->
                            Icon(
                                painter = painterResource(
                                    if (isPlaying) com.example.juke.R.drawable.baseline_pause_24
                                    else com.example.juke.R.drawable.baseline_play_24
                                ),
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Progress line at the bottom
                val progress = if (uiState.duration > 0) {
                    (uiState.position.toFloat() / uiState.duration.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 22.dp, vertical = 6.dp)
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(GlassShapes.Pill)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(3.dp)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
                    )
                }
            }

            // The track a swipe is gliding to, sliding in from the side the card is leaving.
            // Only the drag direction is read here, so the frame-by-frame offset never recomposes this.
            val direction by remember { derivedStateOf { sign(offsetX.value) } }
            val glideTo = if (direction < 0f) nextTrack else if (direction > 0f) prevTrack else null
            if (glideTo != null && cardWidthPx > 0f) {
                MiniPlayerGlideCard(
                    track = glideTo,
                    isPlaying = isPlaying,
                    modifier = Modifier.graphicsLayer {
                        translationX = offsetX.value + if (offsetX.value < 0f) cardWidthPx else -cardWidthPx
                    }
                )
            }
        }
    }
}

/** Static preview of a neighbouring track, laid out exactly like the live mini player row. */
@Composable
private fun MiniPlayerGlideCard(track: Track, isPlaying: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(46.dp).clip(RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (track.thumbnailUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(track.thumbnailUri)
                        .memoryCacheKey(track.thumbnailUri)
                        .diskCacheKey(track.thumbnailUri)
                        .build(),
                    contentDescription = track.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = if (track.isFavourite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = null,
            tint = if (track.isFavourite) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.86f),
            modifier = Modifier.padding(10.dp).size(20.dp)
        )
        Icon(
            painter = painterResource(
                if (isPlaying) com.example.juke.R.drawable.baseline_pause_24
                else com.example.juke.R.drawable.baseline_play_24
            ),
            contentDescription = null,
            modifier = Modifier.padding(8.dp).size(24.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}
