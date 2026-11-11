package com.example.juke.report

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.example.juke.BuildConfig
import com.example.juke.analytics.AnalyticsManager
import com.example.juke.jam.JamSession
import com.example.juke.network.ApiClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/** A user's bug report: what they typed plus app state and recent logs, sent to the Worker, which files the GitHub issue. */
object BugReport {
    private const val LOG_LINES = 400

    @Serializable
    private data class Payload(
        val installId: String,
        val description: String,
        val app: Map<String, String>,
        val jam: Map<String, String>?,
        val logs: String
    )

    @Serializable
    private data class Filed(val issue: Int)

    private val SECRETS = Regex("(authorization|bearer|token|secret|api[_-]?key|password|key)([\"'\\s:=]+)[A-Za-z0-9._~+/=-]{12,}", RegexOption.IGNORE_CASE)
    private val URL_PARAMS = Regex("([?&](?:access_token|token|key|secret|code)=)[^&\\s\"']+", RegexOption.IGNORE_CASE)
    private val EMAIL = Regex("[\\w.+-]+@[\\w-]+\\.[\\w.-]+")

    /** Removes tokens, key-like values in URLs and email addresses; the Worker does this again. */
    fun scrub(text: String): String =
        text.replace(SECRETS) { "${it.groupValues[1]}${it.groupValues[2]}[redacted]" }
            .replace(URL_PARAMS) { "${it.groupValues[1]}[redacted]" }
            .replace(EMAIL, "[email]")

    /** This app's own recent log lines; an app may read its own log without a permission. */
    suspend fun recentLogs(): String = withContext(Dispatchers.IO) {
        try {
            val proc = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "time", "-t", LOG_LINES.toString(), "--pid=${android.os.Process.myPid()}"))
            val out = proc.inputStream.bufferedReader().use { it.readText() }
            proc.destroy()
            scrub(out)
        } catch (e: Exception) {
            "(logs unavailable: ${e.javaClass.simpleName})"
        }
    }

    /** What goes along with the report apart from the logs; shown to the user before they send. */
    fun appInfo(context: Context, nowPlaying: String?, playing: Boolean): Map<String, String> {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        val network = when {
            caps == null -> "offline"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            else -> "other"
        }
        return buildMap {
            put("version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
            put("android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            put("network", network)
            put("now playing", nowPlaying ?: "nothing")
            put("playing", playing.toString())
        }
    }

    fun jamInfo(): Map<String, String>? {
        val st = JamSession.state.value
        if (!st.active) return null
        return mapOf(
            "status" to st.status.name,
            "members" to st.members.size.toString(),
            "queue" to st.queue.size.toString(),
            "host" to (st.hostId != null && st.hostId == st.you).toString(),
            "rev" to st.rev.toString()
        )
    }

    /** The GitHub issue number, or null if the report couldn't be sent (offline, too many reports today, ...). */
    suspend fun send(context: Context, description: String, app: Map<String, String>, includeLogs: Boolean): Int? = try {
        val logs = if (includeLogs) recentLogs() else ""
        val res: HttpResponse = ApiClient.httpClient.post("${BuildConfig.SHARE_BASE_URL.trimEnd('/')}/report") {
            header("X-App-Secret", BuildConfig.SPOTIFY_TOKEN_KEY)
            contentType(ContentType.Application.Json)
            setBody(Payload(AnalyticsManager.getInstance(context).installId(), scrub(description), app, jamInfo(), logs))
        }
        if (res.status.isSuccess()) res.body<Filed>().issue else null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
