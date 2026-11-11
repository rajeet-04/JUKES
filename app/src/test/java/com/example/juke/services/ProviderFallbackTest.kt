package com.example.juke.services

import kotlinx.coroutines.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ProviderFallbackTest {
    @Test fun successfulNewProviderSkipsLegacyRequest() = runBlocking {
        assertEquals("new", preferNewProvider(primary = { "new" }, fallback = { "legacy" }))
    }
    @Test fun failedNewProviderUsesLegacy() = runBlocking {
        assertEquals("legacy", preferNewProvider<String>(primary = { error("HTTP 403") }, fallback = { "legacy" }))
    }
    @Test fun timedOutNewProviderUsesLegacy() = runBlocking {
        assertEquals("legacy", preferNewProvider<String>(primary = { withTimeout(10) { awaitCancellation() } }, fallback = { "legacy" }))
    }
    @Test fun userCancellationDoesNotStartFallback() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        var fallbackCalled = false
        val job = launch {
            preferNewProvider(primary = { entered.complete(Unit); awaitCancellation() }, fallback = { fallbackCalled = true })
        }
        withTimeout(1000) { entered.await(); job.cancelAndJoin() }
        assertFalse(fallbackCalled)
    }
}
