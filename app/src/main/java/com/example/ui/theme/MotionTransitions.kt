package com.example.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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