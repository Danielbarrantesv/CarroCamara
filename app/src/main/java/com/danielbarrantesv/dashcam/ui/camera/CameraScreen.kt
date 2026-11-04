package com.danielbarrantesv.dashcam.ui.camera

import android.content.Intent
import android.content.res.Configuration
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danielbarrantesv.dashcam.service.RecordingService
import com.danielbarrantesv.dashcam.ui.theme.DashCamTypography
import com.danielbarrantesv.dashcam.ui.theme.HudAmber
import com.danielbarrantesv.dashcam.ui.theme.HudDim
import com.danielbarrantesv.dashcam.ui.theme.HudGreen
import com.danielbarrantesv.dashcam.ui.theme.HudRed
import com.danielbarrantesv.dashcam.ui.theme.HudWhite
import com.danielbarrantesv.dashcam.ui.theme.Primary
import com.danielbarrantesv.dashcam.util.Formatters
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding

@Composable
fun CameraScreen(
    viewModel: CameraViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        // Camera preview placeholder (real preview surface would be AndroidView with PreviewView)
        CameraPreviewPlaceholder()

        // HUD overlay
        HudOverlay(
            state = state,
            isLandscape = isLandscape,
            modifier = Modifier.fillMaxSize()
        )

        // Bottom controls
        BottomControls(
            isRecording = state.isRecording,
            onToggleRecording = {
                if (state.isRecording) {
                    ctx.startService(Intent(ctx, RecordingService::class.java).apply {
                        action = RecordingService.ACTION_STOP
                    })
                } else {
                    RecordingService.startWith(ctx, lensFacing = state.lensFacing)
                }
            },
            onSwitchCamera = viewModel::toggleLens,
            onRotate = viewModel::toggleRotationLock,
            rotationLocked = state.rotationLocked,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        )

        // Top REC indicator
        if (state.isRecording) {
            RecIndicator(
                durationSec = state.durationSec,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            )
        }
    }
}

@Composable
private fun CameraPreviewPlaceholder() {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A1A1F),
                        Color(0xFF0B0B0F)
                    )
                )
            )
    ) {
        // Centered camera icon — would be replaced by real PreviewView in production
        Icon(
            Icons.Filled.Videocam,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.1f),
            modifier = Modifier
                .align(Alignment.Center)
                .size(120.dp)
        )
    }
}

@Composable
private fun HudOverlay(
    state: CameraUiState,
    isLandscape: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier) {
        if (isLandscape) {
            // Landscape: HUD on right side as vertical stack
            LandscapeHud(state, Modifier.align(Alignment.CenterEnd).padding(16.dp))
        } else {
            // Portrait: HUD on top (speed) and bottom (distance + GPS + G-force)
            PortraitHud(state, Modifier.align(Alignment.TopEnd).padding(16.dp))
        }
    }
}

@Composable
private fun PortraitHud(state: CameraUiState, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.End) {
        // Speed
        HudSpeedCard(speedKmh = state.speedKmh, unit = state.speedUnit)
        Spacer(Modifier.height(12.dp))
        // Distance
        HudSmallCard(label = "DIST", value = Formatters.distance(state.distanceMeters))
        Spacer(Modifier.height(8.dp))
        // G-force
        HudGForceCard(gForce = state.gForce)
        Spacer(Modifier.height(8.dp))
        // GPS
        HudGpsCard(lat = state.lat, lon = state.lon)
    }
}

@Composable
private fun LandscapeHud(state: CameraUiState, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.End) {
        HudSpeedCard(speedKmh = state.speedKmh, unit = state.speedUnit, compact = true)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HudSmallCard(label = "DIST", value = Formatters.distance(state.distanceMeters))
            HudGForceCard(gForce = state.gForce, compact = true)
        }
        Spacer(Modifier.height(8.dp))
        HudGpsCard(lat = state.lat, lon = state.lon, compact = true)
    }
}

@Composable
private fun HudSpeedCard(speedKmh: Float, unit: String, compact: Boolean = false) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = Formatters.speed(speedKmh / 3.6f, factor = if (unit == "km/h") 3.6f else 2.23694f),
            color = if (speedKmh > 120) HudRed else HudGreen,
            fontSize = if (compact) 36.sp else 48.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
        Text(unit, color = HudDim, fontSize = 11.sp, letterSpacing = 2.sp)
    }
}

@Composable
private fun HudSmallCard(label: String, value: String) {
    Column(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = HudDim, fontSize = 10.sp, letterSpacing = 1.5.sp)
        Text(value, color = HudWhite, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun HudGForceCard(gForce: Float, compact: Boolean = false) {
    val color = when {
        gForce > 1.0f -> HudRed
        gForce > 0.6f -> HudAmber
        else -> HudGreen
    }
    Column(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("G", color = HudDim, fontSize = 10.sp, letterSpacing = 1.5.sp)
        Text(
            "%.2f".format(gForce),
            color = color,
            fontSize = if (compact) 14.sp else 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
    }
}

@Composable
private fun HudGpsCard(lat: Double?, lon: Double?, compact: Boolean = false) {
    val text = if (lat != null && lon != null) Formatters.coordinates(lat, lon) else "—"
    Column(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("GPS", color = if (lat != null) HudGreen else HudDim, fontSize = 10.sp, letterSpacing = 1.5.sp)
        Text(
            text,
            color = HudWhite,
            fontSize = if (compact) 11.sp else 13.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
    }
}

@Composable
private fun RecIndicator(durationSec: Long, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(HudRed))
        Spacer(Modifier.width(8.dp))
        Text(
            "REC · ${Formatters.duration(durationSec)}",
            color = HudWhite,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun BottomControls(
    isRecording: Boolean,
    onToggleRecording: () -> Unit,
    onSwitchCamera: () -> Unit,
    onRotate: () -> Unit,
    rotationLocked: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Switch camera
        ControlButton(
            icon = Icons.Filled.Cameraswitch,
            contentDesc = "Cambiar cámara",
            onClick = onSwitchCamera,
            tint = HudWhite
        )
        Spacer(Modifier.width(24.dp))

        // Main record/stop button
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(if (isRecording) HudRed else Primary)
                .padding(0.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isRecording) Icons.Filled.Stop else Icons.Filled.Videocam,
                contentDescription = if (isRecording) "Detener grabación" else "Iniciar grabación",
                tint = Color.Black,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .padding(0.dp)
            )
            // clickable overlay
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .clickable(onClick = onToggleRecording)
            )
        }
        Spacer(Modifier.width(24.dp))

        // Rotate
        ControlButton(
            icon = Icons.Filled.ScreenRotation,
            contentDesc = "Rotar",
            onClick = onRotate,
            tint = if (rotationLocked) HudAmber else HudWhite
        )
    }
}

@Composable
private fun ControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDesc: String,
    onClick: () -> Unit,
    tint: Color
) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDesc, tint = tint, modifier = Modifier.size(24.dp))
    }
}
