package com.example.juke.utils

import android.util.Log
import com.example.juke.network.ApiClient
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.jvm.javaio.copyTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File

object FastDownloader {

    private const val TAG = "FastDownloader"
    private const val MIN_SEGMENT_BYTES = 256 * 1024L
    private const val MIN_THREADS = 2
    private const val MAX_THREADS = 8

    private val client = ApiClient.httpClient

    private data class RemoteFileInfo(
        val contentLength: Long,
        val supportsRanges: Boolean
    )

    suspend fun downloadSegmented(
        url: String,
        outputFile: File,
        headers: Map<String, String> = emptyMap(),
        threads: Int = 4
    ) = coroutineScope {
        outputFile.parentFile?.mkdirs()

        val stagingFile = File(outputFile.absolutePath + ".downloading")
        if (stagingFile.exists()) {
            stagingFile.delete()
        }

        try {
            val info = fetchRemoteFileInfo(url, headers)
            val threadCount = resolveThreadCount(info?.contentLength ?: 0L, threads)
            val canUseSegments = info != null && info.contentLength > 0L && info.supportsRanges && threadCount > 1

            // Log the chosen thread count so it's visible in logcat when downloads start
            if (canUseSegments) {
                Log.d(TAG, "Starting segmented download: threads=$threadCount")
            } else {
                Log.d(TAG, "Starting single-threaded download: threads=1")
            }

            if (!canUseSegments) {
                downloadSingleThread(url, stagingFile, headers)
            } else {
                val segmentSucceeded = runCatching {
                    downloadUsingSegments(
                        url = url,
                        destination = stagingFile,
                        totalBytes = info.contentLength,
                        headers = headers,
                        threads = threadCount
                    )
                }.isSuccess

                if (!segmentSucceeded) {
                    Log.w(TAG, "Segmented download failed, falling back to single thread")
                    if (stagingFile.exists()) {
                        stagingFile.delete()
                    }
                    downloadSingleThread(url, stagingFile, headers)
                }
            }

            if (!stagingFile.exists() || stagingFile.length() <= 0L) {
                throw Exception("Download failed: empty output")
            }

            moveIntoPlace(stagingFile, outputFile)
        } catch (e: Exception) {
            stagingFile.delete()
            throw e
        }
    }

    private suspend fun fetchRemoteFileInfo(
        url: String,
        headers: Map<String, String>
    ): RemoteFileInfo? {
        return runCatching {
            val headResponse = client.head(url) {
                applyHeaders(headers)
            }

            val contentLength = headResponse.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: 0L
            val acceptRanges = headResponse.headers[HttpHeaders.AcceptRanges]
                ?.contains("bytes", ignoreCase = true) == true

            if (headResponse.status.value in 200..299) {
                RemoteFileInfo(contentLength = contentLength, supportsRanges = acceptRanges)
            } else {
                null
            }
        }.getOrNull()
    }

    /**
     * Chooses an effective segment count for ranged downloads.
     *
     * Caps thread usage so each segment is large enough to be worthwhile and avoids
     * spawning workers that would only fetch tiny ranges.
     */
    private fun resolveThreadCount(contentLength: Long, preferredThreads: Int): Int {
        val clamped = preferredThreads.coerceIn(MIN_THREADS, MAX_THREADS)
        if (contentLength <= 0L) {
            return clamped
        }

        val maxUseful = (contentLength / MIN_SEGMENT_BYTES).toInt().coerceAtLeast(1)
        return minOf(clamped, maxUseful)
    }

    private suspend fun downloadUsingSegments(
        url: String,
        destination: File,
        totalBytes: Long,
        headers: Map<String, String>,
        threads: Int
    ) {
        val chunkSize = totalBytes / threads
        if (chunkSize <= 0L) {
            throw Exception("Invalid chunk size")
        }

        val partFiles = List(threads) { index ->
            File(destination.absolutePath + ".part$index")
        }

        try {
            coroutineScope {
                val jobs = partFiles.mapIndexed { index, partFile ->
                    val startByte = index * chunkSize
                    val endByte = if (index == threads - 1) {
                        totalBytes - 1
                    } else {
                        startByte + chunkSize - 1
                    }

                    async(Dispatchers.IO) {
                        downloadChunk(url, startByte, endByte, partFile, headers)
                    }
                }

                jobs.awaitAll()
            }
            mergeFiles(partFiles, destination)
        } finally {
            cleanup(partFiles)
        }
    }

    private suspend fun downloadChunk(
        url: String,
        startByte: Long,
        endByte: Long,
        destination: File,
        headers: Map<String, String>
    ) {
        val response: HttpResponse = client.get(url) {
            applyHeaders(headers)
            header(HttpHeaders.Range, "bytes=$startByte-$endByte")
        }

        if (response.status != HttpStatusCode.PartialContent) {
            throw Exception("Range request rejected (${response.status.value})")
        }

        writeResponseToFile(response, destination)
    }

    /**
     * Fallback path when range requests are unavailable or segmented download fails.
     */
    private suspend fun downloadSingleThread(
        url: String,
        destination: File,
        headers: Map<String, String>
    ) {
        val response: HttpResponse = client.get(url) {
            applyHeaders(headers)
        }

        if (response.status.value !in 200..299) {
            throw Exception("Download failed with status ${response.status.value}")
        }

        writeResponseToFile(response, destination)
    }

    private suspend fun writeResponseToFile(response: HttpResponse, destination: File) {
        withContext(Dispatchers.IO) {
            destination.outputStream().buffered().use { output ->
                response.bodyAsChannel().copyTo(output)
            }
        }
    }

    private suspend fun mergeFiles(parts: List<File>, finalFile: File) {
        withContext(Dispatchers.IO) {
            finalFile.outputStream().buffered().use { output ->
                for (part in parts) {
                    part.inputStream().buffered().use { input ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

    private suspend fun moveIntoPlace(staging: File, output: File) {
        withContext(Dispatchers.IO) {
            if (output.exists() && !output.delete()) {
                throw Exception("Failed to replace existing file: ${output.absolutePath}")
            }

            if (!staging.renameTo(output)) {
                staging.inputStream().use { input ->
                    output.outputStream().use { out ->
                        input.copyTo(out)
                    }
                }
                staging.delete()
            }
        }
    }

    private fun cleanup(files: List<File>) {
        files.forEach { file ->
            if (file.exists()) {
                file.delete()
            }
        }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.applyHeaders(headers: Map<String, String>) {
        headers.forEach { (name, value) ->
            header(name, value)
        }
    }
}
