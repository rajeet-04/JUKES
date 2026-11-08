import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MaximizeTest {
    @Test fun toggles() {
        var f: javax.swing.JFrame? = null
        javax.swing.SwingUtilities.invokeAndWait { f = javax.swing.JFrame().apply { setBounds(100, 100, 800, 600); isVisible = true } }
        val w = f!!; val before = w.bounds
        javax.swing.SwingUtilities.invokeAndWait { com.example.juke.desktop.toggleMaximize(w) }
        val gc = w.graphicsConfiguration; val ins = java.awt.Toolkit.getDefaultToolkit().getScreenInsets(gc)
        println("MAX bounds=${w.bounds} screen=${gc.bounds} insets=$ins")
        assertTrue(w.width >= gc.bounds.width - ins.left - ins.right - 2 && w.height >= gc.bounds.height - ins.top - ins.bottom - 2, "not full")
        javax.swing.SwingUtilities.invokeAndWait { com.example.juke.desktop.toggleMaximize(w) }
        println("MAX restored=${w.bounds}")
        assertEquals(before.size, w.size)
        javax.swing.SwingUtilities.invokeAndWait { w.dispose() }
    }
}
