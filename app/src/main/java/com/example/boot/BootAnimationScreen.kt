package com.example.boot

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.*
import com.example.R

/**
 * Modern Lottie-driven Boot Animation Screen.
 * Plays the official VoltAtlas Lottie logo animation (2.5s, 75 frames @ 30fps) on deep navy background (#0A1428)
 * with a native Compose text overlay for crisp, readable wordmark & subtitle typography,
 * and smoothly hands off to the main app when both the animation and system initialization complete.
 */
@Composable
fun BootAnimationScreen(
    uiState: BootUiState,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false
) {
    val navyColor = Color(0xFF0A1428)
    val cyanColor = Color(0xFF08B7E8)
    val greenColor = Color(0xFF19C979)

    var userSkipped by remember { mutableStateOf(false) }

    val compositionResult = rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.boot_animation))
    val composition = compositionResult.value
    val isCompositionFailed = compositionResult.isFailure

    val lottieAnimState = animateLottieCompositionAsState(
        composition = composition,
        iterations = 1,
        speed = 1f,
        isPlaying = !reduceMotion && !userSkipped
    )

    val isAnimFinished = reduceMotion || userSkipped || isCompositionFailed || (lottieAnimState.progress >= 1f)
    val isAppReady = userSkipped || uiState.allReady

    // Handoff to main app ONLY when both animation has finished AND app readiness is satisfied
    LaunchedEffect(isAnimFinished, isAppReady) {
        if (isAnimFinished && isAppReady) {
            onFinished()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(navyColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                userSkipped = true
            },
        contentAlignment = Alignment.Center
    ) {
        if (!reduceMotion && composition != null && !isCompositionFailed && !userSkipped) {
            // Lottie layer: renders background, V mark, crossbar, and progress line
            LottieAnimation(
                composition = composition,
                progress = { lottieAnimState.progress },
                modifier = Modifier.fillMaxSize()
            )

            // Native Compose Text Overlay: renders wordmark and subtitle with custom font/color and progress-synced alpha
            val progress = lottieAnimState.progress
            val textAlpha = when {
                progress < 0.30f -> 0f
                progress in 0.30f..0.50f -> ((progress - 0.30f) / 0.20f).coerceIn(0f, 1f)
                progress in 0.50f..0.80f -> 1f
                progress in 0.80f..1.00f -> ((1.00f - progress) / 0.20f).coerceIn(0f, 1f)
                else -> 0f
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(top = 85.dp)
                    .graphicsLayer { alpha = textAlpha }
            ) {
                Text(
                    text = "VoltAtlas",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "ELECTRICAL ENGINEERING PLATFORM",
                    color = cyanColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.5.sp
                )
            }
        } else {
            // Fallback / Static or Reduce-Motion logo view
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                VoltAtlasLogo(
                    modifier = Modifier.size(120.dp),
                    primaryColor = cyanColor,
                    accentColor = greenColor
                )
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "VoltAtlas",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "ELECTRICAL ENGINEERING PLATFORM",
                    color = cyanColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.5.sp
                )
            }
        }
    }
}


