# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users
Phone listeners who play, queue, search and download music on the go, mostly one-handed and mostly in dark theme. Thumb reach matters.

## Product Purpose
JUKE is an Android music player that combines Spotify search and metadata, YouTube Music recommendations, and local or streamed playback. It supports offline downloads, synced lyrics, queue management, playlists, and an equalizer with audio effects. Success is a fast path from intent to playing audio, with the player staying readable and responsive.

## Capabilities and Constraints
- Jetpack Compose, Material 3, Kotlin 2.0.21, min SDK 26, compile/target SDK 36.
- Media3/ExoPlayer playback in a foreground service; album-art color extraction (`ExtractedColors`) already tints the theme.
- Screens: Home, Search, Library, Player, Album/Artist/Playlist detail, Audio Settings, Purge Selection. Components include MiniPlayer, Queue and Lyrics sheets, and playlist dialogs.
- Haze (backdrop blur library) is approved as a new dependency for the glass treatment.

## Brand Commitments
The user asked for a "liquid glass" visual direction across the whole app. Material 3 structure (navigation, Back behavior, touch targets) stays. Album-art color extraction stays and drives tint.

## Evidence on Hand
README and release notes in the repo root and `docs/`. No screenshots reviewed in this session.

## Product Principles
- Playback first: controls and track identity are always legible.
- Glass is a skin over Material 3 structure, never a new navigation model.
- Album art is the color source; the interface borrows from it rather than competing with it.
- Reachable one-handed.

## Accessibility & Inclusion
Lyrics, titles and transport controls must remain readable over any album art (WCAG AA contrast). 48dp minimum touch targets. Honor system Remove animations and font scale.
