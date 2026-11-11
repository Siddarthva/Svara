package com.svara.app

import com.svara.app.ui.PerfTrace
import com.svara.app.ui.EndTraceAfterFrame
import android.os.Bundle
import androidx.activity.compose.setContent
import android.graphics.drawable.ColorDrawable
import com.svara.app.ui.motion.DisplayRate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.isSystemInDarkTheme
import com.svara.app.appearance.Brightness
import com.svara.app.ui.AppearanceViewModel
import com.svara.app.ui.MainViewModel
import com.svara.app.ui.theme.Appearance
import com.svara.app.ui.PermissionScreen
import com.svara.app.ui.SvaraAppScaffold
import com.svara.app.ui.hasAudioPermission
import com.svara.app.ui.onboarding.OnboardingScreen
import com.svara.app.ui.theme.SvaraTheme
import dagger.hilt.android.AndroidEntryPoint

/** A FragmentActivity so the optional app lock can show the system biometric prompt. */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    companion object {
        /** Widgets, the tile and shortcuts open these. */
        const val ACTION_OPEN_NOW_PLAYING = "com.svara.app.action.OPEN_NOW_PLAYING"
        const val ACTION_OPEN_QUEUE = "com.svara.app.action.OPEN_QUEUE"
        const val ACTION_SHUFFLE_ALL = "com.svara.app.action.SHUFFLE_ALL"
        const val ACTION_RESUME = "com.svara.app.action.RESUME"
        const val ACTION_RECENTLY_PLAYED = "com.svara.app.action.RECENTLY_PLAYED"
    }

    private val viewModel: MainViewModel by viewModels()
    private val appearanceViewModel: AppearanceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) viewModel.handleAction(intent?.action)
        enableEdgeToEdge()
        setContent {
            val loaded by appearanceViewModel.appearance.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            // Dark by default for every preset; light only when the user chose to follow the system.
            val appearance = (loaded ?: Appearance()).let {
                it.copy(dark = when (it.custom.brightness) { Brightness.DARK -> true; Brightness.LIGHT -> false; Brightness.SYSTEM -> systemDark })
            }
            // System bar icons follow the app's dark/light state, not the system's.
            DisposableEffect(appearance.dark) {
                val style = if (appearance.dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            SvaraTheme(appearance) {
                // The window behind Compose matches the theme, so a recreation (rotation, font or
                // locale change) or a theme switch never flashes the wrong colour.
                val windowBackground = MaterialTheme.colorScheme.background
                LaunchedEffect(windowBackground) { window.setBackgroundDrawable(ColorDrawable(windowBackground.toArgb())) }
                // Smooth display: the highest refresh rate at this resolution, or the system's choice.
                val smoothDisplay = appearance.custom.smoothDisplay
                LaunchedEffect(smoothDisplay) { DisplayRate.apply(this@MainActivity, smoothDisplay) }
                EndTraceAfterFrame(PerfTrace.PRESET_SWITCH, appearance.preset)
                val onboarded by viewModel.onboardingComplete.collectAsStateWithLifecycle()
                val hasPermission by viewModel.hasPermission.collectAsStateWithLifecycle()
                when {
                    loaded == null || onboarded == null || hasPermission == null -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                    onboarded == false -> OnboardingScreen(
                        hasPermission = hasPermission == true,
                        onPermissionResult = viewModel::onPermissionChecked,
                        onDone = viewModel::completeOnboarding,
                    )
                    hasPermission == false -> PermissionScreen(onResult = viewModel::onPermissionChecked)
                    else -> SvaraAppScaffold(viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        viewModel.handleAction(intent.action)
    }

    override fun onResume() {
        super.onResume()
        viewModel.onPermissionChecked(hasAudioPermission())
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) viewModel.lock()
    }
}
