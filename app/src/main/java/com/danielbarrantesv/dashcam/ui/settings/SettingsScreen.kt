package com.danielbarrantesv.dashcam.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danielbarrantesv.dashcam.R
import com.danielbarrantesv.dashcam.data.prefs.AppLanguage
import com.danielbarrantesv.dashcam.data.prefs.SpeedUnit
import com.danielbarrantesv.dashcam.data.prefs.VideoQuality
import com.danielbarrantesv.dashcam.ui.theme.DashCamTypography
import com.danielbarrantesv.dashcam.ui.theme.Primary

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    // Track current activity so the ViewModel can recreate it after a language change
    DisposableEffect(Unit) {
        viewModel.currentActivity = ctx as? android.app.Activity
            ?: (ctx as? android.content.ContextWrapper)?.baseContext as? android.app.Activity
        onDispose {
            if (viewModel.currentActivity === ctx) viewModel.currentActivity = null
        }
    }

    // SAF picker launcher
    val storagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            // Persist permission so we can access it across sessions
            try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                viewModel.setStorageUri(uri)
            } catch (_: Exception) {}
        }
    }

    // Single permission launcher (re-used for each permission row)
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> /* UI recomposes via state flow */ }

    // Force recomposition when permissions change (after returning from system settings)
    var permRefreshTrigger by remember { mutableStateOf(0) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                permRefreshTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // Read permRefreshTrigger so recomposition happens on resume
    @Suppress("UNUSED_EXPRESSION") permRefreshTrigger

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionHeader(stringResource(R.string.settings_section_recording))
            SettingsCard {
                ToggleRow(
                    title = stringResource(R.string.settings_audio_enabled),
                    subtitle = "Micrófono durante la grabación",
                    checked = state.audioEnabled,
                    onCheckedChange = viewModel::setAudio
                )
                DividerLine()
                QualityRow(
                    title = stringResource(R.string.settings_video_quality),
                    subtitle = state.videoQuality.label,
                    options = VideoQuality.entries.map { it.label to it },
                    selected = state.videoQuality,
                    onSelect = viewModel::setVideoQuality
                )
                DividerLine()
                SliderRow(
                    title = stringResource(R.string.settings_segment_duration),
                    valueText = "${state.segmentDurationSec}s",
                    value = state.segmentDurationSec.toFloat(),
                    range = 15f..300f,
                    steps = 56,
                    onValueChange = { viewModel.setSegmentDuration(it.toInt()) }
                )
            }
        }

        item {
            SectionHeader("Cámara")
            SettingsCard {
                QualityRow(
                    title = "Cámara por defecto",
                    subtitle = if (state.defaultLens == 0) "Trasera" else "Frontal",
                    options = listOf("Trasera" to 0, "Frontal" to 1),
                    selected = state.defaultLens,
                    onSelect = viewModel::setDefaultLens
                )
                DividerLine()
                ToggleRow(
                    title = "Doble cámara",
                    subtitle = "Grabar con cámara frontal y trasera a la vez (próximamente)",
                    checked = state.dualCameraEnabled,
                    onCheckedChange = viewModel::setDualCamera
                )
            }
        }

        item {
            SectionHeader(stringResource(R.string.settings_section_storage))
            SettingsCard {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Folder,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text("Carpeta de videos", style = DashCamTypography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        state.storageDescription,
                        style = DashCamTypography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { storagePicker.launch(null) },
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) { Text("Cambiar ubicación", style = DashCamTypography.labelMedium) }
                        if (state.storageUri != null) {
                            OutlinedButton(
                                onClick = { viewModel.resetStorageUri() },
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) { Text("Restablecer", style = DashCamTypography.labelMedium) }
                        }
                    }
                }
                DividerLine()
                SliderRow(
                    title = stringResource(R.string.settings_storage_limit),
                    valueText = "${state.storageLimitGb} GB",
                    value = state.storageLimitGb.toFloat(),
                    range = 1f..30f,
                    steps = 28,
                    onValueChange = { viewModel.setStorageGb(it.toInt()) }
                )
            }
        }

        item {
            SectionHeader(stringResource(R.string.settings_section_sensors))
            SettingsCard {
                SliderRow(
                    title = stringResource(R.string.settings_gforce_threshold),
                    valueText = "${"%.2f".format(state.gforceThreshold)} g",
                    value = state.gforceThreshold,
                    range = 0.2f..3.0f,
                    steps = 27,
                    onValueChange = viewModel::setGforceThreshold
                )
                DividerLine()
                QualityRow(
                    title = stringResource(R.string.settings_speed_unit),
                    subtitle = state.speedUnit.label,
                    options = SpeedUnit.entries.map { it.label to it },
                    selected = state.speedUnit,
                    onSelect = viewModel::setSpeedUnit
                )
            }
        }

        item {
            SectionHeader("Otros")
            SettingsCard {
                ToggleRow(
                    title = stringResource(R.string.settings_auto_start),
                    subtitle = "Arranca la grabación automáticamente",
                    checked = state.autoStartOnPower,
                    onCheckedChange = viewModel::setAutoStart
                )
                DividerLine()
                ToggleRow(
                    title = stringResource(R.string.settings_keep_screen_on),
                    subtitle = "Evita que se apague mientras grabas",
                    checked = state.keepScreenOn,
                    onCheckedChange = viewModel::setKeepScreenOn
                )
            }
        }

        item {
            SectionHeader("Permisos")
            SettingsCard {
                PermissionRow(
                    title = "Cámara",
                    subtitle = "Necesario para grabar video",
                    granted = com.danielbarrantesv.dashcam.util.Permissions.isCameraGranted(ctx),
                    onRequest = { permLauncher.launch(com.danielbarrantesv.dashcam.util.Permissions.camera) },
                    onOpenSettings = { openAppSettings(ctx) }
                )
                DividerLine()
                PermissionRow(
                    title = "Micrófono",
                    subtitle = "Audio en los videos (opcional)",
                    granted = com.danielbarrantesv.dashcam.util.Permissions.isAudioGranted(ctx),
                    onRequest = { permLauncher.launch(com.danielbarrantesv.dashcam.util.Permissions.recordAudio) },
                    onOpenSettings = { openAppSettings(ctx) }
                )
                DividerLine()
                PermissionRow(
                    title = "Ubicación",
                    subtitle = "GPS, velocidad y ruta",
                    granted = com.danielbarrantesv.dashcam.util.Permissions.isLocationGranted(ctx),
                    onRequest = {
                        permLauncher.launch(com.danielbarrantesv.dashcam.util.Permissions.fineLocation)
                    },
                    onOpenSettings = { openAppSettings(ctx) }
                )
                DividerLine()
                PermissionRow(
                    title = "Ubicación en segundo plano",
                    subtitle = "GPS mientras la app está minimizada",
                    granted = com.danielbarrantesv.dashcam.util.Permissions.isBackgroundLocationGranted(ctx),
                    onRequest = {
                        if (com.danielbarrantesv.dashcam.util.Permissions.isLocationGranted(ctx)) {
                            permLauncher.launch(com.danielbarrantesv.dashcam.util.Permissions.backgroundLocation)
                        }
                    },
                    onOpenSettings = { openAppSettings(ctx) },
                    enabled = com.danielbarrantesv.dashcam.util.Permissions.isLocationGranted(ctx)
                )
                DividerLine()
                PermissionRow(
                    title = "Notificaciones",
                    subtitle = "Alertas de grabación e impactos",
                    granted = com.danielbarrantesv.dashcam.util.Permissions.isNotificationsGranted(ctx),
                    onRequest = {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            permLauncher.launch(com.danielbarrantesv.dashcam.util.Permissions.postNotifications)
                        }
                    },
                    onOpenSettings = { openAppSettings(ctx) }
                )
            }
        }

        item {
            SectionHeader("Idioma")
            SettingsCard {
                LanguageRow(
                    selected = state.language,
                    onSelect = viewModel::setLanguage
                )
            }
        }

        item {
            SectionHeader("Acerca de")
            SettingsCard {
                InfoRow("Versión", "1.0.3")
                DividerLine()
                InfoRow("Autor", "Daniel Barrantes Villarevia")
                DividerLine()
                InfoRow("Repositorio", "github.com/Danielbarrantesv/CarroCamara")
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = DashCamTypography.titleSmall,
        color = Primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(8.dp)) { content() }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = DashCamTypography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = DashCamTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun <T> QualityRow(
    title: String,
    subtitle: String,
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    Column(Modifier.padding(12.dp)) {
        Text(title, style = DashCamTypography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Text(subtitle, style = DashCamTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (label, value) ->
                val isSelected = value == selected
                Button(
                    onClick = { onSelect(value) },
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) Primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) { Text(label, fontWeight = FontWeight.SemiBold, style = DashCamTypography.labelMedium) }
            }
        }
    }
}

@Composable
private fun SliderRow(
    title: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit
) {
    Column(Modifier.padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = DashCamTypography.titleSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Text(valueText, style = DashCamTypography.titleSmall, color = Primary, fontWeight = FontWeight.Bold)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range, steps = steps)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = DashCamTypography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = DashCamTypography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DividerLine() {
    androidx.compose.material3.HorizontalDivider(
        modifier = Modifier.padding(horizontal = 12.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
private fun LanguageRow(
    selected: AppLanguage,
    onSelect: (AppLanguage) -> Unit
) {
    Column(Modifier.padding(12.dp)) {
        Text("Idioma de la app", style = DashCamTypography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Text(
            "Cambia el idioma. Se aplica inmediatamente.",
            style = DashCamTypography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        // Grid of language buttons
        AppLanguage.entries.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { lang ->
                    val isSelected = lang == selected
                    Button(
                        onClick = { onSelect(lang) },
                        modifier = Modifier.weight(1f).height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) Primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(
                            lang.display,
                            fontWeight = FontWeight.SemiBold,
                            style = DashCamTypography.labelMedium
                        )
                    }
                }
                // Fill remaining slots if row is not full
                repeat(3 - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    enabled: Boolean = true
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = DashCamTypography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = DashCamTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        if (granted) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Concedido",
                tint = androidx.compose.ui.graphics.Color(0xFF34C759),
                modifier = Modifier.size(24.dp)
            )
        } else {
            OutlinedButton(
                onClick = if (enabled) onRequest else onOpenSettings,
                enabled = true,
                modifier = Modifier.height(36.dp)
            ) {
                Text(
                    if (enabled) "Conceder" else "Ajustes",
                    style = DashCamTypography.labelMedium
                )
            }
        }
    }
}

/** Opens the system app details page so the user can grant permissions manually. */
private fun openAppSettings(ctx: android.content.Context) {
    val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = android.net.Uri.fromParts("package", ctx.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    ctx.startActivity(intent)
}
