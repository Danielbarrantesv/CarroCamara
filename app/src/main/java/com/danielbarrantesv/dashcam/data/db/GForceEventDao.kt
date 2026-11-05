package com.danielbarrantesv.dashcam.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.danielbarrantesv.dashcam.data.model.GForceEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GForceEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: GForceEventEntity): Long

    @Query("SELECT * FROM gforce_events WHERE tripId = :tripId ORDER BY createdAt ASC")
    fun observeForTrip(tripId: Long): Flow<List<GForceEventEntity>>

    @Query("SELECT * FROM gforce_events WHERE tripId = :tripId ORDER BY createdAt ASC")
    suspend fun getForTrip(tripId: Long): List<GForceEventEntity>

    @Query("SELECT COUNT(*) FROM gforce_events WHERE tripId = :tripId")
    suspend fun countForTrip(tripId: Long): Int

    @Query("SELECT COALESCE(MAX(magnitudeG), 0) FROM gforce_events WHERE tripId = :tripId")
    suspend fun maxGForTrip(tripId: Long): Float
}
