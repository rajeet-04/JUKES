package com.example.juke.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * One finished listen. Rows are a short-lived buffer: a daily report summarises each closed local day
 * and the rows for that day are deleted once the report is confirmed (see the daily report uploader).
 */
@Entity(tableName = "play_events", indices = [Index("local_date")])
data class PlayEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "track_uuid") val trackUuid: String,
    /** Title and artist key, same as song_exposure, for tracks whose uuid changes. */
    @ColumnInfo(name = "song_key") val songKey: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    /** Local day the listen started on, yyyy-MM-dd in the device time zone at the time. */
    @ColumnInfo(name = "local_date") val localDate: String,
    @ColumnInfo(name = "tz") val timeZone: String,
    /** Time the player was actually playing, pauses excluded. */
    @ColumnInfo(name = "listened_ms") val listenedMs: Long,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    val completed: Boolean,
    val skipped: Boolean,
    /** playlist, search, radio, jam or library; null when unknown. */
    val source: String? = null,
    // Snapshot of the song, because stream tracks are purged from `tracks` before the day is reported.
    val title: String? = null,
    val artist: String? = null,
    @ColumnInfo(name = "spotify_id") val spotifyId: String? = null,
    @ColumnInfo(name = "album_spotify_id") val albumSpotifyId: String? = null,
    @ColumnInfo(name = "artist_spotify_ids") val artistSpotifyIds: List<String>? = null,
    @ColumnInfo(name = "yt_video_id") val ytVideoId: String? = null,
    /** Account email when the listener was logged in, null when anonymous. */
    val owner: String? = null
)

@Dao
interface PlayEventDao {
    @Insert
    suspend fun insert(event: PlayEventEntity)

    /** Distinct local dates that have rows, oldest first. */
    @Query("SELECT DISTINCT local_date FROM play_events ORDER BY local_date")
    suspend fun dates(): List<String>

    @Query("SELECT * FROM play_events WHERE local_date = :date ORDER BY started_at")
    suspend fun forDate(date: String): List<PlayEventEntity>

    @Query("DELETE FROM play_events WHERE local_date = :date")
    suspend fun deleteDate(date: String): Int

    @Query("DELETE FROM play_events")
    suspend fun deleteAll(): Int
}
