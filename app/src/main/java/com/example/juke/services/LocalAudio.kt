package com.example.juke.services

import java.io.File

/**
 * Disk checks for a track's audio. Files can disappear without the app knowing: LRU eviction,
 * stale-stream purges, "Clear cache"/"Clear storage" in system settings, or the OS reclaiming
 * space. Callers check the disk itself rather than trusting the DB's localUri.
 */
object LocalAudio {

    /** The owned file behind [localUri], or null for remote/content URIs we don't manage. */
    fun fileFor(localUri: String?): File? = when {
        localUri == null -> null
        localUri.startsWith("file:") -> try {
            File(java.net.URI(localUri))
        } catch (_: Exception) {
            File(localUri.removePrefix("file://"))
        }
        localUri.startsWith("/") -> File(localUri)
        else -> null
    }

    /**
     * True when the audio can't be played from disk: no URI at all, or an owned local file that
     * is gone or empty. Remote/content URIs are not considered missing.
     */
    fun isMissing(localUri: String?): Boolean {
        if (localUri == null) return true
        val file = fileFor(localUri) ?: return false
        return try {
            !file.isFile || file.length() <= 0L
        } catch (_: Exception) {
            true
        }
    }
}
