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
 *
 * ## Horizontal-scroll policy (project rule)
 *
 * Horizontal scrolling IS acceptable for genuinely wide/tabular data
 * (e.g. schedules, SLD/diagram surfaces) and intentional horizontal
 * selectors/chip rows where browsing off-screen content is the natural
 * interaction. Horizontal scrolling must NOT be added as a repair for a
 * normal form or a broken Row when a Row -> Column / stacked reflow would
 * solve it, and must never make engineering values inaccessible without
 * scrolling. Existing `horizontalScroll` usages must be inspected against
 * this rule during future screen audits — legitimate ones are kept.
 */
fun BoxWithConstraintsScope.isTooNarrowForInline(requiredInlineWidth: Dp): Boolean =
    maxWidth < requiredInlineWidth

/** Inverse of [isTooNarrowForInline] for positive-condition readability. */
fun BoxWithConstraintsScope.supportsInline(requiredInlineWidth: Dp): Boolean =
    maxWidth >= requiredInlineWidth
