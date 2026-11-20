package com.example.juke.home

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Feed songs (by video id) that have no Spotify match, so they can't be played. Home stops showing
 * them as soon as one is found, for the rest of the app session.
 */
object FeedMisses {
    private val _ids = MutableStateFlow<Set<String>>(emptySet())
    val ids: StateFlow<Set<String>> = _ids.asStateFlow()

    fun add(videoId: String) = _ids.update { it + videoId }
}

/** [sections] without the songs in [misses]; a feed section left with no songs is dropped. */
fun withoutMisses(sections: List<HomeSection>, misses: Set<String>): List<HomeSection> {
    if (misses.isEmpty()) return sections
    fun List<FeedSong>.kept() = filterNot { it.videoId in misses }
    return sections.mapNotNull { s ->
        when (s) {
            is HomeSection.QuickPicks -> s.copy(songs = s.songs.kept()).takeIf { it.songs.isNotEmpty() }
            is HomeSection.SongRow -> s.copy(songs = s.songs.kept()).takeIf { it.songs.isNotEmpty() }
            is HomeSection.HeroMix -> s.copy(songs = s.songs.kept()).takeIf { it.songs.isNotEmpty() }
            is HomeSection.Ranked -> s.copy(songs = s.songs.kept()).takeIf { it.songs.isNotEmpty() }
            else -> s
        }
    }
}
