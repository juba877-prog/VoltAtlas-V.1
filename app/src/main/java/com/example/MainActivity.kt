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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.boot.BootAnimationScreen
import com.example.boot.BootViewModel
import com.example.ui.EngineeringViewModel
import com.example.ui.MainLayout
import com.example.ui.theme.VoltAtlasTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    // Install the native splash screen before onCreate
    installSplashScreen()
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    
    setContent {
      val viewModel: EngineeringViewModel = viewModel()
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
      
      VoltAtlasTheme(darkTheme = darkTheme) {
        // Show boot animation on launch (remember ensures clean playback per session)
        val isBootFinished = androidx.compose.runtime.remember { mutableStateOf(false) }

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
                fadeIn(animationSpec = tween(600)) togetherWith fadeOut(animationSpec = tween(600))
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
                MainLayout(viewModel = viewModel)
            }
        }
      }
    }
  }
}
