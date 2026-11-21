package com.example.juke.ui.components

import androidx.compose.animation.animateColorAsState
import android.os.Build
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.util.VelocityTracker
import com.example.juke.ui.theme.glassIsLive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateIntSizeAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import com.example.juke.ui.theme.GlassShapes
import com.example.juke.ui.theme.LocalGlassAccent
import com.example.juke.ui.theme.glassLens
import com.example.juke.ui.theme.liquidGlass
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class GlassNavItem(
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
    val icon: @Composable () -> Unit,
)

/**
 * Floating liquid-glass tab bar (after Apple's Liquid Glass): a clear capsule that bends the content
 * behind it at the rim, icons only, and a round glass bubble on the selected tab. Same destinations
 * and semantics as a Material navigation bar (selectable tabs with labels for accessibility).
 */
@Composable
fun GlassNavBar(items: List<GlassNavItem>, modifier: Modifier = Modifier) {
    // The capsule hugs its icons and floats centred: a full-width bar around three icons is mostly
    // empty glass, and its ends smear over whatever artwork scrolls beneath.
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        LiquidBar(Modifier) {
            GlassTabGroup(items = items, vertical = false, modifier = Modifier.padding(6.dp))
        }
    }
}

/**
 * The bar's glass is its own layer behind the tabs: the refraction shader bends everything in its
 * layer, and the icons must stay crisp.
 */
@Composable
private fun LiquidBar(modifier: Modifier, content: @Composable () -> Unit) {
    val dark = com.example.juke.ui.theme.isGlassDark() // follows the app theme, not just the system
    Box(modifier) {
        Box(
            Modifier
                .matchParentSize()
                .liquidGlass(
                    shape = GlassShapes.Pill,
                    // Clear glass: a light smoke in dark mode, a light frost in light mode.
                    fill = if (dark) Color(0xFF0E0E12).copy(alpha = 0.32f) else Color.White.copy(alpha = 0.38f),
                    blur = 10.dp,
                    shadow = 6.dp,
                )
        )
        content()
    }
}

/**
 * One clear liquid lens over the selected tab, the same glass as the player's Lyrics / Up next / Mix
 * dock. The icons are its haze source and are cut out beneath it, so what shows through the lens is
 * the icon bent at its rim, not a tinted pill behind it. It travels on a loose spring, stretches
 * along its travel and squashes across it in proportion to its speed (a drag's real finger speed
 * too), its rim flaring while it moves. On the horizontal bar it can be dragged; let go, it carries
 * the flick's momentum to the tab it is heading for.
 */
@Composable
private fun GlassTabGroup(items: List<GlassNavItem>, vertical: Boolean, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val bounds = remember { mutableStateMapOf<Int, Pair<IntOffset, IntSize>>() }
    val selectedIndex = items.indexOfFirst { it.selected }
    val selected = bounds[selectedIndex]
    // Position along the travel axis, in px; velocity is read straight off the animation.
    val pos = remember { Animatable(0f) }
    var dragging by remember { mutableStateOf(false) }
    var placed by remember { mutableStateOf(false) }
    // Finger speed while dragging (snapTo leaves the animation's own velocity at zero).
    var dragVelocity by remember { mutableFloatStateOf(0f) }
    val currentItems by rememberUpdatedState(items)
    fun axis(o: IntOffset) = if (vertical) o.y.toFloat() else o.x.toFloat()
    val lensSpring = spring<Float>(dampingRatio = 0.8f, stiffness = 300f) // a tap settles; only a drag release carries momentum
    val speed = { ((if (dragging) kotlin.math.abs(dragVelocity) else kotlin.math.abs(pos.velocity)) / 5000f).coerceIn(0f, 1f) }

    LaunchedEffect(selected, dragging) {
        val target = selected?.first?.let(::axis) ?: return@LaunchedEffect
        if (dragging) return@LaunchedEffect
        if (!placed) { pos.snapTo(target); placed = true } else pos.animateTo(target, lensSpring, pos.velocity)
    }
    val size by animateIntSizeAsState(
        selected?.second ?: IntSize.Zero,
        spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "lensSize"
    )
    val cross = selected?.first?.let { if (vertical) it.x else it.y } ?: 0

    val dragModifier = if (vertical) Modifier else Modifier.pointerInput(Unit) {
        val tracker = VelocityTracker()
        fun range(): Pair<Float, Float> {
            val lo = bounds.values.minOfOrNull { it.first.x }?.toFloat() ?: 0f
            val hi = bounds.values.maxOfOrNull { it.first.x }?.toFloat() ?: 0f
            return lo to hi
        }
        fun release() {
            val v = tracker.calculateVelocity().x
            tracker.resetTracking()
            dragging = false
            dragVelocity = 0f
            // Land where the flick was heading: the lens' position projected a little by its speed.
            val projected = pos.value + v * 0.12f
            val nearest = bounds.entries.minByOrNull { (_, b) -> kotlin.math.abs(b.first.x - projected) }?.key ?: return
            val (lo, hi) = range()
            scope.launch {
                // Hand the finger's speed to the spring so the lens keeps moving, then settles.
                pos.snapTo(pos.value.coerceIn(lo, hi))
                val target = bounds[nearest]?.first?.x?.toFloat() ?: return@launch
                val go = currentItems.getOrNull(nearest)
                if (go != null && !go.selected) go.onClick()
                pos.animateTo(target, lensSpring, v)
            }
        }
        detectHorizontalDragGestures(
            onDragStart = { tracker.resetTracking(); dragging = true },
            onDragCancel = { release() },
            onDragEnd = { release() },
            onHorizontalDrag = { change, dx ->
                change.consume()
                tracker.addPosition(change.uptimeMillis, change.position)
                dragVelocity = tracker.calculateVelocity().x
                val (lo, hi) = range()
                // Past the end tabs the glass resists: it gives a little, like rubber, instead of stopping dead.
                val next = pos.value + dx
                val resisted = when {
                    next < lo -> lo - (lo - next).coerceAtMost(48f) * 0.35f
                    next > hi -> hi + (next - hi).coerceAtMost(48f) * 0.35f
                    else -> next
                }
                scope.launch { pos.snapTo(resisted) }
            }
        )
    }

    val live = glassIsLive() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val neon = rememberNeon(LocalGlassAccent.current)
    val iconsHaze = remember { HazeState() }
    val density = LocalDensity.current
    // Where the lens sits, in the icons' coordinates; the icons are cut out there.
    fun lensRect(): Rect {
        val p = pos.value
        return if (vertical) Rect(Offset(cross.toFloat(), p), Size(size.width.toFloat(), size.height.toFloat()))
        else Rect(Offset(p, cross.toFloat()), Size(size.width.toFloat(), size.height.toFloat()))
    }

    Box(modifier = modifier.then(dragModifier)) {
        val tabModifier = { i: Int ->
            Modifier.onPlaced { bounds[i] = it.positionInParent().round() to it.size }
        }
        val cutOut = if (live && selected != null && placed) Modifier.drawWithContent {
            val hole = Path().apply { addRoundRect(RoundRect(lensRect(), CornerRadius(size.height / 2f))) }
            clipPath(hole, ClipOp.Difference) { this@drawWithContent.drawContent() }
        } else Modifier
        Box(Modifier.then(cutOut).hazeSource(iconsHaze)) {
            if (vertical) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    items.forEachIndexed { i, item -> GlassNavTab(item, tabModifier(i).fillMaxWidth()) }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items.forEachIndexed { i, item -> GlassNavTab(item, tabModifier(i).width(64.dp)) }
                }
            }
        }
        if (selected != null && placed) {
            with(density) {
                Box(
                    Modifier
                        .offset {
                            if (vertical) IntOffset(cross, pos.value.roundToInt())
                            else IntOffset(pos.value.roundToInt(), cross)
                        }
                        .size(size.width.toDp(), size.height.toDp())
                        .graphicsLayer {
                            val v = speed()
                            val grab = if (dragging) 0.08f else 0f
                            val along = 1f + 0.38f * v + grab
                            val across = 1f - 0.16f * v + grab * 0.5f
                            scaleX = if (vertical) across else along
                            scaleY = if (vertical) along else across
                        }
                        // The neon ring caught in the lens edge; it flares while the lens travels or is held.
                        .neonRim(neon) { speed() + if (dragging) 0.4f else 0f }
                        .then(
                            // Pure clear glass over the icons: no tint, no frost. It bends the icon
                            // at its rim, with the rainbow edge swelling while it moves or is held.
                            if (live) Modifier.liquidGlass(
                                GlassShapes.Pill, Color.Transparent, blur = 0.dp, rimAlpha = 0.7f, source = iconsHaze,
                                displacement = 18.dp, edge = 14.dp, fold = 0f,
                                strength = { 0.5f + speed() + if (dragging) 0.5f else 0f }
                            ) else Modifier.glassLens(GlassShapes.Pill, LocalGlassAccent.current)
                        )
                )
            }
        }
    }
}

@Composable
private fun GlassNavTab(item: GlassNavItem, modifier: Modifier = Modifier) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val accent = LocalGlassAccent.current
    val tint by animateColorAsState(
        // The selected icon is the neon itself, so it matches its glow and the lens edge.
        targetValue = if (item.selected) rememberNeon(accent) else onSurface.copy(alpha = 0.78f),
        animationSpec = tween(180),
        label = "navTint"
    )
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (item.selected) 1.08f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "navScale"
    )
    // Icons only, like Liquid Glass tab bars; the label stays for TalkBack.
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(GlassShapes.Pill)
            .selectable(
                selected = item.selected,
                role = Role.Tab,
                onClick = item.onClick,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            )
            .semantics { contentDescription = item.label },
        contentAlignment = Alignment.Center
    ) {
        val neon = rememberNeon(accent)
        val glow by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (item.selected) 1f else 0f,
            animationSpec = tween(260),
            label = "navGlow"
        )
        Box(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }) {
            NeonGlowIcon(tint = tint, glow = neon.copy(alpha = glow), icon = item.icon)
        }
    }
}

/** Expanded-width counterpart: a vertical floating glass rail. */
@Composable
fun GlassNavRail(items: List<GlassNavItem>, modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 12.dp)) {
        LiquidBar(Modifier.width(68.dp)) {
            GlassTabGroup(
                items = items,
                vertical = true,
                modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp)
            )
        }
    }
}

/**
 * The neon colour for the glow: the album-art accent pushed to full brightness and saturation so it
 * reads as light, or the theme primary when the accent is nearly grey (a grey neon is just a line).
 */
@Composable
internal fun rememberNeon(accent: Color): Color {
    val primary = MaterialTheme.colorScheme.primary
    return remember(accent, primary) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(accent.toArgb(), hsv)
        if (hsv[1] < 0.12f) android.graphics.Color.colorToHSV(primary.toArgb(), hsv)
        hsv[1] = hsv[1].coerceIn(0.55f, 0.9f)
        hsv[2] = hsv[2].coerceAtLeast(0.95f)
        Color(android.graphics.Color.HSVToColor(hsv))
    }
}

/**
 * Neon edge for the selection lens: a soft bloom just outside the pill, a crisp rim whose
 * brightness sweeps around it (brightest where light would strike, top-left and bottom-right),
 * and a faint pool of light inside. [energy] (0..1+, lens speed) flares the bloom while it moves.
 * The bloom sits behind the glass; the tube and the inner light sit on top of it, so the lens'
 * frost can't wash them out.
 */
internal fun Modifier.neonRim(neon: Color, energy: () -> Float = { 0f }): Modifier = drawWithContent {
    val e = energy().coerceIn(0f, 1f)
    val r = size.height / 2f
    val corner = CornerRadius(r, r)
    val bloomPx = 6.dp.toPx()

    // Bloom: a wide blurred stroke (mask blur is drawn on Android 9+; flat before that).
    drawIntoCanvas { canvas ->
        val paint = Paint().apply {
            color = neon.copy(alpha = 0.6f + 0.35f * e)
            style = PaintingStyle.Stroke
            strokeWidth = 4.dp.toPx()
        }
        paint.asFrameworkPaint().maskFilter =
            android.graphics.BlurMaskFilter(bloomPx, android.graphics.BlurMaskFilter.Blur.NORMAL)
        canvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, paint)
    }

    drawContent()

    // Inner light pooling toward the bottom of the lens.
    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(neon.copy(alpha = 0.22f + 0.12f * e), Color.Transparent),
            center = Offset(size.width / 2f, size.height),
            radius = size.width * 0.6f
        ),
        cornerRadius = corner
    )

    // The tube: a crisp rim with the light sweeping around it.
    val stroke = 1.6.dp.toPx()
    drawRoundRect(
        brush = Brush.sweepGradient(
            0f to neon.copy(alpha = 0.95f),
            0.18f to neon.copy(alpha = 0.5f),
            0.5f to neon,
            0.68f to neon.copy(alpha = 0.5f),
            1f to neon,
            center = Offset(size.width / 2f, size.height / 2f)
        ),
        topLeft = Offset(stroke / 2f, stroke / 2f),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(r - stroke / 2f, r - stroke / 2f),
        style = Stroke(width = stroke)
    )
}

/**
 * An icon with a neon glow: a blurred copy of itself in [glow] underneath the crisp icon in
 * [tint] (the blur draws on Android 12+; earlier versions show just the crisp icon).
 */
@Composable
internal fun NeonGlowIcon(tint: Color, glow: Color, icon: @Composable () -> Unit) {
    Box(contentAlignment = Alignment.Center) {
        if (glow.alpha > 0f) {
            CompositionLocalProvider(LocalContentColor provides glow) {
                Box(Modifier.blur(4.dp, BlurredEdgeTreatment.Unbounded)) { icon() }
            }
        }
        CompositionLocalProvider(LocalContentColor provides tint) { icon() }
    }
}
