package com.example.juke.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

class SheetAction(val label: String, val icon: ImageVector, val destructive: Boolean = false, val run: () -> Unit)

/** Song options as a bottom sheet: cover, title, artist, then one icon row per action. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TrackActionSheet(
    title: String,
    subtitle: String,
    artUrl: String?,
    actions: List<SheetAction>,
    onDismiss: () -> Unit,
    enabled: Boolean = true,
) {
    GlassModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 640.dp).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(6.dp)).then(Modifier)) {
                    if (artUrl != null) AsyncImage(artUrl, null, Modifier.size(52.dp), contentScale = ContentScale.Crop)
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            actions.forEachIndexed { i, a ->
                val color = if (a.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .then(if (i == 0) Modifier.initialFocus() else Modifier)
                    .focusRing()
                    .clickable(enabled = enabled, role = Role.Button) { a.run() }
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(a.icon, null, Modifier.size(24.dp), tint = color.copy(alpha = if (enabled) 1f else 0.38f))
                    Text(a.label, style = MaterialTheme.typography.bodyLarge, color = color.copy(alpha = if (enabled) 1f else 0.38f))
                }
            }
        }
    }
}
