package android.content

import java.io.File
import java.util.Properties

/** Desktop stand-in for the Android Context: app dirs, SharedPreferences, URL opening. */
open class Context {
    companion object {
        const val MODE_PRIVATE = 0
        const val CONNECTIVITY_SERVICE = "connectivity"
        val dataDir: File by lazy {
            val home = System.getProperty("user.home")
            val os = System.getProperty("os.name").lowercase()
            val base = when {
                "mac" in os -> File(home, "Library/Application Support")
                "win" in os -> File(System.getenv("APPDATA") ?: "$home/AppData/Roaming")
                else -> File(System.getenv("XDG_DATA_HOME") ?: "$home/.local/share")
            }
            File(base, "JUKE").also { it.mkdirs() }
        }
    }
    open val applicationContext: Context get() = this
    open val filesDir: File by lazy { File(dataDir, "files").also { it.mkdirs() } }
    open val cacheDir: File by lazy { File(dataDir, "cache").also { it.mkdirs() } }
    val packageName: String get() = "com.example.juke"
    val contentResolver = ContentResolver()
    private val prefs = HashMap<String, Prefs>()
    @Synchronized fun getSharedPreferences(name: String, mode: Int): SharedPreferences = prefs.getOrPut(name) { Prefs(File(dataDir, "prefs/$name.properties")) }
    @Suppress("UNCHECKED_CAST") fun <T> getSystemService(c: Class<T>): T = (if (c == android.net.ConnectivityManager::class.java) android.net.ConnectivityManager() else error("no service ${c.simpleName}")) as T
    fun getSystemService(name: String): Any? = if (name == CONNECTIVITY_SERVICE) android.net.ConnectivityManager() else null
    fun startActivity(i: Intent) {
        val send = i.action == Intent.ACTION_SEND
        val text = (if (send) i.getStringExtra(Intent.EXTRA_TEXT) else i.data?.toString()) ?: return
        if (send) { // "share" on desktop = copy the text
            runCatching { java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(text), null) }
            android.widget.Toast.makeText(this, "Copied to clipboard", 0).show()
        } else if (text.startsWith("http")) runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(text)) }
    }
}

interface SharedPreferences {
    val all: Map<String, *>
    fun contains(k: String): Boolean
    fun getString(k: String, d: String?): String?
    fun getBoolean(k: String, d: Boolean): Boolean
    fun getInt(k: String, d: Int): Int
    fun getLong(k: String, d: Long): Long
    fun getFloat(k: String, d: Float): Float
    fun getStringSet(k: String, d: Set<String>?): Set<String>?
    fun edit(): Editor
    interface Editor {
        fun putString(k: String, v: String?): Editor
        fun putBoolean(k: String, v: Boolean): Editor
        fun putInt(k: String, v: Int): Editor
        fun putLong(k: String, v: Long): Editor
        fun putFloat(k: String, v: Float): Editor
        fun putStringSet(k: String, v: Set<String>?): Editor
        fun remove(k: String): Editor
        fun clear(): Editor
        fun apply()
        fun commit(): Boolean
    }
}

private class Prefs(private val file: File) : SharedPreferences {
    private val m = HashMap<String, String>()
    init { runCatching { file.inputStream().use { Properties().apply { load(it) }.forEach { (k, v) -> m[k as String] = v as String } } } }
    private fun save() { runCatching { file.parentFile.mkdirs(); file.outputStream().use { o -> Properties().also { p -> m.forEach { (k, v) -> p[k] = v } }.store(o, null) } } }
    override val all: Map<String, *> get() = synchronized(this) { HashMap(m) }
    @Synchronized override fun contains(k: String) = k in m
    @Synchronized override fun getString(k: String, d: String?) = m[k] ?: d
    override fun getBoolean(k: String, d: Boolean) = getString(k, null)?.toBooleanStrictOrNull() ?: d
    override fun getInt(k: String, d: Int) = getString(k, null)?.toIntOrNull() ?: d
    override fun getLong(k: String, d: Long) = getString(k, null)?.toLongOrNull() ?: d
    override fun getFloat(k: String, d: Float) = getString(k, null)?.toFloatOrNull() ?: d
    override fun getStringSet(k: String, d: Set<String>?) = getString(k, null)?.let { if (it.isEmpty()) emptySet() else it.split('\u001f').toSet() } ?: d
    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        val ops = ArrayList<(HashMap<String, String>) -> Unit>()
        fun put(k: String, v: String?) = apply { ops += { if (v == null) it.remove(k) else it[k] = v } }
        override fun putString(k: String, v: String?) = put(k, v)
        override fun putBoolean(k: String, v: Boolean) = put(k, v.toString())
        override fun putInt(k: String, v: Int) = put(k, v.toString())
        override fun putLong(k: String, v: Long) = put(k, v.toString())
        override fun putFloat(k: String, v: Float) = put(k, v.toString())
        override fun putStringSet(k: String, v: Set<String>?) = put(k, v?.joinToString("\u001f"))
        override fun remove(k: String) = put(k, null)
        override fun clear() = apply { ops += { it.clear() } }
        override fun apply() { synchronized(this@Prefs) { ops.forEach { it(m) }; save() } }
        override fun commit(): Boolean { apply(); return true }
    }
}

class Intent() {
    companion object {
        const val ACTION_VIEW = "view"; const val ACTION_SEND = "send"
        const val EXTRA_TEXT = "text"; const val EXTRA_SUBJECT = "subject"
        const val FLAG_ACTIVITY_NEW_TASK = 1
        fun createChooser(i: Intent, title: CharSequence?) = i
    }
    constructor(action: String) : this() { this.action = action }
    constructor(action: String, uri: android.net.Uri?) : this() { this.action = action; data = uri }
    var action: String? = null
    var data: android.net.Uri? = null
    var type: String? = null
    val dataString: String? get() = data?.toString()
    private val extras = HashMap<String, Any?>()
    fun putExtra(k: String, v: String?) = apply { extras[k] = v }
    fun getStringExtra(k: String) = extras[k] as? String
    fun addFlags(f: Int) = this
    fun setPackage(p: String?) = this
}
