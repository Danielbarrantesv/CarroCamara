package com.danielbarrantesv.dashcam

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danielbarrantesv.dashcam.ui.MainViewModel
import com.danielbarrantesv.dashcam.ui.navigation.DashCamApp
import com.danielbarrantesv.dashcam.ui.theme.DashCamTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-Activity entry point. Edge-to-edge, splash, theme, root composable.
 *
 * Extends AppCompatActivity so that AppCompatDelegate.setApplicationLocales()
 * works correctly for per-app language switching on Android 12+ (and 13+
 * uses the platform LocaleManager under the hood).
 *
 * Screen rotation is enabled by manifest configChanges + screenOrientation=fullSensor.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

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

