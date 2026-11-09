import com.sun.jna.Structure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NowPlayingMacNsSizeTest {
    @Test
    fun nsSize_isByValueStructWithExpectedFields() {
        val clazz = Class.forName("com.example.juke.services.NowPlaying\$Mac\$NSSize")
        assertTrue(Structure.ByValue::class.java.isAssignableFrom(clazz))

        val constructor = clazz.getDeclaredConstructor(Double::class.javaPrimitiveType, Double::class.javaPrimitiveType)
        constructor.isAccessible = true
        val size = constructor.newInstance(600.0, 600.0) as Structure

        val width = clazz.getDeclaredField("width").apply { isAccessible = true }.getDouble(size)
        val height = clazz.getDeclaredField("height").apply { isAccessible = true }.getDouble(size)
        assertEquals(600.0, width)
        assertEquals(600.0, height)
    }
}
