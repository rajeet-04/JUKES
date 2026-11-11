package android.graphics

import androidx.compose.ui.graphics.ImageBitmap

class Bitmap(val image: ImageBitmap) {
    val width get() = image.width
    val height get() = image.height
}

object Color {
    const val BLACK = 0xFF000000.toInt(); const val WHITE = 0xFFFFFFFF.toInt(); const val TRANSPARENT = 0
    fun colorToHSV(c: Int, hsv: FloatArray) {
        val r = (c shr 16 and 255) / 255f; val g = (c shr 8 and 255) / 255f; val b = (c and 255) / 255f
        val mx = maxOf(r, g, b); val mn = minOf(r, g, b); val d = mx - mn
        val h = when { d == 0f -> 0f; mx == r -> ((g - b) / d + (if (g < b) 6 else 0)); mx == g -> (b - r) / d + 2; else -> (r - g) / d + 4 } * 60f
        hsv[0] = h; hsv[1] = if (mx == 0f) 0f else d / mx; hsv[2] = mx
    }
    fun HSVToColor(hsv: FloatArray): Int = androidx.compose.ui.graphics.Color.hsv(hsv[0], hsv[1], hsv[2]).let { (it.value shr 32).toInt() }
}
