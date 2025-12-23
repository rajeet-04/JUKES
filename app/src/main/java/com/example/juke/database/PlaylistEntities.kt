package com.example.juke.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Room entity for storing playlist metadata.
 */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey
    val id: String,
    
    @ColumnInfo(name = "name")
    val name: String,
    
    @ColumnInfo(name = "description")
    val description: String? = null,
    
    @ColumnInfo(name = "thumbnail_uri")
    val thumbnailUri: String? = null,
    
    @ColumnInfo(name = "spotify_id")
    val spotifyId: String? = null,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "track_count")
    val trackCount: Int = 0
)

/**
 * Room entity for storing playlist-track relationships.
 */
@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlist_id", "track_uuid", "position"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlist_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["track_uuid"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("playlist_id"), Index("track_uuid")]
)
data class PlaylistTrackEntity(
    @ColumnInfo(name = "playlist_id")
    val playlistId: String,
    
    @ColumnInfo(name = "track_uuid")
    val trackUuid: String,
    
    @ColumnInfo(name = "position")
    val position: Int,
    
    @ColumnInfo(name = "added_at")
    val addedAt: Long = System.currentTimeMillis()
)

/**
 * Data class for playlist with track count.
 */
data class PlaylistWithTracks(
    @Embedded val playlist: PlaylistEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "uuid",
        associateBy = Junction(
            PlaylistTrackEntity::class,
            parentColumn = "playlist_id",
            entityColumn = "track_uuid"
        )
    )
    val tracks: List<TrackEntity>
)

/**
 * Data Access Object for Playlist operations.
 */
@Dao
interface PlaylistDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTrack(playlistTrack: PlaylistTrackEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTracks(playlistTracks: List<PlaylistTrackEntity>)
    
    @Query("SELECT * FROM playlists ORDER BY created_at DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>
    
    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    suspend fun getPlaylist(playlistId: String): PlaylistEntity?
    
    @Transaction
    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    suspend fun getPlaylistWithTracks(playlistId: String): PlaylistWithTracks?
    
    @Query("""
        SELECT t.* FROM tracks t
        INNER JOIN playlist_tracks pt ON t.uuid = pt.track_uuid
        WHERE pt.playlist_id = :playlistId
        ORDER BY pt.position ASC
    """)
    suspend fun getPlaylistTracks(playlistId: String): List<TrackEntity>
    
    @Query("""
        SELECT t.* FROM tracks t
        INNER JOIN playlist_tracks pt ON t.uuid = pt.track_uuid
        WHERE pt.playlist_id = :playlistId
        ORDER BY pt.position ASC
    """)
    fun getPlaylistTracksFlow(playlistId: String): Flow<List<TrackEntity>>
    
    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: String)
    
    @Query("DELETE FROM playlist_tracks WHERE playlist_id = :playlistId")
    suspend fun deletePlaylistTracks(playlistId: String)
    
    @Query("DELETE FROM playlist_tracks WHERE playlist_id = :playlistId AND track_uuid = :trackUuid")
    suspend fun removeTrackFromPlaylist(playlistId: String, trackUuid: String)
    
    @Query("UPDATE playlists SET track_count = :count WHERE id = :playlistId")
    suspend fun updatePlaylistTrackCount(playlistId: String, count: Int)
    
    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlist_id = :playlistId")
    suspend fun getPlaylistTrackCount(playlistId: String): Int
}
