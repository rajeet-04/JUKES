package android.content

import android.net.Uri
import java.io.Closeable
import java.io.File
import java.io.InputStream

/** Desktop "content" is just files: URIs carry a file path. */
class ContentResolver {
    private fun file(u: Uri) = File(java.net.URI(u.toString()))
    fun openInputStream(u: Uri): InputStream? = runCatching { file(u).inputStream() }.getOrNull()
    fun query(u: Uri, a: Array<String>?, b: String?, c: Array<String>?, d: String?): Cursor? = runCatching { Cursor(file(u).name) }.getOrNull()
}
class Cursor(private val name: String) : Closeable {
    fun getColumnIndex(c: String) = 0
    fun getColumnIndexOrThrow(c: String) = 0
    fun moveToFirst() = true
    fun getString(i: Int): String = name
    override fun close() {}
}
