package com.example.juke.services

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.example.juke.database.MusicDatabase
import com.example.juke.database.toTrack
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileNotFoundException

/**
 * Read-only cover art for Android Auto: `content://<package>.artwork/<track uuid>`. Auto runs in
 * another process and can't open this app's files, so it asks here. Only the thumbnail recorded
 * for a track in our own database is served.
 */
class ArtworkProvider : ContentProvider() {
    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("read-only")
        val uuid = uri.lastPathSegment ?: throw FileNotFoundException()
        val ctx = context ?: throw FileNotFoundException()
        val thumb = runBlocking { MusicDatabase.getDatabase(ctx).trackDao().getTrackByUuid(uuid)?.toTrack()?.thumbnailUri }
        val file = LocalAudio.fileFor(thumb) ?: thumb?.let { File(it) }
        if (file == null || !file.isFile) throw FileNotFoundException()
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = "image/*"
    override fun query(uri: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<String>?) = 0
}
