package com.example.juke.viewmodels

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.milliseconds

/** Serializes removal batches; Undo cancels the timer, never a deletion already committing. */
internal class LibraryRemovalUndo(
    private val scope: CoroutineScope,
    private val commit: suspend (PendingLibraryRemoval) -> Unit,
    private val onChanged: (PendingLibraryRemoval?, Boolean) -> Unit,
    private val onFailure: (PendingLibraryRemoval) -> Unit,
) {
    var pending: PendingLibraryRemoval? = null
        private set
    var committing: Boolean = false
        private set
    private var timer: Job? = null
    private val mutex = Mutex()

    suspend fun stage(incoming: PendingLibraryRemoval): Boolean {
        return incoming.tracks.isNotEmpty() && !committing && mutex.withLock {
            val previous = pending
            // Never shorten the five-second promise by committing a different operation early.
            if (previous != null && previous.playlist?.id != incoming.playlist?.id) return@withLock false
            timer?.cancel()
            val next = previous?.merge(incoming) ?: incoming
            pending = next
            onChanged(next, false)
            timer = scope.launch {
                delay(5000.milliseconds)
                mutex.withLock { if (pending == next) finish(next) }
            }
            true
        }
    }

    fun undo(): Boolean {
        if (committing || pending == null) return false
        timer?.cancel()
        pending = null
        onChanged(null, false)
        return true
    }

    private suspend fun finish(removal: PendingLibraryRemoval) {
        committing = true
        onChanged(removal, true)
        try {
            withContext(NonCancellable) { commit(removal) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            onFailure(removal)
        } finally {
            pending = null
            committing = false
            onChanged(null, false)
        }
    }
}
