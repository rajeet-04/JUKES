package com.example.juke.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.juke.ui.theme.GlassLevel
import com.example.juke.ui.theme.GlassShapes
import com.example.juke.ui.theme.GlassWindowBlur
import com.example.juke.ui.theme.LocalGlassAccent
import com.example.juke.ui.theme.glassFloat
import com.example.juke.ui.theme.glassPane
import com.example.juke.ui.theme.glassSheetColor

/** Round glass control. 48dp hit target by default; pass `floating` over scrolling artwork. */
@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 48.dp,
    level: GlassLevel = GlassLevel.Regular,
    floating: Boolean = false,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val glass = if (floating) Modifier.glassFloat(CircleShape, level) else Modifier.glassPane(CircleShape, level)
    Box(
        modifier = modifier
            .size(size)
            .then(glass)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f),
            content = content
        )
    }
}

/**
 * Primary action. A lit accent lens: the album color poured into glass, with a contrast-safe label.
 * One per screen at most.
 */
@Composable
fun GlassPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
) {
    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .glassPane(GlassShapes.Pill, GlassLevel.Regular, accent)
            .background(accent.copy(alpha = if (enabled) 0.88f else 0.28f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        CompositionLocalProvider(LocalContentColor provides onAccent) {
            leading?.invoke()
            Text(text, style = MaterialTheme.typography.labelLarge, color = onAccent)
        }
    }
}

/**
 * Material alert dialog drawn as thick glass. On Android 12+ the app behind the dialog is
 * blurred by the window manager, so the dialog reads as a lens rather than a card on a dimmer.
 */
@Composable
fun GlassAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = GlassShapes.Card,
    properties: DialogProperties = DialogProperties(),
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            GlassWindowBlur()
            confirmButton()
        },
        modifier = modifier,
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = shape,
        containerColor = glassSheetColor(),
        tonalElevation = 0.dp,
        iconContentColor = MaterialTheme.colorScheme.primary,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        properties = properties
    )
}

/** Modal bottom sheet as thick glass with a lit handle. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    showHandle: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = GlassShapes.Sheet,
        containerColor = glassSheetColor(),
        tonalElevation = 0.dp,
        scrimColor = Color.Transparent,
        dragHandle = if (!showHandle) null else {
            {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .widthIn(min = 40.dp)
                    .size(width = 40.dp, height = 5.dp)
                    .clip(GlassShapes.Pill)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f))
            )
            }
        }
    ) {
        GlassWindowBlur()
        content()
    }
}

/** Slot-based drop-in for Material FilterChip/AssistChip, drawn as a glass pill. */
@Composable
fun GlassFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier
            .heightIn(min = 40.dp)
            .glassPane(
                GlassShapes.Pill,
                if (selected) GlassLevel.Thick else GlassLevel.Thin,
                if (selected) LocalGlassAccent.current else null
            )
            .selectable(selected = selected, enabled = enabled, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        CompositionLocalProvider(
            LocalContentColor provides onSurface.copy(alpha = if (!enabled) 0.38f else if (selected) 1f else 0.8f)
        ) {
            leadingIcon?.invoke()
            androidx.compose.material3.ProvideTextStyle(MaterialTheme.typography.labelLarge, label)
            trailingIcon?.invoke()
        }
    }
}

/** Drop-in for Material TopAppBar: a floating glass pill carrying the same title/nav/actions slots. */
@Composable
fun GlassTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
            .glassFloat(GlassShapes.Pill, GlassLevel.Regular)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            navigationIcon()
            Box(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                androidx.compose.material3.ProvideTextStyle(MaterialTheme.typography.titleMedium, title)
            }
            actions()
        }
    }
}

/** Drop-in for Material Button/FilledTonalButton/OutlinedButton: a glass lens; `primary` pours the accent in. */
@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val fg = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .glassPane(GlassShapes.Pill, if (primary) GlassLevel.Thick else GlassLevel.Regular, if (primary) LocalGlassAccent.current else null)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides fg.copy(alpha = if (enabled) 1f else 0.38f)) {
            androidx.compose.material3.ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                content()
            }
        }
    }
}
