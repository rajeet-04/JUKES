package com.example.juke.ambient

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BeatPulseTest {
    private fun run(p: BeatPulse, bass: Float, ms: Int): Float {
        var v = 0f
        repeat(ms / 16) { v = p.step(bass, 16f) }
        return v
    }

    @Test fun `steady bass gives no pulse`() {
        val p = BeatPulse()
        assertEquals(0f, run(p, 0.6f, 3000), 0.02f)
    }

    @Test fun `a kick over the running level pulses within a frame or two`() {
        val p = BeatPulse()
        run(p, 0.2f, 2000)
        val hit = p.step(0.8f, 16f).let { p.step(0.8f, 16f) }
        assertTrue("pulse was $hit", hit > 0.5f)
    }

    @Test fun `the pulse dies away after the kick`() {
        val p = BeatPulse()
        run(p, 0.2f, 2000)
        p.step(0.9f, 16f); p.step(0.9f, 16f)
        val after = run(p, 0.2f, 400)
        assertTrue("pulse was $after", after < 0.1f)
    }

    @Test fun `pulse stays within 0 to 1`() {
        val p = BeatPulse()
        run(p, 0f, 1000)
        val v = run(p, 1f, 32)
        assertTrue(v in 0f..1f)
    }
}
