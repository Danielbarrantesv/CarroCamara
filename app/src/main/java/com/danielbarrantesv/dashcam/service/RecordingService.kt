package com.danielbarrantesv.dashcam.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import com.danielbarrantesv.dashcam.DashCamApplication
import com.danielbarrantesv.dashcam.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Lightweight foreground service that keeps the app process alive while recording.
 * The camera and recording are owned by CameraViewModel (activity-scoped), but this
 * service prevents the system from killing the process when the app goes to background.
 *
 * Acquires a PARTIAL_WAKE_LOCK so the CPU keeps running when the screen turns off,
 * ensuring the video encoder keeps receiving frames.
 */
@AndroidEntryPoint
class RecordingService : Service() {

    @Inject
    lateinit var notifHelper: NotificationHelper

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startForeground()
            ACTION_STOP -> stopSelfSafely()
        }
        return START_STICKY
    }

    private fun startForeground() {
        val notif = notifHelper.buildRecordingNotification(0L, 0f)
        ServiceCompat.startForeground(
            this,
            NotificationHelper.NOTIF_RECORDING_ID,
            notif,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
        acquireWakeLock()
    }

    private fun stopSelfSafely() {
        releaseWakeLock()
        notifHelper.cancelRecording()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "DashCam::RecordingWakeLock"
        ).apply { acquire(10 * 60 * 60 * 1000L) } // 10h max
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
    }

    companion object {
        const val ACTION_START = "com.danielbarrantesv.dashcam.REC_START"
        const val ACTION_STOP = "com.danielbarrantesv.dashcam.REC_STOP"

        fun start(ctx: android.content.Context) {
            val intent = Intent(ctx, RecordingService::class.java).apply { action = ACTION_START }
            ctx.startForegroundService(intent)
        }

        fun stop(ctx: android.content.Context) {
            val intent = Intent(ctx, RecordingService::class.java).apply { action = ACTION_STOP }
            ctx.startService(intent)
        }
    }
}
