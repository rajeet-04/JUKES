import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import com.example.juke.desktop.combinedClickable
import kotlin.test.Test
import kotlin.test.assertEquals

/** On desktop a right click does what a long press does on the phone: it opens the song menu. */
class RightClickTest {
    private fun press(scene: ImageComposeScene, button: PointerButton?) {
        scene.sendPointerEvent(PointerEventType.Press, Offset(20f, 20f), button = button)
        scene.sendPointerEvent(PointerEventType.Release, Offset(20f, 20f), button = button)
    }

    @Test fun rightClickRunsLongClickAndLeftClickRunsClick() {
        var clicks = 0; var longs = 0
        val scene = ImageComposeScene(100, 100, Density(1f)) {
            Box(Modifier.fillMaxSize().combinedClickable(onLongClick = { longs++ }, onClick = { clicks++ }))
        }
        scene.render()
        press(scene, PointerButton.Secondary)
        assertEquals(1, longs, "right click opens the menu")
        assertEquals(0, clicks, "and is not a tap")
        press(scene, PointerButton.Primary)
        assertEquals(1, clicks, "left click still taps")
        assertEquals(1, longs)
        scene.close()
    }
}
