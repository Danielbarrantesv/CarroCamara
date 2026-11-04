package com.danielbarrantesv.dashcam

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danielbarrantesv.dashcam.data.prefs.SettingsRepository
import com.danielbarrantesv.dashcam.ui.MainViewModel
import com.danielbarrantesv.dashcam.ui.navigation.DashCamApp
import com.danielbarrantesv.dashcam.ui.theme.DashCamTheme
import com.danielbarrantesv.dashcam.util.LocaleHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * Single-Activity entry point. Edge-to-edge, splash, theme, root composable.
 *
 * Extends AppCompatActivity so that AppCompatDelegate.setApplicationLocales()
 * works correctly for per-app language switching on Android 12+ (and 13+
 * uses the platform LocaleManager under the hood).
 *
 * attachBaseContext() re-applies the persisted locale every time the Activity
 * is recreated, so the language change takes effect immediately.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var settings: SettingsRepository

    override fun attachBaseContext(newBase: Context) {
        // Read persisted language and wrap the context so resources come out
        // in the correct locale. This handles both cold start and recreate.
        var languageTag: String? = null
        try {
            val tempSettings = SettingsRepository(newBase)
            languageTag = runBlocking { tempSettings.language.first().tag }
        } catch (_: Exception) {}
        super.attachBaseContext(LocaleHelper.wrap(newBase, languageTag))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Splash must be installed before super.onCreate()
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val mainViewModel: MainViewModel = hiltViewModel()
            val hasCompletedOnboarding = mainViewModel.hasCompletedOnboarding
                .collectAsStateWithLifecycle(initialValue = null)

            DashCamTheme {
                when (hasCompletedOnboarding.value) {
                    null -> Unit // splash still visible
                    false -> com.danielbarrantesv.dashcam.ui.onboarding.OnboardingFlow(
                        onFinish = { mainViewModel.markOnboardingComplete() }
                    )
                    true -> DashCamApp()
                }
            }
        }
    }
}


