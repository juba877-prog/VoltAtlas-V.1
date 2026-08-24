package com.example.ui.components

import androidx.compose.animation.core.Easing
import com.example.ui.theme.VoltAtlasMotion

/**
 * Phase 8 Slice 8C — deterministic engineering-state feedback POLICY.
 *
 * ENGINEERING TRUTH: the semantic status string itself is NEVER animated,
 * interpolated, or delayed — it publishes immediately in composition and in
 * the accessibility tree. Only decorative container emphasis (border glow /
 * background alpha) may follow the timeline returned here.
 *
 * This policy object is intentionally pure so it can be tested without a
 * Compose runtime.
 */
object StatusFeedbackPolicy {

    /**
     * A single step of the emphasis timeline executed by the UI layer.
     */
    sealed interface EmphasisStep {
        /** Jump instantly to [value] (no visual interpolation). */
        data class Snap(val value: Float) : EmphasisStep

        /** Animate toward [toValue] over [durationMillis] with [easing]. */
        data class Tween(val toValue: Float, val durationMillis: Int, val easing: Easing) : EmphasisStep
    }

    /**
     * True only when the status actually CHANGED from a previously rendered
     * value. First appearance (previous == null) never pulses — cold content
     * must not animate. Identical recompositions never replay.
     */
    fun shouldPulse(previous: String?, current: String): Boolean =
        previous != null && previous != current

    /**
     * Declarative emphasis timeline.
     *
     * - No change or reduced motion -> empty (no pulse; final state already
     *   published synchronously).
     * - Real change -> brief calm emphasis: snap low, ease up on
     *   [VoltAtlasMotion.DefaultEffects] pacing, settle back on
     *   [VoltAtlasMotion.FastEffects]. No spring overshoot — errors and
     *   warnings stay direct; expressive overshoot is reserved elsewhere.
     */
    fun emphasisTimeline(shouldPulse: Boolean, reduceMotion: Boolean): List<EmphasisStep> {
        if (!shouldPulse || reduceMotion) return emptyList()
        return listOf(
            EmphasisStep.Snap(0f),
            EmphasisStep.Tween(
                toValue = 1f,
                durationMillis = VoltAtlasMotion.DefaultEffects.DurationMillis,
                easing = androidx.compose.animation.core.FastOutSlowInEasing
            ),
            EmphasisStep.Tween(
                toValue = 0f,
                durationMillis = VoltAtlasMotion.FastEffects.DurationMillis,
                easing = androidx.compose.animation.core.FastOutLinearInEasing
            )
        )
    }

    /**
     * Rapid successive changes always settle on the LATEST authoritative
     * state: each timeline restart is cancellable by construction (the UI
     * layer drives it from a key-launched effect), so the last emitted status
     * owns the final emphasis frame. This function documents/verifies the
     * contract: the winner is simply the last observed value.
     */
    fun settlesOnLatest(states: Sequence<String>): String = states.last()
}