package com.example.ui.dialogs.circuiteditor.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.data.CircuitEntity
import com.example.ui.dialogs.circuiteditor.mapper.CircuitSourceResolution
import com.example.ui.dialogs.circuiteditor.model.CircuitEditorDraft
import com.example.ui.dialogs.circuiteditor.model.CircuitWorkflow
import com.example.ui.dialogs.circuiteditor.model.CircuitWorkflowStep
import com.example.ui.dialogs.circuiteditor.ui.steps.ReviewStepContent
import com.example.ui.dialogs.circuiteditor.validation.CircuitEditorValidator
import com.example.ui.dialogs.circuiteditor.validation.ValidationResult
import com.example.ui.theme.VoltAtlasTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Runtime test suite for Step 7 (or 6 for LOAD circuits): Review & Save.
 *
 * Covers:
 * - Terminal action contract (Tests 1–13)
 * - Review accuracy (Tests 14–21)
 *
 * Evidence Label: NEW_EDITOR_RUNTIME_OBSERVED
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
class ReviewStepRuntimeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val testResolution = CircuitSourceResolution(
        finalTxId = "TX-01",
        parentBoardObj = null,
        calculatedParentId = null,
        calculatedLevel = 1,
        calculatedSourceType = "Transformer"
    )

    // ==========================================
    // Terminal Action Contract (Tests 1–13)
    // ==========================================

    @Test
    fun test01_nonFinalStepExposesNextAndNoSave() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "Circuit 1", panelType = "LOAD"),
            initialStep = CircuitWorkflowStep.INSTALLATION,
            initialVisited = setOf(
                CircuitWorkflowStep.IDENTITY,
                CircuitWorkflowStep.LOAD,
                CircuitWorkflowStep.SUPPLY,
                CircuitWorkflowStep.FEEDER,
                CircuitWorkflowStep.INSTALLATION
            )
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        composeTestRule.onNodeWithTag("circuit_editor_next_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("save_circuit_btn").assertDoesNotExist()
    }

    @Test
    fun test02_loadReviewExposesBackAndSaveAndNoNext() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "Motor 1", panelType = "LOAD"),
            initialStep = CircuitWorkflowStep.REVIEW,
            initialVisited = setOf(
                CircuitWorkflowStep.IDENTITY,
                CircuitWorkflowStep.LOAD,
                CircuitWorkflowStep.SUPPLY,
                CircuitWorkflowStep.FEEDER,
                CircuitWorkflowStep.INSTALLATION,
                CircuitWorkflowStep.REVIEW
            )
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        composeTestRule.onNodeWithTag("circuit_editor_back_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("save_circuit_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("circuit_editor_next_btn").assertDoesNotExist()
    }

    @Test
    fun test03_panelReviewExposesBackAndSaveAndNoNext() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "Main DB", panelType = "DB"),
            initialStep = CircuitWorkflowStep.REVIEW,
            initialVisited = setOf(
                CircuitWorkflowStep.IDENTITY,
                CircuitWorkflowStep.LOAD,
                CircuitWorkflowStep.SUPPLY,
                CircuitWorkflowStep.FEEDER,
                CircuitWorkflowStep.INSTALLATION,
                CircuitWorkflowStep.PANEL_HEADER,
                CircuitWorkflowStep.REVIEW
            )
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        composeTestRule.onNodeWithTag("circuit_editor_back_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("save_circuit_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("circuit_editor_next_btn").assertDoesNotExist()
    }

    @Test
    fun test04_exactlyOneSaveCircuitBtnExists() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "Panel A", panelType = "DB"),
            initialStep = CircuitWorkflowStep.REVIEW,
            initialVisited = setOf(
                CircuitWorkflowStep.IDENTITY,
                CircuitWorkflowStep.LOAD,
                CircuitWorkflowStep.SUPPLY,
                CircuitWorkflowStep.FEEDER,
                CircuitWorkflowStep.INSTALLATION,
                CircuitWorkflowStep.PANEL_HEADER,
                CircuitWorkflowStep.REVIEW
            )
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        val saveButtons = composeTestRule.onAllNodesWithTag("save_circuit_btn").fetchSemanticsNodes()
        assertEquals("Exactly one save_circuit_btn must exist", 1, saveButtons.size)
    }

    @Test
    fun test05_saveRemainsVisibleWhileReviewContentScrolls() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "Panel A", panelType = "DB"),
            initialStep = CircuitWorkflowStep.REVIEW,
            initialVisited = setOf(
                CircuitWorkflowStep.IDENTITY,
                CircuitWorkflowStep.LOAD,
                CircuitWorkflowStep.SUPPLY,
                CircuitWorkflowStep.FEEDER,
                CircuitWorkflowStep.INSTALLATION,
                CircuitWorkflowStep.PANEL_HEADER,
                CircuitWorkflowStep.REVIEW
            )
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        // Before scroll
        composeTestRule.onNodeWithTag("save_circuit_btn").assertIsDisplayed()

        // Scroll content to bottom spacer
        composeTestRule.onNodeWithTag("content_bottom_clearance_spacer").performScrollTo()

        // After scroll, sticky Save is still visible
        composeTestRule.onNodeWithTag("save_circuit_btn").assertIsDisplayed()
    }

    @Test
    fun test06_backFromReviewNavigatesToInstallationForLoad() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "Load Feeder", panelType = "LOAD"),
            initialStep = CircuitWorkflowStep.REVIEW,
            initialVisited = setOf(
                CircuitWorkflowStep.IDENTITY,
                CircuitWorkflowStep.LOAD,
                CircuitWorkflowStep.SUPPLY,
                CircuitWorkflowStep.FEEDER,
                CircuitWorkflowStep.INSTALLATION,
                CircuitWorkflowStep.REVIEW
            )
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        composeTestRule.onNodeWithTag("circuit_editor_back_btn").performClick()
        assertEquals(CircuitWorkflowStep.INSTALLATION, holder.currentStep)
        composeTestRule.onNodeWithTag("circuit_editor_step_installation").assertIsDisplayed()
    }

    @Test
    fun test07_backFromReviewNavigatesToPanelHeaderForPanel() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "Panel DB", panelType = "DB"),
            initialStep = CircuitWorkflowStep.REVIEW,
            initialVisited = setOf(
                CircuitWorkflowStep.IDENTITY,
                CircuitWorkflowStep.LOAD,
                CircuitWorkflowStep.SUPPLY,
                CircuitWorkflowStep.FEEDER,
                CircuitWorkflowStep.INSTALLATION,
                CircuitWorkflowStep.PANEL_HEADER,
                CircuitWorkflowStep.REVIEW
            )
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        composeTestRule.onNodeWithTag("circuit_editor_back_btn").performClick()
        assertEquals(CircuitWorkflowStep.PANEL_HEADER, holder.currentStep)
        composeTestRule.onNodeWithTag("circuit_editor_step_panel_header").assertIsDisplayed()
    }

    @Test
    fun test08_validSaveCallbackFiresExactlyOnce() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "Feeder Valid", panelType = "LOAD", length_m = "50"),
            initialStep = CircuitWorkflowStep.REVIEW,
            initialVisited = setOf(
                CircuitWorkflowStep.IDENTITY,
                CircuitWorkflowStep.LOAD,
                CircuitWorkflowStep.SUPPLY,
                CircuitWorkflowStep.FEEDER,
                CircuitWorkflowStep.INSTALLATION,
                CircuitWorkflowStep.REVIEW
            )
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        assertEquals(0, holder.saveRequestedCount)
        composeTestRule.onNodeWithTag("save_circuit_btn").performClick()
        assertEquals(1, holder.saveRequestedCount)
        assertEquals("Feeder Valid", holder.lastSavedDraft?.name)
    }

    @Test
    fun test09_invalidSaveCallbackNeverFires() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "", panelType = "LOAD", length_m = "0"),
            initialStep = CircuitWorkflowStep.REVIEW,
            initialVisited = setOf(
                CircuitWorkflowStep.IDENTITY,
                CircuitWorkflowStep.LOAD,
                CircuitWorkflowStep.SUPPLY,
                CircuitWorkflowStep.FEEDER,
                CircuitWorkflowStep.INSTALLATION,
                CircuitWorkflowStep.REVIEW
            )
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        composeTestRule.onNodeWithTag("save_circuit_btn").performClick()
        assertEquals(0, holder.saveRequestedCount)
        assertNull(holder.lastSavedDraft)
    }

    @Test
    fun test10_blankNameSaveDisplaysExactErrorAndNavigatesToIdentity() {
        var currentStep by mutableStateOf(CircuitWorkflowStep.REVIEW)
        var reviewError by mutableStateOf<String?>(null)
        var saveCount = 0
        val draft = CircuitEditorDraft(name = "", panelType = "LOAD")

        val events = object : CircuitEditorUiEvents {
            override fun onDraftChange(newDraft: CircuitEditorDraft) {}
            override fun onStepChange(targetStep: CircuitWorkflowStep) { currentStep = targetStep }
            override fun onNextClick() {}
            override fun onBackClick() {}
            override fun onDismiss() {}
            override fun onAddTransformer() {}
            override fun onSaveClick() {
                val validation = CircuitEditorValidator.validateForSave(draft, emptyList(), 0)
                when (validation) {
                    is ValidationResult.Error -> {
                        reviewError = validation.message
                        currentStep = validation.targetStep
                    }
                    is ValidationResult.Success -> {
                        saveCount++
                    }
                }
            }
        }

        composeTestRule.setContent {
            VoltAtlasTheme {
                CircuitEditorParallelHost(
                    state = CircuitEditorUiState(
                        draft = draft,
                        currentStep = currentStep,
                        sourceResolution = testResolution,
                        reviewError = reviewError
                    ),
                    events = events
                )
            }
        }

        composeTestRule.onNodeWithTag("save_circuit_btn").performClick()

        assertEquals(CircuitWorkflowStep.IDENTITY, currentStep)
        assertEquals(0, saveCount)
        assertEquals("Circuit/Panel name cannot be empty.", reviewError)
        composeTestRule.onNodeWithTag("review_validation_error").assertIsDisplayed()
        composeTestRule.onNodeWithText("Circuit/Panel name cannot be empty.").assertIsDisplayed()
    }

    @Test
    fun test11_nonPositiveLengthSaveDisplaysExactErrorAndNavigatesToFeeder() {
        var currentStep by mutableStateOf(CircuitWorkflowStep.REVIEW)
        var reviewError by mutableStateOf<String?>(null)
        var saveCount = 0
        val draft = CircuitEditorDraft(name = "Feeder Zero", panelType = "LOAD", length_m = "0")

        val events = object : CircuitEditorUiEvents {
            override fun onDraftChange(newDraft: CircuitEditorDraft) {}
            override fun onStepChange(targetStep: CircuitWorkflowStep) { currentStep = targetStep }
            override fun onNextClick() {}
            override fun onBackClick() {}
            override fun onDismiss() {}
            override fun onAddTransformer() {}
            override fun onSaveClick() {
                val validation = CircuitEditorValidator.validateForSave(draft, emptyList(), 0)
                when (validation) {
                    is ValidationResult.Error -> {
                        reviewError = validation.message
                        currentStep = validation.targetStep
                    }
                    is ValidationResult.Success -> {
                        saveCount++
                    }
                }
            }
        }

        composeTestRule.setContent {
            VoltAtlasTheme {
                CircuitEditorParallelHost(
                    state = CircuitEditorUiState(
                        draft = draft,
                        currentStep = currentStep,
                        sourceResolution = testResolution,
                        reviewError = reviewError
                    ),
                    events = events
                )
            }
        }

        composeTestRule.onNodeWithTag("save_circuit_btn").performClick()

        assertEquals(CircuitWorkflowStep.FEEDER, currentStep)
        assertEquals(0, saveCount)
        assertEquals("Cable length must be greater than zero.", reviewError)
        composeTestRule.onNodeWithTag("review_validation_error").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cable length must be greater than zero.").assertIsDisplayed()
    }

    @Test
    fun test12_errorStateDoesNotCreateDuplicateSaveOrCoverStickyActionBar() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "Feeder Valid", panelType = "LOAD"),
            initialStep = CircuitWorkflowStep.REVIEW,
            initialVisited = setOf(
                CircuitWorkflowStep.IDENTITY,
                CircuitWorkflowStep.LOAD,
                CircuitWorkflowStep.SUPPLY,
                CircuitWorkflowStep.FEEDER,
                CircuitWorkflowStep.INSTALLATION,
                CircuitWorkflowStep.REVIEW
            )
        )
        holder.reviewError = "A simulated validation error"
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        // Exactly one save button
        val saveButtons = composeTestRule.onAllNodesWithTag("save_circuit_btn").fetchSemanticsNodes()
        assertEquals(1, saveButtons.size)

        // Error banner is displayed
        composeTestRule.onNodeWithTag("review_validation_error").assertIsDisplayed()

        // Error banner does not cover action bar: error banner bottom <= action bar top
        val errorBounds = composeTestRule.onNodeWithTag("review_validation_error").getBoundsInRoot()
        val saveBounds = composeTestRule.onNodeWithTag("save_circuit_btn").getBoundsInRoot()
        assertTrue("Validation banner must sit above the save button", errorBounds.bottom <= saveBounds.top)
    }

    @Test
    fun test13_systemBackFromReviewNavigatesBackwardRatherThanDismissing() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "Load Feeder", panelType = "LOAD"),
            initialStep = CircuitWorkflowStep.REVIEW,
            initialVisited = setOf(
                CircuitWorkflowStep.IDENTITY,
                CircuitWorkflowStep.LOAD,
                CircuitWorkflowStep.SUPPLY,
                CircuitWorkflowStep.FEEDER,
                CircuitWorkflowStep.INSTALLATION,
                CircuitWorkflowStep.REVIEW
            )
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        holder.events.onBackClick()
        assertEquals(CircuitWorkflowStep.INSTALLATION, holder.currentStep)
        assertEquals(0, holder.dismissCount)
    }

    // ==========================================
    // Review Accuracy (Tests 14–21)
    // ==========================================

    @Test
    fun test14_cableReviewHasNoBuswayOnlyRows() {
        val draft = CircuitEditorDraft(
            name = "Cable Feeder",
            panelType = "LOAD",
            feederType = "Cable",
            manufacturer = "Elsewedy",
            cableType = "Cu/XLPE/SWA/PVC - 4C"
        )
        composeTestRule.setContent {
            VoltAtlasTheme {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    ReviewStepContent(
                        draft = draft,
                        resolution = testResolution,
                        onEditSection = {}
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Busway Model").assertDoesNotExist()
    }

    @Test
    fun test15_buswayReviewHasNoCableOnlyRows() {
        val draft = CircuitEditorDraft(
            name = "Busway Riser",
            panelType = "LOAD",
            feederType = "Busway",
            buswayMfg = "Elsewedy",
            buswayModel = "Power Link"
        )
        composeTestRule.setContent {
            VoltAtlasTheme {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    ReviewStepContent(
                        draft = draft,
                        resolution = testResolution,
                        onEditSection = {}
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Cable Family").assertDoesNotExist()
    }

    @Test
    fun test16_normalSupplyReviewHasNoActiveAtsGeneratorUpsRows() {
        val draft = CircuitEditorDraft(
            name = "Normal Feeder",
            panelType = "LOAD",
            supplyCategory = "NORMAL",
            assignedAtsId = "ATS-HIDDEN",
            assignedGeneratorId = "GEN-HIDDEN",
            assignedUpsId = "UPS-HIDDEN"
        )
        composeTestRule.setContent {
            VoltAtlasTheme {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    ReviewStepContent(
                        draft = draft,
                        resolution = testResolution,
                        onEditSection = {}
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Assigned ATS").assertDoesNotExist()
        composeTestRule.onNodeWithText("Assigned Generator").assertDoesNotExist()
        composeTestRule.onNodeWithText("Assigned UPS").assertDoesNotExist()
    }

    @Test
    fun test17_emergencyReviewShowsOnlyApplicableAtsGeneratorInfo() {
        val draft = CircuitEditorDraft(
            name = "Emergency Feeder",
            panelType = "LOAD",
            supplyCategory = "EMERGENCY",
            assignedAtsId = "ATS-01",
            assignedGeneratorId = "GEN-01",
            assignedUpsId = "UPS-HIDDEN"
        )
        composeTestRule.setContent {
            VoltAtlasTheme {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    ReviewStepContent(
                        draft = draft,
                        resolution = testResolution,
                        onEditSection = {}
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Assigned ATS").assertExists()
        composeTestRule.onNodeWithText("ATS-01").assertExists()
        composeTestRule.onNodeWithText("Assigned Generator").assertExists()
        composeTestRule.onNodeWithText("GEN-01").assertExists()
        composeTestRule.onNodeWithText("Assigned UPS").assertDoesNotExist()
    }

    @Test
    fun test18_upsReviewShowsOnlyApplicableUpsInfo() {
        val draft = CircuitEditorDraft(
            name = "UPS Feeder",
            panelType = "LOAD",
            supplyCategory = "UPS",
            assignedAtsId = "ATS-HIDDEN",
            assignedGeneratorId = "GEN-HIDDEN",
            assignedUpsId = "UPS-01"
        )
        composeTestRule.setContent {
            VoltAtlasTheme {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    ReviewStepContent(
                        draft = draft,
                        resolution = testResolution,
                        onEditSection = {}
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Assigned UPS").assertExists()
        composeTestRule.onNodeWithText("UPS-01").assertExists()
        composeTestRule.onNodeWithText("Assigned ATS").assertDoesNotExist()
        composeTestRule.onNodeWithText("Assigned Generator").assertDoesNotExist()
    }

    @Test
    fun test19_reviewDoesNotCalculateDesignCurrentFromDraftValues() {
        val draft = CircuitEditorDraft(
            name = "Motor Feeder",
            panelType = "LOAD",
            loadKVA = "50.0",
            pf = "0.85",
            demandFactor = "1.0",
            voltageV = "400",
            phases = "3P+N"
        )
        composeTestRule.setContent {
            VoltAtlasTheme {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    ReviewStepContent(
                        draft = draft,
                        resolution = testResolution,
                        onEditSection = {}
                    )
                }
            }
        }

        // Must NOT invent or calculate "Design Current"
        composeTestRule.onNodeWithText("Design Current", substring = true).assertDoesNotExist()
    }

    @Test
    fun test20_panelHeaderReviewObeysExactApplicability() {
        var currentDraft by mutableStateOf(CircuitEditorDraft(name = "Load 1", panelType = "LOAD"))

        composeTestRule.setContent {
            VoltAtlasTheme {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    ReviewStepContent(
                        draft = currentDraft,
                        resolution = testResolution,
                        onEditSection = {}
                    )
                }
            }
        }
        composeTestRule.onNodeWithTag("review_panel_header_section").assertDoesNotExist()

        currentDraft = CircuitEditorDraft(name = "DB 1", panelType = "DB")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("review_panel_header_section").assertExists()
    }

    @Test
    fun test21_allReviewEditActionsNavigateCorrectlyAndRemainUnique() {
        var navigatedStep: CircuitWorkflowStep? = null
        val dbDraft = CircuitEditorDraft(name = "DB 1", panelType = "DB")

        composeTestRule.setContent {
            VoltAtlasTheme {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    ReviewStepContent(
                        draft = dbDraft,
                        resolution = testResolution,
                        onEditSection = { navigatedStep = it }
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("review_edit_identity").performScrollTo().performClick()
        assertEquals(CircuitWorkflowStep.IDENTITY, navigatedStep)

        composeTestRule.onNodeWithTag("review_edit_load").performScrollTo().performClick()
        assertEquals(CircuitWorkflowStep.LOAD, navigatedStep)

        composeTestRule.onNodeWithTag("review_edit_supply").performScrollTo().performClick()
        assertEquals(CircuitWorkflowStep.SUPPLY, navigatedStep)

        composeTestRule.onNodeWithTag("review_edit_feeder").performScrollTo().performClick()
        assertEquals(CircuitWorkflowStep.FEEDER, navigatedStep)

        composeTestRule.onNodeWithTag("review_edit_installation").performScrollTo().performClick()
        assertEquals(CircuitWorkflowStep.INSTALLATION, navigatedStep)

        composeTestRule.onNodeWithTag("review_edit_panel_header").performScrollTo().performClick()
        assertEquals(CircuitWorkflowStep.PANEL_HEADER, navigatedStep)
    }

    @Test
    fun test22_scrolledReviewCardDoesNotIntrudeIntoFixedTopRegion() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "Main DB", panelType = "DB"),
            initialStep = CircuitWorkflowStep.REVIEW,
            initialVisited = setOf(
                CircuitWorkflowStep.IDENTITY,
                CircuitWorkflowStep.LOAD,
                CircuitWorkflowStep.SUPPLY,
                CircuitWorkflowStep.FEEDER,
                CircuitWorkflowStep.INSTALLATION,
                CircuitWorkflowStep.PANEL_HEADER,
                CircuitWorkflowStep.REVIEW
            )
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        val topBar = composeTestRule.onNodeWithTag("circuit_editor_top_fixed_bar")
        topBar.assertExists()
        val topBarBounds = topBar.getBoundsInRoot()

        val scrollViewport = composeTestRule.onNodeWithTag("circuit_editor_scroll_viewport")
        scrollViewport.assertExists()
        val viewportBounds = scrollViewport.getBoundsInRoot()

        // 1. Fixed top bar must be above or at the top of the viewport
        assertTrue(
            "Viewport top (${viewportBounds.top}) must start at or below top bar bottom (${topBarBounds.bottom})",
            viewportBounds.top >= topBarBounds.bottom
        )

        // 2. Scroll to bottom
        composeTestRule.onNodeWithTag("content_bottom_clearance_spacer").performScrollTo()
        composeTestRule.waitForIdle()

        // 3. The top fixed bar must NOT contain any review card items as children/descendants
        composeTestRule.onNode(
            androidx.compose.ui.test.hasTestTag("circuit_editor_top_fixed_bar") and
            androidx.compose.ui.test.hasAnyDescendant(androidx.compose.ui.test.hasTestTag("review_identity_section"))
        ).assertDoesNotExist()

        composeTestRule.onNode(
            androidx.compose.ui.test.hasTestTag("circuit_editor_top_fixed_bar") and
            androidx.compose.ui.test.hasAnyDescendant(androidx.compose.ui.test.hasTestTag("review_panel_header_section"))
        ).assertDoesNotExist()

        // 4. Assert that when scrolled to bottom, review card sections do not have visible bounds overlapping into topBar
        val panelSection = composeTestRule.onNodeWithTag("review_panel_header_section")
        panelSection.assertExists()
        val panelSectionBounds = panelSection.getBoundsInRoot()
        assertTrue(
            "Panel section top (${panelSectionBounds.top}) must be >= topBarBounds.bottom (${topBarBounds.bottom})",
            panelSectionBounds.top >= topBarBounds.bottom
        )
    }

    // ==========================================
    // R1B closure: Review transformer display honesty
    // ==========================================

    // 23. A supplied real transformer ID is displayed exactly as supplied.
    @Test
    fun test23_reviewDisplaysSuppliedRealTransformerIdExactly() {
        val realResolution = CircuitSourceResolution(
            finalTxId = "TR-REAL-42",
            parentBoardObj = null,
            calculatedParentId = null,
            calculatedLevel = 0,
            calculatedSourceType = "TRANSFORMER"
        )
        val draft = CircuitEditorDraft(name = "Real Tx Feeder", panelType = "LOAD", fedFrom = "Transformer")
        composeTestRule.setContent {
            VoltAtlasTheme {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    ReviewStepContent(
                        draft = draft,
                        resolution = realResolution,
                        onEditSection = {}
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("TR-REAL-42").assertIsDisplayed()
        composeTestRule.onNodeWithText("TX-01 (Auto-resolved)").assertDoesNotExist()
    }

    // 24. A blank resolution must render the neutral unavailable label and must never fabricate
    // a transformer ID or imply that one was auto-resolved.
    @Test
    fun test24_reviewBlankResolutionShowsNeutralLabelWithoutFabrication() {
        val blankResolution = CircuitSourceResolution(
            finalTxId = "",
            parentBoardObj = null,
            calculatedParentId = null,
            calculatedLevel = 0,
            calculatedSourceType = "TRANSFORMER"
        )
        val draft = CircuitEditorDraft(name = "No Tx Feeder", panelType = "LOAD", fedFrom = "Transformer")
        composeTestRule.setContent {
            VoltAtlasTheme {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    ReviewStepContent(
                        draft = draft,
                        resolution = blankResolution,
                        onEditSection = {}
                    )
                }
            }
        }

        // Neutral unavailable label, consistent with the Identity/Supply steps.
        composeTestRule.onNodeWithText("None").assertExists()
        // No fabricated identifier and no fabricated "auto-resolved" claim.
        composeTestRule.onNodeWithText("TX-01 (Auto-resolved)").assertDoesNotExist()
        composeTestRule.onNodeWithText("TX-01").assertDoesNotExist()
        composeTestRule.onAllNodesWithText("Auto-resolved").assertCountEquals(0)
    }

    // ==========================================
    // R5: Review Header Remediation & Scroll Reachability
    // ==========================================

    // 25. All Review edit targets satisfy >= 48dp and stay within card bounds at 320dp / font 1.5
    @Test
    fun test25_r5_reviewEditActionBoundsAndTargetSize_compactWidthLargeFont() {
        val panelDraft = CircuitEditorDraft(
            name = "Main DB",
            panelType = "DB",
            loadKVA = "150.0",
            supplyCategory = "EMERGENCY + UPS",
            assignedGeneratorId = "GEN-01",
            assignedAtsId = "ATS-01",
            assignedUpsId = "UPS-01",
            feederType = "Cable",
            busbarRatingA = "630"
        )
        val holder = CircuitEditorTestStateHolder(
            initialDraft = panelDraft,
            initialStep = CircuitWorkflowStep.REVIEW,
            initialVisited = setOf(
                CircuitWorkflowStep.IDENTITY,
                CircuitWorkflowStep.LOAD,
                CircuitWorkflowStep.SUPPLY,
                CircuitWorkflowStep.FEEDER,
                CircuitWorkflowStep.INSTALLATION,
                CircuitWorkflowStep.PANEL_HEADER,
                CircuitWorkflowStep.REVIEW
            )
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        val editTags = listOf(
            "review_edit_identity" to "review_identity_section",
            "review_edit_load" to "review_load_section",
            "review_edit_supply" to "review_supply_section",
            "review_edit_feeder" to "review_feeder_section",
            "review_edit_installation" to "review_installation_section",
            "review_edit_panel_header" to "review_panel_header_section"
        )

        for ((editTag, sectionTag) in editTags) {
            val sectionNode = composeTestRule.onNodeWithTag(sectionTag)
            sectionNode.performScrollTo()
            composeTestRule.waitForIdle()

            val cardBounds = sectionNode.getBoundsInRoot()
            val editBounds = composeTestRule.onNodeWithTag(editTag).getBoundsInRoot()

            val editWidth = editBounds.right - editBounds.left
            val editHeight = editBounds.bottom - editBounds.top
            assertTrue("$editTag width ($editWidth) must be >= 48dp", editWidth >= 48.dp)
            assertTrue("$editTag height ($editHeight) must be >= 48dp", editHeight >= 48.dp)
            assertTrue("$editTag right (${editBounds.right}) must be <= card right (${cardBounds.right})", editBounds.right <= cardBounds.right)
            assertTrue("$editTag top (${editBounds.top}) must be >= card top (${cardBounds.top})", editBounds.top >= cardBounds.top)
        }
    }

    // 26. Programmatic scrolling to a section positions complete heading at or below scrollViewport.top
    @Test
    fun test26_r5_reviewSectionScrollReachability() {
        val panelDraft = CircuitEditorDraft(
            name = "Main DB",
            panelType = "DB",
            loadKVA = "150.0",
            busbarRatingA = "630"
        )
        val holder = CircuitEditorTestStateHolder(
            initialDraft = panelDraft,
            initialStep = CircuitWorkflowStep.REVIEW,
            initialVisited = setOf(
                CircuitWorkflowStep.IDENTITY,
                CircuitWorkflowStep.LOAD,
                CircuitWorkflowStep.SUPPLY,
                CircuitWorkflowStep.FEEDER,
                CircuitWorkflowStep.INSTALLATION,
                CircuitWorkflowStep.PANEL_HEADER,
                CircuitWorkflowStep.REVIEW
            )
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        val viewportBounds = composeTestRule.onNodeWithTag("circuit_editor_scroll_viewport").getBoundsInRoot()
        val panelSection = composeTestRule.onNodeWithTag("review_panel_header_section")
        panelSection.performScrollTo()
        composeTestRule.waitForIdle()

        val panelBounds = panelSection.getBoundsInRoot()
        assertTrue(
            "Panel header section top (${panelBounds.top}) must be >= scrollViewport top (${viewportBounds.top})",
            panelBounds.top >= viewportBounds.top
        )
        assertTrue(
            "Panel header section top (${panelBounds.top}) must be < scrollViewport bottom (${viewportBounds.bottom})",
            panelBounds.top < viewportBounds.bottom
        )
    }

    // 27. Direct Review edit action navigates to target step without stale Review scroll offset
    @Test
    fun test27_r5_reviewScrollResetOnStepNavigation() {
        var navigated = false
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "Main DB", panelType = "DB", busbarRatingA = "630"),
            initialStep = CircuitWorkflowStep.REVIEW,
            initialVisited = setOf(
                CircuitWorkflowStep.IDENTITY,
                CircuitWorkflowStep.LOAD,
                CircuitWorkflowStep.SUPPLY,
                CircuitWorkflowStep.FEEDER,
                CircuitWorkflowStep.INSTALLATION,
                CircuitWorkflowStep.PANEL_HEADER,
                CircuitWorkflowStep.REVIEW
            )
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        // Scroll to bottom of Review
        composeTestRule.onNodeWithTag("content_bottom_clearance_spacer").performScrollTo()
        composeTestRule.waitForIdle()

        // Click Edit on Identity section
        composeTestRule.onNodeWithTag("review_edit_identity").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // Now on Identity step
        assertEquals(CircuitWorkflowStep.IDENTITY, holder.currentStep)
        // Step header is displayed cleanly at top
        composeTestRule.onNodeWithTag("circuit_editor_step_header").assertIsDisplayed()
    }

    // 28. Bottom scrolling reaches final content with at least 16dp clearance above sticky action bar
    @Test
    fun test28_r5_reviewBottomClearanceAboveStickyBar() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "Main DB", panelType = "DB", busbarRatingA = "630"),
            initialStep = CircuitWorkflowStep.REVIEW,
            initialVisited = setOf(
                CircuitWorkflowStep.IDENTITY,
                CircuitWorkflowStep.LOAD,
                CircuitWorkflowStep.SUPPLY,
                CircuitWorkflowStep.FEEDER,
                CircuitWorkflowStep.INSTALLATION,
                CircuitWorkflowStep.PANEL_HEADER,
                CircuitWorkflowStep.REVIEW
            )
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        composeTestRule.onNodeWithTag("content_bottom_clearance_spacer").performScrollTo()
        composeTestRule.waitForIdle()

        val saveBtn = composeTestRule.onNodeWithTag("save_circuit_btn").getBoundsInRoot()
        val panelSection = composeTestRule.onNodeWithTag("review_panel_header_section").getBoundsInRoot()
        val root = composeTestRule.onNodeWithTag("circuit_editor_root").getBoundsInRoot()

        assertTrue("Last content bottom must be <= saveBtn.top - 16dp", panelSection.bottom <= saveBtn.top - 16.dp)
        assertTrue("Save button bottom must be inside visible root", saveBtn.bottom <= root.bottom)
    }

    // ==========================================
    // R6: Review Target Contract & Return Scroll Verification
    // ==========================================

    // 29. Initial entry into Review starts at scroll offset 0
    @Test
    fun test29_r6_initialReviewEntryStartsAtScrollZero() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "Pump 1", panelType = "LOAD"),
            initialStep = CircuitWorkflowStep.REVIEW,
            initialVisited = setOf(
                CircuitWorkflowStep.IDENTITY,
                CircuitWorkflowStep.LOAD,
                CircuitWorkflowStep.SUPPLY,
                CircuitWorkflowStep.FEEDER,
                CircuitWorkflowStep.INSTALLATION,
                CircuitWorkflowStep.REVIEW
            )
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        composeTestRule.onNodeWithTag("circuit_editor_step_header").assertIsDisplayed()
        val identityCard = composeTestRule.onNodeWithTag("review_identity_section").getBoundsInRoot()
        val viewport = composeTestRule.onNodeWithTag("circuit_editor_scroll_viewport").getBoundsInRoot()
        assertTrue("Identity card top is at or below viewport top", identityCard.top >= viewport.top)
    }

    // 30. Explicitly targeted Panel Header section satisfies all viewport containment bounds
    @Test
    fun test30_r6_panelHeaderTargetSatisfiesAllViewportBounds() {
        val panelDraft = CircuitEditorDraft(
            name = "Main DB",
            panelType = "DB",
            loadKVA = "150.0",
            busbarRatingA = "630"
        )
        val holder = CircuitEditorTestStateHolder(
            initialDraft = panelDraft,
            initialStep = CircuitWorkflowStep.REVIEW,
            initialVisited = setOf(
                CircuitWorkflowStep.IDENTITY,
                CircuitWorkflowStep.LOAD,
                CircuitWorkflowStep.SUPPLY,
                CircuitWorkflowStep.FEEDER,
                CircuitWorkflowStep.INSTALLATION,
                CircuitWorkflowStep.PANEL_HEADER,
                CircuitWorkflowStep.REVIEW
            )
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        val headerNode = composeTestRule.onNodeWithTag("review_panel_header_section_header")
        headerNode.performScrollTo()
        composeTestRule.waitForIdle()

        val viewport = composeTestRule.onNodeWithTag("circuit_editor_scroll_viewport").getBoundsInRoot()
        val sectionHeader = headerNode.getBoundsInRoot()
        val editAction = composeTestRule.onNodeWithTag("review_edit_panel_header").getBoundsInRoot()
        val titleNode = composeTestRule.onNodeWithTag("review_panel_header_section_title").getBoundsInRoot()

        assertTrue("sectionHeader.top >= scrollViewport.top", sectionHeader.top >= viewport.top)
        assertTrue("sectionHeader.bottom <= scrollViewport.bottom", sectionHeader.bottom <= viewport.bottom)
        assertTrue("editAction.top >= scrollViewport.top", editAction.top >= viewport.top)
        assertTrue("editAction.bottom <= scrollViewport.bottom", editAction.bottom <= viewport.bottom)
        assertTrue("titleNode.top >= scrollViewport.top", titleNode.top >= viewport.top)
        assertTrue("titleNode.bottom <= scrollViewport.bottom", titleNode.bottom <= viewport.bottom)

        // Complete text present in semantics
        composeTestRule.onNodeWithText("6. Panel Header Equipment").assertExists()

        // Minimum 48dp touch target
        val editWidth = editAction.right - editAction.left
        val editHeight = editAction.bottom - editAction.top
        assertTrue("editAction width >= 48dp", editWidth >= 48.dp)
        assertTrue("editAction height >= 48dp", editHeight >= 48.dp)
    }

    // 31. Returning to Review from an edited step resets scroll position to top (offset 0)
    @Test
    fun test31_r6_returnToReviewFromEditedStepResetsScrollToTop() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "Main DB", panelType = "DB", busbarRatingA = "630"),
            initialStep = CircuitWorkflowStep.REVIEW,
            initialVisited = setOf(
                CircuitWorkflowStep.IDENTITY,
                CircuitWorkflowStep.LOAD,
                CircuitWorkflowStep.SUPPLY,
                CircuitWorkflowStep.FEEDER,
                CircuitWorkflowStep.INSTALLATION,
                CircuitWorkflowStep.PANEL_HEADER,
                CircuitWorkflowStep.REVIEW
            )
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        // Scroll to bottom of Review
        composeTestRule.onNodeWithTag("content_bottom_clearance_spacer").performScrollTo()
        composeTestRule.waitForIdle()

        // Click Edit on Identity section
        composeTestRule.onNodeWithTag("review_edit_identity").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(CircuitWorkflowStep.IDENTITY, holder.currentStep)

        // Return to Review
        holder.events.onStepChange(CircuitWorkflowStep.REVIEW)
        composeTestRule.waitForIdle()

        assertEquals(CircuitWorkflowStep.REVIEW, holder.currentStep)
        // Review starts at scroll offset 0: top header is displayed
        composeTestRule.onNodeWithTag("circuit_editor_step_header").assertIsDisplayed()
        val identitySection = composeTestRule.onNodeWithTag("review_identity_section").getBoundsInRoot()
        val viewport = composeTestRule.onNodeWithTag("circuit_editor_scroll_viewport").getBoundsInRoot()
        assertTrue("Identity section top at top of viewport", identitySection.top >= viewport.top)
    }
}
