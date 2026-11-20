package com.example.juke.ui.theme

import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.ChromaticAberrationMode
import dev.chrisbanes.haze.glass.OpticalSizeValue
import dev.chrisbanes.haze.glass.RefractionProfile
import dev.chrisbanes.haze.glass.hazeGlass

/**
 * Liquid glass design system for JUKE.
 *
 * Two materials, used deliberately:
 *  - **Float glass** (`glassFloat`): real backdrop blur via Haze. Only for surfaces that hover over
 *    scrolling content (nav bar, mini player, top bars, pills over artwork).
 *  - **Pane glass** (`glassPane`): tinted fill, specular rim and sheen without blur. For in-flow
 *    surfaces (cards, rows, chips, fields) that sit on the ambient field, where blurring a smooth
 *    gradient would cost frames and show nothing.
 *
 * Both share the same rim and sheen so they read as one material.
 */
enum class GlassLevel { Thin, Regular, Thick }

/** Glass follows the active color scheme, so a screen can force dark (the player does) without the system flag. */
@Composable
fun isGlassDark(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/** Shared backdrop source for floating glass. Provided by the root layout. */
val LocalHazeState = compositionLocalOf<HazeState?> { null }

/** The accent the ambient field and glass tint pick up (album art when available). */
val LocalGlassAccent = compositionLocalOf { Color(0xFF8E7CFF) }

/**
 * The player's "maximum" glass: deeper refraction, a thicker fold, full rainbow and a brighter
 * highlight. Set true around a screen to boost every glass surface inside it.
 */
val LocalGlassBoost = compositionLocalOf { false }

/** Whether glass is drawn as real liquid here (a source exists and solid surfaces are off). */
@Composable
fun glassIsLive(): Boolean = LocalHazeState.current != null && !GlassPrefs.solid

object GlassShapes {
    val Pill = RoundedCornerShape(percent = 50)
    val Control = RoundedCornerShape(18.dp)
    val Card = RoundedCornerShape(24.dp)
    val Sheet = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    val Bar = RoundedCornerShape(28.dp)
}

private data class GlassSpec(
    val fill: Float,
    val blur: Dp,
    val rimAlpha: Float,
    val elevation: Dp,
)

private fun GlassLevel.spec(dark: Boolean) = when (this) {
    GlassLevel.Thin -> GlassSpec(if (dark) 0.07f else 0.62f, 14.dp, 0.30f, 2.dp)
    GlassLevel.Regular -> GlassSpec(if (dark) 0.11f else 0.78f, 22.dp, 0.38f, 6.dp)
    GlassLevel.Thick -> GlassSpec(if (dark) 0.16f else 0.90f, 32.dp, 0.45f, 12.dp)
}

@Composable
private fun glassBase(accent: Color, dark: Boolean): Color =
    if (dark) lerp(Color.White, accent, 0.08f) else Color.White

/**
 * Fill color for the near-opaque "solid surfaces" mode. Dark glass is a faint white tint, which turns
 * into a white slab when pushed to 94% opacity, so solid dark surfaces use a dark panel instead.
 */
private fun solidBase(glass: Color, accent: Color, dark: Boolean): Color =
    if (dark) lerp(Color(0xFF1C1C22), accent, 0.10f) else glass

/** Specular rim: bright at the lit top-left edge, fades through the body, catches again bottom-right. */
private fun Modifier.glassRim(shape: Shape, rimAlpha: Float, dark: Boolean): Modifier =
    drawWithContent {
        drawContent()
        val outline = shape.createOutline(size, layoutDirection, this)
        val lit = Color.White.copy(alpha = rimAlpha)
        val bounce = Color.White.copy(alpha = rimAlpha * 0.38f)
        val hairline = 1.dp.toPx()
        // Sheen: light refracting through the top of the pane.
        drawOutline(
            outline = outline,
            brush = Brush.verticalGradient(
                colors = listOf(Color.White.copy(alpha = if (dark) 0.09f else 0.30f), Color.Transparent),
                startY = 0f,
                endY = size.height * 0.55f
            )
        )
        drawOutline(
            outline = outline,
            brush = Brush.linearGradient(
                colors = listOf(lit, Color.White.copy(alpha = rimAlpha * 0.06f), bounce),
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            ),
            style = Stroke(width = hairline)
        )
    }

/**
 * Soft lifted shadow drawn only OUTSIDE the shape. The platform elevation shadow also paints under
 * a translucent fill, which muddies glass; this clips the interior away.
 */
private fun Modifier.glassShadow(shape: Shape, elevation: Dp, alpha: Float): Modifier =
    this.drawBehind {
        val outline = shape.createOutline(size, layoutDirection, this)
        val path = Path().apply {
            when (outline) {
                is Outline.Rectangle -> addRect(outline.rect)
                is Outline.Rounded -> addRoundRect(outline.roundRect)
                is Outline.Generic -> addPath(outline.path)
            }
        }
        val reach = elevation.toPx()
        val dy = reach * 0.45f
        clipPath(path, ClipOp.Difference) {
            val steps = 7
            for (i in steps downTo 1) {
                val t = i / steps.toFloat()
                translate(top = dy) {
                    drawOutline(
                        outline = outline,
                        color = Color.Black.copy(alpha = alpha * (1f - t) * 0.5f),
                        style = Stroke(width = reach * 2f * t)
                    )
                }
            }
        }
    }

/** In-flow glass: tinted fill + rim + sheen + soft lifted shadow. No blur. */
@Composable
fun Modifier.glassPane(
    shape: Shape = GlassShapes.Card,
    level: GlassLevel = GlassLevel.Regular,
    tint: Color? = null,
): Modifier {
    val dark = isGlassDark()
    val spec = level.spec(dark)
    val base = glassBase(tint ?: LocalGlassAccent.current, dark)
    val source = LocalHazeState.current
    val lifted = this.glassShadow(shape, spec.elevation, 0.30f)
    if (source == null || GlassPrefs.solid) {
        return lifted
            .clip(shape)
            .background(
                if (GlassPrefs.solid) solidBase(base, tint ?: LocalGlassAccent.current, dark).copy(alpha = 0.94f)
                else base.copy(alpha = spec.fill)
            )
            .glassRim(shape, spec.rimAlpha, dark)
    }
    // In-flow surfaces are clear liquid: no frost, no white-point wash and no accent tint, only a
    // whisper of neutral body so the pane reads as glass. Rim refraction, Fresnel edge, specular
    // and rainbow carry the material.
    val boost = LocalGlassBoost.current
    val clearTint = if (dark) Color.White.copy(alpha = 0.035f + 0.01f * level.ordinal) else Color.White.copy(alpha = 0.28f)
    val style = remember(clearTint, shape, dark, boost) {
        liquidStyle(
            boost = boost,
            shape = shape,
            tint = clearTint,
            dark = dark,
            blur = 3.dp,
            displacement = 48.dp,
            edge = 24.dp,
            fold = 0.25f,
            clear = true,
            rainbow = { 0.6f },
        )
    }
    return lifted.hazeLiquid(source, shape, style)
}

/**
 * The one liquid-glass recipe every surface uses; presets only change its strength. Dense-liquid
 * optics: a wide, strongly bending rim with an inverted fold (the meniscus of a thick fluid),
 * seven-sample spectral dispersion for the rainbow, a bright Fresnel edge, and a highlight placed
 * by [GlassLight] (the gravity sensor). Snapshot reads inside [rainbow] are observed per frame.
 */
private fun liquidStyle(
    shape: Shape,
    tint: Color,
    dark: Boolean,
    blur: Dp,
    displacement: Dp = 64.dp,
    edge: Dp = 30.dp,
    fold: Float = 0.35f,
    specular: Float = 0.7f,
    clear: Boolean = false,
    boost: Boolean = false,
    rainbow: () -> Float = { 0.85f },
): GlassStyle = (if (clear) GlassStyle.clear else GlassStyle.regular).shaped(shape).then {
    val displacement = if (boost) displacement * 1.3f else displacement
    val edge = if (boost) edge + 8.dp else edge
    val fold = if (boost) (fold + 0.15f).coerceAtMost(1f) else fold
    val specular = if (boost) (specular + 0.1f).coerceAtMost(1f) else specular
    optics {
        copy(
            blurRadius = OpticalSizeValue.Fixed(blur),
            // Regular keeps compact controls partly sharp (depth 0.65 at 64dp), which let list
            // text show through the mini player over its own title. Fully diffuse the body;
            // the rim still bends sharp content.
            depth = OpticalSizeValue.Fixed(if (clear) 0.6f else 1f),
            refractionStrength = if (clear) 1f else 0.95f,
            refractionDisplacement = displacement,
            refractionProfile = RefractionProfile.Edge(edge),
            refractionFoldStrength = fold,
            refractionDetailIntensity = 0.45f,
        )
    }
    chromaticAberrationMode(ChromaticAberrationMode.Full)
    fresnelExponent(2f)
    tint(tint)
    // Haze's dark Regular pulls the white point to -0.22, which reads as a grey slab.
    if (!clear) {
        whitePoint(if (dark) 0.06f else 0.5f)
        contrast(0f)
        ambientResponse(0.22f)
    }
    specularIntensity(specular)
    lightPosition(GlassLight.position)
    chromaticAberrationStrength((if (boost) rainbow() * 1.25f + 0.1f else rainbow()).coerceIn(0f, 1f))
}

/**
 * Haze 2 liquid glass over [source]: refraction at the rim, a specular highlight and a faint
 * rainbow (chromatic aberration) edge. The node draws the material behind its own content, and
 * the content is clipped to [shape] after it so the refraction can sample beyond the edge.
 */
private fun Modifier.hazeLiquid(source: HazeState, shape: Shape, style: GlassStyle): Modifier =
    hazeGlass(HazeInput.Sources(source), style).clip(shape)

/** Applies [shape] to a glass style when it is a rounded rectangle (the only shape Haze glass takes). */
private fun GlassStyle.shaped(shape: Shape): GlassStyle =
    (shape as? RoundedCornerShape)?.let { rounded -> then { shape(rounded) } } ?: this

/**
 * Liquid glass (after Apple's design): a clear pane that bends what is behind it at the rim, with a
 * specular highlight and a rainbow edge that grows with [strength] (0..1, read every frame, e.g.
 * while the pane moves). Without a haze source, or in solid mode (always on below Android 13), it
 * falls back to a tinted pane.
 */
@Composable
fun Modifier.liquidGlass(
    shape: Shape,
    fill: Color,
    blur: Dp = 8.dp,
    shadow: Dp = 0.dp,
    rimAlpha: Float = 0.5f,
    source: HazeState? = LocalHazeState.current,
    strength: () -> Float = { 0f },
): Modifier {
    val dark = isGlassDark()
    val lifted = if (shadow > 0.dp) glassShadow(shape, shadow, 0.34f) else this
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || source == null || GlassPrefs.solid) {
        val solid = fill.copy(alpha = (fill.alpha + if (GlassPrefs.solid) 0.6f else 0.3f).coerceAtMost(0.96f))
        return lifted.clip(shape).background(solid).glassRim(shape, rimAlpha, dark)
    }
    val boost = LocalGlassBoost.current
    val style = remember(fill, shape, blur, dark, boost) {
        liquidStyle(
            boost = boost,
            shape = shape,
            tint = fill,
            dark = dark,
            blur = blur,
            fold = 0.4f,
            specular = 0.66f,
            clear = true,
            // The rainbow swells while the pane moves.
            rainbow = { 0.45f + 0.5f * strength() },
        )
    }
    return lifted.hazeLiquid(source, shape, style)
}

/**
 * The moving selection bubble: the same liquid glass as its bar, a little brighter, no shadow (it
 * sits inside the bar). [strength] swells the refraction while it moves.
 */
@Composable
fun Modifier.glassLens(
    shape: Shape = GlassShapes.Pill,
    tint: Color? = null,
    source: HazeState? = LocalHazeState.current,
    strength: () -> Float = { 0f },
): Modifier {
    val dark = isGlassDark()
    val base = glassBase(tint ?: LocalGlassAccent.current, dark)
    return liquidGlass(
        shape = shape,
        fill = base.copy(alpha = if (dark) 0.13f else 0.5f),
        blur = 6.dp,
        rimAlpha = if (dark) 0.32f else 0.55f,
        source = source,
        strength = strength,
    )
}

/**
 * Where the light sits for glass highlights, driven by the gravity sensor so the specular rim
 * stays "up" in the real world as the phone tilts (an idea from QWEA0's Liquid-Glass-Android).
 * Biases are in the material's frame; beyond -1..1 the light sits outside the pane, which reads
 * as a distant light. Upright portrait puts it above the top edge, the static look before.
 */
object GlassLight {
    private const val REACH = 1.6f
    val Rest = androidx.compose.ui.BiasAlignment(0f, -REACH)
    var position: androidx.compose.ui.Alignment by androidx.compose.runtime.mutableStateOf(Rest)
        private set

    private var x = 0f
    private var y = -REACH

    /** Feeds one gravity reading (device axes, m/s²) for the given display rotation. */
    internal fun onGravity(gx: Float, gy: Float, rotation: Int) {
        // Remap device axes to the screen's axes (y up), then flip y for layout space (y down).
        val (sx, sy) = when (rotation) {
            android.view.Surface.ROTATION_90 -> -gy to gx
            android.view.Surface.ROTATION_180 -> -gx to -gy
            android.view.Surface.ROTATION_270 -> gy to -gx
            else -> gx to gy
        }
        val g = android.hardware.SensorManager.GRAVITY_EARTH
        val tx = (sx / g).coerceIn(-1f, 1f) * REACH
        val ty = (-sy / g).coerceIn(-1f, 1f) * REACH
        // Low-pass filter: hand tremor stays out of the highlight.
        x += (tx - x) * 0.12f
        y += (ty - y) * 0.12f
        val current = position as? androidx.compose.ui.BiasAlignment
        // Only publish visible moves; each publish re-resolves every glass style.
        if (current == null || kotlin.math.abs(current.horizontalBias - x) > 0.015f ||
            kotlin.math.abs(current.verticalBias - y) > 0.015f
        ) position = androidx.compose.ui.BiasAlignment(x, y)
    }

    internal fun reset() {
        x = 0f; y = -REACH; position = Rest
    }
}

/**
 * Listens to the gravity sensor while the app is resumed and steers [GlassLight]. Place once at
 * the app root. Off when animations are disabled (reduced motion) or glass is not drawn.
 */
@Composable
fun GlassTiltLight() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val view = LocalView.current
    androidx.lifecycle.compose.LifecycleResumeEffect(context, GlassPrefs.solid) {
        val sensors = context.getSystemService(android.content.Context.SENSOR_SERVICE) as? android.hardware.SensorManager
        val sensor = sensors?.getDefaultSensor(android.hardware.Sensor.TYPE_GRAVITY)
            ?: sensors?.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
        val reducedMotion = android.provider.Settings.Global.getFloat(
            context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) == 0f
        val listener = object : android.hardware.SensorEventListener {
            override fun onSensorChanged(event: android.hardware.SensorEvent) {
                GlassLight.onGravity(event.values[0], event.values[1], view.display?.rotation ?: 0)
            }
            override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) = Unit
        }
        val listening = sensor != null && !reducedMotion && !GlassPrefs.solid &&
            sensors?.registerListener(listener, sensor, android.hardware.SensorManager.SENSOR_DELAY_GAME) == true
        if (!listening) GlassLight.reset()
        onPauseOrDispose { if (listening) sensors?.unregisterListener(listener) }
    }
}

/**
 * Translucency switch (blur and see-through fills); when solid, surfaces become near-opaque.
 *
 * Glass needs Android 13+: on Android 12 and older, RenderEffect blur on some GPUs paints full-height
 * grey bands through the screen, so those devices are always solid and the setting is locked.
 */
object GlassPrefs {
    /** Whether this device renders glass correctly. The desktop app always does. */
    val supported: Boolean = com.example.juke.account.Account.isDesktop ||
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    private var chosen by androidx.compose.runtime.mutableStateOf(false)

    /** The user's choice, forced on where glass is unsupported. */
    var solid: Boolean
        get() = chosen || !supported
        set(value) { chosen = value }
}

/** Floating glass: real backdrop blur of whatever scrolls beneath, plus tint, rim and sheen. */
@Composable
fun Modifier.glassFloat(
    shape: Shape = GlassShapes.Bar,
    level: GlassLevel = GlassLevel.Regular,
    tint: Color? = null,
    source: HazeState? = LocalHazeState.current,
): Modifier {
    val dark = isGlassDark()
    val spec = level.spec(dark)
    val base = glassBase(tint ?: LocalGlassAccent.current, dark)
    val lifted = this.glassShadow(shape, spec.elevation, 0.36f)
    if (source == null || GlassPrefs.solid) {
        return lifted
            .clip(shape)
            .background(
                if (GlassPrefs.solid) solidBase(base, tint ?: LocalGlassAccent.current, dark).copy(alpha = 0.96f)
                else base.copy(alpha = (spec.fill + 0.30f).coerceAtMost(0.9f))
            )
            .glassRim(shape, spec.rimAlpha, dark)
    }
    val boost = LocalGlassBoost.current
    val style = remember(base, spec, shape, dark, boost) {
        liquidStyle(shape = shape, tint = base.copy(alpha = spec.fill * 1.1f), dark = dark, blur = spec.blur, boost = boost)
    }
    return lifted.hazeLiquid(source, shape, style)
}

/** The one app backdrop: true black in dark, a soft off-white in light. No gradients, no motion. */
object GlassBackdrop {
    val Dark = Color.Black
    val Light = Color(0xFFF2F2F7)
    fun color(dark: Boolean): Color = if (dark) Dark else Light
}

/**
 * The layer behind every page, today just the app backdrop. Give it
 * `hazeSource(state, zIndex = -1f)` under the page's own source: glass inside the page samples
 * only sources below its ancestor, so cards refract this layer while floating glass (outside both)
 * refracts everything. [content] draws on top of the backdrop (the music waves).
 */
@Composable
fun GlassAmbient(modifier: Modifier = Modifier, content: @Composable () -> Unit = {}) {
    androidx.compose.foundation.layout.Box(
        modifier.fillMaxSize().background(GlassBackdrop.color(isGlassDark()))
    ) { content() }
}

/**
 * Liquid glass for a sheet or dialog surface. Sheets live in their own window, so they sample the
 * app's sources from the window behind ([HazeInput.Sources] reads across windows). Returns null
 * when glass is off, so callers keep their solid container colour.
 */
@Composable
fun glassSheetModifier(shape: Shape, source: HazeState? = LocalHazeState.current): Modifier? {
    if (source == null || GlassPrefs.solid) return null
    val dark = isGlassDark()
    val tint = lerp(MaterialTheme.colorScheme.surface, glassBase(LocalGlassAccent.current, dark), 0.10f)
        .copy(alpha = if (dark) 0.55f else 0.62f)
    val style = remember(shape, tint, dark) {
        liquidStyle(shape = shape, tint = tint, dark = dark, blur = 30.dp, displacement = 56.dp, edge = 28.dp, rainbow = { 0.7f })
    }
    return Modifier.hazeLiquid(source, shape, style)
}

/**
 * Blurs the real window behind a dialog or bottom sheet (API 31+). Dialog windows can't be
 * sampled by Haze, so the platform does it: the app behind dissolves into frosted light.
 * Below API 31 this is a no-op and the sheet's thicker tint carries the material alone.
 */
@Composable
fun GlassWindowBlur(radius: Dp = 28.dp, dim: Float = 0.28f) {
    val view = LocalView.current
    DisposableEffect(view, radius, dim) {
        val dialogWindow = (view.parent as? DialogWindowProvider)?.window
        if (dialogWindow != null) {
            dialogWindow.setDimAmount(dim)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dialogWindow.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                val params = dialogWindow.attributes
                params.blurBehindRadius = (radius.value * view.resources.displayMetrics.density).toInt()
                dialogWindow.attributes = params
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Popup-hosted sheets: patch the root view's window params directly.
            val root = view.rootView
            val params = root.layoutParams as? WindowManager.LayoutParams
            if (params != null && root.isAttachedToWindow) {
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND or WindowManager.LayoutParams.FLAG_DIM_BEHIND
                params.dimAmount = dim
                params.blurBehindRadius = (radius.value * view.resources.displayMetrics.density).toInt()
                runCatching {
                    (view.context.getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager)
                        .updateViewLayout(root, params)
                }
            }
        }
        onDispose { }
    }
}

/** Container color for sheets and dialogs: the thick glass fill over the window blur. */
@Composable
fun glassSheetColor(): Color {
    val dark = isGlassDark()
    val base = glassBase(LocalGlassAccent.current, dark)
    val surface = MaterialTheme.colorScheme.surface
    // Window blur only exists on 31+; below that the sheet needs to be nearly opaque to stay legible.
    val fill = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.78f else 0.94f
    return lerp(surface, base, 0.10f).copy(alpha = fill)
}

/**
 * Drop-in for Material `Card`: a column of pane glass. `accent` pours a semantic color
 * (error, primary, secondary container) into the glass without losing the material.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = GlassShapes.Card,
    level: GlassLevel = GlassLevel.Regular,
    accent: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        androidx.compose.foundation.layout.Column(
            modifier = modifier
                .glassPane(shape, level)
                .then(if (accent != null) Modifier.background(accent.copy(alpha = 0.30f)) else Modifier)
                .then(
                    if (onClick != null) Modifier.clickable(
                        role = androidx.compose.ui.semantics.Role.Button,
                        onClick = onClick
                    ) else Modifier
                ),
            content = content
        )
    }
}
