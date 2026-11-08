package com.example.juke.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.juke.network.JukesApi
import kotlinx.coroutines.launch

/**
 * Desktop has no embedded browser, so the sign-in is: log in to YouTube Music in your own browser,
 * copy the request Cookie header (DevTools -> Network), paste it here. Same backend call as mobile.
 */
@Composable
fun YoutubeSignInScreen(onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var cookie by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp).widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Connect YouTube Music", style = MaterialTheme.typography.headlineSmall)
        Text("1. Sign in at music.youtube.com in your browser.\n2. Open DevTools → Network, click any request to music.youtube.com and copy its Cookie header.\n3. Paste it below. It is sent to your JUKES backend, never stored in the app.", style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = { runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI("https://music.youtube.com")) } }) { Text("Open YouTube Music") }
        OutlinedTextField(cookie, { cookie = it }, Modifier.fillMaxWidth(), label = { Text("Cookie header") }, minLines = 3, maxLines = 6)
        Button(enabled = !busy && cookie.contains("__Secure-3PAPISID"), onClick = {
            busy = true
            scope.launch {
                val ok = runCatching { JukesApi.connectYoutube(cookie.trim()) }.isSuccess
                Toast.makeText(null, if (ok) "Personalised radio connected" else "Could not connect, try again", 0).show()
                busy = false; if (ok) onDone()
            }
        }) { Text(if (busy) "Connecting…" else "Connect") }
        TextButton(onClick = onDone) { Text("Cancel") }
    }
}
