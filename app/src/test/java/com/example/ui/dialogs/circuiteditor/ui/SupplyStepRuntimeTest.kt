package com.example.ui.dialogs.circuiteditor.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.ui.dialogs.circuiteditor.mapper.CircuitSourceResolution
import com.example.ui.dialogs.circuiteditor.model.AtsSnapshot
import com.example.ui.dialogs.circuiteditor.model.CircuitEditorDraft
import com.example.ui.dialogs.circuiteditor.model.CircuitWorkflowStep
import com.example.ui.dialogs.circuiteditor.model.GeneratorSnapshot
import com.example.ui.dialogs.circuiteditor.model.UpsSnapshot
import com.example.ui.dialogs.circuiteditor.ui.steps.SupplyStepContent
import com.example.ui.theme.VoltAtlasTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Runtime specification tests for SupplyStepContent.
 *
 * Evidence Label: NEW_EDITOR_RUNTIME_OBSERVED
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
class SupplyStepRuntimeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val testGenerators = listOf(GeneratorSnapshot("GEN-01"), GeneratorSnapshot("GEN-02"))
    private val testAtsList = listOf(AtsSnapshot("ATS-01"))
    private val testUpsList = listOf(UpsSnapshot("UPS-01"))
    private val testResolution = CircuitSourceResolution(
        finalTxId = "TR-01",
        parentBoardObj = null,
        calculatedParentId = null,
        calculatedLevel = 1,
        calculatedSourceType = "Transformer"
    )

    @Test
    fun test01_normalShowsNoBackupSelectors() {
        var currentDraft = CircuitEditorDraft(supplyCategory = "NORMAL")
        composeTestRule.setContent {
            VoltAtlasTheme {
                SupplyStepContent(
                    draft = currentDraft,
                    onDraftChange = { currentDraft = it },
                    availableGenerators = testGenerators,
                    availableAts = testAtsList,
                    availableUps = testUpsList,
                    sourceResolution = testResolution
                )
            }
        }

        composeTestRule.onNodeWithTag("circuit_editor_step_supply").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_supply_category").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_assigned_ats").assertDoesNotExist()
        composeTestRule.onNodeWithTag("input_assigned_generator").assertDoesNotExist()
        composeTestRule.onNodeWithTag("input_assigned_ups").assertDoesNotExist()
        composeTestRule.onNodeWithTag("inherited_source_card").assertIsDisplayed()
    }

    @Test
    fun test02_emergencyShowsAtsAndGeneratorOnly() {
        var currentDraft = CircuitEditorDraft(supplyCategory = "EMERGENCY")
        composeTestRule.setContent {
            VoltAtlasTheme {
                SupplyStepContent(
                    draft = currentDraft,
                    onDraftChange = { currentDraft = it },
                    availableGenerators = testGenerators,
                    availableAts = testAtsList,
                    availableUps = testUpsList,
                    sourceResolution = testResolution
                )
            }
        }

        composeTestRule.onNodeWithTag("input_assigned_ats").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_assigned_generator").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_assigned_ups").assertDoesNotExist()
    }

    @Test
    fun test03_upsShowsUpsOnly() {
        var currentDraft = CircuitEditorDraft(supplyCategory = "UPS")
        composeTestRule.setContent {
            VoltAtlasTheme {
                SupplyStepContent(
                    draft = currentDraft,
                    onDraftChange = { currentDraft = it },
                    availableGenerators = testGenerators,
                    availableAts = testAtsList,
                    availableUps = testUpsList,
                    sourceResolution = testResolution
                )
            }
        }

        composeTestRule.onNodeWithTag("input_assigned_ats").assertDoesNotExist()
        composeTestRule.onNodeWithTag("input_assigned_generator").assertDoesNotExist()
        composeTestRule.onNodeWithTag("input_assigned_ups").assertIsDisplayed()
    }

    @Test
    fun test04_emergencyUpsShowsAtsGeneratorAndUps() {
        var currentDraft = CircuitEditorDraft(supplyCategory = "EMERGENCY + UPS")
        composeTestRule.setContent {
            VoltAtlasTheme {
                SupplyStepContent(
                    draft = currentDraft,
                    onDraftChange = { currentDraft = it },
                    availableGenerators = testGenerators,
                    availableAts = testAtsList,
                    availableUps = testUpsList,
                    sourceResolution = testResolution
                )
            }
        }

        composeTestRule.onNodeWithTag("input_assigned_ats").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_assigned_generator").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_assigned_ups").assertIsDisplayed()
    }

    @Test
    fun test05_switchingCategoryUpdatesDraftWithoutInventingIds() {
        var currentDraft = CircuitEditorDraft(supplyCategory = "NORMAL")
        composeTestRule.setContent {
            VoltAtlasTheme {
                SupplyStepContent(
                    draft = currentDraft,
                    onDraftChange = { currentDraft = it },
                    availableGenerators = testGenerators,
                    availableAts = testAtsList,
                    availableUps = testUpsList
                )
            }
        }

        composeTestRule.onNodeWithText("EMERGENCY").performClick()
        assertEquals("EMERGENCY", currentDraft.supplyCategory)
        assertEquals("None", currentDraft.assignedAtsId)
        assertEquals("None", currentDraft.assignedGeneratorId)
        assertEquals("None", currentDraft.assignedUpsId)
    }

    @Test
    fun test06_hiddenSelectionsRemainInDraft() {
        var currentDraft = CircuitEditorDraft(
            supplyCategory = "EMERGENCY + UPS",
            assignedAtsId = "ATS-01",
            assignedGeneratorId = "GEN-01",
            assignedUpsId = "UPS-01"
        )
        composeTestRule.setContent {
            VoltAtlasTheme {
                SupplyStepContent(
                    draft = currentDraft,
                    onDraftChange = { currentDraft = it },
                    availableGenerators = testGenerators,
                    availableAts = testAtsList,
                    availableUps = testUpsList
                )
            }
        }

        // Switch to NORMAL where selectors are hidden
        composeTestRule.onNodeWithText("NORMAL").performClick()
        assertEquals("NORMAL", currentDraft.supplyCategory)
        // Hidden values remain intact in draft
        assertEquals("ATS-01", currentDraft.assignedAtsId)
        assertEquals("GEN-01", currentDraft.assignedGeneratorId)
        assertEquals("UPS-01", currentDraft.assignedUpsId)
    }

    @Test
    fun test07_emptyEquipmentListsShowHonestUnavailableState() {
        var currentDraft = CircuitEditorDraft(supplyCategory = "EMERGENCY + UPS")
        composeTestRule.setContent {
            VoltAtlasTheme {
                SupplyStepContent(
                    draft = currentDraft,
                    onDraftChange = { currentDraft = it },
                    availableGenerators = emptyList(),
                    availableAts = emptyList(),
                    availableUps = emptyList()
                )
            }
        }

        composeTestRule.onNodeWithText("No ATS configured in this project").assertIsDisplayed()
        composeTestRule.onNodeWithText("No generator configured in this project").assertIsDisplayed()
        composeTestRule.onNodeWithText("No UPS configured in this project").assertIsDisplayed()
    }

    @Test
    fun test08_equipmentSelectionCallbacksUpdateExactDraftField() {
        var currentDraft = CircuitEditorDraft(supplyCategory = "EMERGENCY")
        composeTestRule.setContent {
            VoltAtlasTheme {
                SupplyStepContent(
                    draft = currentDraft,
                    onDraftChange = { currentDraft = it },
                    availableGenerators = testGenerators,
                    availableAts = testAtsList,
                    availableUps = testUpsList
                )
            }
        }

        // Open ATS dropdown and select ATS-01
        composeTestRule.onNodeWithTag("input_assigned_ats").performClick()
        composeTestRule.onNodeWithText("ATS-01").performClick()
        assertEquals("ATS-01", currentDraft.assignedAtsId)

        // Open Generator dropdown and select GEN-02
        composeTestRule.onNodeWithTag("input_assigned_generator").performClick()
        composeTestRule.onNodeWithText("GEN-02").performClick()
        assertEquals("GEN-02", currentDraft.assignedGeneratorId)
    }

    @Test
    fun test09_noDuplicateHiddenSelectorTagsExist() {
        var currentDraft = CircuitEditorDraft(supplyCategory = "NORMAL")
        composeTestRule.setContent {
            VoltAtlasTheme {
                SupplyStepContent(
                    draft = currentDraft,
                    onDraftChange = { currentDraft = it }
                )
            }
        }

        // Neither active nor duplicate hidden selector tags exist in the semantics tree
        composeTestRule.onNodeWithTag("input_assigned_ats").assertDoesNotExist()
        composeTestRule.onNodeWithTag("input_assigned_generator").assertDoesNotExist()
        composeTestRule.onNodeWithTag("input_assigned_ups").assertDoesNotExist()
    }

    @Test
    @Config(fontScale = 1.5f, qualifiers = "w320dp-h640dp-xhdpi")
    fun test10_fourCategoryControlIsReadableAndUsableAt320dpFont1_5() {
        var currentDraft = CircuitEditorDraft(supplyCategory = "NORMAL")
        composeTestRule.setContent {
            VoltAtlasTheme {
                SupplyStepContent(
                    draft = currentDraft,
                    onDraftChange = { currentDraft = it }
                )
            }
        }

        composeTestRule.onNodeWithTag("input_supply_category").assertIsDisplayed()
        composeTestRule.onNodeWithText("NORMAL").assertIsDisplayed()
        composeTestRule.onNodeWithText("EMERGENCY").assertIsDisplayed()
        composeTestRule.onNodeWithText("UPS").assertIsDisplayed()
        composeTestRule.onNodeWithText("EMERGENCY + UPS").assertIsDisplayed()

        composeTestRule.onNodeWithText("EMERGENCY + UPS").performClick()
        assertEquals("EMERGENCY + UPS", currentDraft.supplyCategory)
    }

    @Test
    fun test11_failFirst_emptyEquipmentSupportingTextMatchesExactSpecificationWithoutRedundantNone() {
        val draft = CircuitEditorDraft(supplyCategory = "EMERGENCY + UPS")
        composeTestRule.setContent {
            VoltAtlasTheme {
                SupplyStepContent(
                    draft = draft,
                    onDraftChange = {},
                    availableAts = emptyList(),
                    availableGenerators = emptyList(),
                    availableUps = emptyList()
                )
            }
        }

        // Exact supporting text required by Section 5
        composeTestRule.onNodeWithText("No ATS configured in this project").assertIsDisplayed()
        composeTestRule.onNodeWithText("No generator configured in this project").assertIsDisplayed()
        composeTestRule.onNodeWithText("No UPS configured in this project").assertIsDisplayed()
    }

    @Test
    fun test12_failFirst_categoryCardsExposeSingleChoiceRadioButtonSemantics() {
        val draft = CircuitEditorDraft(supplyCategory = "EMERGENCY")
        composeTestRule.setContent {
            VoltAtlasTheme {
                SupplyStepContent(
                    draft = draft,
                    onDraftChange = {}
                )
            }
        }

        // EMERGENCY selected, others not selected
        composeTestRule.onNodeWithText("EMERGENCY").assertIsSelected()
        composeTestRule.onNodeWithText("NORMAL").assertIsNotSelected()
        composeTestRule.onNodeWithText("UPS").assertIsNotSelected()
        composeTestRule.onNodeWithText("EMERGENCY + UPS").assertIsNotSelected()
    }

    @Test
    fun test13_conditionalSupplyVisibilityMatrix_allFourCategories() {
        data class CategoryExpectation(
            val category: String,
            val expectAts: Boolean,
            val expectGen: Boolean,
            val expectUps: Boolean
        )

        val matrix = listOf(
            CategoryExpectation("NORMAL", expectAts = false, expectGen = false, expectUps = false),
            CategoryExpectation("EMERGENCY", expectAts = true, expectGen = true, expectUps = false),
            CategoryExpectation("UPS", expectAts = false, expectGen = false, expectUps = true),
            CategoryExpectation("EMERGENCY + UPS", expectAts = true, expectGen = true, expectUps = true)
        )

        var currentDraft by mutableStateOf(
            CircuitEditorDraft(
                supplyCategory = "NORMAL",
                assignedAtsId = "ATS-01",
                assignedGeneratorId = "GEN-01",
                assignedUpsId = "UPS-01"
            )
        )

        composeTestRule.setContent {
            VoltAtlasTheme {
                SupplyStepContent(
                    draft = currentDraft,
                    onDraftChange = { currentDraft = it },
                    availableGenerators = testGenerators,
                    availableAts = testAtsList,
                    availableUps = testUpsList,
                    sourceResolution = testResolution
                )
            }
        }

        matrix.forEach { exp ->
            currentDraft = currentDraft.copy(supplyCategory = exp.category)
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("circuit_editor_step_supply").assertIsDisplayed()
            composeTestRule.onNodeWithTag("input_supply_category").assertIsDisplayed()
            composeTestRule.onNodeWithTag("inherited_source_card").assertIsDisplayed()

            if (exp.expectAts) {
                composeTestRule.onNodeWithTag("input_assigned_ats").assertIsDisplayed()
            } else {
                composeTestRule.onNodeWithTag("input_assigned_ats").assertDoesNotExist()
            }

            if (exp.expectGen) {
                composeTestRule.onNodeWithTag("input_assigned_generator").assertIsDisplayed()
            } else {
                composeTestRule.onNodeWithTag("input_assigned_generator").assertDoesNotExist()
            }

            if (exp.expectUps) {
                composeTestRule.onNodeWithTag("input_assigned_ups").assertIsDisplayed()
            } else {
                composeTestRule.onNodeWithTag("input_assigned_ups").assertDoesNotExist()
            }

            // Draft retains all IDs regardless of visibility
            assertEquals("ATS-01", currentDraft.assignedAtsId)
            assertEquals("GEN-01", currentDraft.assignedGeneratorId)
            assertEquals("UPS-01", currentDraft.assignedUpsId)
        }
    }

    @Test
    fun test14_supplyIdPreservationSequence_emergencyUpsToNormalToEmergencyUps() {
        var currentDraft by mutableStateOf(
            CircuitEditorDraft(
                supplyCategory = "EMERGENCY + UPS",
                assignedAtsId = "ATS-01",
                assignedGeneratorId = "GEN-01",
                assignedUpsId = "UPS-01"
            )
        )

        composeTestRule.setContent {
            VoltAtlasTheme {
                SupplyStepContent(
                    draft = currentDraft,
                    onDraftChange = { currentDraft = it },
                    availableGenerators = testGenerators,
                    availableAts = testAtsList,
                    availableUps = testUpsList,
                    sourceResolution = testResolution
                )
            }
        }

        // Transition 1: EMERGENCY + UPS -> NORMAL
        composeTestRule.onNodeWithText("NORMAL").performClick()
        assertEquals("NORMAL", currentDraft.supplyCategory)
        assertEquals("ATS-01", currentDraft.assignedAtsId)
        assertEquals("GEN-01", currentDraft.assignedGeneratorId)
        assertEquals("UPS-01", currentDraft.assignedUpsId)

        // Selectors must be hidden
        composeTestRule.onNodeWithTag("input_assigned_ats").assertDoesNotExist()
        composeTestRule.onNodeWithTag("input_assigned_generator").assertDoesNotExist()
        composeTestRule.onNodeWithTag("input_assigned_ups").assertDoesNotExist()

        // Transition 2: NORMAL -> EMERGENCY + UPS
        composeTestRule.onNodeWithText("EMERGENCY + UPS").performClick()
        assertEquals("EMERGENCY + UPS", currentDraft.supplyCategory)
        assertEquals("ATS-01", currentDraft.assignedAtsId)
        assertEquals("GEN-01", currentDraft.assignedGeneratorId)
        assertEquals("UPS-01", currentDraft.assignedUpsId)

        // Selectors must be displayed again with their preserved values
        composeTestRule.onNodeWithTag("input_assigned_ats").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_assigned_generator").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_assigned_ups").assertIsDisplayed()
    }

    @Test
    fun test15_supplyIdPreservationSequence_emergencyToUpsToEmergency() {
        var currentDraft by mutableStateOf(
            CircuitEditorDraft(
                supplyCategory = "EMERGENCY",
                assignedAtsId = "ATS-01",
                assignedGeneratorId = "GEN-01",
                assignedUpsId = "UPS-01"
            )
        )

        composeTestRule.setContent {
            VoltAtlasTheme {
                SupplyStepContent(
                    draft = currentDraft,
                    onDraftChange = { currentDraft = it },
                    availableGenerators = testGenerators,
                    availableAts = testAtsList,
                    availableUps = testUpsList,
                    sourceResolution = testResolution
                )
            }
        }

        // Transition 1: EMERGENCY -> UPS
        composeTestRule.onNodeWithText("UPS").performClick()
        assertEquals("UPS", currentDraft.supplyCategory)
        assertEquals("ATS-01", currentDraft.assignedAtsId)
        assertEquals("GEN-01", currentDraft.assignedGeneratorId)
        assertEquals("UPS-01", currentDraft.assignedUpsId)

        composeTestRule.onNodeWithTag("input_assigned_ats").assertDoesNotExist()
        composeTestRule.onNodeWithTag("input_assigned_generator").assertDoesNotExist()
        composeTestRule.onNodeWithTag("input_assigned_ups").assertIsDisplayed()

        // Transition 2: UPS -> EMERGENCY
        composeTestRule.onNodeWithText("EMERGENCY").performClick()
        assertEquals("EMERGENCY", currentDraft.supplyCategory)
        assertEquals("ATS-01", currentDraft.assignedAtsId)
        assertEquals("GEN-01", currentDraft.assignedGeneratorId)
        assertEquals("UPS-01", currentDraft.assignedUpsId)

        composeTestRule.onNodeWithTag("input_assigned_ats").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_assigned_generator").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_assigned_ups").assertDoesNotExist()
    }

    @Test
    fun test16_supplyIdPreservationSequence_upsToNormalToUps() {
        var currentDraft by mutableStateOf(
            CircuitEditorDraft(
                supplyCategory = "UPS",
                assignedAtsId = "None",
                assignedGeneratorId = "None",
                assignedUpsId = "UPS-01"
            )
        )

        composeTestRule.setContent {
            VoltAtlasTheme {
                SupplyStepContent(
                    draft = currentDraft,
                    onDraftChange = { currentDraft = it },
                    availableGenerators = testGenerators,
                    availableAts = testAtsList,
                    availableUps = testUpsList,
                    sourceResolution = testResolution
                )
            }
        }

        // Transition 1: UPS -> NORMAL
        composeTestRule.onNodeWithText("NORMAL").performClick()
        assertEquals("NORMAL", currentDraft.supplyCategory)
        assertEquals("UPS-01", currentDraft.assignedUpsId)
        composeTestRule.onNodeWithTag("input_assigned_ups").assertDoesNotExist()

        // Transition 2: NORMAL -> UPS
        composeTestRule.onNodeWithText("UPS").performClick()
        assertEquals("UPS", currentDraft.supplyCategory)
        assertEquals("UPS-01", currentDraft.assignedUpsId)
        composeTestRule.onNodeWithTag("input_assigned_ups").assertIsDisplayed()
    }
}

