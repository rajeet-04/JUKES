package com.example.juke.network

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class RadioOrderTest {
    private fun <T> run(vararg sources: suspend () -> List<T>?): List<T> =
        runBlocking { with(JukesApi) { sources.toList().firstNonEmpty() } }

    @Test fun firstSourceWinsAndSecondIsNotCalled() {
        var secondCalled = false
        val out = run<String>({ listOf("a") }, { secondCalled = true; listOf("b") })
        assertEquals(listOf("a"), out)
        assertEquals(false, secondCalled)
    }

    @Test fun nullOrEmptyFallsThrough() {
        assertEquals(listOf("c"), run<String>({ null }, { emptyList() }, { listOf("c") }))
    }

    @Test fun nothingAvailableIsEmpty() {
        assertEquals(emptyList<String>(), run<String>({ null }))
    }
}
