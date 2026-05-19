package com.example.juke.services

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.juke.BuildConfig
import com.example.juke.models.GithubRelease
import com.example.juke.models.GithubReleaseAsset
import com.example.juke.network.ApiClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

data class DownloadedUpdate(
    val releaseTag: String,
    val fileName: String,
    val absolutePath: String
)

sealed interface UpdateDownloadState {
    object Idle : UpdateDownloadState

    data class Downloading(
        val releaseTag: String,
        val fileName: String,
        val downloadId: Long
    ) : UpdateDownloadState

    data class Ready(val update: DownloadedUpdate) : UpdateDownloadState

    data class Error(val message: String) : UpdateDownloadState
}

object UpdateManager {
    private const val REPO_OWNER = "rajeet-04"
    private const val REPO_NAME = "JUKES"
    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"

    // Use /releases (list) instead of /releases/latest to see pre-releases
    private const val GITHUB_API_URL =
        "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases"

    private val _downloadState = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val downloadState: StateFlow<UpdateDownloadState> = _downloadState.asStateFlow()

    private var downloadReceiver: BroadcastReceiver? = null
    private var activeDownloadId: Long? = null
    private var activeDownloadFileName: String? = null
    private var activeReleaseTag: String? = null

    suspend fun checkForUpdates(): GithubRelease? = withContext(Dispatchers.IO) {
        try {
            // Fetch list of releases (returns generic list)
            val response = ApiClient.httpClient.get(GITHUB_API_URL)
            val releases: List<GithubRelease> = response.body()

            if (releases.isEmpty()) return@withContext null

            // The API usually returns sorted by date, but we take the first one as 'latest'
            val latestRelease = releases.first()

            // Clean up version strings (remove 'v' prefix)
            val currentVersion = BuildConfig.VERSION_NAME // e.g., "1.0.1-beta"
            val latestVersionTag = latestRelease.tagName.removePrefix("v") // e.g., "1.0.2"

            if (isNewer(currentVersion, latestVersionTag)) {
                return@withContext latestRelease
            }
        } catch (e: Exception) {
            Log.e("UpdateManager", "Failed to check for updates", e)
        }
        return@withContext null
    }

    fun startUpdateDownload(context: Context, release: GithubRelease): Boolean {
        if (_downloadState.value is UpdateDownloadState.Downloading) {
            return true
        }

        val asset = release.assets.firstOrNull(::isApkAsset)
        if (asset == null) {
            _downloadState.value = UpdateDownloadState.Error(
                "No APK file was found for ${release.tagName}."
            )
            return false
        }

        return try {
            val appContext = context.applicationContext
            val fileName = resolveDownloadFileName(asset.name, release.tagName)
            val request = DownloadManager.Request(Uri.parse(asset.browserDownloadUrl)).apply {
                setTitle("Downloading JUKE Update")
                setDescription("Fetching ${release.tagName}...")
                setMimeType(APK_MIME_TYPE)
                setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            }

            val downloadManager =
                appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            cleanupReceiver(appContext)
            val downloadId = downloadManager.enqueue(request)

            activeDownloadId = downloadId
            activeDownloadFileName = fileName
            activeReleaseTag = release.tagName
            _downloadState.value = UpdateDownloadState.Downloading(
                releaseTag = release.tagName,
                fileName = fileName,
                downloadId = downloadId
            )

            registerDownloadReceiver(appContext)
            true
        } catch (e: Exception) {
            Log.e("UpdateManager", "Failed to enqueue update download", e)
            _downloadState.value = UpdateDownloadState.Error(
                "Couldn't start the update download."
            )
            false
        }
    }

    fun installDownloadedUpdate(context: Context, downloadedUpdate: DownloadedUpdate): Boolean {
        val apkFile = File(downloadedUpdate.absolutePath)
        if (!apkFile.exists()) {
            Toast.makeText(context, "Downloaded APK not found.", Toast.LENGTH_SHORT).show()
            return false
        }

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            Toast.makeText(
                context,
                "Allow installs from JUKE to continue.",
                Toast.LENGTH_LONG
            ).show()
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(settingsIntent)
            return false
        }

        return try {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, APK_MIME_TYPE)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
            true
        } catch (e: Exception) {
            Log.e("UpdateManager", "Failed to open installer", e)
            Toast.makeText(context, "Couldn't open the installer.", Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun openDownloadsFolder(context: Context): Boolean {
        val downloadsIntent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(downloadsIntent)
            true
        } catch (e: Exception) {
            Log.e("UpdateManager", "Failed to open downloads folder", e)
            Toast.makeText(context, "No downloads app found.", Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun clearDownloadState() {
        if (_downloadState.value !is UpdateDownloadState.Downloading) {
            _downloadState.value = UpdateDownloadState.Idle
        }
    }

    /**
     * Compares two version strings.
     * Returns true if [remote] is newer than [current].
     * Handles standard SemVer (1.0.0 vs 1.0.1) and basic suffixes.
     */
    private fun isNewer(current: String, remote: String): Boolean {
        // Simple normalization: ignore suffixes for the main number check
        // Real implementation might need complex SemVer parsing if you mix betas and stable often
        val currClean = current.split("-")[0]
        val remoteClean = remote.split("-")[0]

        val currParts = currClean.split(".").mapNotNull { it.toIntOrNull() }
        val remoteParts = remoteClean.split(".").mapNotNull { it.toIntOrNull() }

        val length = maxOf(currParts.size, remoteParts.size)

        for (i in 0 until length) {
            val c = currParts.getOrElse(i) { 0 }
            val r = remoteParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }

        // If numeric parts are equal, check suffixes
        // Logic: 1.0.1 (stable) > 1.0.1-beta
        val currIsBeta = current.contains("beta", true) || current.contains("alpha", true)
        val remoteIsBeta = remote.contains("beta", true) || remote.contains("alpha", true)

        if (currIsBeta && !remoteIsBeta) return true // Upgrade from beta to stable

        // If both are beta or both stable, and numbers are equal, assume same version (false)
        return false
    }

    private fun isApkAsset(asset: GithubReleaseAsset): Boolean {
        return asset.name.endsWith(".apk", ignoreCase = true) ||
            asset.contentType.equals(APK_MIME_TYPE, ignoreCase = true)
    }

    private fun registerDownloadReceiver(context: Context) {
        if (downloadReceiver != null) return

        downloadReceiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return

                val completedDownloadId = intent.getLongExtra(
                    DownloadManager.EXTRA_DOWNLOAD_ID,
                    -1L
                )
                if (completedDownloadId != activeDownloadId) return

                handleDownloadComplete(context, completedDownloadId)
            }
        }

        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(downloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(downloadReceiver, filter)
        }
    }

    private fun handleDownloadComplete(context: Context, downloadId: Long) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)

        try {
            downloadManager.query(query)?.use { cursor ->
                if (!cursor.moveToFirst()) {
                    _downloadState.value = UpdateDownloadState.Error(
                        "The completed update couldn't be found."
                    )
                    return
                }

                val statusIndex = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                val status = cursor.getInt(statusIndex)

                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    val fileName = activeDownloadFileName.orEmpty()
                    val releaseTag = activeReleaseTag.orEmpty()
                    val downloadedFile = createInstallerCopy(
                        context = context,
                        downloadManager = downloadManager,
                        downloadId = downloadId,
                        fileName = fileName
                    )

                    if (downloadedFile == null || !downloadedFile.exists()) {
                        _downloadState.value = UpdateDownloadState.Error(
                            "The update finished downloading, but the APK file couldn't be found."
                        )
                        return
                    }

                    _downloadState.value = UpdateDownloadState.Ready(
                        DownloadedUpdate(
                            releaseTag = releaseTag,
                            fileName = downloadedFile.name,
                            absolutePath = downloadedFile.absolutePath
                        )
                    )
                } else {
                    val reasonIndex = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)
                    val reason = cursor.getInt(reasonIndex)
                    _downloadState.value = UpdateDownloadState.Error(
                        "Update download failed (code $reason)."
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("UpdateManager", "Failed to process completed update download", e)
            _downloadState.value = UpdateDownloadState.Error(
                "Couldn't finish preparing the downloaded update."
            )
        } finally {
            cleanupReceiver(context)
        }
    }

    private fun cleanupReceiver(context: Context) {
        downloadReceiver?.let { receiver ->
            runCatching {
                context.unregisterReceiver(receiver)
            }.onFailure {
                Log.d("UpdateManager", "Receiver was already unregistered")
            }
        }

        downloadReceiver = null
        activeDownloadId = null
        activeDownloadFileName = null
        activeReleaseTag = null
    }

    private fun resolveDownloadFileName(assetName: String, releaseTag: String): String {
        val fallbackName = "JUKES-${releaseTag.removePrefix("v")}.apk"
        val baseName = assetName.takeIf { it.endsWith(".apk", ignoreCase = true) } ?: fallbackName
        val stem = if (baseName.endsWith(".apk", ignoreCase = true)) {
            baseName.dropLast(4)
        } else {
            baseName
        }

        val downloadsDirectory = publicDownloadsDirectory()
        if (!downloadsDirectory.exists()) {
            downloadsDirectory.mkdirs()
        }

        var candidate = "$stem.apk"
        var suffix = 1
        while (File(downloadsDirectory, candidate).exists()) {
            candidate = "$stem-$suffix.apk"
            suffix++
        }

        return candidate
    }

    private fun createInstallerCopy(
        context: Context,
        downloadManager: DownloadManager,
        downloadId: Long,
        fileName: String
    ): File? {
        val downloadUri = downloadManager.getUriForDownloadedFile(downloadId) ?: return null
        val updatesDirectory = File(context.cacheDir, "updates")
        if (!updatesDirectory.exists()) {
            updatesDirectory.mkdirs()
        }

        updatesDirectory.listFiles()?.forEach { cachedApk ->
            if (cachedApk.name != fileName) {
                cachedApk.delete()
            }
        }

        val installerFile = File(updatesDirectory, fileName)
        context.contentResolver.openInputStream(downloadUri)?.use { input ->
            installerFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: return null

        return installerFile
    }

    @Suppress("DEPRECATION")
    private fun publicDownloadsDirectory(): File {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    }
}
