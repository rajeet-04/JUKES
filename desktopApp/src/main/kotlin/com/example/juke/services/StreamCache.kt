package com.example.juke.services

import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.Duration

/**
 * The player can't take the backend's fragmented MP4 straight off the network (no seek, "media type
 * not supported", stalls). Stream URLs are fetched whole into this cache first, then played from disk.
 */
object StreamCache {
    private const val MAX_FILES = 30
    private val dir get() = File(android.app.Application.instance.cacheDir, "streams").also { it.mkdirs() }
    private val http by lazy { HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).connectTimeout(Duration.ofSeconds(15)).build() }

    fun fileFor(url: String) = File(dir, MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }.take(24) + ".m4a")
    fun cached(url: String): File? = fileFor(url).takeIf { it.length() > 0 }

    /** Playlists (HLS) are left to the player itself. */
    fun needsFetch(url: String) = url.startsWith("http", true) && !url.substringBefore('?').endsWith(".m3u8", true) && cached(url) == null

    private val downloads = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.CompletableFuture<File>>()

    /** Blocking; call off the UI thread. Returns the cached file or throws. */
    fun fetch(url: String): File {
        cached(url)?.let { return it }
        val result = java.util.concurrent.CompletableFuture<File>()
        downloads.putIfAbsent(url, result)?.let { return it.get() }
        try {
            val file = cached(url) ?: download(url)
            result.complete(file)
            return file
        } catch (error: Throwable) {
            result.completeExceptionally(error)
            throw error
        } finally {
            downloads.remove(url, result)
        }
    }

    private fun download(url: String): File {
        val out = fileFor(url)
        val part = java.nio.file.Files.createTempFile(out.parentFile.toPath(), out.name, ".part")
        try {
            val request = HttpRequest.newBuilder(URI(url)).timeout(Duration.ofSeconds(120)).GET().build()
            val response = http.send(request, HttpResponse.BodyHandlers.ofFile(part))
            if (response.statusCode() !in 200..299 || java.nio.file.Files.size(part) == 0L) {
                throw java.io.IOException("stream HTTP ${response.statusCode()}")
            }
            java.nio.file.Files.move(part, out.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE)
            dir.listFiles { f -> f.name.endsWith(".m4a") }?.sortedByDescending { it.lastModified() }
                ?.drop(MAX_FILES)?.forEach { it.delete() }
            return out
        } finally {
            java.nio.file.Files.deleteIfExists(part)
        }
    }
}
