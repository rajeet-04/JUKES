package android.widget

import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow

/** Toasts surface as a message the main window shows in a snackbar. */
class Toast private constructor(private val text: String) {
    companion object {
        const val LENGTH_SHORT = 0; const val LENGTH_LONG = 1
        val messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
        fun makeText(c: Context?, t: CharSequence, d: Int) = Toast(t.toString())
    }
    fun show() { messages.tryEmit(text) }
}
