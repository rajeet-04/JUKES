package coil.request

import android.content.Context
import android.graphics.drawable.Drawable

class ImageRequest private constructor(val data: Any?, val memoryCacheKey: String?) {
    class Builder(@Suppress("UNUSED_PARAMETER") c: Context) {
        private var data: Any? = null; private var key: String? = null
        fun data(d: Any?) = apply { data = d }
        fun size(px: Int) = this
        fun allowHardware(b: Boolean) = this
        fun crossfade(b: Boolean) = this
        fun memoryCacheKey(k: String?) = apply { key = k }
        fun diskCacheKey(k: String?) = this
        fun build() = ImageRequest(data, key)
    }
}
sealed interface ImageResult
class SuccessResult(val drawable: Drawable) : ImageResult
class ErrorResult : ImageResult
