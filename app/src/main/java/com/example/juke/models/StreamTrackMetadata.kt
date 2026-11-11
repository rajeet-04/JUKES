package com.example.juke.models

internal fun mergeCompletedStream(snapshot: Track, latest: Track): Track = latest.copy(localUri = snapshot.localUri)
