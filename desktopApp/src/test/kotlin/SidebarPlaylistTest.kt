import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.platform.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Density
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.example.juke.desktop.DesktopViewModels
import com.example.juke.desktop.JukeApp
import com.example.juke.network.JukesApi
import java.io.File
import kotlin.test.Test
import org.jetbrains.skia.EncodedImageFormat

/** Clicks a playlist in the left pane and shoots before/after. */
@OptIn(ExperimentalComposeUiApi::class)
class SidebarPlaylistTest {
    private fun <T> edt(f: () -> T): T { var r: Result<T>? = null; javax.swing.SwingUtilities.invokeAndWait { r = runCatching(f) }; return r!!.getOrThrow() }
    private var t = System.nanoTime()
    private fun shoot(scene: ImageComposeScene, name: String, ms: Int) {
        repeat(ms / 100) { Thread.sleep(100); t += 100_000_000; edt { scene.render(t) } }
        edt { File("/private/tmp/claude-501/$name.png").writeBytes(scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes) }
    }
    private fun click(scene: ImageComposeScene, x: Float, y: Float) = edt {
        val p = Offset(x, y)
        scene.sendPointerEvent(PointerEventType.Move, p); scene.sendPointerEvent(PointerEventType.Press, p); scene.sendPointerEvent(PointerEventType.Release, p)
    }
    @Test fun clickPlaylist() {
        JukesApi.init(Application.instance)
        val scene = edt { ImageComposeScene(1180, 780, Density(1f)) {
            CompositionLocalProvider(LocalViewModelStoreOwner provides DesktopViewModels, LocalConfiguration provides Configuration(1180, 780), androidx.lifecycle.compose.LocalLifecycleOwner provides androidx.lifecycle.ProcessLifecycleOwner) { JukeApp() }
        } }
        shoot(scene, "sb_0", 3000)
        click(scene, 80f, 265f)
        shoot(scene, "sb_1", 2500)
        androidx.lifecycle.ViewModelProvider.create(DesktopViewModels)[com.example.juke.viewmodels.LibraryViewModel::class].uiState.value.let { System.err.println("STATE sel=${it.selectedPlaylist?.name} tracks=${it.tracks.size} fav=${it.showFavoritesOnly}") }
        click(scene, 80f, 385f)
        shoot(scene, "sb_2", 2500)
        edt { scene.close() }
    }
}
