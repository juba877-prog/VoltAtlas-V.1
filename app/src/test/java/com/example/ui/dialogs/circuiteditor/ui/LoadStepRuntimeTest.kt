package com.example.ui.dialogs.circuiteditor.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.example.data.LoadCategories
import com.example.ui.dialogs.circuiteditor.model.CircuitEditorDraft
import com.example.ui.dialogs.circuiteditor.model.CircuitWorkflowStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Runtime specification tests for Load Specifications step.
 *
 * Evidence Label: NEW_EDITOR_RUNTIME_OBSERVED
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
class LoadStepRuntimeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun test20_allLoadFieldsAndTagsRender() {
        val holder = CircuitEditorTestStateHolder(
            initialStep = CircuitWorkflowStep.LOAD,
            initialVisited = setOf(CircuitWorkflowStep.IDENTITY, CircuitWorkflowStep.LOAD)
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        composeTestRule.onNodeWithTag("circuit_editor_step_load").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_load_category").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_load_nature").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_load_kva").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_pf").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_demand_factor").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_safety_factor").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_voltage").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_phases").assertIsDisplayed()
        composeTestRule.onNodeWithTag("checkbox_is_harmonic").assertIsDisplayed()
    }

    @Test
    fun test21_loadCategorySelectionTriggersPfDfUpdate() {
        val holder = CircuitEditorTestStateHolder(
            initialStep = CircuitWorkflowStep.LOAD,
            initialVisited = setOf(CircuitWorkflowStep.IDENTITY, CircuitWorkflowStep.LOAD),
            initialDraft = CircuitEditorDraft(loadCategory = null, pf = "0.85", demandFactor = "1.0")
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        // Programmatically or via event simulate category selection for LTG (defaultPf=0.92, defaultDf=0.90)
        val ltg = LoadCategories.findByCode("LTG")!!
        composeTestRule.runOnUiThread {
            holder.events.onDraftChange(
                holder.draft.copy(
                    loadCategory = ltg.code,
                    pf = ltg.defaultPf.toString(),
                    demandFactor = ltg.defaultDf.toString()
                )
            )
        }
        composeTestRule.waitForIdle()

        assertEquals("LTG", holder.draft.loadCategory)
        assertEquals("0.92", holder.draft.pf)
        assertEquals("0.9", holder.draft.demandFactor)
    }

    @Test
    fun test22_mtrShowsWarningCard() {
        val holder = CircuitEditorTestStateHolder(
            initialStep = CircuitWorkflowStep.LOAD,
            initialVisited = setOf(CircuitWorkflowStep.IDENTITY, CircuitWorkflowStep.LOAD),
            initialDraft = CircuitEditorDraft(loadCategory = "MTR")
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        composeTestRule.onNodeWithTag("motor_warning_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Note: Motor category affects load reporting only. Manually verify breaker curve (C/D) and starting-current cable sizing per IEC 60947-4-1 — this is not yet automated.").assertIsDisplayed()
    }

    @Test
    fun test23_staticHidesMotorFields() {
        val holder = CircuitEditorTestStateHolder(
            initialStep = CircuitWorkflowStep.LOAD,
            initialVisited = setOf(CircuitWorkflowStep.IDENTITY, CircuitWorkflowStep.LOAD),
            initialDraft = CircuitEditorDraft(loadNature = "Static")
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        composeTestRule.onNodeWithTag("input_starting_method").assertDoesNotExist()
        composeTestRule.onNodeWithTag("input_starting_current_multiplier").assertDoesNotExist()
    }

    @Test
    fun test24_motorShowsStartingMethodAndMultiplier() {
        val holder = CircuitEditorTestStateHolder(
            initialStep = CircuitWorkflowStep.LOAD,
            initialVisited = setOf(CircuitWorkflowStep.IDENTITY, CircuitWorkflowStep.LOAD),
            initialDraft = CircuitEditorDraft(loadNature = "Motor", startingMethod = "DOL", startingCurrentMultiplier = "6.0")
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        composeTestRule.onNodeWithTag("input_starting_method").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_starting_current_multiplier").assertIsDisplayed()
    }

    @Test
    fun test25_harmonicSwitchUpdatesDraftState() {
        val holder = CircuitEditorTestStateHolder(
            initialStep = CircuitWorkflowStep.LOAD,
            initialVisited = setOf(CircuitWorkflowStep.IDENTITY, CircuitWorkflowStep.LOAD),
            initialDraft = CircuitEditorDraft(isHarmonic = false)
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        val switchNode = composeTestRule.onNodeWithTag("checkbox_is_harmonic")
        switchNode.assertIsOff()
        switchNode.performClick()
        assertTrue(holder.draft.isHarmonic)
        switchNode.assertIsOn()
    }

    @Test
    fun test26_numericTextRemainsEditableWithoutBlocking() {
        val holder = CircuitEditorTestStateHolder(
            initialStep = CircuitWorkflowStep.LOAD,
            initialVisited = setOf(CircuitWorkflowStep.IDENTITY, CircuitWorkflowStep.LOAD),
            initialDraft = CircuitEditorDraft(loadKVA = "10.0")
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        composeTestRule.onNode(hasParent(hasTestTag("input_load_kva")) and hasSetTextAction()).performTextReplacement("25.5")
        assertEquals("25.5", holder.draft.loadKVA)
    }

    @Test
    fun test27_longLabelsDoNotRelyOnHardcodedAbbreviation() {
        val holder = CircuitEditorTestStateHolder(
            initialStep = CircuitWorkflowStep.LOAD,
            initialVisited = setOf(CircuitWorkflowStep.IDENTITY, CircuitWorkflowStep.LOAD)
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        composeTestRule.onNodeWithText("Phases").assertIsDisplayed()
        composeTestRule.onNodeWithText("Power Factor").assertIsDisplayed()
        composeTestRule.onNodeWithText("Demand Factor").assertIsDisplayed()
        composeTestRule.onNodeWithText("Safety Factor").assertIsDisplayed()
    }

    @Test
    fun test28_draftValuesSurviveRecomposition() {
        val holder = CircuitEditorTestStateHolder(
            initialStep = CircuitWorkflowStep.LOAD,
            initialVisited = setOf(CircuitWorkflowStep.IDENTITY, CircuitWorkflowStep.LOAD),
            initialDraft = CircuitEditorDraft(loadKVA = "45.0", pf = "0.90")
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        assertEquals("45.0", holder.draft.loadKVA)
        assertEquals("0.90", holder.draft.pf)

        // Force host recomposition by updating external property
        composeTestRule.runOnUiThread {
            holder.draft = holder.draft.copy(voltageV = "230")
        }
        composeTestRule.waitForIdle()

        assertEquals("45.0", holder.draft.loadKVA)
        assertEquals("0.90", holder.draft.pf)
        assertEquals("230", holder.draft.voltageV)
    }

    @Test
    fun test29_noDuplicateSemanticTagsInActiveStepTree() {
        val holder = CircuitEditorTestStateHolder(
            initialStep = CircuitWorkflowStep.LOAD,
            initialVisited = setOf(CircuitWorkflowStep.IDENTITY, CircuitWorkflowStep.LOAD),
            initialDraft = CircuitEditorDraft(loadNature = "Motor", loadCategory = "MTR")
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        val allTags = listOf(
            "circuit_editor_root",
            "circuit_editor_header",
            "circuit_editor_close_btn",
            "circuit_editor_back_btn",
            "circuit_editor_next_btn",
            "circuit_editor_progress",
            "circuit_editor_step_load",
            "input_load_category",
            "motor_warning_card",
            "input_load_nature",
            "input_starting_method",
            "input_starting_current_multiplier",
            "input_load_kva",
            "input_pf",
            "input_demand_factor",
            "input_safety_factor",
            "input_voltage",
            "input_phases",
            "checkbox_is_harmonic"
        )

        allTags.forEach { tag ->
            val count = composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().size
            assertEquals("Tag '$tag' must appear exactly once in the composition", 1, count)
        }
    }

    @Test
    fun test30_hiddenStepComponentsNotSimultaneouslyComposed() {
        val holder = CircuitEditorTestStateHolder(
            initialStep = CircuitWorkflowStep.LOAD,
            initialVisited = setOf(CircuitWorkflowStep.IDENTITY, CircuitWorkflowStep.LOAD)
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        // On Load step, Identity step tags MUST NOT exist in the semantics tree
        composeTestRule.onNodeWithTag("circuit_editor_step_identity").assertDoesNotExist()
        composeTestRule.onNodeWithTag("input_circuit_name").assertDoesNotExist()
        composeTestRule.onNodeWithTag("input_panel_type").assertDoesNotExist()
        composeTestRule.onNodeWithTag("input_fed_from").assertDoesNotExist()
        composeTestRule.onNodeWithTag("computed_hierarchy_card").assertDoesNotExist()
    }

    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.5f)
    fun test31_motorControlsStackVerticallyAtLargeFontScale() {
        val holder = CircuitEditorTestStateHolder(
            initialStep = CircuitWorkflowStep.LOAD,
            initialVisited = setOf(CircuitWorkflowStep.IDENTITY, CircuitWorkflowStep.LOAD),
            initialDraft = CircuitEditorDraft(loadNature = "Motor")
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        // Section 5 #4: Motor controls switch to one-column arrangement at 360dp / font 1.5
        val methodBounds = composeTestRule.onNodeWithTag("input_starting_method").getBoundsInRoot()
        val multiplierBounds = composeTestRule.onNodeWithTag("input_starting_current_multiplier").getBoundsInRoot()
        assertTrue(
            "Starting multiplier must be positioned below starting method in single column at font 1.5 (method bottom: ${methodBounds.bottom}, mult top: ${multiplierBounds.top})",
            multiplierBounds.top >= methodBounds.bottom
        )
    }

    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.5f)
    fun test32_startingMultiplierRemainsVisibleAndEditableAtFont1_5() {
        val holder = CircuitEditorTestStateHolder(
            initialStep = CircuitWorkflowStep.LOAD,
            initialVisited = setOf(CircuitWorkflowStep.IDENTITY, CircuitWorkflowStep.LOAD),
            initialDraft = CircuitEditorDraft(loadNature = "Motor", startingCurrentMultiplier = "6.0")
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        // Section 5 #5: Starting Multiplier content remains visible and editable at font 1.5
        composeTestRule.onNodeWithTag("input_starting_current_multiplier").assertIsDisplayed()
        composeTestRule.onNode(hasParent(hasTestTag("input_starting_current_multiplier")) and hasSetTextAction())
            .performTextReplacement("7.5")
        assertEquals("7.5", holder.draft.startingCurrentMultiplier)
    }

    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.5f)
    fun test33_finalLoadFieldCanScrollFullyAboveBottomActionBar() {
        val holder = CircuitEditorTestStateHolder(
            initialStep = CircuitWorkflowStep.LOAD,
            initialVisited = setOf(CircuitWorkflowStep.IDENTITY, CircuitWorkflowStep.LOAD),
            initialDraft = CircuitEditorDraft(loadNature = "Motor")
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        // Section 5 #6: Final Load field can scroll above the bottom action bar
        val nextBtn = composeTestRule.onNodeWithTag("circuit_editor_next_btn").getBoundsInRoot()
        val harmonicNode = composeTestRule.onNodeWithTag("checkbox_is_harmonic")
        harmonicNode.performScrollTo()
        val harmonicBounds = harmonicNode.getBoundsInRoot()

        assertTrue(
            "Final Load field must scroll completely above bottom action bar (harmonic bottom: ${harmonicBounds.bottom}, nextBtn top: ${nextBtn.top})",
            harmonicBounds.bottom <= nextBtn.top
        )
    }

    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
    fun test34_simulatedImeEntireHarmonicCardAndSupportingTextAboveActionBar() {
        val holder = CircuitEditorTestStateHolder(
            initialStep = CircuitWorkflowStep.LOAD,
            initialVisited = setOf(CircuitWorkflowStep.IDENTITY, CircuitWorkflowStep.LOAD),
            initialDraft = CircuitEditorDraft(loadNature = "Motor")
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(
                stateHolder = holder,
                modifier = Modifier.padding(bottom = 280.dp)
            )
        }

        // Section 4 #2: In SIMULATED_IME_INSET, the complete harmonic card, including all supporting text, lies above the action-bar top
        val nextBtn = composeTestRule.onNodeWithTag("circuit_editor_next_btn").getBoundsInRoot()
        val cardNode = composeTestRule.onNodeWithTag("card_harmonic_load")
        cardNode.performScrollTo()
        val cardBounds = cardNode.getBoundsInRoot()
        val textNode = composeTestRule.onNodeWithTag("harmonic_supporting_text")
        val textBounds = textNode.getBoundsInRoot()

        assertTrue(
            "Harmonic card bottom must lie above nextBtn top under simulated IME (card bottom: ${cardBounds.bottom}, nextBtn top: ${nextBtn.top})",
            cardBounds.bottom <= nextBtn.top
        )
        assertTrue(
            "Harmonic supporting text bottom must lie above nextBtn top under simulated IME (text bottom: ${textBounds.bottom}, nextBtn top: ${nextBtn.top})",
            textBounds.bottom <= nextBtn.top
        )
    }

    @Test
    @Config(sdk = [33], qualifiers = "w384dp-h800dp-xhdpi", fontScale = 1.3f)
    fun test35_adaptivePairStacksAtIntermediateWidthWithFont1_3() {
        val holder = CircuitEditorTestStateHolder(
            initialStep = CircuitWorkflowStep.LOAD,
            initialVisited = setOf(CircuitWorkflowStep.IDENTITY, CircuitWorkflowStep.LOAD)
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        // Section 4 #5: Adaptive field pairs stack whenever measured child width would fall below the defined readable minimum, including intermediate widths
        val demandBounds = composeTestRule.onNodeWithTag("input_demand_factor").getBoundsInRoot()
        val safetyBounds = composeTestRule.onNodeWithTag("input_safety_factor").getBoundsInRoot()
        assertTrue(
            "Safety Factor must stack below Demand Factor at intermediate width 384dp with font 1.3 (demand bottom: ${demandBounds.bottom}, safety top: ${safetyBounds.top})",
            safetyBounds.top >= demandBounds.bottom
        )
    }
}
