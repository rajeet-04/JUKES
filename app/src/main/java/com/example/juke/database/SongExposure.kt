package com.example.juke.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

/**
 * How much the listener has heard / been shown a song lately, keyed by song (not file uuid) so it
 * survives stream purges and re-downloads. Each score is a decaying sum stored as of [updatedAt];
 * see [com.example.juke.services.Variety] for the weights and half-lives.
 */
@Entity(tableName = "song_exposure")
data class SongExposureEntity(
    @PrimaryKey @ColumnInfo(name = "song_key") val songKey: String,
    @ColumnInfo(name = "play_score") val playScore: Double = 0.0,
    @ColumnInfo(name = "skip_score") val skipScore: Double = 0.0,
    @ColumnInfo(name = "rec_score") val recScore: Double = 0.0,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Dao
interface SongExposureDao {
    @Query("SELECT * FROM song_exposure WHERE song_key = :key")
    suspend fun get(key: String): SongExposureEntity?

    @Query("SELECT * FROM song_exposure WHERE song_key IN (:keys)")
    suspend fun getAll(keys: List<String>): List<SongExposureEntity>

    @Upsert
    suspend fun upsert(entity: SongExposureEntity)

    /** Rows untouched this long have decayed to nothing. */
    @Query("DELETE FROM song_exposure WHERE updated_at < :before")
    suspend fun pruneOlderThan(before: Long): Int
}

/** Projection of a track's title and artist. */
data class TitleArtist(val title: String, val artist: String)
