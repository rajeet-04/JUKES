import android.app.Application
import com.example.juke.network.JukesApi
import com.example.juke.network.SpotifyApi
import com.example.juke.services.MusicService
import com.example.juke.services.PlaybackManager
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class SeekAccuracyTest {
    @Test fun seekLandsOnTarget() = runBlocking {
        val app = Application.instance; JukesApi.init(app)
        val hit = SpotifyApi.search("Blinding Lights The Weeknd", listOf("track")).tracks!!.items.first()
        val track = MusicService(app).streamTrack(SpotifyApi.spotifyTrackToSong(hit))
        val pm = PlaybackManager.getInstance(app)
        pm.setQueue(listOf(track), 0)
        kotlinx.coroutines.delay(4000)
        for (target in listOf(31_234L, 62_870L, 95_410L, 12_050L, 140_700L)) {
            pm.seekPrecise(target)
            val samples = (1..5).map { kotlinx.coroutines.delay(300); pm.getCurrentPosition() - target }
            println("SEEK target=$target deltas(ms, every 100ms)=$samples")
        }
        pm.release()
    }
}
