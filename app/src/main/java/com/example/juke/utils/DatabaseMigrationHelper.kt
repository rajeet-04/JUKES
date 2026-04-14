package com.example.juke.utils

import android.content.Context
import android.util.Log
import com.example.juke.database.MusicDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object DatabaseMigrationHelper {
    private const val TAG = "DatabaseMigrationHelper"

    suspend fun fixDownloadTimestamps(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                val db = MusicDatabase.getDatabase(context)
                val trackDao = db.trackDao()

                val downloadedTracks = trackDao.getDownloadedTracks()
                var updatedCount = 0

                System.currentTimeMillis()

                for (track in downloadedTracks) {
                    val localUri = track.localUri ?: continue
                    val file = File(localUri)

                    if (file.exists()) {
                        val lastModified = file.lastModified()

                        // If the file timestamp is valid and significantly different from the current time
                        // (indicating it was downloaded earlier than this migration run), update it.
                        // Or if we just want to be accurate, always update it to file time.
                        // The migration set it to 'currentTime'. If file is older, we should use file time.

                        // We'll update if the difference is more than 1 second, just to avoid unnecessary writes
                        // if it just happened.
                        if (Math.abs(
                                track.downloadedAt?.minus(lastModified) ?: Long.MAX_VALUE
                            ) > 1000
                        ) {
                            val updatedTrack = track.copy(downloadedAt = lastModified)
                            trackDao.insertTrack(updatedTrack)
                            updatedCount++
                        }
                    }
                }

                if (updatedCount > 0) {
                    Log.d(TAG, "Fixed timestamps for $updatedCount downloaded tracks")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fixing download timestamps", e)
            }
        }
    }
}
