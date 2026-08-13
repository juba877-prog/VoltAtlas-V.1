package com.example.boot

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.*
import com.example.R
import com.example.ui.theme.BrandAccent
import com.example.ui.theme.StatusPass

/**
 * Modern Lottie-driven Boot Animation Screen.
 * Plays the official VoltAtlas Lottie logo animation (2.5s, 75 frames @ 30fps) with dynamic Lottie KeyPath property
 * overrides matching the current MaterialTheme colorScheme (Dark/Light mode compliant).
 * Renders native Compose text overlay & reduced-motion fallback with theme-aware colors.
 */
@Composable
fun BootAnimationScreen(
    uiState: BootUiState,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false
) {
    val backgroundColor = MaterialTheme.colorScheme.background
    val primaryTextColor = MaterialTheme.colorScheme.onBackground
    val accentColor = BrandAccent
    val greenAccent = StatusPass

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

    val dynamicProperties = rememberLottieDynamicProperties(
        rememberLottieDynamicProperty(
            property = LottieProperty.COLOR,
            value = backgroundColor.toArgb(),
            keyPath = arrayOf("Background Navy", "**")
        ),
        rememberLottieDynamicProperty(
            property = LottieProperty.STROKE_COLOR,
            value = accentColor.toArgb(),
            keyPath = arrayOf("V Mark", "**")
        ),
        rememberLottieDynamicProperty(
            property = LottieProperty.STROKE_COLOR,
            value = greenAccent.toArgb(),
            keyPath = arrayOf("Crossbar", "**")
        ),
        rememberLottieDynamicProperty(
            property = LottieProperty.COLOR,
            value = accentColor.toArgb(),
            keyPath = arrayOf("Progress Line", "**")
        ),
        rememberLottieDynamicProperty(
            property = LottieProperty.COLOR,
            value = primaryTextColor.toArgb(),
            keyPath = arrayOf("Wordmark", "**")
        ),
        rememberLottieDynamicProperty(
            property = LottieProperty.COLOR,
            value = accentColor.toArgb(),
            keyPath = arrayOf("Subtitle", "**")
        )
    )

    // Handoff to main app ONLY when both animation has finished AND app readiness is satisfied
    LaunchedEffect(isAnimFinished, isAppReady) {
        if (isAnimFinished && isAppReady) {
            onFinished()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                userSkipped = true
            },
        contentAlignment = Alignment.Center
    ) {
        if (!reduceMotion && composition != null && !isCompositionFailed && !userSkipped) {
            // Dynamic Lottie layer: renders background, V mark, crossbar, and progress line with theme colors
            LottieAnimation(
                composition = composition,
                progress = { lottieAnimState.progress },
                dynamicProperties = dynamicProperties,
                modifier = Modifier.fillMaxSize()
            )

            // Native Compose Text Overlay: renders wordmark and subtitle with theme typography and progress-synced alpha
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
                    color = primaryTextColor,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "ELECTRICAL ENGINEERING PLATFORM",
                    color = accentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.5.sp
                )
            }
        } else {
            // Fallback / Static or Reduce-Motion logo view (theme aware)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                VoltAtlasLogo(
                    modifier = Modifier.size(120.dp),
                    primaryColor = accentColor,
                    accentColor = greenAccent
                )
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "VoltAtlas",
                    color = primaryTextColor,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "ELECTRICAL ENGINEERING PLATFORM",
                    color = accentColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.5.sp
                )
            }
        }
    }
}
