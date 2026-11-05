package com.danielbarrantesv.dashcam.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.danielbarrantesv.dashcam.data.model.ReportEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReportDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(report: ReportEntity): Long

    @Query("SELECT * FROM reports ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ReportEntity>>

    @Query("SELECT * FROM reports WHERE id = :id")
    suspend fun getById(id: Long): ReportEntity?

    @Query("SELECT * FROM reports WHERE tripId = :tripId ORDER BY createdAt DESC LIMIT 1")
    suspend fun latestForTrip(tripId: Long): ReportEntity?

    @Query("DELETE FROM reports WHERE id = :id")
    suspend fun deleteById(id: Long)
}
