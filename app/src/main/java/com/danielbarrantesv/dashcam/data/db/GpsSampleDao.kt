package com.danielbarrantesv.dashcam.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.danielbarrantesv.dashcam.data.model.GpsSampleEntity

@Dao
interface GpsSampleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sample: GpsSampleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(samples: List<GpsSampleEntity>)

    @Query("SELECT * FROM gps_samples WHERE tripId = :tripId ORDER BY createdAt ASC")
    suspend fun getForTrip(tripId: Long): List<GpsSampleEntity>

    @Query("SELECT COUNT(*) FROM gps_samples WHERE tripId = :tripId")
    suspend fun countForTrip(tripId: Long): Int

    @Query("DELETE FROM gps_samples WHERE tripId = :tripId")
    suspend fun deleteForTrip(tripId: Long)
}
