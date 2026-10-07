package com.example.juke.services

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.milliseconds

/** First successful provider wins; failures do not cancel a healthy provider. */
internal suspend fun <T> resolveStreamUrl(primary: suspend () -> T, fallback: suspend () -> T): T = coroutineScope {
    val results = Channel<Result<T>>(Channel.UNLIMITED)
    val jobs = listOf(primary, fallback).map { provider ->
        launch {
            val result = try {
                Result.success(withTimeout(10_000L.milliseconds) { provider() })
            } catch (e: CancellationException) {
                // A provider timeout is a failure; caller cancellation must propagate.
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(e)
            }
            results.send(result)
        }
    }
    try {
        val first = results.receive()
        if (first.isSuccess) first.getOrThrow()
        else {
            val second = results.receive()
            if (second.isSuccess) second.getOrThrow()
            else throw IllegalStateException("Both stream providers failed", second.exceptionOrNull()).apply {
                first.exceptionOrNull()?.let { addSuppressed(it) }
            }
        }
    } finally {
        jobs.forEach { it.cancel() }
        results.close()
    }
}
