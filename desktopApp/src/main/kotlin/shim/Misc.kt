package android.os

object Build {
    const val MANUFACTURER = "Desktop"
    val MODEL: String get() = System.getProperty("os.name") ?: "Desktop"
    const val DEVICE = "desktop"
    object VERSION { const val SDK_INT = 34; val RELEASE: String get() = System.getProperty("os.version") ?: "" }
    object VERSION_CODES { const val Q = 29; const val R = 30; const val S = 31; const val TIRAMISU = 33 }
}
