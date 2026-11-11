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
    // Bounded by bytes, not count: decoded covers are native memory (w*h*4 each), so 120 full-size ones were ~200 MB.
    private val mem = LinkedHashMap<String, ImageBitmap>(64, .75f, true)
    private var memBytes = 0L
    private fun put(k: String, b: ImageBitmap) = synchronized(mem) {
        mem.put(k, b)?.let { memBytes -= it.width.toLong() * it.height * 4 }
        memBytes += b.width.toLong() * b.height * 4
        val it = mem.entries.iterator()
        while (memBytes > MEM_BUDGET && mem.size > 1 && it.hasNext()) { val e = it.next(); memBytes -= e.value.width.toLong() * e.value.height * 4; it.remove() }
    }
    /** Covers are shown at most ~500 px wide; decode no bigger than [MAX_SIDE] (raster copy, so the encoded original is released). */
    private fun decode(bytes: ByteArray): ImageBitmap {
        val img = org.jetbrains.skia.Image.makeFromEncoded(bytes)
        val scale = minOf(1f, MAX_SIDE / maxOf(img.width, img.height))
        if (scale >= 1f) return img.toComposeImageBitmap()
        val w = (img.width * scale).toInt().coerceAtLeast(1); val h = (img.height * scale).toInt().coerceAtLeast(1)
        return org.jetbrains.skia.Surface.makeRasterN32Premul(w, h).use { surface ->
            surface.canvas.drawImageRect(img, org.jetbrains.skia.Rect.makeWH(img.width.toFloat(), img.height.toFloat()), org.jetbrains.skia.Rect.makeWH(w.toFloat(), h.toFloat()), org.jetbrains.skia.SamplingMode.MITCHELL, null, true)
            img.close()
            surface.makeImageSnapshot().toComposeImageBitmap()
        }
    }
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
                decode(bytes).also { put(k, it) }
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
    companion object { val shared = ImageLoader(); const val MEM_BUDGET = 48L shl 20; const val MAX_SIDE = 512f }
}

val Context.imageLoader: ImageLoader get() = ImageLoader.shared
object Coil { fun imageLoader(c: Context) = ImageLoader.shared }
