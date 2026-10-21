package com.example.juke.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.juke.report.BugReport
import com.example.juke.viewmodels.MusicViewModel
import kotlinx.coroutines.launch

/** Settings > Report a bug: a description plus app info and recent logs, filed as a GitHub issue by the Worker. */
@Composable
fun ReportBugDialog(musicViewModel: MusicViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var withLogs by remember { mutableStateOf(true) }
    var sending by remember { mutableStateOf(false) }

    GlassAlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text("Report a bug") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(2000) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    placeholder = { Text("What went wrong? What were you doing?") },
                    enabled = !sending
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Include recent app logs", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Helps find the cause. Tokens and emails are removed, and logs are kept private.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = withLogs, onCheckedChange = { withLogs = it }, enabled = !sending)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Also sent: app version, phone model, Android version, network type, the song playing and Jam status. " +
                        "The report is public on GitHub, so don't put anything private in the description.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank() && !sending,
                onClick = {
                    sending = true
                    scope.launch {
                        val track = musicViewModel.uiState.value.currentTrack
                        val info = BugReport.appInfo(
                            context,
                            track?.let { "${it.title} - ${it.artist}" },
                            musicViewModel.playbackManager.isPlayingFlow.value
                        )
                        val issue = BugReport.send(context, text, info, withLogs)
                        sending = false
                        if (issue != null) {
                            Toast.makeText(context, "Thanks! Reported as #$issue", Toast.LENGTH_LONG).show()
                            onDismiss()
                        } else {
                            Toast.makeText(context, "Couldn't send the report. Check your connection and try again.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            ) { Text(if (sending) "Sending…" else "Send") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !sending) { Text("Cancel") } }
    )
}
