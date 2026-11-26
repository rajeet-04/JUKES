package com.example.juke.ui.components.player

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.example.juke.ui.screens.LyricLine

/**
 * The playback position between the player's coarse updates (every 250–300 ms), advanced on each
 * frame while playing so a word fills smoothly. Never runs more than a second past the last update.
 */
@Composable
fun rememberFrameClock(position: Long, playing: Boolean): () -> Long {
    val clock = remember { mutableLongStateOf(position) }
    LaunchedEffect(position, playing) {
        clock.longValue = position
        if (!playing) return@LaunchedEffect
        val start = withFrameMillis { it }
        while (true) withFrameMillis { now -> clock.longValue = position + (now - start).coerceAtMost(1_000) }
    }
    return { clock.longValue }
}

/**
 * The active line with word timings, sung word by word: words already sung are at full strength,
 * the word being sung fills in with a soft glow, and words still to come stay as dim as the
 * surrounding lines ([dimAlpha]).
 */
fun karaokeText(line: LyricLine, now: Long, color: Color, dimAlpha: Float): AnnotatedString = buildAnnotatedString {
    val words = line.words
    words.forEachIndexed { i, word ->
        val next = words.getOrNull(i + 1)?.timeMs
        // A long held note fills over its length; a quick run of words still visibly fills.
        val span = ((next ?: (word.timeMs + 600)) - word.timeMs).coerceIn(140, 1_200)
        val progress = ((now - word.timeMs).toFloat() / span).coerceIn(0f, 1f)
        val singing = now >= word.timeMs && (next == null || now < next) && progress < 1f
        val alpha = if (now < word.timeMs) dimAlpha else dimAlpha + (1f - dimAlpha) * progress
        withStyle(
            SpanStyle(
                color = color.copy(alpha = color.alpha * alpha),
                shadow = if (singing) Shadow(color.copy(alpha = 0.45f * (1f - progress * 0.5f)), blurRadius = 18f) else null,
            )
        ) { append(if (i == words.lastIndex) word.text.trimEnd() else word.text) }
    }
}

/**
 * [rememberFrameClock] for a position read through [position] (the view model's position flow): the
 * read happens here, off the caller's composition, so a tick never recomposes the screen around it.
 */
@Composable
fun rememberFrameClock(position: () -> Long, playing: Boolean): () -> Long {
    val clock = remember { mutableLongStateOf(androidx.compose.runtime.snapshots.Snapshot.withoutReadObservation(position)) }
    LaunchedEffect(playing) {
        androidx.compose.runtime.snapshotFlow { position() }.collectLatest { p ->
            clock.longValue = p
            if (!playing) return@collectLatest
            val start = withFrameMillis { it }
            while (true) withFrameMillis { now -> clock.longValue = p + (now - start).coerceAtMost(1_000) }
        }
    }
    return { clock.longValue }
}

/** Unsung lyric text: white at low strength, so it reads as frosted glass over the backdrop. */
const val FROST_ALPHA = 0.38f

/**
 * One lyric line, sung as frosted glass filling with white. With word timings ([clock] set), white
 * with a soft glow sweeps across the words as they are sung; without, the whole line is lit.
 *
 * The text is laid out once; the sweep is drawn each frame by clipping a lit copy over the frosted
 * one, so singing never recomposes or re-measures (it used to rebuild the styled string per frame).
 */
@Composable
fun KaraokeLine(
    line: LyricLine,
    clock: (() -> Long)?,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    onLineCount: (Int) -> Unit = {},
) {
    val words = line.words
    val timed = clock != null && words.isNotEmpty()
    val text = remember(line) { if (words.isEmpty()) line.text else words.joinToString("") { it.text }.trimEnd() }
    // Where each word starts in [text], to find its place in the layout.
    val starts = remember(line) { words.runningFold(0) { at, w -> at + w.text.length }.dropLast(1) }
    var layout by remember { androidx.compose.runtime.mutableStateOf<TextLayoutResult?>(null) }
    val glow = with(LocalDensity.current) { 14.dp.toPx() }
    Box(modifier) {
        Text(
            text, style = style, color = Color.White.copy(alpha = if (timed) FROST_ALPHA else 0f),
            maxLines = maxLines, overflow = TextOverflow.Ellipsis,
            onTextLayout = { layout = it; onLineCount(it.lineCount) }
        )
        Text(
            text, style = style.copy(shadow = Shadow(Color.White.copy(alpha = 0.55f), blurRadius = 18f)), color = Color.White,
            maxLines = maxLines, overflow = TextOverflow.Ellipsis,
            modifier = if (!timed) Modifier else Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
                val l = layout ?: return@drawWithContent
                val now = clock!!()
                val i = words.indexOfLast { it.timeMs <= now }
                if (i < 0) return@drawWithContent
                val next = words.getOrNull(i + 1)?.timeMs
                val span = ((next ?: (words[i].timeMs + 600)) - words[i].timeMs).coerceIn(140, 1_200)
                val p = ((now - words[i].timeMs).toFloat() / span).coerceIn(0f, 1f)
                val from = starts[i].coerceAtMost(text.length)
                val to = (starts[i] + words[i].text.trimEnd().length).coerceIn(from, text.length)
                val row = l.getLineForOffset((to - 1).coerceAtLeast(from))
                // ponytail: left-to-right sweep; a right-to-left script would fill from the wrong side.
                val left = if (l.getLineForOffset(from) == row) l.getHorizontalPosition(from, true) else l.getLineLeft(row)
                val right = l.getHorizontalPosition(to, true).let { if (l.getLineForOffset(to) != row || it < left) l.getLineRight(row) else it }
                val edge = left + (right - left) * p
                val feather = glow
                drawContent()
                // Soft right edge on the active row, nothing on the rows below it: a feathered mask
                // instead of a clip, so the glow neither forms a box nor lights the next row.
                drawRect(
                    Brush.horizontalGradient(0f to Color.Black, 1f to Color.Transparent, startX = edge - feather, endX = edge + feather),
                    topLeft = Offset(edge - feather, l.getLineTop(row)),
                    size = Size(size.width + glow - (edge - feather), l.getLineBottom(row) - l.getLineTop(row)),
                    blendMode = BlendMode.DstIn,
                )
                if (row < l.lineCount - 1) drawRect(
                    Color.Transparent, topLeft = Offset(-glow, l.getLineBottom(row)),
                    size = Size(size.width + 2 * glow, size.height + glow), blendMode = BlendMode.DstIn,
                )
            }
        )
    }
}
