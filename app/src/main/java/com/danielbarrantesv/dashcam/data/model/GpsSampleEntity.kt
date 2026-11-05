package com.danielbarrantesv.dashcam.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single GPS sample (one row per location update, ~1 Hz).
 * Used for the route polyline and for the insurer report's track data.
 */
@Entity(
    tableName = "gps_samples",
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
data class GpsSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val createdAt: Long,
    val lat: Double,
    val lon: Double,
    val altitude: Double?,
    val accuracy: Float?,                // meters
    val speedMs: Float?,                 // m/s
    val bearing: Float?                  // degrees
)
