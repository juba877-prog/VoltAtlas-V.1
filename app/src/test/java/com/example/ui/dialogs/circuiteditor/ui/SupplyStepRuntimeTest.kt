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
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.example.ui.dialogs.circuiteditor.model.TransformerSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    private fun assertHierarchyResponsiveness(
        expectedLevel: String,
        expectedSource: String,
        expectedSourceType: String,
        expectedTxId: String
    ) {
        composeTestRule.waitForIdle()

        // Ensure hierarchy card is scrolled into view in compact viewports
        composeTestRule.onNodeWithTag("hierarchy_value_resolved_transformer").performScrollTo()
        composeTestRule.waitForIdle()

        // 1. Verify semantic values are complete and match expected
        composeTestRule.onNodeWithTag("hierarchy_value_level").assertIsDisplayed().assertTextEquals(expectedLevel)
        composeTestRule.onNodeWithTag("hierarchy_value_supply_source").assertIsDisplayed().assertTextEquals(expectedSource)
        composeTestRule.onNodeWithTag("hierarchy_value_source_type").assertIsDisplayed().assertTextEquals(expectedSourceType)
        composeTestRule.onNodeWithTag("hierarchy_value_resolved_transformer").assertIsDisplayed().assertTextEquals(expectedTxId)

        // 2. Collect all hierarchy text node bounds
        val titleBounds = composeTestRule.onNodeWithTag("hierarchy_title").getBoundsInRoot()
        val pairs = listOf(
            "hierarchy_label_level" to "hierarchy_value_level",
            "hierarchy_label_supply_source" to "hierarchy_value_supply_source",
            "hierarchy_label_source_type" to "hierarchy_value_source_type",
            "hierarchy_label_resolved_transformer" to "hierarchy_value_resolved_transformer"
        )

        val allNodes = mutableListOf<Pair<String, DpRect>>()
        allNodes.add("hierarchy_title" to titleBounds)

        for ((labelTag, valTag) in pairs) {
            val labelBounds = composeTestRule.onNodeWithTag(labelTag).getBoundsInRoot()
            val valBounds = composeTestRule.onNodeWithTag(valTag).getBoundsInRoot()
            allNodes.add(labelTag to labelBounds)
            allNodes.add(valTag to valBounds)

            // Positive vertical or horizontal separation
            val isVerticallySeparated = valBounds.top.value >= labelBounds.bottom.value
            val isHorizontallySeparated = valBounds.left.value >= labelBounds.right.value
            assertTrue(
                "Pair ($labelTag, $valTag) must have positive separation! Label: $labelBounds, Value: $valBounds",
                isVerticallySeparated || isHorizontallySeparated
            )
        }

        // 3. No hierarchy text node intersects another hierarchy text node
        for (i in 0 until allNodes.size) {
            for (j in i + 1 until allNodes.size) {
                val (tag1, r1) = allNodes[i]
                val (tag2, r2) = allNodes[j]
                val overlaps = !(r1.right.value <= r2.left.value ||
                                 r2.right.value <= r1.left.value ||
                                 r1.bottom.value <= r2.top.value ||
                                 r2.bottom.value <= r1.top.value)
                assertFalse(
                    "Nodes $tag1 and $tag2 must not overlap! Bounds1=$r1, Bounds2=$r2",
                    overlaps
                )
            }
        }
    }

    private fun assertCardBottomClearance() {
        composeTestRule.onNodeWithTag("content_bottom_clearance_spacer").performScrollTo()
        composeTestRule.waitForIdle()

        val cardBounds = composeTestRule.onNodeWithTag("inherited_source_card").getBoundsInRoot()
        val nextBtnBounds = composeTestRule.onNodeWithTag("circuit_editor_next_btn").getBoundsInRoot()
        val actionBarTop = nextBtnBounds.top - 12.dp
        val clearance = actionBarTop - cardBounds.bottom

        println("HIERARCHY_CLEARANCE: cardBottom=${cardBounds.bottom}, actionBarTop=$actionBarTop, clearance=$clearance")
        assertTrue("Card clearance must be >= 16dp, measured: $clearance", clearance.value >= 16f)
    }

    @Test
    @Config(sdk = [33], qualifiers = "w320dp-h640dp-xhdpi", fontScale = 1.5f)
    fun test17_failFirst_hierarchyCardResponsivenessAt320dpFont1_5() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(
                supplyCategory = "NORMAL",
                fedFrom = "Transformer",
                transformerId = "TX-01"
            ),
            initialStep = CircuitWorkflowStep.SUPPLY,
            initialVisited = setOf(CircuitWorkflowStep.IDENTITY, CircuitWorkflowStep.LOAD, CircuitWorkflowStep.SUPPLY),
            availableTransformers = listOf(TransformerSnapshot("TX-01"))
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder, darkTheme = true)
        }

        assertHierarchyResponsiveness(
            expectedLevel = "0 (Root = 0)",
            expectedSource = "Transformer",
            expectedSourceType = "TRANSFORMER",
            expectedTxId = "TX-01"
        )
        assertCardBottomClearance()
    }

    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.0f)
    fun test18_hierarchyCardResponsivenessAt360dpFont1_0() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(
                supplyCategory = "NORMAL",
                fedFrom = "Transformer",
                transformerId = "TX-01"
            ),
            initialStep = CircuitWorkflowStep.SUPPLY,
            initialVisited = setOf(CircuitWorkflowStep.IDENTITY, CircuitWorkflowStep.LOAD, CircuitWorkflowStep.SUPPLY),
            availableTransformers = listOf(TransformerSnapshot("TX-01"))
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder, darkTheme = true)
        }

        assertHierarchyResponsiveness(
            expectedLevel = "0 (Root = 0)",
            expectedSource = "Transformer",
            expectedSourceType = "TRANSFORMER",
            expectedTxId = "TX-01"
        )
    }

    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.5f)
    fun test19_hierarchyCardResponsivenessAt360dpFont1_5() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(
                supplyCategory = "NORMAL",
                fedFrom = "Transformer",
                transformerId = "TX-01"
            ),
            initialStep = CircuitWorkflowStep.SUPPLY,
            initialVisited = setOf(CircuitWorkflowStep.IDENTITY, CircuitWorkflowStep.LOAD, CircuitWorkflowStep.SUPPLY),
            availableTransformers = listOf(TransformerSnapshot("TX-01"))
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder, darkTheme = false)
        }

        assertHierarchyResponsiveness(
            expectedLevel = "0 (Root = 0)",
            expectedSource = "Transformer",
            expectedSourceType = "TRANSFORMER",
            expectedTxId = "TX-01"
        )
        assertCardBottomClearance()
    }

    @Test
    @Config(sdk = [33], qualifiers = "w412dp-h915dp-xhdpi", fontScale = 1.3f)
    fun test20_hierarchyCardResponsivenessAt412dpFont1_3() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(
                supplyCategory = "NORMAL",
                fedFrom = "Transformer",
                transformerId = "TX-01"
            ),
            initialStep = CircuitWorkflowStep.SUPPLY,
            initialVisited = setOf(CircuitWorkflowStep.IDENTITY, CircuitWorkflowStep.LOAD, CircuitWorkflowStep.SUPPLY),
            availableTransformers = listOf(TransformerSnapshot("TX-01"))
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder, darkTheme = true)
        }

        assertHierarchyResponsiveness(
            expectedLevel = "0 (Root = 0)",
            expectedSource = "Transformer",
            expectedSourceType = "TRANSFORMER",
            expectedTxId = "TX-01"
        )
    }

    @Test
    @Config(sdk = [33], qualifiers = "w320dp-h640dp-xhdpi", fontScale = 1.5f)
    fun test21_hierarchyCardWithLongSourceLabelAt320dpFont1_5() {
        val longSource = "Main Substation Medium Voltage Switchgear 11kV Section A Busbar"
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(
                supplyCategory = "NORMAL",
                fedFrom = longSource,
                transformerId = "TX-01"
            ),
            initialStep = CircuitWorkflowStep.SUPPLY,
            initialVisited = setOf(CircuitWorkflowStep.IDENTITY, CircuitWorkflowStep.LOAD, CircuitWorkflowStep.SUPPLY),
            availableTransformers = listOf(TransformerSnapshot("TX-01"))
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder, darkTheme = true)
        }

        assertHierarchyResponsiveness(
            expectedLevel = "0 (Root = 0)",
            expectedSource = longSource,
            expectedSourceType = "TRANSFORMER",
            expectedTxId = "TX-01"
        )
        assertCardBottomClearance()
    }
}

