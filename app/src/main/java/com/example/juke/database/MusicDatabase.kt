package com.example.juke.database

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.juke.models.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Migration from version 1 to 2
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Add any schema changes from version 1 to 2 here
        // Example: database.execSQL("ALTER TABLE tracks ADD COLUMN new_column TEXT")
    }
}

/**
 * Migration from version 2 to 3
 * Adds notification_thumbnail_uri column for storing 64x64 thumbnails used in notifications
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tracks ADD COLUMN notification_thumbnail_uri TEXT")
    }
}

/**
 * Migration from version 3 to 4
 * Removes notification_thumbnail_uri column as we no longer store 64x64 thumbnails
 * Also ensures playlists table exists for users who may have skipped migrations
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // SQLite doesn't support DROP COLUMN directly, so we need to recreate the table
        db.execSQL(
            """
            CREATE TABLE tracks_new (
                uuid TEXT PRIMARY KEY NOT NULL,
                title TEXT NOT NULL,
                artist TEXT NOT NULL,
                thumbnail_uri TEXT,
                duration_sec INTEGER NOT NULL,
                local_uri TEXT,
                yt_video_id TEXT,
                synced_lyrics TEXT,
                plain_lyrics TEXT,
                is_favourite INTEGER NOT NULL DEFAULT 0,
                play_count INTEGER NOT NULL DEFAULT 0,
                last_played_at TEXT
            )
        """
        )
        db.execSQL("INSERT INTO tracks_new SELECT uuid, title, artist, thumbnail_uri, duration_sec, local_uri, yt_video_id, synced_lyrics, plain_lyrics, is_favourite, play_count, last_played_at FROM tracks")
        db.execSQL("DROP TABLE tracks")
        db.execSQL("ALTER TABLE tracks_new RENAME TO tracks")

        // Ensure playlists table exists (create if missing)
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS playlists (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                description TEXT,
                thumbnail_uri TEXT,
                spotify_id TEXT,
                created_at INTEGER NOT NULL,
                track_count INTEGER NOT NULL DEFAULT 0
            )
        """
        )

        // Ensure playlist_tracks table exists (create if missing)
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS playlist_tracks (
                playlist_id TEXT NOT NULL,
                track_uuid TEXT NOT NULL,
                position INTEGER NOT NULL,
                added_at INTEGER NOT NULL,
                PRIMARY KEY(playlist_id, track_uuid, position),
                FOREIGN KEY(playlist_id) REFERENCES playlists(id) ON DELETE CASCADE,
                FOREIGN KEY(track_uuid) REFERENCES tracks(uuid) ON DELETE CASCADE
            )
        """
        )

        // Create indices for playlist_tracks
        db.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_tracks_playlist_id ON playlist_tracks(playlist_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_playlist_tracks_track_uuid ON playlist_tracks(track_uuid)")
    }
}

/**
 * Migration from version 4 to 5
 * Adds downloaded_at column to tracks table
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Add downloaded_at column, default to current timestamp for existing rows
        // We use System.currentTimeMillis() effectively by setting a default value, 
        // but SQLite DEFAULT expects a constant or expression. 
        // We'll set it to 0 initially or null? 
        // User asked: "previous tracks might not have that new column so initialize them to current time"
        // So we should update them.

        val currentTime = System.currentTimeMillis()
        db.execSQL("ALTER TABLE tracks ADD COLUMN downloaded_at INTEGER")
        db.execSQL("UPDATE tracks SET downloaded_at = $currentTime")
    }
}


/**
 * Migration from version 5 to 6
 * Adds spotify_id, album_spotify_id, and artist_spotify_ids columns
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tracks ADD COLUMN spotify_id TEXT")
        db.execSQL("ALTER TABLE tracks ADD COLUMN album_spotify_id TEXT")
        db.execSQL("ALTER TABLE tracks ADD COLUMN artist_spotify_ids TEXT")
    }
}

class Converters {
    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        return value?.let { Json.encodeToString(it) }
    }

    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        return value?.let { Json.decodeFromString(it) }
    }
}

/**
 * Room Database for JUKE music player.
 */
@Database(
    entities = [TrackEntity::class, PlaylistEntity::class, PlaylistTrackEntity::class],
    version = 6,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        @Volatile
        private var INSTANCE: MusicDatabase? = null

        fun getDatabase(context: Context): MusicDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MusicDatabase::class.java,
                    "music_database"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6
                    )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

/**
 * Room entity for storing track data.
 */
@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey
    val uuid: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "artist")
    val artist: String,

    @ColumnInfo(name = "thumbnail_uri")
    val thumbnailUri: String? = null,

    @ColumnInfo(name = "duration_sec")
    val durationSec: Int,

    @ColumnInfo(name = "local_uri")
    val localUri: String? = null,

    @ColumnInfo(name = "yt_video_id")
    val ytVideoId: String? = null,

    @ColumnInfo(name = "synced_lyrics")
    val syncedLyrics: String? = null,

    @ColumnInfo(name = "plain_lyrics")
    val plainLyrics: String? = null,

    @ColumnInfo(name = "is_favourite", defaultValue = "0")
    val isFavourite: Boolean = false,

    @ColumnInfo(name = "play_count", defaultValue = "0")
    val playCount: Int = 0,


    @ColumnInfo(name = "last_played_at")
    val lastPlayedAt: String? = null,

    @ColumnInfo(name = "downloaded_at")
    val downloadedAt: Long? = null,

    @ColumnInfo(name = "spotify_id")
    val spotifyId: String? = null,

    @ColumnInfo(name = "album_spotify_id")
    val albumSpotifyId: String? = null,

    @ColumnInfo(name = "artist_spotify_ids")
    val artistSpotifyIds: List<String>? = null
)

/**
 * Extension function to convert TrackEntity to Track model.
 */
fun TrackEntity.toTrack(): Track {
    return Track(
        uuid = uuid,
        title = title,
        artist = artist,
        thumbnailUri = thumbnailUri,
        durationSec = durationSec,
        localUri = localUri,
        ytVideoId = ytVideoId,
        syncedLyrics = syncedLyrics,
        plainLyrics = plainLyrics,
        isFavourite = isFavourite,
        playCount = playCount,
        lastPlayedAt = lastPlayedAt,
        downloadedAt = downloadedAt,
        spotifyId = spotifyId,
        albumSpotifyId = albumSpotifyId,
        artistSpotifyIds = artistSpotifyIds
    )
}

/**
 * Extension function to convert Track model to TrackEntity.
 */
fun Track.toEntity(): TrackEntity {
    return TrackEntity(
        uuid = uuid,
        title = title,
        artist = artist,
        thumbnailUri = thumbnailUri,
        durationSec = durationSec,
        localUri = localUri,
        ytVideoId = ytVideoId,
        syncedLyrics = syncedLyrics,
        plainLyrics = plainLyrics,
        isFavourite = isFavourite,
        playCount = playCount,
        lastPlayedAt = lastPlayedAt,
        downloadedAt = downloadedAt,
        spotifyId = spotifyId,
        albumSpotifyId = albumSpotifyId,
        artistSpotifyIds = artistSpotifyIds
    )
}

/**
 * Data Access Object for Track operations.
 */
@Dao
interface TrackDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: TrackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<TrackEntity>)

    @Query("SELECT * FROM tracks ORDER BY last_played_at DESC")
    suspend fun getAllTracks(): List<TrackEntity>

    @Query("SELECT * FROM tracks ORDER BY last_played_at DESC")
    fun getAllTracksFlow(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE uuid = :uuid")
    suspend fun getTrackByUuid(uuid: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE last_played_at IS NOT NULL ORDER BY last_played_at DESC LIMIT :limit")
    suspend fun getRecentlyPlayed(limit: Int = 10): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE is_favourite = 1 ORDER BY last_played_at DESC")
    suspend fun getFavourites(): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE is_favourite = 1 ORDER BY last_played_at DESC")
    fun getFavouritesFlow(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE local_uri IS NOT NULL ORDER BY last_played_at DESC")
    suspend fun getDownloadedTracks(): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE local_uri IS NOT NULL ORDER BY last_played_at DESC")
    fun getDownloadedTracksFlow(): Flow<List<TrackEntity>>

    @Query("UPDATE tracks SET is_favourite = :isFavourite WHERE uuid = :uuid")
    suspend fun updateTrackFavourite(uuid: String, isFavourite: Boolean)

    @Query("UPDATE tracks SET play_count = play_count + 1, last_played_at = :lastPlayedAt WHERE uuid = :uuid")
    suspend fun incrementPlayCount(uuid: String, lastPlayedAt: String)

    @Query("UPDATE tracks SET play_count = :playCount, last_played_at = :lastPlayedAt WHERE uuid = :uuid")
    suspend fun updateTrackPlayStats(uuid: String, playCount: Int, lastPlayedAt: String?)

    @Query("DELETE FROM tracks WHERE uuid = :uuid")
    suspend fun deleteTrack(uuid: String)

    @Query("DELETE FROM tracks WHERE uuid IN (:uuids)")
    suspend fun deleteTracks(uuids: List<String>)

    @Query("DELETE FROM tracks")
    suspend fun deleteAllTracks()

    @Query("SELECT * FROM tracks WHERE play_count > 0 ORDER BY play_count DESC LIMIT :limit")
    suspend fun getMostPlayed(limit: Int = 10): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE LOWER(TRIM(title)) = LOWER(TRIM(:title)) AND LOWER(TRIM(artist)) = LOWER(TRIM(:artist)) AND ABS(duration_sec - :durationSec) <= 2 LIMIT 1")
    suspend fun findTrackByTitleArtist(
        title: String,
        artist: String,
        durationSec: Int
    ): TrackEntity?

    @Query("SELECT * FROM tracks WHERE LOWER(TRIM(title)) = LOWER(TRIM(:title)) AND ABS(duration_sec - :durationSec) <= 2")
    suspend fun findTracksByTitleAndDuration(title: String, durationSec: Int): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE LOWER(title) LIKE '%' || LOWER(:query) || '%' OR LOWER(artist) LIKE '%' || LOWER(:query) || '%' ORDER BY last_played_at DESC")
    suspend fun searchTracks(query: String): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE LOWER(title) LIKE '%' || LOWER(:query) || '%' OR LOWER(artist) LIKE '%' || LOWER(:query) || '%' ORDER BY last_played_at DESC")
    fun searchTracksFlow(query: String): Flow<List<TrackEntity>>

    @Query("SELECT COUNT(*) FROM tracks")
    suspend fun getTrackCount(): Int

    @Query("""
        SELECT * FROM tracks 
        WHERE (
            (play_count < 5 AND last_played_at < :lastPlayedThreshold) OR 
            (play_count = 0 AND downloaded_at < :downloadedThreshold) OR 
            (duration_sec < 60) OR
            (thumbnail_uri IS NULL OR artist = 'Unknown')
        )
        AND is_favourite = 0 
        AND uuid NOT IN (SELECT track_uuid FROM playlist_tracks)
        ORDER BY downloaded_at ASC
    """)
    suspend fun getPurgeableTracks(lastPlayedThreshold: String, downloadedThreshold: Long): List<TrackEntity>
}
