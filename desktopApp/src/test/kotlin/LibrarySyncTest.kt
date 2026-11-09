import com.example.juke.account.Account
import com.example.juke.account.LibrarySync
import kotlin.test.Test
import kotlin.test.assertEquals

class LibrarySyncTest {
    private fun m(base: Map<String, Int>?, local: Map<String, Int>, remote: Map<String, Int>) = LibrarySync.merge(base, local, remote)

    @Test fun firstSyncTakesTheAccountsCopyAndAddsLocalOnly() =
        assertEquals(mapOf("a" to 2, "b" to 1, "c" to 3), m(null, mapOf("a" to 1, "b" to 1), mapOf("a" to 2, "c" to 3)))

    @Test fun ourChangeWinsTheirsComesIn() =
        assertEquals(mapOf("a" to 9, "b" to 7), m(mapOf("a" to 1, "b" to 1), mapOf("a" to 9, "b" to 1), mapOf("a" to 1, "b" to 7)))

    @Test fun removalsTravelBothWays() {
        // removed here, untouched there: gone; removed there, untouched here: gone
        assertEquals(emptyMap(), m(mapOf("a" to 1, "b" to 1), mapOf("b" to 1), mapOf("a" to 1)))
    }

    @Test fun anEditBeatsARemoval() {
        assertEquals(mapOf("a" to 5), m(mapOf("a" to 1), mapOf("a" to 5), emptyMap()))
        assertEquals(mapOf("a" to 6), m(mapOf("a" to 1), emptyMap(), mapOf("a" to 6)))
    }

    @Test fun newOnBothSidesKeepsBoth() =
        assertEquals(mapOf("x" to 1, "y" to 2), m(emptyMap(), mapOf("x" to 1), mapOf("y" to 2)))

    @Test fun twoDevicesWithDifferentCopiesOfASongAgree() {
        val mine = LibrarySync.Song("u1", "Song", "A", "file:/art.jpg", 200, sp = "sp1", fav = true)
        val theirs = LibrarySync.Song("u2", "Song (Remastered)", "A", "https://art", 201, yt = "yt1", sp = "sp1", fav = false)
        val doc = with(LibrarySync) { LibrarySync.Doc(songs = mapOf("sp1" to mine)).informedBy(LibrarySync.Doc(songs = mapOf("sp1" to theirs))) }
        assertEquals(theirs.copy(fav = true), doc.songs["sp1"]) // their details, our like: equal docs once likes match, so no re-upload
    }

    @Test fun aSyncThatWouldWipeTheLibraryIsRefused() {
        val songs = (1..40).associate { "s$it" to LibrarySync.Song("u$it", "T$it", "A", fav = true) }
        val local = LibrarySync.Doc(songs = songs)
        assert(LibrarySync.lost(local, LibrarySync.Doc(songs = songs.entries.take(5).associate { it.toPair() })) != null)
        assertEquals(null, LibrarySync.lost(local, LibrarySync.Doc(songs = songs - "s1" - "s2"))) // a couple of real removals go through
    }

    @Test fun passwordStretchIsStableAndKeyedByEmail() {
        val k = Account.stretch(" A@B.co", "hunter22")
        assertEquals(64, k.length)
        assertEquals(k, Account.stretch("a@b.co", "hunter22"))
        assert(k != Account.stretch("c@b.co", "hunter22"))
    }
}
