package com.example.juke.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.juke.ui.theme.GlassCard

private const val DOWNLOAD_URL = "https://github.com/rajeet-04/JUKES/releases"

/** Settings card announcing the desktop app. Shown on Android only (the desktop app is the thing it advertises). */
@Composable
fun DesktopAppSection() {
    if (System.getProperty("java.vm.vendor") != "The Android Project") return
    val uriHandler = LocalUriHandler.current
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("JUKE for desktop", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "JUKE now has an app for macOS and Windows. Log in with the same account to get your library there and control playback between your devices.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button({ runCatching { uriHandler.openUri(DOWNLOAD_URL) } }, Modifier.fillMaxWidth()) { Text("Download the desktop app") }
        }
    }
}
