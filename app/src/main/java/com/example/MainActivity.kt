package com.example

import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.boot.BootAnimationScreen
import com.example.boot.BootViewModel
import com.example.ui.EngineeringViewModel
import com.example.ui.MainLayout
import androidx.compose.runtime.CompositionLocalProvider
import com.example.ui.theme.LocalReduceMotion
import com.example.ui.theme.VoltAtlasTheme
import com.example.ui.theme.VoltAtlasMotion

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    // Install the native splash screen before onCreate
    installSplashScreen()
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    requestHighRefreshRate()
    
    setContent {
      val viewModel: EngineeringViewModel = viewModel()
      val assistantViewModel: com.example.ai.ui.EngineeringAssistantViewModel = viewModel(
        factory = remember { com.example.ai.ui.EngineeringAssistantViewModelFactory.production(application) }
      )
      val bootViewModel: BootViewModel = viewModel()
      val themeMode by viewModel.themeMode.collectAsState()
      val bootState by bootViewModel.state.collectAsState()
      val startupState by viewModel.startupState.collectAsState()
      
      val darkTheme = when (themeMode) {
        "LIGHT" -> false
        "DARK" -> true
        else -> androidx.compose.foundation.isSystemInDarkTheme()
      }

      // Detect reduce-motion accessibility setting
      val context = LocalContext.current
      val reduceMotion = try {
        Settings.Global.getFloat(
          context.contentResolver,
          Settings.Global.ANIMATOR_DURATION_SCALE,
          1f
        ) == 0f
      } catch (_: Exception) { false }
      
      CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
        VoltAtlasTheme(darkTheme = darkTheme) {
        // Show boot animation on launch (rememberSaveable preserves boot state across Activity recreation e.g. system theme toggle)
        val isBootFinished = rememberSaveable { mutableStateOf(false) }

        // Keep boot ViewModel synced with real startupState
        androidx.compose.runtime.LaunchedEffect(startupState) {
          bootViewModel.updateFromStartupState(startupState)
        }

        // Start the boot sequence on first composition
        androidx.compose.runtime.LaunchedEffect(Unit) {
          bootViewModel.start()
        }

        AnimatedContent(
            targetState = isBootFinished.value,
            transitionSpec = {
                if (reduceMotion) {
                    fadeIn(animationSpec = tween(VoltAtlasMotion.DurationMicro)) togetherWith fadeOut(animationSpec = tween(VoltAtlasMotion.DurationMicro))
                } else {
                    fadeIn(animationSpec = tween(VoltAtlasMotion.DurationEmphasized)) togetherWith fadeOut(animationSpec = tween(VoltAtlasMotion.DurationEmphasized))
                }
            },
            label = "BootToMainTransition"
        ) { finished ->
            if (!finished) {
                BootAnimationScreen(
                    uiState = bootState,
                    reduceMotion = reduceMotion,
                    onFinished = { isBootFinished.value = true }
                )
            } else {
                MainLayout(viewModel = viewModel, assistantViewModel = assistantViewModel)
            }
        }
      }
    }
  }
}

  private fun requestHighRefreshRate() {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
      try {
        @Suppress("DEPRECATION")
        val display = windowManager.defaultDisplay
        val modes = display?.supportedModes ?: return
        val maxMode = modes.maxByOrNull { it.refreshRate } ?: return
        val lp = window.attributes
        if (lp.preferredDisplayModeId != maxMode.modeId) {
          lp.preferredDisplayModeId = maxMode.modeId
          window.attributes = lp
        }
      } catch (_: Exception) {
        // Fallback gracefully if display mode API is unavailable
      }
    }
  }
}
