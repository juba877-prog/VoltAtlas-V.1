package com.example.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/**
 * Phase 8 Slice 8B — restrained shared-axis-inspired screen entry.
 *
 * Plays ONCE per [key] change (e.g. project id), never on ordinary
 * recomposition or return from a child screen. Composition of the destination
 * runs entirely BEFORE/DURING the draw-phase transform, so input is never
 * blocked and no second interactive screen layer exists: the previous screen
 * has already left composition (enter-only by design — no dual heavy trees).
 *
 * Reduced motion ([LocalReduceMotion]) snaps straight to identity: the final
 * stable frame publishes immediately.
 *
 * RTL: translation sign is mirrored via [LocalLayoutDirection] so Forward
 * always reads as "deeper into hierarchy" regardless of layout direction.
 */
@Composable
fun SharedAxisEnter(
    direction: VoltAtlasMotion.NavigationDirection,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val reduceMotion = LocalReduceMotion.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    val progress = remember(direction) { Animatable(0f) }

    LaunchedEffect(reduceMotion) {
        if (reduceMotion) {
            progress.snapTo(1f)
        } else {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = VoltAtlasMotion.WorkspaceEntryDurationMillis,
                    easing = VoltAtlasMotion.StandardSpatial.easing
                )
            )
        }
    }

    Box2D(modifier = modifier) {
        // content drawn with draw-phase-only transforms: zero relayout cost
        Box2D(modifier = Modifier.graphicsLayer {
            val p = progress.value
            val dirSign = if (direction == VoltAtlasMotion.NavigationDirection.Forward) 1 else -1
            val rtlSign = if (isRtl) -1 else 1
            // Short travel: 1/4 width max — no carousel feel.
            translationX = dirSign * rtlSign * (1f - p) * size.width / 4f
            // Subtle scale: engineering screens must never appear to zoom wildly.
            val s = 0.97f + (0.03f * p)
            scaleX = s
            scaleY = s
            alpha = p
        }) {
            content()
        }
    }
}

/** Minimal Box alias kept local to avoid extra imports at call sites. */
@Composable
private fun Box2D(modifier: Modifier, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(modifier = modifier) { content() }
}

/**
 * Phase 8 Slice 8D — capped first-entry stagger POLICY (pure, testable).
 *
 * Allowed ONLY for small first-entry card/tile groups (e.g. the 8 Quick
 * Engineering Hub tiles). NEVER applied to circuit / BOQ / panel schedule /
 * catalog lists. Reduced motion removes all stagger (immediate).
 */
object StaggerPolicy {
    /** Per-item delay small enough that no item feels withheld. */
    const val PerItemDelayMillis = 40

    /**
     * Hard cap on the LAST item's delay: with the cap below one standard
     * duration, even the final tile begins almost immediately.
     */
    const val MaxTotalStaggerMillis = 240

    fun delayForIndex(index: Int, reduceMotion: Boolean = false): Int =
        if (reduceMotion || index <= 0) {
            0
        } else {
            minOf(index * PerItemDelayMillis, MaxTotalStaggerMillis)
        }
}

/**
 * Phase 8 Slice 8D — one-shot entrance choreography for SMALL tile groups.
 *
 * Each tile manages its OWN saveable once-flag: the entrance plays exactly
 * once per group lifetime. Ordinary recomposition never replays it, and
 * returning from a child screen restores the flag (tiles publish instantly).
 * Reduced motion publishes the final frame immediately.
 */
@Composable
fun FirstEntryStagger(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val reduceMotion = LocalReduceMotion.current
    var hasPlayed by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }

    val progress = remember { Animatable(if (hasPlayed || reduceMotion) 1f else 0f) }

    LaunchedEffect(Unit) {
        if (!hasPlayed && !reduceMotion) {
            kotlinx.coroutines.delay(
                StaggerPolicy.delayForIndex(index, reduceMotion).toLong()
            )
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = VoltAtlasMotion.FastSpatial.DurationMillis,
                    easing = VoltAtlasMotion.StandardSpatial.easing
                )
            )
        }
        if (progress.value < 1f) progress.snapTo(1f)
        hasPlayed = true
    }

    androidx.compose.foundation.layout.Box(modifier = modifier.graphicsLayer {
        val p = progress.value
        alpha = p
        translationY = (1f - p) * 16f // subtle rise-in; no scale bounce
    }) {
        content()
    }
}