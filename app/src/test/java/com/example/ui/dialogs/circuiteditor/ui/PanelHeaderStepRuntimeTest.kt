package com.example.ui.dialogs.circuiteditor.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getBoundsInRoot
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
import com.example.ui.dialogs.circuiteditor.ui.steps.PanelHeaderStepContent
import com.example.ui.theme.VoltAtlasTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Runtime test suite for Step 6: Panel Header Equipment Data.
 *
 * Evidence Label: NEW_EDITOR_RUNTIME_OBSERVED
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
class PanelHeaderStepRuntimeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun test08_busbarRatingFieldUpdatesDraft() {
        var currentDraft by mutableStateOf(CircuitEditorDraft(busbarRatingA = ""))
        composeTestRule.setContent {
            VoltAtlasTheme {
                PanelHeaderStepContent(
                    draft = currentDraft,
                    onDraftChange = { currentDraft = it }
                )
            }
        }

        val busbarField = composeTestRule.onNode(hasParent(hasTestTag("input_edit_dialog_busbar_rating")) and hasSetTextAction())
        busbarField.performTextInput("400")

        assertEquals("400", currentDraft.busbarRatingA)
    }

    @Test
    fun test09_shortCircuitRatingFieldIsReadOnlyAndDisplaysProvidedValue() {
        var currentDraft by mutableStateOf(CircuitEditorDraft())
        composeTestRule.setContent {
            VoltAtlasTheme {
                PanelHeaderStepContent(
                    draft = currentDraft,
                    onDraftChange = { currentDraft = it },
                    scRatingText = "25.00 kA"
                )
            }
        }

        val scField = composeTestRule.onNodeWithTag("display_edit_dialog_sc_rating")
        scField.assertIsDisplayed()
        composeTestRule.onNodeWithText("25.00 kA").assertIsDisplayed()
    }

    @Test
    fun test10_mainIncomingTypeDropdownRendersOptions() {
        var currentDraft by mutableStateOf(CircuitEditorDraft(mainIncomingType = ""))
        composeTestRule.setContent {
            VoltAtlasTheme {
                PanelHeaderStepContent(
                    draft = currentDraft,
                    onDraftChange = { currentDraft = it }
                )
            }
        }

        val incomingTypeDropdown = composeTestRule.onNodeWithTag("input_edit_dialog_main_incoming_type")
        incomingTypeDropdown.performClick()
        composeTestRule.onAllNodesWithText("MCCB").onLast().performClick()

        assertEquals("MCCB", currentDraft.mainIncomingType)
    }

    @Test
    fun test11_mainIncomingCountFieldUpdatesDraft() {
        var currentDraft by mutableStateOf(CircuitEditorDraft(mainIncomingCount = ""))
        composeTestRule.setContent {
            VoltAtlasTheme {
                PanelHeaderStepContent(
                    draft = currentDraft,
                    onDraftChange = { currentDraft = it }
                )
            }
        }

        val countField = composeTestRule.onNode(hasParent(hasTestTag("input_edit_dialog_main_incoming_count")) and hasSetTextAction())
        countField.performTextInput("2")

        assertEquals("2", currentDraft.mainIncomingCount)
    }

    @Test
    fun test12_ipRatingDropdownRendersOptions() {
        var currentDraft by mutableStateOf(CircuitEditorDraft(ipRating = ""))
        composeTestRule.setContent {
            VoltAtlasTheme {
                PanelHeaderStepContent(
                    draft = currentDraft,
                    onDraftChange = { currentDraft = it }
                )
            }
        }

        val ipDropdown = composeTestRule.onNodeWithTag("input_edit_dialog_ip_rating")
        ipDropdown.performClick()
        composeTestRule.onAllNodesWithText("IP54").onLast().performClick()

        assertEquals("IP54", currentDraft.ipRating)
    }

    @Test
    fun test13_cableEntryDropdownRendersOptions() {
        var currentDraft by mutableStateOf(CircuitEditorDraft(cableEntry = ""))
        composeTestRule.setContent {
            VoltAtlasTheme {
                PanelHeaderStepContent(
                    draft = currentDraft,
                    onDraftChange = { currentDraft = it }
                )
            }
        }

        val entryDropdown = composeTestRule.onNodeWithTag("input_edit_dialog_cable_entry")
        entryDropdown.performClick()
        composeTestRule.onAllNodesWithText("Bottom").onLast().performClick()

        assertEquals("Bottom", currentDraft.cableEntry)
    }

    @Test
    fun test14_locationFieldUpdatesDraft() {
        var currentDraft by mutableStateOf(CircuitEditorDraft(panelLocation = ""))
        composeTestRule.setContent {
            VoltAtlasTheme {
                PanelHeaderStepContent(
                    draft = currentDraft,
                    onDraftChange = { currentDraft = it }
                )
            }
        }

        val locationField = composeTestRule.onNode(hasParent(hasTestTag("input_edit_dialog_location")) and hasSetTextAction())
        locationField.performTextInput("Electrical Room 1")

        assertEquals("Electrical Room 1", currentDraft.panelLocation)
    }

    @Test
    fun test15_numberOfWaysFieldUpdatesDraft() {
        var currentDraft by mutableStateOf(CircuitEditorDraft(numberOfWays = ""))
        composeTestRule.setContent {
            VoltAtlasTheme {
                PanelHeaderStepContent(
                    draft = currentDraft,
                    onDraftChange = { currentDraft = it }
                )
            }
        }

        val waysField = composeTestRule.onNode(hasParent(hasTestTag("input_edit_dialog_number_of_ways")) and hasSetTextAction())
        waysField.performTextInput("12")

        assertEquals("12", currentDraft.numberOfWays)
    }

    @Test
    fun test16_feederDescriptionFieldUpdatesDraft() {
        var currentDraft by mutableStateOf(CircuitEditorDraft(feederDescription = ""))
        composeTestRule.setContent {
            VoltAtlasTheme {
                PanelHeaderStepContent(
                    draft = currentDraft,
                    onDraftChange = { currentDraft = it }
                )
            }
        }

        val descField = composeTestRule.onNode(hasParent(hasTestTag("input_edit_dialog_feeder_description")) and hasSetTextAction())
        descField.performTextInput("Main incoming from Transformer TX-01 via duct bank")

        assertEquals("Main incoming from Transformer TX-01 via duct bank", currentDraft.feederDescription)
    }

    @Test
    @Config(sdk = [33], qualifiers = "w320dp-h800dp-xhdpi", fontScale = 1.5f)
    fun test17_responsiveStackingAt320dpFont1_5() {
        var currentDraft by mutableStateOf(CircuitEditorDraft())
        composeTestRule.setContent {
            VoltAtlasTheme {
                PanelHeaderStepContent(
                    draft = currentDraft,
                    onDraftChange = { currentDraft = it }
                )
            }
        }

        val busbarBounds = composeTestRule.onNodeWithTag("input_edit_dialog_busbar_rating").getBoundsInRoot()
        val scBounds = composeTestRule.onNodeWithTag("display_edit_dialog_sc_rating").getBoundsInRoot()
        assertTrue("Expected busbarRating to stack above scRating", scBounds.top >= busbarBounds.bottom)

        val incomingTypeBounds = composeTestRule.onNodeWithTag("input_edit_dialog_main_incoming_type").getBoundsInRoot()
        val countBounds = composeTestRule.onNodeWithTag("input_edit_dialog_main_incoming_count").getBoundsInRoot()
        assertTrue("Expected incomingType to stack above incomingCount", countBounds.top >= incomingTypeBounds.bottom)

        val ipBounds = composeTestRule.onNodeWithTag("input_edit_dialog_ip_rating").getBoundsInRoot()
        val entryBounds = composeTestRule.onNodeWithTag("input_edit_dialog_cable_entry").getBoundsInRoot()
        assertTrue("Expected ipRating to stack above cableEntry", entryBounds.top >= ipBounds.bottom)

        val locBounds = composeTestRule.onNodeWithTag("input_edit_dialog_location").getBoundsInRoot()
        val waysBounds = composeTestRule.onNodeWithTag("input_edit_dialog_number_of_ways").getBoundsInRoot()
        assertTrue("Expected location to stack above numberOfWays", waysBounds.top >= locBounds.bottom)
    }
}
