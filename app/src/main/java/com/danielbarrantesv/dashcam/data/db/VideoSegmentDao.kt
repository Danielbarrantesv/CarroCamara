package com.danielbarrantesv.dashcam.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.danielbarrantesv.dashcam.data.model.VideoSegmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoSegmentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(segment: VideoSegmentEntity): Long

    @Update
    suspend fun update(segment: VideoSegmentEntity)

    @Query("SELECT * FROM video_segments WHERE tripId = :tripId ORDER BY createdAt ASC")
    fun observeForTrip(tripId: Long): Flow<List<VideoSegmentEntity>>

    @Query("SELECT * FROM video_segments WHERE tripId = :tripId ORDER BY createdAt ASC")
    suspend fun getForTrip(tripId: Long): List<VideoSegmentEntity>

    @Query("SELECT * FROM video_segments ORDER BY createdAt ASC")
    suspend fun getAll(): List<VideoSegmentEntity>

    /**
     * Oldest unprotected segment — the first candidate to be deleted when storage runs out.
     */
    @Query("SELECT * FROM video_segments WHERE isProtected = 0 ORDER BY createdAt ASC LIMIT 1")
    suspend fun oldestUnprotected(): VideoSegmentEntity?

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM video_segments")
    suspend fun totalSizeBytes(): Long

    @Query("SELECT COUNT(*) FROM video_segments WHERE isProtected = 1")
    suspend fun protectedCount(): Int

    @Query("UPDATE video_segments SET isProtected = :protected WHERE id = :id")
    suspend fun setProtected(id: Long, protected: Boolean)

    @Query("DELETE FROM video_segments WHERE id = :id")
    suspend fun deleteById(id: Long)
}
