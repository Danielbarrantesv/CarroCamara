package com.danielbarrantesv.dashcam.ui.trips

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danielbarrantesv.dashcam.data.model.TripEntity
import com.danielbarrantesv.dashcam.data.model.VideoSegmentEntity
import com.danielbarrantesv.dashcam.data.repository.TripRepository
import com.danielbarrantesv.dashcam.data.prefs.SettingsRepository
import com.danielbarrantesv.dashcam.util.FileUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class TripsUiState(
    val trips: List<TripEntity> = emptyList(),
    val isDeletingAll: Boolean = false,
    val message: String? = null,
    val storageUri: String? = null
)

@HiltViewModel
class TripsViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val tripRepo: TripRepository,
    private val settings: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TripsUiState())
    val uiState: StateFlow<TripsUiState> = _uiState.asStateFlow()

    val trips: StateFlow<List<TripEntity>> = tripRepo.observeAllTrips()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        viewModelScope.launch {
            settings.storageUri.collect { uri ->
                _uiState.value = _uiState.value.copy(storageUri = uri)
            }
        }
    }

    fun getSegmentsForTrip(tripId: Long, onResult: (List<VideoSegmentEntity>) -> Unit) {
        viewModelScope.launch {
            onResult(tripRepo.getSegmentsForTrip(tripId))
        }
    }

    /** Returns true if the segment file exists (either as a File or as a SAF Uri). */
    fun segmentExists(segment: VideoSegmentEntity): Boolean {
        return try {
            val path = segment.filePath
            if (path.startsWith("content://")) {
                val uri = Uri.parse(path)
                ctx.contentResolver.openInputStream(uri)?.close()
                true
            } else {
                File(path).exists()
            }
        } catch (e: Exception) {
            false
        }
    }

    /** Returns a Uri that can be played by ExoPlayer, regardless of storage type. */
    fun segmentPlayUri(segment: VideoSegmentEntity): Uri? {
        return try {
            val path = segment.filePath
            if (path.startsWith("content://")) {
                Uri.parse(path)
            } else {
                val file = File(path)
                if (file.exists()) Uri.fromFile(file) else null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun deleteTrip(tripId: Long) {
        viewModelScope.launch {
            val segments = tripRepo.getSegmentsForTrip(tripId)
            segments.forEach { seg -> deleteSegmentFile(seg) }
            tripRepo.deleteTrip(tripId)
            _uiState.value = _uiState.value.copy(message = "Viaje eliminado")
        }
    }

    fun deleteSegment(segment: VideoSegmentEntity) {
        viewModelScope.launch {
            deleteSegmentFile(segment)
            tripRepo.deleteSegment(segment.id)
            _uiState.value = _uiState.value.copy(message = "Video eliminado")
        }
    }

    private fun deleteSegmentFile(segment: VideoSegmentEntity) {
        try {
            val path = segment.filePath
            if (path.startsWith("content://")) {
                FileUtils.deleteSafFile(ctx, Uri.parse(path))
            } else {
                FileUtils.safeDelete(File(path))
            }
        } catch (_: Exception) {}
    }

    fun deleteAllTrips() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDeletingAll = true)
            try {
                val currentTrips = trips.value
                currentTrips.forEach { trip ->
                    val segments = tripRepo.getSegmentsForTrip(trip.id)
                    segments.forEach { seg -> deleteSegmentFile(seg) }
                    tripRepo.deleteTrip(trip.id)
                }
                _uiState.value = _uiState.value.copy(
                    isDeletingAll = false,
                    message = "${currentTrips.size} viajes eliminados"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isDeletingAll = false,
                    message = "Error: ${e.message}"
                )
            }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}

