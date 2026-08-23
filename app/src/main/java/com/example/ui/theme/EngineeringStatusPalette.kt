package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Engineering status palette (Step 7, Commit 3).
 *
 * Semantic engineering colors decoupled from Material role slots. The Material
 * `tertiary` quartet has been freed to neutral slates; any component that needs
 * a *status* meaning must read from here, so a future Material-role change can
 * never silently re-color a pass / warn / fail signal.
 *
 * All constants are pinned by ThemeRoleSeparationTest.
 */
object EngineeringStatusPalette {
    // Core engineering status colors.
    val Pass = Color(0xFF3DD68C)
    val Warning = Color(0xFFF5A623)
    val Fail = Color(0xFFF0554C)
    val Neutral = Color(0xFF94A3B8)
    val Missing = Color(0xFF6B7684)

    // Scenario (What-If / WARNING badge / partial receipt / CLEAR badge) container pair.
    // Pixel-identical to the legacy tertiaryContainer values that these surfaces
    // rendered before the Material tertiary slot was freed.
    val ScenarioContainerDark = Color(0xFF0F382A)
    val ScenarioContainerLight = Color(0xFFD1F2E2)

    val ScenarioContainer: Color
        @Composable get() = if (LocalThemeIsDark.current) ScenarioContainerDark else ScenarioContainerLight

    /**
     * Foreground for [ScenarioContainer].
     *
     * Dark theme keeps the legacy pixel (Pass green on #0F382A ≈ 6.9:1 WCAG contrast).
     * Light theme MUST NOT use Pass on #D1F2E2 (≈ 1.56:1 - fails WCAG AA badly),
     * so it uses a deep green (#14532D ≈ 7.6:1) instead. Verified in
     * ThemeRoleSeparationTest; never assume Pass is a valid foreground on every
     * container.
     */
    val OnScenarioContainerLight = Color(0xFF14532D)

    val OnScenarioContainer: Color
        @Composable get() = if (LocalThemeIsDark.current) Pass else OnScenarioContainerLight
}
