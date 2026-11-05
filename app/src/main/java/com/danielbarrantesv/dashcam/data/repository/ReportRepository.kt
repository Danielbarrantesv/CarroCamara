package com.danielbarrantesv.dashcam.data.repository

import com.danielbarrantesv.dashcam.data.db.ReportDao
import com.danielbarrantesv.dashcam.data.model.ReportEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReportRepository @Inject constructor(
    private val reportDao: ReportDao
) {
    fun observeAll(): Flow<List<ReportEntity>> = reportDao.observeAll()

    suspend fun add(report: ReportEntity): Long = reportDao.insert(report)

    suspend fun getById(id: Long): ReportEntity? = reportDao.getById(id)

    suspend fun latestForTrip(tripId: Long): ReportEntity? = reportDao.latestForTrip(tripId)

    suspend fun delete(id: Long) = reportDao.deleteById(id)
}
