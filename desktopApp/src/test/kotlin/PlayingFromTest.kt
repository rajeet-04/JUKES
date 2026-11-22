import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import com.example.juke.desktop.PlayingFromLabel
import com.example.juke.viewmodels.PlayContext
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayingFromTest {
    @Test fun clickingTheLabelOpensTheList() {
        var opened: PlayContext? = null
        val from = PlayContext(PlayContext.Kind.ALBUM, "alb1", "After Hours", setOf("t1"))
        val scene = ImageComposeScene(400, 120, Density(1f)) { MaterialTheme { PlayingFromLabel(from, { opened = from }) } }
        scene.render()
        scene.sendPointerEvent(PointerEventType.Press, Offset(40f, 20f), button = PointerButton.Primary)
        scene.sendPointerEvent(PointerEventType.Release, Offset(40f, 20f), button = PointerButton.Primary)
        scene.close()
        assertEquals(from, opened)
    }
}
