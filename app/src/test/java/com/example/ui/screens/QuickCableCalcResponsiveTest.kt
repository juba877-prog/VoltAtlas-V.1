package com.example.ui.screens

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.ui.theme.VoltAtlasTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Phase 3E — QuickCableCalcScreen responsive coverage (structure/semantics,
 * no screenshot goldens).
 *
 * The scratchpad is built from repeated two-field rows. Under narrow width or
 * enlarged font scale each pair must stack (preserving field order and
 * therefore the label/value association) instead of leaving both fields too
 * narrow for their engineering labels.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w320dp-h800dp-xhdpi")
class QuickCableCalcResponsiveTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun render(fontScale: Float) {
        composeTestRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale = fontScale)
            ) {
                VoltAtlasTheme(darkTheme = false) {
                    QuickCableCalcScreen(onBack = {})
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun narrowWidth_fsc1_5_fieldPairsStackAndKeepFieldWidth() {
        render(fontScale = 1.5f)

        // Tagged fields keep their contracts and gain full row width when
        // stacked (>= half of a 320dp content area).
        val loadField = composeTestRule.onNodeWithTag("input_load_kva")
            .fetchSemanticsNode().boundsInRoot
        val voltageField = composeTestRule.onNodeWithTag("input_voltage")
            .fetchSemanticsNode().boundsInRoot

        val contentWidthPx = with(composeTestRule.density) { 288.dp.toPx() }
        assertTrue(
            "stacked field must use the full row (width=${loadField.width})",
            loadField.width >= contentWidthPx * 0.8f
        )
        // Stacked => second field sits BELOW the first (order preserved).
        assertTrue(
            "field order must be preserved when stacked",
            voltageField.top >= loadField.bottom - 2f
        )
        // Labels remain in the semantics for both fields.
        composeTestRule.onNodeWithText("Load (kVA)").assertExists()
        composeTestRule.onNodeWithText("Voltage (V)").assertExists()
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xhdpi")
class QuickCableCalcInlineRegressionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun normalPhoneWidth_fsc1_0_fieldPairsStayInline() {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = false) {
                QuickCableCalcScreen(onBack = {})
            }
        }
        composeTestRule.waitForIdle()

        val loadField = composeTestRule.onNodeWithTag("input_load_kva")
            .fetchSemanticsNode().boundsInRoot
        val voltageField = composeTestRule.onNodeWithTag("input_voltage")
            .fetchSemanticsNode().boundsInRoot

        // Inline contract at fontScale 1.0 on a normal phone window: the two
        // fields share one row (same vertical band, first left of second).
        assertTrue(
            "fields must share a row at fsc 1.0",
            kotlin.math.abs(loadField.top - voltageField.top) <= 4f
        )
        assertTrue(voltageField.left >= loadField.right - 2f)
    }
}
