package com.example.ui.components

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.graphics.toArgb
import com.example.ui.theme.EngineeringStatusPalette
import com.example.ui.theme.VoltAtlasMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 8 Slice 8C — engineering-state truth & animation policy tests.
 *
 * Proves:
 *  - unchanged values NEVER retrigger emphasis effects;
 *  - first appearance does not pulse (no animation on cold content);
 *  - rapid state changes settle on the LATEST authoritative state;
 *  - reduced-motion timeline is empty (no pulse/overshoot) while the status
 *    value itself still publishes immediately by contract;
 *  - PASS / WARNING / FAIL remain the pinned EngineeringStatusPalette values;
 *  - pulse timelines never use springs (calm, direct — no overshoot on
 *    errors/warnings).
 */
class StatusFeedbackPolicyTest {

    // ── Replay prevention ───────────────────────────────────────────────────

    @Test
    fun testIdenticalRecompositionsDoNotRetriggerEffects() {
        assertFalse(StatusFeedbackPolicy.shouldPulse("PASS", "PASS"))
        assertFalse(StatusFeedbackPolicy.shouldPulse("FAIL", "FAIL"))
        assertFalse(StatusFeedbackPolicy.shouldPulse("WARNING", "WARNING"))
        assertFalse(StatusFeedbackPolicy.shouldPulse("STALE", "STALE"))
    }

    @Test
    fun testFirstAppearanceDoesNotPulse() {
        // previous == null means cold composition: content must not animate.
        assertFalse(StatusFeedbackPolicy.shouldPulse(null, "PASS"))
        assertFalse(StatusFeedbackPolicy.shouldPulse(null, "FAIL"))
    }

    @Test
    fun testRealStateChangeTriggersSingleEmphasis() {
        assertTrue(StatusFeedbackPolicy.shouldPulse("PASS", "FAIL"))
        assertTrue(StatusFeedbackPolicy.shouldPulse("STALE", "PASS"))
        assertTrue(StatusFeedbackPolicy.shouldPulse("MISSING", "INCOMPLETE"))
    }

    // ── Rapid-change settling ───────────────────────────────────────────────

    @Test
    fun testRapidChangesSettleOnLatestAuthoritativeState() {
        val rapidSequence = listOf("CALCULATING", "PASS", "STALE", "FAIL")
        assertEquals(
            "The LAST emitted state owns the final published frame",
            "FAIL",
            StatusFeedbackPolicy.settlesOnLatest(rapidSequence.asSequence())
        )
        assertEquals(
            "NORMAL",
            StatusFeedbackPolicy.settlesOnLatest(listOf("FAIL", "WARNING", "NORMAL").asSequence())
        )
    }

    // ── Reduced motion ──────────────────────────────────────────────────────

    @Test
    fun testReducedMotionTimelineIsEmpty() {
        // No pulse, no overshoot, no spatial effect under reduced motion.
        assertTrue(StatusFeedbackPolicy.emphasisTimeline(shouldPulse = true, reduceMotion = true).isEmpty())
        assertTrue(StatusFeedbackPolicy.emphasisTimeline(shouldPulse = false, reduceMotion = false).isEmpty())
        assertTrue(StatusFeedbackPolicy.emphasisTimeline(shouldPulse = false, reduceMotion = true).isEmpty())
    }

    @Test
    fun testNormalMotionTimelineIsCalmSnapTweenTweenWithoutSprings() {
        val timeline = StatusFeedbackPolicy.emphasisTimeline(shouldPulse = true, reduceMotion = false)
        assertEquals(3, timeline.size)

        val snap = timeline[0] as StatusFeedbackPolicy.EmphasisStep.Snap
        assertEquals(0f, snap.value)

        val up = timeline[1] as StatusFeedbackPolicy.EmphasisStep.Tween
        assertEquals(1f, up.toValue)
        assertEquals(VoltAtlasMotion.DefaultEffects.DurationMillis, up.durationMillis)
        assertEquals(FastOutSlowInEasing::class, up.easing::class)

        val down = timeline[2] as StatusFeedbackPolicy.EmphasisStep.Tween
        assertEquals(0f, down.toValue)
        assertEquals(VoltAtlasMotion.FastEffects.DurationMillis, down.durationMillis)
        assertEquals(FastOutLinearInEasing::class, down.easing::class)
    }

    // ── Engineering palette integrity ───────────────────────────────────────

    @Test
    fun testPassWarningFailColorsRemainApprovedSemanticValues() {
        // These pixels are the Step 7 engineering-truth contract; motion must
        // never re-map them (e.g. to Material tertiary roles).
        val argb: (androidx.compose.ui.graphics.Color) -> Int = { it.toArgb() }
        assertEquals(0xFF3DD68C.toInt(), argb(EngineeringStatusPalette.Pass))
        assertEquals(0xFFF5A623.toInt(), argb(EngineeringStatusPalette.Warning))
        assertEquals(0xFFF0554C.toInt(), argb(EngineeringStatusPalette.Fail))

        // And they must stay distinct from each other and from Material tertiary.
        assertNotEquals(EngineeringStatusPalette.Pass, EngineeringStatusPalette.Warning)
        assertNotEquals(EngineeringStatusPalette.Warning, EngineeringStatusPalette.Fail)
        assertNotEquals(EngineeringStatusPalette.Pass, EngineeringStatusPalette.Fail)
    }
}