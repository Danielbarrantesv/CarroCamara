package com.danielbarrantesv.dashcam.ui.trips

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danielbarrantesv.dashcam.data.model.TripEntity
import com.danielbarrantesv.dashcam.data.model.VideoSegmentEntity
import com.danielbarrantesv.dashcam.ui.theme.DashCamTypography
import com.danielbarrantesv.dashcam.ui.theme.HudAmber
import com.danielbarrantesv.dashcam.ui.theme.HudGreen
import com.danielbarrantesv.dashcam.ui.theme.HudRed
import com.danielbarrantesv.dashcam.ui.theme.Primary
import com.danielbarrantesv.dashcam.util.FileUtils
import com.danielbarrantesv.dashcam.util.Formatters
import java.io.File

@Composable
fun TripsScreen(
    viewModel: TripsViewModel = hiltViewModel()
) {
    val trips by viewModel.trips.collectAsStateWithLifecycle(emptyList())
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    // Track expanded trips (showing their video list)
    val expandedTrips = remember { mutableStateMapOf<Long, Boolean>() }
    // Track segments loaded per trip
    val tripSegments = remember { mutableStateMapOf<Long, List<VideoSegmentEntity>>() }
    // Video player state
    var playingUri by remember { mutableStateOf<Uri?>(null) }
    // Delete-all confirmation dialog
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    // Snackbar for messages
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            // Header with delete-all button
            Row(
                Modifier.fillMaxWidth().padding(24.dp, 24.dp, 16.dp, 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Viajes grabados",
                    style = DashCamTypography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                if (trips.isNotEmpty()) {
                    IconButton(onClick = { showDeleteAllDialog = true }) {
                        Icon(
                            Icons.Filled.DeleteSweep,
                            contentDescription = "Borrar todos",
                            tint = HudRed,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            if (trips.isEmpty()) {
                EmptyTrips()
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, bottom = 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(trips, key = { it.id }) { trip ->
                        val isExpanded = expandedTrips[trip.id] == true
                        TripCard(
                            trip = trip,
                            isExpanded = isExpanded,
                            segments = tripSegments[trip.id] ?: emptyList(),
                            viewModel = viewModel,
                            onToggleExpand = {
                                val newValue = !isExpanded
                                expandedTrips[trip.id] = newValue
                                if (newValue && tripSegments[trip.id] == null) {
                                    viewModel.getSegmentsForTrip(trip.id) { segs ->
                                        tripSegments[trip.id] = segs
                                    }
                                }
                            },
                            onPlaySegment = { seg ->
                                viewModel.segmentPlayUri(seg)?.let { uri ->
                                    playingUri = uri
                                }
                            },
                            onDeleteSegment = { seg ->
                                viewModel.deleteSegment(seg)
                                viewModel.getSegmentsForTrip(trip.id) { segs ->
                                    tripSegments[trip.id] = segs
                                }
                            },
                            onDeleteTrip = { viewModel.deleteTrip(trip.id) }
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        )

        // Delete all confirmation dialog
        if (showDeleteAllDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteAllDialog = false },
                title = { Text("¿Borrar todos los viajes?") },
                text = {
                    Text(
                        "Se eliminarán ${trips.size} viajes y todos sus videos. " +
                        "Esta acción no se puede deshacer."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteAllDialog = false
                            viewModel.deleteAllTrips()
                            expandedTrips.clear()
                            tripSegments.clear()
                        }
                    ) { Text("Borrar todo", color = HudRed) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteAllDialog = false }) { Text("Cancelar") }
                }
            )
        }

        // Loading overlay during delete-all
        if (uiState.isDeletingAll) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Primary)
                    Spacer(Modifier.height(12.dp))
                    Text("Borrando viajes…", color = Color.White)
                }
            }
        }

        // Full-screen video player
        playingUri?.let { uri ->
            VideoPlayer(uri = uri, onClose = { playingUri = null })
        }
    }
}

@Composable
private fun EmptyTrips() {
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.DirectionsCar,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(80.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Aún no hay viajes grabados",
                style = DashCamTypography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Inicia la grabación desde la pestaña Cámara",
                style = DashCamTypography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TripCard(
    trip: TripEntity,
    isExpanded: Boolean,
    segments: List<VideoSegmentEntity>,
    viewModel: TripsViewModel,
    onToggleExpand: () -> Unit,
    onPlaySegment: (VideoSegmentEntity) -> Unit,
    onDeleteSegment: (VideoSegmentEntity) -> Unit,
    onDeleteTrip: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.DirectionsCar, contentDescription = null, tint = Color.Black, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Viaje del ${Formatters.dateTime(trip.startedAt)}",
                        style = DashCamTypography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Duración: ${Formatters.duration(trip.durationSeconds)}",
                        style = DashCamTypography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (trip.reportPath != null) {
                    Box(
                        Modifier.size(8.dp).clip(CircleShape).background(HudGreen)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                IconButton(onClick = onToggleExpand) {
                    Icon(
                        if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (isExpanded) "Contraer" else "Expandir",
                        tint = Primary
                    )
                }
                IconButton(onClick = onDeleteTrip) {
                    Icon(Icons.Filled.Delete, contentDescription = "Eliminar viaje", tint = HudRed)
                }
            }
            Spacer(Modifier.height(16.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatBadge(Icons.Filled.Straighten, Formatters.distance(trip.distanceMeters), "Distancia")
                StatBadge(Icons.Filled.Speed, "${"%.0f".format(trip.maxSpeedKmh)} km/h", "Vel. máx")
                StatBadge(Icons.Filled.Schedule, Formatters.duration(trip.durationSeconds), "Duración")
                StatBadge(Icons.Filled.WarningAmber, "${trip.eventCount}", "Eventos", trip.eventCount > 0)
            }

            if (trip.maxGForce > 0f) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "G máx: ${"%.2f".format(trip.maxGForce)} g  ·  ${trip.videoSegmentCount} videos  ·  ${
                        FileUtils.formatBytes(trip.totalStorageBytes)
                    }",
                    style = DashCamTypography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Expanded section: list of videos
            AnimatedVisibility(visible = isExpanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column(Modifier.padding(top = 16.dp)) {
                    Text(
                        "Videos (${segments.size})",
                        style = DashCamTypography.titleSmall,
                        color = Primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    if (segments.isEmpty()) {
                        Text(
                            "Sin videos disponibles",
                            style = DashCamTypography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        segments.forEach { seg ->
                            VideoSegmentItem(
                                segment = seg,
                                exists = viewModel.segmentExists(seg),
                                onPlay = { onPlaySegment(seg) },
                                onDelete = { onDeleteSegment(seg) }
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoSegmentItem(
    segment: VideoSegmentEntity,
    exists: Boolean,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    val displayName = segment.filePath.substringAfterLast('/').let {
        if (it.startsWith("content://")) "video_${segment.id}.mp4" else it
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.VideoLibrary,
            contentDescription = null,
            tint = if (exists) Primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                displayName,
                style = DashCamTypography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
            Text(
                "${Formatters.duration(segment.durationMs / 1000)} · ${FileUtils.formatBytes(segment.sizeBytes)}" +
                    if (!exists) " · (archivo no encontrado)" else "",
                style = DashCamTypography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (exists) {
            IconButton(onClick = onPlay) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Reproducir",
                    tint = Primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Borrar video",
                tint = HudRed,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun StatBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    warning: Boolean = false
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            icon,
            contentDescription = null,
            tint = when {
                warning -> HudRed
                label.startsWith("Vel") && value.toFloatOrNull()?.let { it > 120 } ?: false -> HudAmber
                else -> Primary
            },
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            style = DashCamTypography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            label,
            style = DashCamTypography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
