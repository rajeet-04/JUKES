package com.example.juke.ui.components

import androidx.compose.runtime.staticCompositionLocalOf

/** Opens the album / artist screen for a Spotify id, from any song row's menu. */
class OpenMedia(val album: (String) -> Unit, val artist: (String) -> Unit)

val LocalOpenMedia = staticCompositionLocalOf { OpenMedia({}, {}) }
