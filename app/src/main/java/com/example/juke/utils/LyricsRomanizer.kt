package com.example.juke.utils

import android.icu.text.Transliterator
import android.os.Build
import androidx.annotation.RequiresApi

private val lrcTimestampRegex = """^(\s*\[\d{2}:\d{2}\.\d{1,3}]\s*)(.*)$""".toRegex()

@RequiresApi(Build.VERSION_CODES.Q)
object LyricsRomanizer {
    private val transliterator: Transliterator by lazy {
        Transliterator.getInstance("Any-Latin; Latin-ASCII")
    }

    fun romanizeText(text: String): String {
        return transliterator.transliterate(text)
    }

    fun romanizeSyncedLyrics(syncedLyrics: String): String {
        return syncedLyrics
            .lineSequence().joinToString("\n") { line ->
                val match = lrcTimestampRegex.find(line)
                if (match != null) {
                    val prefix = match.groupValues[1]
                    val lyricText = match.groupValues[2]
                    prefix + romanizeText(lyricText)
                } else {
                    line
                }
            }
    }
}
