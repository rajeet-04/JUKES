import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.example.juke.database.DatabaseBackup
import com.example.juke.database.MIGRATION_12_13
import com.example.juke.database.MusicDatabase
import com.example.juke.database.SongExposureEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** A desktop library saved by 3.1.x (database version 12) must open in 3.2.1 with its rows. */
class MigrationTest {
    private fun open(file: File) = Room.databaseBuilder<MusicDatabase>(file.path)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .addMigrations(MIGRATION_12_13)
        .build()

    @Test fun v12DatabaseKeepsRowsAfterUpgrade() = runBlocking {
        val file = File.createTempFile("juke-mig", ".db").also { it.delete(); it.deleteOnExit() }
        // Current schema, then rewound to what 3.1.x wrote: no exposure timestamps, version 12.
        open(file).also {
            it.songExposureDao().upsert(SongExposureEntity("a|b", playScore = 2.0, updatedAt = 5L))
            it.close()
        }
        BundledSQLiteDriver().open(file.path).also { c ->
            listOf("last_play_at", "last_play_seq", "last_skip_at").forEach { c.execSQL("ALTER TABLE song_exposure DROP COLUMN $it") }
            c.execSQL("DROP TABLE room_master_table")
            c.execSQL("PRAGMA user_version = 12")
            c.close()
        }
        val db = open(file)
        val row = db.songExposureDao().get("a|b")
        db.close()
        assertNotNull(row, "the row must survive the upgrade")
        assertEquals(2.0, row.playScore)
        assertEquals(null, row.lastPlayAt)
    }

    /** A 3.2-dev desktop build created the version-12 file with the new columns already in it: adding them again must not fail (it used to, and Room then reset the whole library). */
    @Test fun v12DatabaseThatAlreadyHasTheColumnsStillUpgrades() = runBlocking {
        val file = File.createTempFile("juke-mig2", ".db").also { it.delete(); it.deleteOnExit() }
        open(file).also {
            it.songExposureDao().upsert(SongExposureEntity("c|d", playScore = 5.0, updatedAt = 9L, lastPlayAt = 7L))
            it.close()
        }
        BundledSQLiteDriver().open(file.path).also { c ->
            c.execSQL("DROP TABLE room_master_table")
            c.execSQL("PRAGMA user_version = 12")
            c.close()
        }
        val db = open(file)
        val row = db.songExposureDao().get("c|d")
        db.close()
        assertNotNull(row, "the row must survive")
        assertEquals(7L, row.lastPlayAt)
    }

    @Test fun upgradeKeepsACopyOfTheOldDatabaseFirst() {
        val dir = File.createTempFile("juke-bk", "").also { it.delete(); it.mkdirs(); it.deleteOnExit() }
        val db = File(dir, "music_database.db").also { it.writeText("library v12") }
        File(dir, "music_database.db-wal").writeText("wal")
        val copy = DatabaseBackup.beforeUpgrade(db, fromVersion = 12, toVersion = 13)
        assertNotNull(copy)
        assertEquals("library v12", copy.readText())
        assertEquals("wal", File(copy.path + "-wal").readText())
        assertEquals(null, DatabaseBackup.beforeUpgrade(db, fromVersion = 13, toVersion = 13), "no copy when nothing changes")
    }
}
