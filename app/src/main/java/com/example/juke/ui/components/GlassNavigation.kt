package com.example.juke.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateIntSizeAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.round
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.height
import com.example.juke.ui.theme.liquidGlass
import androidx.compose.ui.unit.dp
import com.example.juke.ui.theme.GlassLevel
import com.example.juke.ui.theme.GlassShapes
import com.example.juke.ui.theme.LocalGlassAccent
import com.example.juke.ui.theme.glassFloat
import com.example.juke.ui.theme.glassLens
import com.example.juke.ui.theme.glassPane

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
 * One liquid selection lens. It travels on a loose spring, stretches along its direction of
 * travel in proportion to its speed while squashing across it, and swells slightly while moving,
 * like a drop of glass sliding between tabs. On the horizontal bar it can also be dragged and
 * released onto the nearest tab.
 */
@Composable
private fun GlassTabGroup(items: List<GlassNavItem>, vertical: Boolean, modifier: Modifier = Modifier) {
    val accent = LocalGlassAccent.current
    val scope = rememberCoroutineScope()
    val bounds = remember { mutableStateMapOf<Int, Pair<IntOffset, IntSize>>() }
    val selectedIndex = items.indexOfFirst { it.selected }
    val selected = bounds[selectedIndex]
    // Position along the travel axis, in px; velocity is read straight off the animation.
    val pos = remember { Animatable(0f) }
    var dragging by remember { mutableStateOf(false) }
    var placed by remember { mutableStateOf(false) }
    fun axis(o: IntOffset) = if (vertical) o.y.toFloat() else o.x.toFloat()
    val lensSpring = spring<Float>(dampingRatio = 0.8f, stiffness = 300f) // a tap settles; only a drag release carries momentum

    LaunchedEffect(selected, dragging) {
        val target = selected?.first?.let(::axis) ?: return@LaunchedEffect
        if (dragging) return@LaunchedEffect
        if (!placed) { pos.snapTo(target); placed = true } else pos.animateTo(target, lensSpring)
    }
    val size by animateIntSizeAsState(
        selected?.second ?: IntSize.Zero,
        spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "lensSize"
    )
    val cross = selected?.first?.let { if (vertical) it.x else it.y } ?: 0

    val dragModifier = if (vertical) Modifier else Modifier.pointerInput(items) {
        detectHorizontalDragGestures(
            onDragStart = { dragging = true },
            onDragCancel = { dragging = false },
            onDragEnd = { dragging = false },
            onHorizontalDrag = { change, dx ->
                change.consume()
                val lo = bounds.values.minOfOrNull { it.first.x }?.toFloat() ?: 0f
                val hi = bounds.values.maxOfOrNull { it.first.x }?.toFloat() ?: 0f
                scope.launch { pos.snapTo((pos.value + dx).coerceIn(lo, hi)) }
            }
        )
    }
    // On release, land on whichever tab the lens is nearest to (projected a little by its velocity).
    LaunchedEffect(dragging) {
        if (dragging || !placed) return@LaunchedEffect
        val p = pos.value
        val nearest = bounds.entries.minByOrNull { (_, v) -> kotlin.math.abs(v.first.x - p) }?.key ?: return@LaunchedEffect
        if (nearest != selectedIndex) items.getOrNull(nearest)?.onClick?.invoke()
        else bounds[nearest]?.let { pos.animateTo(it.first.x.toFloat(), lensSpring) }
    }

    Box(modifier = modifier.then(dragModifier)) {
        if (selected != null) {
            with(LocalDensity.current) {
                Box(
                    Modifier
                        .offset {
                            if (vertical) IntOffset(cross, pos.value.roundToInt())
                            else IntOffset(pos.value.roundToInt(), cross)
                        }
                        .size(size.width.toDp(), size.height.toDp())
                        .graphicsLayer {
                            val speed = (kotlin.math.abs(pos.velocity) / 5000f).coerceIn(0f, 1f)
                            val grab = if (dragging) 0.08f else 0f
                            val along = 1f + 0.38f * speed + grab
                            val across = 1f - 0.16f * speed + grab * 0.5f
                            scaleX = if (vertical) across else along
                            scaleY = if (vertical) along else across
                        }
                        .glassLens(GlassShapes.Pill, accent) {
                            (kotlin.math.abs(pos.velocity) / 4000f).coerceIn(0f, 1f) + if (dragging) 0.4f else 0f
                        }
                )
            }
        }
        val tabModifier = { i: Int ->
            Modifier.onPlaced { bounds[i] = it.positionInParent().round() to it.size }
        }
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
}

@Composable
private fun GlassNavTab(item: GlassNavItem, modifier: Modifier = Modifier) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val accent = LocalGlassAccent.current
    val tint by animateColorAsState(
        targetValue = if (item.selected) accent else onSurface.copy(alpha = 0.78f),
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
        CompositionLocalProvider(LocalContentColor provides tint) {
            Box(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }) { item.icon() }
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
