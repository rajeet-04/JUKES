package com.example.juke.services

import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.example.juke.models.Track
import java.io.File

/** Item building and paging for the Android Auto browse tree (see PlaybackService). */
object AutoLibrary {
    const val ROOT = "root"
    const val RECENT = "recent"
    const val FAVORITES = "favorites"
    const val DOWNLOADS = "all_tracks"

    /** Auto shows a few items while driving; never send more than this per folder. */
    const val MAX_ITEMS = 100

    fun <T> pageOf(items: List<T>, page: Int, pageSize: Int): List<T> {
        val capped = items.take(MAX_ITEMS)
        if (pageSize <= 0) return capped
        return capped.drop(page.coerceAtLeast(0) * pageSize).take(pageSize)
    }

    /** Where a vanished stream/download will be written again; an ENOENT there triggers recovery. */
    fun expectedPath(track: Track): String =
        if (track.isStream) "stream_files/${track.uuid}_stream.mp3" else "music/${track.uuid}.mp3"

    fun folder(id: String, title: String): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                .build()
        )
        .build()

    /** Local art is served by [ArtworkProvider] because Auto can't read this app's files. */
    private fun artworkFor(packageName: String, track: Track): Uri? {
        val thumb = track.thumbnailUri?.takeIf { it.isNotBlank() } ?: return null
        return if (thumb.startsWith("http", ignoreCase = true)) thumb.toUri()
        else "content://$packageName.artwork/${Uri.encode(track.uuid)}".toUri()
    }

    private fun metadata(packageName: String, track: Track) =
        MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .apply { artworkFor(packageName, track)?.let { setArtworkUri(it) } }
            .build()

    /** A row in a folder: tapping it makes Auto ask for [playbackItem] by media id. */
    fun browseItem(packageName: String, track: Track): MediaItem = MediaItem.Builder()
        .setMediaId(track.uuid)
        .setMediaMetadata(metadata(packageName, track))
        .build()

    /**
     * What the player gets for [track]. A missing file is pointed at where it will be fetched
     * again (same trick as the queue restore); null when it can't be fetched at all.
     */
    fun playbackItem(packageName: String, filesDir: File, track: Track): MediaItem? {
        var t = track
        if (LocalAudio.isMissing(t.localUri)) {
            if (t.spotifyId == null) return null
            if (t.localUri == null) t = t.copy(localUri = File(filesDir, expectedPath(t)).absolutePath)
        }
        return MediaItem.Builder()
            .setMediaId(t.uuid)
            .setUri(t.localUri)
            .setMediaMetadata(metadata(packageName, t))
            .let { PlaybackService.applyStreamHints(it, t.localUri) }
            .build()
    }
}
