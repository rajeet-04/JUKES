package androidx.compose.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp

class Configuration(val screenWidthDp: Int, val screenHeightDp: Int) {
    val orientation get() = if (screenWidthDp > screenHeightDp) ORIENTATION_LANDSCAPE else ORIENTATION_PORTRAIT
    val smallestScreenWidthDp get() = minOf(screenWidthDp, screenHeightDp)
    val fontScale = 1f
    companion object { const val ORIENTATION_PORTRAIT = 1; const val ORIENTATION_LANDSCAPE = 2 }
}
val LocalConfiguration = compositionLocalOf { Configuration(1100, 720) }
