import androidx.compose.ui.BiasAlignment
import com.example.juke.ui.theme.GlassLight
import kotlin.test.Test
import kotlin.test.assertTrue

/** The glass highlight leans toward the pointer, like it leans with the tilt on a phone. */
class GlassLightTest {
    private fun settle(fx: Float, fy: Float): BiasAlignment {
        repeat(60) { GlassLight.aim(fx, fy) }
        return GlassLight.position as BiasAlignment
    }

    @Test fun lightFollowsPointer() {
        GlassLight.reset()
        val left = settle(0.05f, 0.5f)
        val right = settle(0.95f, 0.5f)
        assertTrue(left.horizontalBias < 0 && right.horizontalBias > 0, "left=${left.horizontalBias} right=${right.horizontalBias}")
        val top = settle(0.5f, 0.05f)
        val bottom = settle(0.5f, 0.95f)
        assertTrue(top.verticalBias < bottom.verticalBias)
        GlassLight.reset()
    }
}
