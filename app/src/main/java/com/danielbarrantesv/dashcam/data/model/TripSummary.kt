package com.danielbarrantesv.dashcam.data.model

/**
 * UI-facing aggregate for a trip in the list view (joins trip + counts + path preview).
 */
data class TripSummary(
    val id: Long,
    val startedAt: Long,
    val endedAt: Long?,
    val distanceMeters: Double,
    val durationSeconds: Long,
    val maxSpeedKmh: Float,
    val avgSpeedKmh: Float,
    val maxGForce: Float,
    val eventCount: Int,
    val videoSegmentCount: Int,
    val hasReport: Boolean
)
