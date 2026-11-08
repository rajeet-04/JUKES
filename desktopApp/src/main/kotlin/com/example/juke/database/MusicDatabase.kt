package com.example.juke.database

import android.content.Context
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import java.io.File
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import androidx.room.RoomRawQuery
import com.example.juke.models.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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
    entities = [TrackEntity::class, PlaylistEntity::class, PlaylistTrackEntity::class, PendingImportEntity::class, SongExposureEntity::class],
    version = 12,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun songExposureDao(): SongExposureDao

    companion object {
        @Volatile
        private var INSTANCE: MusicDatabase? = null

        fun getDatabase(context: Context): MusicDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder<MusicDatabase>(File(Context.dataDir, "music_database.db").path)
                    .setDriver(BundledSQLiteDriver())
                    .setQueryCoroutineContext(Dispatchers.IO)
                    .fallbackToDestructiveMigration(dropAllTables = false)
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

    @ColumnInfo(name = "romanized_synced_lyrics")
    val romanizedSyncedLyrics: String? = null,

    @ColumnInfo(name = "romanized_plain_lyrics")
    val romanizedPlainLyrics: String? = null,

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
    val artistSpotifyIds: List<String>? = null,

    @ColumnInfo(name = "is_stream", defaultValue = "0")
    val isStream: Boolean = false,

    @ColumnInfo(name = "lyrics_offset_ms", defaultValue = "0")
    val lyricsOffsetMs: Long = 0L
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
        romanizedSyncedLyrics = romanizedSyncedLyrics,
        romanizedPlainLyrics = romanizedPlainLyrics,
        isFavourite = isFavourite,
        playCount = playCount,
        lastPlayedAt = lastPlayedAt,
        downloadedAt = downloadedAt,
        spotifyId = spotifyId,
        albumSpotifyId = albumSpotifyId,
        artistSpotifyIds = artistSpotifyIds,
        isStream = isStream,
        lyricsOffsetMs = lyricsOffsetMs
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
        romanizedSyncedLyrics = romanizedSyncedLyrics,
        romanizedPlainLyrics = romanizedPlainLyrics,
        isFavourite = isFavourite,
        playCount = playCount,
        lastPlayedAt = lastPlayedAt,
        downloadedAt = downloadedAt,
        spotifyId = spotifyId,
        albumSpotifyId = albumSpotifyId,
        artistSpotifyIds = artistSpotifyIds,
        isStream = isStream,
        lyricsOffsetMs = lyricsOffsetMs
    )
}

/**
 * Data Access Object for Track operations.
 */
@Dao
interface TrackDao {

    // Upsert, never REPLACE: SQLite carries out REPLACE as delete + insert, and the delete
    // cascades to playlist_tracks, so re-saving a track (lyrics, romanization, stream promoted to a
    // download) silently removed it from every playlist.
    @Upsert
    suspend fun insertTrack(track: TrackEntity)

    @Upsert
    suspend fun insertTracks(tracks: List<TrackEntity>)

    @Query("SELECT * FROM tracks WHERE is_stream = 0 ORDER BY last_played_at DESC")
    suspend fun getAllTracks(): List<TrackEntity>

    // Lightweight flow query - excludes large lyrics columns to prevent CursorWindow overflow
    @Query("SELECT uuid, title, artist, thumbnail_uri, duration_sec, local_uri, yt_video_id, NULL as synced_lyrics, NULL as plain_lyrics, NULL as romanized_synced_lyrics, NULL as romanized_plain_lyrics, is_favourite, play_count, last_played_at, downloaded_at, spotify_id, album_spotify_id, artist_spotify_ids, is_stream, lyrics_offset_ms FROM tracks WHERE is_stream = 0 ORDER BY last_played_at DESC")
    fun getAllTracksFlow(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE uuid = :uuid")
    suspend fun getTrackByUuid(uuid: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE last_played_at IS NOT NULL ORDER BY last_played_at DESC LIMIT :limit")
    suspend fun getRecentlyPlayed(limit: Int = 10): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE is_favourite = 1 ORDER BY last_played_at DESC")
    suspend fun getFavourites(): List<TrackEntity>

    // Android Auto lists: no lyrics columns, so long lists can't overflow the CursorWindow
    @Query("SELECT uuid, title, artist, thumbnail_uri, duration_sec, local_uri, yt_video_id, NULL as synced_lyrics, NULL as plain_lyrics, NULL as romanized_synced_lyrics, NULL as romanized_plain_lyrics, is_favourite, play_count, last_played_at, downloaded_at, spotify_id, album_spotify_id, artist_spotify_ids, is_stream, lyrics_offset_ms FROM tracks WHERE last_played_at IS NOT NULL ORDER BY last_played_at DESC LIMIT :limit")
    suspend fun getRecentlyPlayedLight(limit: Int): List<TrackEntity>

    @Query("SELECT uuid, title, artist, thumbnail_uri, duration_sec, local_uri, yt_video_id, NULL as synced_lyrics, NULL as plain_lyrics, NULL as romanized_synced_lyrics, NULL as romanized_plain_lyrics, is_favourite, play_count, last_played_at, downloaded_at, spotify_id, album_spotify_id, artist_spotify_ids, is_stream, lyrics_offset_ms FROM tracks WHERE is_favourite = 1 ORDER BY last_played_at DESC LIMIT :limit")
    suspend fun getFavouritesLight(limit: Int): List<TrackEntity>

    @Query("SELECT uuid, title, artist, thumbnail_uri, duration_sec, local_uri, yt_video_id, NULL as synced_lyrics, NULL as plain_lyrics, NULL as romanized_synced_lyrics, NULL as romanized_plain_lyrics, is_favourite, play_count, last_played_at, downloaded_at, spotify_id, album_spotify_id, artist_spotify_ids, is_stream, lyrics_offset_ms FROM tracks WHERE local_uri IS NOT NULL AND is_stream = 0 ORDER BY last_played_at DESC LIMIT :limit")
    suspend fun getDownloadedTracksLight(limit: Int): List<TrackEntity>

    @Query("SELECT uuid, title, artist, thumbnail_uri, duration_sec, local_uri, yt_video_id, NULL as synced_lyrics, NULL as plain_lyrics, NULL as romanized_synced_lyrics, NULL as romanized_plain_lyrics, is_favourite, play_count, last_played_at, downloaded_at, spotify_id, album_spotify_id, artist_spotify_ids, is_stream, lyrics_offset_ms FROM tracks WHERE LOWER(title) LIKE '%' || LOWER(:query) || '%' OR LOWER(artist) LIKE '%' || LOWER(:query) || '%' ORDER BY last_played_at DESC LIMIT :limit")
    suspend fun searchTracksLight(query: String, limit: Int): List<TrackEntity>

    // Title/artist only: enough to build song keys without loading lyrics columns
    @Query("SELECT title, artist FROM tracks WHERE is_favourite = 1")
    suspend fun getFavouriteTitleArtists(): List<TitleArtist>

    // Lightweight flow query - excludes large lyrics columns to prevent CursorWindow overflow
    @Query("SELECT uuid, title, artist, thumbnail_uri, duration_sec, local_uri, yt_video_id, NULL as synced_lyrics, NULL as plain_lyrics, NULL as romanized_synced_lyrics, NULL as romanized_plain_lyrics, is_favourite, play_count, last_played_at, downloaded_at, spotify_id, album_spotify_id, artist_spotify_ids, is_stream, lyrics_offset_ms FROM tracks WHERE is_favourite = 1 ORDER BY last_played_at DESC")
    fun getFavouritesFlow(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE local_uri IS NOT NULL AND is_stream = 0 ORDER BY last_played_at DESC")
    suspend fun getDownloadedTracks(): List<TrackEntity>

    // Lightweight flow query - excludes large lyrics columns to prevent CursorWindow overflow
    @Query("SELECT uuid, title, artist, thumbnail_uri, duration_sec, local_uri, yt_video_id, NULL as synced_lyrics, NULL as plain_lyrics, NULL as romanized_synced_lyrics, NULL as romanized_plain_lyrics, is_favourite, play_count, last_played_at, downloaded_at, spotify_id, album_spotify_id, artist_spotify_ids, is_stream, lyrics_offset_ms FROM tracks WHERE local_uri IS NOT NULL AND is_stream = 0 ORDER BY last_played_at DESC")
    fun getDownloadedTracksFlow(): Flow<List<TrackEntity>>

    @Query("UPDATE tracks SET lyrics_offset_ms = :offsetMs WHERE uuid = :uuid")
    suspend fun updateLyricsOffset(uuid: String, offsetMs: Long)

    @Query("UPDATE tracks SET local_uri = :streamUrl WHERE uuid = :uuid")
    suspend fun updateTrackStreamUrl(uuid: String, streamUrl: String)

    @Query("UPDATE tracks SET local_uri = NULL WHERE is_stream = 1 AND uuid IN (:uuids)")
    suspend fun clearStreamLocalUris(uuids: List<String>)

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

    @Query("SELECT * FROM tracks WHERE is_stream = 1")
    suspend fun getStreamTracks(): List<TrackEntity>

    @Query("DELETE FROM tracks WHERE is_stream = 1")
    suspend fun deleteStreamTracks(): Int

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

    @RawQuery
    suspend fun searchTracksRaw(query: RoomRawQuery): List<TrackEntity>

    @Query("SELECT COUNT(*) FROM tracks")
    suspend fun getTrackCount(): Int

    @Query(
        """
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
    """
    )
    suspend fun getPurgeableTracks(
        lastPlayedThreshold: String,
        downloadedThreshold: Long
    ): List<TrackEntity>
}
