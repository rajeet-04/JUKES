package androidx.palette.graphics

import android.graphics.Bitmap
import androidx.core.graphics.ColorUtils
import androidx.compose.ui.graphics.ImageBitmap

/** Small Palette clone: 5-bit colour buckets, picked by saturation/lightness like the Android targets. */
class Palette private constructor(private val sw: List<Swatch>) {
    class Swatch(val rgb: Int, val population: Int) {
        val hsl: FloatArray by lazy { hsl(rgb) }
        val bodyTextColor: Int get() = if (ColorUtils.calculateContrast(-1, rgb) >= 4.5) -1 else 0xFF000000.toInt()
        val titleTextColor: Int get() = bodyTextColor
    }
    private fun pick(sMin: Float, sMax: Float, lMin: Float, lMax: Float) =
        sw.filter { it.hsl[1] in sMin..sMax && it.hsl[2] in lMin..lMax }.maxByOrNull { it.population }
    val dominantSwatch get() = sw.maxByOrNull { it.population }
    val vibrantSwatch get() = pick(.35f, 1f, .3f, .7f)
    val lightVibrantSwatch get() = pick(.35f, 1f, .55f, 1f)
    val darkVibrantSwatch get() = pick(.35f, 1f, 0f, .45f)
    val mutedSwatch get() = pick(0f, .4f, .3f, .7f)
    val lightMutedSwatch get() = pick(0f, .4f, .55f, 1f)
    val darkMutedSwatch get() = pick(0f, .4f, 0f, .45f)

    class Builder(private val img: ImageBitmap) {
        fun generate(): Palette {
            val w = img.width; val h = img.height
            val px = IntArray(w * h); img.readPixels(px)
            val counts = HashMap<Int, Int>()
            for (p in px) { if ((p ushr 24) < 128) continue; val k = ((p shr 19 and 31) shl 10) or ((p shr 11 and 31) shl 5) or (p shr 3 and 31); counts.merge(k, 1, Int::plus) }
            return Palette(counts.map { (k, n) -> Swatch(0xFF000000.toInt() or ((k shr 10 shl 3 or 4) shl 16) or ((k shr 5 and 31 shl 3 or 4) shl 8) or (k and 31 shl 3 or 4), n) })
        }
    }
    companion object { fun from(b: Bitmap) = Builder(b.image) }
}

private fun hsl(c: Int): FloatArray {
    val r = (c shr 16 and 255) / 255f; val g = (c shr 8 and 255) / 255f; val b = (c and 255) / 255f
    val mx = maxOf(r, g, b); val mn = minOf(r, g, b); val l = (mx + mn) / 2; val d = mx - mn
    val s = if (d == 0f) 0f else d / (1 - kotlin.math.abs(2 * l - 1))
    return floatArrayOf(0f, s, l)
}
