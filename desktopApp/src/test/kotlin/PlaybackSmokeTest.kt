import android.app.Application
import com.example.juke.network.JukesApi
import com.example.juke.network.SpotifyApi
import com.example.juke.services.MusicService
import com.example.juke.services.PlaybackManager
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/** Real network + real player: search, stream via the backend, play, and see the clock move. */
class PlaybackSmokeTest {
    @Test fun searchStreamPlay() = runBlocking {
        val app = Application.instance
        JukesApi.init(app)
        val hit = SpotifyApi.search("Blinding Lights The Weeknd", listOf("track")).tracks!!.items.first()
        println("found: ${hit.name}")
        val track = MusicService(app).streamTrack(SpotifyApi.spotifyTrackToSong(hit))
        println("track uri: ${track.localUri} stream=${track.isStream}")
        val pm = PlaybackManager.getInstance(app)
        com.example.juke.services.NowPlaying.start(pm)
        pm.setQueue(listOf(track), 0)
        kotlinx.coroutines.delay(3000)
        var t = 0L
        repeat(40) { kotlinx.coroutines.delay(500); t = pm.getCurrentPosition(); if (t > 2500) return@repeat }
        println("playing=${pm.isPlayingFlow.value} pos=$t dur=${pm.getDuration()}")
        assertTrue(pm.isPlayingFlow.value && t > 2000, "player did not advance: pos=$t")
        run { // read back what macOS now shows in the Now Playing widget
            val f = { n: String -> com.sun.jna.Function.getFunction("objc", n) }
            val msg = f("objc_msgSend")
            val c = f("objc_getClass").invokePointer(arrayOf("MPNowPlayingInfoCenter"))
            val sel = { n: String -> f("sel_registerName").invokePointer(arrayOf(n)) }
            val center = msg.invokePointer(arrayOf(c, sel("defaultCenter")))
            val info = msg.invokePointer(arrayOf(center, sel("nowPlayingInfo")))
            val desc = msg.invokePointer(arrayOf(info, sel("description")))
            val text = msg.invokePointer(arrayOf(desc, sel("UTF8String"))).getString(0)
            println("NOWPLAYING: $text")
            assertTrue(text.contains("Blinding Lights"), "Now Playing info missing")
            assertTrue(text.contains("Artwork"), "Now Playing artwork missing")
        }
        try { pm.togglePlayPause() } catch (t: Throwable) { t.printStackTrace(System.out); throw t }
        kotlinx.coroutines.delay(1500)
        val pausedAt = pm.getCurrentPosition(); kotlinx.coroutines.delay(1500)
        println("after pause: playing=${pm.isPlayingFlow.value} pos=$pausedAt then ${pm.getCurrentPosition()}")
        assertTrue(!pm.isPlayingFlow.value && pm.getCurrentPosition() - pausedAt < 400, "pause failed")
        pm.togglePlayPause(); kotlinx.coroutines.delay(2500)
        println("after resume: playing=${pm.isPlayingFlow.value} pos=${pm.getCurrentPosition()}")
        assertTrue(pm.isPlayingFlow.value && pm.getCurrentPosition() > pausedAt, "resume failed")
        pm.seekTo(120_000); kotlinx.coroutines.delay(2500)
        println("after seek: pos=${pm.getCurrentPosition()}")
        println("fragmented seek ok=${pm.getCurrentPosition() in 119_000..130_000}")
        // same audio, remuxed to a plain (non-fragmented) mp4: does seeking work there?
        val plain = track.copy(localUri = "/private/tmp/claude-501/plain.m4a")
        if (java.io.File(plain.localUri!!).isFile) {
            pm.setQueue(listOf(plain), 0); kotlinx.coroutines.delay(3000)
            pm.seekTo(120_000); kotlinx.coroutines.delay(2500)
            println("plain mp4 after seek: pos=${pm.getCurrentPosition()} playing=${pm.isPlayingFlow.value}")
        }
        pm.release()
    }
}
