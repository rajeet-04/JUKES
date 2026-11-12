import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.platform.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.example.juke.desktop.DesktopViewModels
import com.example.juke.desktop.JukeApp
import com.example.juke.network.JukesApi
import com.example.juke.network.SpotifyApi
import com.example.juke.ui.screens.PlayerScreen
import com.example.juke.ui.theme.JUKETheme
import com.example.juke.viewmodels.MusicViewModel
import java.io.File
import kotlin.test.Test
import org.jetbrains.skia.EncodedImageFormat

/** Renders the real screens offscreen while a song plays; any composition error fails the test. */
@OptIn(ExperimentalComposeUiApi::class)
class UiRenderTest {
    private fun <T> edt(f: () -> T): T { var r: Result<T>? = null; javax.swing.SwingUtilities.invokeAndWait { r = runCatching(f) }; return r!!.getOrThrow() }
    private fun shoot(scene: ImageComposeScene, name: String, ms: Int) {
        var t = System.nanoTime()
        repeat(ms / 100) { Thread.sleep(100); t += 100_000_000; edt { scene.render(t) } }
        edt { File(File(System.getProperty("user.dir"), "build/test-shots").apply { mkdirs() }, "$name.png").writeBytes(scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes) }
    }

    @Test fun screens() {
        val app = Application.instance
        JukesApi.init(app)
        val vm = ViewModelProvider.create(DesktopViewModels)[MusicViewModel::class]
        var showPlayer = false
        val scene = edt { ImageComposeScene(1180, 780, Density(1f)) {
            CompositionLocalProvider(LocalViewModelStoreOwner provides DesktopViewModels, LocalConfiguration provides Configuration(1180, 780), androidx.lifecycle.compose.LocalLifecycleOwner provides androidx.lifecycle.ProcessLifecycleOwner) {
                JukeApp()
            }
        } }
        shoot(scene, "home", 4000)
        // no network on CI: the home render above is the check there; the playing screens need a real song
        val hit = runCatching { kotlinx.coroutines.runBlocking { SpotifyApi.search("Blinding Lights The Weeknd", listOf("track")).tracks!!.items.first() } }.getOrNull()
        if (hit == null) { println("UI no network: skipped the playing screens"); edt { scene.close() }; return }
        vm.playInstant(SpotifyApi.spotifyTrackToSong(hit))
        shoot(scene, "home_playing", 14000)
        edt {
            val pos = androidx.compose.ui.geometry.Offset(30f, 80f)
            scene.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Move, pos)
            scene.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Press, pos)
            scene.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Release, pos)
        }
        shoot(scene, "home_collapsed", 1500)
        edt { scene.close() }

        val player = edt { ImageComposeScene(1180, 780, Density(1f)) {
            CompositionLocalProvider(LocalViewModelStoreOwner provides DesktopViewModels, LocalConfiguration provides Configuration(1180, 780), androidx.lifecycle.compose.LocalLifecycleOwner provides androidx.lifecycle.ProcessLifecycleOwner) {
                JUKETheme(extractedColors = vm.uiState.value.extractedColors) {
                    com.example.juke.desktop.DesktopPlayerScreen(vm.uiState.value, vm, onDismiss = {}, onNavigateToArtist = {})
                }
            }
        } }
        shoot(player, "player", 3000)
        edt {
            val pos = androidx.compose.ui.geometry.Offset(740f, 100f)
            player.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Move, pos)
            player.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Press, pos)
            player.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Release, pos)
        }
        shoot(player, "player_queue", 800)
        edt {
            val pos = androidx.compose.ui.geometry.Offset(1136f, 34f)
            player.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Move, pos)
            player.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Press, pos)
            player.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Release, pos)
        }
        shoot(player, "player_menu", 800)
        edt { player.close() }
        val wide = edt { ImageComposeScene(1920, 1080, Density(1f)) {
            CompositionLocalProvider(LocalViewModelStoreOwner provides DesktopViewModels, LocalConfiguration provides Configuration(1920, 1080), androidx.lifecycle.compose.LocalLifecycleOwner provides androidx.lifecycle.ProcessLifecycleOwner) {
                JUKETheme(extractedColors = vm.uiState.value.extractedColors) {
                    com.example.juke.desktop.DesktopPlayerScreen(vm.uiState.value, vm, onDismiss = {}, onNavigateToArtist = {})
                }
            }
        } }
        shoot(wide, "player_wide", 2500)
        edt { wide.close() }
        vm.togglePlayPause()
    }
}
