import com.example.juke.account.LibrarySync
import com.example.juke.account.LibrarySync.Doc
import com.example.juke.account.LibrarySync.Playlist
import com.example.juke.account.LibrarySync.Song
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** A device whose library vanished since its last sync must not tell the account "I deleted everything". */
class SyncWipeGuardTest {
    private fun song(i: Int) = Song("u$i", "t$i", "a", null, 200, null, "sp$i", null, emptyList(), false)
    private fun doc(songs: Int, playlists: Int = 0) = Doc(
        songs = (1..songs).associate { "sp$it" to song(it) },
        playlists = (1..playlists).associate { "p$it" to Playlist("pl$it", songs = listOf("sp1")) }
    )

    @Test fun emptiedLibraryIsAWipe() { assertNotNull(LibrarySync.wiped(base = doc(906, 27), local = Doc())) }

    @Test fun resetDatabaseWithFewSongsLeftIsAWipe() { assertNotNull(LibrarySync.wiped(base = doc(906, 27), local = doc(10))) }

    @Test fun firstSyncHasNothingToCompareWith() { assertNull(LibrarySync.wiped(base = null, local = Doc())) }

    @Test fun removingAFewSongsIsNormal() { assertNull(LibrarySync.wiped(base = doc(906, 27), local = doc(900, 27))) }

    @Test fun smallLibrariesAreLeftAlone() { assertNull(LibrarySync.wiped(base = doc(8), local = Doc())) }

    @Test fun lostPlaylistsAloneAreAWipe() { assertNotNull(LibrarySync.wiped(base = doc(906, 27), local = doc(906, 0))) }

    @Test fun messageSaysHowToRecover() { assertEquals(true, LibrarySync.wiped(doc(906, 27), Doc())!!.contains("Log out")) }
}
