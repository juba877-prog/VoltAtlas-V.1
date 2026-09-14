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
 * Remembers the current semantic window-width class.
 *
 * Implementation deliberately uses [LocalConfiguration] so it stays
 * compatible with the repository's current Compose BOM (2024.09.00) without
 * adding a new adaptive/window-size-class dependency. The value is keyed on
 * the configuration so it updates on rotation, split-window resize, and
 * foldable posture changes.
 */
@Composable
fun rememberVoltAtlasWindowWidth(): VoltAtlasWindowWidth {
    val configuration = LocalConfiguration.current
    return remember(configuration) {
        val widthDp = configuration.screenWidthDp
        when {
            widthDp >= 840 -> VoltAtlasWindowWidth.Expanded
            widthDp >= 600 -> VoltAtlasWindowWidth.Medium
            else -> VoltAtlasWindowWidth.Compact
        }
    }
}
