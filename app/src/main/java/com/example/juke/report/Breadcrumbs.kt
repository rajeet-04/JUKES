package com.example.juke.report

/**
 * What the app was doing just before a crash, kept in memory and attached to the crash report. Event names only:
 * never titles, artists, search text, ids or anything else about the person.
 */
object Breadcrumbs {
    private const val MAX = 15
    private val ring = ArrayDeque<String>()
    private val started = System.currentTimeMillis()

    @Volatile var screen: String = ""
    @Volatile var playback: String = "idle"

    @Synchronized fun add(event: String) {
        ring.addLast("${(System.currentTimeMillis() - started) / 1000}s ${event.take(40)}")
        while (ring.size > MAX) ring.removeFirst()
    }

    fun screen(route: String) { screen = route.substringBefore('/').substringBefore('?'); add("open $screen") }
    fun playing(playing: Boolean) { val s = if (playing) "playing" else "paused"; if (playback != s) { playback = s; add(s) } }

    /** Extra fields for the report's "App" table. */
    @Synchronized fun context(): Map<String, String> {
        val rt = Runtime.getRuntime()
        val mb = 1024 * 1024
        return mapOf(
            "screen" to screen.ifEmpty { "unknown" },
            "playback" to playback,
            "uptime" to "${(System.currentTimeMillis() - started) / 60_000} min",
            "heap" to "${(rt.totalMemory() - rt.freeMemory()) / mb}/${rt.maxMemory() / mb} MB",
            "trail" to ring.joinToString(" › ")
        )
    }
}
