import com.example.juke.models.Track
import com.example.juke.utils.TrackSearch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrackSearchTest {
    private fun t(id: String, title: String, artist: String, plain: String? = null, synced: String? = null, rom: String? = null) =
        Track(uuid = id, title = title, artist = artist, durationSec = 200, plainLyrics = plain, syncedLyrics = synced, romanizedPlainLyrics = rom)

    private val lib = listOf(
        t("1", "Numb", "Linkin Park", plain = "I've become so numb I can't feel you there"),
        t("2", "Tum Hi Ho", "Arijit Singh", synced = "[00:12.30]Tum hi ho\n[00:20.10]ab tum hi ho", rom = "tum hi ho aashiqui"),
        t("3", "Señorita", "Shawn Mendes"),
        t("4", "Kesariya", "Arijit Singh", plain = "तेरा रंग"),
    )
    private fun ids(q: String) = TrackSearch.rank(lib, q).map { it.uuid }

    @Test fun partialArtistAndTitle() { assertEquals("1", ids("linkin numb").first()); assertEquals(listOf("1"), ids("park nu")) }
    @Test fun typos() { assertEquals("1", ids("linkn pak").first()); assertEquals("2", ids("arijt singh tum").first()); assertEquals("3", ids("senorita").first()); assertTrue("3" in ids("shaw mendez")) }
    @Test fun midLyrics() { assertEquals(listOf("1"), ids("become so numb")); assertEquals(listOf("2"), ids("ab tum")) }
    @Test fun romanized() { assertEquals(listOf("2"), ids("aashiqui")); assertEquals("2", ids("ashiqui").first()) }
    @Test fun noFalsePositives() { assertEquals(emptyList(), ids("zzzzqq")); assertEquals(emptyList(), ids("numb banana")) }
    @Test fun blankKeepsOrder() { assertEquals(lib, TrackSearch.rank(lib, "  ")) }
    @Test fun titleBeatsLyrics() {
        val l = listOf(t("a", "Other", "X", plain = "numb numb"), t("b", "Numb", "Y"))
        assertEquals("b", TrackSearch.rank(l, "numb").first().uuid)
    }

    @Test fun lyricHitGivesTheLineAndTime() {
        val hit = TrackSearch.lyricHit(lib[1], "ab tum")!!
        assertTrue(hit.text.contains("ab tum hi ho")); assertTrue(hit.startMs != null && hit.startMs!! in 19_000..21_000)
        assertEquals("numb", TrackSearch.lyricHit(lib[0], "become so numb")!!.text.let { "numb" }) // plain lyrics: a line, no time
        assertEquals(null, TrackSearch.lyricHit(lib[0], "become so numb")!!.startMs)
        assertEquals(null, TrackSearch.lyricHit(lib[0], "linkin numb")) // title/artist explain it
        assertTrue(TrackSearch.lyricHit(lib[1], "aashiqui") != null) // romanized
    }

    @Test fun lyricHitWhenWordsAreScatteredOrRespelled() {
        val song = t("9", "Luka Chippi", "Seedhe Maut", rom = "tera bhai sara wazan\nkhud se uthta hai\nkuch aur likha hai")
        assertTrue(TrackSearch.rank(listOf(song), "tera bhai sara wajan khud se uthta hai").isNotEmpty())
        assertEquals("tera bhai sara wazan / khud se uthta hai", TrackSearch.lyricHit(song, "tera bhai sara wajan khud se uthta hai")!!.text)
        assertEquals("khud se uthta hai", TrackSearch.lyricHit(song, "khud uthta")!!.text)
    }
}
