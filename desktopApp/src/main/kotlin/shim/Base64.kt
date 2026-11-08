package android.util

object Base64 {
    const val DEFAULT = 0; const val NO_PADDING = 1; const val NO_WRAP = 2; const val URL_SAFE = 8
    private fun enc(f: Int) = (if (f and URL_SAFE != 0) java.util.Base64.getUrlEncoder() else java.util.Base64.getEncoder()).let { if (f and NO_PADDING != 0) it.withoutPadding() else it }
    fun encodeToString(b: ByteArray, f: Int): String = enc(f).encodeToString(b)
    fun encode(b: ByteArray, f: Int): ByteArray = enc(f).encode(b)
    fun decode(s: String, f: Int): ByteArray = (if (f and URL_SAFE != 0) java.util.Base64.getUrlDecoder() else java.util.Base64.getMimeDecoder()).decode(s)
}
