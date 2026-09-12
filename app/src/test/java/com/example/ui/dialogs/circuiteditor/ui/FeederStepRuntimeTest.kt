package com.example.ui.dialogs.circuiteditor.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
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
import com.example.data.CableCatalogEntry
import com.example.data.CableType
import com.example.data.catalog.BuswayCatalogEntry
import com.example.ui.dialogs.circuiteditor.model.CableCatalogSnapshot
import com.example.ui.dialogs.circuiteditor.model.CircuitEditorDraft
import com.example.ui.dialogs.circuiteditor.ui.steps.FeederStepContent
import com.example.ui.theme.VoltAtlasTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Runtime specification tests for FeederStepContent.
 *
 * Evidence Label: NEW_EDITOR_RUNTIME_OBSERVED
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
class FeederStepRuntimeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val testCatalog = CableCatalogSnapshot(
        manufacturers = listOf("Elsewedy", "Ducab"),
        cableTypes = listOf(
            CableType(code = 1, cableType = "Cu/XLPE/SWA/PVC - 4C", conductor = "Cu", insulation = "XLPE", armour = "SWA", cores = "4C", voltage = "0.6/1kV", standard = "IEC 60502", rColumn = "R_Cu90"),
            CableType(code = 2, cableType = "Cu/XLPE/None/PVC - 4C", conductor = "Cu", insulation = "XLPE", armour = "None", cores = "4C", voltage = "0.6/1kV", standard = "IEC 60502", rColumn = "R_Cu90"),
            CableType(code = 3, cableType = "Al/XLPE/STA/PVC - 4C", conductor = "Al", insulation = "XLPE", armour = "STA", cores = "4C", voltage = "0.6/1kV", standard = "IEC 60502", rColumn = "R_Al90"),
            CableType(code = 4, cableType = "Cu/PVC/None/PVC - 1C", conductor = "Cu", insulation = "PVC", armour = "None", cores = "1C", voltage = "0.6/1kV", standard = "IEC 60502", rColumn = "R_Cu70"),
            CableType(code = 5, cableType = "Cu/XLPE/SWA/PVC - 3C", conductor = "Cu", insulation = "XLPE", armour = "SWA", cores = "3C", voltage = "0.6/1kV", standard = "IEC 60502", rColumn = "R_Cu90"),
            CableType(code = 6, cableType = "Cu/XLPE/SWA/PVC - 2C", conductor = "Cu", insulation = "XLPE", armour = "SWA", cores = "2C", voltage = "0.6/1kV", standard = "IEC 60502", rColumn = "R_Cu90"),
            CableType(code = 7, cableType = "Cu/XLPE/SWA/PVC - 3.5C", conductor = "Cu", insulation = "XLPE", armour = "SWA", cores = "3.5C", voltage = "0.6/1kV", standard = "IEC 60502", rColumn = "R_Cu90"),
            CableType(code = 8, cableType = "Ducab Cu/XLPE/AWA/PVC - 1C", conductor = "Cu", insulation = "XLPE", armour = "AWA", cores = "1C", voltage = "0.6/1kV", standard = "BS 5467", rColumn = "R_Cu90")
        ),
        cableCatalog = listOf(
            CableCatalogEntry(mfg = "Elsewedy", typeCode = 1, size = 16.0, model = "4x16", gnd = 90.0, duct = 80.0, air = 75.0, od = 22.0, wt = 1200.0),
            CableCatalogEntry(mfg = "Elsewedy", typeCode = 2, size = 16.0, model = "4x16", gnd = 90.0, duct = 80.0, air = 75.0, od = 22.0, wt = 1200.0),
            CableCatalogEntry(mfg = "Elsewedy", typeCode = 3, size = 16.0, model = "4x16", gnd = 90.0, duct = 80.0, air = 75.0, od = 22.0, wt = 1200.0),
            CableCatalogEntry(mfg = "Elsewedy", typeCode = 4, size = 16.0, model = "1x16", gnd = 90.0, duct = 80.0, air = 75.0, od = 22.0, wt = 1200.0),
            CableCatalogEntry(mfg = "Elsewedy", typeCode = 5, size = 16.0, model = "3x16", gnd = 90.0, duct = 80.0, air = 75.0, od = 22.0, wt = 1200.0),
            CableCatalogEntry(mfg = "Elsewedy", typeCode = 6, size = 16.0, model = "2x16", gnd = 90.0, duct = 80.0, air = 75.0, od = 22.0, wt = 1200.0),
            CableCatalogEntry(mfg = "Elsewedy", typeCode = 7, size = 16.0, model = "3.5x16", gnd = 90.0, duct = 80.0, air = 75.0, od = 22.0, wt = 1200.0),
            CableCatalogEntry(mfg = "Ducab", typeCode = 8, size = 50.0, model = "1x50", gnd = 180.0, duct = 160.0, air = 150.0, od = 28.0, wt = 1600.0)
        ),
        busways = listOf(
            BuswayCatalogEntry(mfg = "Elsewedy", model = "Power Link", conductor = "Cu", ratingA = 1000.0, ipkKA = 105.0, icwKA = 50.0, rMOhmPerM = 0.04, xMOhmPerM = 0.02)
        )
    )

    @Test
    fun test11_cableBranchIsCorrectInitialBranch() {
        val draft = CircuitEditorDraft(feederType = "Cable")
        composeTestRule.setContent {
            VoltAtlasTheme {
                FeederStepContent(draft = draft, onDraftChange = {}, catalogSnapshot = testCatalog)
            }
        }

        composeTestRule.onNodeWithTag("circuit_editor_step_feeder").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_feeder_type").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_manufacturer").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_busway_mfg").assertDoesNotExist()
        composeTestRule.onNodeWithTag("input_busway_model").assertDoesNotExist()
    }

    @Test
    fun test12_cableBranchContainsAllRequiredInputsAndTags() {
        val draft = CircuitEditorDraft(
            feederType = "Cable",
            manufacturer = "Elsewedy",
            cableType = "Cu/XLPE/SWA/PVC - 4C"
        )
        composeTestRule.setContent {
            VoltAtlasTheme {
                FeederStepContent(draft = draft, onDraftChange = {}, catalogSnapshot = testCatalog)
            }
        }

        composeTestRule.onNodeWithTag("input_feeder_type").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_manufacturer").assertIsDisplayed()
        composeTestRule.onNodeWithTag("filter_mat_Copper").assertIsDisplayed()
        composeTestRule.onNodeWithTag("filter_mat_Aluminium").assertIsDisplayed()
        composeTestRule.onNodeWithTag("filter_armour_Unarmoured").assertIsDisplayed()
        composeTestRule.onNodeWithTag("filter_armour_Armoured").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_core_config").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_cable_type").assertIsDisplayed()
        composeTestRule.onNodeWithTag("open_cable_family_picker").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_parallel_runs").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_length").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_max_vd").assertIsDisplayed()
    }

    @Test
    fun test13_buswayBranchContainsOnlyApplicableControlsPlusSharedInputs() {
        val draft = CircuitEditorDraft(feederType = "Busway")
        composeTestRule.setContent {
            VoltAtlasTheme {
                FeederStepContent(draft = draft, onDraftChange = {}, catalogSnapshot = testCatalog)
            }
        }

        composeTestRule.onNodeWithTag("input_feeder_type").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_busway_mfg").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_busway_model").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_parallel_runs").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_length").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_max_vd").assertIsDisplayed()

        // Cable-specific tags must not exist
        composeTestRule.onNodeWithTag("input_manufacturer").assertDoesNotExist()
        composeTestRule.onNodeWithTag("filter_mat_Copper").assertDoesNotExist()
        composeTestRule.onNodeWithTag("filter_armour_Armoured").assertDoesNotExist()
        composeTestRule.onNodeWithTag("input_cable_type").assertDoesNotExist()
        composeTestRule.onNodeWithTag("open_cable_family_picker").assertDoesNotExist()
    }

    @Test
    fun test14_switchingCableBuswayCableRetainsPriorBranchValues() {
        var currentDraft by mutableStateOf(
            CircuitEditorDraft(
                feederType = "Cable",
                manufacturer = "Elsewedy",
                cableType = "Cu/XLPE/SWA/PVC - 4C",
                buswayMfg = "Elsewedy",
                buswayModel = "Power Link"
            )
        )
        composeTestRule.setContent {
            VoltAtlasTheme {
                FeederStepContent(
                    draft = currentDraft,
                    onDraftChange = { currentDraft = it },
                    catalogSnapshot = testCatalog
                )
            }
        }

        // Switch to Busway
        composeTestRule.onNodeWithTag("input_feeder_type").performClick()
        composeTestRule.onAllNodesWithText("Busway").onLast().performClick()
        assertEquals("Busway", currentDraft.feederType)
        assertEquals("Cu/XLPE/SWA/PVC - 4C", currentDraft.cableType)

        // Switch back to Cable
        composeTestRule.onNodeWithTag("input_feeder_type").performClick()
        composeTestRule.onAllNodesWithText("Cable").onLast().performClick()
        assertEquals("Cable", currentDraft.feederType)
        assertEquals("Cu/XLPE/SWA/PVC - 4C", currentDraft.cableType)
        assertEquals("Power Link", currentDraft.buswayModel)
    }

    @Test
    fun test15_manufacturerChangeReconcilesFamily() {
        var currentDraft = CircuitEditorDraft(
            feederType = "Cable",
            manufacturer = "Elsewedy",
            cableType = "Cu/XLPE/SWA/PVC - 4C"
        )
        composeTestRule.setContent {
            VoltAtlasTheme {
                FeederStepContent(
                    draft = currentDraft,
                    onDraftChange = { currentDraft = it },
                    catalogSnapshot = testCatalog
                )
            }
        }

        // Change manufacturer to Ducab
        composeTestRule.onNodeWithTag("input_manufacturer").performClick()
        composeTestRule.onNodeWithText("Ducab").performClick()

        assertEquals("Ducab", currentDraft.manufacturer)
        assertEquals("Ducab Cu/XLPE/AWA/PVC - 1C", currentDraft.cableType)
    }

    @Test
    fun test16_materialChangeReconcilesArmourCoresFamily() {
        var currentDraft = CircuitEditorDraft(
            feederType = "Cable",
            manufacturer = "Elsewedy",
            cableType = "Cu/XLPE/SWA/PVC - 4C",
            filterMat = "Copper",
            filterArmour = "Armoured",
            filterCores = "4C"
        )
        composeTestRule.setContent {
            VoltAtlasTheme {
                FeederStepContent(
                    draft = currentDraft,
                    onDraftChange = { currentDraft = it },
                    catalogSnapshot = testCatalog
                )
            }
        }

        composeTestRule.onNodeWithTag("filter_mat_Aluminium").performClick()
        assertEquals("Aluminium", currentDraft.filterMat)
        assertEquals("Al/XLPE/STA/PVC - 4C", currentDraft.cableType)
    }

    @Test
    fun test17_armourChangeReconcilesCoresFamily() {
        var currentDraft = CircuitEditorDraft(
            feederType = "Cable",
            manufacturer = "Elsewedy",
            cableType = "Cu/XLPE/SWA/PVC - 4C",
            filterMat = "Copper",
            filterArmour = "Armoured",
            filterCores = "4C"
        )
        composeTestRule.setContent {
            VoltAtlasTheme {
                FeederStepContent(
                    draft = currentDraft,
                    onDraftChange = { currentDraft = it },
                    catalogSnapshot = testCatalog
                )
            }
        }

        composeTestRule.onNodeWithTag("filter_armour_Unarmoured").performClick()
        assertEquals("Unarmoured", currentDraft.filterArmour)
        assertEquals("Cu/XLPE/None/PVC - 4C", currentDraft.cableType)
    }

    @Test
    fun test18_coreChangeReconcilesFamily() {
        var currentDraft = CircuitEditorDraft(
            feederType = "Cable",
            manufacturer = "Elsewedy",
            cableType = "Cu/XLPE/SWA/PVC - 4C",
            filterMat = "Copper",
            filterArmour = "Armoured",
            filterCores = "4C"
        )
        composeTestRule.setContent {
            VoltAtlasTheme {
                FeederStepContent(
                    draft = currentDraft,
                    onDraftChange = { currentDraft = it },
                    catalogSnapshot = testCatalog
                )
            }
        }

        composeTestRule.onNodeWithTag("input_core_config").performClick()
        composeTestRule.onNodeWithText("3C").performClick()
        assertEquals("3C", currentDraft.filterCores)
        assertEquals("Cu/XLPE/SWA/PVC - 3C", currentDraft.cableType)
    }

    @Test
    fun test19_searchVisibilityFollowsCandidateCountRule() {
        // With current test data, for Elsewedy Cu Armoured 4C, only 1 candidate exists -> search field not shown
        var currentDraft = CircuitEditorDraft(
            feederType = "Cable",
            manufacturer = "Elsewedy",
            cableType = "Cu/XLPE/SWA/PVC - 4C",
            filterMat = "Copper",
            filterArmour = "Armoured",
            filterCores = "4C"
        )
        composeTestRule.setContent {
            VoltAtlasTheme {
                FeederStepContent(
                    draft = currentDraft,
                    onDraftChange = { currentDraft = it },
                    catalogSnapshot = testCatalog
                )
            }
        }

        composeTestRule.onNodeWithText("Search compatible families...").assertDoesNotExist()
    }

    @Test
    fun test20_noEditableInsulationControlExists() {
        val draft = CircuitEditorDraft(feederType = "Cable")
        composeTestRule.setContent {
            VoltAtlasTheme {
                FeederStepContent(draft = draft, onDraftChange = {}, catalogSnapshot = testCatalog)
            }
        }

        composeTestRule.onNodeWithTag("input_insulation").assertDoesNotExist()
        composeTestRule.onNodeWithTag("filter_insulation_XLPE").assertDoesNotExist()
        composeTestRule.onNodeWithTag("filter_insulation_PVC").assertDoesNotExist()
    }

    @Test
    fun test21_readOnlyInsulationDerivesFromSelectedFamily() {
        val draft = CircuitEditorDraft(
            feederType = "Cable",
            cableType = "Cu/XLPE/SWA/PVC - 4C"
        )
        composeTestRule.setContent {
            VoltAtlasTheme {
                FeederStepContent(draft = draft, onDraftChange = {}, catalogSnapshot = testCatalog)
            }
        }

        composeTestRule.onNodeWithText("Derived Insulation: XLPE (Derived from selected family)").assertIsDisplayed()
    }

    @Test
    fun test22_numericDraftFieldsRemainEditableStrings() {
        var currentDraft by mutableStateOf(
            CircuitEditorDraft(
                feederType = "Cable",
                parallelRuns = "1",
                length_m = "30",
                maxVDpct = "3.0"
            )
        )
        composeTestRule.setContent {
            VoltAtlasTheme {
                FeederStepContent(
                    draft = currentDraft,
                    onDraftChange = { currentDraft = it },
                    catalogSnapshot = testCatalog
                )
            }
        }

        val parallelField = composeTestRule.onNode(hasParent(hasTestTag("input_parallel_runs")) and hasSetTextAction())
        parallelField.performTextClearance()
        parallelField.performTextInput("4")

        val lengthField = composeTestRule.onNode(hasParent(hasTestTag("input_length")) and hasSetTextAction())
        lengthField.performTextClearance()
        lengthField.performTextInput("50")

        val maxVdField = composeTestRule.onNode(hasParent(hasTestTag("input_max_vd")) and hasSetTextAction())
        maxVdField.performTextClearance()
        maxVdField.performTextInput("2.5")

        assertEquals("4", currentDraft.parallelRuns)
        assertEquals("50", currentDraft.length_m)
        assertEquals("2.5", currentDraft.maxVDpct)
    }

    @Test
    fun test23_emptyCableCatalogAndEmptyBuswayCatalogRenderHonestly() {
        val emptyCatalog = CableCatalogSnapshot()
        val draft = CircuitEditorDraft(feederType = "Cable")
        composeTestRule.setContent {
            VoltAtlasTheme {
                FeederStepContent(draft = draft, onDraftChange = {}, catalogSnapshot = emptyCatalog)
            }
        }

        composeTestRule.onNodeWithTag("circuit_editor_step_feeder").assertIsDisplayed()
    }

    @Test
    fun test24_hiddenCableAndBuswayBranchTagsNotSimultaneouslyComposed() {
        val draft = CircuitEditorDraft(feederType = "Cable")
        composeTestRule.setContent {
            VoltAtlasTheme {
                FeederStepContent(draft = draft, onDraftChange = {}, catalogSnapshot = testCatalog)
            }
        }

        // Cable tags exist
        composeTestRule.onNodeWithTag("input_manufacturer").assertIsDisplayed()
        // Busway tags do not exist
        composeTestRule.onNodeWithTag("input_busway_mfg").assertDoesNotExist()
        composeTestRule.onNodeWithTag("input_busway_model").assertDoesNotExist()
    }
}
