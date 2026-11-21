package com.example.juke.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.example.juke.ui.theme.GlassPrefs
import com.example.juke.ui.theme.GlassShapes
import com.example.juke.ui.theme.LocalHazeState
import com.example.juke.ui.theme.glassSheetColor
import com.example.juke.ui.theme.isGlassDark
import com.example.juke.ui.theme.liquidGlass
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Menus drawn inside the app's own window, so their glass can refract the real page behind them.
 * A Material sheet is a separate window, which Haze can't sample: liquid glass there drew a shifted
 * copy of the page over the menu. Callers compose [GlassMenuSheet] where they are; its content is
 * handed to the [GlassSheetLayer] at the top of the app, with the caller's composition locals.
 */
class GlassSheetHost {
    internal val entries = mutableStateListOf<SheetEntry>()
}

internal class SheetEntry(
    source: HazeState,
    locals: CompositionLocalContext,
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    var source by mutableStateOf(source)
    var locals by mutableStateOf(locals)
    var title by mutableStateOf(title)
    var onDismiss by mutableStateOf(onDismiss)
    var content by mutableStateOf(content)
    /** The caller let go of the menu: it slides away, then leaves the layer. */
    var leaving by mutableStateOf(false)
}

val LocalGlassSheetHost = staticCompositionLocalOf<GlassSheetHost?> { null }

/**
 * A menu sheet of liquid glass: a floating card that rises from the bottom over a dim scrim,
 * bending the page behind it at its rim. Tap outside, press back or drag it down to dismiss.
 * Without the in-window layer, or where liquid glass isn't drawn, it is the frosted Material sheet.
 * For menus only: a text field would need the keyboard handling the Material sheet has.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun GlassMenuSheet(
    onDismissRequest: () -> Unit,
    title: String = "Options",
    content: @Composable ColumnScope.() -> Unit,
) {
    val host = LocalGlassSheetHost.current
    val source = LocalHazeState.current
    if (host == null || source == null || !GlassPrefs.supported || GlassPrefs.solid) {
        GlassModalBottomSheet(onDismissRequest = onDismissRequest, content = content)
        return
    }
    val locals = currentCompositionLocalContext
    val entry = remember { SheetEntry(source, locals, title, onDismissRequest, content) }
    SideEffect {
        entry.source = source
        entry.locals = locals
        entry.title = title
        entry.onDismiss = onDismissRequest
        entry.content = content
    }
    DisposableEffect(host, entry) {
        host.entries += entry
        onDispose { entry.leaving = true }
    }
}

/** Draws the open menus. Sits above everything in the app, the player included. */
@Composable
fun GlassSheetLayer(host: GlassSheetHost) {
    host.entries.toList().forEach { entry ->
        key(entry) { MenuSheetFrame(entry, onGone = { host.entries.remove(entry) }) }
    }
}

@Composable
private fun MenuSheetFrame(entry: SheetEntry, onGone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    // How far the card sits below its resting place, in px: its full height when hidden.
    val offset = remember { Animatable(0f) }
    var height by remember { mutableIntStateOf(0) }
    var shown by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    val travel = { height.toFloat() + with(density) { 48.dp.toPx() } }
    val settleSpring = spring<Float>(dampingRatio = 0.86f, stiffness = 420f)

    // Rise once, when the card is first measured (an effect keyed on the height was cancelled as it settled).
    LaunchedEffect(Unit) {
        snapshotFlow { height }.first { it > 0 }
        offset.snapTo(travel())
        shown = true
        offset.animateTo(0f, settleSpring)
    }
    LaunchedEffect(entry.leaving) {
        if (!entry.leaving) return@LaunchedEffect
        if (shown) offset.animateTo(travel(), tween(200))
        onGone()
    }
    fun dismiss() {
        if (entry.leaving) return
        entry.onDismiss()
        // A caller may keep it open (e.g. while busy): then it springs back.
        scope.launch {
            withFrameNanos { }
            withFrameNanos { }
            if (!entry.leaving) offset.animateTo(0f, settleSpring)
        }
    }
    fun settle(velocity: Float) {
        dragging = false
        if (offset.value > height * 0.35f || velocity > with(density) { 1000.dp.toPx() }) dismiss()
        else scope.launch { offset.animateTo(0f, settleSpring, velocity) }
    }
    val drag = rememberDraggableState { dy -> scope.launch { offset.snapTo((offset.value + dy).coerceAtLeast(0f)) } }
    // Pulling down a list that is already at its top moves the card instead; pushing up takes it back first.
    val nested = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0 && offset.value > 0f) {
                    val used = maxOf(available.y, -offset.value)
                    scope.launch { offset.snapTo(offset.value + used) }
                    return Offset(0f, used)
                }
                return Offset.Zero
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0 && source == NestedScrollSource.UserInput) {
                    dragging = true
                    scope.launch { offset.snapTo(offset.value + available.y) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (offset.value > 0f) { settle(available.y); return available }
                return Velocity.Zero
            }
        }
    }
    // Back closes the newest menu first: its handler is registered last.
    BackHandler(enabled = !entry.leaving) { dismiss() }

    val visible = { if (!shown || height == 0) 0f else (1f - offset.value / travel()).coerceIn(0f, 1f) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxCard = maxHeight * 0.88f
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = visible() }
                .background(Color.Black.copy(alpha = 0.34f))
                .pointerInput(Unit) { detectTapGestures { dismiss() } }
        )
        CompositionLocalProvider(entry.locals) {
            val dark = isGlassDark()
            val fill = glassSheetColor().copy(alpha = if (dark) 0.5f else 0.6f)
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 10.dp)
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxCard)
                    .onSizeChanged { height = it.height }
                    .offset { IntOffset(0, offset.value.roundToInt()) }
                    .graphicsLayer { alpha = if (shown) 1f else 0f }
                    .semantics { paneTitle = entry.title }
                    .nestedScroll(nested)
                    .draggable(
                        drag, Orientation.Vertical,
                        onDragStarted = { dragging = true },
                        onDragStopped = { v -> settle(v) }
                    )
                    // Liquid glass over the real page: frosted body, bent rim, rainbow edge
                    // that swells while it is dragged.
                    .liquidGlass(
                        RoundedCornerShape(32.dp), fill, blur = 30.dp, shadow = 14.dp, rimAlpha = 0.6f,
                        source = entry.source, displacement = 56.dp, edge = 28.dp, clear = false,
                        strength = { if (dragging) 0.8f else 0.3f }
                    )
                    // Taps on the card are the card's, not the scrim's.
                    .pointerInput(Unit) { detectTapGestures { } }
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 10.dp, bottom = 6.dp)
                        .size(width = 40.dp, height = 5.dp)
                        .clip(GlassShapes.Pill)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f))
                )
                // Glass inside the menu falls back to tinted panes; it would sample the page through the card.
                CompositionLocalProvider(
                    LocalHazeState provides null,
                    LocalContentColor provides MaterialTheme.colorScheme.onSurface
                ) {
                    entry.content(this)
                }
            }
        }
    }
}
