package com.example.juke.models

import java.util.Locale

/**
 * LRCLIB's `lyricsfile` (YAML, version 1.0) to enhanced LRC, which keeps word timings inline:
 * `[00:27.17]<00:27.20>I've <00:27.59>been <00:27.88>tryna <00:28.20>call`.
 *
 * Only the shape LRCLIB writes is read: `lines:` items with `text`, `start_ms`, `end_ms` and
 * `words:` items with `text`, `start_ms`. Returns null when no line has word timings (the
 * record's plain LRC is as good) or when the file has something this reader doesn't handle.
 */
internal object Lyricsfile {
    private class Item(val indent: Int, var text: String = "", var startMs: Long? = null) {
        val words = ArrayList<Item>()
    }

    private val ITEM = Regex("""^(\s*)-\s+text:\s?(.*)$""")
    private val KEY = Regex("""^(\s*)(start_ms|end_ms|text):\s?(.*)$""")

    fun toEnhancedLrc(yaml: String?): String? {
        if (yaml.isNullOrBlank() || "words:" !in yaml) return null
        val lines = ArrayList<Item>()
        var inLines = false
        var lineIndent = -1
        for (raw in yaml.lineSequence()) {
            if (raw.isBlank()) continue
            val indent = raw.length - raw.trimStart().length
            if (indent == 0 && !raw.startsWith("-")) { // a top-level key
                if (inLines) break
                inLines = raw.trimEnd() == "lines:"
                continue
            }
            if (!inLines) continue
            val item = ITEM.find(raw)
            if (item != null) {
                val at = item.groupValues[1].length
                val text = scalar(item.groupValues[2]) ?: return null
                if (lineIndent < 0) lineIndent = at
                if (at == lineIndent) lines += Item(at, text)
                else lines.lastOrNull()?.words?.add(Item(at, text)) ?: return null
                continue
            }
            val key = KEY.find(raw) ?: continue // "words:" and keys this reader has no use for
            val at = key.groupValues[1].length
            val line = lines.lastOrNull() ?: continue
            val owner = line.words.lastOrNull()?.takeIf { at == it.indent + 2 } ?: line.takeIf { at == it.indent + 2 } ?: continue
            when (key.groupValues[2]) {
                "start_ms" -> owner.startMs = key.groupValues[3].trim().toLongOrNull() ?: return null
                "text" -> owner.text = scalar(key.groupValues[3]) ?: return null
            }
        }
        if (lines.none { it.words.isNotEmpty() }) return null
        return lines.filter { it.startMs != null }.joinToString("\n") { line ->
            val words = line.words.filter { it.startMs != null }
            stamp('[', line.startMs!!, ']') + if (words.isEmpty()) line.text else words.joinToString("") { stamp('<', it.startMs!!, '>') + it.text }
        }
    }

    private fun stamp(open: Char, ms: Long, close: Char): String {
        val m = ms / 60_000
        return open + String.format(Locale.US, "%02d:%05.2f", m, (ms - m * 60_000) / 1000.0) + close
    }

    /** A YAML scalar on one line: plain, 'single' ('' is ') or "double" (backslash escapes). */
    private fun scalar(value: String): String? {
        val v = value.trimEnd()
        return when {
            v.startsWith("'") -> if (v.length >= 2 && v.endsWith("'")) v.substring(1, v.length - 1).replace("''", "'") else null
            v.startsWith("\"") -> if (v.length >= 2 && v.endsWith("\"") && !v.endsWith("\\\"")) unescape(v.substring(1, v.length - 1)) else null
            v.startsWith("|") || v.startsWith(">") -> null // block scalars: not written for line text
            else -> v.substringBefore(" #").trim()
        }
    }

    private fun unescape(s: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c != '\\' || i + 1 >= s.length) { out.append(c); i++; continue }
            when (val e = s[i + 1]) {
                'n' -> out.append('\n')
                't' -> out.append('\t')
                '0' -> out.append('\u0000')
                'x' -> { out.append(s.substring(i + 2, i + 4).toInt(16).toChar()); i += 2 }
                'u' -> { out.append(s.substring(i + 2, i + 6).toInt(16).toChar()); i += 4 }
                'U' -> { out.appendCodePoint(s.substring(i + 2, i + 10).toInt(16)); i += 8 }
                else -> out.append(e)
            }
            i += 2
        }
        return out.toString()
    }
}

/** This result with LRCLIB's word timings, when its lyricsfile has them, as enhanced LRC. */
internal fun LRCLibResult.withWordSync(): LRCLibResult =
    runCatching { Lyricsfile.toEnhancedLrc(lyricsfile) }.getOrNull()?.let { copy(syncedLyrics = it) } ?: this
