package androidx.activity.compose

import androidx.compose.runtime.*

/** Desktop back: Esc or mouse-back calls the most recent enabled handler (see Main). */
object BackDispatcher {
    private val handlers = ArrayList<Handler>()
    class Handler(var enabled: Boolean, var onBack: () -> Unit)
    fun add(h: Handler) { handlers += h }
    fun remove(h: Handler) { handlers -= h }
    /** True when something consumed the press. */
    fun back(): Boolean { val h = handlers.lastOrNull { it.enabled } ?: return false; h.onBack(); return true }
}

@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val h = remember { BackDispatcher.Handler(enabled, onBack) }
    h.enabled = enabled; h.onBack = onBack
    DisposableEffect(h) { BackDispatcher.add(h); onDispose { BackDispatcher.remove(h) } }
}
