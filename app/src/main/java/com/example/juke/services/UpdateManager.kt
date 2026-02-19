package com.example.juke.services

import android.util.Log
import com.example.juke.BuildConfig
import com.example.juke.models.GithubRelease
import com.example.juke.network.ApiClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object UpdateManager {
    private const val REPO_OWNER = "rajeet-04"
    private const val REPO_NAME = "JUKES"

    // Use /releases (list) instead of /releases/latest to see pre-releases
    private const val GITHUB_API_URL =
        "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases"

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
}
