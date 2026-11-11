package com.example.juke.services

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal suspend fun <T> preferNewProvider(primary: suspend () -> T, fallback: suspend () -> T): T {
    return try { primary() } catch (_: Exception) {
        // Provider-local timeouts can fall back; cancellation of playback must propagate.
        currentCoroutineContext().ensureActive()
        fallback()
    }
}
