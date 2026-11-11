import com.example.juke.jam.Connect
import kotlin.test.Test
import kotlin.test.assertEquals

class ConnectQueueTest {
    private fun s(t: String, sp: String? = null) = Connect.Song(t, "A", spotifyId = sp)

    @Test fun currentSongLeadsWhenTheQueueStartsElsewhere() =
        assertEquals(listOf("cur", "n2"), Connect.currentFirst(s("cur", "c"), listOf(s("old", "o"), s("cur", "c"), s("n2", "n"))).map { it.title })

    @Test fun missingCurrentIsPrepended() =
        assertEquals(listOf("cur", "x"), Connect.currentFirst(s("cur", "c"), listOf(s("x", "x"))).map { it.title })

    @Test fun alreadyFirstIsUntouched() {
        val q = listOf(s("cur", "c"), s("x", "x"))
        assertEquals(q, Connect.currentFirst(s("cur", "c"), q))
    }
}
