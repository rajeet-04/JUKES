package com.example.juke.utils

import android.content.Context
import android.util.Log
import androidx.core.content.edit

/**
 * Manages a persistent set of blacklisted artist names.
 *
 * When an artist is blacklisted, every recommendation whose artist field
 * (or title) contains the blacklisted name will be aggressively filtered out
 * before it even reaches Spotify validation.
 *
 * Storage: SharedPreferences (`artist_blacklist_prefs`), key = `blacklisted_artists`.
 * All names are stored **lowercased** for case-insensitive matching.
 */
object BlacklistManager {

    private const val TAG = "BlacklistManager"
    private const val PREFS_NAME = "artist_blacklist_prefs"
    private const val KEY_BLACKLISTED = "blacklisted_artists"

    // ── read / write ────────────────────────────────────────────────

    /** Return the full set of blacklisted artist names (all lowercase). */
    fun getBlacklistedArtists(context: Context): Set<String> {
        return context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_BLACKLISTED, emptySet()) ?: emptySet()
    }

    /** Add an artist name to the blacklist (case-insensitive). */
    fun addArtist(context: Context, artistName: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current =
            prefs.getStringSet(KEY_BLACKLISTED, emptySet())?.toMutableSet() ?: mutableSetOf()
        val normalized = artistName.trim().lowercase()
        if (normalized.isNotEmpty() && current.add(normalized)) {
            prefs.edit { putStringSet(KEY_BLACKLISTED, current) }
            Log.i(TAG, "Added \"$normalized\" to artist blacklist (total: ${current.size})")
        }
    }

    /** Remove an artist name from the blacklist. */
    fun removeArtist(context: Context, artistName: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current =
            prefs.getStringSet(KEY_BLACKLISTED, emptySet())?.toMutableSet() ?: mutableSetOf()
        val normalized = artistName.trim().lowercase()
        if (current.remove(normalized)) {
            prefs.edit { putStringSet(KEY_BLACKLISTED, current) }
            Log.i(TAG, "Removed \"$normalized\" from artist blacklist (total: ${current.size})")
        }
    }

    /** Clear the entire blacklist. */
    fun clearAll(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { remove(KEY_BLACKLISTED) }
        Log.i(TAG, "Artist blacklist cleared")
    }

    // ── matching helpers ────────────────────────────────────────────

    /**
     * Returns `true` if *any* of the given [artists] appears in the blacklist.
     *
     * Each artist name is parsed from the comma / "and" / "feat." separated
     * string and compared against every blacklisted entry using substring
     * matching (aggressive).
     */
    fun containsBlacklistedArtist(
        context: Context,
        artists: String,
        blacklist: Set<String> = getBlacklistedArtists(context)
    ): Boolean {
        if (blacklist.isEmpty()) return false

        val lowerArtists = artists.lowercase()

        // First: simple substring check on the whole artist string
        for (blocked in blacklist) {
            if (lowerArtists.contains(blocked)) return true
        }

        return false
    }

    /**
     * Returns `true` if the song **title** mentions a blacklisted artist
     * (e.g. "SongName feat. BlockedArtist").
     */
    fun titleContainsBlacklistedArtist(
        context: Context,
        title: String,
        blacklist: Set<String> = getBlacklistedArtists(context)
    ): Boolean {
        if (blacklist.isEmpty()) return false
        val lowerTitle = title.lowercase()
        return blacklist.any { lowerTitle.contains(it) }
    }
}
