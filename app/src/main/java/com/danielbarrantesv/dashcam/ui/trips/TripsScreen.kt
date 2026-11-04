package com.danielbarrantesv.dashcam.ui.trips

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
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danielbarrantesv.dashcam.data.model.TripEntity
import com.danielbarrantesv.dashcam.ui.theme.DashCamTypography
import com.danielbarrantesv.dashcam.ui.theme.HudAmber
import com.danielbarrantesv.dashcam.ui.theme.HudGreen
import com.danielbarrantesv.dashcam.ui.theme.HudRed
import com.danielbarrantesv.dashcam.ui.theme.Primary
import com.danielbarrantesv.dashcam.util.FileUtils
import com.danielbarrantesv.dashcam.util.Formatters

@Composable
fun TripsScreen(
    viewModel: TripsViewModel = hiltViewModel()
) {
    val trips by viewModel.trips.collectAsStateWithLifecycle(emptyList())

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Text(
            "Viajes grabados",
            style = DashCamTypography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(24.dp, 24.dp, 24.dp, 8.dp)
        )

        if (trips.isEmpty()) {
            EmptyTrips()
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(trips, key = { it.id }) { trip ->
                    TripCard(trip)
                }
            }
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
private fun TripCard(trip: TripEntity) {
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
