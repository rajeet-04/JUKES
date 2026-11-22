package com.example.juke.ui.components.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.juke.models.Track
import com.example.juke.ui.components.playerArtworkEndpoint
import com.example.juke.ui.icons.JukeIcons
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import com.example.juke.ui.theme.glassIsLive
import com.example.juke.ui.theme.liquidGlass
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import com.example.juke.utils.rememberJukeHaptics
import com.example.juke.viewmodels.MusicViewModel
import kotlin.math.absoluteValue

@Composable
fun PlayerArtwork(
    queue: List<Track>,
    queueIndex: Int,
    currentTrack: Track,
    currentPosition: () -> Long,
    showLyrics: Boolean,
    musicViewModel: MusicViewModel,
    isTablet: Boolean,
    onToggleLyrics: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val haptic = rememberJukeHaptics()

    val pagerState = rememberPagerState(
        initialPage = queueIndex.coerceAtLeast(0),
        pageCount = { queue.size.coerceAtLeast(1) }
    )

    // Sync external playback changes (next button, track end) to the Pager
    LaunchedEffect(queueIndex) {
        if (queueIndex in queue.indices && pagerState.currentPage != queueIndex) {
            pagerState.animateScrollToPage(queueIndex)
        }
    }

    // Sync manual user swipes to the ViewModel
    LaunchedEffect(pagerState.settledPage) {
        if (pagerState.settledPage != queueIndex && pagerState.settledPage in queue.indices) {
            val swipedTrack = queue[pagerState.settledPage]
            musicViewModel.playTrackFromQueue(swipedTrack)
            haptic.click()
        }
    }

    // The caller sizes the slot: a square for the artwork, the whole area while lyrics are open.
    val artworkModifier = modifier.fillMaxSize()

    if (queue.isEmpty()) {
        // Fallback if queue is empty for some reason
        ArtworkCard(
            track = currentTrack,
            isCurrentTrack = true,
            showLyrics = showLyrics,
            currentPosition = currentPosition,
            musicViewModel = musicViewModel,
            isTablet = isTablet,
            onToggleLyrics = onToggleLyrics,
            modifier = artworkModifier
        )
    } else {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 0.dp),
            modifier = artworkModifier
        ) { page ->
            val pageTrack = if (page == queueIndex) {
                currentTrack
            } else {
                queue.getOrNull(page) ?: currentTrack
            }

            ArtworkCard(
                track = pageTrack,
                isCurrentTrack = pageTrack.uuid == currentTrack.uuid,
                // Only show lyrics on the active page
                showLyrics = showLyrics && page == pagerState.currentPage,
                currentPosition = currentPosition,
                musicViewModel = musicViewModel,
                isTablet = isTablet,
                onToggleLyrics = onToggleLyrics,
                modifier = Modifier.graphicsLayer {
                    // Read continuous pager motion in the draw phase, avoiding recomposition every frame.
                    val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                    val distance = pageOffset.absoluteValue.coerceIn(0f, 1f)
                    scaleX = 1f - 0.15f * distance
                    scaleY = scaleX
                    alpha = 1f - 0.5f * distance
                }
            )
        }
    }
}

@Composable
private fun ArtworkCard(
    track: Track,
    isCurrentTrack: Boolean,
    showLyrics: Boolean,
    currentPosition: () -> Long,
    musicViewModel: MusicViewModel,
    isTablet: Boolean,
    onToggleLyrics: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val haptic = rememberJukeHaptics()
    val context = LocalContext.current

    val shape = RoundedCornerShape(32.dp)
    val live = glassIsLive() && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
    // The artwork is the glass's source: a clear lens over it bends the art's own edges (rim,
    // rainbow, highlight), so the tile reads as glass. A pane under the opaque art never showed.
    val artHaze = remember { HazeState() }
    Box(
        modifier = modifier
            .fillMaxSize()
            // Room for the shadow inside the pager page, which clips its content.
            .padding(ART_INSET)
            .playerArtworkEndpoint(expanded = true, enabled = isCurrentTrack, fly = !showLyrics)
            .shadow(6.dp, shape)
            .then(if (onToggleLyrics == null) Modifier else Modifier.clickable {
                haptic.click()
                onToggleLyrics()
            }),
        contentAlignment = Alignment.Center
    ) {
        if (track.thumbnailUri != null) {
            // Use an explicit ImageRequest so Coil can key the memory/disk cache by URI
            // and immediately serve from cache when the composable is re-entered after
            // a track switch (avoids the blank-frame flash on already-rendered components).
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(track.thumbnailUri)
                    .memoryCacheKey(track.thumbnailUri)
                    .diskCacheKey(track.thumbnailUri)
                    .crossfade(true)
                    .build(),
                contentDescription = track.title,
                modifier = Modifier.fillMaxSize().then(if (live) Modifier.hazeSource(artHaze) else Modifier),
                contentScale = ContentScale.Crop
            )
            if (live) {
                Box(
                    Modifier.matchParentSize().liquidGlass(
                        shape, Color.Transparent, blur = 0.dp, rimAlpha = 0.7f, source = artHaze,
                        // No fold: it would mirror the art's edge back in.
                        displacement = 14.dp, edge = 12.dp, fold = 0f, detail = 0f
                    )
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = JukeIcons.Play,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Fade the lyrics over the artwork rather than popping them in; leaving is quicker.
        AnimatedVisibility(
            visible = showLyrics,
            enter = fadeIn(tween(260, easing = CubicBezierEasing(0f, 0f, 0f, 1f))),
            exit = fadeOut(tween(240))
        ) {
            LyricsOverlay(
                currentTrack = track,
                currentPosition = currentPosition,
                musicViewModel = musicViewModel,
                isTablet = isTablet,
                onDismiss = { onToggleLyrics?.invoke() }
            )
        }
    }
}

/** Space around the artwork tile so its shadow isn't cut off by the pager. */
internal val ART_INSET = 12.dp
