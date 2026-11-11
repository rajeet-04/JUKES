package com.example.juke.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.juke.ui.icons.JukeIcons
import kotlinx.coroutines.launch

/**
 * The Home screen's card rows. On desktop (mouse, no swipe) round arrows at the row's edges scroll it a page at a time,
 * and appear only in the direction there is more to see. On Android it is a plain LazyRow.
 */
@Composable
fun ArrowRow(modifier: Modifier = Modifier, content: LazyListScope.() -> Unit) {
    val state = rememberLazyListState()
    val padding = PaddingValues(horizontal = 24.dp)
    val spacing = Arrangement.spacedBy(12.dp)
    if (System.getProperty("java.vm.vendor") == "The Android Project") {
        LazyRow(modifier, state, contentPadding = padding, horizontalArrangement = spacing, content = content)
        return
    }
    val scope = rememberCoroutineScope()
    fun page(dir: Float) = scope.launch { state.animateScrollBy(dir * state.layoutInfo.viewportSize.width * 0.8f) }
    Box(modifier) {
        LazyRow(Modifier, state, contentPadding = padding, horizontalArrangement = spacing, content = content)
        @Composable fun Arrow(alignment: Alignment, rotation: Float, dir: Float) {
            Box(Modifier.align(alignment).padding(horizontal = 6.dp).size(38.dp).clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.72f)).clickable { page(dir) }, contentAlignment = Alignment.Center) {
                Icon(JukeIcons.ChevronDown, if (dir < 0) "Scroll left" else "Scroll right", Modifier.size(22.dp).rotate(rotation), tint = MaterialTheme.colorScheme.onSurface)
            }
        }
        if (state.canScrollBackward) Arrow(Alignment.CenterStart, 90f, -1f)
        if (state.canScrollForward) Arrow(Alignment.CenterEnd, -90f, 1f)
    }
}
