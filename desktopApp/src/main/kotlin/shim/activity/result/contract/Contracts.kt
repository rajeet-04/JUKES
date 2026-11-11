package androidx.activity.result.contract

import android.net.Uri
import androidx.activity.result.PickVisualMediaRequest
import java.awt.FileDialog
import java.awt.Frame

/** Desktop contracts open the native file dialog. */
abstract class ActivityResultContract<I, O> { abstract fun pick(input: I): O }

class ActivityResultContracts private constructor() {
    class PickVisualMedia : ActivityResultContract<PickVisualMediaRequest, Uri?>() {
        sealed interface VisualMediaType
        object ImageOnly : VisualMediaType
        object ImageAndVideo : VisualMediaType
        override fun pick(input: PickVisualMediaRequest): Uri? = choose(false, listOf("png", "jpg", "jpeg", "webp", "gif")).firstOrNull()
    }
    class OpenMultipleDocuments : ActivityResultContract<Array<String>, List<Uri>>() {
        override fun pick(input: Array<String>): List<Uri> = choose(true, listOf("mp3", "m4a", "flac", "wav", "ogg", "opus", "aac"))
    }
}

private fun choose(multi: Boolean, ext: List<String>): List<Uri> {
    val d = FileDialog(null as Frame?, "Choose", FileDialog.LOAD)
    d.isMultipleMode = multi
    d.setFilenameFilter { _, n -> ext.any { n.endsWith(".$it", true) } }
    d.isVisible = true
    return d.files.map { Uri.fromFile(it) }
}
