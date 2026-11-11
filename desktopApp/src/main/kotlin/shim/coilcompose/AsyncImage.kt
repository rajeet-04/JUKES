package coil.compose

import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.DefaultAlpha
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import coil.ImageLoader
import coil.request.ImageRequest

@Composable
fun AsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    placeholder: Painter? = null,
    error: Painter? = null,
    fallback: Painter? = null,
    alignment: Alignment = Alignment.Center,
    contentScale: ContentScale = ContentScale.Fit,
    alpha: Float = DefaultAlpha,
    colorFilter: ColorFilter? = null,
    filterQuality: FilterQuality = FilterQuality.Low,
) {
    val data = (model as? ImageRequest)?.data ?: model
    val key = (model as? ImageRequest)?.memoryCacheKey
    val loader = ImageLoader.shared
    val img by produceState<ImageBitmap?>(loader.cached(key ?: data?.toString().orEmpty()), data, key) { value = loader.load(data, key) }
    img?.let { Image(it, contentDescription, modifier, alignment, contentScale, alpha, colorFilter, filterQuality) }
        ?: (if (data == null) fallback else placeholder)?.let { Image(it, contentDescription, modifier, alignment, contentScale, alpha, colorFilter) }
        ?: androidx.compose.foundation.layout.Box(modifier)
}
