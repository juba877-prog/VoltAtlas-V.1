package com.example.ui.dialogs.circuiteditor.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.example.data.CircuitEntity
import com.example.ui.dialogs.circuiteditor.model.CircuitEditorDraft
import com.example.ui.dialogs.circuiteditor.model.CircuitWorkflowStep
import com.example.ui.dialogs.circuiteditor.model.TransformerSnapshot
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Runtime specification tests for Identity & Source step.
 *
 * Evidence Label: NEW_EDITOR_RUNTIME_OBSERVED
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
class IdentityStepRuntimeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun test12_allIdentityFieldsAndTagsRender() {
        val holder = CircuitEditorTestStateHolder(
            availableTransformers = listOf(TransformerSnapshot("TX-01"))
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        composeTestRule.onNodeWithTag("circuit_editor_step_identity").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_circuit_name").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_panel_type").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_fed_from").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_transformer_id").assertIsDisplayed()
        composeTestRule.onNodeWithTag("add_transformer_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("computed_hierarchy_card").assertIsDisplayed()
    }

    @Test
    fun test13_transformerControlsShownOnlyForTransformerSource() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(fedFrom = "MDB-Main"),
            availableTransformers = listOf(TransformerSnapshot("TX-01")),
            existingCircuits = listOf(
                CircuitEntity(id = 1, circuitName = "MDB-Main", panelType = "MDB")
            )
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        composeTestRule.onNodeWithTag("input_transformer_id").assertDoesNotExist()
        composeTestRule.onNodeWithTag("add_transformer_btn").assertDoesNotExist()
    }

    @Test
    fun test14_addTransformerCallbackFiresOnce() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(fedFrom = "Transformer")
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        assertEquals(0, holder.addTransformerCount)
        composeTestRule.onNodeWithTag("add_transformer_btn").performClick()
        assertEquals(1, holder.addTransformerCount)
    }

    @Test
    fun test15_projectSubtitleRenderBehavior() {
        val holder = CircuitEditorTestStateHolder(projectSubtitle = null)
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }
        composeTestRule.onNodeWithText("Commercial Tower Project").assertDoesNotExist()

        holder.projectSubtitle = "Commercial Tower Project"
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Commercial Tower Project").assertIsDisplayed()
    }

    @Test
    fun test16_blankNameBlocksNextAndShowsValidatorError() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "   ")
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        composeTestRule.onNodeWithTag("circuit_editor_next_btn").performClick()
        assertEquals(CircuitWorkflowStep.IDENTITY, holder.currentStep)
        assertEquals("Circuit/Panel name cannot be empty.", holder.identityError)
        composeTestRule.onNodeWithText("Circuit/Panel name cannot be empty.").assertIsDisplayed()
    }

    @Test
    fun test17_duplicateNameBlocksNext() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "Existing-Panel"),
            existingCircuits = listOf(
                CircuitEntity(id = 10, circuitName = "Existing-Panel", panelType = "DB")
            ),
            editingCircuitId = 0
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        composeTestRule.onNodeWithTag("circuit_editor_next_btn").performClick()
        assertEquals(CircuitWorkflowStep.IDENTITY, holder.currentStep)
        val expectedMsg = "A circuit/panel named 'Existing-Panel' already exists — choose a different name."
        assertEquals(expectedMsg, holder.identityError)
        composeTestRule.onNodeWithText(expectedMsg).assertIsDisplayed()
    }

    @Test
    fun test18_validIdentityAdvancesToLoad() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "New Feeder 1")
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        composeTestRule.onNodeWithTag("circuit_editor_next_btn").performClick()
        assertEquals(CircuitWorkflowStep.LOAD, holder.currentStep)
        composeTestRule.onNodeWithTag("circuit_editor_step_load").assertIsDisplayed()
        composeTestRule.onNodeWithTag("circuit_editor_step_identity").assertDoesNotExist()
    }

    @Test
    fun test19_hierarchyCardIsReadOnlyAndDisplaysDerivedContext() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(fedFrom = "Transformer", transformerId = "TX-99"),
            availableTransformers = listOf(TransformerSnapshot("TX-99"))
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        composeTestRule.onNodeWithTag("computed_hierarchy_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Derived Network Hierarchy").assertIsDisplayed()
        composeTestRule.onNodeWithText("Supply Source").assertIsDisplayed()
        composeTestRule.onNodeWithText("0 (Root = 0)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Resolved Transformer").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("TX-99")[0].assertIsDisplayed()
        composeTestRule.onAllNodesWithText("TX-99")[1].assertIsDisplayed()
    }
}
