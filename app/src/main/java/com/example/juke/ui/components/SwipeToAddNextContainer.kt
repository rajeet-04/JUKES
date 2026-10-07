package com.example.juke.ui.components

import android.annotation.SuppressLint
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.juke.ui.icons.JukeIcons
import com.example.juke.ui.theme.LocalGlassAccent
import com.example.juke.ui.theme.isGlassDark
import com.example.juke.utils.rememberJukeHaptics
import kotlin.math.abs

@Composable
fun SwipeToAddNextContainer(
    onAddNext: () -> Unit,
    onDelete: (() -> Unit)? = null,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val haptic = rememberJukeHaptics()

    // Guard flag: ensures the action fires only ONCE per swipe gesture.
    // confirmValueChange can be called multiple times during a single drag
    // (every time the item crosses the threshold), which caused rapid-fire
    // duplicate insertions. Resetting on Settled prevents cross-gesture leakage.
    val actionFired = remember { mutableStateOf(false) }

    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { distance -> distance * 0.65f },
        confirmValueChange = { value ->
            if (!enabled) return@rememberSwipeToDismissBoxState false
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    if (!actionFired.value) {
                        actionFired.value = true
                        haptic.confirm()
                        onAddNext()
                    }
                    false // Reset swipe position after action
                }

                SwipeToDismissBoxValue.EndToStart -> {
                    if (!actionFired.value) {
                        actionFired.value = true
                        haptic.reject()
                        onDelete?.invoke()
                    }
                    false // Reset swipe position after action
                }

                SwipeToDismissBoxValue.Settled -> {
                    // Swipe returned to neutral — allow the next swipe to fire
                    actionFired.value = false
                    false
                }
            }
        }
    )

    val shape = RoundedCornerShape(12.dp)
    val dark = isGlassDark()
    val accent = LocalGlassAccent.current

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = enabled,
        enableDismissFromEndToStart = enabled && onDelete != null,
        backgroundContent = {
            // No opaque fill: a tinted glow that lights up from behind the sliding glass card.
            val direction = dismissState.dismissDirection
            val glow = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primary
                SwipeToDismissBoxValue.EndToStart -> if (onDelete != null) MaterialTheme.colorScheme.error else null
                else -> null
            }
            if (glow != null) {
                val toEnd = direction == SwipeToDismissBoxValue.StartToEnd
                val strength = dismissState.progress.coerceIn(0f, 1f)
                val onGlow = if (dark) Color.White else MaterialTheme.colorScheme.onSurface
                // Only the strip the card has slid away from may show the label; anything under the
                // card would bleed through the glass and clash with the song title.
                val offsetPx = runCatching { dismissState.requireOffset() }.getOrDefault(0f)
                val revealed = with(LocalDensity.current) { abs(offsetPx).toDp() }
                Box(Modifier.fillMaxSize().clip(shape)) {
                    // Light source behind the glass: blurred so it reads as a glow, never as shapes.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .blur(18.dp)
                            .background(
                                Brush.horizontalGradient(
                                    if (toEnd) listOf(glow.copy(alpha = 0.55f * strength + 0.15f), glow.copy(alpha = 0.04f))
                                    else listOf(glow.copy(alpha = 0.04f), glow.copy(alpha = 0.55f * strength + 0.15f))
                                )
                            )
                    )
                    Box(
                        Modifier
                            .width(revealed)
                            .fillMaxHeight()
                            .align(if (toEnd) Alignment.CenterStart else Alignment.CenterEnd)
                            .clipToBounds(),
                        contentAlignment = if (toEnd) Alignment.CenterStart else Alignment.CenterEnd
                    ) {
                        Row(
                            modifier = Modifier
                                .wrapContentWidth(if (toEnd) Alignment.Start else Alignment.End, unbounded = true)
                                .padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (toEnd) {
                                Icon(JukeIcons.Play, contentDescription = "Add next", tint = onGlow)
                                Text("Add to queue next", style = MaterialTheme.typography.bodyLarge, color = onGlow, maxLines = 1, softWrap = false)
                            } else {
                                Text("Delete", style = MaterialTheme.typography.bodyLarge, color = onGlow, maxLines = 1, softWrap = false)
                                Icon(JukeIcons.Delete, contentDescription = "Delete", tint = onGlow)
                            }
                        }
                    }
                }
            }
        },
        content = {
            // The row turns into tinted glass as it is dragged, so the glow reads through it.
            val dragging = dismissState.dismissDirection != SwipeToDismissBoxValue.Settled
            val glass by animateFloatAsState(
                targetValue = if (dragging) 1f else 0f,
                label = "swipeGlass"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(
                        (if (dark) Color(0xFF14141A) else Color.White).copy(alpha = 0.55f * glass)
                    )
                    .background(accent.copy(alpha = (if (dark) 0.14f else 0.10f) * glass))
                    .border(0.6.dp, Color.White.copy(alpha = (if (dark) 0.22f else 0.5f) * glass), shape),
                content = content
            )
        },
        modifier = modifier
    )
}
