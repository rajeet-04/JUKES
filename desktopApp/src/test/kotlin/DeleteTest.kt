import android.app.Application
import com.example.juke.network.JukesApi
import com.example.juke.network.SpotifyApi
import com.example.juke.services.MusicService
import com.example.juke.services.PlaybackManager
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/** Delete the song that is playing (and one that is not), through the same call the UI uses. */
class DeleteTest {
    @Test fun deleteViaViewModelWithQueue() = runBlocking {
        Thread.setDefaultUncaughtExceptionHandler { t, e -> println("UNCAUGHT in ${t.name}"); e.printStackTrace(System.out) }
        val app = Application.instance
        JukesApi.init(app)
        val svc = MusicService(app)
        val hits = SpotifyApi.search("The Weeknd Starboy", listOf("track")).tracks!!.items.take(2)
        val tracks = hits.map { svc.streamTrack(SpotifyApi.spotifyTrackToSong(it)) }
        val pm = PlaybackManager.getInstance(app)
        pm.setQueue(tracks, 0)
        kotlinx.coroutines.delay(5000)
        var lvm: com.example.juke.viewmodels.LibraryViewModel? = null
        javax.swing.SwingUtilities.invokeAndWait { lvm = com.example.juke.viewmodels.LibraryViewModel(app) }
        println("DEL2 before: queue=${pm.queueFlow.value.size} playing=${pm.isPlayingFlow.value}")
        javax.swing.SwingUtilities.invokeAndWait { lvm!!.stageRemoval(listOf(tracks[0])) }
        kotlinx.coroutines.delay(9000)
        println("DEL2 after: queue=${pm.queueFlow.value} playing=${pm.isPlayingFlow.value} pos=${pm.getCurrentPosition()}")
        kotlinx.coroutines.delay(3000)
        pm.release()
    }

    @Test fun deletePlaying() = runBlocking {
        val app = Application.instance
        JukesApi.init(app)
        val svc = MusicService(app)
        val hit = SpotifyApi.search("Blinding Lights The Weeknd", listOf("track")).tracks!!.items.first()
        val track = svc.streamTrack(SpotifyApi.spotifyTrackToSong(hit))
        val pm = PlaybackManager.getInstance(app)
        pm.setQueue(listOf(track), 0)
        kotlinx.coroutines.delay(4000)
        println("DEL before: playing=${pm.isPlayingFlow.value} queue=${pm.queueFlow.value.size}")
        try { svc.deleteTracksAndFiles(listOf(track), reportErrors = true); println("DEL ok queue=${pm.queueFlow.value.size} playing=${pm.isPlayingFlow.value}") }
        catch (t: Throwable) { println("DEL FAILED"); t.printStackTrace(System.out); throw t }
        kotlinx.coroutines.delay(2000)
        pm.release()
    }
}
