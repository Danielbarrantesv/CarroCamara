package com.danielbarrantesv.dashcam.data.repository

import com.danielbarrantesv.dashcam.data.db.GForceEventDao
import com.danielbarrantesv.dashcam.data.db.GpsSampleDao
import com.danielbarrantesv.dashcam.data.db.TripDao
import com.danielbarrantesv.dashcam.data.db.VideoSegmentDao
import com.danielbarrantesv.dashcam.data.model.GForceEventEntity
import com.danielbarrantesv.dashcam.data.model.GpsSampleEntity
import com.danielbarrantesv.dashcam.data.model.TripEntity
import com.danielbarrantesv.dashcam.data.model.VideoSegmentEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TripRepository @Inject constructor(
    private val tripDao: TripDao,
    private val videoSegmentDao: VideoSegmentDao,
    private val gForceEventDao: GForceEventDao,
    private val gpsSampleDao: GpsSampleDao
) {
    fun observeAllTrips(): Flow<List<TripEntity>> = tripDao.observeAll()

    suspend fun getTrip(id: Long): TripEntity? = tripDao.getById(id)

    suspend fun startTrip(startedAt: Long, lat: Double?, lon: Double?): Long =
        tripDao.insert(
            TripEntity(
                startedAt = startedAt,
                startLat = lat,
                startLon = lon
            )
        )

    suspend fun endTrip(
        id: Long,
        endedAt: Long,
        distanceMeters: Double,
        durationSeconds: Long,
        maxSpeedKmh: Float,
        avgSpeedKmh: Float,
        endLat: Double?,
        endLon: Double?,
        maxGForce: Float,
        eventCount: Int,
        videoSegmentCount: Int,
        totalStorageBytes: Long,
        protectedCount: Int
    ) {
        val trip = tripDao.getById(id) ?: return
        tripDao.update(
            trip.copy(
                endedAt = endedAt,
                distanceMeters = distanceMeters,
                durationSeconds = durationSeconds,
                maxSpeedKmh = maxSpeedKmh,
                avgSpeedKmh = avgSpeedKmh,
                endLat = endLat,
                endLon = endLon,
                maxGForce = maxGForce,
                eventCount = eventCount,
                videoSegmentCount = videoSegmentCount,
                totalStorageBytes = totalStorageBytes,
                protectedCount = protectedCount
            )
        )
    }

    suspend fun setReportPath(tripId: Long, path: String) =
        tripDao.setReportPath(tripId, path)

    suspend fun deleteTrip(id: Long) = tripDao.deleteById(id)

    // ---- Video segments ----
    fun observeSegments(tripId: Long): Flow<List<VideoSegmentEntity>> =
        videoSegmentDao.observeForTrip(tripId)

    suspend fun addSegment(segment: VideoSegmentEntity): Long =
        videoSegmentDao.insert(segment)

    suspend fun oldestUnprotectedSegment(): VideoSegmentEntity? =
        videoSegmentDao.oldestUnprotected()

    suspend fun totalVideoSizeBytes(): Long = videoSegmentDao.totalSizeBytes()

    suspend fun protectedCount(): Int = videoSegmentDao.protectedCount()

    suspend fun setSegmentProtected(id: Long, isProtected: Boolean) =
        videoSegmentDao.setProtected(id, isProtected)

    suspend fun deleteSegment(id: Long) = videoSegmentDao.deleteById(id)

    suspend fun getSegmentsForTrip(tripId: Long): List<VideoSegmentEntity> =
        videoSegmentDao.getForTrip(tripId)

    // ---- G-force events ----
    fun observeEvents(tripId: Long): Flow<List<GForceEventEntity>> =
        gForceEventDao.observeForTrip(tripId)

    suspend fun addEvent(event: GForceEventEntity): Long =
        gForceEventDao.insert(event)

    suspend fun getEventsForTrip(tripId: Long): List<GForceEventEntity> =
        gForceEventDao.getForTrip(tripId)

    suspend fun maxGForTrip(tripId: Long): Float = gForceEventDao.maxGForTrip(tripId)

    // ---- GPS samples ----
    suspend fun addGpsSample(sample: GpsSampleEntity): Long =
        gpsSampleDao.insert(sample)

    suspend fun getGpsForTrip(tripId: Long): List<GpsSampleEntity> =
        gpsSampleDao.getForTrip(tripId)

    suspend fun deleteGpsForTrip(tripId: Long) = gpsSampleDao.deleteForTrip(tripId)
}
