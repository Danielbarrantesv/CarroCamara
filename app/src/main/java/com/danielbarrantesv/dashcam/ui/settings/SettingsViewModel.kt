package com.danielbarrantesv.dashcam.ui.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danielbarrantesv.dashcam.MainActivity
import com.danielbarrantesv.dashcam.data.prefs.AppLanguage
import com.danielbarrantesv.dashcam.data.prefs.SettingsRepository
import com.danielbarrantesv.dashcam.data.prefs.SpeedUnit
import com.danielbarrantesv.dashcam.data.prefs.VideoQuality
import com.danielbarrantesv.dashcam.util.FileUtils
import com.danielbarrantesv.dashcam.util.LocaleHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val audioEnabled: Boolean = false,
    val videoQuality: VideoQuality = VideoQuality.FHD,
    val segmentDurationSec: Int = 60,
    val storageLimitGb: Int = 5,
    val gforceThreshold: Float = 0.8f,
    val speedUnit: SpeedUnit = SpeedUnit.KMH,
    val autoStartOnPower: Boolean = false,
    val keepScreenOn: Boolean = true,
    val storageUri: String? = null,
    val storageDescription: String = "",
    val dualCameraEnabled: Boolean = false,
    val defaultLens: Int = 0,
    val language: AppLanguage = AppLanguage.ES
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val repo: SettingsRepository
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = combine(
        combine(repo.audioEnabled, repo.videoQuality, repo.segmentDurationSec) { a, b, c -> Triple(a, b, c) },
        combine(repo.storageLimitGb, repo.gforceThreshold, repo.speedUnit) { a, b, c -> Triple(a, b, c) },
        combine(repo.autoStartOnPower, repo.keepScreenOn, repo.dualCameraEnabled, repo.defaultLens) { a, b, c, d -> listOf(a, b, c, d) },
        combine(repo.storageUri, repo.language) { uri, lang -> uri to lang }
    ) { rec, sens, other, storageAndLang ->
        val (storageUri, lang) = storageAndLang
        SettingsUiState(
            audioEnabled = rec.first,
            videoQuality = rec.second,
            segmentDurationSec = rec.third,
            storageLimitGb = sens.first,
            gforceThreshold = sens.second,
            speedUnit = sens.third,
            autoStartOnPower = other[0] as Boolean,
            keepScreenOn = other[1] as Boolean,
            dualCameraEnabled = other[2] as Boolean,
            defaultLens = other[3] as Int,
            storageUri = storageUri,
            storageDescription = storageUri?.let { describeUri(it) }
                ?: FileUtils.defaultStorageDescription(ctx),
            language = lang
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    private fun describeUri(uriStr: String): String {
        return try {
            val uri = Uri.parse(uriStr)
            val tree = androidx.documentfile.provider.DocumentFile.fromTreeUri(ctx, uri)
            "Carpeta personalizada\n${tree?.name ?: uri.lastPathSegment ?: uri.toString()}"
        } catch (e: Exception) {
            FileUtils.defaultStorageDescription(ctx)
        }
    }

    fun setAudio(v: Boolean) = viewModelScope.launch { repo.setAudioEnabled(v) }
    fun setVideoQuality(q: VideoQuality) = viewModelScope.launch { repo.setVideoQuality(q) }
    fun setSegmentDuration(s: Int) = viewModelScope.launch { repo.setSegmentDurationSec(s) }
    fun setStorageGb(gb: Int) = viewModelScope.launch { repo.setStorageLimitGb(gb) }
    fun setGforceThreshold(g: Float) = viewModelScope.launch { repo.setGforceThreshold(g) }
    fun setSpeedUnit(u: SpeedUnit) = viewModelScope.launch { repo.setSpeedUnit(u) }
    fun setAutoStart(v: Boolean) = viewModelScope.launch { repo.setAutoStartOnPower(v) }
    fun setKeepScreenOn(v: Boolean) = viewModelScope.launch { repo.setKeepScreenOn(v) }
    fun setDualCamera(v: Boolean) = viewModelScope.launch { repo.setDualCameraEnabled(v) }
    fun setDefaultLens(lens: Int) = viewModelScope.launch { repo.setDefaultLens(lens) }

    fun setStorageUri(uri: Uri?) = viewModelScope.launch {
        if (uri != null) {
            // Persist permission so we can access it across sessions
            try {
                val flags = (Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                ctx.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (_: Exception) {}
        }
        repo.setStorageUri(uri?.toString())
    }

    fun resetStorageUri() = viewModelScope.launch {
        repo.setStorageUri(null)
    }

    fun setLanguage(lang: AppLanguage) = viewModelScope.launch {
        repo.setLanguage(lang)
        // Apply the locale change via LocaleHelper (which uses AppCompatDelegate
        // + LocaleManager + Locale.setDefault under the hood).
        LocaleHelper.applyLanguage(ctx, lang.tag)
        // Recreate the activity so the locale wrapper in attachBaseContext
        // re-applies with the new language.
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            currentActivity?.recreate()
        }
    }

    /** Set by SettingsScreen via DisposableEffect so we can recreate the right activity. */
    @Volatile var currentActivity: Activity? = null
}
