package com.example.juke.utils

object ArtistUtils {
    /**
     * Checks if two artist strings represent the same set of artists, ignoring order and case.
     * Handles delimiters: comma (,), ampersand (&), semi-colon (;), and potentially " feat. " or " ft. "
     */
    fun areArtistsEqual(artist1: String?, artist2: String?): Boolean {
        if (artist1 == null || artist2 == null) return artist1 == artist2
        
        // Normalize strings: lowercase and trim
        val a1 = normalizeArtist(artist1)
        val a2 = normalizeArtist(artist2)
        
        return a1 == a2
    }
    
    /**
     * Normalizes an artist credit into a set so order and separator differences compare equal.
     *
     * This intentionally treats `,`, `&`, `;`, `feat.` and `ft.` as equivalent separators.
     */
    private fun normalizeArtist(artist: String): Set<String> {
        // Replace common delimiters/separators with a unique token
        // We handle "," "&" ";" and "feat." "ft."
        // We want "Artist A, Artist B" == "Artist B & Artist A"
        
        return artist.lowercase()
            .replace(" feat. ", ",")
            .replace(" ft. ", ",")
            .replace(" & ", ",")
            .replace(";", ",")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }
}
