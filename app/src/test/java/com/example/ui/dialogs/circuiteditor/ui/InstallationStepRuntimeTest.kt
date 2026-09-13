package com.example.ui.dialogs.circuiteditor.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.example.ui.dialogs.circuiteditor.model.CircuitEditorDraft
import com.example.ui.dialogs.circuiteditor.ui.steps.InstallationStepContent
import com.example.ui.theme.VoltAtlasTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Runtime test suite for Step 5: Installation Conditions.
 *
 * Evidence Label: NEW_EDITOR_RUNTIME_OBSERVED
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
class InstallationStepRuntimeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun test01_installationMethodDropdownRendersOptions() {
        var currentDraft by mutableStateOf(CircuitEditorDraft(installation = "Laid in Ducts"))
        composeTestRule.setContent {
            VoltAtlasTheme {
                InstallationStepContent(
                    draft = currentDraft,
                    onDraftChange = { currentDraft = it }
                )
            }
        }

        composeTestRule.onNodeWithTag("circuit_editor_step_installation").assertIsDisplayed()
        val dropdown = composeTestRule.onNodeWithTag("input_installation_method")
        dropdown.assertIsDisplayed()

        dropdown.performClick()
        composeTestRule.onAllNodesWithText("Laid Direct in Ground").onLast().performClick()

        assertEquals("Laid Direct in Ground", currentDraft.installation)
    }

    @Test
    fun test02_airAndGroundTempFieldsUpdateDraft() {
        var currentDraft by mutableStateOf(CircuitEditorDraft(airTempC = "40", gndTempC = "40"))
        composeTestRule.setContent {
            VoltAtlasTheme {
                InstallationStepContent(
                    draft = currentDraft,
                    onDraftChange = { currentDraft = it }
                )
            }
        }

        val airField = composeTestRule.onNode(hasParent(hasTestTag("input_air_temp")) and hasSetTextAction())
        airField.performTextClearance()
        airField.performTextInput("45")

        val gndField = composeTestRule.onNode(hasParent(hasTestTag("input_gnd_temp")) and hasSetTextAction())
        gndField.performTextClearance()
        gndField.performTextInput("35")

        assertEquals("45", currentDraft.airTempC)
        assertEquals("35", currentDraft.gndTempC)
    }

    @Test
    fun test03_burialDepthAndSoilResFieldsUpdateDraft() {
        var currentDraft by mutableStateOf(CircuitEditorDraft(burialDepth_m = "0.6", soilRes = "125"))
        composeTestRule.setContent {
            VoltAtlasTheme {
                InstallationStepContent(
                    draft = currentDraft,
                    onDraftChange = { currentDraft = it }
                )
            }
        }

        val depthField = composeTestRule.onNode(hasParent(hasTestTag("input_burial_depth")) and hasSetTextAction())
        depthField.performTextClearance()
        depthField.performTextInput("0.8")

        val resField = composeTestRule.onNode(hasParent(hasTestTag("input_soil_res")) and hasSetTextAction())
        resField.performTextClearance()
        resField.performTextInput("150")

        assertEquals("0.8", currentDraft.burialDepth_m)
        assertEquals("150", currentDraft.soilRes)
    }

    @Test
    fun test04_noCircuitsFieldUpdatesDraft() {
        var currentDraft by mutableStateOf(CircuitEditorDraft(noCircuits = "1"))
        composeTestRule.setContent {
            VoltAtlasTheme {
                InstallationStepContent(
                    draft = currentDraft,
                    onDraftChange = { currentDraft = it }
                )
            }
        }

        val noCircuitsField = composeTestRule.onNode(hasParent(hasTestTag("input_no_circuits")) and hasSetTextAction())
        noCircuitsField.performTextClearance()
        noCircuitsField.performTextInput("3")

        assertEquals("3", currentDraft.noCircuits)
    }

    @Test
    fun test05_formationDropdownRendersOptions() {
        var currentDraft by mutableStateOf(CircuitEditorDraft(formation = "Flat"))
        composeTestRule.setContent {
            VoltAtlasTheme {
                InstallationStepContent(
                    draft = currentDraft,
                    onDraftChange = { currentDraft = it }
                )
            }
        }

        val formationDropdown = composeTestRule.onNodeWithTag("input_formation")
        formationDropdown.performClick()
        composeTestRule.onAllNodesWithText("Trefoil").onLast().performClick()

        assertEquals("Trefoil", currentDraft.formation)
    }

    @Test
    fun test06_spacingDropdownRendersOptions() {
        var currentDraft by mutableStateOf(CircuitEditorDraft(spacing = "Touching"))
        composeTestRule.setContent {
            VoltAtlasTheme {
                InstallationStepContent(
                    draft = currentDraft,
                    onDraftChange = { currentDraft = it }
                )
            }
        }

        val spacingDropdown = composeTestRule.onNodeWithTag("input_spacing")
        spacingDropdown.performClick()
        composeTestRule.onAllNodesWithText("0.15m").onLast().performClick()

        assertEquals("0.15m", currentDraft.spacing)
    }

    @Test
    @Config(sdk = [33], qualifiers = "w320dp-h800dp-xhdpi", fontScale = 1.5f)
    fun test07_responsiveStackingAt320dpFont1_5() {
        var currentDraft by mutableStateOf(CircuitEditorDraft())
        composeTestRule.setContent {
            VoltAtlasTheme {
                InstallationStepContent(
                    draft = currentDraft,
                    onDraftChange = { currentDraft = it }
                )
            }
        }

        val airBounds = composeTestRule.onNodeWithTag("input_air_temp").getBoundsInRoot()
        val gndBounds = composeTestRule.onNodeWithTag("input_gnd_temp").getBoundsInRoot()

        // At 320dp and font scale 1.5, fields must be stacked vertically (air above gnd)
        assertTrue("Expected airTemp to stack above gndTemp", gndBounds.top >= airBounds.bottom)

        val depthBounds = composeTestRule.onNodeWithTag("input_burial_depth").getBoundsInRoot()
        val resBounds = composeTestRule.onNodeWithTag("input_soil_res").getBoundsInRoot()
        assertTrue("Expected burialDepth to stack above soilRes", resBounds.top >= depthBounds.bottom)

        val formationBounds = composeTestRule.onNodeWithTag("input_formation").getBoundsInRoot()
        val spacingBounds = composeTestRule.onNodeWithTag("input_spacing").getBoundsInRoot()
        assertTrue("Expected formation to stack above spacing", spacingBounds.top >= formationBounds.bottom)
    }
}
