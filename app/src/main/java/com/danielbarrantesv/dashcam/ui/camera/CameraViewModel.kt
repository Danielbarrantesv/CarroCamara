package com.danielbarrantesv.dashcam.ui.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danielbarrantesv.dashcam.data.prefs.SettingsRepository
import com.danielbarrantesv.dashcam.data.prefs.SpeedUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CameraUiState(
    val isRecording: Boolean = false,
    val durationSec: Long = 0,
    val speedKmh: Float = 0f,
    val distanceMeters: Double = 0.0,
    val gForce: Float = 0f,
    val lat: Double? = null,
    val lon: Double? = null,
    val speedUnit: String = "km/h",
    val lensFacing: Int = 0, // CameraSelector.LENS_FACING_BACK = 0
    val rotationLocked: Boolean = false
)

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val settings: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(CameraUiState())
    val state: StateFlow<CameraUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settings.speedUnit.collect { u: SpeedUnit ->
                _state.value = _state.value.copy(speedUnit = u.label)
            }
        }
    }

    fun toggleLens() {
        _state.value = _state.value.copy(
            lensFacing = if (_state.value.lensFacing == 0) 1 else 0
        )
    }

    fun toggleRotationLock() {
        _state.value = _state.value.copy(
            rotationLocked = !_state.value.rotationLocked
        )
    }

    // In a production app, RecordingService exposes a StateFlow<RecordingState> that
    // we would map to CameraUiState. This is wired when the service binds.
}
