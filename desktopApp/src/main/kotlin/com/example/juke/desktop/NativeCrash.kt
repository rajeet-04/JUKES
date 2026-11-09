package com.example.juke.desktop

import com.example.juke.report.CrashReporter
import java.io.File

/**
 * A crash inside native code (JNA, the audio or graphics stack) kills the JVM, so no Kotlin handler runs. The JVM
 * leaves an hs_err log instead (-XX:ErrorFile in build.gradle.kts); the next launch turns it into a normal crash
 * report, which the existing Worker files as a GitHub issue.
 */
object NativeCrash {
    /** Must match -XX:ErrorFile in desktopApp/build.gradle.kts. */
    val dir = File(if (System.getProperty("os.name").orEmpty().startsWith("Windows")) "C:/Windows/Temp" else "/tmp")

    fun collect(context: android.content.Context) = runCatching {
        dir.listFiles { f -> f.name.startsWith("juke-hs_err_") && f.name.endsWith(".log") }?.forEach { f ->
            runCatching { report(context, f) }
            f.delete()
        }
    }

    private fun report(context: android.content.Context, f: File) {
        if (System.currentTimeMillis() - f.lastModified() > 7 * 86_400_000L) return
        val lines = f.useLines { it.take(400).toList() }
        val signal = lines.firstOrNull { it.trimStart('#', ' ').startsWith("SIG") || it.contains("EXCEPTION_ACCESS_VIOLATION") }
            ?.trimStart('#', ' ')?.substringBefore(" at pc")?.trim() ?: "fatal error"
        val problem = lines.indexOfFirst { it.contains("Problematic frame") }.let { if (it >= 0) lines.getOrNull(it + 1)?.trimStart('#', ' ') else null }.orEmpty()
        val frames = lines.filter { Regex("""^[CjJVv]\s+\[?[^\s\]]+""").containsMatchIn(it) && !it.startsWith("#") }.take(25)
        val thread = lines.firstOrNull { it.startsWith("Current thread") }?.substringAfter('"', "")?.substringBefore('"')?.ifEmpty { null } ?: "unknown"
        val trace = buildString {
            append("JVM native crash: $signal\n")
            if (problem.isNotEmpty()) append("\tat $problem\n")
            frames.forEach { append("\tat ").append(it.trim()).append('\n') }
        }
        CrashReporter.saveReport(context, "JVM native crash ($signal)", problem, trace, thread, f.lastModified())
    }
}
