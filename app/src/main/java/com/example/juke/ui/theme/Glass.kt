package com.example.juke.ui.theme

import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

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
    return this
        .glassShadow(shape, spec.elevation, 0.30f)
        .clip(shape)
        .background(
            if (GlassPrefs.solid) solidBase(base, tint ?: LocalGlassAccent.current, dark).copy(alpha = 0.94f)
            else base.copy(alpha = spec.fill)
        )
        .glassRim(shape, spec.rimAlpha, dark)
}

/**
 * AGSL lens refraction (Android 13+). Content near the lens edge is pulled in from further toward
 * the center (magnifying rim), split slightly per color channel, and the effect swells with
 * the `strength` uniform while the lens is moving.
 */
private const val LENS_AGSL = """
uniform shader content;
uniform float2 size;
uniform float radius;
uniform float strength;

float sdRoundBox(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

half4 main(float2 fc) {
    float2 c = size * 0.5;
    float2 p = fc - c;
    float d = sdRoundBox(p, c, radius);
    float depth = clamp(-d / (min(size.x, size.y) * 0.5), 0.0, 1.0);
    float edge = 1.0 - smoothstep(0.0, 0.6, depth);
    float bend = edge * edge * (0.45 + 0.55 * strength);
    float2 dir = p / max(length(p), 1.0);
    float2 base = c + p * (1.0 - 0.06 * (0.4 + strength));
    float2 pull = -dir * bend * 16.0;
    // Color fringing only while the glass moves; at rest it is clear.
    half4 g = content.eval(base + pull);
    half r = content.eval(base + pull * (1.0 - 0.15 * strength)).r;
    half b = content.eval(base + pull * (1.0 + 0.2 * strength)).b;
    half spec = half(edge * edge * 0.10 * (0.5 + strength));
    return half4(clamp(half3(r, g.g, b) + spec, 0.0, 1.0) * g.a, g.a);
}
"""

/**
 * Liquid glass (after Apple's design): a clear pane that only lightly blurs what is behind it and
 * bends it at the rim through an AGSL refraction shader (Android 13+), with a specular rim. Below
 * Android 13, without a haze source, or in solid mode it falls back to a tinted pane.
 * [strength] (0..1) is read every frame and swells the refraction, e.g. while the pane moves.
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
    val style = remember(fill, blur) {
        HazeStyle(
            backgroundColor = Color.Transparent,
            tint = HazeTint(fill),
            blurRadius = blur,
            noiseFactor = 0.02f,
            fallbackTint = HazeTint(fill.copy(alpha = (fill.alpha + 0.3f).coerceAtMost(0.96f)))
        )
    }
    val shader = remember { android.graphics.RuntimeShader(LENS_AGSL) }
    return lifted
        .graphicsLayer {
            val corner = (shape as? RoundedCornerShape)?.topStart?.toPx(size, this) ?: 0f
            shader.setFloatUniform("size", size.width, size.height)
            shader.setFloatUniform("radius", corner)
            shader.setFloatUniform("strength", strength())
            renderEffect = android.graphics.RenderEffect
                .createRuntimeShaderEffect(shader, "content")
                .asComposeRenderEffect()
        }
        .clip(shape)
        .hazeEffect(source, style)
        .glassRim(shape, rimAlpha, dark)
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

/** User opt-out of translucency (blur and see-through fills): surfaces become near-opaque. */
object GlassPrefs {
    var solid by androidx.compose.runtime.mutableStateOf(false)
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
    val style = remember(base, spec) {
        HazeStyle(
            backgroundColor = Color.Transparent,
            tint = HazeTint(base.copy(alpha = spec.fill)),
            blurRadius = spec.blur,
            noiseFactor = 0.04f,
            fallbackTint = HazeTint(base.copy(alpha = (spec.fill + 0.30f).coerceAtMost(0.9f)))
        )
    }
    return this
        .glassShadow(shape, spec.elevation, 0.36f)
        .clip(shape)
        .then(
            if (source != null && !GlassPrefs.solid) Modifier.hazeEffect(source, style)
            else Modifier.background(
                if (GlassPrefs.solid) solidBase(base, tint ?: LocalGlassAccent.current, dark).copy(alpha = 0.96f)
                else base.copy(alpha = (spec.fill + 0.30f).coerceAtMost(0.9f))
            )
        )
        .glassRim(shape, spec.rimAlpha, dark)
}

/** The one app backdrop: true black in dark, a soft off-white in light. No gradients, no motion. */
object GlassBackdrop {
    val Dark = Color.Black
    val Light = Color(0xFFF2F2F7)
    fun color(dark: Boolean): Color = if (dark) Dark else Light
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
