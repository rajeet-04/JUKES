package androidx.core.graphics

import kotlin.math.pow

object ColorUtils {
    fun calculateLuminance(c: Int): Double {
        fun ch(v: Int): Double { val s = v / 255.0; return if (s < 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * ch(c shr 16 and 255) + 0.7152 * ch(c shr 8 and 255) + 0.0722 * ch(c and 255)
    }
    fun calculateContrast(fg: Int, bg: Int): Double {
        val a = calculateLuminance(fg) + 0.05; val b = calculateLuminance(bg) + 0.05
        return maxOf(a, b) / minOf(a, b)
    }
    fun blendARGB(c1: Int, c2: Int, r: Float): Int {
        fun m(s: Int) = ((c1 shr s and 255) * (1 - r) + (c2 shr s and 255) * r).toInt().coerceIn(0, 255)
        return (m(24) shl 24) or (m(16) shl 16) or (m(8) shl 8) or m(0)
    }
    fun setAlphaComponent(c: Int, a: Int) = (c and 0xFFFFFF) or (a shl 24)
}
