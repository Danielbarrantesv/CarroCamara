package com.danielbarrantesv.dashcam.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.danielbarrantesv.dashcam.data.model.GForceEventEntity
import com.danielbarrantesv.dashcam.data.model.GpsSampleEntity
import com.danielbarrantesv.dashcam.data.model.ReportEntity
import com.danielbarrantesv.dashcam.data.model.TripEntity
import com.danielbarrantesv.dashcam.data.model.VideoSegmentEntity

@Database(
    entities = [
        TripEntity::class,
        VideoSegmentEntity::class,
        GForceEventEntity::class,
        GpsSampleEntity::class,
        ReportEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class DashCamDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun videoSegmentDao(): VideoSegmentDao
    abstract fun gForceEventDao(): GForceEventDao
    abstract fun gpsSampleDao(): GpsSampleDao
    abstract fun reportDao(): ReportDao
}
