package com.example.juke.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.juke.report.BugReport
import kotlinx.coroutines.launch

/** Home > lightbulb: an idea for the app, filed as a GitHub issue by the Worker. No logs and no playback state, only the app version and phone. */
@Composable
fun RequestFeatureDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    GlassAlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text("Request a feature") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(2000) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    placeholder = { Text("What would you like JUKE to do?") },
                    enabled = !sending
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Also sent: app version, phone model and Android version. " +
                        "The request is public on GitHub, so don't put anything private in it.",
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
                        val info = BugReport.appInfo(context, null, false).filterKeys { it in setOf("version", "device", "android") }
                        val issue = BugReport.send(context, text, info, includeLogs = false, kind = "feature")
                        sending = false
                        if (issue != null) {
                            Toast.makeText(context, "Thanks! Requested as #$issue", Toast.LENGTH_LONG).show()
                            onDismiss()
                        } else {
                            Toast.makeText(context, "Couldn't send it. Check your connection and try again.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            ) { Text(if (sending) "Sending…" else "Send") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !sending) { Text("Cancel") } }
    )
}
