package com.danielbarrantesv.dashcam.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A generated insurer report (PDF). Stored so the user can re-open/share it later.
 */
@Entity(tableName = "reports")
data class ReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val createdAt: Long,
    val filePath: String,
    val fileSizeBytes: Long,
    val policyHolderName: String? = null,
    val policyNumber: String? = null,
    val vehiclePlate: String? = null
)
