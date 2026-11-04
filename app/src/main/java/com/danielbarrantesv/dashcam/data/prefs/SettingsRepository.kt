package com.danielbarrantesv.dashcam.data.prefs

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "dashcam_settings")

/** Video quality levels exposed to the user. */
enum class VideoQuality(val label: String, val width: Int, val height: Int, val bitrate: Int) {
    SD("480p", 854, 480, 2_000_000),
    HD("720p", 1280, 720, 5_000_000),
    FHD("1080p", 1920, 1080, 8_000_000),
    UHD("2160p", 3840, 2160, 25_000_000);

    companion object {
        fun fromName(name: String?): VideoQuality = entries.firstOrNull { it.name == name } ?: FHD
    }
}

enum class SpeedUnit(val label: String, val factor: Float) {
    KMH("km/h", 3.6f),
    MPH("mph", 2.23694f);

    companion object {
        fun fromName(name: String?): SpeedUnit = entries.firstOrNull { it.name == name } ?: KMH
    }
}

/** App language — affects locale applied via AppCompatDelegate.setApplicationLocales(). */
enum class AppLanguage(val tag: String, val display: String) {
    ES("es", "Español"),
    EN("en", "English"),
    FR("fr", "Français"),
    ZH("zh", "中文"),
    JA("ja", "日本語"),
    DE("de", "Deutsch");

    companion object {
        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { it.tag == tag } ?: ES
    }
}

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val ctx: Context
) {
    private object Keys {
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val STORAGE_LIMIT_GB = intPreferencesKey("storage_limit_gb")
        val SEGMENT_DURATION_S = intPreferencesKey("segment_duration_s")
        val VIDEO_QUALITY = stringPreferencesKey("video_quality")
        val AUDIO_ENABLED = booleanPreferencesKey("audio_enabled")
        val GFORCE_THRESHOLD = floatPreferencesKey("gforce_threshold")
        val SPEED_UNIT = stringPreferencesKey("speed_unit")
        val AUTO_START_ON_POWER = booleanPreferencesKey("auto_start_on_power")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val LAST_TRIP_ID = intPreferencesKey("last_trip_id")
        val STORAGE_URI = stringPreferencesKey("storage_uri")
        val DUAL_CAMERA = booleanPreferencesKey("dual_camera")
        val DEFAULT_LENS = intPreferencesKey("default_lens") // 0 = back, 1 = front
        val LANGUAGE = stringPreferencesKey("language")
    }

    val hasCompletedOnboarding: Flow<Boolean> =
        ctx.dataStore.data.map { it[Keys.ONBOARDING_DONE] ?: false }

    val storageLimitGb: Flow<Int> =
        ctx.dataStore.data.map { it[Keys.STORAGE_LIMIT_GB] ?: 5 }

    val segmentDurationSec: Flow<Int> =
        ctx.dataStore.data.map { it[Keys.SEGMENT_DURATION_S] ?: 60 }

    val videoQuality: Flow<VideoQuality> =
        ctx.dataStore.data.map { VideoQuality.fromName(it[Keys.VIDEO_QUALITY]) }

    val audioEnabled: Flow<Boolean> =
        ctx.dataStore.data.map { it[Keys.AUDIO_ENABLED] ?: false }

    val gforceThreshold: Flow<Float> =
        ctx.dataStore.data.map { it[Keys.GFORCE_THRESHOLD] ?: 0.8f }

    val speedUnit: Flow<SpeedUnit> =
        ctx.dataStore.data.map { SpeedUnit.fromName(it[Keys.SPEED_UNIT]) }

    val autoStartOnPower: Flow<Boolean> =
        ctx.dataStore.data.map { it[Keys.AUTO_START_ON_POWER] ?: false }

    val keepScreenOn: Flow<Boolean> =
        ctx.dataStore.data.map { it[Keys.KEEP_SCREEN_ON] ?: true }

    val lastTripId: Flow<Long?> =
        ctx.dataStore.data.map { it[Keys.LAST_TRIP_ID]?.toLong() }

    suspend fun setOnboardingComplete() =
        ctx.dataStore.edit { it[Keys.ONBOARDING_DONE] = true }

    suspend fun setStorageLimitGb(gb: Int) =
        ctx.dataStore.edit { it[Keys.STORAGE_LIMIT_GB] = gb.coerceIn(1, 50) }

    suspend fun setSegmentDurationSec(sec: Int) =
        ctx.dataStore.edit { it[Keys.SEGMENT_DURATION_S] = sec.coerceIn(15, 300) }

    suspend fun setVideoQuality(q: VideoQuality) =
        ctx.dataStore.edit { it[Keys.VIDEO_QUALITY] = q.name }

    suspend fun setAudioEnabled(v: Boolean) =
        ctx.dataStore.edit { it[Keys.AUDIO_ENABLED] = v }

    suspend fun setGforceThreshold(g: Float) =
        ctx.dataStore.edit { it[Keys.GFORCE_THRESHOLD] = g.coerceIn(0.2f, 3.0f) }

    suspend fun setSpeedUnit(u: SpeedUnit) =
        ctx.dataStore.edit { it[Keys.SPEED_UNIT] = u.name }

    suspend fun setAutoStartOnPower(v: Boolean) =
        ctx.dataStore.edit { it[Keys.AUTO_START_ON_POWER] = v }

    suspend fun setKeepScreenOn(v: Boolean) =
        ctx.dataStore.edit { it[Keys.KEEP_SCREEN_ON] = v }

    suspend fun setLastTripId(id: Long) =
        ctx.dataStore.edit { it[Keys.LAST_TRIP_ID] = id.toInt() }

    // ---- Custom storage location (SAF tree URI) ----
    val storageUri: Flow<String?> =
        ctx.dataStore.data.map { it[Keys.STORAGE_URI] }

    suspend fun setStorageUri(uri: String?) =
        ctx.dataStore.edit {
            if (uri == null) it.remove(Keys.STORAGE_URI) else it[Keys.STORAGE_URI] = uri
        }

    // ---- Dual camera (front + back simultaneously) ----
    val dualCameraEnabled: Flow<Boolean> =
        ctx.dataStore.data.map { it[Keys.DUAL_CAMERA] ?: false }

    suspend fun setDualCameraEnabled(v: Boolean) =
        ctx.dataStore.edit { it[Keys.DUAL_CAMERA] = v }

    // ---- Default lens (0 = back, 1 = front) ----
    val defaultLens: Flow<Int> =
        ctx.dataStore.data.map { it[Keys.DEFAULT_LENS] ?: 0 }

    suspend fun setDefaultLens(lens: Int) =
        ctx.dataStore.edit { it[Keys.DEFAULT_LENS] = lens.coerceIn(0, 1) }

    // ---- App language ----
    val language: Flow<AppLanguage> =
        ctx.dataStore.data.map { AppLanguage.fromTag(it[Keys.LANGUAGE]) }

    suspend fun setLanguage(lang: AppLanguage) =
        ctx.dataStore.edit { it[Keys.LANGUAGE] = lang.tag }
}
