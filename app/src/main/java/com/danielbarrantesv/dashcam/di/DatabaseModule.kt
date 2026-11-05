package com.danielbarrantesv.dashcam.di

import android.content.Context
import androidx.room.Room
import com.danielbarrantesv.dashcam.data.db.DashCamDatabase
import com.danielbarrantesv.dashcam.data.db.GForceEventDao
import com.danielbarrantesv.dashcam.data.db.GpsSampleDao
import com.danielbarrantesv.dashcam.data.db.ReportDao
import com.danielbarrantesv.dashcam.data.db.TripDao
import com.danielbarrantesv.dashcam.data.db.VideoSegmentDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): DashCamDatabase =
        Room.databaseBuilder(ctx, DashCamDatabase::class.java, "dashcam.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun tripDao(db: DashCamDatabase): TripDao = db.tripDao()
    @Provides fun videoSegmentDao(db: DashCamDatabase): VideoSegmentDao = db.videoSegmentDao()
    @Provides fun gForceEventDao(db: DashCamDatabase): GForceEventDao = db.gForceEventDao()
    @Provides fun gpsSampleDao(db: DashCamDatabase): GpsSampleDao = db.gpsSampleDao()
    @Provides fun reportDao(db: DashCamDatabase): ReportDao = db.reportDao()
}
