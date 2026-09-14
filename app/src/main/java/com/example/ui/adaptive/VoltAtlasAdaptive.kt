package com.example.ui.adaptive

import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.ui.unit.Dp

/**
 * Component-local content-breakpoint helpers.
 *
 * These describe *content requirements* (how much width a semantic group
 * needs to stay readable in one row), never device identities. Use them
 * inside `BoxWithConstraints` when a component must reflow independently of
 * the window class (e.g. a card inside a two-pane tablet layout).
 *
 * Usage:
 * ```
 * BoxWithConstraints(Modifier.fillMaxWidth()) {
 *     if (isTooNarrowForInline(requiredInlineWidth = MetricRowInlineWidth)) {
 *         Column { /* stacked metric cells */ }
 *     } else {
 *         Row { /* inline metric cells */ }
 *     }
 * }
 * ```
 */
fun BoxWithConstraintsScope.isTooNarrowForInline(requiredInlineWidth: Dp): Boolean =
    maxWidth < requiredInlineWidth

/** Inverse of [isTooNarrowForInline] for positive-condition readability. */
fun BoxWithConstraintsScope.supportsInline(requiredInlineWidth: Dp): Boolean =
    maxWidth >= requiredInlineWidth
