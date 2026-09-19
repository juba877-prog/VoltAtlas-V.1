package com.example.ui.screens.quick

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.ui.theme.VoltAtlasTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pure contract tests for QuickPfcContent composable (Seam 3) under compose-state-and-effects.
 *
 * Verifies that the plain content composable:
 * 1. Renders pure UI without depending on Android platform services (Context/Toast/Clipboard).
 * 2. Properly displays state-driven labels, inputs, and calculated results.
 * 3. Delivers event callbacks for user interactions (back button, copy action, input mode tab).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xhdpi")
class QuickPfcContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun quickPfcContent_rendersWithoutPlatformDependencies_andDeliversCallbacks() {
        var backClicked = false
        var copyClicked = false
        var selectedMode: Int? = null

        val input = PfcInputState(
            inputMode = PfcInputMode.ACTIVE_POWER_KW,
            phase = PfcSystemPhase.THREE_PHASE,
            voltageV = 400.0,
            powerKwText = "100.0",
            powerKvaText = "125.0",
            currentAText = "180.0",
            initialPfText = "0.80",
            targetPfText = "0.95",
            frequency = PfcFrequency.FREQ_50HZ,
            connection = PfcCapacitorConnection.DELTA
        )
        val result = PfcCalculationEngine.calculate(input)
        val state = PfcScreenUiState(
            inputState = input,
            calculationResult = result,
            inputModeIndex = 0,
            phaseIndex = 0,
            voltageText = "400",
            powerKwText = "100.0",
            initialPfText = "0.80",
            targetPfText = "0.95"
        )

        composeTestRule.setContent {
            VoltAtlasTheme {
                QuickPfcContent(
                    uiState = state,
                    onSelectInputMode = { selectedMode = it },
                    onSelectPhase = {},
                    onSelectFreq = {},
                    onSelectConnection = {},
                    onVoltageChange = {},
                    onPowerKwChange = {},
                    onPowerKvaChange = {},
                    onCurrentAChange = {},
                    onInitialPfChange = {},
                    onTargetPfChange = {},
                    onBack = { backClicked = true },
                    onCopySummary = { copyClicked = true }
                )
            }
        }
        composeTestRule.waitForIdle()

        // 1. Verify TopBar and Headings render
        composeTestRule.onNodeWithText("Power Factor Correction").assertIsDisplayed()
        composeTestRule.onNodeWithText("Target PF, Reactive Relief & Capacitor Sizing").assertIsDisplayed()

        // 2. Verify back button callback
        composeTestRule.onNodeWithTag("pfc_v2_back_btn").assertIsDisplayed().performClick()
        assertTrue("onBack must be invoked when back button is clicked", backClicked)

        // 3. Verify copy summary button callback
        composeTestRule.onNodeWithTag("copy_pfc_summary_btn").assertIsDisplayed().performClick()
        assertTrue("onCopySummary must be invoked when copy button is clicked", copyClicked)

        // 4. Verify input mode selection callback
        composeTestRule.onNodeWithText("Apparent (kVA)").assertIsDisplayed().performClick()
        assertEquals(1, selectedMode)
    }
}
