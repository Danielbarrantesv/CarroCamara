package com.danielbarrantesv.dashcam.ui.camera

import android.app.Activity
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.view.PreviewView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.danielbarrantesv.dashcam.ui.theme.HudAmber
import com.danielbarrantesv.dashcam.ui.theme.HudDim
import com.danielbarrantesv.dashcam.ui.theme.HudGreen
import com.danielbarrantesv.dashcam.ui.theme.HudRed
import com.danielbarrantesv.dashcam.ui.theme.HudWhite
import com.danielbarrantesv.dashcam.ui.theme.Primary
import com.danielbarrantesv.dashcam.util.Formatters

/** Walk up the Context chain to find the Activity. */
private tailrec fun android.content.Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun CameraScreen() {
    // Scope the ViewModel to the Activity (not the NavBackStackEntry) so that
    // switching tabs doesn't destroy the recording. The camera use cases stay
    // bound to the Activity lifecycle.
    val ctx = LocalContext.current
    val activity = ctx as? Activity ?: ctx.findActivity()
    val viewModel: CameraViewModel = if (activity is androidx.activity.ComponentActivity) {
        viewModel(viewModelStoreOwner = activity)
    } else {
        hiltViewModel()
    }

    val state by viewModel.state.collectAsStateWithLifecycle()
    // CRITICAL: bind camera to the ACTIVITY lifecycle, not the NavBackStackEntry.
    // NavBackStackEntry's lifecycle goes to STOPPED when the user switches tabs,
    // which would tear down the camera and corrupt the recording. The Activity
    // stays RESUMED while the app is foregrounded, so the camera keeps running.
    val lifecycleOwner = if (activity is androidx.lifecycle.LifecycleOwner) activity
        else LocalLifecycleOwner.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Real PreviewView — created once, reused across recompositions
    val previewView = remember {
        PreviewView(ctx).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    // Rebind camera when lensFacing changes OR when the composable re-enters
    // composition after a tab switch. Using a stable key (Unit) means it fires
    // every time the user returns to this screen, ensuring the preview surface
    // is re-attached to the (still-running) Preview use case.
    LaunchedEffect(state.lensFacing, lifecycleOwner) {
        viewModel.bindCamera(lifecycleOwner, previewView)
    }

    // Re-attach the preview surface whenever the PreviewView is recreated
    // (e.g. on configuration change or returning from another tab).
    LaunchedEffect(previewView) {
        if (state.isRecording) {
            viewModel.attachPreview(previewView)
        }
    }

    // IMPORTANT: We do NOT call viewModel.cleanup() on dispose.
    // The ViewModel is activity-scoped, so it survives tab switches.
    // The camera and recording continue while the user is in other tabs.
    // Cleanup happens only in ViewModel.onCleared() (when the activity dies).
    DisposableEffect(Unit) {
        onDispose {
            // Just release the preview surface — keep recording alive
            viewModel.releasePreview()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        // ============ Real camera preview ============
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // Dark gradient overlay on edges for HUD legibility
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.45f),
                            Color.Transparent,
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.55f)
                        )
                    )
                )
        )

        // ============ HUD overlay ============
        HudOverlay(
            state = state,
            isLandscape = isLandscape,
            modifier = Modifier.fillMaxSize()
        )

        // ============ Top REC indicator ============
        if (state.isRecording) {
            RecIndicator(
                durationSec = state.durationSec,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            )
        }

        // ============ Last event indicator (top center) ============
        if (state.lastEventLabel != null) {
            EventIndicator(
                label = state.lastEventLabel!!,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
            )
        }

        // ============ Error message ============
        if (state.error != null) {
            ErrorBanner(
                message = state.error!!,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 110.dp)
            )
        }

        // ============ Bottom controls ============
        BottomControls(
            isRecording = state.isRecording,
            isCameraBound = state.isCameraBound,
            onToggleRecording = viewModel::toggleRecording,
            onSwitchCamera = viewModel::toggleLens,
            onRotate = viewModel::toggleRotationLock,
            rotationLocked = state.rotationLocked,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 24.dp)
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
            LandscapeHud(state, Modifier.align(Alignment.CenterEnd).padding(16.dp))
        } else {
            PortraitHud(state, Modifier.align(Alignment.TopEnd).padding(top = 60.dp, end = 16.dp))
        }
    }
}

@Composable
private fun PortraitHud(state: CameraUiState, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.End) {
        HudSpeedCard(speedKmh = state.speedKmh, unit = state.speedUnit)
        Spacer(Modifier.height(12.dp))
        HudSmallCard(label = "DIST", value = Formatters.distance(state.distanceMeters))
        Spacer(Modifier.height(8.dp))
        HudGForceCard(gForce = state.gForce)
        Spacer(Modifier.height(8.dp))
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
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "%.0f".format(speedKmh),
            color = if (speedKmh > 120) HudRed else HudGreen,
            fontSize = if (compact) 36.sp else 48.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Text(unit, color = HudDim, fontSize = 11.sp, letterSpacing = 2.sp)
    }
}

@Composable
private fun HudSmallCard(label: String, value: String) {
    Column(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.6f))
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
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("G", color = HudDim, fontSize = 10.sp, letterSpacing = 1.5.sp)
        Text(
            "%.2f".format(gForce),
            color = color,
            fontSize = if (compact) 14.sp else 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun HudGpsCard(lat: Double?, lon: Double?, compact: Boolean = false) {
    val text = if (lat != null && lon != null) Formatters.coordinates(lat, lon) else "SIN SEÑAL"
    Column(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("GPS", color = if (lat != null) HudGreen else HudDim, fontSize = 10.sp, letterSpacing = 1.5.sp)
        Text(
            text,
            color = HudWhite,
            fontSize = if (compact) 11.sp else 13.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun RecIndicator(durationSec: Long, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Pulsing dot
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
private fun EventIndicator(label: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(HudRed.copy(alpha = 0.85f))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
private fun ErrorBanner(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(HudRed.copy(alpha = 0.9f))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(message, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun BottomControls(
    isRecording: Boolean,
    isCameraBound: Boolean,
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
        // Switch camera (disabled while recording)
        ControlButton(
            icon = Icons.Filled.Cameraswitch,
            contentDesc = "Cambiar cámara",
            onClick = onSwitchCamera,
            tint = if (isRecording) HudDim else HudWhite,
            enabled = !isRecording
        )
        Spacer(Modifier.width(24.dp))

        // Main record/stop button — clickable directly on the parent
        Box(
            Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(if (isRecording) HudRed else Primary)
                .clickable(enabled = isCameraBound) { onToggleRecording() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isRecording) Icons.Filled.Stop else Icons.Filled.Videocam,
                contentDescription = if (isRecording) "Detener grabación" else "Iniciar grabación",
                tint = Color.Black,
                modifier = Modifier.size(36.dp)
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
    tint: Color,
    enabled: Boolean = true
) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = contentDesc,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
    }
}
