package com.example.juke.services

import androidx.room.withTransaction

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import com.example.juke.database.MusicDatabase
import com.example.juke.database.toEntity
import com.example.juke.database.toTrack
import com.example.juke.models.SpotdownSong
import com.example.juke.models.Track
import com.example.juke.models.withUpdatedLyrics
import com.example.juke.network.ApiClient
import com.example.juke.network.RecommenderApi
import com.example.juke.network.AlexaBackendApi
import com.example.juke.network.JukesApi
import com.example.juke.network.SpotsaverApi
import com.example.juke.network.SpotifyApi
import com.example.juke.utils.FastDownloader
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.http.contentLength
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.UUID
import kotlin.math.abs

/**
 * Music Service for downloading and indexing tracks.
 */
class MusicService(private val context: Context) {

    private val TAG = "MusicService"
    private val BACKEND_STREAM_TIMEOUT_MS = JukesApi.PREPARE_BUDGET_MS + 5_000L
    private val database = MusicDatabase.getDatabase(context)
    private val trackDao = database.trackDao()
    private val sourceMemory = SourceMemory(context)

    /**
     * One provider's download request for [song]. [live] = for immediate streaming (the backend
     * then streams a cache miss instead of finishing the file first, and Gamepvz uses its stream
     * URL). A queued Spotmate conversion reports its task id to [onSpotmateQueued] and still throws.
     */
    private suspend fun requestFor(
        source: Source,
        song: SpotdownSong,
        live: Boolean,
        onSpotmateQueued: (String) -> Unit = {}
    ): SpotifyApi.DirectDownloadRequest {
        val durationSec = SpotifyApi.parseDuration(song.duration)
        return when (source) {
            Source.BACKEND -> AlexaBackendApi.getDownloadRequest(song.title, song.artist, durationSec, live)
            Source.SPOTSAVER -> SpotsaverApi.getDownloadRequest(song.title, song.artist, durationSec)
            Source.GAMEPVZ -> SpotifyApi.getGamepvzDownloadRequest(song.url).let {
                if (live) it.copy(url = com.example.juke.network.gamepvzStreamUrl(it.url)) else it
            }
            Source.SPOTMATE -> try {
                SpotifyApi.getSpotmateDownloadRequest(song.url)
            } catch (e: SpotifyApi.SpotmateQueuedException) {
                Log.w(TAG, "Spotmate conversion queued (taskId=${e.taskId}), trying alternate source")
                onSpotmateQueued(e.taskId)
                throw e
            }
        }
    }

    /**
     * Per-source budget for a full download. The backend finishes the file first (prepare + poll, up
     * to [JukesApi.PREPARE_BUDGET_MS]) and then serves it.
     */
    private fun downloadTimeoutMs(source: Source) = when (source) {
        Source.BACKEND -> JukesApi.PREPARE_BUDGET_MS + 60_000L
        Source.SPOTSAVER -> 45_000L
        else -> 90_000L
    }

    private fun generateUUID(): String {
        return UUID.randomUUID().toString().replace("-", "")
    }

    private suspend fun <T> retryWithBackoff(
        maxRetries: Int = 5,
        operationName: String = "operation",
        block: suspend () -> T
    ): T {
        var lastError: Exception? = null

        for (attempt in 1..maxRetries) {
            try {
                return block()
            } catch (e: Exception) {
                lastError = e

                val isRetryable = e.message?.contains("500") == true ||
                        e.message?.contains("network") == true ||
                        e.message?.contains("timeout") == true

                if (attempt < maxRetries && isRetryable) {
                    val delayMs = minOf(1000L * (1 shl (attempt - 1)), 10000L)
                    Log.d(
                        TAG,
                        "[Retry $attempt/$maxRetries] $operationName failed, retrying in ${delayMs}ms..."
                    )
                    delay(delayMs)
                } else if (attempt >= maxRetries) {
                    Log.e(TAG, "[Retry] $operationName failed after $maxRetries attempts")
                    break
                } else {
                    throw e
                }
            }
        }

        throw lastError ?: Exception("Operation failed")
    }

    private suspend fun resolveStreamToLocalFile(
        song: SpotdownSong,
        stableUuid: String,
        forceSpotmateFirst: Boolean = false
    ): String {
        if (!song.url.startsWith("https://open.spotify.com/track/")) {
            throw Exception("Invalid Spotify URL format")
        }

        // Keep stream files in app-internal files dir so they survive cache eviction.
        val streamDir = File(context.filesDir, "stream_files")
        if (!streamDir.exists()) streamDir.mkdirs()

        val finalFile = File(streamDir, "${stableUuid}_stream.mp3")
        val tempFile = File(streamDir, "${stableUuid}_stream.tmp")

        // Spotsaver is primary; keep the existing legacy provider preference for fallback.
        val useGamepvzFirst = if (forceSpotmateFirst) false
            else (System.currentTimeMillis() % 2L) == 0L
        var queuedSpotmateTaskId: String? = null

        suspend fun downloadToTempFile(source: Source, lastSource: Boolean) {
            if (tempFile.exists()) {
                tempFile.delete()
            }

            val request = requestFor(source, song, live = false) { queuedSpotmateTaskId = it }

            FastDownloader.downloadSegmented(
                url = request.url,
                outputFile = tempFile,
                headers = request.headers,
                threads = 4,
                probeRanges = request.probeRanges
            )

            if (!tempFile.exists() || tempFile.length() < 100_000L || !isValidAudioHeader(tempFile)) {
                throw Exception("Stream payload is too small")
            }
            verifyLength(tempFile, SpotifyApi.parseDuration(song.duration), lastSource)
        }

        val streamKey = RecommenderApi.songKey(song.title, song.artist)
        val order = sourceMemory.order(streamKey, useGamepvzFirst)
        val failures = mutableListOf<String>()
        var fetched = false
        for (source in order) {
            try {
                withTimeout(if (source == Source.SPOTSAVER) 30_000L else downloadTimeoutMs(source)) {
                    downloadToTempFile(source, lastSource = source == order.last())
                }
                sourceMemory.recordUsed(stableUuid, source)
                fetched = true
                break
            } catch (e: Exception) {
                // Provider timeouts fall through to the next source; cancellation of playback propagates.
                currentCoroutineContext().ensureActive()
                Log.w(TAG, "$source stream fetch failed (${e.message}), trying next source")
                failures += "$source=${e.message}"
                // A preview clip or wrong version: remember, so later pulls of this song skip this source.
                if (e is WrongLengthException) sourceMemory.avoid(streamKey, source)
            }
        }
        if (!fetched) {
            val queuedTaskId = queuedSpotmateTaskId
            if (queuedTaskId.isNullOrBlank()) {
                Log.e(TAG, "All stream sources failed: $failures")
                throw Exception("Stream unavailable: ${failures.joinToString(", ")}")
            }
            Log.w(TAG, "All direct stream sources failed; polling queued Spotmate task: $queuedTaskId")
            try {
                val queuedData = withTimeout(120_000L) { SpotifyApi.downloadSongFromSpotmateTask(queuedTaskId) }
                if (queuedData.isEmpty() || queuedData.size < 100_000) {
                    throw Exception("Queued Spotmate stream payload is too small")
                }
                if (tempFile.exists()) tempFile.delete()
                tempFile.writeBytes(queuedData)
                verifyLength(tempFile, SpotifyApi.parseDuration(song.duration), lenient = true)
                sourceMemory.recordUsed(stableUuid, Source.SPOTMATE)
            } catch (queuedTaskEx: Exception) {
                Log.e(TAG, "Queued Spotmate task failed: ${queuedTaskEx.message}")
                throw Exception("Stream unavailable: ${failures.joinToString(", ")}, queued=${queuedTaskEx.message}")
            }
        }

        if (finalFile.exists()) {
            finalFile.delete()
        }

        val moved = tempFile.renameTo(finalFile)
        if (!moved) {
            tempFile.copyTo(finalFile, overwrite = true)
            tempFile.delete()
        }

        if (!finalFile.exists() || finalFile.length() <= 0L) {
            throw Exception("Failed to persist stream file")
        }

        return finalFile.absolutePath
    }

    /**
     * Cheap check for a provider URL that serves a short preview clip instead of the song: the file
     * is far too small for the Spotify length even at a low bitrate (under ~64 kbps equivalent).
     * Unknown size (no HEAD support, chunked) is treated as fine and left to the later length check.
     */
    private suspend fun looksLikePreview(request: SpotifyApi.DirectDownloadRequest, expectedSec: Int): Boolean {
        if (expectedSec < 60) return false
        return try {
            val bytes = withTimeout(4_000L) {
                ApiClient.httpClient.head(request.url) {
                    request.headers.forEach { (k, v) -> header(k, v) }
                }.contentLength()
            }
            bytes != null && bytes > 0 && bytes < expectedSec * 8_000L
        } catch (e: kotlinx.coroutines.CancellationException) {
            currentCoroutineContext().ensureActive()
            false
        } catch (_: Exception) {
            false
        }
    }

    private class WrongLengthException(message: String) : Exception(message)

    private fun fileDurationSec(file: File): Int? = try {
        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(file.absolutePath)
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { (it / 1000).toInt() }
        } finally {
            mmr.release()
        }
    } catch (_: Exception) {
        null
    }

    /**
     * Compare the fetched audio with the Spotify length. A source that returns a preview clip
     * (about 30 s) or a clearly different version is rejected so the next source is tried. When
     * [lenient] (the last source left) only truncated audio is rejected, so a track whose length
     * legitimately differs a little is not lost.
     */
    private fun verifyLength(file: File, expectedSec: Int, lenient: Boolean) {
        if (expectedSec < 45) return
        val actual = fileDurationSec(file) ?: return
        val truncated = actual < expectedSec * 0.85 - 3
        val different = abs(actual - expectedSec) > maxOf(8, expectedSec * 7 / 100)
        Log.d(TAG, "Length check: got ${actual}s, expected ${expectedSec}s (truncated=$truncated, different=$different)")
        if (truncated || (different && !lenient)) {
            throw WrongLengthException("got ${actual}s, expected ${expectedSec}s")
        }
    }

    /** MP3 (ID3 tag or frame sync), MP4/M4A (`ftyp` box) or WebM (EBML): what the sources serve. */
    private fun isValidAudioHeader(file: File): Boolean {
        if (!file.exists() || file.length() < 8L) return false
        return try {
            file.inputStream().use { input ->
                val header = ByteArray(8)
                val bytesRead = input.read(header)
                if (bytesRead < 8) return@use false

                val isID3 = header[0] == 0x49.toByte() &&
                        header[1] == 0x44.toByte() &&
                        header[2] == 0x33.toByte()

                val isMP3Frame = header[0] == 0xFF.toByte() &&
                        (header[1].toInt() and 0xE0) == 0xE0

                val isMp4 = header[4] == 'f'.code.toByte() && header[5] == 't'.code.toByte() &&
                        header[6] == 'y'.code.toByte() && header[7] == 'p'.code.toByte()

                val isWebm = header[0] == 0x1A.toByte() && header[1] == 0x45.toByte() &&
                        header[2] == 0xDF.toByte() && header[3] == 0xA3.toByte()

                isID3 || isMP3Frame || isMp4 || isWebm
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun isHealthyExistingStreamFile(filePath: String, expectedDurationSec: Int): Boolean {
        val streamFile = File(filePath)
        if (!streamFile.exists() || streamFile.length() < 100_000L) {
            return false
        }

        if (!isValidAudioHeader(streamFile)) {
            return false
        }

        return try {
            val mmr = MediaMetadataRetriever()
            mmr.setDataSource(streamFile.absolutePath)
            val durationMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            mmr.release()
            val durationSec = (durationMs?.toLongOrNull() ?: 0L) / 1000L

            if (durationSec <= 0L) {
                false
            } else {
                // Accept if duration is within 30 seconds of expected. This is generous
                // enough for VBR MP3s with imperfect Xing headers, but strict enough to
                // reject truncated or wrong-track files.
                // REMOVED: "|| durationSec > 20L" — that short-circuit was accepting any
                // file over 20s as healthy, including wrong/truncated streams.
                val tolerance = maxOf(30, expectedDurationSec / 5)
                abs(durationSec.toInt() - expectedDurationSec) <= tolerance
            }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun smartDownloadAndIndex(
        song: SpotdownSong
    ): Track {
        val durationSec = SpotifyApi.parseDuration(song.duration)

        // Check if track already exists in database
        val candidates = trackDao.findTracksByTitleAndDuration(song.title, durationSec)
        val existingTrack = candidates.find {
            com.example.juke.utils.ArtistUtils.areArtistsEqual(it.artist, song.artist)
        }

        // If track exists with a remote/streaming URL, we'll update it with the downloaded file
        // Otherwise if it has a local file, just return it
        if (existingTrack != null && existingTrack.localUri != null) {
            val isRemoteUri = existingTrack.localUri.startsWith("http", ignoreCase = true)

            if (!isRemoteUri && !existingTrack.isStream) {
                // Already has a local file, return it
                Log.d(
                    TAG,
                    "Track already exists in database with local file: ${song.title} by ${song.artist}"
                )
                return existingTrack.toTrack()
            } else if (existingTrack.isStream) {
                // Existing stream entry: promote to a permanent local download
                Log.d(
                    TAG,
                    "Found stream track in database, promoting to full download: ${song.title}"
                )
            } else {
                // Has remote URL, we'll download and update this same record
                Log.d(
                    TAG,
                    "Found streaming track in database, will update with downloaded file: ${song.title}"
                )
            }
        }

        // Use existing UUID if track exists (streaming version), otherwise generate new one
        val uuid = existingTrack?.uuid ?: generateUUID()
        val musicDir = File(context.filesDir, "music")
        if (!musicDir.exists()) musicDir.mkdirs()

        val audioFile = File(musicDir, "$uuid.mp3")

        try {
            Log.d(TAG, "Validating Spotify URL: ${song.url}")
            if (!song.url.startsWith("https://open.spotify.com/track/")) {
                throw Exception("Invalid Spotify URL format")
            }

            // Backend (when configured), then Spotsaver; preserve the legacy fallback order.
            val useGamepvzFirst = (System.currentTimeMillis() % 2L) == 0L
            Log.d(
                TAG,
                "Downloading '${song.title}' — primary: ${if (AlexaBackendApi.isConfigured) "backend" else "Spotsaver"}"
            )
            var queuedSpotmateTaskId: String? = null

            suspend fun trySource(source: Source, lastSource: Boolean = false) {
                if (audioFile.exists()) {
                    audioFile.delete()
                }

                val request = requestFor(source, song, live = false) { queuedSpotmateTaskId = it }

                FastDownloader.downloadSegmented(
                    url = request.url,
                    outputFile = audioFile,
                    headers = request.headers,
                    threads = 4,
                    probeRanges = request.probeRanges
                )

                if (!audioFile.exists() || audioFile.length() < 100_000L || !isValidAudioHeader(audioFile)) {
                    throw Exception("Downloaded file is too small to be valid audio")
                }
                verifyLength(audioFile, durationSec, lastSource)
            }

            val songKey = RecommenderApi.songKey(song.title, song.artist)
            val order = sourceMemory.order(songKey, useGamepvzFirst)
            val failures = mutableListOf<String>()
            var usedSource: Source? = null
            for (source in order) {
                try {
                    withTimeout(downloadTimeoutMs(source)) {
                        trySource(source, lastSource = source == order.last())
                    }
                    usedSource = source
                    break
                } catch (e: Exception) {
                    currentCoroutineContext().ensureActive()
                    Log.w(TAG, "$source download failed (${e.message}), trying next source")
                    failures += "$source=${e.message}"
                    if (e is WrongLengthException) sourceMemory.avoid(songKey, source)
                }
            }
            if (usedSource == null) {
                val queuedTaskId = queuedSpotmateTaskId
                if (queuedTaskId.isNullOrBlank()) {
                    throw Exception("All sources failed: ${failures.joinToString(", ")}")
                }
                Log.w(TAG, "All direct sources failed; polling queued Spotmate task: $queuedTaskId")
                val queuedData = withTimeout(120_000L) { SpotifyApi.downloadSongFromSpotmateTask(queuedTaskId) }
                if (queuedData.isEmpty() || queuedData.size < 100_000) {
                    throw Exception("Queued Spotmate download is too small")
                }
                if (audioFile.exists()) audioFile.delete()
                audioFile.writeBytes(queuedData)
                verifyLength(audioFile, durationSec, lenient = true)
                usedSource = Source.SPOTMATE
            }
            sourceMemory.recordUsed(uuid, usedSource)

            Log.d(TAG, "Downloaded ${audioFile.length()} bytes — wrote to ${audioFile.absolutePath}")

            if (!audioFile.exists() || audioFile.length() == 0L) {
                throw Exception("Failed to write audio file")
            }


            val lyricsResult =
                SpotifyApi.searchLyrics(song.title, song.artist, song.album, durationSec)
            val ytVideoId = RecommenderApi.getBestVideoMatch("${song.title} ${song.artist}", SpotifyApi.parseDuration(song.duration), song.artist)

            var thumbnailUri: String? = null
            if (song.thumbnail.isNotBlank()) {
                try {
                    val thumbnailFile = File(musicDir, "${uuid}_thumb.jpg")
                    // Download thumbnail bytes with retry
                    val imageBytes: ByteArray = try {
                        retryWithBackoff(maxRetries = 3, operationName = "download thumbnail") {
                            ApiClient.httpClient.get(song.thumbnail).body()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Thumbnail download failed: ${e.message}", e)
                        ByteArray(0)
                    }

                    if (imageBytes.isNotEmpty()) {
                        thumbnailFile.writeBytes(imageBytes)
                        thumbnailUri = thumbnailFile.absolutePath
                        Log.d(TAG, "Thumbnail saved to: ${thumbnailFile.absolutePath}")
                    } else {
                        Log.d(TAG, "No thumbnail bytes downloaded for ${song.title}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving thumbnail: ${e.message}", e)
                }
            }

            val syncedLyrics = lyricsResult?.syncedLyrics ?: existingTrack?.syncedLyrics
            val plainLyrics = lyricsResult?.plainLyrics ?: existingTrack?.plainLyrics

            val track = Track(
                uuid = uuid,
                title = song.title,
                artist = song.artist,
                thumbnailUri = thumbnailUri
                    ?: existingTrack?.thumbnailUri, // Keep existing thumb if download fails
                durationSec = durationSec,
                localUri = audioFile.absolutePath,
                ytVideoId = ytVideoId
                    ?: existingTrack?.ytVideoId, // Keep existing YT ID if verify fails? (RecommenderApi might return null?)
                isFavourite = existingTrack?.isFavourite ?: false,
                playCount = existingTrack?.playCount ?: 0,
                lastPlayedAt = existingTrack?.lastPlayedAt,
                downloadedAt = System.currentTimeMillis(),
                spotifyId = song.spotifyId,
                albumSpotifyId = song.albumSpotifyId,
                artistSpotifyIds = song.artistSpotifyIds,
                isStream = false
            ).withUpdatedLyrics(
                syncedLyrics = syncedLyrics,
                plainLyrics = plainLyrics
            )

            trackDao.insertTrack(track.toEntity())

            Log.d(TAG, "Successfully downloaded and indexed: ${track.title}")
            return track

        } catch (e: Exception) {
            if (audioFile.exists()) {
                audioFile.delete()
            }
            Log.e(TAG, "Error in smartDownloadAndIndex: ${e.message}", e)
            throw e
        }
    }


    /**
     * Instant-play path: resolves the direct MP3 URL and returns a Track immediately so
     * ExoPlayer can start streaming from the network URL right away (sub-second start).
     *
     * Simultaneously kicks off a background download via [FastDownloader] to save the full
     * file to [stream_files/]. When the download completes, [onLocalFileReady] is called
     * with the updated Track (localUri pointing to the local file) so the caller can swap
     * the ExoPlayer source for better persistence and LRU caching.
     *
     * @param song            Source song metadata.
     * @param forceSpotmateFirst  Provider preference for queued full-download recovery only.
     * @param pinnedUuids     UUIDs protected from LRU eviction during the download.
     * @param onLocalFileReady Called on [Dispatchers.Main] once the background download
     *                         finishes. Receives the updated Track with a local file URI.
     *                         Will NOT be called if the download fails.
     * @return A Track with [localUri] set to the direct HTTP URL, ready for immediate playback.
     */
    suspend fun streamTrackInstant(
        song: SpotdownSong,
        forceSpotmateFirst: Boolean = true,
        pinnedUuids: Set<String> = emptySet(),
        awaitPlaybackStarted: suspend (String) -> Unit,
        onLocalFileReady: suspend (Track) -> Unit
    ): Track {
        if (!song.url.startsWith("https://open.spotify.com/track/")) {
            throw Exception("Invalid Spotify URL format")
        }

        val durationSec = SpotifyApi.parseDuration(song.duration)

        // Check for an existing permanent download first — no streaming needed.
        val candidates = trackDao.findTracksByTitleAndDuration(song.title, durationSec)
        val existing = candidates.find {
            com.example.juke.utils.ArtistUtils.areArtistsEqual(it.artist, song.artist)
        }
        if (existing != null && !existing.isStream && existing.localUri != null &&
            !existing.localUri.startsWith("http") && File(existing.localUri).exists()
        ) {
            return existing.toTrack()
        }

        // Check for a healthy cached stream file — play from disk instantly.
        val uuid = existing?.uuid ?: generateUUID()
        val streamDir = File(context.filesDir, "stream_files")
        val cachedFile = File(streamDir, "${uuid}_stream.mp3")
        if (cachedFile.exists() && isHealthyExistingStreamFile(cachedFile.absolutePath, durationSec)) {
            Log.d(TAG, "streamTrackInstant: reusing cached stream file for '${song.title}'")
            cachedFile.setLastModified(System.currentTimeMillis())
            val cachedTrack = existing?.toTrack()?.copy(
                localUri = cachedFile.absolutePath,
                isStream = true,
                thumbnailUri = if (song.thumbnail.isNotBlank()) song.thumbnail else existing.thumbnailUri
            ) ?: Track(
                uuid = uuid,
                title = song.title,
                artist = song.artist,
                thumbnailUri = if (song.thumbnail.isNotBlank()) song.thumbnail else null,
                durationSec = durationSec,
                localUri = cachedFile.absolutePath,
                isStream = true,
                spotifyId = song.spotifyId,
                albumSpotifyId = song.albumSpotifyId,
                artistSpotifyIds = song.artistSpotifyIds
            )
            return cachedTrack
        }

        val resolveStarted = android.os.SystemClock.elapsedRealtime()
        var queuedSpotmateTaskId: String? = null
        val songKey = RecommenderApi.songKey(song.title, song.artist)
        var resolvedSource = Source.SPOTSAVER
        fun trackFromLocalFile(localPath: String) = Track(
            uuid = uuid, title = song.title, artist = song.artist,
            thumbnailUri = song.thumbnail.takeIf { it.isNotBlank() },
            durationSec = durationSec, localUri = localPath, isStream = true,
            isFavourite = existing?.isFavourite ?: false,
            playCount = existing?.playCount ?: 0,
            lastPlayedAt = existing?.lastPlayedAt,
            downloadedAt = existing?.downloadedAt ?: System.currentTimeMillis(),
            spotifyId = song.spotifyId, albumSpotifyId = song.albumSpotifyId,
            artistSpotifyIds = song.artistSpotifyIds
        )
        val resolvedRequest = try {
            if (sourceMemory.avoided(songKey).isNotEmpty() || sourceMemory.preferred != null) {
                // The user rejected a source for this song: go through the providers one at a time,
                // rejected ones last, instead of racing them.
                var found: SpotifyApi.DirectDownloadRequest? = null
                val failures = mutableListOf<String>()
                for (source in sourceMemory.order(songKey, gamepvzFirst = true)) {
                    try {
                        found = withTimeout(
                            when (source) {
                                Source.SPOTSAVER -> 8_000L
                                Source.BACKEND -> BACKEND_STREAM_TIMEOUT_MS
                                else -> 15_000L
                            }
                        ) {
                            requestFor(source, song, live = true) { queuedSpotmateTaskId = it }
                        }
                        resolvedSource = source
                        break
                    } catch (e: Exception) {
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                        failures += "$source=${e.message}"
                    }
                }
                found ?: throw Exception("Stream unavailable: ${failures.joinToString(", ")}")
            } else (if (AlexaBackendApi.isConfigured) {
                // The backend is the first source: the UI shows "preparing" while it finishes the file.
                try {
                    withTimeout(BACKEND_STREAM_TIMEOUT_MS) { requestFor(Source.BACKEND, song, live = true) }
                        .also { resolvedSource = Source.BACKEND }
                } catch (e: Exception) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    Log.w(TAG, "Backend stream lookup failed (${e.message}); falling back to Spotsaver")
                    null
                }
            } else null) ?: preferNewProvider(
                primary = {
                    withTimeout(8_000L) { SpotsaverApi.getDownloadRequest(song.title, song.artist, SpotifyApi.parseDuration(song.duration)) }
                        .also { resolvedSource = Source.SPOTSAVER }
                },
                fallback = {
                    resolveStreamUrl(
                        primary = {
                            val request = SpotifyApi.getGamepvzDownloadRequest(song.url)
                            resolvedSource = Source.GAMEPVZ
                            request.copy(url = com.example.juke.network.gamepvzStreamUrl(request.url))
                        },
                        fallback = {
                            try {
                                SpotifyApi.getSpotmateDownloadRequest(song.url).also { resolvedSource = Source.SPOTMATE }
                            } catch (e: SpotifyApi.SpotmateQueuedException) {
                                queuedSpotmateTaskId = e.taskId
                                throw e
                            }
                        }
                    )
                }
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (queuedSpotmateTaskId.isNullOrBlank()) throw e
            // Preserve the existing queued-conversion recovery when neither URL is ready.
            return trackFromLocalFile(resolveStreamToLocalFile(song, uuid, forceSpotmateFirst))
        }
        // yt-dlp never serves previews, and a HEAD on the backend's live URL would start a second
        // download there, so the backend skips this check (its result is length-checked anyway).
        if (resolvedSource != Source.BACKEND && looksLikePreview(resolvedRequest, durationSec)) {
            // This source serves a ~30 s preview: remember that, and fetch a verified full file from the
            // remaining sources instead of streaming the clip.
            Log.w(TAG, "$resolvedSource returned a preview-sized file for '${song.title}'; switching source")
            sourceMemory.avoid(songKey, resolvedSource)
            return trackFromLocalFile(resolveStreamToLocalFile(song, uuid, forceSpotmateFirst))
        }
        Log.d(TAG, "Stream URL resolved in ${android.os.SystemClock.elapsedRealtime() - resolveStarted}ms via $resolvedSource")
        sourceMemory.recordUsed(uuid, resolvedSource)

        // Build a Track with the HTTP URL as localUri — ExoPlayer's CacheDataSource
        // will stream it directly from the network while caching chunks in its 256 MB
        // SimpleCache. Playback starts as soon as the first ~1.5s of audio is buffered.
        val httpTrack = Track(
            uuid = uuid,
            title = song.title,
            artist = song.artist,
            thumbnailUri = if (song.thumbnail.isNotBlank()) song.thumbnail else null,
            durationSec = durationSec,
            localUri = resolvedRequest.url,   // <-- HTTP URL, not a file path
            isStream = true,
            isFavourite = existing?.isFavourite ?: false,
            playCount = existing?.playCount ?: 0,
            lastPlayedAt = existing?.lastPlayedAt,
            downloadedAt = existing?.downloadedAt ?: System.currentTimeMillis(),
            spotifyId = song.spotifyId,
            albumSpotifyId = song.albumSpotifyId,
            artistSpotifyIds = song.artistSpotifyIds,
            syncedLyrics = existing?.syncedLyrics,
            plainLyrics = existing?.plainLyrics,
            lyricsOffsetMs = existing?.lyricsOffsetMs ?: 0L
        )

        // Background: download the full file to stream_files/ for LRU persistence.
        // When done, call back so the caller can swap ExoPlayer's source to the local file.
        val bgScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        bgScope.launch {
            try {
                // Give the player's initial HTTP connection exclusive access to this URL.
                withTimeout(60_000L) { awaitPlaybackStarted(uuid) }
                Log.d(TAG, "streamTrackInstant: background download started for '${song.title}'")
                if (!streamDir.exists()) streamDir.mkdirs()
                val finalFile = File(streamDir, "${uuid}_stream.mp3")
                val tempFile = File(streamDir, "${uuid}_stream.tmp")

                // A legacy `/audio/` live URL has no length; ask for the finished file instead (the
                // server waits for the stream that is already running, so yt-dlp doesn't run twice).
                // `/v1` audio URLs are finished files already.
                val bgRequest = if (resolvedSource == Source.BACKEND && "/audio/?" in resolvedRequest.url &&
                    "wait=1" !in resolvedRequest.url
                ) {
                    resolvedRequest.copy(url = resolvedRequest.url + "&wait=1", probeRanges = true)
                } else resolvedRequest
                withTimeout(120_000L) {
                    FastDownloader.downloadSegmented(
                        url = bgRequest.url,
                        outputFile = tempFile,
                        headers = bgRequest.headers,
                        threads = 4,
                        probeRanges = bgRequest.probeRanges
                    )
                }

                if (!tempFile.exists() || tempFile.length() < 100_000L || !isValidAudioHeader(tempFile)) {
                    Log.w(TAG, "streamTrackInstant: background download invalid or too small, discarding")
                    tempFile.delete()
                    return@launch
                }
                try {
                    verifyLength(tempFile, durationSec, lenient = true)
                } catch (e: WrongLengthException) {
                    Log.w(TAG, "streamTrackInstant: ${e.message} from $resolvedSource, discarding and avoiding it")
                    tempFile.delete()
                    sourceMemory.avoid(songKey, resolvedSource)
                    return@launch
                }

                if (finalFile.exists()) finalFile.delete()
                val moved = tempFile.renameTo(finalFile)
                if (!moved) {
                    tempFile.copyTo(finalFile, overwrite = true)
                    tempFile.delete()
                }

                if (!finalFile.exists() || finalFile.length() <= 0L) {
                    Log.w(TAG, "streamTrackInstant: failed to persist background download")
                    return@launch
                }

                Log.d(TAG, "streamTrackInstant: background download complete for '${song.title}' (${finalFile.length() / 1024}KB)")

                // Evict old stream files now that we have a new one
                evictStreamCache(pinnedUuids = pinnedUuids + uuid)

                val localTrack = httpTrack.copy(localUri = finalFile.absolutePath)

                // Notify caller on Main so they can swap ExoPlayer source
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onLocalFileReady(localTrack)
                }
            } catch (e: Exception) {
                Log.w(TAG, "streamTrackInstant: background download failed for '${song.title}': ${e.message}")
                // Non-fatal — ExoPlayer continues streaming from the HTTP URL via its cache
            }
        }

        return httpTrack
    }

    /**
     * The audio for [track] is the wrong song: reject the source that produced it and fetch the song
     * again from another one. Rejected sources are remembered, so later pulls of this song (re-stream,
     * re-download, queue revalidation) skip them too. Streams are refetched as streams and permanent
     * downloads as downloads; the track keeps its uuid, lyrics, favourite flag and play stats.
     */
    suspend fun refetchTrack(track: Track): Track {
        val spotifyId = track.spotifyId ?: throw Exception("Cannot refetch: missing Spotify info")
        val songKey = RecommenderApi.songKey(track.title, track.artist)
        // Without a record, assume the first source in the default order produced it.
        sourceMemory.avoid(songKey, sourceMemory.lastUsed(track.uuid) ?: sourceMemory.available.first())

        val song = SpotdownSong(
            title = track.title,
            artist = track.artist,
            thumbnail = track.thumbnailUri ?: "",
            url = "https://open.spotify.com/track/$spotifyId",
            duration = "${track.durationSec / 60}:${"%02d".format(track.durationSec % 60)}",
            spotifyId = spotifyId,
            albumSpotifyId = track.albumSpotifyId,
            artistSpotifyIds = track.artistSpotifyIds
        )

        // Set the bad audio aside (not deleted) so no cached/"already downloaded" shortcut hands it
        // straight back, and so it can be restored if every other source fails too.
        val dir = if (track.isStream) "stream_files" else "music"
        val name = if (track.isStream) "${track.uuid}_stream.mp3" else "${track.uuid}.mp3"
        val current = File(context.filesDir, "$dir/$name")
        val aside = File(context.filesDir, "$dir/$name.bak")
        aside.delete()
        if (current.exists()) current.renameTo(aside)
        return try {
            val fresh = if (track.isStream) {
                streamTrack(song, preferredUuid = track.uuid, fetchLyricsSynchronously = false)
            } else {
                trackDao.insertTrack(track.copy(localUri = null).toEntity())
                smartDownloadAndIndex(song)
            }
            aside.delete()
            fresh
        } catch (e: Exception) {
            if (aside.exists()) {
                current.delete()
                aside.renameTo(current)
            }
            if (!track.isStream) trackDao.insertTrack(track.toEntity())
            throw e
        }
    }

    /**
     * Stream a track to a local file and return a playable Track object.
     *
     * @param song Source song metadata
     * @param preferredUuid Reuse an existing UUID (e.g. for queue re-validation)
     * @param forceSpotmateFirst If true, Spotmate is tried first (faster for instant search plays).
     *                           Otherwise each call randomly picks primary/fallback (50/50).
     * @param pinnedUuids UUIDs that must NOT be evicted by LRU (e.g., the active queue's tracks).
     * @param fetchLyricsSynchronously If true, lyrics are fetched on the critical path.
     *                                 Set false for instant playback and hydrate lyrics later.
     * @param fetchYtVideoIdSynchronously If true, YT video ID is fetched on the critical path.
     *                                    Set false for instant playback and hydrate later.
     */
    suspend fun streamTrack(
        song: SpotdownSong,
        preferredUuid: String? = null,
        forceSpotmateFirst: Boolean = false,
        pinnedUuids: Set<String> = emptySet(),
        fetchLyricsSynchronously: Boolean = true,
        fetchYtVideoIdSynchronously: Boolean = true
    ): Track {
        val durationSec = SpotifyApi.parseDuration(song.duration)

        // Check if track already exists as a PERMANENT download in the database
        val existingByUuid = preferredUuid?.let { trackDao.getTrackByUuid(it) }
        val candidates = trackDao.findTracksByTitleAndDuration(song.title, durationSec)
        val existing = existingByUuid ?: candidates.find {
            com.example.juke.utils.ArtistUtils.areArtistsEqual(it.artist, song.artist)
        }

        // If it's already a permanent download, return it directly (no streaming needed)
        if (existing != null && !existing.isStream && existing.localUri != null && !existing.localUri.startsWith(
                "http"
            )
        ) {
            return existing.toTrack()
        }

        val uuid = preferredUuid ?: existing?.uuid ?: generateUUID()

        // Check if we already have a healthy stream file on disk (from LRU cache)
        val streamDir = File(context.filesDir, "stream_files")
        val cachedFile = File(streamDir, "${uuid}_stream.mp3")
        if (cachedFile.exists() && isHealthyExistingStreamFile(
                cachedFile.absolutePath,
                durationSec
            )
        ) {
            Log.d(TAG, "Reusing cached stream file for '${song.title}'")
            // Touch file to mark as recently used for LRU
            cachedFile.setLastModified(System.currentTimeMillis())

            return existing?.toTrack()?.copy(
                thumbnailUri = if (song.thumbnail.isNotBlank()) song.thumbnail else existing.thumbnailUri,
                durationSec = durationSec,
                localUri = cachedFile.absolutePath,
                isStream = true,
                spotifyId = song.spotifyId ?: existing.spotifyId,
                albumSpotifyId = song.albumSpotifyId ?: existing.albumSpotifyId,
                artistSpotifyIds = song.artistSpotifyIds ?: existing.artistSpotifyIds
            ) ?: Track(
                uuid = uuid,
                title = song.title,
                artist = song.artist,
                thumbnailUri = if (song.thumbnail.isNotBlank()) song.thumbnail else null,
                durationSec = durationSec,
                localUri = cachedFile.absolutePath,
                isStream = true,
                spotifyId = song.spotifyId,
                albumSpotifyId = song.albumSpotifyId,
                artistSpotifyIds = song.artistSpotifyIds
            )
        }

        // Resolve stream to local file (downloads the audio data)
        val localFilePath = try {
            resolveStreamToLocalFile(song, uuid, forceSpotmateFirst)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prepare stream file", e)
            throw e
        }

        // Evict old stream files to keep cache bounded.
        // Pinned UUIDs (currently queued tracks) are protected from eviction.
        evictStreamCache(pinnedUuids = pinnedUuids)

        val lyricsResult = if (fetchLyricsSynchronously) {
            SpotifyApi.searchLyrics(song.title, song.artist, song.album, durationSec)
        } else {
            Log.d(TAG, "Skipping synchronous lyrics fetch for instant stream: ${song.title}")
            null
        }
        val ytVideoId = if (fetchYtVideoIdSynchronously) {
            RecommenderApi.getBestVideoMatch("${song.title} ${song.artist}", SpotifyApi.parseDuration(song.duration), song.artist)
        } else {
            Log.d(TAG, "Skipping synchronous YT video ID fetch for instant stream: ${song.title}")
            existing?.ytVideoId
        }

        val syncedLyrics = lyricsResult?.syncedLyrics ?: existing?.syncedLyrics
        val plainLyrics = lyricsResult?.plainLyrics ?: existing?.plainLyrics

        val track = Track(
            uuid = uuid,
            title = song.title,
            artist = song.artist,
            thumbnailUri = if (song.thumbnail.isNotBlank()) song.thumbnail else null,
            durationSec = durationSec,
            localUri = localFilePath,
            ytVideoId = ytVideoId,
            isFavourite = existing?.isFavourite ?: false,
            playCount = existing?.playCount ?: 0,
            lastPlayedAt = existing?.lastPlayedAt,
            downloadedAt = existing?.downloadedAt ?: System.currentTimeMillis(),
            spotifyId = song.spotifyId,
            albumSpotifyId = song.albumSpotifyId,
            artistSpotifyIds = song.artistSpotifyIds,
            isStream = true,
            lyricsOffsetMs = existing?.lyricsOffsetMs ?: 0L
        ).withUpdatedLyrics(
            syncedLyrics = syncedLyrics,
            plainLyrics = plainLyrics
        )

        // Stream tracks are NOT inserted into DB here — the CALLER is responsible
        // for persisting via trackDao.insertTrack() when the track should survive
        // a restart (e.g. queue retention). promoteStreamToDownload() upgrades
        // a stream entry to a permanent local download.
        Log.d(TAG, "Stream track prepared (caller must persist to DB): ${track.title}")
        return track
    }

    suspend fun promoteStreamToDownload(track: Track): Track {
        if (!track.isStream) return track

        Log.d(TAG, "Promoting stream to permanent download: ${track.title}")

        val musicDir = File(context.filesDir, "music")
        if (!musicDir.exists()) musicDir.mkdirs()

        val permanentFile = File(musicDir, "${track.uuid}.mp3")

        // Try to move the existing stream file locally instead of re-fetching
        val streamFileUsable = track.localUri?.let { uri ->
            val streamFile = File(uri)
            if (streamFile.exists() && streamFile.length() > 100_000 && isValidAudioHeader(streamFile)) {
                try {
                    // Copy to music dir (copy+delete is safer than rename across dirs)
                    streamFile.copyTo(permanentFile, overwrite = true)
                    // COMMENT OUT THIS LINE to prevent playback crashes:
                    // streamFile.delete()
                    Log.d(
                        TAG,
                        "Moved stream file to permanent storage: ${permanentFile.absolutePath}"
                    )
                    true
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to move stream file locally: ${e.message}")
                    false
                }
            } else {
                Log.w(TAG, "Stream file missing or too small, will re-download")
                false
            }
        } ?: false

        // Fall back to full network download only if moving failed
        if (!streamFileUsable) {
            Log.d(TAG, "Falling back to network download for: ${track.title}")
            val spotifyUrl =
                if (track.spotifyId != null) "https://open.spotify.com/track/${track.spotifyId}" else null
            if (spotifyUrl == null) throw Exception("Cannot download: Missing Spotify info")

            val song = SpotdownSong(
                title = track.title,
                artist = track.artist,
                thumbnail = track.thumbnailUri ?: "",
                url = spotifyUrl,
                duration = "${track.durationSec / 60}:${"%02d".format(track.durationSec % 60)}",
                spotifyId = track.spotifyId,
                albumSpotifyId = track.albumSpotifyId,
                artistSpotifyIds = track.artistSpotifyIds
            )
            return smartDownloadAndIndex(song)
        }

        // Stream file moved successfully — persist thumbnail locally & update DB record
        var localThumbnailUri = track.thumbnailUri
        if (!localThumbnailUri.isNullOrBlank() && localThumbnailUri.startsWith("http")) {
            try {
                val thumbnailFile = File(musicDir, "${track.uuid}_thumb.jpg")
                val imageBytes: ByteArray =
                    retryWithBackoff(maxRetries = 3, operationName = "download thumbnail") {
                        ApiClient.httpClient.get(localThumbnailUri!!).body()
                    }
                if (imageBytes.isNotEmpty()) {
                    thumbnailFile.writeBytes(imageBytes)
                    localThumbnailUri = thumbnailFile.absolutePath
                    Log.d(TAG, "Thumbnail saved locally: ${thumbnailFile.absolutePath}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Thumbnail download failed, keeping URL: ${e.message}")
            }
        }

        // Fetch lyrics if not already present
        var syncedLyrics = track.syncedLyrics
        var plainLyrics = track.plainLyrics
        if (syncedLyrics == null || plainLyrics == null) {
            try {
                val lyricsResult =
                    SpotifyApi.searchLyrics(track.title, track.artist, "", track.durationSec)
                if (syncedLyrics == null) syncedLyrics = lyricsResult?.syncedLyrics
                if (plainLyrics == null) plainLyrics = lyricsResult?.plainLyrics
            } catch (_: Exception) {
            }
        }

        val promotedTrack = Track(
            uuid = track.uuid,
            title = track.title,
            artist = track.artist,
            thumbnailUri = localThumbnailUri,
            durationSec = track.durationSec,
            localUri = permanentFile.absolutePath,
            ytVideoId = track.ytVideoId
                ?: RecommenderApi.getBestVideoMatch("${track.title} ${track.artist}", track.durationSec, track.artist),
            isFavourite = track.isFavourite,
            playCount = track.playCount,
            lastPlayedAt = track.lastPlayedAt,
            downloadedAt = System.currentTimeMillis(),
            spotifyId = track.spotifyId,
            albumSpotifyId = track.albumSpotifyId,
            artistSpotifyIds = track.artistSpotifyIds,
            isStream = false,
            lyricsOffsetMs = track.lyricsOffsetMs
        ).withUpdatedLyrics(
            syncedLyrics = syncedLyrics,
            plainLyrics = plainLyrics
        )

        trackDao.insertTrack(promotedTrack.toEntity())
        Log.d(TAG, "Stream promoted to permanent download (local move): ${track.title}")
        return promotedTrack
    }

    suspend fun deleteTrackAndFiles(track: Track) {
        deleteTracksAndFiles(listOf(track))
    }

    /** Collect affected playlists before removing memberships; update counts in the same transaction. */
    suspend fun deleteTracksAndFiles(tracks: List<Track>, reportErrors: Boolean = false) {
        if (tracks.isEmpty()) return
        try {
            val uniqueTracks = tracks.distinctBy { it.uuid }
            val trackUuids = uniqueTracks.map { it.uuid }
            // Resolve imported file:// URIs as well as the absolute paths used by downloads.
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                uniqueTracks.forEach { track ->
                    listOfNotNull(track.localUri, track.thumbnailUri).forEach { uri ->
                        val file = when {
                            uri.startsWith("file:") -> File(java.net.URI(uri))
                            uri.startsWith("/") -> File(uri)
                            else -> null // Remote/content resources are not owned files.
                        }
                        if (file != null && file.exists() && !file.delete()) {
                            throw java.io.IOException("Could not remove a downloaded file")
                        }
                    }
                }
            }
            database.withTransaction {
                val playlistDao = database.playlistDao()
                val affected = uniqueTracks.flatMap { playlistDao.getPlaylistsForTrack(it.uuid) }
                    .map { it.id }.toSet()
                playlistDao.deletePlaylistTracksForTracks(trackUuids)
                trackDao.deleteTracks(trackUuids)
                affected.forEach { id ->
                    playlistDao.updatePlaylistTrackCount(id, playlistDao.getPlaylistTrackCount(id))
                }
            }
            val playbackManager = PlaybackManager.getInstance(context)
            val queueManager = QueueManager.getInstance(context)
            trackUuids.forEach { uuid ->
                playbackManager.removeDeletedTrackFromQueue(uuid)
                // The recommendation queue also feeds the player; stale entries could reappear later.
                if (queueManager.currentQueue.value.any { it.uuid == uuid }) queueManager.removeFromQueue(uuid)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting tracks: ${e.message}", e)
            if (reportErrors) throw e
        }
    }

    /**
     * Clean up old cache files that are no longer referenced in database.
     * Call this periodically (e.g., on app startup) to keep cache under control.
     * 
     * @param maxAgeDays Files older than this many days will be deleted (default 7)
     * @return Pair of (filesDeleted, bytesFreed)
     */
    suspend fun cleanupOrphanedCacheFiles(maxAgeDays: Int = 7): Pair<Int, Long> {        val musicDir = File(context.filesDir, "music")
        if (!musicDir.exists()) {
            Log.d(TAG, "Music directory doesn't exist, nothing to clean")
            return Pair(0, 0L)
        }

        val allTracks = trackDao.getAllTracks()
        val validUris = allTracks.mapNotNull { it.localUri }.toSet()
        val validThumbnails = allTracks.mapNotNull { it.thumbnailUri }.toSet()

        val cutoffTime = System.currentTimeMillis() - (maxAgeDays * 24 * 60 * 60 * 1000L)
        var filesDeleted = 0
        var bytesFreed = 0L

        musicDir.listFiles()?.forEach { file ->
            val absolutePath = file.absolutePath

            // Check if this file is referenced in database
            val isOrphaned = !validUris.contains(absolutePath) &&
                    !validThumbnails.contains(absolutePath)

            // Only delete files that are NOT in the database AND are old.
            // BUG FIX: The previous logic deleted ANY file older than maxAgeDays,
            // including DB-referenced stream files for favourite tracks. Now we only
            // use age as a secondary guard for truly orphaned files (e.g. from a
            // crash mid-write). DB-referenced files are lifetime-managed explicitly.
            val isOld = file.lastModified() < cutoffTime

            if (isOrphaned && isOld) {
                val size = file.length()
                if (file.delete()) {
                    filesDeleted++
                    bytesFreed += size
                    Log.d(
                        TAG,
                        "Deleted orphaned cache file: ${file.name} (${size} bytes, ${maxAgeDays}d+ old)"
                    )
                } else {
                    Log.w(TAG, "Failed to delete orphaned cache file: ${file.name}")
                }
            }
        }

        Log.d(
            TAG,
            "Cache cleanup complete: $filesDeleted files deleted, ${bytesFreed / 1024 / 1024}MB freed"
        )
        return Pair(filesDeleted, bytesFreed)
    }

    /**
     * Get current cache size in bytes.
     */
    fun getCacheSizeBytes(): Long {
        val musicDir = File(context.filesDir, "music")
        if (!musicDir.exists()) return 0L

        return musicDir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    /**
     * LRU eviction for the stream_files directory.
     * Keeps at most [maxFiles] stream files. When the limit is exceeded,
     * the oldest files (by lastModified) are deleted first.
     *
     * Files whose UUID is in [pinnedUuids] are NEVER deleted, even if over the limit.
     * This prevents ExoPlayer ENOENT errors when a queued track's file is evicted
     * just before playback.
     */
    fun evictStreamCache(maxFiles: Int = 30, pinnedUuids: Set<String> = emptySet()) {
        try {
            val streamDir = File(context.filesDir, "stream_files")
            if (!streamDir.exists()) return

            val files =
                streamDir.listFiles()?.filter { it.isFile && it.name.endsWith("_stream.mp3") }
                    ?: return

            if (files.size <= maxFiles) return

            // Sort by lastModified ascending (oldest first), but never evict pinned files
            val (pinned, evictable) = files.partition { file ->
                val uuid = file.name.removeSuffix("_stream.mp3")
                pinnedUuids.contains(uuid)
            }

            val sorted = evictable.sortedBy { it.lastModified() }
            // Protect pinned files: only evict from the evictable pool
            val overLimit = (files.size - pinned.size) - maxFiles
            if (overLimit <= 0) return

            val toDelete = sorted.take(overLimit)

            var bytesFreed = 0L
            toDelete.forEach { file ->
                val size = file.length()
                if (file.delete()) {
                    bytesFreed += size
                    Log.d(TAG, "LRU evicted stream file: ${file.name} (${size / 1024}KB)")
                }
            }
            Log.d(
                TAG,
                "Stream cache eviction: removed ${toDelete.size} files, freed ${bytesFreed / 1024 / 1024}MB "
                    + "(${pinned.size} pinned, ${evictable.size - toDelete.size} kept)"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error during stream cache eviction: ${e.message}", e)
        }
    }

    /**
     * Get current stream cache size in bytes.
     */
    fun getStreamCacheSizeBytes(): Long {
        val streamDir = File(context.filesDir, "stream_files")
        if (!streamDir.exists()) return 0L
        return streamDir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    /**
     * One-time cleanup: Remove stale stream entries from the database.
     * Preserves stream tracks that are part of the saved playback queue
     * so they survive app restarts.
     *
     * @param preserveUuids UUIDs of tracks in the saved queue that should NOT be purged
     */
    suspend fun purgeStaleStreamEntries(preserveUuids: Set<String> = emptySet()): Int {
        val allStreams = trackDao.getStreamTracks()
        val toDelete = allStreams.filter { it.uuid !in preserveUuids }
        if (toDelete.isNotEmpty()) {
            trackDao.deleteTracks(toDelete.map { it.uuid })
            Log.d(
                TAG,
                "Purged ${toDelete.size} stale stream entries (preserved ${allStreams.size - toDelete.size} queue tracks)"
            )
        }
        return toDelete.size
    }
}
