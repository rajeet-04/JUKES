package android.util

object Log {
    const val VERBOSE = 2; const val DEBUG = 3; const val INFO = 4; const val WARN = 5; const val ERROR = 6
    fun println(prio: Int, t: String?, m: String?) = run { System.err.println("$t: $m"); 0 }
    private fun p(l: String, t: String?, m: String?, e: Throwable?) { System.err.println("$l/$t: $m" + (e?.let { "\n" + it.stackTraceToString() } ?: "")) }
    fun v(t: String?, m: String?) = 0
    fun d(t: String?, m: String?) = 0
    fun i(t: String?, m: String?) = 0
    fun w(t: String?, m: String?) = run { p("W", t, m, null); 0 }
    fun w(t: String?, m: String?, e: Throwable?) = run { p("W", t, m, e); 0 }
    fun w(t: String?, e: Throwable?) = run { p("W", t, null, e); 0 }
    fun e(t: String?, m: String?) = run { p("E", t, m, null); 0 }
    fun e(t: String?, m: String?, e: Throwable?) = run { p("E", t, m, e); 0 }
    fun getStackTraceString(e: Throwable?): String = e?.stackTraceToString() ?: ""
}
