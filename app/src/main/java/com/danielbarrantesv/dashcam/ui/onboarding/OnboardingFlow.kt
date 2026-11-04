package com.danielbarrantesv.dashcam.ui.onboarding

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danielbarrantesv.dashcam.R
import com.danielbarrantesv.dashcam.data.prefs.SettingsRepository
import com.danielbarrantesv.dashcam.data.prefs.SpeedUnit
import com.danielbarrantesv.dashcam.data.prefs.VideoQuality
import com.danielbarrantesv.dashcam.ui.theme.DashCamTypography
import com.danielbarrantesv.dashcam.ui.theme.HudAmber
import com.danielbarrantesv.dashcam.ui.theme.HudGreen
import com.danielbarrantesv.dashcam.ui.theme.Primary
import com.danielbarrantesv.dashcam.ui.theme.Success
import com.danielbarrantesv.dashcam.util.Permissions
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Three-step onboarding:
 *   1. Welcome
 *   2. Storage configuration (configurable default 5 GB)
 *   3. Permissions (camera, mic, location, background location, notifications)
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun OnboardingFlow(onFinish: () -> Unit) {
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(0) }

    // Selected language (default: Spanish, but load persisted value after recreate)
    var selectedLanguage by remember { mutableStateOf(com.danielbarrantesv.dashcam.data.prefs.AppLanguage.ES) }

    // After a recreate (triggered by language change), load the persisted language
    // so the highlight shows on the correct button.
    LaunchedEffect(Unit) {
        try {
            val prefs = SettingsRepository(context)
            selectedLanguage = prefs.language.first()
        } catch (_: Exception) {}
    }

    // Permissions state
    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(
            Permissions.camera,
            Permissions.recordAudio,
            Permissions.fineLocation,
            Permissions.coarseLocation,
            Permissions.postNotifications
        )
    )

    // Storage config
    var storageGb by remember { mutableFloatStateOf(5f) }
    var quality by remember { mutableStateOf(VideoQuality.FHD) }
    var unit by remember { mutableStateOf(SpeedUnit.KMH) }

    // Background location (must be requested after foreground location)
    val bgLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> /* regardless, continue */ }

    var customStorageUri by remember { mutableStateOf<Uri?>(null) }

    // SAF folder picker for storage location
    val storagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            customStorageUri = uri
            try {
                val flags = (Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (_: Exception) {}
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (step) {
            0 -> LanguageStep(
                selected = selectedLanguage,
                onSelect = { lang ->
                    selectedLanguage = lang
                    // Persist immediately so it survives recreate
                    val prefs = SettingsRepository(context)
                    MainScope().launch { prefs.setLanguage(lang) }
                    // Apply locale via AppCompatDelegate
                    androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                        androidx.core.os.LocaleListCompat.forLanguageTags(lang.tag)
                    )
                    // Force activity recreate so the LanguageStep itself re-renders
                    // in the new locale (the selected button + description).
                    (context as? android.app.Activity)?.recreate()
                },
                onContinue = { step = 1 }
            )
            1 -> WelcomeStep(onContinue = { step = 2 })
            2 -> StorageStep(
                storageGb = storageGb,
                onStorageChange = { storageGb = it },
                quality = quality,
                onQualityChange = { quality = it },
                unit = unit,
                onUnitChange = { unit = it },
                onContinue = { step = 3 }
            )
            3 -> StorageFolderStep(
                customUri = customStorageUri,
                onPickFolder = { storagePicker.launch(null) },
                onClearFolder = { customStorageUri = null },
                onContinue = { step = 4 }
            )
            4 -> PermissionsStep(
                permissionsState = permissionsState,
                onBackgroundLocationRequest = {
                    bgLocationLauncher.launch(Permissions.backgroundLocation)
                },
                onContinue = {
                    // Persist settings before finishing
                    val prefs = SettingsRepository(context)
                    val storageFinalGb = storageGb.toInt()
                    val qualityFinal = quality
                    val unitFinal = unit
                    val finalUri = customStorageUri
                    val finalLang = selectedLanguage
                    MainScope().launch {
                        prefs.setLanguage(finalLang)
                        prefs.setStorageLimitGb(storageFinalGb)
                        prefs.setVideoQuality(qualityFinal)
                        prefs.setSpeedUnit(unitFinal)
                        prefs.setStorageUri(finalUri?.toString())
                        prefs.setOnboardingComplete()
                        onFinish()
                    }
                }
            )
        }
    }
}

@Composable
private fun LanguageStep(
    selected: com.danielbarrantesv.dashcam.data.prefs.AppLanguage,
    onSelect: (com.danielbarrantesv.dashcam.data.prefs.AppLanguage) -> Unit,
    onContinue: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().navigationBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                stringResource(R.string.onboarding_language_title),
                style = DashCamTypography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.onboarding_language_subtitle),
                style = DashCamTypography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(32.dp))

            com.danielbarrantesv.dashcam.data.prefs.AppLanguage.entries.forEach { lang ->
                val isSelected = lang == selected
                Button(
                    onClick = { onSelect(lang) },
                    modifier = Modifier.fillMaxWidth().height(56.dp).padding(vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) Primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text(
                        lang.display,
                        fontWeight = FontWeight.SemiBold,
                        style = DashCamTypography.titleMedium
                    )
                }
            }
        }

        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary)
        ) {
            Text(stringResource(R.string.onboarding_language_continue), color = Color.Black, fontWeight = FontWeight.SemiBold, style = DashCamTypography.labelMedium)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = Color.Black)
        }
    }
}

@Composable
private fun WelcomeStep(onContinue: () -> Unit) {
    Column(
        Modifier.fillMaxSize().navigationBarsPadding().padding(32.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(64.dp))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(120.dp).clip(CircleShape).background(Primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.PhotoCamera,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(64.dp)
                )
            }
            Spacer(Modifier.height(32.dp))
            Text(
                text = stringResource(R.string.onboarding_welcome_app_name),
                style = DashCamTypography.displayMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.onboarding_welcome_tagline),
                style = DashCamTypography.titleMedium,
                color = Primary
            )
            Spacer(Modifier.height(32.dp))
            Text(
                text = stringResource(R.string.onboarding_welcome_body),
                style = DashCamTypography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary)
        ) {
            Text(stringResource(R.string.onboarding_welcome_button), color = Color.Black, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = Color.Black)
        }
    }
}

@Composable
private fun StorageStep(
    storageGb: Float,
    onStorageChange: (Float) -> Unit,
    quality: VideoQuality,
    onQualityChange: (VideoQuality) -> Unit,
    unit: SpeedUnit,
    onUnitChange: (SpeedUnit) -> Unit,
    onContinue: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().navigationBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(stringResource(R.string.onboarding_storage_title), style = DashCamTypography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.onboarding_storage_body),
                style = DashCamTypography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(32.dp))

            // Storage slider
            Text("${storageGb.toInt()} ${stringResource(R.string.onboarding_storage_unit)}", style = DashCamTypography.displayMedium, color = Primary)
            Text(
                "≈ ${"%.0f".format(storageGb * 12)} ${stringResource(R.string.onboarding_storage_minutes_hint)}",
                style = DashCamTypography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = storageGb,
                onValueChange = onStorageChange,
                valueRange = 1f..30f,
                steps = 28
            )

            Spacer(Modifier.height(32.dp))

            // Video quality
            Text(stringResource(R.string.onboarding_storage_quality_title), style = DashCamTypography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VideoQuality.entries.forEach { q ->
                    QualityChip(
                        label = q.label,
                        selected = quality == q,
                        onClick = { onQualityChange(q) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // Speed unit
            Text(stringResource(R.string.onboarding_storage_unit_title), style = DashCamTypography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SpeedUnit.entries.forEach { u ->
                    QualityChip(
                        label = u.label,
                        selected = unit == u,
                        onClick = { onUnitChange(u) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary)
        ) {
            Text(stringResource(R.string.onboarding_continue), color = Color.Black, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = Color.Black)
        }
    }
}

@Composable
private fun StorageFolderStep(
    customUri: Uri?,
    onPickFolder: () -> Unit,
    onClearFolder: () -> Unit,
    onContinue: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().navigationBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                stringResource(R.string.onboarding_folder_title),
                style = DashCamTypography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.onboarding_folder_body),
                style = DashCamTypography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(32.dp))

            // Current selection card
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (customUri != null)
                        MaterialTheme.colorScheme.surfaceVariant
                    else MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Folder,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (customUri != null) stringResource(R.string.onboarding_folder_custom) else stringResource(R.string.onboarding_folder_default),
                                style = DashCamTypography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                if (customUri != null)
                                    customUri.toString().let {
                                        if (it.length > 60) "…" + it.takeLast(60) else it
                                    }
                                else stringResource(R.string.onboarding_folder_default_desc),
                                style = DashCamTypography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (customUri != null) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = Success,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onPickFolder,
                    modifier = Modifier.weight(1f).height(48.dp)
                ) { Text(stringResource(R.string.onboarding_folder_pick), style = DashCamTypography.labelMedium) }
                if (customUri != null) {
                    OutlinedButton(
                        onClick = onClearFolder,
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) { Text(stringResource(R.string.onboarding_folder_use_default), style = DashCamTypography.labelMedium) }
                }
            }
        }

        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary)
        ) {
            Text(stringResource(R.string.onboarding_continue), color = Color.Black, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = Color.Black)
        }
    }
}

@Composable
private fun QualityChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) Primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (selected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) { Text(label, fontWeight = FontWeight.SemiBold) }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun PermissionsStep(
    permissionsState: com.google.accompanist.permissions.MultiplePermissionsState,
    onBackgroundLocationRequest: () -> Unit,
    onContinue: () -> Unit
) {
    val context = LocalContext.current

    Column(
        Modifier.fillMaxSize().navigationBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(stringResource(R.string.onboarding_permissions_title), style = DashCamTypography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.onboarding_permissions_body),
                style = DashCamTypography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))

            val items = buildList {
                add(PermItem(Icons.Filled.PhotoCamera, stringResource(R.string.onboarding_perm_camera_title), stringResource(R.string.onboarding_perm_camera_desc), Permissions.isCameraGranted(context)))
                add(PermItem(Icons.Filled.Mic, stringResource(R.string.onboarding_perm_mic_title), stringResource(R.string.onboarding_perm_mic_desc), Permissions.isAudioGranted(context)))
                add(PermItem(Icons.Filled.LocationOn, stringResource(R.string.onboarding_perm_location_title), stringResource(R.string.onboarding_perm_location_desc), Permissions.isLocationGranted(context)))
                add(PermItem(Icons.Filled.LocationOn, stringResource(R.string.onboarding_perm_bg_location_title), stringResource(R.string.onboarding_perm_bg_location_desc), Permissions.isBackgroundLocationGranted(context)))
                add(PermItem(Icons.Filled.Notifications, stringResource(R.string.onboarding_perm_notifications_title), stringResource(R.string.onboarding_perm_notifications_desc), Permissions.isNotificationsGranted(context)))
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(items) { item ->
                    PermissionCard(item)
                }
            }
        }

        Column {
            Button(
                onClick = { permissionsState.launchMultiplePermissionRequest() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Text(stringResource(R.string.onboarding_perm_grant_button), color = Color.Black, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(12.dp))

            Text(
                stringResource(R.string.onboarding_perm_bg_location_hint),
                style = DashCamTypography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onBackgroundLocationRequest,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) { Text(stringResource(R.string.onboarding_perm_bg_location_button)) }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Text(stringResource(R.string.onboarding_start), color = Color.Black, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private data class PermItem(val icon: androidx.compose.ui.graphics.vector.ImageVector, val title: String, val desc: String, val granted: Boolean)

@Composable
private fun PermissionCard(item: PermItem) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(item.icon, contentDescription = null, tint = Primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, style = DashCamTypography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(item.desc, style = DashCamTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            if (item.granted) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = Success, modifier = Modifier.size(24.dp))
            } else {
                Box(
                    Modifier.size(12.dp).clip(CircleShape).background(HudAmber)
                )
            }
        }
    }
}
