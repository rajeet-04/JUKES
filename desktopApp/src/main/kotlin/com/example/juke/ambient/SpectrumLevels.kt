package com.example.juke.ambient

/**
 * Turns a JavaFX audio spectrum (magnitudes in dB, -60 = silence, evenly spaced from 0 Hz to Nyquist)
 * into the bass / treble / energy levels the background waves read on Android from PCM.
 */
fun levelsFromSpectrum(magnitudesDb: FloatArray, nyquistHz: Float = 22_050f): Levels {
    val n = magnitudesDb.size
    if (n == 0) return Levels.Silent
    val hzPerBin = nyquistHz / n
    fun norm(db: Float) = ((db + 60f) / 60f).coerceIn(0f, 1f).let { it * it }
    fun avg(fromHz: Float, toHz: Float): Float {
        val a = (fromHz / hzPerBin).toInt().coerceIn(0, n - 1)
        val b = (toHz / hzPerBin).toInt().coerceIn(a, n - 1)
        var sum = 0f
        for (i in a..b) sum += norm(magnitudesDb[i])
        return sum / (b - a + 1)
    }
    val bass = avg(0f, 250f)
    val treble = avg(4_000f, 16_000f)
    val energy = avg(0f, 16_000f)
    if (bass == 0f && treble == 0f && energy == 0f) return Levels.Silent
    return Levels(energy = (energy * 2f).coerceIn(0f, 1f), bass = (bass * 1.5f).coerceIn(0f, 1f), treble = (treble * 2.5f).coerceIn(0f, 1f))
}
