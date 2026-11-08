package com.example.juke.desktop

import android.app.Application
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackDispatcher
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.example.juke.jam.JamSession
import com.example.juke.network.JukesApi
import com.example.juke.services.PlaylistImportManager
import com.example.juke.ui.theme.GlassPrefs
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.MutableSharedFlow

/** One store for the whole app, like the Activity's: every screen shares the same view models. */
object DesktopViewModels : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
    override val viewModelStore = ViewModelStore()
    override val defaultViewModelProviderFactory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T = create(modelClass.java, extras)
        fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
            modelClass.getConstructor(Application::class.java).newInstance(Application.instance)
    }
    override val defaultViewModelCreationExtras: CreationExtras get() = CreationExtras.Empty
}

/** Links given to the app (command line arguments). */
object AppLinks { val incoming = MutableSharedFlow<String>(replay = 8, extraBufferCapacity = 8) }

private fun startup(args: Array<String>) {
    val app = Application.instance
    GlassPrefs.solid = app.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE).getBoolean("solid_surfaces", false)
    // Heavy singletons off the UI thread, so the first frame doesn't wait for them
    Thread {
        com.example.juke.network.ApiClient.httpClient
        com.example.juke.database.MusicDatabase.getDatabase(app)
    }.apply { name = "startup-warmup"; isDaemon = true; priority = Thread.NORM_PRIORITY - 1; start() }
    JukesApi.progressiveEnabled = app.getSharedPreferences("music_settings_prefs", Context.MODE_PRIVATE).getBoolean("progressive_playback", true)
    JukesApi.init(app)
    PlaylistImportManager.get(app).resume()
    JamSession.resume(app)
    com.example.juke.services.NowPlaying.start(com.example.juke.services.PlaybackManager.getInstance(app))
    args.forEach { AppLinks.incoming.tryEmit(it) }
}

fun main(args: Array<String>) {
    System.setProperty("apple.awt.application.name", "JUKE")
    // Dock / taskbar icon while running unpackaged (a packaged app gets it from its bundle)
    runCatching {
        val img = javax.imageio.ImageIO.read(object {}.javaClass.getResource("/juke_icon.png"))
        if (java.awt.Taskbar.isTaskbarSupported() && java.awt.Taskbar.getTaskbar().isSupported(java.awt.Taskbar.Feature.ICON_IMAGE)) java.awt.Taskbar.getTaskbar().iconImage = img
    }
    startup(args)
    application {
        val state = rememberWindowState(size = DpSize(1180.dp, 780.dp))
        Window(
            onCloseRequest = {
                com.example.juke.services.PlaybackManager.getInstance(Application.instance).release()
                exitApplication()
            },
            title = "JUKE",
            icon = androidx.compose.ui.res.loadImageBitmap(object {}.javaClass.getResourceAsStream("/juke_icon.png")!!).let { androidx.compose.ui.graphics.painter.BitmapPainter(it) },
            state = state,
            onKeyEvent = { e ->
                if (e.type != KeyEventType.KeyDown) false else {
                    val pm = com.example.juke.services.PlaybackManager.getInstance(Application.instance)
                    val mod = e.isMetaPressed || e.isCtrlPressed
                    when {
                        e.key == Key.Escape -> BackDispatcher.back()
                        e.key == Key.Spacebar -> { pm.togglePlayPause(); true }
                        mod && e.key == Key.DirectionRight -> { pm.skipToNext(); true }
                        mod && e.key == Key.DirectionLeft -> { pm.skipToPrevious(); true }
                        mod && e.key == Key.DirectionUp -> { pm.setVolume(pm.volumeFlow.value + 0.1f); true }
                        mod && e.key == Key.DirectionDown -> { pm.setVolume(pm.volumeFlow.value - 0.1f); true }
                        else -> false
                    }
                }
            },
        ) {
            LaunchedEffect(Unit) { window.minimumSize = java.awt.Dimension(420, 640) }
            val focused = LocalWindowInfo.current.isWindowFocused
            LaunchedEffect(focused) { ProcessLifecycleOwner.setForeground(focused) }
            val density = LocalDensity.current
            val size = LocalWindowInfo.current.containerSize
            val config = Configuration(with(density) { size.width.toDp().value.toInt() }, with(density) { size.height.toDp().value.toInt() })
            // macOS: content runs under a transparent title bar (traffic lights float over the wide layout).
            val isMac = remember { System.getProperty("os.name").orEmpty().startsWith("Mac") }
            val wide = config.screenWidthDp >= 900
            LaunchedEffect(wide) {
                if (isMac) with(window.rootPane) {
                    putClientProperty("apple.awt.fullWindowContent", wide)
                    putClientProperty("apple.awt.transparentTitleBar", wide)
                    putClientProperty("apple.awt.windowTitleVisible", !wide)
                }
            }
            CompositionLocalProvider(LocalViewModelStoreOwner provides DesktopViewModels, LocalConfiguration provides config,
                LocalTitleInset provides (if (isMac && wide) 76.dp else 0.dp), LocalWindowScope provides this@Window) {
                JukeApp()
                ToastHost()
            }
        }
    }
}

/** Android toasts become a short snackbar at the bottom of the window. */
@Composable
private fun ToastHost() {
    val host = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { Toast.messages.collect { host.currentSnackbarData?.dismiss(); host.showSnackbar(it, duration = SnackbarDuration.Short) } }
    Box(Modifier.fillMaxSize().padding(bottom = 96.dp), contentAlignment = Alignment.BottomCenter) { SnackbarHost(host) }
}

@Composable
inline fun <reified VM : ViewModel> viewModel(
    owner: ViewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current) { "no ViewModelStoreOwner" },
    key: String? = null,
    factory: ViewModelProvider.Factory? = DesktopViewModels.defaultViewModelProviderFactory,
): VM = androidx.lifecycle.viewmodel.compose.viewModel(owner, key, factory)
