package com.danielbarrantesv.dashcam.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danielbarrantesv.dashcam.data.model.ReportEntity
import com.danielbarrantesv.dashcam.data.model.TripEntity
import com.danielbarrantesv.dashcam.data.repository.ReportRepository
import com.danielbarrantesv.dashcam.data.repository.TripRepository
import com.danielbarrantesv.dashcam.reports.PdfGenerator
import com.danielbarrantesv.dashcam.reports.ReportInput
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReportsUiState(
    val reports: List<ReportEntity> = emptyList(),
    val trips: List<TripEntity> = emptyList(),
    val isGenerating: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ReportsViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val tripRepo: TripRepository,
    private val reportRepo: ReportRepository,
    private val pdf: PdfGenerator
) : ViewModel() {

    private val _state = MutableStateFlow(ReportsUiState())
    val state: StateFlow<ReportsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                tripRepo.observeAllTrips(),
                reportRepo.observeAll()
            ) { trips, reports ->
                ReportsUiState(reports = reports, trips = trips)
            }.collect { _state.value = it }
        }
    }

    fun generateReport(trip: TripEntity, holder: String, policy: String, plate: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isGenerating = true, error = null)
            try {
                val events = tripRepo.getEventsForTrip(trip.id)
                val gps = tripRepo.getGpsForTrip(trip.id)

                val file = pdf.generate(
                    ctx,
                    ReportInput(trip, events, gps, holder, policy, plate)
                )

                val reportId = reportRepo.add(
                    ReportEntity(
                        tripId = trip.id,
                        createdAt = System.currentTimeMillis(),
                        filePath = file.absolutePath,
                        fileSizeBytes = file.length(),
                        policyHolderName = holder.ifBlank { null },
                        policyNumber = policy.ifBlank { null },
                        vehiclePlate = plate.ifBlank { null }
                    )
                )
                tripRepo.setReportPath(trip.id, file.absolutePath)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(error = t.message ?: "Error al generar el reporte")
            } finally {
                _state.value = _state.value.copy(isGenerating = false)
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
