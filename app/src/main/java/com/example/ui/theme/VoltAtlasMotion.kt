package com.example.ui.theme

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.unit.IntOffset

/**
 * VoltAtlas Centralized Motion System & Animation Specifications.
 * Establishes consistent motion durations, spring physics parameters, and tab/screen transition specs
 * compliant with Material 3 motion guidelines while supporting system reduced-motion preferences.
 */
object VoltAtlasMotion {
    // Duration Constants (milliseconds)
    const val DurationMicro = 100
    const val DurationFast = 150
    const val DurationStandard = 200
    const val DurationEmphasized = 280
    const val DurationSlow = 350

    // Spring Physics Parameters
    val SpringStiffnessLow = Spring.StiffnessLow
    val SpringDampingNoBouncy = Spring.DampingRatioNoBouncy

    // Common Tween Specs
    val fadeEnterSpec: FiniteAnimationSpec<Float> = tween(durationMillis = DurationStandard, easing = FastOutSlowInEasing)
    val fadeExitSpec: FiniteAnimationSpec<Float> = tween(durationMillis = DurationFast, easing = FastOutLinearInEasing)
    val instantFadeSpec: FiniteAnimationSpec<Float> = tween(durationMillis = DurationMicro, easing = LinearEasing)

    // Common Slide Spring Specs
    val slideSpringSpec: FiniteAnimationSpec<IntOffset> = spring(
        stiffness = SpringStiffnessLow,
        dampingRatio = SpringDampingNoBouncy
    )

    // Tab Transition Specs for MainLayout
    val tabForwardTransition: ContentTransform =
        (fadeIn(animationSpec = fadeEnterSpec) + slideInHorizontally(animationSpec = slideSpringSpec, initialOffsetX = { it / 4 })) togetherWith
        (fadeOut(animationSpec = fadeExitSpec) + slideOutHorizontally(animationSpec = slideSpringSpec, targetOffsetX = { -it / 4 }))

    val tabBackwardTransition: ContentTransform =
        (fadeIn(animationSpec = fadeEnterSpec) + slideInHorizontally(animationSpec = slideSpringSpec, initialOffsetX = { -it / 4 })) togetherWith
        (fadeOut(animationSpec = fadeExitSpec) + slideOutHorizontally(animationSpec = slideSpringSpec, targetOffsetX = { it / 4 }))

    val tabReducedMotionTransition: ContentTransform =
        fadeIn(animationSpec = instantFadeSpec) togetherWith fadeOut(animationSpec = instantFadeSpec)
}
