package com.danielbarrantesv.dashcam.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A G-force event detected during a trip (sharp brake, hard acceleration, impact, sharp turn).
 * Stored for the insurer report. Coordinates link it to the trip's GPS track.
 */
@Entity(
    tableName = "gforce_events",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tripId"), Index("createdAt")]
)
data class GForceEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val createdAt: Long,
    val type: String,                    // BRAKE | ACCEL | TURN_LEFT | TURN_RIGHT | IMPACT
    val magnitudeG: Float,               // total magnitude
    val axisX: Float,                    // lateral (left/right)
    val axisY: Float,                    // longitudinal (forward/back)
    val axisZ: Float,                    // vertical
    val lat: Double? = null,
    val lon: Double? = null,
    val speedKmh: Float? = null,
    val videoSegmentId: Long? = null     // segment covering this moment
)
