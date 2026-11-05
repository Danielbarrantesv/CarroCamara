package com.danielbarrantesv.dashcam.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.danielbarrantesv.dashcam.DashCamApplication
import com.danielbarrantesv.dashcam.MainActivity
import com.danielbarrantesv.dashcam.R
import com.danielbarrantesv.dashcam.util.Formatters
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Builds all dashcam notifications. Singleton to keep notification IDs consistent. */
@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val NOTIF_RECORDING_ID = 1001
        const val NOTIF_EVENT_ID_BASE = 2000
        const val NOTIF_SERVER_ID = 3001
    }

    private val nm: NotificationManager =
        context.getSystemService(NotificationManager::class.java)

    fun buildRecordingNotification(durationSec: Long, speedKmh: Float): Notification {
        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            context,
            1,
            Intent(context, RecordingService::class.java).apply {
                action = RecordingService.ACTION_STOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(context, DashCamApplication.CHANNEL_RECORDING)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.notif_recording_title))
            .setContentText(
                "⏱ ${Formatters.duration(durationSec)}   ● ${"%.0f".format(speedKmh)} km/h"
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .addAction(0, context.getString(R.string.rec_stop), stopIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun updateRecordingNotification(durationSec: Long, speedKmh: Float) {
        nm.notify(
            NOTIF_RECORDING_ID,
            buildRecordingNotification(durationSec, speedKmh)
        )
    }

    fun cancelRecording() {
        nm.cancel(NOTIF_RECORDING_ID)
    }

    fun postEventNotification(eventId: Long, magnitudeG: Float) {
        val n = NotificationCompat.Builder(context, DashCamApplication.CHANNEL_EVENTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.notif_event_title))
            .setContentText(context.getString(R.string.notif_event_text, magnitudeG))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        nm.notify((NOTIF_EVENT_ID_BASE + eventId).toInt(), n)
    }

    fun buildServerNotification(host: String): Notification {
        val openIntent = PendingIntent.getActivity(
            context,
            2,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            context,
            3,
            Intent(context, HttpServerService::class.java).apply {
                action = HttpServerService.ACTION_STOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(context, DashCamApplication.CHANNEL_TRANSFER)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.notif_server_title))
            .setContentText(context.getString(R.string.notif_server_text, host))
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(0, context.getString(R.string.transfer_stop_server), stopIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun updateServerNotification(host: String) {
        nm.notify(NOTIF_SERVER_ID, buildServerNotification(host))
    }

    fun cancelServer() {
        nm.cancel(NOTIF_SERVER_ID)
    }
}
