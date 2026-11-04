package com.danielbarrantesv.dashcam.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleService
import com.danielbarrantesv.dashcam.data.model.GForceEventEntity
import com.danielbarrantesv.dashcam.data.model.GpsSampleEntity
import com.danielbarrantesv.dashcam.data.prefs.SettingsRepository
import com.danielbarrantesv.dashcam.data.repository.TripRepository
import com.danielbarrantesv.dashcam.util.FileUtils
import com.danielbarrantesv.dashcam.util.Formatters
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that orchestrates the whole dashcam loop while the app is in
 * background or the screen is off:
 *   - Loop recorder (video segments)
 *   - GPS tracker (1 Hz samples → DB)
 *   - G-force detector (accelerometer → events → DB + notification)
 *   - Wake lock (keep CPU running while screen off)
 *   - Persistent notification (required for foreground services)
 *
 * Speaks to UI via the shared [RecordingState] singleton.
 */
@AndroidEntryPoint
class RecordingService : LifecycleService() {

    @Inject lateinit var loopRecorder: LoopRecorder
    @Inject lateinit var gpsTracker: GpsTracker
    @Inject lateinit var gForceDetector: GForceDetector
    @Inject lateinit var tripRepo: TripRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var notifHelper: NotificationHelper

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var gpsJob: Job? = null
    private var gForceJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var currentTripId: Long = 0L
    private var tripStartMs: Long = 0L

    private val _uiState = MutableStateFlow(RecordingState())
    val uiState: StateFlow<RecordingState> = _uiState.asStateFlow()

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onCreate() {
        super.onCreate()
        loopRecorder.bind(this as LifecycleOwner)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> startRecording(intent.getIntExtra(EXTRA_LENS_FACING, 0))
            ACTION_STOP -> stopRecording()
        }
        return START_STICKY
    }

    private fun startRecording(lensFacing: Int) {
        // Foreground service start with the modern API (Android 14+ requires correct type)
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

        scope.launch {
            tripStartMs = System.currentTimeMillis()
            currentTripId = tripRepo.startTrip(tripStartMs, lat = null, lon = null)
            settings.setLastTripId(currentTripId)

            _uiState.value = _uiState.value.copy(
                isRecording = true,
                tripId = currentTripId,
                startedAt = tripStartMs,
                speedKmh = 0f,
                distanceMeters = 0.0,
                gForce = 0f,
                lat = null,
                lon = null
            )

            loopRecorder.startRecording(currentTripId, lensFacing)
            startGpsStream()
            startGForceStream()
        }
    }

    private fun startGpsStream() {
        gpsJob?.cancel()
        gpsJob = scope.launch {
            var lastLat: Double? = null
            var lastLon: Double? = null
            var distance: Double = 0.0
            var maxSpeed: Float = 0f
            var speedSum: Float = 0f
            var sampleCount: Int = 0

            gpsTracker.locationUpdates().collect { loc ->
                val speed = if (loc.hasSpeed()) loc.speed else 0f
                val speedKmh = speed * 3.6f

                // Distance accumulation via haversine
                if (lastLat != null && lastLon != null) {
                    distance += haversine(lastLat!!, lastLon!!, loc.latitude, loc.longitude)
                }
                lastLat = loc.latitude
                lastLon = loc.longitude

                if (speedKmh > maxSpeed) maxSpeed = speedKmh
                speedSum += speedKmh
                sampleCount++

                tripRepo.addGpsSample(
                    GpsSampleEntity(
                        tripId = currentTripId,
                        createdAt = System.currentTimeMillis(),
                        lat = loc.latitude,
                        lon = loc.longitude,
                        altitude = if (loc.hasAltitude()) loc.altitude else null,
                        accuracy = if (loc.hasAccuracy()) loc.accuracy else null,
                        speedMs = if (loc.hasSpeed()) loc.speed else null,
                        bearing = if (loc.hasBearing()) loc.bearing else null
                    )
                )

                _uiState.value = _uiState.value.copy(
                    speedKmh = speedKmh,
                    distanceMeters = distance,
                    lat = loc.latitude,
                    lon = loc.longitude,
                    maxSpeedKmh = maxSpeed,
                    avgSpeedKmh = if (sampleCount > 0) speedSum / sampleCount else 0f
                )

                // Update notification every few seconds
                notifHelper.updateRecordingNotification(
                    durationSec = (System.currentTimeMillis() - tripStartMs) / 1000,
                    speedKmh = speedKmh
                )
            }
        }
    }

    private fun startGForceStream() {
        gForceJob?.cancel()
        gForceJob = scope.launch {
            val threshold = settings.gforceThreshold.first()
            var peakG = 0f

            gForceDetector.samples().collect { sample ->
                if (sample.magnitudeG > peakG) {
                    peakG = sample.magnitudeG
                    _uiState.value = _uiState.value.copy(gForce = peakG)
                }

                val type = gForceDetector.classify(sample, threshold) ?: return@collect

                val lat = _uiState.value.lat
                val lon = _uiState.value.lon
                val speed = _uiState.value.speedKmh

                val eventId = tripRepo.addEvent(
                    GForceEventEntity(
                        tripId = currentTripId,
                        createdAt = sample.timestampMs,
                        type = type.name,
                        magnitudeG = sample.magnitudeG,
                        axisX = sample.x / GForceDetector.GRAVITY,
                        axisY = sample.y / GForceDetector.GRAVITY,
                        axisZ = sample.z / GForceDetector.GRAVITY,
                        lat = lat,
                        lon = lon,
                        speedKmh = speed
                    )
                )

                notifHelper.postEventNotification(eventId, sample.magnitudeG)

                _uiState.value = _uiState.value.copy(
                    lastEventType = type.label,
                    lastEventMagnitude = sample.magnitudeG
                )
            }
        }
    }

    private fun stopRecording() {
        scope.launch {
            gpsJob?.cancel()
            gForceJob?.cancel()
            loopRecorder.stopRecording()

            val endMs = System.currentTimeMillis()
            val durationSec = (endMs - tripStartMs) / 1000
            val st = _uiState.value

            tripRepo.endTrip(
                id = currentTripId,
                endedAt = endMs,
                distanceMeters = st.distanceMeters,
                durationSeconds = durationSec,
                maxSpeedKmh = st.maxSpeedKmh,
                avgSpeedKmh = st.avgSpeedKmh,
                endLat = st.lat,
                endLon = st.lon,
                maxGForce = st.gForce,
                eventCount = 0,
                videoSegmentCount = 0,
                totalStorageBytes = loopRecorder.state.value.storageUsedBytes,
                protectedCount = 0
            )

            releaseWakeLock()
            notifHelper.cancelRecording()
            stopForeground(STOP_FOREGROUND_REMOVE)

            _uiState.value = RecordingState()
            stopSelf()
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "DashCam::RecordingWakeLock"
        ).apply { acquire(10 * 60 * 60 * 1000L /* 10h max */) }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        releaseWakeLock()
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }

    companion object {
        const val ACTION_START = "com.danielbarrantesv.dashcam.START"
        const val ACTION_STOP = "com.danielbarrantesv.dashcam.STOP"
        const val EXTRA_LENS_FACING = "lens_facing"

        fun startWith(ctx: android.content.Context, lensFacing: Int) {
            val intent = Intent(ctx, RecordingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_LENS_FACING, lensFacing)
            }
            ctx.startService(intent)
        }

        fun stop(ctx: android.content.Context) {
            val intent = Intent(ctx, RecordingService::class.java).apply { action = ACTION_STOP }
            ctx.startService(intent)
        }
    }
}

/** Streamed state shared with the UI overlay. */
data class RecordingState(
    val isRecording: Boolean = false,
    val tripId: Long = 0L,
    val startedAt: Long = 0L,
    val speedKmh: Float = 0f,
    val maxSpeedKmh: Float = 0f,
    val avgSpeedKmh: Float = 0f,
    val distanceMeters: Double = 0.0,
    val gForce: Float = 0f,
    val lat: Double? = null,
    val lon: Double? = null,
    val lastEventType: String? = null,
    val lastEventMagnitude: Float = 0f
)
