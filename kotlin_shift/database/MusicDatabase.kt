package com.juke.database

import android.content.Context
import androidx.room.*
import com.juke.models.Track
import kotlinx.coroutines.flow.Flow

/**
 * Room Database for JUKE music player.
 * 
 * Stores all track metadata, user preferences, and playback history.
 */
@Database(
    entities = [TrackEntity::class],
    version = 1,
    exportSchema = false
)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    
    companion object {
        @Volatile
        private var INSTANCE: MusicDatabase? = null
        
        fun getDatabase(context: Context): MusicDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MusicDatabase::class.java,
                    "music_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

/**
 * Room entity for storing track data.
 * 
 * Maps to the "tracks" table in SQLite.
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
    val lastPlayedAt: String? = null
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
        lastPlayedAt = lastPlayedAt
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
        lastPlayedAt = lastPlayedAt
    )
}

/**
 * Data Access Object for Track operations.
 * 
 * Provides methods for querying, inserting, updating, and deleting tracks.
 */
@Dao
interface TrackDao {
    
    /**
     * Insert or replace a track.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: TrackEntity)
    
    /**
     * Insert multiple tracks.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<TrackEntity>)
    
    /**
     * Get all tracks ordered by last played date.
     */
    @Query("SELECT * FROM tracks ORDER BY last_played_at DESC")
    suspend fun getAllTracks(): List<TrackEntity>
    
    /**
     * Get all tracks as Flow for reactive updates.
     */
    @Query("SELECT * FROM tracks ORDER BY last_played_at DESC")
    fun getAllTracksFlow(): Flow<List<TrackEntity>>
    
    /**
     * Get track by UUID.
     */
    @Query("SELECT * FROM tracks WHERE uuid = :uuid")
    suspend fun getTrackByUuid(uuid: String): TrackEntity?
    
    /**
     * Get recently played tracks (last N tracks).
     */
    @Query("SELECT * FROM tracks WHERE last_played_at IS NOT NULL ORDER BY last_played_at DESC LIMIT :limit")
    suspend fun getRecentlyPlayed(limit: Int = 10): List<TrackEntity>
    
    /**
     * Get favorite tracks.
     */
    @Query("SELECT * FROM tracks WHERE is_favourite = 1 ORDER BY last_played_at DESC")
    suspend fun getFavourites(): List<TrackEntity>
    
    /**
     * Get favorite tracks as Flow.
     */
    @Query("SELECT * FROM tracks WHERE is_favourite = 1 ORDER BY last_played_at DESC")
    fun getFavouritesFlow(): Flow<List<TrackEntity>>
    
    /**
     * Get downloaded tracks (tracks with local file).
     */
    @Query("SELECT * FROM tracks WHERE local_uri IS NOT NULL ORDER BY last_played_at DESC")
    suspend fun getDownloadedTracks(): List<TrackEntity>
    
    /**
     * Get downloaded tracks as Flow.
     */
    @Query("SELECT * FROM tracks WHERE local_uri IS NOT NULL ORDER BY last_played_at DESC")
    fun getDownloadedTracksFlow(): Flow<List<TrackEntity>>
    
    /**
     * Update track favorite status.
     */
    @Query("UPDATE tracks SET is_favourite = :isFavourite WHERE uuid = :uuid")
    suspend fun updateTrackFavourite(uuid: String, isFavourite: Boolean)
    
    /**
     * Increment play count and update last played timestamp.
     */
    @Query("UPDATE tracks SET play_count = play_count + 1, last_played_at = :lastPlayedAt WHERE uuid = :uuid")
    suspend fun incrementPlayCount(uuid: String, lastPlayedAt: String)
    
    /**
     * Delete track by UUID.
     */
    @Query("DELETE FROM tracks WHERE uuid = :uuid")
    suspend fun deleteTrack(uuid: String)
    
    /**
     * Delete all tracks.
     */
    @Query("DELETE FROM tracks")
    suspend fun deleteAllTracks()
    
    /**
     * Get most played tracks.
     */
    @Query("SELECT * FROM tracks WHERE play_count > 0 ORDER BY play_count DESC LIMIT :limit")
    suspend fun getMostPlayed(limit: Int = 10): List<TrackEntity>
    
    /**
     * Find track by title and artist (fuzzy search).
     */
    @Query("SELECT * FROM tracks WHERE LOWER(title) LIKE '%' || LOWER(:title) || '%' AND LOWER(artist) LIKE '%' || LOWER(:artist) || '%' LIMIT 1")
    suspend fun findTrackByTitleArtist(title: String, artist: String): TrackEntity?
    
    /**
     * Search tracks by query (searches title and artist).
     */
    @Query("SELECT * FROM tracks WHERE LOWER(title) LIKE '%' || LOWER(:query) || '%' OR LOWER(artist) LIKE '%' || LOWER(:query) || '%' ORDER BY last_played_at DESC")
    suspend fun searchTracks(query: String): List<TrackEntity>
    
    /**
     * Search tracks as Flow.
     */
    @Query("SELECT * FROM tracks WHERE LOWER(title) LIKE '%' || LOWER(:query) || '%' OR LOWER(artist) LIKE '%' || LOWER(:query) || '%' ORDER BY last_played_at DESC")
    fun searchTracksFlow(query: String): Flow<List<TrackEntity>>
    
    /**
     * Get track count.
     */
    @Query("SELECT COUNT(*) FROM tracks")
    suspend fun getTrackCount(): Int
}
