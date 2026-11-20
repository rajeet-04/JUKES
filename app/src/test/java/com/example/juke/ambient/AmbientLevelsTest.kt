package com.example.juke.ambient

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

class AmbientLevelsTest {
    private val rate = 44100

    /** Interleaved stereo 16-bit PCM of a sine at [hz], [amp] of full scale. */
    private fun sine(hz: Double, amp: Double, frames: Int = 4096): ByteBuffer {
        val b = ByteBuffer.allocate(frames * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until frames) {
            val s = (amp * Short.MAX_VALUE * sin(2 * PI * hz * i / rate)).toInt().toShort()
            b.putShort(s); b.putShort(s)
        }
        b.flip()
        return b
    }

    @Test fun `silence reads as nothing`() {
        val a = LevelAnalyzer(rate, 2)
        val l = a.analyze(ByteBuffer.allocate(4096 * 4).order(ByteOrder.nativeOrder()))
        assertEquals(0f, l.energy, 1e-4f)
        assertEquals(0f, l.bass, 1e-4f)
        assertEquals(0f, l.treble, 1e-4f)
    }

    @Test fun `a low tone is bass, not treble`() {
        val a = LevelAnalyzer(rate, 2)
        a.analyze(sine(60.0, 0.5)) // settle the filters
        val l = a.analyze(sine(60.0, 0.5))
        assertTrue("bass ${l.bass} > treble ${l.treble}", l.bass > 0.3f && l.bass > l.treble * 4)
    }

    @Test fun `a high tone is treble, not bass`() {
        val a = LevelAnalyzer(rate, 2)
        a.analyze(sine(6000.0, 0.5))
        val l = a.analyze(sine(6000.0, 0.5))
        assertTrue("treble ${l.treble} > bass ${l.bass}", l.treble > 0.3f && l.treble > l.bass * 4)
    }

    @Test fun `louder audio reads higher, and the input is left untouched`() {
        val a = LevelAnalyzer(rate, 2)
        val quiet = a.analyze(sine(440.0, 0.05)).energy
        val buf = sine(440.0, 0.6)
        val before = buf.position()
        val loud = a.analyze(buf).energy
        assertTrue(loud > quiet * 3)
        assertEquals(before, buf.position())
    }

    @Test fun `levels are read a set time after they were measured`() {
        val t = LevelTimeline(capacity = 8)
        t.push(1000, Levels(0.1f, 0.1f, 0.1f))
        t.push(1100, Levels(0.5f, 0.5f, 0.5f))
        t.push(1200, Levels(0.9f, 0.9f, 0.9f))
        assertEquals(0.5f, t.at(1150)!!.energy, 1e-6f)
        assertEquals(0.9f, t.at(5000)!!.energy, 1e-6f)
        assertEquals(null, t.at(900))
        assertEquals(null, t.at(5000, maxAgeMs = 1000))
    }

    @Test fun `the envelope jumps up quickly and settles slowly`() {
        val e = Envelope(attackMs = 60f, releaseMs = 600f)
        e.step(1f, 60f)
        val up = e.value
        repeat(10) { e.step(1f, 33f) }
        e.step(0f, 60f)
        val downOneStep = e.value
        assertTrue("attack $up", up > 0.55f)
        assertTrue("release $downOneStep", downOneStep > 0.85f)
    }
}
