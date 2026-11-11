package com.example.juke.ui.components

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.juke.BuildConfig
import com.example.juke.models.GithubRelease
import com.example.juke.services.UpdateDownloadState
import com.example.juke.services.UpdateManager
import com.example.juke.ui.theme.GlassLevel
import com.example.juke.ui.theme.glassPane
import com.example.juke.utils.rememberJukeHaptics
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * One sheet for the whole update flow: available -> downloading (live progress) -> ready to install.
 * Critical releases (tag contains "hotfix"/"emergency") can't be swiped away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateSheet(
    release: GithubRelease?,
    state: UpdateDownloadState,
    onStart: (GithubRelease) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptic = rememberJukeHaptics()
    val critical = release?.isCritical == true
    val ready = (state as? UpdateDownloadState.Ready)?.update
    val downloading = state as? UpdateDownloadState.Downloading
    val tag = release?.tagName ?: ready?.releaseTag.orEmpty()

    LaunchedEffect(downloading != null) {
        while (downloading != null) {
            UpdateManager.pollProgress(context)
            delay(500.milliseconds)
        }
    }

    GlassModalBottomSheet(
        onDismissRequest = { if (!critical) onDismiss() },
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { !critical }
        )
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Header(critical = critical, ready = ready != null, to = tag)

            release?.let { Meta(it) }

            val notes = remember(release?.body) { releaseNotes(release?.body) }
            if (ready == null && downloading == null && notes.isNotEmpty()) {
                if (!notes.first().first) {
                    Text("What's new", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                }
                Column(
                    Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    notes.forEach { (heading, line) ->
                        Text(
                            line,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (heading) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (heading) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (downloading != null) DownloadProgress(downloading)

            if (critical && ready == null && downloading == null) {
                Text(
                    "This update fixes critical issues and is required to keep using JUKE.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            GlassPillButton(
                text = when {
                    ready != null -> "Install now"
                    downloading != null -> "Downloading…"
                    else -> "Update to $tag"
                },
                enabled = downloading == null && (ready != null || release != null),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    haptic.heavyClick()
                    if (ready != null) {
                        if (UpdateManager.installDownloadedUpdate(context, ready)) onDismiss()
                    } else if (release != null) onStart(release)
                }
            )

            if (ready != null) {
                TextButton(
                    onClick = { if (UpdateManager.openDownloadsFolder(context)) onDismiss() },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) { Text("Open Downloads folder") }
            } else if (!critical) {
                TextButton(
                    onClick = { haptic.click(); onDismiss() },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) { Text(if (downloading != null) "Hide" else "Not now") }
            }
        }
    }
}

@Composable
private fun Header(critical: Boolean, ready: Boolean, to: String) {
    val tint = if (critical) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(48.dp).glassPane(CircleShape, GlassLevel.Thick), contentAlignment = Alignment.Center) {
            Icon(if (critical) Icons.Filled.Warning else Icons.Filled.SystemUpdate, null, tint = tint)
        }
        Column {
            Text(
                when {
                    ready -> "Ready to install"
                    critical -> "Critical update"
                    else -> "Update available"
                },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "${BuildConfig.VERSION_NAME}  →  $to",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Meta(release: GithubRelease) {
    val context = LocalContext.current
    val parts = buildList {
        release.assets.firstOrNull { it.name.endsWith(".apk", true) }?.size?.takeIf { it > 0 }
            ?.let { add(Formatter.formatShortFileSize(context, it)) }
        release.publishedAt?.take(10)?.let { d ->
            runCatching { add(LocalDate.parse(d).format(DateTimeFormatter.ofPattern("MMM d, yyyy"))) }
        }
        if (release.isPrerelease) add("Pre-release")
    }
    if (parts.isNotEmpty()) {
        Text(
            parts.joinToString("  ·  "),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DownloadProgress(d: UpdateDownloadState.Downloading) {
    val context = LocalContext.current
    val fraction = if (d.total > 0) (d.bytes.toFloat() / d.total).coerceIn(0f, 1f) else null
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (fraction != null) LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text(
            if (fraction != null) {
                "${(fraction * 100).toInt()}%  ·  ${Formatter.formatShortFileSize(context, d.bytes)} of ${Formatter.formatShortFileSize(context, d.total)}"
            } else "Starting download…",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Spacer(Modifier.height(2.dp))
}

private val linkRegex = Regex("\\[([^\\]]+)]\\([^)]*\\)")

/** GitHub markdown -> plain (isHeading, text) lines: no hashes, asterisks, links or changelog footer. */
private fun releaseNotes(body: String?): List<Pair<Boolean, String>> =
    body.orEmpty().lines().map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("```") && !it.contains("Full Changelog", true) }
        .map { line ->
            val heading = line.startsWith("#")
            val text = line.trimStart('#', ' ')
                .replace(linkRegex, "$1")
                .replace(Regex("^[-*+]\\s+"), "• ")
                .replace("**", "").replace("__", "").replace("`", "")
            heading to text
        }
        .take(30)
