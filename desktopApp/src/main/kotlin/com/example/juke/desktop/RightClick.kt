package com.example.juke.desktop

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable as foundationCombinedClickable
import androidx.compose.foundation.Indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role

/**
 * Drop-in for foundation's `combinedClickable` (the desktop build swaps the import in the shared
 * sources, see `stageAndroid`): a right click runs [onLongClick], which is the song menu on a phone.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.combinedClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onLongClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = rightClickToLongClick(enabled, onLongClick)
    .foundationCombinedClickable(
        enabled = enabled, onClickLabel = onClickLabel, role = role, onLongClickLabel = onLongClickLabel,
        onLongClick = onLongClick, onDoubleClick = onDoubleClick, onClick = onClick
    )

/** The same, for rows that bring their own [interactionSource] and [indication]. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.combinedClickable(
    interactionSource: MutableInteractionSource?,
    indication: Indication?,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onLongClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = rightClickToLongClick(enabled, onLongClick)
    .foundationCombinedClickable(
        interactionSource = interactionSource, indication = indication,
        enabled = enabled, onClickLabel = onClickLabel, role = role, onLongClickLabel = onLongClickLabel,
        onLongClick = onLongClick, onDoubleClick = onDoubleClick, onClick = onClick
    )

private fun Modifier.rightClickToLongClick(enabled: Boolean, onLongClick: (() -> Unit)?): Modifier =
    if (enabled && onLongClick != null) pointerInput(onLongClick) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                    event.changes.forEach { it.consume() }
                    onLongClick()
                }
            }
        }
    } else this
