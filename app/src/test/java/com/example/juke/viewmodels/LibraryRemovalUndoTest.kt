package com.example.juke.viewmodels

import com.example.juke.database.PlaylistEntity
import com.example.juke.models.Track
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryRemovalUndoTest {
    private fun removal(vararg ids: String, playlist: String? = null) = PendingLibraryRemoval(
        ids.map { Track(uuid = it, title = it, artist = "Artist", durationSec = 180) },
        playlist?.let { PlaylistEntity(id = it, name = it) },
    )

    @Test fun `single and batch files stay intact for the full undo window`() = runTest {
        val committed = mutableListOf<PendingLibraryRemoval>()
        val undo = LibraryRemovalUndo(this, { committed += it }, { _, _ -> }, { fail("Unexpected failure") })
        undo.stage(removal("a", "b"))
        runCurrent()
        advanceTimeBy(4999)
        runCurrent()
        assertTrue(committed.isEmpty())
        assertEquals(setOf("a", "b"), undo.pending?.trackIds)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(setOf("a", "b"), committed.single().trackIds)
        assertNull(undo.pending)
    }

    @Test fun `undo cancels permanent batch deletion`() = runTest {
        var commits = 0
        val undo = LibraryRemovalUndo(this, { commits++ }, { _, _ -> }, { fail("Unexpected failure") })
        undo.stage(removal("a", "b"))
        runCurrent()
        advanceTimeBy(4000)
        assertTrue(undo.undo())
        advanceUntilIdle()
        assertEquals(0, commits)
        assertNull(undo.pending)
        assertFalse(undo.undo())
    }

    @Test fun `successive removals in one playlist merge without duplicates and reset timer`() = runTest {
        val committed = mutableListOf<PendingLibraryRemoval>()
        val undo = LibraryRemovalUndo(this, { committed += it }, { _, _ -> }, { fail("Unexpected failure") })
        undo.stage(removal("a", playlist = "p"))
        runCurrent()
        advanceTimeBy(3000)
        undo.stage(removal("a", "b", playlist = "p"))
        runCurrent()
        advanceTimeBy(4999)
        runCurrent()
        assertTrue(committed.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf("a", "b"), committed.single().tracks.map { it.uuid })
    }

    @Test fun `different destinations cannot shorten the existing undo window`() = runTest {
        val committed = mutableListOf<PendingLibraryRemoval>()
        val undo = LibraryRemovalUndo(this, { committed += it }, { _, _ -> }, { fail("Unexpected failure") })
        assertTrue(undo.stage(removal("a", playlist = "p")))
        assertFalse(undo.stage(removal("b")))
        runCurrent()
        assertTrue(committed.isEmpty())
        assertEquals("p", undo.pending?.playlist?.id)
        advanceTimeBy(4999)
        runCurrent()
        assertTrue(committed.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals("p", committed.single().playlist?.id)
        assertTrue(undo.stage(removal("b")))
        assertTrue(undo.undo())
        advanceUntilIdle()
        assertEquals(1, committed.size)
    }

    @Test fun `removing from a playlist never hides the download in other library views`() {
        val pending = removal("a", playlist = "p")
        assertTrue(pending.hides("a", "p"))
        assertFalse(pending.hides("a", null))
        assertFalse(pending.hides("a", "other"))
        assertFalse(pending.hides("b", "p"))
        assertTrue(removal("a").hides("a", "other"))
    }

    @Test fun `failure restores visibility and reports the failed batch`() = runTest {
        val failed = mutableListOf<PendingLibraryRemoval>()
        var visiblePending: PendingLibraryRemoval? = null
        val undo = LibraryRemovalUndo(this, { error("Storage unavailable") },
            { pending, _ -> visiblePending = pending }, { failed += it })
        undo.stage(removal("a", "b"))
        advanceUntilIdle()
        assertEquals(setOf("a", "b"), failed.single().trackIds)
        assertNull(visiblePending)
        assertFalse(undo.committing)
    }

    @Test fun `undo cannot cancel deletion once storage commit starts`() = runTest {
        val release = CompletableDeferred<Unit>()
        val undo = LibraryRemovalUndo(this, { release.await() }, { _, _ -> }, { fail("Unexpected failure") })
        undo.stage(removal("a"))
        runCurrent()
        advanceTimeBy(5000)
        runCurrent()
        assertTrue(undo.committing)
        assertFalse(undo.undo())
        undo.stage(removal("b"))
        runCurrent()
        assertEquals(setOf("a"), undo.pending?.trackIds)
        release.complete(Unit)
        advanceUntilIdle()
        assertNull(undo.pending)
    }
}
