package com.danielbarrantesv.dashcam.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A recorded trip — from the moment recording starts until it stops.
 * One trip contains many [VideoSegmentEntity] and many [GForceEventEntity].
 */
@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,                 // epoch millis
    val endedAt: Long? = null,           // epoch millis
    val distanceMeters: Double = 0.0,    // accumulated
    val durationSeconds: Long = 0,       // accumulated
    val maxSpeedKmh: Float = 0f,
    val avgSpeedKmh: Float = 0f,
    val startLat: Double? = null,
    val startLon: Double? = null,
    val endLat: Double? = null,
    val endLon: Double? = null,
    val maxGForce: Float = 0f,           // peak acceleration in g
    val eventCount: Int = 0,
    val videoSegmentCount: Int = 0,
    val totalStorageBytes: Long = 0,
    val protectedCount: Int = 0,         // segments manually saved
    val reportPath: String? = null       // PDF path if generated
) {
    val isFinished: Boolean get() = endedAt != null
}
