package com.example.juke.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.juke.jam.Handoff
import com.example.juke.viewmodels.MusicViewModel
import kotlinx.coroutines.launch

/** Pair another of your devices, then move playback between them. Lives at the bottom of the Jam sheet. */
@Composable
fun HandoffSection(musicViewModel: MusicViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val code by Handoff.code.collectAsStateWithLifecycle()
    val status by Handoff.status.collectAsStateWithLifecycle()
    var entry by remember { mutableStateOf("") }
    var bad by remember { mutableStateOf(false) }

    HorizontalDivider()
    Text("Your devices", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    val c = code
    if (c == null) {
        Text(
            "Move what you're playing between your phone and computer. Pair them once with a code.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button({ Handoff.pairNew(context) }, Modifier.fillMaxWidth()) { Text("Pair: show a code") }
        OutlinedTextField(
            value = entry, onValueChange = { entry = it.take(6).uppercase(); bad = false },
            label = { Text("Or type the code from your other device") }, singleLine = true, isError = bad,
            supportingText = if (bad) {{ Text("That isn't a pairing code") }} else null,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedButton({ bad = !Handoff.pairWith(context, entry) }, Modifier.fillMaxWidth(), enabled = entry.length == 6) { Text("Pair with this code") }
    } else {
        Text(
            "Paired. Code $c: enter it on your other device if you haven't yet.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button({ scope.launch { Handoff.send(musicViewModel) } }, Modifier.weight(1f)) { Text("Play on other device") }
            OutlinedButton({ scope.launch { Handoff.pull(context) } }, Modifier.weight(1f)) { Text("Bring playback here") }
        }
        if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodySmall)
        TextButton({ Handoff.unpair(context) }) { Text("Unpair") }
    }
}
