import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.juke.ui.theme.LocalHazeState
import com.example.juke.ui.theme.liquidGlass
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import org.jetbrains.skia.Bitmap
import kotlin.test.Test
import kotlin.test.assertTrue

/** Liquid glass on desktop is real: over stripes, the pane's rim bends them (pixels differ from no glass). */
@OptIn(ExperimentalComposeUiApi::class)
class LiquidGlassRenderTest {
    private fun render(withGlass: Boolean): Bitmap {
        val scene = ImageComposeScene(240, 240, Density(1f)) {
            val haze = remember { HazeState() }
            CompositionLocalProvider(LocalHazeState provides haze) {
                Box(Modifier.fillMaxSize().hazeSource(haze)) {
                    Row(Modifier.fillMaxSize()) {
                        repeat(12) { Box(Modifier.size(20.dp, 240.dp).background(if (it % 2 == 0) Color.White else Color(0xFF1C3FFF))) }
                    }
                }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(140.dp).then(
                            if (withGlass) Modifier.liquidGlass(RoundedCornerShape(32.dp), Color.White.copy(alpha = 0.04f), source = haze) else Modifier
                        )
                    )
                }
            }
        }
        var t = System.nanoTime()
        repeat(4) { t += 100_000_000; scene.render(t) }
        val img = scene.render(t + 100_000_000)
        scene.close()
        return Bitmap.makeFromImage(img)
    }

    @Test fun rimBendsWhatIsBehindIt() {
        val plain = render(false)
        val glass = render(true)
        var differing = 0
        // the band just inside the pane's edge, where refraction acts
        for (y in 50..190) for (x in 50..190) {
            val inRim = x < 70 || x > 170 || y < 70 || y > 170
            if (inRim && plain.getColor(x, y) != glass.getColor(x, y)) differing++
        }
        println("LIQUIDGLASS rim pixels changed: $differing of ${4 * 20 * 140 + 0}")
        assertTrue(differing > 400, "refraction should change the rim pixels, changed $differing")
    }
}
