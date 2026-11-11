package com.example.juke.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.juke.viewmodels.MusicViewModel

private class PlayerTransitionState {
    var miniBounds by mutableStateOf<Rect?>(null)
    /** The whole mini-player bar: the player sheet grows out of / shrinks back into this rect. */
    var barBounds by mutableStateOf<Rect?>(null)
    var playerBounds by mutableStateOf<Rect?>(null)
    var rootBounds by mutableStateOf(Rect.Zero)
    val animation = Animatable(0f)
    var dragProgress by mutableStateOf<Float?>(null)
    val progress get() = dragProgress ?: animation.value
    val dragRange get() = ((barBounds ?: miniBounds)?.top?.minus(rootBounds.top) ?: rootBounds.height).coerceAtLeast(1f)
    var expanded by mutableStateOf(false)
    /** Progress units/s from the last drag release, so the settle continues the finger's motion. */
    var releaseVelocity = 0f
    var hasArtwork by mutableStateOf(false)
    val moving get() = progress > 0f && progress < 1f
}

/** The container's current rect (bar -> screen) and corner radius at the present progress. */
private fun PlayerTransitionState.morph(size: androidx.compose.ui.geometry.Size, barRadius: Float): Pair<Rect, Float> {
    val t = progress
    val bar = barBounds
    val barLeft = bar?.left?.minus(rootBounds.left) ?: 0f
    val barTop = bar?.top?.minus(rootBounds.top) ?: size.height
    val barRight = bar?.right?.minus(rootBounds.left) ?: size.width
    val barBottom = bar?.bottom?.minus(rootBounds.top) ?: size.height
    return Rect(
        barLeft * (1f - t),
        barTop * (1f - t),
        barRight + (size.width - barRight) * t,
        barBottom + (size.height - barBottom) * t
    ) to barRadius * (1f - t)
}

private val LocalPlayerTransition = staticCompositionLocalOf<PlayerTransitionState?> { null }

/** Measure real endpoints rather than guessing artwork positions from screen dimensions. */
@Composable
fun Modifier.playerArtworkEndpoint(expanded: Boolean, enabled: Boolean = true): Modifier {
    val state = LocalPlayerTransition.current
    return if (state == null || !enabled) this else this
        .onGloballyPositioned {
            if (expanded) state.playerBounds = it.boundsInRoot()
            else state.miniBounds = it.boundsInRoot()
        }
        .graphicsLayer {
            alpha = if (state.moving && state.hasArtwork && state.miniBounds != null && state.playerBounds != null) 0f else 1f
        }
}

/**
 * Marks the mini-player bar. The player sheet morphs from/to this rect, and the bar itself fades out
 * as the sheet takes over (and back in as it lands), so it reads as one surface changing size.
 */
@Composable
fun Modifier.playerBarEndpoint(): Modifier {
    val state = LocalPlayerTransition.current ?: return this
    return this
        .onGloballyPositioned { state.barBounds = it.boundsInRoot() }
        .graphicsLayer { alpha = (1f - state.progress * 4f).coerceIn(0f, 1f) }
}

@Composable
fun PlayerTransitionHost(
    expanded: Boolean,
    musicViewModel: MusicViewModel,
    content: @Composable () -> Unit
) {
    val state = remember { PlayerTransitionState() }
    state.expanded = expanded
    LaunchedEffect(expanded) {
        // Retarget from the current drag/animation position, never restart at an endpoint.
        state.animation.snapTo(state.progress)
        state.dragProgress = null
        // Compose honors the system animator duration scale, including disabled animations.
        val velocity = state.releaseVelocity
        state.releaseVelocity = 0f
        state.animation.animateTo(if (expanded) 1f else 0f, playerExpansionSpec(expanded), initialVelocity = velocity)
    }
    val uiState by musicViewModel.uiState.collectAsStateWithLifecycle()
    state.hasArtwork = uiState.currentTrack?.thumbnailUri != null
    val density = LocalDensity.current
    CompositionLocalProvider(LocalPlayerTransition provides state) {
        Box(Modifier.fillMaxSize().onGloballyPositioned { state.rootBounds = it.boundsInRoot() }) {
            content()
            val start = state.miniBounds
            val end = state.playerBounds
            val track = uiState.currentTrack
            if (state.moving && state.hasArtwork && start != null && end != null && track != null) {
                // Draw at the final size and transform it: no image remeasurement each frame.
                AsyncImage(
                    model = track.thumbnailUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(with(density) { end.width.toDp() }, with(density) { end.height.toDp() })
                        .graphicsLayer {
                            val t = state.progress
                            transformOrigin = TransformOrigin(0f, 0f)
                            translationX = start.left + (end.left - start.left) * t - state.rootBounds.left
                            translationY = start.top + (end.top - start.top) * t - state.rootBounds.top
                            scaleX = (start.width + (end.width - start.width) * t) / end.width
                            scaleY = (start.height + (end.height - start.height) * t) / end.height
                            // Correct the radius for the scaled artwork at the mini-player endpoint.
                            val radius = (18f / scaleX) * (1f - t) + 32f * t
                            shape = RoundedCornerShape(radius.dp)
                            clip = true
                        }
                )
            }
        }
    }
}

@Composable
fun PlayerTransitionContent(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val state = LocalPlayerTransition.current ?: return
    val density = LocalDensity.current
    val dismissDistance = with(density) { 72.dp.toPx() }
    val dismissVelocity = with(density) { 1200.dp.toPx() }
    val dragState = rememberDraggableState { delta ->
        state.dragProgress = (state.progress - delta / state.dragRange).coerceIn(0f, 1f)
    }
    val visible by remember { derivedStateOf { state.expanded || state.progress > 0f } }
    val barRadius = with(density) { 28.dp.toPx() }
    if (visible) {
        Box(Modifier.fillMaxSize().graphicsLayer {
            // One container that morphs between the mini-player bar and the full screen: all four
            // edges and the corner radius interpolate, so the sheet visibly shrinks into the bar.
            val (rect, radius) = state.morph(size, barRadius)
            shape = object : androidx.compose.ui.graphics.Shape {
                override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density) =
                    androidx.compose.ui.graphics.Outline.Rounded(
                        androidx.compose.ui.geometry.RoundRect(rect, androidx.compose.ui.geometry.CornerRadius(radius))
                    )
            }
            clip = true
        }.drawBehind {
            // Transparent at the bar so the glass mini player shows through, solid before it is full size.
            drawRect(Color.Black, alpha = (state.progress * 2.2f).coerceIn(0f, 1f))
            // A faint glass rim while it is still bar-sized, so it reads as the glass bar growing.
            val rimAlpha = (1f - state.progress * 3f).coerceIn(0f, 1f) * 0.22f
            if (rimAlpha > 0f) {
                val (rect, radius) = state.morph(size, barRadius)
                drawRoundRect(
                    color = Color.White.copy(alpha = rimAlpha),
                    topLeft = rect.topLeft,
                    size = rect.size,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                )
            }
        }
            .draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                enabled = state.expanded,
                onDragStarted = {
                    state.animation.stop()
                    state.dragProgress = state.animation.value
                },
                onDragStopped = { velocity ->
                    val dismiss = (1f - state.progress) * state.dragRange >= dismissDistance ||
                        (velocity >= dismissVelocity && state.progress < 1f)
                    // Transfer the exact finger position and speed to the animation on release.
                    if (state.expanded) {
                        val progressVelocity = -velocity / state.dragRange
                        state.animation.snapTo(state.progress)
                        state.dragProgress = null
                        if (dismiss) {
                            state.releaseVelocity = progressVelocity
                            onDismiss()
                        } else state.animation.animateTo(1f, playerExpansionSpec(true), initialVelocity = progressVelocity)
                    }
                }
            )
            .pointerInput(Unit) { detectTapGestures { /* Consume taps on the player backdrop. */ } }) {
            // Each part of the sheet fades in on its own window (see playerStage), so nothing shows
            // squashed inside the shrinking container.
            Box(Modifier.fillMaxSize()) { content() }
        }
    }
}

// A critically damped spring: no bounce, but it takes over the finger's velocity on release, and it
// retargets mid-flight without a seam. Closing is stiffer than opening (exit faster than entrance).
private fun playerExpansionSpec(expanding: Boolean) =
    spring<Float>(dampingRatio = 1f, stiffness = if (expanding) 380f else 560f, visibilityThreshold = 0.0005f)

/**
 * Staggers one part of the player sheet: it fades in and rises while the expansion progress passes
 * [from]..[to], and (being driven by the same progress) leaves in reverse order when collapsing.
 */
@Composable
fun Modifier.playerStage(from: Float, to: Float, rise: androidx.compose.ui.unit.Dp = 24.dp): Modifier {
    val state = LocalPlayerTransition.current ?: return this
    val risePx = with(LocalDensity.current) { rise.toPx() }
    return graphicsLayer {
        val a = ((state.progress - from) / (to - from)).coerceIn(0f, 1f)
        alpha = a
        translationY = (1f - a) * risePx
    }
}
