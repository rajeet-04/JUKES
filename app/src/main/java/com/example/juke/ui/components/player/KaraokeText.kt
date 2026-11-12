package com.example.juke.ui.components.player

import androidx.compose.runtime.Composable
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
