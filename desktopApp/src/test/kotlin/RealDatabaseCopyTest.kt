import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.example.juke.database.DB_VERSION
import com.example.juke.database.DatabaseBackup
import com.example.juke.database.MIGRATION_12_13
import com.example.juke.database.MusicDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Upgrade rehearsal on a COPY of a real library. Runs only when REAL_DB_COPY points at a folder holding
 * music_database.db (never the live one); the copy is rewound to version 12 first.
 */
class RealDatabaseCopyTest {
    @Test fun realLibrarySurvivesTheUpgrade() = runBlocking {
        val dir = System.getenv("REAL_DB_COPY")?.let(::File) ?: return@runBlocking
        val file = File(dir, "music_database.db")
        fun count(table: String): Int = BundledSQLiteDriver().open(file.path).use { c -> c.prepare("SELECT count(*) FROM $table").use { it.step(); it.getLong(0).toInt() } }
        val tracks = count("tracks"); val playlists = count("playlists")
        BundledSQLiteDriver().open(file.path).use { c ->
            listOf("last_play_at", "last_play_seq", "last_skip_at").forEach { c.execSQL("ALTER TABLE song_exposure DROP COLUMN $it") }
            c.execSQL("DROP TABLE room_master_table"); c.execSQL("PRAGMA user_version = 12")
        }
        assertEquals(12, DatabaseBackup.storedVersion(file))
        val backup = DatabaseBackup.beforeUpgrade(file, 12, DB_VERSION)
        assertTrue(backup != null && backup.length() > 0, "a copy is kept before the upgrade")
        val db = Room.databaseBuilder<MusicDatabase>(file.path).setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO).addMigrations(MIGRATION_12_13).build()
        db.songExposureDao().get("warm-up"); db.close()
        assertEquals(tracks, count("tracks"), "tracks")
        assertEquals(playlists, count("playlists"), "playlists")
        println("REALDB upgraded 12->13 with $tracks tracks and $playlists playlists intact")
    }
}
