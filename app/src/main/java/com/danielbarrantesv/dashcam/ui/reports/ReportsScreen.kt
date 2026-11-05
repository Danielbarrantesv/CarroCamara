package com.danielbarrantesv.dashcam.ui.reports

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danielbarrantesv.dashcam.data.model.ReportEntity
import com.danielbarrantesv.dashcam.data.model.TripEntity
import com.danielbarrantesv.dashcam.ui.theme.DashCamTypography
import com.danielbarrantesv.dashcam.ui.theme.Primary
import com.danielbarrantesv.dashcam.util.FileUtils
import com.danielbarrantesv.dashcam.util.Formatters
import java.io.File

@Composable
fun ReportsScreen(
    viewModel: ReportsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Text(
            "Reportes para aseguradora",
            style = DashCamTypography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(24.dp, 24.dp, 24.dp, 8.dp)
        )

        if (state.reports.isEmpty() && state.trips.isEmpty()) {
            EmptyReports()
            return
        }

        // Generate new report card
        GenerateReportCard(
            trips = state.trips,
            isGenerating = state.isGenerating,
            onGenerate = { trip, name, policy, plate ->
                viewModel.generateReport(trip, name, policy, plate)
            }
        )

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(state.reports, key = { it.id }) { r ->
                ReportCard(r) { file ->
                    openReport(ctx, file)
                }
            }
        }
    }

    if (state.error != null) {
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            confirmButton = {
                TextButton(onClick = viewModel::clearError) { Text("OK") }
            },
            title = { Text("Error") },
            text = { Text(state.error!!) }
        )
    }
}

@Composable
private fun EmptyReports() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.PictureAsPdf, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(80.dp))
            Spacer(Modifier.height(16.dp))
            Text("Aún no has generado reportes", style = DashCamTypography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text("Graba un viaje desde Cámara para empezar", style = DashCamTypography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun GenerateReportCard(
    trips: List<TripEntity>,
    isGenerating: Boolean,
    onGenerate: (TripEntity, String, String, String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedTrip by remember { mutableStateOf<TripEntity?>(null) }
    var holder by remember { mutableStateOf("") }
    var policy by remember { mutableStateOf("") }
    var plate by remember { mutableStateOf("") }

    Card(
        Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Generar nuevo reporte", style = DashCamTypography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))

            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = selectedTrip?.let { "Viaje del ${Formatters.dateTime(it.startedAt)}" } ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Selecciona un viaje") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    trips.forEach { trip ->
                        DropdownMenuItem(
                            text = { Text("Viaje del ${Formatters.dateTime(trip.startedAt)} · ${Formatters.distance(trip.distanceMeters)}") },
                            onClick = {
                                selectedTrip = trip
                                expanded = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = holder, onValueChange = { holder = it },
                label = { Text("Titular de la póliza") },
                modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = policy, onValueChange = { policy = it },
                label = { Text("No. de póliza") },
                modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = plate, onValueChange = { plate = it },
                label = { Text("Placa del vehículo") },
                modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { selectedTrip?.let { onGenerate(it, holder, policy, plate) } },
                enabled = selectedTrip != null && !isGenerating,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Generando…", color = Color.Black)
                } else {
                    Icon(Icons.Filled.PictureAsPdf, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Generar PDF", color = Color.Black, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ReportCard(report: ReportEntity, onOpen: (File) -> Unit) {
    val file = File(report.filePath)
    Card(
        Modifier.fillMaxWidth().clickable { onOpen(file) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Primary),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Description, contentDescription = null, tint = Color.Black, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(file.name, style = DashCamTypography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Text(
                    "${Formatters.dateTime(report.createdAt)} · ${FileUtils.formatBytes(report.fileSizeBytes)}",
                    style = DashCamTypography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val ctx = LocalContext.current
            IconButton(onClick = { onOpen(file) }) {
                Icon(Icons.Filled.OpenInNew, contentDescription = "Abrir", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { shareReport(ctx, file) }) {
                Icon(Icons.Filled.Share, contentDescription = "Compartir", tint = Primary)
            }
        }
    }
}

private fun openReport(ctx: android.content.Context, file: File) {
    val uri = FileUtils.fileUri(ctx, file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    ctx.startActivity(Intent.createChooser(intent, "Abrir reporte"))
}

private fun shareReport(ctx: android.content.Context, file: File) {
    val uri = FileUtils.fileUri(ctx, file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(intent, "Compartir reporte"))
}
