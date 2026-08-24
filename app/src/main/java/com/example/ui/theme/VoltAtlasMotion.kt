package com.example.ui.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset

/**
 * VoltAtlas Centralized Motion System (Phase 8).
 *
 * MOTION HIERARCHY — every animation in the app MUST reference one of these
 * seven contracts instead of declaring ad-hoc easings/springs:
 *
 *  STANDARD MOTION   -> [VoltAtlasMotion.StandardSpatial] / [FastSpatial]
 *      Routine navigation, forms, tabs, controls, utility changes, dense
 *      engineering content. Calm by default: deterministic tweens, no bounce.
 *
 *  EMPHASIZED MOTION -> [VoltAtlasMotion.EmphasizedSpatial]
 *      Selected high-value moments ONLY: workspace entry, opening the
 *      assistant, major result-card reveal, successful export, significant
 *      confirmed user action. The only token allowed a controlled expressive
 *      spring. NEVER applied to tables, routine navigation, validation
 *      errors, dropdowns, or frequently changing engineering values.
 *
 *  EFFECTS MOTION    -> [VoltAtlasMotion.FastEffects] / [DefaultEffects] /
 *                       [SlowEffects]
 *      Color, alpha, status-container emphasis, loading completion,
 *      freshness changes, non-spatial feedback. Conceptually separate from
 *      spatial motion: effects tokens never move geometry.
 *
 *  REDUCED MOTION    -> [VoltAtlasMotion.ReducedMotion]
 *      Immediate state publication with minimal or instant fades; no bounce,
 *      stagger, scale pulse, or large spatial travel. [LocalReduceMotion]
 *      remains the authoritative detection source.
 *
 * ENGINEERING-TRUTH RULE: no animation may fabricate, interpolate, delay,
 * obscure, or visually replace an engineering result. Calculated values and
 * semantic states publish immediately; motion may only highlight the
 * already-published result.
 */
object VoltAtlasMotion {
    // ── Duration Constants (milliseconds) ──────────────────────────────────
    const val DurationMicro = 100
    const val DurationFast = 150
    const val DurationStandard = 200
    const val DurationEmphasized = 280
    const val DurationSlow = 350

    // ── SPATIAL TOKENS (position/size/transform) ───────────────────────────

    /**
     * Calm application default for routine spatial changes (tabs, forms,
     * panels, dense engineering content). Deterministic 200ms ease-in-out.
     */
    object StandardSpatial {
        const val DurationMillis = DurationStandard
        val easing = FastOutSlowInEasing
        val floatSpec: FiniteAnimationSpec<Float> =
            tween(durationMillis = DurationMillis, easing = easing)
        val offsetSpec: FiniteAnimationSpec<IntOffset> =
            tween(durationMillis = DurationMillis, easing = easing)
    }

    /** Quick spatial feedback that must not linger (small reveals, chips). */
    object FastSpatial {
        const val DurationMillis = DurationFast
        val easing = FastOutSlowInEasing
        val floatSpec: FiniteAnimationSpec<Float> =
            tween(durationMillis = DurationMillis, easing = easing)
        val offsetSpec: FiniteAnimationSpec<IntOffset> =
            tween(durationMillis = DurationMillis, easing = easing)
    }

    /**
     * The ONLY token permitted a controlled expressive spring. Reserved for
     * explicitly approved emphasized moments (workspace entry, assistant
     * opening, result reveal). Slight overshoot (damping 0.85) settles fast
     * without feeling playful; no-bounce contexts must never use it.
     */
    object EmphasizedSpatial {
        const val DurationMillis = DurationEmphasized
        /** Controlled expressive damping: subtle single overshoot, then settle. */
        const val ExpressiveDampingRatio = 0.85f
        val stiffness = Spring.StiffnessMediumLow
        val floatSpec: FiniteAnimationSpec<Float> = spring(
            dampingRatio = ExpressiveDampingRatio,
            stiffness = stiffness
        )
        val offsetSpec: FiniteAnimationSpec<IntOffset> = spring(
            dampingRatio = ExpressiveDampingRatio,
            stiffness = stiffness
        )
    }

    // ── EFFECTS TOKENS (color/alpha/emphasis only — never geometry) ────────

    /** Rapid emphasis changes: badge color swap, quick container emphasis. */
    object FastEffects {
        const val DurationMillis = DurationFast
        val floatSpec: FiniteAnimationSpec<Float> =
            tween(durationMillis = DurationMillis, easing = FastOutLinearInEasing)
    }

    /** Default effect pacing for status/freshness/container emphasis. */
    object DefaultEffects {
        const val DurationMillis = DurationStandard
        val floatSpec: FiniteAnimationSpec<Float> =
            tween(durationMillis = DurationMillis, easing = FastOutSlowInEasing)
    }

    /** Slow ambient effects: freshness decay, calm progress completion. */
    object SlowEffects {
        const val DurationMillis = DurationSlow
        val floatSpec: FiniteAnimationSpec<Float> =
            tween(durationMillis = DurationMillis, easing = FastOutSlowInEasing)
    }

    // ── REDUCED MOTION TOKEN ───────────────────────────────────────────────

    /**
     * Reduced-motion contract: minimal 100ms linear fade so state still reads
     * as a change, but with zero spatial travel, pulse, bounce, or stagger.
     * When a truly instant publication is required, skip animation entirely.
     */
    object ReducedMotion {
        const val DurationMillis = DurationMicro
        val floatSpec: FiniteAnimationSpec<Float> =
            tween(durationMillis = DurationMillis, easing = LinearEasing)
        val offsetSpec: FiniteAnimationSpec<IntOffset> =
            tween(durationMillis = DurationMillis, easing = LinearEasing)
    }

    @Deprecated("Use StandardSpatial.floatSpec", ReplaceWith("VoltAtlasMotion.StandardSpatial.floatSpec"))
    val fadeEnterSpec: FiniteAnimationSpec<Float> = StandardSpatial.floatSpec

    @Deprecated("Use FastEffects.floatSpec", ReplaceWith("VoltAtlasMotion.FastEffects.floatSpec"))
    val fadeExitSpec: FiniteAnimationSpec<Float> = FastEffects.floatSpec

    @Deprecated("Use ReducedMotion.floatSpec", ReplaceWith("VoltAtlasMotion.ReducedMotion.floatSpec"))
    val instantFadeSpec: FiniteAnimationSpec<Float> = ReducedMotion.floatSpec

    @Deprecated("Use EmphasizedSpatial.offsetSpec", ReplaceWith("VoltAtlasMotion.EmphasizedSpatial.offsetSpec"))
    val slideSpringSpec: FiniteAnimationSpec<IntOffset> = EmphasizedSpatial.offsetSpec

    @Deprecated("Use StandardSpatial.offsetSpec", ReplaceWith("VoltAtlasMotion.StandardSpatial.offsetSpec"))
    val tabSlideSpec: FiniteAnimationSpec<IntOffset> = StandardSpatial.offsetSpec

    @Deprecated("Use StandardSpatial / ReducedMotion contracts directly", ReplaceWith("VoltAtlasMotion.StandardSpatial"))
    val SpringStiffnessLow = Spring.StiffnessLow

    @Deprecated("Use StandardSpatial / ReducedMotion contracts directly", ReplaceWith("VoltAtlasMotion.ReducedMotion"))
    val SpringDampingNoBouncy = Spring.DampingRatioNoBouncy

    // ── LEGACY TRANSITIONS (existing MainLayout call sites) ────────────────
    // Slice 8B replaces these with directional shared-axis builders; kept so
    // current call sites compile unchanged until migrated.

    // Tab Transition Specs for MainLayout
    @Deprecated("Migrated by Slice 8B directional transition builder", ReplaceWith("VoltAtlasMotion.tabContentTransform"))
    val tabForwardTransition: ContentTransform =
        (androidx.compose.animation.fadeIn(animationSpec = StandardSpatial.floatSpec) +
         androidx.compose.animation.slideInHorizontally(animationSpec = StandardSpatial.offsetSpec, initialOffsetX = { it / 4 })) togetherWith
        (androidx.compose.animation.fadeOut(animationSpec = FastEffects.floatSpec) +
         androidx.compose.animation.slideOutHorizontally(animationSpec = StandardSpatial.offsetSpec, targetOffsetX = { -it / 4 }))

    @Deprecated("Migrated by Slice 8B directional transition builder", ReplaceWith("VoltAtlasMotion.tabContentTransform"))
    val tabBackwardTransition: ContentTransform =
        (androidx.compose.animation.fadeIn(animationSpec = StandardSpatial.floatSpec) +
         androidx.compose.animation.slideInHorizontally(animationSpec = StandardSpatial.offsetSpec, initialOffsetX = { -it / 4 })) togetherWith
        (androidx.compose.animation.fadeOut(animationSpec = FastEffects.floatSpec) +
         androidx.compose.animation.slideOutHorizontally(animationSpec = StandardSpatial.offsetSpec, targetOffsetX = { it / 4 }))

    // Reduced motion transition: minimal crossfade without slide offsets
    val tabReducedMotionTransition: ContentTransform =
        androidx.compose.animation.fadeIn(animationSpec = ReducedMotion.floatSpec) togetherWith
        androidx.compose.animation.fadeOut(animationSpec = ReducedMotion.floatSpec)
}
