package com.example.juke.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.juke.ui.theme.GlassShapes
import com.example.juke.ui.theme.LocalGlassAccent
import com.example.juke.ui.theme.isGlassDark
import com.example.juke.ui.theme.liquidGlass

/**
 * Whether the bottom chrome is merged into one row: scrolling down a list folds the tab bar into a
 * single round button beside the mini player, scrolling back up unfolds it. Driven by the real
 * scroll distance with hysteresis, so a small or reversing scroll doesn't flicker it.
 */
class CollapsingChrome(private val thresholdPx: Float) {
    var collapsed by mutableStateOf(false)
    private var travel = 0f

    val connection = object : NestedScrollConnection {
        // This observer updates chrome state without consuming any additional scroll distance.
        @Suppress("SameReturnValue")
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            val dy = consumed.y // what actually scrolled: nothing at a list's ends
            if (dy == 0f) return Offset.Zero
            if ((dy < 0f) != (travel < 0f)) travel = 0f // direction changed: start counting again
            travel += dy
            if (travel <= -thresholdPx) collapsed = true // content moving up = reading further down
            else if (travel >= thresholdPx) collapsed = false
            return Offset.Zero
        }
    }

    fun expand() {
        collapsed = false
        travel = 0f
    }
}

/** The tab bar folded into one glass button showing the current tab; a tap unfolds it. */
@Composable
fun CollapsedTabButton(label: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    val dark = isGlassDark()
    Box(
        modifier = Modifier
            .size(56.dp)
            .liquidGlass(
                shape = GlassShapes.Pill,
                fill = if (dark) Color(0xFF0E0E12).copy(alpha = 0.32f) else Color.White.copy(alpha = 0.38f),
                blur = 10.dp,
                shadow = 6.dp,
            )
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "$label. Show all tabs" },
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides LocalGlassAccent.current) { icon() }
    }
}
