package com.example.juke.ui.components

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.juke.services.DownloadInfo
import com.example.juke.viewmodels.DownloadItem
import com.example.juke.viewmodels.DownloadStatus

/**
 * Compact download queue banner — shows a slim progress bar + summary line.
 * Tapping expands to show individual track rows.
 */
@Composable
fun CompactDownloadBanner(
    currentDownload: DownloadItem?,
    downloadQueue: List<DownloadItem>,
    recommendationDownloads: List<DownloadInfo>,
    onCancelDownload: (String) -> Unit,
    onRetryDownload: (DownloadItem) -> Unit,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    val totalActive =
        (if (currentDownload != null) 1 else 0) + downloadQueue.size + recommendationDownloads.size
    if (totalActive == 0) return

    var expanded by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .clip(RoundedCornerShape(12.dp))
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        tonalElevation = 2.dp
    ) {
        Column {
            // ── Header row ──────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pulsing dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = alpha)
                        )
                )
                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    val activeTitle = currentDownload?.song?.title
                        ?: recommendationDownloads.firstOrNull()?.title
                        ?: "Downloading…"
                    Text(
                        text = activeTitle,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val queuedCount = downloadQueue.size + recommendationDownloads.size -
                            (if (currentDownload == null && recommendationDownloads.isNotEmpty()) 1 else 0)
                    val label = when {
                        queuedCount > 0 -> "+$queuedCount more in queue"
                        else -> if (currentDownload != null) "Downloading…" else "Importing from playlist…"
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Thin indeterminate progress bar
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            )

            // ── Expanded detail list ─────────────────────────────────────
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(bottom = 6.dp)) {
                    // Current download
                    currentDownload?.let {
                        CompactDownloadRow(
                            title = it.song.title,
                            artist = it.song.artist,
                            thumbnail = it.song.thumbnail,
                            status = it.status,
                            statusLabel = when (it.status) {
                                DownloadStatus.DOWNLOADING -> "Downloading…"
                                DownloadStatus.QUEUED -> "Queued"
                                DownloadStatus.FAILED -> it.error ?: "Failed"
                                DownloadStatus.COMPLETED -> "Done"
                            },
                            onAction = if (it.status == DownloadStatus.FAILED) {
                                { onRetryDownload(it) }
                            } else null,
                            actionIcon = if (it.status == DownloadStatus.FAILED) Icons.Default.Refresh else null
                        )
                    }

                    // Queued manual downloads
                    downloadQueue.forEach { item ->
                        CompactDownloadRow(
                            title = item.song.title,
                            artist = item.song.artist,
                            thumbnail = item.song.thumbnail,
                            status = item.status,
                            statusLabel = "Queued",
                            onAction = { onCancelDownload(item.id) },
                            actionIcon = Icons.Default.Close
                        )
                    }

                    // Playlist / recommendation downloads
                    recommendationDownloads.forEach { info ->
                        CompactDownloadRow(
                            title = info.title,
                            artist = info.artist,
                            thumbnail = null,
                            status = DownloadStatus.DOWNLOADING,
                            statusLabel = when (info.source) {
                                "playlist" -> "Importing…"
                                "recommendation" -> "Radio…"
                                else -> "Downloading…"
                            },
                            onAction = null,
                            actionIcon = null
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactDownloadRow(
    title: String,
    artist: String,
    thumbnail: String?,
    status: DownloadStatus,
    statusLabel: String,
    onAction: (() -> Unit)?,
    actionIcon: androidx.compose.ui.graphics.vector.ImageVector?,
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Tiny thumbnail / spinner
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (thumbnail != null) {
                AsyncImage(
                    model = thumbnail,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.45f
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }
            if (status == DownloadStatus.DOWNLOADING || status == DownloadStatus.QUEUED) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 1.5.dp,
                    color = if (status == DownloadStatus.DOWNLOADING)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
            )
            Text(
                text = "$artist · $statusLabel",
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = when (status) {
                    DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
                    DownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                }
            )
        }

        if (onAction != null && actionIcon != null) {
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onAction()
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = actionIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ── Legacy single-item composable kept for any other call sites ──────────────
@Composable
fun DownloadingTrackItem(
    downloadItem: DownloadItem,
    onCancel: () -> Unit = {},
    onRetry: () -> Unit = {},
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    LocalHapticFeedback.current
    CompactDownloadRow(
        title = downloadItem.song.title,
        artist = downloadItem.song.artist,
        thumbnail = downloadItem.song.thumbnail,
        status = downloadItem.status,
        statusLabel = when (downloadItem.status) {
            DownloadStatus.DOWNLOADING -> "Downloading…"
            DownloadStatus.QUEUED -> "Queued"
            DownloadStatus.FAILED -> downloadItem.error ?: "Failed"
            DownloadStatus.COMPLETED -> "Done"
        },
        onAction = when (downloadItem.status) {
            DownloadStatus.FAILED -> onRetry
            DownloadStatus.QUEUED -> onCancel
            else -> null
        },
        actionIcon = when (downloadItem.status) {
            DownloadStatus.FAILED -> Icons.Default.Refresh
            DownloadStatus.QUEUED -> Icons.Default.Close
            else -> null
        }
    )
}
