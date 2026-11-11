package android.os

import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing

class Looper private constructor() { companion object { private val main = Looper(); fun getMainLooper() = main } }

class Handler(@Suppress("UNUSED_PARAMETER") l: Looper? = null) {
    private val jobs = HashMap<Runnable, Job>()
    fun post(r: Runnable) = postDelayed(r, 0)
    @Synchronized fun postDelayed(r: Runnable, ms: Long): Boolean { jobs[r] = GlobalScope.launch(Dispatchers.Swing) { delay(ms); r.run() }; return true }
    @Synchronized fun removeCallbacks(r: Runnable) { jobs.remove(r)?.cancel() }
}

object SystemClock { fun elapsedRealtime() = System.nanoTime() / 1_000_000; fun uptimeMillis() = elapsedRealtime() }
