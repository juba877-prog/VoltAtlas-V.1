package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Step 7 / Commit 3 regression proof:
 *  1. The Material `tertiary` quartet is freed from green and pinned to the new
 *     neutral slate values in BOTH schemes - a rendered MaterialTheme can never
 *     silently re-introduce engineering semantics through a tertiary role.
 *  2. EngineeringStatusPalette constants are value-pinned.
 *  3. Amendment 5 - real WCAG contrast verification for every newly assigned
 *     tertiary pair and engineering status foreground/container combination.
 *     Pass is explicitly proven NOT to be a valid foreground on the light
 *     scenario container (hence the theme-aware OnScenarioContainer).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class ThemeRoleSeparationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ---------- WCAG 2.x relative luminance / contrast ratio ----------
    private fun linearize(channel: Float): Float {
        val c = channel / 255f
        return if (c <= 0.04045f) c / 12.92f else Math.pow(((c + 0.055) / 1.055).toDouble(), 2.4).toFloat()
    }

    private fun relativeLuminance(color: Long): Double {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return (0.2126 * linearize(r.toFloat()) +
            0.7152 * linearize(g.toFloat()) +
            0.0722 * linearize(b.toFloat()))
    }

    private fun contrastRatio(fg: Long, bg: Long): Double {
        val l1 = relativeLuminance(fg)
        val l2 = relativeLuminance(bg)
        return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
    }

    private fun argb(color: androidx.compose.ui.graphics.Color): Long =
        color.toArgb().toLong() and 0xFFFFFFFFL

    // ---------- rendering probe ----------
    private var capturedScheme: ColorScheme? = null

    private fun probeScheme(dark: Boolean): ColorScheme {
        capturedScheme = null
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = dark) {
                capturedScheme = MaterialTheme.colorScheme
            }
        }
        composeTestRule.waitForIdle()
        return requireNotNull(capturedScheme) { "colorScheme was not captured" }
    }

    /** Captures BOTH schemes from one composition (setContent may run only once per test). */
    private fun probeBothSchemes(): Pair<ColorScheme, ColorScheme> {
        var darkScheme: ColorScheme? = null
        var lightScheme: ColorScheme? = null
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) { darkScheme = MaterialTheme.colorScheme }
            VoltAtlasTheme(darkTheme = false) { lightScheme = MaterialTheme.colorScheme }
        }
        composeTestRule.waitForIdle()
        return Pair(
            requireNotNull(darkScheme) { "dark scheme was not captured" },
            requireNotNull(lightScheme) { "light scheme was not captured" }
        )
    }

    // ---------- 1. palette value pins ----------
    @Test
    fun engineeringStatusPalette_constantsArePinned() {
        assertEquals(0xFF3DD68CL, argb(EngineeringStatusPalette.Pass))
        assertEquals(0xFFF5A623L, argb(EngineeringStatusPalette.Warning))
        assertEquals(0xFFF0554CL, argb(EngineeringStatusPalette.Fail))
        assertEquals(0xFF94A3B8L, argb(EngineeringStatusPalette.Neutral))
        assertEquals(0xFF6B7684L, argb(EngineeringStatusPalette.Missing))
        assertEquals(0xFF0F382AL, argb(EngineeringStatusPalette.ScenarioContainerDark))
        assertEquals(0xFFD1F2E2L, argb(EngineeringStatusPalette.ScenarioContainerLight))
        assertEquals(0xFF14532DL, argb(EngineeringStatusPalette.OnScenarioContainerLight))
    }

    // ---------- 2. role separation + new tertiary pins ----------
    @Test
    fun darkTertiaryQuartet_isNeutralSlate_neverGreen() {
        val s = probeScheme(dark = true)
        assertEquals(0xFF94A3B8L, argb(s.tertiary))
        assertEquals(0xFF0D1117L, argb(s.onTertiary))
        assertEquals(0xFF252E3DL, argb(s.tertiaryContainer))
        assertEquals(0xFFAEB6C2L, argb(s.onTertiaryContainer))
        assertNotEquals(EngineeringStatusPalette.Pass, s.tertiary)
        assertNotEquals(EngineeringStatusPalette.Pass, s.tertiaryContainer)
        assertNotEquals(EngineeringStatusPalette.Pass, s.onTertiaryContainer)
        assertNotEquals(EngineeringStatusPalette.ScenarioContainerDark, s.tertiaryContainer)
    }

    @Test
    fun lightTertiaryQuartet_isNeutralSlate_neverGreen() {
        val s = probeScheme(dark = false)
        assertEquals(0xFF64748BL, argb(s.tertiary))
        assertEquals(0xFFFFFFFFL, argb(s.onTertiary))
        assertEquals(0xFFE2E8F0L, argb(s.tertiaryContainer))
        assertEquals(0xFF334155L, argb(s.onTertiaryContainer))
        assertNotEquals(EngineeringStatusPalette.Pass, s.tertiary)
        assertNotEquals(EngineeringStatusPalette.Pass, s.tertiaryContainer)
        assertNotEquals(EngineeringStatusPalette.Pass, s.onTertiaryContainer)
        assertNotEquals(EngineeringStatusPalette.ScenarioContainerLight, s.tertiaryContainer)
    }

    // ---------- 3. Amendment 5: WCAG contrast verification ----------
    @Test
    fun newlyAssignedTertiaryPairs_meetWcagAa_inBothThemes() {
        val (dark, light) = probeBothSchemes()

        fun hex(c: androidx.compose.ui.graphics.Color): Long = c.toArgb().toLong() and 0xFFFFFFFFL

        assertTrue(
            "dark tertiary/onTertiary contrast ${contrastRatio(hex(dark.onTertiary), hex(dark.tertiary))} must be >= 4.5",
            contrastRatio(hex(dark.onTertiary), hex(dark.tertiary)) >= 4.5
        )
        assertTrue(
            "dark tertiaryContainer/onTertiaryContainer contrast " +
                contrastRatio(hex(dark.onTertiaryContainer), hex(dark.tertiaryContainer)) + " must be >= 4.5",
            contrastRatio(hex(dark.onTertiaryContainer), hex(dark.tertiaryContainer)) >= 4.5
        )
        assertTrue(
            "light tertiary/onTertiary contrast ${contrastRatio(hex(light.onTertiary), hex(light.tertiary))} must be >= 4.5",
            contrastRatio(hex(light.onTertiary), hex(light.tertiary)) >= 4.5
        )
        assertTrue(
            "light tertiaryContainer/onTertiaryContainer contrast " +
                contrastRatio(hex(light.onTertiaryContainer), hex(light.tertiaryContainer)) + " must be >= 4.5",
            contrastRatio(hex(light.onTertiaryContainer), hex(light.tertiaryContainer)) >= 4.5
        )
    }

    @Test
    fun scenarioForegroundContainer_combos_meetWcagAa() {
        val passHex = 0xFF3DD68CL
        val darkContainer = 0xFF0F382AL
        val lightContainer = 0xFFD1F2E2L
        val onLight = 0xFF14532DL

        // Dark theme combo (legacy pixels retained).
        val darkCombo = contrastRatio(passHex, darkContainer)
        assertTrue("Pass on ScenarioContainerDark ($darkCombo) must be >= 4.5", darkCombo >= 4.5)

        // Light theme combo ships the deep-green foreground.
        val lightCombo = contrastRatio(onLight, lightContainer)
        assertTrue(
            "OnScenarioContainerLight on ScenarioContainerLight ($lightCombo) must be >= 4.5",
            lightCombo >= 4.5
        )

        // Proof that Pass must NOT be assumed valid on every container:
        // raw Pass on the light container fails AA decisively.
        val naiveLight = contrastRatio(passHex, lightContainer)
        assertTrue(
            "Pass on ScenarioContainerLight ($naiveLight) is expected to FAIL AA (< 3); " +
                "this is why OnScenarioContainer is theme-aware",
            naiveLight < 3.0
        )
    }
}
