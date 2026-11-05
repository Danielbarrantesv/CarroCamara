package com.danielbarrantesv.dashcam.service

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.PendingRecording
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture as VideoCaptureV2
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.danielbarrantesv.dashcam.data.model.VideoSegmentEntity
import com.danielbarrantesv.dashcam.data.prefs.SettingsRepository
import com.danielbarrantesv.dashcam.data.prefs.VideoQuality
import com.danielbarrantesv.dashcam.data.repository.TripRepository
import com.danielbarrantesv.dashcam.util.FileUtils
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "LoopRecorder"

/**
 * Loop recorder built on top of CameraX VideoCapture with Recorder.
 *
 * - Starts one [Recording] per segment of [segmentDurationSec] seconds.
 * - On each segment boundary: stops current, writes a [VideoSegmentEntity] to DB,
 *   starts the next.
 * - On storage overflow: deletes the oldest unprotected segment and its file
 *   before starting a new segment.
 */
@Singleton
class LoopRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val tripRepo: TripRepository
) {
    data class State(
        val isRecording: Boolean = false,
        val currentSegmentIndex: Int = 0,
        val currentSegmentStartMs: Long = 0,
        val tripId: Long = 0,
        val storageUsedBytes: Long = 0,
        val storageLimitBytes: Long = 0,
        val error: String? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    private var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>? = null
    private var videoCapture: VideoCaptureV2<Recorder>? = null
    private var activeRecording: Recording? = null
    private var lifecycleOwner: LifecycleOwner? = null

    private var segmentSec: Int = 60

    fun bind(lifecycleOwner: LifecycleOwner) {
        this.lifecycleOwner = lifecycleOwner
        cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    }

    fun startRecording(tripId: Long, lensFacing: Int) {
        val owner = lifecycleOwner ?: run {
            _state.value = _state.value.copy(error = "Lifecycle no bound")
            return
        }

        scope.launch {
            val quality = settings.videoQuality.first()
            segmentSec = settings.segmentDurationSec.first()
            val storageGb = settings.storageLimitGb.first()
            val audioEnabled = settings.audioEnabled.first()

            try {
                val provider = cameraProviderFuture?.get() ?: return@launch
                provider.unbindAll()

                val recorder = Recorder.Builder()
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
                val capture = VideoCaptureV2.Builder(recorder).build()
                val preview = Preview.Builder().build()

                val selector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()

                provider.bindToLifecycle(owner, selector, preview, capture)

                videoCapture = capture
                _state.value = _state.value.copy(
                    isRecording = true,
                    tripId = tripId,
                    currentSegmentIndex = 0,
                    storageLimitBytes = storageGb.toLong() * 1_073_741_824L
                )

                startNextSegment(tripId, 0, audioEnabled)
            } catch (t: Throwable) {
                Log.e(TAG, "startRecording failed", t)
                _state.value = _state.value.copy(error = t.message ?: "Camera error")
            }
        }
    }

    private fun startNextSegment(tripId: Long, index: Int, audioEnabled: Boolean) {
        val capture = videoCapture ?: return

        scope.launch {
            val limit = _state.value.storageLimitBytes
            var used = tripRepo.totalVideoSizeBytes()
            while (used > limit) {
                val oldest = tripRepo.oldestUnprotectedSegment() ?: break
                FileUtils.safeDelete(File(oldest.filePath))
                tripRepo.deleteSegment(oldest.id)
                used = tripRepo.totalVideoSizeBytes()
            }

            val outFile = FileUtils.videoFileForSegment(context, tripId, index)
            val outputOpts = FileOutputOptions.Builder(outFile).build()

            val pending: PendingRecording = capture.output.prepareRecording(context, outputOpts)

            activeRecording = if (audioEnabled) {
                pending
                    .withAudioEnabled()
                    .start(ContextCompat.getMainExecutor(context)) { event ->
                        handleVideoEvent(event, tripId, index, outFile)
                    }
            } else {
                pending
                    .start(ContextCompat.getMainExecutor(context)) { event ->
                        handleVideoEvent(event, tripId, index, outFile)
                    }
            }

            _state.value = _state.value.copy(
                currentSegmentIndex = index,
                currentSegmentStartMs = System.currentTimeMillis(),
                storageUsedBytes = used
            )
        }
    }

    private fun handleVideoEvent(
        event: VideoRecordEvent,
        tripId: Long,
        index: Int,
        outFile: File
    ) {
        when (event) {
            is VideoRecordEvent.Status -> Unit
            is VideoRecordEvent.Finalize -> {
                if (!event.hasError()) {
                    val durationMs =
                        System.currentTimeMillis() - _state.value.currentSegmentStartMs
                    scope.launch {
                        tripRepo.addSegment(
                            VideoSegmentEntity(
                                tripId = tripId,
                                filePath = outFile.absolutePath,
                                createdAt = _state.value.currentSegmentStartMs,
                                durationMs = durationMs,
                                sizeBytes = outFile.length()
                            )
                        )
                        if (_state.value.isRecording) {
                            val audio = settings.audioEnabled.first()
                            startNextSegment(tripId, index + 1, audio)
                        }
                    }
                } else {
                    Log.e(TAG, "Finalize error: ${event.cause}")
                    _state.value = _state.value.copy(error = event.cause?.message)
                }
            }
            else -> Unit
        }
    }

    fun stopRecording() {
        activeRecording?.stop()
        activeRecording = null
        cameraProviderFuture?.get()?.unbindAll()
        _state.value = _state.value.copy(isRecording = false)
    }

    fun protectCurrentSegment() {
        scope.launch {
            val st = _state.value
            val segments = tripRepo.getSegmentsForTrip(st.tripId)
            val current = segments.lastOrNull()
            current?.let { tripRepo.setSegmentProtected(it.id, true) }
        }
    }

    /** Force-stop current segment and immediately start the next one. */
    fun forceNewSegment() {
        activeRecording?.stop()
    }
}
