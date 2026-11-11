package androidx.compose.ui.res

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.example.juke.R

private val names = mapOf(R.drawable.baseline_download_24 to "baseline_download_24", R.drawable.baseline_favorite_24 to "baseline_favorite_24",
    R.drawable.baseline_favorite_border_24 to "baseline_favorite_border_24", R.drawable.baseline_mix to "baseline_mix", R.drawable.baseline_play_24 to "baseline_play_24")

@Composable
fun painterResource(id: Int): Painter = painterResource("drawable/${names.getValue(id)}.xml")
