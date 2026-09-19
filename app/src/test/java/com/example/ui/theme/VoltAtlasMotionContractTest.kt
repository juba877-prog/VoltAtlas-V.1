package com.example.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 8 Slice 8A — centralized motion contract tests.
 *
 * Proves:
 *  - all seven motion tokens exist and remain mutually distinct;
 *  - standard and emphasized spatial tokens are NOT equivalent;
 *  - reduced-motion produces minimal behavior (micro duration, linear,
 *    no bounce-capable spring);
 *  - effects tokens are separated from spatial tokens conceptually
 *    (deterministic tween pacing, never springs).
 */
class VoltAtlasMotionContractTest {

    // ── Token existence & distinctness ─────────────────────────────────────

    @Test
    fun testAllSevenMotionTokensAreDefined() {
        val tokens = listOf(
            VoltAtlasMotion.StandardSpatial,
            VoltAtlasMotion.FastSpatial,
            VoltAtlasMotion.EmphasizedSpatial,
            VoltAtlasMotion.FastEffects,
            VoltAtlasMotion.DefaultEffects,
            VoltAtlasMotion.SlowEffects,
            VoltAtlasMotion.ReducedMotion
        )
        assertEquals(7, tokens.size)
        assertEquals(7, tokens.toSet().size)
    }

    @Test
    fun testStandardAndEmphasizedTokensRemainDistinct() {
        // Standard is a calm deterministic tween; emphasized is a controlled
        // expressive spring. They must never converge.
        assertNotEquals(
            "StandardSpatial must not share EmphasizedSpatial's duration",
            VoltAtlasMotion.StandardSpatial.DurationMillis,
            VoltAtlasMotion.EmphasizedSpatial.DurationMillis
        )
        assertNotEquals(
            VoltAtlasMotion.StandardSpatial.floatSpec,
            VoltAtlasMotion.EmphasizedSpatial.floatSpec
        )
        // Expressive damping must be under-damped enough to feel intentional
        // but controlled (no playful bounce).
        assertTrue(
            "Expressive damping must be < 1.0 (subtle overshoot)",
            VoltAtlasMotion.EmphasizedSpatial.ExpressiveDampingRatio < 1.0f
        )
        assertTrue(
            "Expressive damping must be >= 0.75 (controlled, not bouncy)",
            VoltAtlasMotion.EmphasizedSpatial.ExpressiveDampingRatio >= 0.75f
        )
    }

    @Test
    fun testStandardMotionIsCalmApplicationDefault() {
        // Standard = 200ms FastOutSlowIn tween — the calm application default.
        assertEquals(200, VoltAtlasMotion.StandardSpatial.DurationMillis)
        assertEquals(VoltAtlasMotion.DurationStandard, VoltAtlasMotion.DefaultEffects.DurationMillis)
    }

    @Test
    fun testFastSpatialIsQuickerThanStandard() {
        assertTrue(
            VoltAtlasMotion.FastSpatial.DurationMillis < VoltAtlasMotion.StandardSpatial.DurationMillis
        )
    }

    @Test
    fun testSlowEffectsIsSlowerThanDefault() {
        assertTrue(
            VoltAtlasMotion.SlowEffects.DurationMillis > VoltAtlasMotion.DefaultEffects.DurationMillis
        )
    }

    // ── Reduced motion ─────────────────────────────────────────────────────

    @Test
    fun testReducedMotionProducesMinimalBehavior() {
        // Minimal = micro-duration fade, no long travel time.
        assertEquals(VoltAtlasMotion.DurationMicro, VoltAtlasMotion.ReducedMotion.DurationMillis)
        assertTrue(
            "Reduced motion must be at most as fast as the quickest token",
            VoltAtlasMotion.ReducedMotion.DurationMillis <= VoltAtlasMotion.FastEffects.DurationMillis &&
                VoltAtlasMotion.ReducedMotion.DurationMillis <= VoltAtlasMotion.FastSpatial.DurationMillis
        )
        // No bounce: reduced motion exposes only tween specs (no spring spec).
        val specClassNames = listOf(
            VoltAtlasMotion.ReducedMotion.floatSpec,
            VoltAtlasMotion.ReducedMotion.offsetSpec
        ).map { it::class.java.simpleName }
        assertTrue(
            "ReducedMotion specs must be deterministic tweens, got: $specClassNames",
            specClassNames.all { it.contains("Tween") }
        )
    }

    // ── Effects vs spatial separation ──────────────────────────────────────

    @Test
    fun testEffectsTokensUseDeterministicTweenDurationsOnly() {
        // Effects motion is color/alpha/emphasis only — deterministic tweens.
        assertEquals(150, VoltAtlasMotion.FastEffects.DurationMillis)
        assertEquals(200, VoltAtlasMotion.DefaultEffects.DurationMillis)
        assertEquals(350, VoltAtlasMotion.SlowEffects.DurationMillis)
    }

    // ── Stage 4 / O3: Spec factories & intent-based contract assertions ───

    @Test
    fun testSpatialIntSizeSpecsAreDeterministicTweens() {
        val standardSize = VoltAtlasMotion.StandardSpatial.intSizeSpec as androidx.compose.animation.core.TweenSpec<androidx.compose.ui.unit.IntSize>
        assertEquals(200, standardSize.durationMillis)
        assertEquals(androidx.compose.animation.core.FastOutSlowInEasing, standardSize.easing)

        val fastSize = VoltAtlasMotion.FastSpatial.intSizeSpec as androidx.compose.animation.core.TweenSpec<androidx.compose.ui.unit.IntSize>
        assertEquals(150, fastSize.durationMillis)
        assertEquals(androidx.compose.animation.core.FastOutSlowInEasing, fastSize.easing)

        assertNotEquals(
            "StandardSpatial.intSizeSpec and FastSpatial.intSizeSpec must remain distinct",
            VoltAtlasMotion.StandardSpatial.intSizeSpec,
            VoltAtlasMotion.FastSpatial.intSizeSpec
        )
    }

    @Test
    fun testSpecFactoriesNumericValuesAndIntent() {
        val standardFloat = VoltAtlasMotion.StandardSpatial.floatSpec as androidx.compose.animation.core.TweenSpec<Float>
        assertEquals(200, standardFloat.durationMillis)
        assertEquals(androidx.compose.animation.core.FastOutSlowInEasing, standardFloat.easing)

        val fastEffectsFloat = VoltAtlasMotion.FastEffects.floatSpec as androidx.compose.animation.core.TweenSpec<Float>
        assertEquals(150, fastEffectsFloat.durationMillis)
        assertEquals(androidx.compose.animation.core.FastOutLinearInEasing, fastEffectsFloat.easing)

        val reducedFloat = VoltAtlasMotion.ReducedMotion.floatSpec as androidx.compose.animation.core.TweenSpec<Float>
        assertEquals(VoltAtlasMotion.DurationMicro, reducedFloat.durationMillis)
        assertEquals(100, reducedFloat.durationMillis)
        assertEquals(androidx.compose.animation.core.LinearEasing, reducedFloat.easing)

        assertTrue(
            "ReducedMotion duration must be <= 100ms per contract, got ${VoltAtlasMotion.ReducedMotion.DurationMillis}",
            VoltAtlasMotion.ReducedMotion.DurationMillis <= 100
        )
    }

    @Test
    fun testExpandableTransitionsIntentAndReducedMotionDistinctness() {
        // 1. Intent-based distinctness assertions: reduced motion must genuinely differ from standard
        assertNotEquals(
            "expandableEnter(true) must not equal expandableEnter(false)",
            VoltAtlasMotion.expandableEnter(reduceMotion = true),
            VoltAtlasMotion.expandableEnter(reduceMotion = false)
        )
        assertNotEquals(
            "expandableExit(true) must not equal expandableExit(false)",
            VoltAtlasMotion.expandableExit(reduceMotion = true),
            VoltAtlasMotion.expandableExit(reduceMotion = false)
        )

        // 2. Under reduced motion, transition degrades to zero-spatial micro-fade
        assertEquals(
            androidx.compose.animation.fadeIn(animationSpec = VoltAtlasMotion.ReducedMotion.floatSpec),
            VoltAtlasMotion.expandableEnter(reduceMotion = true)
        )
        assertEquals(
            androidx.compose.animation.fadeOut(animationSpec = VoltAtlasMotion.ReducedMotion.floatSpec),
            VoltAtlasMotion.expandableExit(reduceMotion = true)
        )
    }

    @Test
    fun testBootContentTransformIntentAndReducedMotionDistinctness() {
        val standardBoot = VoltAtlasMotion.bootContentTransform(reduceMotion = false)
        val reducedBoot = VoltAtlasMotion.bootContentTransform(reduceMotion = true)

        assertNotEquals(
            "bootContentTransform enter transition must differ under reduced motion",
            standardBoot.targetContentEnter,
            reducedBoot.targetContentEnter
        )
        assertNotEquals(
            "bootContentTransform exit transition must differ under reduced motion",
            standardBoot.initialContentExit,
            reducedBoot.initialContentExit
        )

        val expectedReducedBootEnter = androidx.compose.animation.fadeIn(animationSpec = VoltAtlasMotion.ReducedMotion.floatSpec)
        val expectedReducedBootExit = androidx.compose.animation.fadeOut(animationSpec = VoltAtlasMotion.ReducedMotion.floatSpec)

        assertEquals(expectedReducedBootEnter, reducedBoot.targetContentEnter)
        assertEquals(expectedReducedBootExit, reducedBoot.initialContentExit)
    }
}