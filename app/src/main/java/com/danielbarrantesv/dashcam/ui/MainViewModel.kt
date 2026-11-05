package com.danielbarrantesv.dashcam.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danielbarrantesv.dashcam.data.prefs.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val settings: SettingsRepository
) : ViewModel() {

    val hasCompletedOnboarding: StateFlow<Boolean?> = settings.hasCompletedOnboarding
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun markOnboardingComplete() {
        viewModelScope.launch { settings.setOnboardingComplete() }
    }
}
