package com.example.juke.services

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.example.juke.BuildConfig
import com.example.juke.models.GithubRelease
import com.example.juke.network.ApiClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import okhttp3.Request
import kotlin.time.Duration.Companion.seconds

data class DownloadedUpdate(val releaseTag: String, val fileName: String, val absolutePath: String)

sealed interface UpdateDownloadState {
    object Idle : UpdateDownloadState
    data class Downloading(val releaseTag: String, val fileName: String, val downloadId: Long, val bytes: Long = 0, val total: Long = 0) : UpdateDownloadState
    data class Ready(val update: DownloadedUpdate) : UpdateDownloadState
    data class Error(val message: String) : UpdateDownloadState
}

/** Desktop update flow: find the newest release with an installer for this OS, download it, open it. */
object UpdateManager {
    private const val GITHUB_API_URL = "https://api.github.com/repos/rajeet-04/JUKES/releases"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _downloadState = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val downloadState: StateFlow<UpdateDownloadState> = _downloadState.asStateFlow()
    val manualRelease = MutableStateFlow<GithubRelease?>(null)
    private const val CHECK_INTERVAL_MS = 6 * 60 * 60_000L
    private const val SNOOZE_MS = 24 * 60 * 60_000L
    private var job: Job? = null

    private val os = System.getProperty("os.name").lowercase()
    private val ext = when { "mac" in os -> listOf(".dmg"); "win" in os -> listOf(".msi", ".exe"); else -> listOf(".deb", ".appimage") }
    private fun isInstaller(a: com.example.juke.models.GithubReleaseAsset) = ext.any { a.name.lowercase().endsWith(it) }

    suspend fun checkForUpdates(context: Context, force: Boolean = false): GithubRelease? = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong("last_clean_check", 0) < CHECK_INTERVAL_MS) return@withContext null
        val includePre = BuildConfig.VERSION_NAME.let { it.contains("beta", true) || it.contains("alpha", true) }
        val release = try {
            withTimeout(8.seconds) {
                val all: List<GithubRelease> = ApiClient.httpClient.get(GITHUB_API_URL).body()
                all.firstOrNull { !it.isDraft && (includePre || !it.isPrerelease) && it.assets.any(::isInstaller) }
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { Log.e("UpdateManager", "Failed to check for updates", e); return@withContext null }
        val newer = release?.takeIf { isNewer(it.tagName.removePrefix("v")) }
        if (newer == null) { prefs.edit { putLong("last_clean_check", now) }; return@withContext null }
        val snoozed = prefs.getString("snoozed_tag", null) == newer.tagName && now - prefs.getLong("snoozed_at", 0) < SNOOZE_MS
        if (!force && !newer.isCritical && snoozed) null else newer
    }

    fun snooze(context: Context, release: GithubRelease) {
        context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE).edit { putString("snoozed_tag", release.tagName); putLong("snoozed_at", System.currentTimeMillis()) }
    }

    fun pollProgress(context: Context) {}

    fun startUpdateDownload(context: Context, release: GithubRelease): Boolean {
        val asset = release.assets.firstOrNull(::isInstaller) ?: return false
        val dir = File(System.getProperty("user.home"), "Downloads").also { it.mkdirs() }
        val target = File(dir, asset.name)
        job?.cancel()
        _downloadState.value = UpdateDownloadState.Downloading(release.tagName, asset.name, 0, 0, asset.size)
        job = scope.launch {
            try {
                ApiClient.okHttp.newCall(Request.Builder().url(asset.browserDownloadUrl).build()).execute().use { r ->
                    check(r.isSuccessful) { "HTTP ${r.code}" }
                    val body = r.body!!; val total = body.contentLength().takeIf { it > 0 } ?: asset.size
                    var done = 0L
                    body.byteStream().use { i -> target.outputStream().use { o ->
                        val buf = ByteArray(64 * 1024)
                        while (true) { val n = i.read(buf); if (n < 0) break; o.write(buf, 0, n); done += n; ensureActive(); _downloadState.value = UpdateDownloadState.Downloading(release.tagName, asset.name, 0, done, total) }
                    } }
                }
                _downloadState.value = UpdateDownloadState.Ready(DownloadedUpdate(release.tagName, asset.name, target.absolutePath))
            } catch (e: CancellationException) { throw e } catch (e: Exception) { _downloadState.value = UpdateDownloadState.Error(e.message ?: "Download failed") }
        }
        return true
    }

    fun installDownloadedUpdate(context: Context, downloadedUpdate: DownloadedUpdate): Boolean =
        runCatching { java.awt.Desktop.getDesktop().open(File(downloadedUpdate.absolutePath)); true }.getOrDefault(false)

    fun openDownloadsFolder(context: Context): Boolean =
        runCatching { java.awt.Desktop.getDesktop().open(File(System.getProperty("user.home"), "Downloads")); true }.getOrDefault(false)

    fun clearDownloadState() { job?.cancel(); _downloadState.value = UpdateDownloadState.Idle }

    private fun isNewer(remote: String): Boolean {
        val c = BuildConfig.VERSION_NAME.split("-")[0].split(".").mapNotNull { it.toIntOrNull() }
        val r = remote.split("-")[0].split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(c.size, r.size)) { val a = c.getOrElse(i) { 0 }; val b = r.getOrElse(i) { 0 }; if (b > a) return true; if (b < a) return false }
        return false
    }
}
