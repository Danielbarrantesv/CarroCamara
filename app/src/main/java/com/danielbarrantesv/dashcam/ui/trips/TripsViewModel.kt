package com.danielbarrantesv.dashcam.ui.trips

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danielbarrantesv.dashcam.data.model.TripEntity
import com.danielbarrantesv.dashcam.data.model.VideoSegmentEntity
import com.danielbarrantesv.dashcam.data.repository.TripRepository
import com.danielbarrantesv.dashcam.util.FileUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class TripsUiState(
    val trips: List<TripEntity> = emptyList(),
    val isDeletingAll: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class TripsViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val tripRepo: TripRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TripsUiState())
    val uiState: StateFlow<TripsUiState> = _uiState.asStateFlow()

    val trips: StateFlow<List<TripEntity>> = tripRepo.observeAllTrips()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun getSegmentsForTrip(tripId: Long, onResult: (List<VideoSegmentEntity>) -> Unit) {
        viewModelScope.launch {
            onResult(tripRepo.getSegmentsForTrip(tripId))
        }
    }

    fun deleteTrip(tripId: Long) {
        viewModelScope.launch {
            // Delete segment files from disk
            val segments = tripRepo.getSegmentsForTrip(tripId)
            segments.forEach { seg ->
                FileUtils.safeDelete(File(seg.filePath))
            }
            // Delete the trip (cascades to segments, events, gps_samples)
            tripRepo.deleteTrip(tripId)
            _uiState.value = _uiState.value.copy(message = "Viaje eliminado")
        }
    }

    fun deleteSegment(segment: VideoSegmentEntity) {
        viewModelScope.launch {
            FileUtils.safeDelete(File(segment.filePath))
            tripRepo.deleteSegment(segment.id)
            _uiState.value = _uiState.value.copy(message = "Video eliminado")
        }
    }

    fun deleteAllTrips() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDeletingAll = true)
            try {
                val allTrips = tripRepo.observeAllTrips()
                // Collect current value
                val currentTrips = trips.value
                currentTrips.forEach { trip ->
                    val segments = tripRepo.getSegmentsForTrip(trip.id)
                    segments.forEach { seg ->
                        FileUtils.safeDelete(File(seg.filePath))
                    }
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
