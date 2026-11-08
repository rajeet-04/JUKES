import android.app.Application
import com.example.juke.network.JukesApi
import com.example.juke.services.PlaybackManager
import kotlinx.coroutines.runBlocking
import androidx.core.content.edit
import kotlin.test.Test

class RestoreTest {
    @Test fun restoreMissingFile() = runBlocking {
        val app = Application.instance; JukesApi.init(app)
        val dao = com.example.juke.database.MusicDatabase.getDatabase(app).trackDao()
        val src = dao.getAllTracks().first { it.spotifyId != null }.let { it }
        val ghost = src.copy(uuid = "ghost-test", localUri = System.getProperty("ghostUri") ?: "/nonexistent/ghost.mp3")
        dao.insertTrack(ghost)
        app.getSharedPreferences("playback_state_prefs", android.content.Context.MODE_PRIVATE).edit {
            putString("queue_track_ids", "ghost-test"); putInt("queue_start_index", 0); putLong("playback_position", 30000L); putBoolean("has_saved_state", true) }
        val t0 = System.currentTimeMillis()
        val pm = PlaybackManager.getInstance(app); pm.initialize()
        try { repeat(400) { kotlinx.coroutines.delay(500); if (pm.getDuration() > 0 && true) { println("RESTORE2 ready after ${System.currentTimeMillis() - t0}ms uri=${pm.currentTrackNow?.localUri}"); return@runBlocking } }
            println("RESTORE2 never ready (200s)") } finally { dao.deleteTracks(listOf("ghost-test")) }
    }

    @Test fun viewModelHydration() = runBlocking {
        val app = Application.instance; JukesApi.init(app)
        val t0 = System.currentTimeMillis()
        com.example.juke.services.PlaylistImportManager.get(app).resume()
        com.example.juke.jam.JamSession.resume(app)
        var vm: com.example.juke.viewmodels.MusicViewModel? = null
        javax.swing.SwingUtilities.invokeAndWait { vm = com.example.juke.viewmodels.MusicViewModel(app) }
        println("VMH constructed +${System.currentTimeMillis() - t0}ms")
        repeat(600) { kotlinx.coroutines.delay(250); val s = vm!!.uiState.value; if (s.currentTrack != null) { println("VMH track shown +${System.currentTimeMillis() - t0}ms queue=${s.queue.size}"); return@runBlocking } }
        println("VMH never shown")
    }

    @Test fun restoreTiming() = runBlocking {
        val app = Application.instance; JukesApi.init(app)
        val db = com.example.juke.database.MusicDatabase.getDatabase(app)
        val ids = db.trackDao().getAllTracks().map { it.uuid }
        println("RESTORE library=${ids.size}")
        val big = List(System.getProperty("qsize")?.toInt() ?: 300) { ids[it % ids.size] }
        app.getSharedPreferences("playback_state_prefs", android.content.Context.MODE_PRIVATE).edit {
            putString("queue_track_ids", big.joinToString(",")); putInt("queue_start_index", 40); putLong("playback_position", 30000L); putBoolean("has_saved_state", true) }
        val t0 = System.currentTimeMillis()
        val pm = PlaybackManager.getInstance(app)
        pm.initialize()
        repeat(240) { kotlinx.coroutines.delay(500); if (pm.hasRestoredState.value && pm.getDuration() > 0) { println("RESTORE ready after ${System.currentTimeMillis() - t0}ms dur=${pm.getDuration()}"); return@runBlocking } }
        println("RESTORE never ready restored=${pm.hasRestoredState.value}")
    }
}
