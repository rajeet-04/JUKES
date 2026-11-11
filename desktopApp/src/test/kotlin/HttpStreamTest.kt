import android.app.Application
import com.example.juke.network.JukesApi
import com.example.juke.network.SpotifyApi
import com.example.juke.services.MusicService
import com.example.juke.services.PlaybackManager
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/** A track whose audio is a bare backend URL (the "media type not supported" case) must play and seek. */
class HttpStreamTest {
    @Test fun playsBackendUrl() = runBlocking {
        val app = Application.instance; JukesApi.init(app)
        val hit = SpotifyApi.search("Blinding Lights The Weeknd", listOf("track")).tracks!!.items.first()
        val base = MusicService(app).streamTrack(SpotifyApi.spotifyTrackToSong(hit))
        val t = base.copy(localUri = "https://ms.rajeet.in/v1/audio/IBkT4Yww7zk")
        val pm = PlaybackManager.getInstance(app)
        pm.setQueue(listOf(t), 0)
        var p = 0L; repeat(60) { kotlinx.coroutines.delay(500); p = pm.getCurrentPosition(); if (p > 2500) return@repeat }
        println("HTTP playing=${pm.isPlayingFlow.value} pos=$p")
        assertTrue(pm.isPlayingFlow.value && p > 2000, "http stream did not play")
        pm.seekTo(60_000); kotlinx.coroutines.delay(2500)
        println("HTTP after seek pos=${pm.getCurrentPosition()}")
        assertTrue(pm.getCurrentPosition() in 59_000..70_000, "seek failed")
        pm.release()
    }
}
