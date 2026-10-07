package com.example.juke.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
    var playerBounds by mutableStateOf<Rect?>(null)
    var rootBounds by mutableStateOf(Rect.Zero)
    val animation = Animatable(0f)
    var dragProgress by mutableStateOf<Float?>(null)
    val progress get() = dragProgress ?: animation.value
    val dragRange get() = (miniBounds?.top?.minus(rootBounds.top) ?: rootBounds.height).coerceAtLeast(1f)
    var expanded by mutableStateOf(false)
    var hasArtwork by mutableStateOf(false)
    val moving get() = progress > 0f && progress < 1f
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
        state.animation.animateTo(if (expanded) 1f else 0f, playerExpansionSpec())
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
    if (state.expanded || state.progress > 0f) {
        Box(Modifier.fillMaxSize().graphicsLayer {
            // Reveal the surface upward from the mini-player while the artwork travels independently.
            val top = state.miniBounds?.top?.minus(state.rootBounds.top) ?: size.height
            shape = object : androidx.compose.ui.graphics.Shape {
                override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density) =
                    androidx.compose.ui.graphics.Outline.Rectangle(Rect(0f, top * (1f - state.progress), size.width, size.height))
            }
            clip = true
        }.background(Color.Black)
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
                    // Transfer the exact finger position to the animation before releasing the drag.
                    if (state.expanded) {
                        state.animation.snapTo(state.progress)
                        state.dragProgress = null
                        if (dismiss) onDismiss()
                        else state.animation.animateTo(1f, playerExpansionSpec())
                    }
                }
            )
            .pointerInput(Unit) { detectTapGestures { /* Consume taps on the player backdrop. */ } }) {
            Box(Modifier.fillMaxSize().graphicsLayer {
                alpha = ((state.progress - 0.15f) / 0.85f).coerceIn(0f, 1f)
            }) { content() }
        }
    }
}

private fun playerExpansionSpec() = tween<Float>(280, easing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f))
