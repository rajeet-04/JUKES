package android.text.format

object Formatter {
    fun formatShortFileSize(c: android.content.Context?, bytes: Long) = formatFileSize(c, bytes)
    fun formatFileSize(c: android.content.Context?, bytes: Long): String {
        if (bytes < 1000) return "$bytes B"
        val u = arrayOf("kB", "MB", "GB", "TB"); var v = bytes / 1000.0; var i = 0
        while (v >= 1000 && i < u.lastIndex) { v /= 1000; i++ }
        return "%.1f %s".format(v, u[i])
    }
}
