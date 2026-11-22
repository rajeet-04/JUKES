import com.example.juke.ambient.Levels
import com.example.juke.ambient.levelsFromSpectrum
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AmbientLevelsTest {
    private val floorDb = -60f

    @Test fun lowBinsAreBassAndHighBinsAreTreble() {
        val low = FloatArray(128) { if (it < 3) 0f else floorDb }
        val high = FloatArray(128) { if (it > 40) 0f else floorDb }
        val lo = levelsFromSpectrum(low)
        val hi = levelsFromSpectrum(high)
        assertTrue(lo.bass > lo.treble, "low-band energy reads as bass: $lo")
        assertTrue(hi.treble > hi.bass, "high-band energy reads as treble: $hi")
    }

    @Test fun silenceIsSilent() {
        assertEquals(Levels.Silent, levelsFromSpectrum(FloatArray(128) { floorDb }))
    }

    @Test fun valuesStayInRange() {
        val l = levelsFromSpectrum(FloatArray(128) { 6f })
        assertTrue(l.energy in 0f..1f && l.bass in 0f..1f && l.treble in 0f..1f, "$l")
    }
}
