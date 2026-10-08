package com.example.juke.services

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/**
 * Reads HLS playlists (`.m3u8`) straight from the network and everything else through the cache.
 * A progressive playlist grows while the server downloads (no-store); a cached copy would freeze it
 * and the player would never see new segments or the end tag. Segments are immutable and still cached.
 */
@UnstableApi
class PlaylistBypassDataSource(
    private val cached: DataSource,
    private val direct: DataSource,
) : DataSource {
    private var active: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        cached.addTransferListener(transferListener)
        direct.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val source = if (isPlaylist(dataSpec.uri)) direct else cached
        active = source
        return source.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        checkNotNull(active) { "read before open" }.read(buffer, offset, length)

    override fun getUri(): Uri? = active?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders ?: emptyMap()

    override fun close() {
        try {
            active?.close()
        } finally {
            active = null
        }
    }

    class Factory(
        private val cached: DataSource.Factory,
        private val direct: DataSource.Factory,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            PlaylistBypassDataSource(cached.createDataSource(), direct.createDataSource())
    }

    companion object {
        fun isPlaylist(uri: Uri): Boolean = uri.path?.endsWith(".m3u8", ignoreCase = true) == true
    }
}
