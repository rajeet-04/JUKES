package com.example.juke.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.juke.network.JukesApi
import kotlinx.coroutines.launch

/** The Cookie header from whatever was pasted: the bare header value, a "cookie: ..." line, or a copied cURL command (-b / -H 'cookie: ...'). */
internal fun extractCookie(raw: String): String {
    val t = raw.trim()
    Regex("""(?i)(?:-H|--header)\s+\$?['"]cookie:\s*([^'"]+)['"]""").find(t)?.let { return it.groupValues[1].trim() }
    Regex("""(?:-b|--cookie)\s+\$?['"]([^'"]+)['"]""").find(t)?.let { return it.groupValues[1].trim() }
    Regex("""(?im)^\s*cookie:\s*(.+)$""").find(t)?.let { return it.groupValues[1].trim() }
    return t
}

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
        Text("1. Sign in at music.youtube.com in your browser.\n2. Open DevTools (F12, or Cmd+Option+I on Mac) → Network. Reload the page, type \"browse\" in the filter box and right-click the first request → Copy → Copy as cURL.\n3. Paste it below (the cURL text is fine, JUKE finds the cookie in it). It is sent to your JUKES backend, never stored in the app.", style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = { runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI("https://music.youtube.com")) } }) { Text("Open YouTube Music") }
        OutlinedTextField(cookie, { cookie = it }, Modifier.fillMaxWidth(), label = { Text("Cookie header or copied cURL") }, minLines = 3, maxLines = 6,
            supportingText = { if (cookie.isNotBlank() && !extractCookie(cookie).contains("__Secure-3PAPISID")) Text("No YouTube Music login found in this text. Make sure you are signed in, and copy a request to music.youtube.com.") })
        Button(enabled = !busy && extractCookie(cookie).contains("__Secure-3PAPISID"), onClick = {
            busy = true
            scope.launch {
                val ok = runCatching { JukesApi.connectYoutube(extractCookie(cookie)) }.isSuccess
                Toast.makeText(null, if (ok) "Personalised radio connected" else "Could not connect, try again", 0).show()
                busy = false; if (ok) onDone()
            }
        }) { Text(if (busy) "Connecting…" else "Connect") }
        TextButton(onClick = onDone) { Text("Cancel") }
    }
}
