package android.util

import java.io.File

/**
 * Desktop logger. Warnings and errors go to stderr; everything (verbose and up) goes to <app data>/logs/juke.log,
 * which rotates at 3 MB, so a packaged app without a console (Windows) can still be diagnosed.
 * Set JUKE_DEBUG=1 to also print everything to stderr.
 */
object Log {
    const val VERBOSE = 2; const val DEBUG = 3; const val INFO = 4; const val WARN = 5; const val ERROR = 6
    private val debug = System.getenv("JUKE_DEBUG") != null
    private val file: File? by lazy {
        runCatching {
            val f = File(android.content.Context.dataDir, "logs/juke.log").also { it.parentFile.mkdirs() }
            if (f.length() > 3_000_000) f.renameTo(File(f.parentFile, "juke.old.log"))
            f
        }.getOrNull()
    }
    private val stamp = java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS")

    private fun emit(l: String, t: String?, m: String?, e: Throwable?, toErr: Boolean) {
        val line = "$l/$t: $m" + (e?.let { "\n" + it.stackTraceToString() } ?: "")
        if (toErr || debug) System.err.println(line)
        runCatching { file?.appendText("${java.time.LocalDateTime.now().format(stamp)} $line\n") }
    }
    private fun p(l: String, t: String?, m: String?, e: Throwable?) = emit(l, t, m, e, l == "W" || l == "E")

    fun println(prio: Int, t: String?, m: String?) = run { p(if (prio >= ERROR) "E" else if (prio == WARN) "W" else "I", t, m, null); 0 }
    fun v(t: String?, m: String?) = run { p("V", t, m, null); 0 }
    fun d(t: String?, m: String?) = run { p("D", t, m, null); 0 }
    fun i(t: String?, m: String?) = run { p("I", t, m, null); 0 }
    fun w(t: String?, m: String?) = run { p("W", t, m, null); 0 }
    fun w(t: String?, m: String?, e: Throwable?) = run { p("W", t, m, e); 0 }
    fun w(t: String?, e: Throwable?) = run { p("W", t, null, e); 0 }
    fun e(t: String?, m: String?) = run { p("E", t, m, null); 0 }
    fun e(t: String?, m: String?, e: Throwable?) = run { p("E", t, m, e); 0 }
    fun getStackTraceString(e: Throwable?): String = e?.stackTraceToString() ?: ""
}
