package com.danielbarrantesv.dashcam.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danielbarrantesv.dashcam.data.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class TripsViewModel @Inject constructor(
    private val tripRepo: TripRepository
) : ViewModel() {

    val trips: StateFlow<List<com.danielbarrantesv.dashcam.data.model.TripEntity>> =
        tripRepo.observeAllTrips()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}
