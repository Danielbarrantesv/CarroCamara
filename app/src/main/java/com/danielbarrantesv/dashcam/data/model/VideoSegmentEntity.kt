package com.danielbarrantesv.dashcam.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single video file written by the loop recorder.
 * Segments are overwritten (oldest first) when storage limit is reached,
 * UNLESS [isProtected] = true (set by the user pressing "save event").
 */
@Entity(
    tableName = "video_segments",
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
data class VideoSegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val filePath: String,
    val createdAt: Long,                 // epoch millis
    val durationMs: Long,
    val sizeBytes: Long,
    val isProtected: Boolean = false,    // protected segments are NOT auto-deleted
    val thumbnailPath: String? = null
)
