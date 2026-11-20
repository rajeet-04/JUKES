package com.example.juke.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistLinksTest {
    @Test fun `every artist gets its own entry, by name`() {
        assertEquals(
            listOf("Ruhav" to "a1", "Niteesh Singh" to "a2", "TheGreyKid" to "a3"),
            artistLinks("Ruhav, Niteesh Singh, TheGreyKid", listOf("a1", "a2", "a3"))
        )
    }

    @Test fun `names that don't line up with the ids fall back to the first artist`() {
        assertEquals(listOf("Salem Sandhu" to "a1"), artistLinks("Salem Sandhu, Taimour Baig", listOf("a1", "a2", "a3")))
    }

    @Test fun `no ids means no artist entries`() {
        assertEquals(emptyList<Pair<String, String>>(), artistLinks("ansh", null))
    }
}
