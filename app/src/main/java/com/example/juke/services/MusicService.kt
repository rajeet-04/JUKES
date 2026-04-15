package com.example.juke.services

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import com.example.juke.database.MusicDatabase
import com.example.juke.database.toEntity
import com.example.juke.database.toTrack
import com.example.juke.models.SpotdownSong
import com.example.juke.models.Track
import com.example.juke.network.ApiClient
import com.example.juke.network.RecommenderApi
import com.example.juke.network.SpotifyApi
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.UUID
import kotlin.math.abs

/**
 * Music Service for downloading and indexing tracks.
 */
class MusicService(private val context: Context) {

    private val TAG = "MusicService"
    private val database = MusicDatabase.getDatabase(context)
    private val trackDao = database.trackDao()

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

        // For instant search plays: Spotmate first (faster). Otherwise 50/50 random.
        val useGamepvzFirst = if (forceSpotmateFirst) false
            else (System.currentTimeMillis() % 2L) == 0L
        val primaryName = if (useGamepvzFirst) "Gamepvz" else "Spotmate"
        val fallbackName = if (useGamepvzFirst) "Spotmate" else "Gamepvz"

        suspend fun streamPrimary() =
            if (useGamepvzFirst) SpotifyApi.downloadSongFromGamepvz(song.url)
            else SpotifyApi.downloadSongFromSpotmate(song.url)

        suspend fun streamFallback() =
            if (useGamepvzFirst) SpotifyApi.downloadSongFromSpotmate(song.url)
            else SpotifyApi.downloadSongFromGamepvz(song.url)

        val audioData = try {
            withTimeout(90_000L) { streamPrimary() }
        } catch (e: Exception) {
            Log.w(
                TAG,
                "$primaryName stream fetch failed (${e.message}), falling back to $fallbackName"
            )
            try {
                withTimeout(90_000L) { streamFallback() }
            } catch (fallbackEx: Exception) {
                Log.e(TAG, "Both stream sources failed: ${fallbackEx.message}")
                throw Exception("Stream unavailable: $primaryName=${e.message}, $fallbackName=${fallbackEx.message}")
            }
        }

        if (audioData.isEmpty() || audioData.size < 100_000) {
            throw Exception("Stream payload is too small")
        }

        tempFile.writeBytes(audioData)
        if (finalFile.exists()) {
            finalFile.delete()
        }

        val moved = tempFile.renameTo(finalFile)
        if (!moved) {
            finalFile.writeBytes(audioData)
            tempFile.delete()
        }

        if (!finalFile.exists() || finalFile.length() <= 0L) {
            throw Exception("Failed to persist stream file")
        }

        return finalFile.absolutePath
    }

    private fun isValidMp3Header(file: File): Boolean {
        if (!file.exists() || file.length() < 3L) return false
        return try {
            file.inputStream().use { input ->
                val header = ByteArray(3)
                val bytesRead = input.read(header)
                if (bytesRead < 3) return@use false

                val isID3 = header[0] == 0x49.toByte() &&
                        header[1] == 0x44.toByte() &&
                        header[2] == 0x33.toByte()

                val isMP3Frame = header[0] == 0xFF.toByte() &&
                        (header[1].toInt() and 0xE0) == 0xE0

                isID3 || isMP3Frame
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

        if (!isValidMp3Header(streamFile)) {
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
                return Track(
                    uuid = existingTrack.uuid,
                    title = existingTrack.title,
                    artist = existingTrack.artist,
                    thumbnailUri = existingTrack.thumbnailUri,
                    durationSec = existingTrack.durationSec,
                    localUri = existingTrack.localUri,
                    ytVideoId = existingTrack.ytVideoId,
                    syncedLyrics = existingTrack.syncedLyrics,
                    plainLyrics = existingTrack.plainLyrics,
                    isFavourite = existingTrack.isFavourite,
                    playCount = existingTrack.playCount,
                    lastPlayedAt = existingTrack.lastPlayedAt,
                    downloadedAt = existingTrack.downloadedAt,
                    spotifyId = existingTrack.spotifyId,
                    albumSpotifyId = existingTrack.albumSpotifyId,
                    artistSpotifyIds = existingTrack.artistSpotifyIds
                )
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

            // Try both sources in random order to balance load and provide automatic fallback.
            val useGamepvzFirst = (System.currentTimeMillis() % 2L) == 0L
            Log.d(
                TAG,
                "Downloading '${song.title}' — primary: ${if (useGamepvzFirst) "Gamepvz" else "Spotmate"}"
            )

            suspend fun trySource(useGamepvz: Boolean): ByteArray {
                val data = if (useGamepvz) {
                    SpotifyApi.downloadSongFromGamepvz(song.url)
                } else {
                    SpotifyApi.downloadSongFromSpotmate(song.url)
                }
                if (data.isEmpty() || data.size < 100_000) {
                    throw Exception("Downloaded file is too small to be a valid MP3")
                }
                return data
            }

            var usedGamepvzFirst = useGamepvzFirst
            val audioData = try {
                withTimeout(90_000L) { trySource(useGamepvzFirst) }
            } catch (e: Exception) {
                val fallbackName = if (useGamepvzFirst) "Spotmate" else "Gamepvz"
                Log.w(TAG, "Primary download failed (${e.message}), falling back to $fallbackName")
                usedGamepvzFirst = !useGamepvzFirst
                withTimeout(90_000L) { trySource(!useGamepvzFirst) }
            }

            audioFile.writeBytes(audioData)
            Log.d(TAG, "Downloaded ${audioData.size} bytes — wrote to ${audioFile.absolutePath}")

            if (!audioFile.exists() || audioFile.length() == 0L) {
                throw Exception("Failed to write audio file")
            }

            // Verify duration
            try {
                val mmr = MediaMetadataRetriever()
                mmr.setDataSource(audioFile.absolutePath)
                val durationStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                val fileDurationSec = (durationStr?.toLongOrNull() ?: 0L) / 1000
                mmr.release()

                Log.d(
                    TAG,
                    "Downloaded file duration: ${fileDurationSec}s, Expected: ${durationSec}s"
                )

                if (abs(fileDurationSec - durationSec) > 5) {
                    val altName = if (usedGamepvzFirst) "Spotmate" else "Gamepvz"
                    Log.w(
                        TAG,
                        "Duration mismatch! Expected ${durationSec}s, got ${fileDurationSec}s. Retrying with $altName"
                    )
                    try {
                        val altData = withTimeout(90_000L) {
                            if (usedGamepvzFirst) SpotifyApi.downloadSongFromSpotmate(song.url)
                            else SpotifyApi.downloadSongFromGamepvz(song.url)
                        }
                        audioFile.writeBytes(altData)
                        Log.d(TAG, "Alternative download succeeded, overwrote file.")
                    } catch (retryEx: Exception) {
                        Log.w(TAG, "Alternative also failed (${retryEx.message}), keeping original")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error verifying duration: ${e.message}")
            }

            val lyricsResult =
                SpotifyApi.searchLyrics(song.title, song.artist, song.album, durationSec)
            val ytVideoId = RecommenderApi.getBestVideoMatch("${song.title} ${song.artist}")

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
                syncedLyrics = lyricsResult?.syncedLyrics ?: existingTrack?.syncedLyrics,
                plainLyrics = lyricsResult?.plainLyrics ?: existingTrack?.plainLyrics,
                isFavourite = existingTrack?.isFavourite ?: false,
                playCount = existingTrack?.playCount ?: 0,
                lastPlayedAt = existingTrack?.lastPlayedAt,
                downloadedAt = System.currentTimeMillis(),
                spotifyId = song.spotifyId,
                albumSpotifyId = song.albumSpotifyId,
                artistSpotifyIds = song.artistSpotifyIds,
                isStream = false
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

            return Track(
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
            RecommenderApi.getBestVideoMatch("${song.title} ${song.artist}")
        } else {
            Log.d(TAG, "Skipping synchronous YT video ID fetch for instant stream: ${song.title}")
            existing?.ytVideoId
        }

        val track = Track(
            uuid = uuid,
            title = song.title,
            artist = song.artist,
            thumbnailUri = if (song.thumbnail.isNotBlank()) song.thumbnail else null,
            durationSec = durationSec,
            localUri = localFilePath,
            ytVideoId = ytVideoId,
            syncedLyrics = lyricsResult?.syncedLyrics ?: existing?.syncedLyrics,
            plainLyrics = lyricsResult?.plainLyrics ?: existing?.plainLyrics,
            isFavourite = false,
            playCount = 0,
            lastPlayedAt = null,
            downloadedAt = System.currentTimeMillis(),
            spotifyId = song.spotifyId,
            albumSpotifyId = song.albumSpotifyId,
            artistSpotifyIds = song.artistSpotifyIds,
            isStream = true,
            lyricsOffsetMs = 0L
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
            if (streamFile.exists() && streamFile.length() > 100_000 && isValidMp3Header(streamFile)) {
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
                ?: RecommenderApi.getBestVideoMatch("${track.title} ${track.artist}"),
            syncedLyrics = syncedLyrics,
            plainLyrics = plainLyrics,
            isFavourite = track.isFavourite,
            playCount = track.playCount,
            lastPlayedAt = track.lastPlayedAt,
            downloadedAt = System.currentTimeMillis(),
            spotifyId = track.spotifyId,
            albumSpotifyId = track.albumSpotifyId,
            artistSpotifyIds = track.artistSpotifyIds,
            isStream = false,
            lyricsOffsetMs = track.lyricsOffsetMs
        )

        trackDao.insertTrack(promotedTrack.toEntity())
        Log.d(TAG, "Stream promoted to permanent download (local move): ${track.title}")
        return promotedTrack
    }

    suspend fun deleteTrackAndFiles(track: Track) {
        try {
            // CRITICAL: Delete playlist_tracks entries FIRST to avoid FK constraint errors
            // This is more reliable than relying on ON DELETE CASCADE
            database.playlistDao().deletePlaylistTracksForTrack(track.uuid)
            Log.d(TAG, "Deleted playlist_tracks for track: ${track.uuid}")

            // Get playlists that contain this track before deleting (for updating counts)
            val playlistsToUpdate =
                database.playlistDao().getPlaylistsForTrack(track.uuid).map { it.id }

            track.localUri?.let { uri ->
                if (!uri.startsWith("http")) {
                    File(uri).delete()
                }
            }

            track.thumbnailUri?.let { uri ->
                File(uri).delete()
            }

            trackDao.deleteTrack(track.uuid)

            // Update track counts for affected playlists
            playlistsToUpdate.forEach { playlistId ->
                val newCount = database.playlistDao().getPlaylistTrackCount(playlistId)
                database.playlistDao().updatePlaylistTrackCount(playlistId, newCount)
                Log.d(TAG, "Updated track count for playlist $playlistId to $newCount")
            }

            // SAFETY: Remove from playback queue if present
            try {
                val playbackManager = PlaybackManager.getInstance(context)
                playbackManager.removeDeletedTrackFromQueue(track.uuid)
                Log.d(TAG, "Removed deleted track from playback queue: ${track.title}")
            } catch (e: Exception) {
                Log.w(TAG, "Could not remove track from playback queue: ${e.message}")
            }

            Log.d(TAG, "Deleted track: ${track.title}")
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting track: ${e.message}", e)
        }
    }

    suspend fun deleteTracksAndFiles(tracks: List<Track>) {
        if (tracks.isEmpty()) return

        try {
            // CRITICAL: Delete playlist_tracks entries FIRST to avoid FK constraint errors
            val trackUuids = tracks.map { it.uuid }
            database.playlistDao().deletePlaylistTracksForTracks(trackUuids)
            Log.d(TAG, "Deleted playlist_tracks for ${tracks.size} tracks")

            // Delete files for all tracks
            tracks.forEach { track ->
                track.localUri?.let { uri ->
                    if (!uri.startsWith("http")) {
                        File(uri).delete()
                    }
                }
                track.thumbnailUri?.let { uri ->
                    File(uri).delete()
                }
            }

            // Collect all Playlist IDs involved
            val playlistDao = database.playlistDao()

            // Get all unique playlist IDs that contain ANY of these tracks
            val affectedPlaylistIds = mutableSetOf<String>()
            tracks.forEach { track ->
                try {
                    val playlists = playlistDao.getPlaylistsForTrack(track.uuid)
                    affectedPlaylistIds.addAll(playlists.map { it.id })
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching playlists for track ${track.uuid}", e)
                }
            }

            // Bulk delete from DB to prevent multiple invalidations and Cursor leaks
            trackDao.deleteTracks(trackUuids)
            Log.d(TAG, "Bulk deleted ${tracks.size} tracks from database")

            // Update track counts for affected playlists
            affectedPlaylistIds.forEach { playlistId ->
                val newCount = playlistDao.getPlaylistTrackCount(playlistId)
                playlistDao.updatePlaylistTrackCount(playlistId, newCount)
                Log.d(TAG, "Updated track count for playlist $playlistId to $newCount")
            }

            // SAFETY: Remove all deleted tracks from playback queue
            try {
                val playbackManager = PlaybackManager.getInstance(context)
                trackUuids.forEach { uuid ->
                    playbackManager.removeDeletedTrackFromQueue(uuid)
                }
                Log.d(TAG, "Removed ${trackUuids.size} deleted tracks from playback queue")
            } catch (e: Exception) {
                Log.w(TAG, "Could not remove tracks from playback queue: ${e.message}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in bulk delete: ${e.message}", e)
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
