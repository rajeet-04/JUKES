# Unreleased Changes

## [Unreleased]

### Added

- **Word-by-word lyrics.** Songs with word timings on LRCLIB are sung word by word: the current line fills in as each word is sung. Works in the full lyrics view (phone and desktop), the phone mini player, and the line under the artwork.
- Lyrics come from the JUKES server first, which also finds timed lyrics from YouTube when LRCLIB has none, then from LRCLIB directly.
- Romanized Punjabi (Gurmukhi) is written the way people type it ("Socha tere baare"), not in academic transliteration.
- Romanized Urdu gets its short vowels back ("dukan", "mumkin", "mujhe" instead of "dakan", "mamkan", "majhe"). Saved Urdu romanizations are redone once.

### Changed

- Phone lyrics card: darker, even backdrop so every line is readable; lines already sung sit further back than the ones to come; lyrics no longer run under the sync and close buttons.
- Opening and closing the lyrics on the phone resizes the card in one motion.
- A song saved with plain lyrics only is checked again for timed lyrics once per app run.

### Fixed

- "Looking for lyrics..." no longer stays on screen forever on desktop; "Refresh lyrics" uses the new lookup.
- Dragging the phone player closed with lyrics open no longer leaves the artwork floating over the lyrics.
- Changing songs while paused (swiping the artwork, next/previous, picking from the queue) shows 0:00 right away instead of the last song's time.
- macOS Now Playing shows the JUKE icon instead of a generic one.
