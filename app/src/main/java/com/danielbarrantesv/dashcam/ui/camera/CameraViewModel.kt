package com.danielbarrantesv.dashcam.ui.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danielbarrantesv.dashcam.R
import com.danielbarrantesv.dashcam.data.model.GForceEventEntity
import com.danielbarrantesv.dashcam.data.model.GpsSampleEntity
import com.danielbarrantesv.dashcam.data.model.VideoSegmentEntity
import com.danielbarrantesv.dashcam.data.prefs.SettingsRepository
import com.danielbarrantesv.dashcam.data.prefs.SpeedUnit
import com.danielbarrantesv.dashcam.data.prefs.VideoQuality
import com.danielbarrantesv.dashcam.data.repository.TripRepository
import com.danielbarrantesv.dashcam.service.GForceDetector
import com.danielbarrantesv.dashcam.service.GpsTracker
import com.danielbarrantesv.dashcam.service.RecordingService
import com.danielbarrantesv.dashcam.util.FileUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

data class CameraUiState(
    val isRecording: Boolean = false,
    val isCameraBound: Boolean = false,
    val durationSec: Long = 0,
    val speedKmh: Float = 0f,
    val distanceMeters: Double = 0.0,
    val gForce: Float = 0f,
    val lat: Double? = null,
    val lon: Double? = null,
    val maxSpeedKmh: Float = 0f,
    val avgSpeedKmh: Float = 0f,
    val speedUnit: String = "km/h",
    val lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    val rotationLocked: Boolean = false,
    val currentSegmentIndex: Int = 0,
    val storageUsedBytes: Long = 0,
    val startedAt: Long = 0,
    val error: String? = null,
    val lastEventLabel: String? = null,
    val dualCameraEnabled: Boolean = false,
    val storageUri: String? = null
)

@HiltViewModel
class CameraViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val settings: SettingsRepository,
    private val tripRepo: TripRepository,
    private val gpsTracker: GpsTracker,
    private val gForceDetector: GForceDetector
) : ViewModel() {

    private val _state = MutableStateFlow(CameraUiState())
    val state: StateFlow<CameraUiState> = _state.asStateFlow()

    /**
     * Custom LifecycleOwner that stays in RESUMED state. Used to bind CameraX use cases
     * so that the camera is NOT released when the Activity goes to STOPPED (background).
     * The foreground service (RecordingService) + wake lock keep the process and CPU alive,
     * and this lifecycle owner keeps the camera bound.
     */
    private val cameraLifecycleOwner = AlwaysResumedLifecycleOwner()

    private var cameraProvider: ProcessCameraProvider? = null
    // Primary capture (the one whose preview we show, and the one used for single-lens recording)
    private var primaryCapture: VideoCapture<Recorder>? = null
    // Secondary capture (only used when dual-camera recording is enabled)
    private var secondaryCapture: VideoCapture<Recorder>? = null
    // Preview use case — kept around so we can re-attach the surface when the user
    // returns to the camera screen without re-binding everything.
    private var previewUseCase: Preview? = null
    private var primaryRecording: Recording? = null
    private var secondaryRecording: Recording? = null

    private var currentTripId: Long = 0L
    private var tripStartMs: Long = 0L
    private var segmentIndex: Int = 0
    private var currentSegmentStartMs: Long = 0L

    private var timerJob: Job? = null
    private var segmentTimerJob: Job? = null
    private var gpsJob: Job? = null
    private var gForceJob: Job? = null

    init {
        viewModelScope.launch {
            settings.defaultLens.collect { lens ->
                _state.value = _state.value.copy(lensFacing = lens)
            }
        }
        viewModelScope.launch {
            settings.speedUnit.collect { u: SpeedUnit ->
                _state.value = _state.value.copy(speedUnit = u.label)
            }
        }
        viewModelScope.launch {
            settings.dualCameraEnabled.collect { enabled ->
                _state.value = _state.value.copy(dualCameraEnabled = enabled)
            }
        }
        viewModelScope.launch {
            settings.storageUri.collect { uri ->
                _state.value = _state.value.copy(storageUri = uri)
            }
        }
    }

    /**
     * Binds Preview + primary VideoCapture use case to the given lifecycleOwner and surfaces
     * the preview onto [previewView]. If dual camera is enabled and the device supports it,
     * a secondary VideoCapture is also bound for the other lens.
     */
    fun bindCamera(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            _state.value = _state.value.copy(error = ctx.getString(R.string.camera_permission_denied))
            return
        }

        // If recording is in progress, just re-attach the preview surface
        // without disturbing the active recording. This is what lets the user
        // switch tabs and come back without losing the recording.
        if (_state.value.isRecording) {
            try {
                previewUseCase?.setSurfaceProvider(previewView.surfaceProvider)
            } catch (_: Exception) {}
            return
        }

        viewModelScope.launch {
            try {
                val provider = getCameraProvider(ctx)
                cameraProvider = provider

                val preview = Preview.Builder().build().also { p ->
                    p.setSurfaceProvider(previewView.surfaceProvider)
                }
                previewUseCase = preview

                val quality = settings.videoQuality.first()
                // For front camera, cap at HD — most front cameras don't support
                // FHD+Preview simultaneously and throw "No supported surface combination".
                val effectiveQuality = if (_state.value.lensFacing == CameraSelector.LENS_FACING_FRONT
                    && (quality == VideoQuality.FHD || quality == VideoQuality.UHD)) {
                    Log.i(TAG, "Front camera detected — capping quality to HD to avoid surface combination error")
                    VideoQuality.HD
                } else {
                    quality
                }
                val recorder = buildRecorder(effectiveQuality)
                val capture = VideoCapture.withOutput(recorder)

                val selector = CameraSelector.Builder()
                    .requireLensFacing(_state.value.lensFacing)
                    .build()

                provider.unbindAll()

                // ===== Dual camera detection =====
                val dualRequested = settings.dualCameraEnabled.first()
                val concurrentSupported = try {
                    provider.availableConcurrentCameraInfos.isNotEmpty()
                } catch (_: Exception) { false }

                if (dualRequested && !concurrentSupported) {
                    _state.value = _state.value.copy(
                        error = ctx.getString(R.string.camera_dual_not_supported)
                    )
                }

                if (dualRequested && concurrentSupported) {
                    // Use CameraX ConcurrentCamera API (CameraX 1.4+)
                    val backSelector = if (_state.value.lensFacing == CameraSelector.LENS_FACING_BACK)
                        CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
                    val frontSelector = if (_state.value.lensFacing == CameraSelector.LENS_FACING_BACK)
                        CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA

                    val backRecorder = buildRecorder(quality)
                    val backCapture = VideoCapture.withOutput(backRecorder)
                    val frontRecorder = buildRecorder(quality)
                    val frontCapture = VideoCapture.withOutput(frontRecorder)

                    val primaryCap: VideoCapture<Recorder>
                    val secondaryCap: VideoCapture<Recorder>

                    val backUseCaseGroup = UseCaseGroup.Builder()
                        .addUseCase(preview)
                        .addUseCase(backCapture)
                        .build()
                    val frontUseCaseGroup = UseCaseGroup.Builder()
                        .addUseCase(frontCapture)
                        .build()

                    val backConfig = androidx.camera.core.ConcurrentCamera.SingleCameraConfig(
                        backSelector, backUseCaseGroup, cameraLifecycleOwner
                    )
                    val frontConfig = androidx.camera.core.ConcurrentCamera.SingleCameraConfig(
                        frontSelector, frontUseCaseGroup, cameraLifecycleOwner
                    )

                    try {
                        provider.bindToLifecycle(listOf(backConfig, frontConfig))
                        if (_state.value.lensFacing == CameraSelector.LENS_FACING_BACK) {
                            primaryCap = backCapture
                            secondaryCap = frontCapture
                        } else {
                            primaryCap = frontCapture
                            secondaryCap = backCapture
                        }
                        primaryCapture = primaryCap
                        secondaryCapture = secondaryCap
                    } catch (e: Exception) {
                        Log.w(TAG, "Concurrent camera bind failed, falling back to single", e)
                        try {
                            provider.bindToLifecycle(cameraLifecycleOwner, backSelector, preview, backCapture)
                            primaryCapture = backCapture
                            secondaryCapture = null
                        } catch (e2: Exception) {
                            // Last-resort: bind only video capture (no preview)
                            provider.bindToLifecycle(cameraLifecycleOwner, selector, capture)
                            primaryCapture = capture
                            secondaryCapture = null
                        }
                    }
                } else {
                    // Single camera with progressive fallback to avoid
                    // "No supported surface combination" crashes on devices
                    // whose camera doesn't support Preview + VideoCapture at the
                    // requested quality.
                    // Try 1: Preview + VideoCapture at effectiveQuality
                    // Try 2: Preview + VideoCapture at HD
                    // Try 3: Preview + VideoCapture at SD
                    // Try 4: VideoCapture only (no preview) at SD
                    var bound = false
                    val qualitiesToTry = mutableListOf(effectiveQuality)
                    if (effectiveQuality != VideoQuality.HD) qualitiesToTry.add(VideoQuality.HD)
                    if (effectiveQuality != VideoQuality.SD) qualitiesToTry.add(VideoQuality.SD)

                    for (q in qualitiesToTry) {
                        try {
                            val rec = buildRecorder(q)
                            val cap = VideoCapture.withOutput(rec)
                            provider.bindToLifecycle(cameraLifecycleOwner, selector, preview, cap)
                            primaryCapture = cap
                            Log.i(TAG, "Camera bound at quality=$q with preview")
                            bound = true
                            break
                        } catch (e: Exception) {
                            Log.w(TAG, "Bind failed at quality=$q with preview: ${e.message}")
                        }
                    }

                    if (!bound) {
                        // Last resort: VideoCapture only, no preview, at SD
                        try {
                            val sdRecorder = buildRecorder(VideoQuality.SD)
                            val sdCapture = VideoCapture.withOutput(sdRecorder)
                            provider.bindToLifecycle(cameraLifecycleOwner, selector, sdCapture)
                            primaryCapture = sdCapture
                            _state.value = _state.value.copy(
                                error = ctx.getString(R.string.camera_preview_unavailable)
                            )
                            Log.w(TAG, "Camera bound at SD quality, no preview (last resort)")
                        } catch (e3: Exception) {
                            Log.e(TAG, "All camera bind attempts failed", e3)
                            throw e3
                        }
                    }
                    secondaryCapture = null
                }

                _state.value = _state.value.copy(isCameraBound = true)
                startGpsStream()
            } catch (e: Exception) {
                Log.e(TAG, "bindCamera failed", e)
                _state.value = _state.value.copy(error = ctx.getString(R.string.camera_open_error, e.message),
                    isCameraBound = false
                )
            }
        }
    }

    /**
     * Releases the preview surface without stopping the recording.
     * Called when the user navigates away from the camera screen but the
     * recording should continue.
     */
    fun releasePreview() {
        try {
            previewUseCase?.setSurfaceProvider(null)
        } catch (_: Exception) {}
    }

    /**
     * Re-attaches a new PreviewView surface to the existing Preview use case
     * without rebinding. Use this when the user returns to the camera screen
     * while a recording is in progress (the PreviewView was recreated but
     * the camera use cases are still bound to the Activity lifecycle).
     */
    fun attachPreview(previewView: PreviewView) {
        try {
            previewUseCase?.setSurfaceProvider(previewView.surfaceProvider)
        } catch (_: Exception) {}
    }

    private fun buildRecorder(quality: VideoQuality): Recorder {
        return Recorder.Builder()
            .setQualitySelector(
                QualitySelector.from(
                    when (quality) {
                        VideoQuality.SD -> Quality.SD
                        VideoQuality.HD -> Quality.HD
                        VideoQuality.FHD -> Quality.FHD
                        VideoQuality.UHD -> Quality.UHD
                    },
                    // Critical: without a fallback strategy, binding Preview + VideoCapture
                    // can throw "No supported surface combination" on devices whose front
                    // camera doesn't support FHD+Preview simultaneously. The fallback lets
                    // CameraX step down to a lower quality instead of crashing.
                    FallbackStrategy.lowerQualityOrHigherThan(Quality.HD)
                )
            )
            .build()
    }

    fun toggleRecording() {
        if (_state.value.isRecording) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    private fun startRecording() {
        val capture = primaryCapture ?: run {
            _state.value = _state.value.copy(error = ctx.getString(R.string.camera_not_ready))
            return
        }

        viewModelScope.launch {
            try {
                val audioEnabled = settings.audioEnabled.first()
                val dualEnabled = _state.value.dualCameraEnabled && secondaryCapture != null
                val storageUriStr = settings.storageUri.first()
                val storageTreeUri = storageUriStr?.let { Uri.parse(it) }

                tripStartMs = System.currentTimeMillis()
                currentTripId = tripRepo.startTrip(tripStartMs, _state.value.lat, _state.value.lon)
                segmentIndex = 0

                _state.value = _state.value.copy(
                    isRecording = true,
                    startedAt = tripStartMs,
                    durationSec = 0,
                    distanceMeters = 0.0,
                    maxSpeedKmh = 0f,
                    avgSpeedKmh = 0f,
                    gForce = 0f,
                    error = null,
                    lastEventLabel = null
                )

                // Start foreground service so the recording survives background/screen-off.
                // The service acquires a PARTIAL_WAKE_LOCK and shows a persistent notification.
                RecordingService.start(ctx)

                // Keep screen on while recording (if user has the setting enabled)
                // The Activity reference is passed from CameraScreen via setKeepScreenOn()
                // — we can't call it here because ctx is ApplicationContext, not Activity.
                // CameraScreen observes isRecording and calls setKeepScreenOn().

                val primaryLens = lensLabel(_state.value.lensFacing)
                startNextSegment(capture, primaryLens, audioEnabled, storageTreeUri)

                if (dualEnabled) {
                    val secondaryLens = lensLabel(
                        if (_state.value.lensFacing == CameraSelector.LENS_FACING_BACK)
                            CameraSelector.LENS_FACING_FRONT
                        else CameraSelector.LENS_FACING_BACK
                    )
                    secondaryCapture?.let { secCap ->
                        startSecondarySegment(secCap, secondaryLens, audioEnabled, storageTreeUri)
                    }
                }

                startTimer()
                startSegmentTimer()
                startGForceStream()
            } catch (e: Exception) {
                Log.e(TAG, "startRecording failed", e)
                _state.value = _state.value.copy(error = "No se pudo iniciar: ${e.message}")
            }
        }
    }

    private fun lensLabel(lens: Int): String =
        if (lens == CameraSelector.LENS_FACING_BACK) "Back" else "Front"

    private fun startNextSegment(
        capture: VideoCapture<Recorder>,
        lensLabel: String,
        audioEnabled: Boolean,
        storageTreeUri: Uri?
    ) {
        val tripId = currentTripId
        val index = segmentIndex

        viewModelScope.launch {
            try {
                val limitGb = settings.storageLimitGb.first()
                val limitBytes = limitGb.toLong() * 1_073_741_824L
                var used = tripRepo.totalVideoSizeBytes()
                while (used > limitBytes) {
                    val oldest = tripRepo.oldestUnprotectedSegment() ?: break
                    FileUtils.safeDelete(File(oldest.filePath))
                    tripRepo.deleteSegment(oldest.id)
                    used = tripRepo.totalVideoSizeBytes()
                }

                val outFile = FileUtils.videoFileForSegment(ctx, tripId, index, lensLabel)
                val outputOpts = FileOutputOptions.Builder(outFile).build()
                val pending = capture.output.prepareRecording(ctx, outputOpts)

                currentSegmentStartMs = System.currentTimeMillis()

                primaryRecording = if (audioEnabled) {
                    pending.withAudioEnabled()
                        .start(ContextCompat.getMainExecutor(ctx)) { event ->
                            handleVideoEvent(event, tripId, index, lensLabel, outFile, storageTreeUri, isSecondary = false)
                        }
                } else {
                    pending.start(ContextCompat.getMainExecutor(ctx)) { event ->
                        handleVideoEvent(event, tripId, index, lensLabel, outFile, storageTreeUri, isSecondary = false)
                    }
                }

                _state.value = _state.value.copy(
                    currentSegmentIndex = index,
                    storageUsedBytes = used
                )
                segmentIndex++
            } catch (e: Exception) {
                Log.e(TAG, "startNextSegment failed", e)
            }
        }
    }

    private fun startSecondarySegment(
        capture: VideoCapture<Recorder>,
        lensLabel: String,
        audioEnabled: Boolean,
        storageTreeUri: Uri?
    ) {
        val tripId = currentTripId
        val index = segmentIndex

        viewModelScope.launch {
            try {
                val outFile = FileUtils.videoFileForSegment(ctx, tripId, index, lensLabel)
                val outputOpts = FileOutputOptions.Builder(outFile).build()
                val pending = capture.output.prepareRecording(ctx, outputOpts)

                // Note: secondary camera cannot use audio (only one mic at a time)
                secondaryRecording = pending.start(ContextCompat.getMainExecutor(ctx)) { event ->
                    handleVideoEvent(event, tripId, index, lensLabel, outFile, storageTreeUri, isSecondary = true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "startSecondarySegment failed", e)
            }
        }
    }

    private fun handleVideoEvent(
        event: VideoRecordEvent,
        tripId: Long,
        index: Int,
        lensLabel: String,
        outFile: File,
        storageTreeUri: Uri?,
        isSecondary: Boolean
    ) {
        when (event) {
            is VideoRecordEvent.Finalize -> {
                val hasError = event.hasError()
                val fileExists = outFile.exists() && outFile.length() > 0

                if (!hasError || fileExists) {
                    // Success OR partial file saved despite error — register it.
                    // Run on IO dispatcher so moveToCustomStorage (which can be a large
                    // file copy) doesn't block the main thread and cause an ANR.
                    viewModelScope.launch(Dispatchers.IO) {
                        val durationMs = System.currentTimeMillis() - currentSegmentStartMs
                        // If a custom storage folder is set, move the file there
                        val finalUri = if (fileExists) {
                            FileUtils.moveToCustomStorage(ctx, outFile, storageTreeUri, outFile.name)
                        } else null
                        val finalPath = finalUri?.toString() ?: outFile.absolutePath
                        val sizeBytes = if (storageTreeUri != null) {
                            outFile.length().takeIf { it > 0 } ?: 0L
                        } else {
                            outFile.length()
                        }

                        Log.i(TAG, "Segment saved: ${outFile.name}, size=$sizeBytes, duration=${durationMs}ms, error=$hasError")

                        tripRepo.addSegment(
                            VideoSegmentEntity(
                                tripId = tripId,
                                filePath = finalPath,
                                createdAt = currentSegmentStartMs,
                                durationMs = durationMs,
                                sizeBytes = sizeBytes
                            )
                        )
                        if (_state.value.isRecording && !isSecondary) {
                            val audio = settings.audioEnabled.first()
                            // Restart only the primary segment
                            primaryCapture?.let { cap ->
                                startNextSegment(cap, lensLabel, audio, storageTreeUri)
                            }
                        }
                    }
                } else {
                    Log.e(TAG, "Finalize error ($lensLabel), no file saved: ${event.cause}")
                    val cause = event.cause
                    if (cause != null && _state.value.isRecording) {
                        _state.value = _state.value.copy(
                            error = ctx.getString(R.string.camera_open_error, cause.message ?: "unknown")
                        )
                    }
                }
            }
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_state.value.isRecording) {
                delay(1000)
                _state.value = _state.value.copy(
                    durationSec = (System.currentTimeMillis() - tripStartMs) / 1000
                )
            }
        }
    }

    /**
     * Segment timer: stops the current recording every segmentDurationSec seconds.
     * The Finalize callback in handleVideoEvent will then save the segment and
     * automatically start the next one. This is what creates the "loop recording"
     * effect with multiple files instead of one giant file.
     */
    private fun startSegmentTimer() {
        segmentTimerJob?.cancel()
        segmentTimerJob = viewModelScope.launch {
            val segmentSec = settings.segmentDurationSec.first()
            Log.i(TAG, "Segment timer started: ${segmentSec}s per segment")
            while (_state.value.isRecording) {
                delay(segmentSec * 1000L)
                if (!_state.value.isRecording) break
                Log.i(TAG, "Segment timer: stopping segment ${segmentIndex - 1} after ${segmentSec}s")
                try {
                    primaryRecording?.stop()
                } catch (e: Exception) {
                    Log.w(TAG, "Segment timer: stop() failed", e)
                }
                // The Finalize callback will handle saving + starting next segment.
                // Also stop the secondary recording if dual camera is active.
                try {
                    secondaryRecording?.stop()
                } catch (_: Exception) {}
            }
        }
    }

    private fun startGpsStream() {
        gpsJob?.cancel()
        gpsJob = viewModelScope.launch {
            var lastLat: Double? = null
            var lastLon: Double? = null
            var distance: Double = 0.0
            var maxSpeed: Float = 0f
            var speedSum: Float = 0f
            var sampleCount: Int = 0

            try {
                gpsTracker.locationUpdates().collect { loc ->
                    val speed = if (loc.hasSpeed()) loc.speed else 0f
                    val speedKmh = speed * 3.6f

                    if (lastLat != null && lastLon != null) {
                        distance += haversine(lastLat!!, lastLon!!, loc.latitude, loc.longitude)
                    }
                    lastLat = loc.latitude
                    lastLon = loc.longitude

                    if (speedKmh > maxSpeed) maxSpeed = speedKmh
                    speedSum += speedKmh
                    sampleCount++

                    if (_state.value.isRecording) {
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
                    }

                    _state.value = _state.value.copy(
                        speedKmh = speedKmh,
                        distanceMeters = distance,
                        lat = loc.latitude,
                        lon = loc.longitude,
                        maxSpeedKmh = maxSpeed,
                        avgSpeedKmh = if (sampleCount > 0) speedSum / sampleCount else 0f
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "GPS stream error: ${e.message}")
            }
        }
    }

    private fun startGForceStream() {
        gForceJob?.cancel()
        gForceJob = viewModelScope.launch {
            val threshold = settings.gforceThreshold.first()
            var peakG = 0f

            try {
                gForceDetector.samples().collect { sample ->
                    if (sample.magnitudeG > peakG) {
                        peakG = sample.magnitudeG
                        _state.value = _state.value.copy(gForce = peakG)
                    }

                    val type = gForceDetector.classify(sample, threshold) ?: return@collect

                    if (_state.value.isRecording) {
                        tripRepo.addEvent(
                            GForceEventEntity(
                                tripId = currentTripId,
                                createdAt = sample.timestampMs,
                                type = type.name,
                                magnitudeG = sample.magnitudeG,
                                axisX = sample.x / GForceDetector.GRAVITY,
                                axisY = sample.y / GForceDetector.GRAVITY,
                                axisZ = sample.z / GForceDetector.GRAVITY,
                                lat = _state.value.lat,
                                lon = _state.value.lon,
                                speedKmh = _state.value.speedKmh
                            )
                        )
                        _state.value = _state.value.copy(
                            lastEventLabel = "${type.label} · ${"%.2f".format(sample.magnitudeG)}g"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "GForce stream error: ${e.message}")
            }
        }
    }

    private fun stopRecording() {
        // Cancel timers first so they don't try to stop() the recording again
        segmentTimerJob?.cancel()
        timerJob?.cancel()
        gForceJob?.cancel()

        // Stop the foreground service — releases wake lock and removes notification.
        RecordingService.stop(ctx)

        // Stop recordings — Finalize callback will fire asynchronously and save
        // the last segment. We don't wait for it here to avoid blocking the UI.
        try { primaryRecording?.stop() } catch (_: Exception) {}
        try { secondaryRecording?.stop() } catch (_: Exception) {}
        primaryRecording = null
        secondaryRecording = null

        // Keep screen on is handled by CameraScreen (which has the Activity reference)

        viewModelScope.launch(Dispatchers.IO) {
            val endMs = System.currentTimeMillis()
            val durationSec = (endMs - tripStartMs) / 1000
            val st = _state.value

            if (currentTripId > 0) {
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
                    videoSegmentCount = segmentIndex,
                    totalStorageBytes = st.storageUsedBytes,
                    protectedCount = 0
                )
            }

            _state.value = _state.value.copy(
                isRecording = false,
                durationSec = 0,
                currentSegmentIndex = 0,
                lastEventLabel = null,
                error = null
            )
            segmentIndex = 0
            currentTripId = 0L
        }
    }

    fun cleanup() {
        if (_state.value.isRecording) {
            stopRecording()
        }
        segmentTimerJob?.cancel()
        timerJob?.cancel()
        gpsJob?.cancel()
        gForceJob?.cancel()
        try { primaryRecording?.stop() } catch (_: Exception) {}
        try { secondaryRecording?.stop() } catch (_: Exception) {}
        primaryRecording = null
        secondaryRecording = null
    }

    fun toggleLens() {
        if (_state.value.isRecording) return
        val newLens = if (_state.value.lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        _state.value = _state.value.copy(
            lensFacing = newLens,
            isCameraBound = false
        )
        viewModelScope.launch { settings.setDefaultLens(newLens) }
    }

    fun toggleRotationLock() {
        _state.value = _state.value.copy(
            rotationLocked = !_state.value.rotationLocked
        )
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    override fun onCleared() {
        super.onCleared()
        cleanup()
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

    /**
     * Sets FLAG_KEEP_SCREEN_ON on the given Activity's window so the screen
     * doesn't turn off while recording.
     */
    fun setKeepScreenOn(activity: android.app.Activity?, enabled: Boolean) {
        try {
            activity?.runOnUiThread {
                if (enabled) {
                    activity.window?.addFlags(
                        android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    )
                } else {
                    activity.window?.clearFlags(
                        android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "setKeepScreenOn failed", e)
        }
    }

    companion object {
        private const val TAG = "CameraViewModel"

        suspend fun getCameraProvider(ctx: Context): ProcessCameraProvider =
            suspendCoroutine { cont ->
                val future = ProcessCameraProvider.getInstance(ctx)
                future.addListener({
                    try {
                        cont.resume(future.get())
                    } catch (e: Exception) {
                        cont.resumeWithException(e)
                    }
                }, ContextCompat.getMainExecutor(ctx))
            }
    }
}

/**
 * LifecycleOwner that stays in the RESUMED state forever.
 *
 * Used by CameraViewModel to bind CameraX use cases so that the camera is NOT
 * released when the Activity goes to STOPPED (background/screen-off). Combined
 * with the RecordingService foreground service + wake lock, this allows the
 * recording to continue uninterrupted when the user leaves the app or the screen
 * turns off.
 */
private class AlwaysResumedLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    init {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }
    override val lifecycle: LifecycleRegistry = registry
}
