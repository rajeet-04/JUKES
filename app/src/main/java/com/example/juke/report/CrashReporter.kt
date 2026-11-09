package com.example.juke.report

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.juke.BuildConfig
import com.example.juke.analytics.AnalyticsManager
import com.example.juke.network.ApiClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Writes every crash to disk as the process dies and sends it to the Worker on the next launch, which files one
 * GitHub issue per distinct crash. PostHog's own handler (installed before this one) still runs afterwards.
 */
object CrashReporter {
    private const val TAG = "CrashReporter"
    private const val KEEP = 5
    private const val MAX_TRACE = 16 * 1024
    private const val MAX_AGE_MS = 7 * 86_400_000L

    @Serializable
    data class Crash(
        val installId: String,
        val type: String,
        val message: String,
        val trace: String,
        val thread: String,
        val at: Long,
        val app: Map<String, String>
    )

    private val json = Json { ignoreUnknownKeys = true }

    private fun dir(context: Context) = File(context.filesDir, "crashes")

    fun install(context: Context) {
        val app = context.applicationContext
        runCatching { // foreground/background trail (Android only: the desktop shim has no process lifecycle events)
            androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
                override fun onStart(owner: androidx.lifecycle.LifecycleOwner) = Breadcrumbs.add("app foreground")
                override fun onStop(owner: androidx.lifecycle.LifecycleOwner) = Breadcrumbs.add("app background")
            })
        }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                save(app, thread, error)
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun save(context: Context, thread: Thread, error: Throwable) {
        val root = generateSequence(error) { it.cause }.last()
        saveReport(context, root.javaClass.name, root.message.orEmpty(), error.stackTraceToString(), thread.name, System.currentTimeMillis())
    }

    /** Queues one crash for the next send. Also used for crashes that killed the VM itself (desktop native crashes). */
    fun saveReport(context: Context, type: String, message: String, trace: String, thread: String, at: Long, extra: Map<String, String> = emptyMap()) {
        val crash = Crash(
            installId = AnalyticsManager.getIfInitialized()?.installId() ?: "",
            type = type,
            message = BugReport.scrub(message).take(500),
            trace = BugReport.scrub(trace).take(MAX_TRACE),
            thread = thread,
            at = at,
            app = mapOf(
                "version" to "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                "device" to "${Build.MANUFACTURER} ${Build.MODEL}",
                "android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                "runtime" to "${System.getProperty("java.vm.name")} ${System.getProperty("java.version")} on ${System.getProperty("os.name")} ${System.getProperty("os.version")} ${System.getProperty("os.arch")}"
            ) + Breadcrumbs.context() + extra
        )
        val dir = dir(context).apply { mkdirs() }
        File(dir, "${crash.at}.json").writeText(json.encodeToString(crash))
        dir.listFiles()?.sortedByDescending { it.name }?.drop(KEEP)?.forEach { it.delete() }
    }

    /** Sends crashes saved by earlier runs; kept for the next launch if the Worker can't be reached. */
    suspend fun sendPending(context: Context) = withContext(Dispatchers.IO) {
        val files = dir(context).listFiles()?.sortedBy { it.name } ?: return@withContext
        for (file in files) {
            try {
                val crash = json.decodeFromString<Crash>(file.readText())
                if (System.currentTimeMillis() - crash.at > MAX_AGE_MS) { file.delete(); continue }
                val withId = if (crash.installId.isEmpty()) crash.copy(installId = AnalyticsManager.getInstance(context).installId()) else crash
                val res = ApiClient.httpClient.post("${BuildConfig.SHARE_BASE_URL.trimEnd('/')}/crash") {
                    header("X-App-Secret", BuildConfig.SPOTIFY_TOKEN_KEY)
                    contentType(ContentType.Application.Json)
                    setBody(withId)
                }
                // 4xx other than rate limiting will never succeed, so don't retry it forever
                if (res.status.isSuccess() || (res.status.value in 400..499 && res.status.value != 429)) file.delete()
            } catch (e: kotlinx.serialization.SerializationException) {
                file.delete()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "Crash report not sent yet: ${e.message}")
                return@withContext
            }
        }
    }
}
