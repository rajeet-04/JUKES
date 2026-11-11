package android.media

import java.io.File
import java.io.RandomAccessFile

/** Duration/title probe for local files: MP4 (mvhd) exactly, MP3 by CBR estimate. */
class MediaMetadataRetriever {
    companion object { const val METADATA_KEY_DURATION = 9; const val METADATA_KEY_TITLE = 7; const val METADATA_KEY_ARTIST = 2; const val METADATA_KEY_ALBUM = 1 }
    private var file: File? = null
    fun setDataSource(path: String) { file = File(path).also { require(it.isFile) { "no file" } } }
    fun release() {}
    val embeddedPicture: ByteArray? get() = null // ponytail: imported tracks get no cover; parse ID3 APIC / MP4 covr if wanted
    fun extractMetadata(key: Int): String? = if (key == METADATA_KEY_DURATION) file?.let(::durationMs)?.toString() else file?.takeIf { key == METADATA_KEY_TITLE }?.nameWithoutExtension
    private fun durationMs(f: File): Long? = runCatching { RandomAccessFile(f, "r").use { mp4(it, f.length()) ?: mp3(it, f.length()) } }.getOrNull()
    private fun mp4(r: RandomAccessFile, len: Long): Long? {
        fun scan(from: Long, to: Long): Long? {
            var p = from
            while (p + 8 <= to) {
                r.seek(p); var size = r.readInt().toLong() and 0xFFFFFFFFL; val t = ByteArray(4).also { r.readFully(it) }.decodeToString()
                var hdr = 8L; if (size == 1L) { size = r.readLong(); hdr = 16 } else if (size == 0L) size = to - p
                if (size < hdr) return null
                if (t == "moov") return scan(p + hdr, p + size)
                if (t == "mvhd") { r.seek(p + hdr); val v = r.readByte().toInt(); r.skipBytes(3 + if (v == 1) 16 else 8); val ts = r.readInt().toLong() and 0xFFFFFFFFL; val d = if (v == 1) r.readLong() else r.readInt().toLong() and 0xFFFFFFFFL; return if (ts > 0) d * 1000 / ts else null }
                p += size
            }
            return null
        }
        return scan(0, len)
    }
    private fun mp3(r: RandomAccessFile, len: Long): Long? {
        r.seek(0); val h = ByteArray(10); r.readFully(h)
        var off = if (h[0] == 'I'.code.toByte() && h[1] == 'D'.code.toByte()) 10L + ((h[6].toInt() shl 21) or (h[7].toInt() shl 14) or (h[8].toInt() shl 7) or h[9].toInt()) else 0L
        val br = intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0)
        repeat(4096) {
            r.seek(off); val b = r.readInt()
            if (b ushr 21 == 0x7FF && (b shr 19 and 3) == 3 && (b shr 17 and 3) == 1) { val k = br[b shr 12 and 15]; if (k > 0) return (len - off) * 8 / k }
            off++
        }
        return null
    }
}
