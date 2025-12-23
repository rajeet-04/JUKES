package com.example.juke.database

import android.content.Context
import androidx.room.*
import com.example.juke.models.Track
import kotlinx.coroutines.flow.Flow

/**
 * Room Database for JUKE music player.
 */
@Database(
    entities = [TrackEntity::class, PlaylistEntity::class, PlaylistTrackEntity::class],
    version = 2,
    exportSchema = false
)
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
    
    @Query("DELETE FROM tracks WHERE uuid = :uuid")
    suspend fun deleteTrack(uuid: String)
    
    @Query("DELETE FROM tracks")
    suspend fun deleteAllTracks()
    
    @Query("SELECT * FROM tracks WHERE play_count > 0 ORDER BY play_count DESC LIMIT :limit")
    suspend fun getMostPlayed(limit: Int = 10): List<TrackEntity>
    
    @Query("SELECT * FROM tracks WHERE LOWER(title) LIKE '%' || LOWER(:title) || '%' AND LOWER(artist) LIKE '%' || LOWER(:artist) || '%' LIMIT 1")
    suspend fun findTrackByTitleArtist(title: String, artist: String): TrackEntity?
    
    @Query("SELECT * FROM tracks WHERE LOWER(title) LIKE '%' || LOWER(:query) || '%' OR LOWER(artist) LIKE '%' || LOWER(:query) || '%' ORDER BY last_played_at DESC")
    suspend fun searchTracks(query: String): List<TrackEntity>
    
    @Query("SELECT * FROM tracks WHERE LOWER(title) LIKE '%' || LOWER(:query) || '%' OR LOWER(artist) LIKE '%' || LOWER(:query) || '%' ORDER BY last_played_at DESC")
    fun searchTracksFlow(query: String): Flow<List<TrackEntity>>
    
    @Query("SELECT COUNT(*) FROM tracks")
    suspend fun getTrackCount(): Int
}
