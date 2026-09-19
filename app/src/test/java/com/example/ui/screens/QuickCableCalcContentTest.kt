package com.example.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.example.data.CircuitEntity
import com.example.engine.coordinator.CableCalculationService
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
 * Pure contract tests for QuickCableCalcContent composable (Seam 3) under compose-state-and-effects.
 *
 * Verifies that the plain content composable:
 * 1. Renders pure UI without depending on Android platform services (Context/Toast/Clipboard).
 * 2. Properly displays state-driven labels, inputs, and calculated results.
 * 3. Delivers event callbacks for user interactions (back button, copy action, input change).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xhdpi")
class QuickCableCalcContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun quickCableCalcContent_rendersWithoutPlatformDependencies_andDeliversCallbacks() {
        var backClicked = false
        var copyClicked = false
        var updatedLoadKva: String? = null

        val dummyCircuit = CircuitEntity(
            id = 1,
            projectId = "QUICK_CALC",
            circuitName = "Quick Feeder",
            loadKVA = 50.0,
            voltageV = 400,
            phases = "3P+N",
            pf = 0.80,
            safetyFactor = 1.00,
            length_m = 50.0,
            maxVDpct = 0.03,
            parallelRuns = 1,
            feederType = "Cable",
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            installation = "Laid in Free Air",
            airTempC = 40,
            gndTempC = 35,
            burialDepth_m = 0.8,
            soilRes = 120,
            noCircuits = 1,
            formation = "Flat",
            spacing = "Touching",
            fedFrom = "Grid",
            transformerId = ""
        )

        val dummyResult = CableCalculationService.calculateSingleCircuit(
            circuit = dummyCircuit,
            parentCumulativeVD = 0.0,
            upstreamCircuits = emptyList(),
            transformers = emptyList()
        )

        val state = CableCalcScreenUiState(
            loadKvaText = "50.0",
            voltageText = "400",
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            calculationResult = dummyResult,
            calculatedCircuit = dummyCircuit
        )

        composeTestRule.setContent {
            VoltAtlasTheme {
                QuickCableCalcContent(
                    uiState = state,
                    onLoadKvaChange = { updatedLoadKva = it },
                    onVoltageChange = {},
                    onPhasesChange = {},
                    onPfChange = {},
                    onSafetyFactorChange = {},
                    onLengthChange = {},
                    onMaxVdPctChange = {},
                    onParallelRunsChange = {},
                    onManufacturerChange = {},
                    onCableTypeChange = {},
                    onInstallationChange = {},
                    onAirTempChange = {},
                    onGndTempChange = {},
                    onBurialDepthChange = {},
                    onSoilResChange = {},
                    onNoCircuitsChange = {},
                    onFormationChange = {},
                    onSpacingChange = {},
                    onToggleOverrides = {},
                    onOverrideDeviceTypeChange = {},
                    onOverrideDeviceRatingChange = {},
                    onOverrideCableSizeChange = {},
                    onOverrideNeutralSizeChange = {},
                    onOverrideEarthSizeChange = {},
                    onCalculate = {},
                    onBack = { backClicked = true },
                    onCopySummary = { copyClicked = true }
                )
            }
        }
        composeTestRule.waitForIdle()

        // 1. Verify TopBar and Headings render
        composeTestRule.onNodeWithText("Quick Cable Sizing").assertIsDisplayed()
        composeTestRule.onNodeWithText("Stateless Scratchpad (In-Memory)").assertIsDisplayed()

        // 2. Verify back button callback
        composeTestRule.onNodeWithTag("quick_calc_back_btn").assertIsDisplayed().performClick()
        assertTrue("onBack must be invoked when back button is clicked", backClicked)

        // 3. Verify copy summary button callback
        composeTestRule.onNodeWithTag("copy_summary_btn").assertIsDisplayed().performClick()
        assertTrue("onCopySummary must be invoked when copy button is clicked", copyClicked)

        // 4. Verify input value change callback
        composeTestRule.onNodeWithTag("input_load_kva").assertIsDisplayed().performTextReplacement("75.0")
        assertEquals("75.0", updatedLoadKva)
    }
}
