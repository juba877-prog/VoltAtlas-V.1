package com.example.ui.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

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
        val intSizeSpec: FiniteAnimationSpec<IntSize> =
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
        val intSizeSpec: FiniteAnimationSpec<IntSize> =
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

    // ── EXPANDABLE DRAWER / CARD TRANSITIONS ──────────────────────────────
    /**
     * Standardized expand transition for drawers, cards, and calculation breakdowns.
     * Deterministic 200ms tween on standard spatial tokens; minimal fade under reduced motion.
     */
    fun expandableEnter(reduceMotion: Boolean): EnterTransition =
        if (reduceMotion) {
            fadeIn(animationSpec = ReducedMotion.floatSpec)
        } else {
            fadeIn(animationSpec = StandardSpatial.floatSpec) +
                expandVertically(animationSpec = StandardSpatial.intSizeSpec)
        }

    /**
     * Standardized collapse transition for drawers, cards, and calculation breakdowns.
     * Deterministic 150ms tween on fast spatial tokens; minimal fade under reduced motion.
     */
    fun expandableExit(reduceMotion: Boolean): ExitTransition =
        if (reduceMotion) {
            fadeOut(animationSpec = ReducedMotion.floatSpec)
        } else {
            fadeOut(animationSpec = FastEffects.floatSpec) +
                shrinkVertically(animationSpec = FastSpatial.intSizeSpec)
        }

    @Composable
    fun expandableEnter(): EnterTransition = expandableEnter(LocalReduceMotion.current)

    @Composable
    fun expandableExit(): ExitTransition = expandableExit(LocalReduceMotion.current)

    // ── BOOT SCREEN TRANSITION ─────────────────────────────────────────────
    /**
     * Boot to MainLayout screen crossfade transition.
     * Respects reduced motion with instant/micro-fade.
     */
    fun bootContentTransform(reduceMotion: Boolean): ContentTransform =
        if (reduceMotion) {
            fadeIn(animationSpec = ReducedMotion.floatSpec) togetherWith
                fadeOut(animationSpec = ReducedMotion.floatSpec)
        } else {
            fadeIn(animationSpec = tween(durationMillis = DurationEmphasized, easing = StandardSpatial.easing)) togetherWith
                fadeOut(animationSpec = FastEffects.floatSpec)
        }

    @Composable
    fun bootContentTransform(): ContentTransform = bootContentTransform(LocalReduceMotion.current)

    // ── DIRECTIONAL SHARED-AXIS TRANSITION CONTRACT (Slice 8B) ─────────────

    /**
     * Horizontal navigation direction. In RTL layouts the composables apply
     * the sign through [LocalLayoutDirection]-aware offsets at the call site;
     * the token layer only owns magnitude and timing.
     */
    enum class NavigationDirection { Forward, Backward }

    /**
     * Pure, deterministic direction policy for index-based destinations.
     * Higher destination index == deeper in hierarchy == Forward.
     */
    fun navigationDirection(fromIndex: Int, toIndex: Int): NavigationDirection =
        if (toIndex >= fromIndex) NavigationDirection.Forward else NavigationDirection.Backward

    /**
     * Restrained shared-axis-inspired composition for PRIMARY WORKSPACE
     * DESTINATION CHANGES (MainLayout tabs): directional translation limited
     * to 1/6 of the width (no carousel feel), subtle scale (0.98 -> 1.0,
     * engineering screens must not zoom wildly), plus alpha.
     *
     * Deterministic [StandardSpatial] tweens only — routine navigation never
     * uses the expressive spring. Exit mirrors enter so back navigation is
     * visually symmetric. Reduced motion degrades to the minimal fade pair.
     */
    fun tabContentTransform(
        direction: NavigationDirection,
        reduceMotion: Boolean
    ): ContentTransform {
        if (reduceMotion) return tabReducedMotionTransition
        val sign = if (direction == NavigationDirection.Forward) 1 else -1
        val enter = androidx.compose.animation.fadeIn(animationSpec = StandardSpatial.floatSpec) +
            androidx.compose.animation.slideInHorizontally(
                animationSpec = StandardSpatial.offsetSpec,
                initialOffsetX = { sign * it / 6 }
            ) +
            androidx.compose.animation.scaleIn(
                animationSpec = StandardSpatial.floatSpec,
                initialScale = 0.98f
            )
        val exit = androidx.compose.animation.fadeOut(animationSpec = FastEffects.floatSpec) +
            androidx.compose.animation.slideOutHorizontally(
                animationSpec = StandardSpatial.offsetSpec,
                targetOffsetX = { -sign * it / 6 }
            ) +
            androidx.compose.animation.scaleOut(
                animationSpec = FastEffects.floatSpec,
                targetScale = 0.98f
            )
        return enter togetherWith exit
    }

    /**
     * EMPHASIZED workspace-entry composition (Project Picker -> Project
     * Workspace and back): slightly deeper travel (1/4 width) and scale range
     * than routine tabs because workspace entry is an approved high-value
     * moment, while remaining deterministic (tween-paced, no bounce).
     * Reduced motion degrades to the minimal fade pair.
     */
    const val WorkspaceEntryDurationMillis = DurationEmphasized
    fun workspaceEntryTransform(
        direction: NavigationDirection,
        reduceMotion: Boolean
    ): ContentTransform {
        if (reduceMotion) return tabReducedMotionTransition
        val sign = if (direction == NavigationDirection.Forward) 1 else -1
        val enter = androidx.compose.animation.fadeIn(animationSpec = DefaultEffects.floatSpec) +
            androidx.compose.animation.slideInHorizontally(
                animationSpec = StandardSpatial.offsetSpec,
                initialOffsetX = { sign * it / 4 }
            ) +
            androidx.compose.animation.scaleIn(
                animationSpec = StandardSpatial.floatSpec,
                initialScale = 0.97f
            )
        val exit = androidx.compose.animation.fadeOut(animationSpec = FastEffects.floatSpec) +
            androidx.compose.animation.slideOutHorizontally(
                animationSpec = FastSpatial.offsetSpec,
                targetOffsetX = { -sign * it / 4 }
            )
        return enter togetherWith exit
    }

    // ── LEGACY TRANSITIONS (existing MainLayout call sites) ────────────────
    // Slice 8B replaces these with directional shared-axis builders; kept so
    // current call sites compile unchanged until migrated.

    // Reduced motion transition: minimal crossfade without slide offsets
    val tabReducedMotionTransition: ContentTransform =
        androidx.compose.animation.fadeIn(animationSpec = ReducedMotion.floatSpec) togetherWith
        androidx.compose.animation.fadeOut(animationSpec = ReducedMotion.floatSpec)
}
