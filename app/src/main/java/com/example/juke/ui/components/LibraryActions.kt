package com.example.juke.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.juke.ui.theme.GlassShapes
import com.example.juke.ui.theme.isGlassDark
import com.example.juke.ui.theme.liquidGlass

/** One material layer, with labeled actions that wrap at large font sizes. */
@Composable
fun LibrarySelectionActions(
    enabled: Boolean,
    onPlayNext: () -> Unit,
    onPlaylist: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = isGlassDark()
    Box(modifier) {
        // Refract the backdrop only, so action text stays crisp like the app's tab bar.
        Box(Modifier.matchParentSize().liquidGlass(
            shape = GlassShapes.Bar,
            fill = if (dark) Color(0xFF0E0E12).copy(alpha = 0.32f) else Color.White.copy(alpha = 0.38f),
            blur = 10.dp, shadow = 6.dp,
        ))
        FlowRow(
            modifier = Modifier.padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        ) {
            LabelledLibraryAction("Play next", Icons.Default.PlayArrow, enabled, onPlayNext, emphasized = true)
            LabelledLibraryAction("Playlist", Icons.AutoMirrored.Filled.PlaylistAdd, enabled, onPlaylist)
            LabelledLibraryAction("More", Icons.Default.MoreHoriz, enabled, onMore)
        }
    }
}

@Composable
private fun LabelledLibraryAction(label: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit, emphasized: Boolean = false) {
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.heightIn(min = 48.dp),
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )) {
        Icon(icon, null, Modifier.size(20.dp))
        Text(label, Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelLarge)
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LibraryTrackActionsSheet(
    title: String,
    subtitle: String,
    playlistName: String?,
    enabled: Boolean,
    onPlayNext: () -> Unit,
    onQueueEnd: () -> Unit,
    onPlaylist: () -> Unit,
    onRemoveFromPlaylist: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    GlassModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 640.dp).verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            LibrarySheetHeader(title, subtitle, onDismiss, enabled = enabled)
            LibraryActionRow("Play next", "Immediately after the current song", Icons.Default.PlayArrow, enabled, onPlayNext)
            LibraryActionRow("Add to queue end", "After the songs already queued", Icons.AutoMirrored.Filled.QueueMusic, enabled, onQueueEnd)
            LibraryActionRow("Add to playlist", "Choose an existing playlist or create one", Icons.AutoMirrored.Filled.PlaylistAdd, enabled, onPlaylist)
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
            if (playlistName != null) {
                LibraryActionRow("Remove from this playlist", "From $playlistName; keep downloads and library songs", Icons.Default.RemoveCircleOutline, enabled, onRemoveFromPlaylist)
            }
            LibraryActionRow("Delete from library", "Remove downloads and all playlist entries", Icons.Default.DeleteOutline, enabled, onDelete, destructive = true)
        }
    }
}

@Composable
fun LibrarySheetHeader(title: String, subtitle: String, onDismiss: () -> Unit, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        GlassIconButton(onClick = onDismiss, contentDescription = "Close", enabled = enabled) {
            Icon(Icons.Default.Close, null)
        }
    }
}

@Composable
private fun LibraryActionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(24.dp), tint = color)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = color.copy(alpha = if (enabled) 1f else 0.38f))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
