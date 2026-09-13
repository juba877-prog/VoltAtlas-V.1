package com.example.ui.dialogs.circuiteditor.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.data.CircuitEntity
import com.example.ui.dialogs.circuiteditor.mapper.CircuitSourceResolution
import com.example.ui.dialogs.circuiteditor.model.CircuitEditorDraft
import com.example.ui.dialogs.circuiteditor.model.CircuitWorkflowStep
import com.example.ui.dialogs.circuiteditor.ui.steps.ReviewStepContent
import com.example.ui.theme.VoltAtlasTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Runtime test suite for Step 7 (or 6 for LOAD circuits): Review & Save.
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

    @Test
    fun test18_reviewRendersAllApplicableSectionsForLoadCircuit() {
        val loadDraft = CircuitEditorDraft(name = "Load Circuit 1", panelType = "LOAD")
        composeTestRule.setContent {
            VoltAtlasTheme {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    ReviewStepContent(
                        draft = loadDraft,
                        resolution = testResolution,
                        onEditSection = {},
                        onSaveClick = {}
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("circuit_editor_step_review").assertExists()
        composeTestRule.onNodeWithTag("review_identity_section").assertExists()
        composeTestRule.onNodeWithTag("review_load_section").assertExists()
        composeTestRule.onNodeWithTag("review_supply_section").assertExists()
        composeTestRule.onNodeWithTag("review_feeder_section").assertExists()
        composeTestRule.onNodeWithTag("review_installation_section").assertExists()

        // For panelType == "LOAD", Panel Header section MUST NOT exist
        composeTestRule.onNodeWithTag("review_panel_header_section").assertDoesNotExist()
    }

    @Test
    fun test19_reviewRendersPanelHeaderSectionForDistributionBoard() {
        val dbDraft = CircuitEditorDraft(name = "Distribution Board 1", panelType = "DB")
        composeTestRule.setContent {
            VoltAtlasTheme {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    ReviewStepContent(
                        draft = dbDraft,
                        resolution = testResolution,
                        onEditSection = {},
                        onSaveClick = {}
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("review_panel_header_section").assertExists()
    }

    @Test
    fun test20_reviewDisplaysDraftValuesAccurately() {
        val detailedDraft = CircuitEditorDraft(
            name = "Main DB",
            panelType = "MDB",
            loadKVA = "150.0",
            pf = "0.90",
            demandFactor = "0.85",
            feederType = "Cable",
            manufacturer = "Elsewedy",
            cableType = "Cu/XLPE/SWA/PVC - 4C",
            length_m = "45",
            installation = "Laid in Ducts",
            airTempC = "45"
        )
        composeTestRule.setContent {
            VoltAtlasTheme {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    ReviewStepContent(
                        draft = detailedDraft,
                        resolution = testResolution,
                        onEditSection = {},
                        onSaveClick = {}
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Main DB").assertExists()
        composeTestRule.onNodeWithText("150.0 kVA").assertExists()
        composeTestRule.onNodeWithText("0.90").assertExists()
        composeTestRule.onNodeWithText("45 m").assertExists()
        composeTestRule.onNodeWithText("Laid in Ducts").assertExists()
        composeTestRule.onNodeWithText("45 °C").assertExists()
    }

    @Test
    fun test21_editIdentitySectionNavigatesToIdentity() {
        var navigatedStep: CircuitWorkflowStep? = null
        composeTestRule.setContent {
            VoltAtlasTheme {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    ReviewStepContent(
                        draft = CircuitEditorDraft(),
                        resolution = testResolution,
                        onEditSection = { navigatedStep = it },
                        onSaveClick = {}
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("review_edit_identity").performScrollTo().performClick()
        assertEquals(CircuitWorkflowStep.IDENTITY, navigatedStep)
    }

    @Test
    fun test22_editLoadSectionNavigatesToLoad() {
        var navigatedStep: CircuitWorkflowStep? = null
        composeTestRule.setContent {
            VoltAtlasTheme {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    ReviewStepContent(
                        draft = CircuitEditorDraft(),
                        resolution = testResolution,
                        onEditSection = { navigatedStep = it },
                        onSaveClick = {}
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("review_edit_load").performScrollTo().performClick()
        assertEquals(CircuitWorkflowStep.LOAD, navigatedStep)
    }

    @Test
    fun test23_editSupplySectionNavigatesToSupply() {
        var navigatedStep: CircuitWorkflowStep? = null
        composeTestRule.setContent {
            VoltAtlasTheme {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    ReviewStepContent(
                        draft = CircuitEditorDraft(),
                        resolution = testResolution,
                        onEditSection = { navigatedStep = it },
                        onSaveClick = {}
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("review_edit_supply").performScrollTo().performClick()
        assertEquals(CircuitWorkflowStep.SUPPLY, navigatedStep)
    }

    @Test
    fun test24_editFeederSectionNavigatesToFeeder() {
        var navigatedStep: CircuitWorkflowStep? = null
        composeTestRule.setContent {
            VoltAtlasTheme {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    ReviewStepContent(
                        draft = CircuitEditorDraft(),
                        resolution = testResolution,
                        onEditSection = { navigatedStep = it },
                        onSaveClick = {}
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("review_edit_feeder").performScrollTo().performClick()
        assertEquals(CircuitWorkflowStep.FEEDER, navigatedStep)
    }

    @Test
    fun test25_editInstallationSectionNavigatesToInstallation() {
        var navigatedStep: CircuitWorkflowStep? = null
        composeTestRule.setContent {
            VoltAtlasTheme {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    ReviewStepContent(
                        draft = CircuitEditorDraft(),
                        resolution = testResolution,
                        onEditSection = { navigatedStep = it },
                        onSaveClick = {}
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("review_edit_installation").performScrollTo().performClick()
        assertEquals(CircuitWorkflowStep.INSTALLATION, navigatedStep)
    }

    @Test
    fun test26_editPanelHeaderSectionNavigatesToPanelHeader() {
        var navigatedStep: CircuitWorkflowStep? = null
        composeTestRule.setContent {
            VoltAtlasTheme {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    ReviewStepContent(
                        draft = CircuitEditorDraft(panelType = "DB"),
                        resolution = testResolution,
                        onEditSection = { navigatedStep = it },
                        onSaveClick = {}
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("review_edit_panel_header").performScrollTo().performClick()
        assertEquals(CircuitWorkflowStep.PANEL_HEADER, navigatedStep)
    }

    @Test
    fun test27_saveCircuitValidationFailureDisplaysError() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "", panelType = "LOAD"),
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

        composeTestRule.onNodeWithTag("save_circuit_btn").performScrollTo().performClick()

        assertEquals(0, holder.saveRequestedCount)
        assertNotNull(holder.reviewError)
        assertEquals("Circuit/Panel name cannot be empty.", holder.reviewError)
        composeTestRule.onNodeWithTag("review_validation_error").assertExists()
        composeTestRule.onNodeWithText("Circuit/Panel name cannot be empty.").assertExists()
    }

    @Test
    fun test28_saveCircuitValidationSuccessInvokesSaveCallback() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "Valid Feeder 01", panelType = "LOAD", length_m = "35"),
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

        composeTestRule.onNodeWithTag("save_circuit_btn").performScrollTo().performClick()

        assertEquals(1, holder.saveRequestedCount)
        assertNull(holder.reviewError)
        assertNotNull(holder.lastSavedDraft)
        assertEquals("Valid Feeder 01", holder.lastSavedDraft?.name)
        composeTestRule.onNodeWithTag("review_validation_error").assertDoesNotExist()
    }

    @Test
    fun test29_duplicateNameValidationFailureDisplaysError() {
        val existing = listOf(
            CircuitEntity(id = 10, circuitName = "Pump Station DB", loadKVA = 50.0)
        )
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "pump station db", panelType = "LOAD"),
            initialStep = CircuitWorkflowStep.REVIEW,
            initialVisited = setOf(
                CircuitWorkflowStep.IDENTITY,
                CircuitWorkflowStep.LOAD,
                CircuitWorkflowStep.SUPPLY,
                CircuitWorkflowStep.FEEDER,
                CircuitWorkflowStep.INSTALLATION,
                CircuitWorkflowStep.REVIEW
            ),
            existingCircuits = existing
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder)
        }

        composeTestRule.onNodeWithTag("save_circuit_btn").performScrollTo().performClick()

        assertEquals(0, holder.saveRequestedCount)
        assertNotNull(holder.reviewError)
        assertEquals("A circuit/panel named 'pump station db' already exists — choose a different name.", holder.reviewError)
        composeTestRule.onNodeWithTag("review_validation_error").assertExists()
    }

    @Test
    @Config(sdk = [33], qualifiers = "w320dp-h800dp-xhdpi", fontScale = 1.5f)
    fun test30_responsiveLayoutAt320dpFont1_5() {
        val dbDraft = CircuitEditorDraft(
            name = "DB-Main",
            panelType = "SMDB",
            loadKVA = "75.0",
            installation = "Laid Direct in Ground"
        )
        composeTestRule.setContent {
            VoltAtlasTheme {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    ReviewStepContent(
                        draft = dbDraft,
                        resolution = testResolution,
                        onEditSection = {},
                        onSaveClick = {}
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("circuit_editor_step_review").assertExists()
        composeTestRule.onNodeWithTag("review_identity_section").assertExists()
        composeTestRule.onNodeWithTag("review_panel_header_section").assertExists()
        composeTestRule.onNodeWithTag("save_circuit_btn").assertExists()
    }
}
