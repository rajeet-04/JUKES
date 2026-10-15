package com.example.juke.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.juke.network.JukesApi
import kotlinx.coroutines.launch

private const val MUSIC = "https://music.youtube.com"
private const val SIGN_IN = "https://accounts.google.com/ServiceLogin?service=youtube&continue=$MUSIC"

// Google refuses sign-in in a UA that says "wv" (embedded WebView), so present as Chrome.
private const val CHROME_UA =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/124.0.0.0 Mobile Safari/537.36"

/** Signs in to YouTube Music in a WebView and hands the session cookie to the backend. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YoutubeSignInScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val sent = remember { booleanArrayOf(false) }
    Box(Modifier.fillMaxSize().statusBarsPadding()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = CHROME_UA
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                            if (sent[0] || !url.startsWith(MUSIC)) return
                            val cookie = CookieManager.getInstance().getCookie(MUSIC)
                            if (cookie?.contains("__Secure-3PAPISID") != true) return
                            sent[0] = true
                            scope.launch {
                                val ok = runCatching { JukesApi.connectYoutube(cookie) }.isSuccess
                                Toast.makeText(
                                    ctx,
                                    if (ok) "Personalised radio connected" else "Could not connect, try again",
                                    Toast.LENGTH_SHORT
                                ).show()
                                if (ok) onDone() else sent[0] = false
                            }
                        }
                    }
                    loadUrl(SIGN_IN)
                }
            }
        )
    }
}
