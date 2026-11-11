package com.example.juke.services

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamUrlResolverTest {
    @Test fun slowPrimaryDoesNotBlockPlayableFallback() = runBlocking {
        val stopped = CompletableDeferred<Unit>()
        val result = withTimeout(1000) {
            resolveStreamUrl(
                primary = { try { awaitCancellation() } finally { stopped.complete(Unit) } },
                fallback = { "playable" }
            )
        }
        assertEquals("playable", result)
        assertTrue(stopped.isCompleted)
    }

    @Test fun readyPrimaryDoesNotWaitForFallback() = runBlocking {
        assertEquals("primary", withTimeout(1000) {
            resolveStreamUrl(primary = { "primary" }, fallback = { awaitCancellation() })
        })
    }

    @Test fun cancellingPlaybackStopsBothProviders() = runBlocking {
        val primaryStarted = CompletableDeferred<Unit>()
        val fallbackStarted = CompletableDeferred<Unit>()
        val primaryStopped = CompletableDeferred<Unit>()
        val fallbackStopped = CompletableDeferred<Unit>()
        val job = launch {
            resolveStreamUrl(
                primary = {
                    primaryStarted.complete(Unit)
                    try { awaitCancellation() } finally { primaryStopped.complete(Unit) }
                },
                fallback = {
                    fallbackStarted.complete(Unit)
                    try { awaitCancellation() } finally { fallbackStopped.complete(Unit) }
                }
            )
        }
        withTimeout(1000) {
            primaryStarted.await()
            fallbackStarted.await()
            job.cancelAndJoin()
        }
        assertTrue(primaryStopped.isCompleted && fallbackStopped.isCompleted)
    }

    @Test fun failedProviderDoesNotCancelOtherProvider() = runBlocking {
        assertEquals("playable", resolveStreamUrl(
            primary = { throw IllegalStateException("expired") },
            fallback = { "playable" }
        ))
    }

    @Test fun bothFailuresAreReported() = runBlocking {
        try {
            resolveStreamUrl<String>(
                primary = { throw IllegalStateException("expired") },
                fallback = { throw IllegalArgumentException("unavailable") }
            )
            throw AssertionError("Expected resolution failure")
        } catch (e: Exception) {
            assertTrue(e is IllegalStateException || e is IllegalArgumentException)
        }
    }
}
