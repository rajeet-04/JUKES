package com.example.juke.ui.components

import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

/** True while the last input was a key press, false after a mouse press. Only the desktop window flips it. */
object KeyboardNav { var active by mutableStateOf(false) }

/** An outline on whatever holds keyboard focus. Only drawn while navigating by keyboard, so clicking a row leaves no ring. */
fun Modifier.focusRing(shape: Shape = RoundedCornerShape(10.dp)): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val ring = MaterialTheme.colorScheme.primary
    onFocusChanged { focused = it.hasFocus }
        .then(if (focused && KeyboardNav.active) Modifier.border(2.dp, ring, shape) else Modifier)
}

/** Alt+Enter, the Menu key or Shift+F10: the keyboard's right click. Runs before the row's own Enter-to-play. */
fun Modifier.onMenuKey(onOpen: () -> Unit): Modifier = onPreviewKeyEvent { e ->
    val menu = e.type == KeyEventType.KeyDown &&
        (e.key == Key.Menu || (e.key == Key.F10 && e.isShiftPressed) || ((e.key == Key.Enter || e.key == Key.NumPadEnter) && e.isAltPressed))
    if (menu) onOpen()
    menu
}

private val ARROWS = mapOf(Key.DirectionUp to FocusDirection.Up, Key.DirectionDown to FocusDirection.Down, Key.DirectionLeft to FocusDirection.Left, Key.DirectionRight to FocusDirection.Right)

/**
 * Compose desktop moves focus on Tab only. This lets the arrow keys walk it to the neighbouring control or row.
 * Goes on a window root and on each popup's content (a popup has its own focus scope the window's handler never reaches).
 * Whatever has focus gets the key first, so text fields and sliders keep their arrows; [skip] steps aside as well.
 */
fun Modifier.arrowKeyFocus(skip: () -> Boolean = { false }): Modifier = composed {
    val focus = LocalFocusManager.current
    onKeyEvent { e ->
        val dir = ARROWS[e.key]
        if (dir == null || e.type != KeyEventType.KeyDown || e.isAltPressed || e.isShiftPressed || e.isCtrlPressed || e.isMetaPressed || skip()) false
        else focus.moveFocus(dir)
    }
}

/** Takes keyboard focus once its row is on screen (a sheet is still animating in at first, so keep trying briefly). */
fun Modifier.initialFocus(): Modifier = composed {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { repeat(20) { if (runCatching { requester.requestFocus() }.isSuccess) return@LaunchedEffect; kotlinx.coroutines.delay(50) } }
    focusRequester(requester)
}
