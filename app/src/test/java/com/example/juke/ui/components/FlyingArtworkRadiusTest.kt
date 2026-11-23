package com.example.juke.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlyingArtworkRadiusTest {
    private fun ok(r: Float) = assertTrue("radius $r", r.isFinite() && r >= 0f)

    @Test fun endpointsKeepTheirRadius() {
        assertEquals(18f, flyingArtworkRadius(startWidth = 100f, endWidth = 100f, t = 0f), 0.001f)
        assertEquals(32f, flyingArtworkRadius(startWidth = 100f, endWidth = 400f, t = 1f), 0.001f)
    }

    @Test fun aScaledDownLayerGetsALargerRadiusToLookRight() {
        // At t = 0 a 100 px mini artwork is a 400 px layer scaled to 0.25, so 18 dp on screen needs 72 in the layer
        assertEquals(72f, flyingArtworkRadius(startWidth = 100f, endWidth = 400f, t = 0f), 0.001f)
    }

    @Test fun neverNanOrNegativeWhenBoundsHaveNoSize() {
        // This was the crash: both bounds 0 wide gave 0 / 0 = NaN for every corner
        for (t in listOf(0f, 0.3f, 1f)) {
            ok(flyingArtworkRadius(0f, 0f, t))
            ok(flyingArtworkRadius(0f, 400f, t))
            ok(flyingArtworkRadius(100f, 0f, t))
            ok(flyingArtworkRadius(-5f, -5f, t))
        }
    }

    @Test fun neverNanForOddProgress() {
        for (t in listOf(Float.NaN, -1f, 2f, Float.POSITIVE_INFINITY)) ok(flyingArtworkRadius(100f, 400f, t))
    }
}
