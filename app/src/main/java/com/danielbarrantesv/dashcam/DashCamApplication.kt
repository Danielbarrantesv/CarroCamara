package com.danielbarrantesv.dashcam

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.danielbarrantesv.dashcam.data.prefs.SettingsRepository
import com.danielbarrantesv.dashcam.util.LocaleHelper
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * Application entry point. Sets up:
 *  - Hilt dependency injection
 *  - WorkManager with HiltWorkerFactory
 *  - Notification channels (recording, events, transfer)
 *  - Apply persisted language on cold start (via attachBaseContext + onCreate)
 */
@HiltAndroidApp
class DashCamApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var settings: SettingsRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    /**
     * Apply the persisted locale synchronously before any Activity is created.
     * We use runBlocking here because attachBaseContext runs BEFORE onCreate and
     * BEFORE Hilt injection completes — we cannot use the injected settings field yet.
     *
     * We instantiate a one-off SettingsRepository using the wrapped base context.
     */
    override fun attachBaseContext(base: Context) {
        // Read the persisted language tag synchronously.
        // DataStore on first run is fast (one file read) and this only happens at
        // process startup, so the blocking call is acceptable.
        var languageTag: String? = null
        try {
            // We use a temporary SettingsRepository that reads from the base context.
            // Hilt isn't ready yet, so we construct it manually.
            val tempSettings = SettingsRepository(base)
            // runBlocking + first() to get the current value synchronously.
            languageTag = runBlocking { tempSettings.language.first().tag }
        } catch (_: Exception) {
            // If DataStore isn't ready, fall back to system default.
        }
        super.attachBaseContext(LocaleHelper.applyAtStartup(base, languageTag))
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        // Also apply via AppCompatDelegate (redundant with attachBaseContext but
        // covers the case where the Activity is recreated without process death).
        appScope.launch {
            try {
                val lang = settings.language.first()
                AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.forLanguageTags(lang.tag)
                )
            } catch (_: Exception) {}
        }
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java)

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_RECORDING,
                getString(R.string.notif_channel_recording),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_recording_desc)
                setShowBadge(false)
            }
        )

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_EVENTS,
                getString(R.string.notif_channel_events),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notif_channel_events_desc)
                enableVibration(true)
            }
        )

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_TRANSFER,
                getString(R.string.notif_channel_transfer),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_transfer_desc)
            }
        )
    }

    companion object {
        const val CHANNEL_RECORDING = "recording"
        const val CHANNEL_EVENTS = "events"
        const val CHANNEL_TRANSFER = "transfer"
    }
}


