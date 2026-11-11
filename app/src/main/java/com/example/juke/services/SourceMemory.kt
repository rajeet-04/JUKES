package com.example.juke.services

import android.content.Context
import androidx.core.content.edit
import com.example.juke.network.AlexaBackendApi

/** The audio providers a song can be fetched from. BACKEND = the Alexa skill server. */
enum class Source { BACKEND, SPOTSAVER, GAMEPVZ, SPOTMATE }

/**
 * Remembers which provider produced each song's audio and which providers the user rejected for a
 * song ("wrong song" → refetch). Later pulls of that song put rejected providers last, so a bad source
 * is not picked again. Persisted so it survives restarts.
 */
class SourceMemory(
    context: Context,
    private val backendEnabled: Boolean = AlexaBackendApi.isConfigured
) {
    private val prefs = context.getSharedPreferences("source_memory", Context.MODE_PRIVATE)

    /** Providers this build can use: the backend only when its URL and key are baked in. */
    val available: List<Source> get() = Source.entries.filter { it != Source.BACKEND || backendEnabled }

    /** Provider that produced the audio currently stored for [uuid], if known. */
    fun lastUsed(uuid: String): Source? = prefs.getString("used:$uuid", null)?.toSourceOrNull()

    fun recordUsed(uuid: String, source: Source) = prefs.edit { putString("used:$uuid", source.name) }

    /** Providers the user rejected for this song. */
    fun avoided(songKey: String): Set<Source> =
        prefs.getString("avoid:$songKey", null)?.split(',')?.mapNotNull { it.toSourceOrNull() }?.toSet() ?: emptySet()

    fun avoid(songKey: String, source: Source) {
        val next = avoided(songKey) + source
        // Every provider rejected means the problem is the song, not the source: start over.
        if (available.all { it in next }) prefs.edit { remove("avoid:$songKey") }
        else prefs.edit { putString("avoid:$songKey", next.joinToString(",") { it.name }) }
    }

    /** User's preferred first provider (Advanced settings), or null for the automatic order. */
    var preferred: Source?
        get() = prefs.getString("preferred", null)?.toSourceOrNull()
        set(value) = prefs.edit { if (value == null) remove("preferred") else putString("preferred", value.name) }

    /** How many songs currently have a rejected provider. */
    fun rejectedSongCount(): Int = prefs.all.keys.count { it.startsWith("avoid:") }

    /** Forget every rejected provider (the per-song "wrong song" history). */
    fun resetRejected() = prefs.edit { prefs.all.keys.filter { it.startsWith("avoid:") }.forEach { remove(it) } }

    /**
     * Provider order for one pull of [songKey]: the backend (when configured), Spotsaver, then the
     * legacy pair in [gamepvzFirst] order, with rejected providers moved to the end.
     */
    fun order(songKey: String, gamepvzFirst: Boolean): List<Source> {
        val legacy = if (gamepvzFirst) listOf(Source.SPOTSAVER, Source.GAMEPVZ, Source.SPOTMATE)
        else listOf(Source.SPOTSAVER, Source.SPOTMATE, Source.GAMEPVZ)
        val base = (if (backendEnabled) listOf(Source.BACKEND) else emptyList()) + legacy
        val avoided = avoided(songKey)
        val ordered = base.filterNot { it in avoided } + base.filter { it in avoided }
        val first = preferred?.takeIf { it !in avoided && it in available } ?: return ordered
        return listOf(first) + ordered.filter { it != first }
    }

    private fun String.toSourceOrNull() = Source.entries.firstOrNull { it.name == this }
}
