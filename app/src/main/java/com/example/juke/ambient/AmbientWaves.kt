package com.example.juke.ambient

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlin.math.PI
import kotlin.math.sin

/** The "Background waves" setting (Audio settings → look); on by default. */
object AmbientPrefs {
    var enabled by mutableStateOf(true)
}

/**
 * Soft coloured waves low on the app background that move with the music. They take the playing
 * song's artwork colours, swell with the bass and ripple with the treble. On pause they settle and
 * fade out to the plain backdrop, and the frame loop stops, so a paused app draws nothing.
 */
@Composable
fun AmbientWaves(playing: Boolean, colors: List<Color>, dark: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val resumed = lifecycle.currentStateAsState().value.isAtLeast(Lifecycle.State.RESUMED)
    // Battery saver turns the waves off, and back on the moment it ends (e.g. on plugging in),
    // without waiting for the app to be reopened.
    var powerSave by remember { mutableStateOf(isPowerSave(context)) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { powerSave = isPowerSave(context) }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { context.unregisterReceiver(receiver) }
    }
    // A change while the app was in the background (no receiver then) is caught on resume.
    LaunchedEffect(resumed) { if (resumed) powerSave = isPowerSave(context) }
    // "Remove animations" has no broadcast: read it on each resume.
    val noAnim = remember(resumed) {
        android.provider.Settings.Global.getFloat(
            context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) == 0f
    }
    val quiet = powerSave || noAnim
    val on = AmbientPrefs.enabled && !quiet && colors.isNotEmpty()

    val c0 by animateColorAsState(colors.getOrElse(0) { Color.Transparent }, tween(1200), label = "wave0")
    val c1 by animateColorAsState(colors.getOrElse(1) { colors.getOrElse(0) { Color.Transparent } }, tween(1200), label = "wave1")
    val c2 by animateColorAsState(colors.getOrElse(2) { colors.getOrElse(0) { Color.Transparent } }, tween(1200), label = "wave2")

    val wave = remember { WaveState() }
    var tick by remember { mutableLongStateOf(0L) }

    LaunchedEffect(playing, on, resumed) {
        if (!on || !resumed) {
            if (!on) wave.visible.snap(0f)
            tick++
            return@LaunchedEffect
        }
        if (!playing && wave.visible.value < FADED) return@LaunchedEffect
        AudioLevels.listening = true
        try {
            var last = withFrameMillis { it }
            while (true) {
                val now = withFrameMillis { it }
                val dt = (now - last).toFloat()
                if (dt < FRAME_MS) continue // 30 fps is plenty for soft colour, and halves the glass re-blur
                last = now
                wave.advance(playing, dt)
                tick = now
                if (!playing && wave.visible.value < FADED) break
            }
        } finally {
            AudioLevels.listening = false
        }
    }

    val blur = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) BlurEffect(56f / SCALE, 56f / SCALE, TileMode.Decal) else null
    }
    // Drawn and blurred at 1/SCALE size, then stretched to fill: soft colour looks the same and the
    // full-screen blur stops dominating the frame (issue #41).
    Canvas(
        modifier.fillMaxSize()
            .layout { m, c ->
                val p = m.measure(Constraints.fixed((c.maxWidth + SCALE - 1) / SCALE, (c.maxHeight + SCALE - 1) / SCALE))
                layout(c.maxWidth, c.maxHeight) { p.place(0, 0) }
            }
            .graphicsLayer {
                scaleX = SCALE.toFloat()
                scaleY = SCALE.toFloat()
                transformOrigin = TransformOrigin(0f, 0f)
                renderEffect = blur
            }
    ) {
        tick // redraw on every frame of the loop
        val visible = wave.visible.value
        if (visible < FADED) return@Canvas
        val w = size.width
        val h = size.height
        val bass = wave.bass.value
        val treble = wave.treble.value
        val energy = wave.energy.value
        val kick = wave.kick
        val phase = wave.phase
        val steps = 48
        listOf(c0, c1, c2).forEachIndexed { i, color ->
            val base = h * (0.62f + 0.08f * i)
            // Bass sets the swell; each kick drum punches the crest up on the beat.
            val amp = h * (0.022f + 0.06f * bass * (1f - 0.25f * i) + 0.06f * kick * (1f - 0.3f * i))
            val path = Path().apply {
                moveTo(0f, h)
                for (s in 0..steps) {
                    val x = w * s / steps
                    val u = (x / w) * 2f * PI.toFloat()
                    val y = base -
                        amp * sin(u * (1.1f + 0.35f * i) + phase * (1f + 0.3f * i) + i * 1.7f) -
                        amp * 0.45f * sin(u * (2.3f + 0.5f * i) - phase * 1.4f + i) -
                        h * 0.012f * treble * sin(u * 7f + phase * 4f)
                    lineTo(x, y)
                }
                lineTo(w, h)
                close()
            }
            val alpha = visible * (if (dark) 0.24f else 0.16f) * (0.7f + 0.4f * energy + 0.35f * kick) * (1f - 0.15f * i)
            drawPath(
                path,
                Brush.verticalGradient(
                    listOf(color.copy(alpha = alpha), color.copy(alpha = alpha * 0.35f)),
                    startY = base - amp * 1.5f,
                    endY = h
                )
            )
        }
    }
}

/**
 * A still glow in the artwork's colours, for under the glass (issue #41). Glass re-renders whenever
 * its source changes, so this changes only with the song (colour fade) or play state (fade in/out);
 * the moving [AmbientWaves] stay outside the glass source.
 */
@Composable
fun AmbientGlow(playing: Boolean, colors: List<Color>, dark: Boolean, modifier: Modifier = Modifier) {
    val on = AmbientPrefs.enabled && colors.isNotEmpty()
    val strength by animateFloatAsState(if (on && playing) 1f else 0f, tween(700), label = "glow")
    val c0 by animateColorAsState(colors.getOrElse(0) { Color.Transparent }, tween(1200), label = "glow0")
    val c1 by animateColorAsState(colors.getOrElse(1) { colors.getOrElse(0) { Color.Transparent } }, tween(1200), label = "glow1")
    Canvas(modifier.fillMaxSize()) { // state read here, in draw: no recomposition while it fades
        if (strength < FADED) return@Canvas
        val a = strength * (if (dark) 0.22f else 0.14f)
        drawRect(Brush.verticalGradient(0.45f to Color.Transparent, 0.72f to c0.copy(alpha = a), 1f to c1.copy(alpha = a * 0.8f)))
    }
}

/** What the waves are doing, smoothed so beats swell rather than flicker. */
private class WaveState {
    val visible = Envelope(attackMs = 500f, releaseMs = 700f)
    val bass = Envelope(attackMs = 35f, releaseMs = 300f)
    private val beat = BeatPulse()
    var kick = 0f
        private set
    val energy = Envelope(attackMs = 120f, releaseMs = 700f)
    val treble = Envelope(attackMs = 40f, releaseMs = 250f)
    var phase = 0f

    fun advance(playing: Boolean, dt: Float) {
        // No fresh levels while playing (offloaded audio skips the meter): breathe gently instead.
        val l = if (playing) AudioLevels.now() ?: IDLE else Levels.Silent
        visible.step(if (playing) 1f else 0f, dt)
        bass.step(l.bass, dt)
        kick = beat.step(l.bass, dt)
        energy.step(l.energy, dt)
        treble.step(l.treble, dt)
        // Slow drift on its own; the music speeds it up a little.
        phase += dt / 1000f * (0.35f + 0.9f * energy.value)
    }
}

private fun isPowerSave(context: Context) =
    context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true

private val IDLE = Levels(energy = 0.35f, bass = 0.3f, treble = 0.15f)
private const val FADED = 0.003f
private const val FRAME_MS = 32f
private const val SCALE = 4
