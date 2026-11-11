package com.example.juke.services

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertTrue
import org.junit.Test

@UnstableApi
class BoostAudioProcessorTest {
    private fun peak(enabled: Boolean, hz: Double): Int {
        val p = BoostAudioProcessor()
        p.configure(enabled, 10f, 9f)
        p.configure(AudioFormat(44100, 1, C.ENCODING_PCM_16BIT))
        p.flush()
        val n = 4410
        val buf = ByteBuffer.allocateDirect(n * 2).order(ByteOrder.nativeOrder())
        repeat(n) { buf.putShort((sin(2 * PI * hz * it / 44100) * 8000).toInt().toShort()) }
        buf.flip()
        p.queueInput(buf)
        val out = p.output
        var max = 0
        while (out.remaining() >= 2) max = maxOf(max, abs(out.short.toInt()))
        return max
    }

    @Test
    fun boostRaisesLevelAndOffIsUnity() {
        assertTrue(peak(true, 60.0) > 8000 * 2)   // bass + gain clearly louder
        assertTrue(abs(peak(false, 60.0) - 8000) < 50) // disabled passes through
    }

    private fun stablePeak(amp: Int): Int {
        val p = BoostAudioProcessor()
        p.configure(false, 0f, 0f, stable = true)
        p.configure(AudioFormat(44100, 1, C.ENCODING_PCM_16BIT))
        p.flush()
        val n = 44100 * 6
        val buf = ByteBuffer.allocateDirect(n * 2).order(ByteOrder.nativeOrder())
        repeat(n) { buf.putShort((sin(2 * PI * 440.0 * it / 44100) * amp).toInt().toShort()) }
        buf.flip()
        p.queueInput(buf)
        val out = p.output
        var max = 0
        var i = 0
        while (out.remaining() >= 2) { val v = abs(out.short.toInt()); if (i++ > n - 4410) max = maxOf(max, v) }
        return max
    }

    @Test
    fun stableVolumeMovesQuietUpAndLoudDown() {
        assertTrue(stablePeak(1500) > 1500 * 2)
        assertTrue(stablePeak(30000) < 30000)
    }
}
