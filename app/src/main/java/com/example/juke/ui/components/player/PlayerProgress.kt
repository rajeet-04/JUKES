package com.example.juke.ui.components.player

import android.graphics.RenderNode
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.juke.utils.rememberJukeHaptics
import com.example.juke.viewmodels.MusicUiState
import com.example.juke.viewmodels.MusicViewModel
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun PlayerProgress(
    /** The thumb and fill read position when updating their layer transforms. */
    position: () -> Long,
    uiState: MusicUiState,
    musicViewModel: MusicViewModel,
    modifier: Modifier = Modifier,
    /** End label counts down what is left (-1:55) instead of the length. */
    showRemaining: Boolean = false,
    thumbSize: Dp = 22.dp
) {
    val duration = if (uiState.duration > 0) uiState.duration else musicViewModel.playbackManager.getDuration()

    val bounded = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    val showCountdown = showRemaining && duration > 0
    val container = modifier.fillMaxWidth().graphicsLayer()
    Column(modifier = if (bounded) container.boundSeekDamage() else container) {
        if (bounded) {
            LayeredSeekSlider(position, duration, musicViewModel::seekTo, thumbSize)
        } else {
            PlaybackSeekSlider(position(), duration, musicViewModel::seekTo, thumbSize = thumbSize)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            PlayerTimeLabel(seconds = { position().coerceAtLeast(0) / 1000 }, layered = bounded)
            if (showCountdown) {
                PlayerTimeLabel(
                    seconds = { (duration - position()).coerceAtLeast(0) / 1000 },
                    prefix = "-",
                    layered = bounded
                )
            } else {
                TimeText(formatTime(duration / 1000))
            }
        }
    }
}

@Composable
private fun TimeText(value: String) {
    Text(value, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** A new digit slot is laid out only when the minutes gain or lose a decimal place. */
@Composable
private fun PlayerTimeLabel(seconds: () -> Long, layered: Boolean, prefix: String = "") {
    val currentSeconds by rememberUpdatedState(seconds)
    val locale = LocalConfiguration.current.locales[0]
    if (!layered) {
        TimeText(prefix + formatTime(currentSeconds(), locale))
        return
    }

    val minuteDigits by remember { derivedStateOf { (currentSeconds() / 60).toString().length } }
    val minuteDivisors = remember(minuteDigits) {
        generateSequence(60L) { it * 10 }.take(minuteDigits).toList().asReversed()
    }
    val glyphs = remember(locale) { (0..9).map { String.format(locale, "%d", it) } }
    // Numeric times retain minute:second order inside an RTL player.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(Modifier.clearAndSetSemantics {
            text = AnnotatedString(prefix + formatTime(currentSeconds(), locale))
        }) {
            if (prefix.isNotEmpty()) TimeText(prefix)
            for (divisor in minuteDivisors) {
                ClockDigit(glyphs) { (currentSeconds() / divisor % 10).toInt() }
            }
            TimeText(":")
            ClockDigit(glyphs) { (currentSeconds() / 10 % 6).toInt() }
            ClockDigit(glyphs) { (currentSeconds() % 10).toInt() }
        }
    }
}

@Composable
private fun ClockDigit(glyphs: List<String>, digit: () -> Int) {
    // Expose one line of a recorded column. Moving it behind the clip preserves the glyph shape.
    Layout(
        modifier = Modifier.clipToBounds(),
        content = {
            Column(
                modifier = Modifier.graphicsLayer { translationY = -digit() * (size.height / glyphs.size) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                for (glyph in glyphs) TimeText(glyph)
            }
        }
    ) { measurables, constraints ->
        val column = measurables.single().measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
        val lineHeight = column.height / glyphs.size
        layout(column.width, lineHeight.coerceIn(constraints.minHeight, constraints.maxHeight)) {
            column.place(0, 0)
        }
    }
}

/** Fixed-size layers keep ordinary playback ticks out of measurement and draw recording. */
@Composable
private fun LayeredSeekSlider(
    position: () -> Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    thumbSize: Dp
) {
    val enabled = durationMs > 0
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val direction = if (rtl) -1f else 1f
    val haptic = rememberJukeHaptics()
    val fraction = { if (enabled) (position().toFloat() / durationMs).coerceIn(0f, 1f) else 0f }
    var lastBucket by remember(durationMs) { mutableIntStateOf(-1) }
    val seek by rememberUpdatedState<(Float) -> Unit> { value ->
        val target = value.coerceIn(0f, 1f)
        val bucket = (target * 24).toInt()
        val previousBucket = if (lastBucket < 0) (fraction() * 24).toInt() else lastBucket
        if (bucket != previousBucket) haptic.tick()
        lastBucket = bucket
        onSeek((target * durationMs).toLong().coerceIn(0, durationMs))
    }
    val finish = {
        lastBucket = -1
        haptic.gestureEnd()
    }
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(48.dp).clipToBounds()
            .semantics {
                contentDescription = "Playback position"
                progressBarRangeInfo = ProgressBarRangeInfo(fraction(), 0f..1f)
                if (!enabled) disabled()
                else setProgress { value ->
                    val target = value.coerceIn(0f, 1f)
                    if (target == fraction()) false else {
                        seek(target)
                        finish()
                        true
                    }
                }
            }
            .onKeyEvent { event ->
                if (!enabled || event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val target = when (event.key) {
                    Key.DirectionLeft -> fraction() - direction * 0.01f
                    Key.DirectionRight -> fraction() + direction * 0.01f
                    Key.MoveHome -> 0f
                    Key.MoveEnd -> 1f
                    else -> return@onKeyEvent false
                }
                seek(target)
                finish()
                true
            }
            .focusable(enabled)
    ) {
        val thumbPx = with(LocalDensity.current) { thumbSize.roundToPx() }
        val travel = (constraints.maxWidth - thumbPx).coerceAtLeast(0).toFloat()
        val seekAt = { x: Float ->
            if (travel > 0f) {
                val physical = (x - thumbPx / 2f) / travel
                seek(if (rtl) 1f - physical else physical)
            }
        }
        Box(
            Modifier.matchParentSize()
                .then(if (!enabled) Modifier else Modifier
                    .pointerInput(travel, rtl, durationMs) {
                        detectTapGestures {
                            seekAt(it.x)
                            finish()
                        }
                    }
                    .pointerInput(travel, rtl, durationMs) {
                        detectHorizontalDragGestures(
                            onDragEnd = finish,
                            onDragCancel = { lastBucket = -1 }
                        ) { change, _ ->
                            change.consume()
                            seekAt(change.position.x)
                        }
                    }
                )
        ) {
            Box(
                Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = thumbSize / 2)
                    .height(6.dp).clip(CircleShape)
                    .background(colors.onSurface.copy(alpha = if (enabled) 0.22f else 0.12f))
            ) {
                Box(Modifier.matchParentSize().graphicsLayer {
                    translationX = direction * size.width * (fraction() - 1f)
                }.background(colors.primary, CircleShape))
            }
            Box(Modifier.align(Alignment.CenterStart).graphicsLayer {
                translationX = direction * travel * fraction()
            }) {
                SeekThumb(thumbSize, enabled)
            }
        }
    }
}

// On API 29+ HWUI bounds descendant damage at a RenderNode with clipToBounds set.
// Compose 1.12 maps even a rectangular graphicsLayer clip to clipToOutline.
@RequiresApi(Build.VERSION_CODES.Q)
private fun Modifier.boundSeekDamage(): Modifier {
    return drawWithCache {
        val strip = RenderNode("JUKES seek strip").apply {
            setPosition(0, 0, size.width.roundToInt(), size.height.roundToInt())
            clipToBounds = true
        }
        onDrawWithContent {
            val parent = drawContext.canvas
            if (parent.nativeCanvas.isHardwareAccelerated) {
                val recording = strip.beginRecording()
                try {
                    drawContext.canvas = Canvas(recording)
                    drawContent()
                } finally {
                    drawContext.canvas = parent
                    strip.endRecording()
                }
                parent.nativeCanvas.drawRenderNode(strip)
            } else {
                drawContent()
            }
        }
    }
}

@Composable
private fun SeekThumb(size: Dp, enabled: Boolean) {
    val surface = MaterialTheme.colorScheme.onSurface
    Box(
        Modifier.size(size)
            .shadow(6.dp, CircleShape, clip = false, ambientColor = Color.Black.copy(alpha = 0.3f), spotColor = Color.Black.copy(alpha = 0.45f))
            .background(if (enabled) surface else surface.copy(alpha = 0.38f), CircleShape)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaybackSeekSlider(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    /** Called when the drag ends. A remote device seeks here only; [onSeek] then just moves the thumb. */
    onSeekEnd: (() -> Unit)? = null,
    thumbSize: Dp = 22.dp
) {
    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val haptic = rememberJukeHaptics()
    var lastBucket by remember(durationMs) { mutableIntStateOf((progress * 24).toInt()) }
    Slider(
        value = progress,
        enabled = durationMs > 0,
        onValueChange = { value ->
            val bucket = (value * 24).toInt()
            if (bucket != lastBucket) {
                haptic.tick()
                lastBucket = bucket
            }
            onSeek((value * durationMs).toLong())
        },
        onValueChangeFinished = { haptic.gestureEnd(); onSeekEnd?.invoke() },
        thumb = { SeekThumb(thumbSize, durationMs > 0) },
        track = { state ->
            SliderDefaults.Track(
                sliderState = state,
                modifier = Modifier.height(6.dp),
                thumbTrackGapSize = 0.dp,
                drawStopIndicator = null,
                enabled = durationMs > 0,
                colors = SliderDefaults.colors(
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
                )
            )
        },
        modifier = modifier.fillMaxWidth().semantics { contentDescription = "Playback position" }
    )
}

private fun formatTime(totalSeconds: Long, locale: Locale = Locale.getDefault()): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(locale, "%d:%02d", minutes, seconds)
}
