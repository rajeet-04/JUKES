package coil

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import coil.request.*
import com.example.juke.network.ApiClient
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

/** Coil-shaped image loader: memory LRU + on-disk byte cache, http(s)/file/content paths. */
class ImageLoader {
    private val mem = object : LinkedHashMap<String, ImageBitmap>(64, .75f, true) { override fun removeEldestEntry(e: MutableMap.MutableEntry<String, ImageBitmap>?) = size > 120 }
    private val disk by lazy { File(Context.dataDir, "cache/img").also { it.mkdirs() } }
    fun cached(key: String): ImageBitmap? = synchronized(mem) { mem[key] }

    suspend fun load(data: Any?, key: String? = null): ImageBitmap? {
        val src = when (data) { null -> return null; is java.io.File -> data.absolutePath; else -> data.toString() }
        val k = key ?: src
        cached(k)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val bytes = when {
                    src.startsWith("http") -> fetch(src)
                    src.startsWith("file:") -> File(java.net.URI(src)).readBytes()
                    else -> File(src).readBytes()
                }
                org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap().also { synchronized(mem) { mem[k] = it } }
            }.getOrNull()
        }
    }
    private fun fetch(url: String): ByteArray {
        val f = File(disk, MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) })
        if (f.isFile) return f.readBytes()
        ApiClient.okHttp.newCall(Request.Builder().url(url).build()).execute().use { r ->
            check(r.isSuccessful); return r.body!!.bytes().also { runCatching { f.writeBytes(it) } }
        }
    }
    suspend fun execute(r: ImageRequest): ImageResult =
        load(r.data, r.memoryCacheKey)?.let { SuccessResult(BitmapDrawable(Bitmap(it))) } ?: ErrorResult()
    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    fun enqueue(r: ImageRequest) { kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) { load(r.data, r.memoryCacheKey) } }
    companion object { val shared = ImageLoader() }
}

val Context.imageLoader: ImageLoader get() = ImageLoader.shared
object Coil { fun imageLoader(c: Context) = ImageLoader.shared }
