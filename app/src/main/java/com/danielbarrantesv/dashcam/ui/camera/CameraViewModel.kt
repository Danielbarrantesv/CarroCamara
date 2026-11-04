package com.danielbarrantesv.dashcam.ui.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.camera.core.ConcurrentCamera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danielbarrantesv.dashcam.data.model.GForceEventEntity
import com.danielbarrantesv.dashcam.data.model.GpsSampleEntity
import com.danielbarrantesv.dashcam.data.model.VideoSegmentEntity
import com.danielbarrantesv.dashcam.data.prefs.SettingsRepository
import com.danielbarrantesv.dashcam.data.prefs.SpeedUnit
import com.danielbarrantesv.dashcam.data.prefs.VideoQuality
import com.danielbarrantesv.dashcam.data.repository.TripRepository
import com.danielbarrantesv.dashcam.service.GForceDetector
import com.danielbarrantesv.dashcam.service.GpsTracker
import com.danielbarrantesv.dashcam.util.FileUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
            _state.value = _state.value.copy(error = "Permiso de cámara no concedido")
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
                val recorder = buildRecorder(quality)
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
                        error = "Tu dispositivo no soporta grabación con doble cámara. " +
                            "Se grabará solo con la cámara seleccionada."
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

                    val backConfig = ConcurrentCamera.SingleCameraConfig(
                        backSelector, backUseCaseGroup, lifecycleOwner
                    )
                    val frontConfig = ConcurrentCamera.SingleCameraConfig(
                        frontSelector, frontUseCaseGroup, lifecycleOwner
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
                        provider.bindToLifecycle(lifecycleOwner, backSelector, preview, backCapture)
                        primaryCapture = backCapture
                        secondaryCapture = null
                    }
                } else {
                    provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                    primaryCapture = capture
                    secondaryCapture = null
                }

                _state.value = _state.value.copy(isCameraBound = true)
                startGpsStream()
            } catch (e: Exception) {
                Log.e(TAG, "bindCamera failed", e)
                _state.value = _state.value.copy(
                    error = "No se pudo abrir la cámara: ${e.message}",
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

    private fun buildRecorder(quality: VideoQuality): Recorder {
        return Recorder.Builder()
            .setQualitySelector(
                QualitySelector.from(
                    when (quality) {
                        VideoQuality.SD -> Quality.SD
                        VideoQuality.HD -> Quality.HD
                        VideoQuality.FHD -> Quality.FHD
                        VideoQuality.UHD -> Quality.UHD
                    }
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
            _state.value = _state.value.copy(error = "Cámara no lista. Cierra y abre la app.")
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
                if (!event.hasError()) {
                    viewModelScope.launch {
                        val durationMs = System.currentTimeMillis() - currentSegmentStartMs
                        // If a custom storage folder is set, move the file there
                        val finalUri = FileUtils.moveToCustomStorage(
                            ctx, outFile, storageTreeUri, outFile.name
                        )
                        val finalPath = finalUri?.toString() ?: outFile.absolutePath

                        tripRepo.addSegment(
                            VideoSegmentEntity(
                                tripId = tripId,
                                filePath = finalPath,
                                createdAt = currentSegmentStartMs,
                                durationMs = durationMs,
                                sizeBytes = if (storageTreeUri != null) {
                                    // File was moved to SAF, size is what we recorded
                                    outFile.length().takeIf { it > 0 } ?: 0L
                                } else outFile.length()
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
                    Log.e(TAG, "Finalize error ($lensLabel): ${event.cause}")
                    val cause = event.cause
                    if (cause != null && _state.value.isRecording) {
                        _state.value = _state.value.copy(
                            error = "Grabación interrumpida: ${cause.message ?: "error desconocido"}"
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
        try { primaryRecording?.stop() } catch (_: Exception) {}
        try { secondaryRecording?.stop() } catch (_: Exception) {}
        primaryRecording = null
        secondaryRecording = null
        timerJob?.cancel()
        gForceJob?.cancel()

        viewModelScope.launch {
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
