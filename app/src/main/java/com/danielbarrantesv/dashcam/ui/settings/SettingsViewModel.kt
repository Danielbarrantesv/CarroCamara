package com.danielbarrantesv.dashcam.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danielbarrantesv.dashcam.data.prefs.SettingsRepository
import com.danielbarrantesv.dashcam.data.prefs.SpeedUnit
import com.danielbarrantesv.dashcam.data.prefs.VideoQuality
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val audioEnabled: Boolean = false,
    val videoQuality: VideoQuality = VideoQuality.FHD,
    val segmentDurationSec: Int = 60,
    val storageLimitGb: Int = 5,
    val gforceThreshold: Float = 0.8f,
    val speedUnit: SpeedUnit = SpeedUnit.KMH,
    val autoStartOnPower: Boolean = false,
    val keepScreenOn: Boolean = true
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = combine(
        combine(repo.audioEnabled, repo.videoQuality, repo.segmentDurationSec) { a, b, c -> Triple(a, b, c) },
        combine(repo.storageLimitGb, repo.gforceThreshold, repo.speedUnit) { a, b, c -> Triple(a, b, c) },
        combine(repo.autoStartOnPower, repo.keepScreenOn) { a, b -> Pair(a, b) }
    ) { rec, sens, other ->
        SettingsUiState(
            audioEnabled = rec.first,
            videoQuality = rec.second,
            segmentDurationSec = rec.third,
            storageLimitGb = sens.first,
            gforceThreshold = sens.second,
            speedUnit = sens.third,
            autoStartOnPower = other.first,
            keepScreenOn = other.second
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    fun setAudio(v: Boolean) = viewModelScope.launch { repo.setAudioEnabled(v) }
    fun setVideoQuality(q: VideoQuality) = viewModelScope.launch { repo.setVideoQuality(q) }
    fun setSegmentDuration(s: Int) = viewModelScope.launch { repo.setSegmentDurationSec(s) }
    fun setStorageGb(gb: Int) = viewModelScope.launch { repo.setStorageLimitGb(gb) }
    fun setGforceThreshold(g: Float) = viewModelScope.launch { repo.setGforceThreshold(g) }
    fun setSpeedUnit(u: SpeedUnit) = viewModelScope.launch { repo.setSpeedUnit(u) }
    fun setAutoStart(v: Boolean) = viewModelScope.launch { repo.setAutoStartOnPower(v) }
    fun setKeepScreenOn(v: Boolean) = viewModelScope.launch { repo.setKeepScreenOn(v) }
}
