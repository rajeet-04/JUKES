package com.example.juke.services

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Test

@UnstableApi
class EdgeSilenceProcessorTest {
    @Test
    fun dropsOnlyLeadingAndTrailingSilence() {
        val rate = 44100
        val p = EdgeSilenceProcessor().apply { enabled = true }
        p.configure(AudioFormat(rate, 1, C.ENCODING_PCM_16BIT))
        p.flush()
        // 1s silence, 1s tone, 1s silence (middle gap), 1s tone, 1s silence
        val parts = listOf(false, true, false, true, false)
        val buf = ByteBuffer.allocateDirect(parts.size * rate * 2).order(ByteOrder.nativeOrder())
        parts.forEach { tone ->
            repeat(rate) { buf.putShort(if (tone) (sin(2 * PI * 440 * it / rate) * 8000).toInt().toShort() else 0) }
        }
        buf.flip()
        p.queueInput(buf)
        p.queueEndOfStream()
        val out = p.output
        // tone + gap + tone = 3 seconds (small tolerance for 10 ms chunking)
        val frames = out.remaining() / 2
        assertEquals(3 * rate, frames.toLong().toInt().coerceIn(3 * rate - 441, 3 * rate + 441))
    }
}
