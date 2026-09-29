package com.example.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration

/**
 * Centralized window-width policy for VoltAtlas major screen decisions.
 *
 * The classes are a *starting policy* (aligned with the Material 3 window
 * size classes), not device categories: they are derived from the currently
 * available window width, so split-window and resize conditions follow the
 * same rules without orientation checks.
 *
 * Major layout choices (rail vs bottom navigation, one-pane vs multi-column
 * compositions) must come from this policy instead of scattered
 * `screenWidthDp >= 600` / orientation checks.
 *
 * Component-local content breakpoints (e.g. `maxWidth < 350.dp` inside a
 * card) remain legitimate and stay component-local; this enum is only for
 * window-level decisions.
 */
enum class VoltAtlasWindowWidth {
    /** Phone-like / narrow app window (`< 600dp`). */
    Compact,

    /** Tablet or wide split window (`600–839dp`). */
    Medium,

    /** Large tablet / desktop-like width (`>= 840dp`). */
    Expanded;

    /** True when the window supports a side navigation rail composition. */
    val supportsNavRail: Boolean
        get() = this != Compact
}

/**
 * Window width (dp) at which the shell switches from bottom navigation to a navigation
 * rail. Shared with the adaptive Navigation 3 scene (More-modules list-detail) so the
 * navigation chrome and the workspace layout switch at the same breakpoint.
 */
const val VoltAtlasNavRailMinWidthDp: Int = 600

/** Window width (dp) at which desktop-like (expanded) compositions start. */
const val VoltAtlasExpandedMinWidthDp: Int = 840

/** Classifies a raw window width (dp) with the shared [VoltAtlasWindowWidth] policy. */
fun voltAtlasWindowWidthFor(widthDp: Int): VoltAtlasWindowWidth = when {
    widthDp >= VoltAtlasExpandedMinWidthDp -> VoltAtlasWindowWidth.Expanded
    widthDp >= VoltAtlasNavRailMinWidthDp -> VoltAtlasWindowWidth.Medium
    else -> VoltAtlasWindowWidth.Compact
}

/**
 * Remembers the current semantic window-width class.
 *
 * Implementation deliberately uses [LocalConfiguration] so it stays
 * compatible with the repository's current Compose BOM (2024.09.00) without
 * adding a new adaptive/window-size-class dependency. The value is keyed on
 * the configuration, so it follows whatever the currently available
 * window/configuration width is: rotation, split-window resize, and foldable
 * states are all reflected through a changed window width. The helper does
 * NOT observe foldable posture/folding features directly (no hinge/posture
 * API is used), and for window-level composition decisions it does not
 * need to — this is "respond to the current available window width", not
 * "be posture aware".
 */
@Composable
fun rememberVoltAtlasWindowWidth(): VoltAtlasWindowWidth {
    val configuration = LocalConfiguration.current
    return remember(configuration) {
        voltAtlasWindowWidthFor(configuration.screenWidthDp)
    }
}
