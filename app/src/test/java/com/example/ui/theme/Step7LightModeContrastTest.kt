package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Step 7 (Light-Mode Correction) regression proof.
 *
 * Resolves the REAL production color getters (the same composable tokens the
 * Dashboard / More Modules / Transformer / Circuit screens read) under both
 * VoltAtlasTheme modes and verifies WCAG 2.x contrast for every content pair
 * called out by the approved visual direction:
 *
 *  - Dashboard Assistant CTA title / body
 *  - Project Health status (PASS / WARNING / FAIL, plain + tinted badge)
 *  - More Modules title / subtitle / affordance arrow
 *  - Transformer heading / label+value content
 *  - Circuit search placeholder / content
 *  - Disabled / de-emphasised content remains distinguishable (never disappears)
 *  - Dark Mode pixels are pinned to the legacy constants (no dark regressions)
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class Step7LightModeContrastTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ---------- WCAG 2.x relative luminance / contrast ratio ----------
    private fun linearize(channel: Float): Double {
        val c = channel.toDouble()
        return if (c <= 0.04045) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
    }

    private fun relativeLuminance(color: Color): Double =
        0.2126 * linearize(color.red) + 0.7152 * linearize(color.green) + 0.0722 * linearize(color.blue)

    private fun contrastRatio(fg: Color, bg: Color): Double {
        val l1 = relativeLuminance(fg)
        val l2 = relativeLuminance(bg)
        return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
    }

    /** Composite of a translucent foreground over its container (badge/chip tints). */
    private fun blend(fg: Color, bg: Color, alpha: Float): Color = Color(
        red = fg.red * alpha + bg.red * (1 - alpha),
        green = fg.green * alpha + bg.green * (1 - alpha),
        blue = fg.blue * alpha + bg.blue * (1 - alpha),
        alpha = 1f
    )

    private data class Resolved(
        val textPrimary: Color,
        val textSecondary: Color,
        val textTertiary: Color,
        val textDisabled: Color,
        val surface: Color,
        val surfaceContainer: Color,
        val onSurfaceVariant: Color,
        val pass: Color,
        val warning: Color,
        val fail: Color,
        val primary: Color
    )

    private var dark: Resolved? = null
    private var light: Resolved? = null

    private fun probeBothModes() {
        composeTestRule.setContent {
            @Composable
            fun resolve(): Resolved = Resolved(
                textPrimary = ThemeTextPrimary,
                textSecondary = ThemeTextSecondary,
                textTertiary = ThemeTextTertiary,
                textDisabled = ThemeTextDisabled,
                surface = MaterialTheme.colorScheme.surface,
                surfaceContainer = MaterialTheme.colorScheme.surfaceContainer,
                onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant,
                pass = ThemeStatusPass,
                warning = ThemeStatusWarning,
                fail = ThemeStatusFail,
                primary = ThemePrimary
            )
            VoltAtlasTheme(darkTheme = true) { dark = resolve() }
            VoltAtlasTheme(darkTheme = false) { light = resolve() }
        }
        composeTestRule.waitForIdle()
    }

    private fun assertAa(fg: Color, bg: Color, label: String, min: Double = 4.5) {
        val ratio = contrastRatio(fg, bg)
        assertTrue("$label contrast $ratio must be >= $min", ratio >= min)
    }

    // ---------- 1. Dashboard Assistant CTA ----------
    @Test
    fun dashboardAssistantCta_titleAndBody_meetWcagAa_onLightSurfaces() {
        probeBothModes()
        val l = requireNotNull(light)
        assertAa(l.textPrimary, l.surface, "Assistant CTA title (light)")
        assertAa(l.textPrimary, l.surfaceContainer, "Assistant CTA title (light, surfaceContainer)")
        assertAa(l.textSecondary, l.surface, "Assistant CTA body (light)")
        assertAa(l.textSecondary, l.surfaceContainer, "Assistant CTA body (light, surfaceContainer)")
    }

    // ---------- 2. Project Health status ----------
    @Test
    fun projectHealth_passWarnFail_meetWcagAa_inBothModes_plainSurface() {
        probeBothModes()
        for ((mode, r) in listOf("dark" to requireNotNull(dark), "light" to requireNotNull(light))) {
            assertAa(r.pass, r.surface, "PASS status ($mode)")
            assertAa(r.warning, r.surface, "WARNING status ($mode)")
            assertAa(r.fail, r.surface, "FAIL status ($mode)")
        }
    }

    @Test
    fun projectHealth_passWarnFail_meetWcagAa_onTintedBadgeBackgrounds() {
        probeBothModes()
        // VoltAtlasStatusBadge draws fg.copy(alpha = 0.15f) behind the text.
        // LIGHT mode (the correction target): full WCAG AA >= 4.5.
        val l = requireNotNull(light)
        assertAa(l.pass, blend(l.pass, l.surfaceContainer, 0.15f), "PASS badge (light)")
        assertAa(l.warning, blend(l.warning, l.surfaceContainer, 0.15f), "WARNING badge (light)")
        assertAa(l.fail, blend(l.fail, l.surfaceContainer, 0.15f), "FAIL badge (light)")
        // DARK mode: pixels are intentionally preserved; legacy combos must keep
        // the bold-status/UI component floor (>= 3.0).
        val d = requireNotNull(dark)
        assertAa(d.pass, blend(d.pass, d.surfaceContainer, 0.15f), "PASS badge (dark)", min = 3.0)
        assertAa(d.warning, blend(d.warning, d.surfaceContainer, 0.15f), "WARNING badge (dark)", min = 3.0)
        assertAa(d.fail, blend(d.fail, d.surfaceContainer, 0.15f), "FAIL badge (dark)", min = 3.0)
    }

    // ---------- 3. More Modules ----------
    @Test
    fun moreModules_titleSubtitleArrow_meetWcagAa_inLightMode() {
        probeBothModes()
        val l = requireNotNull(light)
        assertAa(l.textPrimary, l.surface, "More Modules title (light)")
        assertAa(l.textSecondary, l.surface, "More Modules subtitle (light)")
        // Interactive affordance arrows must remain clearly visible (UI component floor).
        assertAa(l.textSecondary, l.surfaceContainer, "More Modules arrow (light)", min = 3.0)
        assertAa(l.textTertiary, l.surfaceContainer, "Module card arrow (light)", min = 3.0)
    }

    // ---------- 4. Transformer heading / content ----------
    @Test
    fun transformer_headingAndContent_meetWcagAa_onSurfaceContainers() {
        probeBothModes()
        for ((mode, r) in listOf("dark" to requireNotNull(dark), "light" to requireNotNull(light))) {
            assertAa(r.primary, r.surfaceContainer, "Transformer card heading accent ($mode)")
            assertAa(r.textPrimary, r.surfaceContainer, "Transformer values/heading ($mode)")
            assertAa(r.textSecondary, r.surfaceContainer, "Transformer labels ($mode)")
        }
    }

    // ---------- 5. Circuit search field ----------
    @Test
    fun circuitSearch_placeholderAndContent_meetWcagAa_inBothModes() {
        probeBothModes()
        for ((mode, r) in listOf("dark" to requireNotNull(dark), "light" to requireNotNull(light))) {
            assertAa(r.onSurfaceVariant, r.surfaceContainer, "Search placeholder ($mode)")
            assertAa(r.textPrimary, r.surfaceContainer, "Search typed content ($mode)")
        }
    }

    // ---------- 6. Disabled content stays distinguishable ----------
    @Test
    fun disabledContent_remainsDistinguishable_andBelowSecondaryInLightMode() {
        probeBothModes()
        val l = requireNotNull(light)
        val disabledRatio = contrastRatio(l.textDisabled, l.surface)
        assertTrue(
            "Disabled text ($disabledRatio) must stay visible on light surfaces (>= 2.5)",
            disabledRatio >= 2.5
        )
        // Hierarchy: enabled secondary text must still be strictly stronger than disabled.
        assertTrue(
            "Hierarchy broken: disabled luminance ${relativeLuminance(l.textDisabled)} " +
                "must exceed secondary ${relativeLuminance(l.textSecondary)}",
            relativeLuminance(l.textDisabled) > relativeLuminance(l.textSecondary)
        )
    }

    // ---------- 7. Dark Mode pixel preservation ----------
    @Test
    fun darkMode_resolvesLegacyPixels_exactly() {
        probeBothModes()
        val d = requireNotNull(dark)
        assertEquals(TextPrimary, d.textPrimary)
        assertEquals(TextSecondary, d.textSecondary)
        assertEquals(TextLow, d.textTertiary)
        assertEquals(Surface, d.surface)
        assertEquals(StatusPass, d.pass)
        assertEquals(StatusWarning, d.warning)
        assertEquals(StatusFail, d.fail)
        assertEquals(SleekPrimary, d.primary)
    }

    // ---------- 8. Light brand blue is deepened & saturated ----------
    @Test
    fun lightBrandBlue_isDeepSaturatedToken() {
        probeBothModes()
        val l = requireNotNull(light)
        assertEquals(Color(0xFF0369A1), l.primary)
        assertAa(l.primary, l.surface, "Light brand blue as foreground")
    }
}
