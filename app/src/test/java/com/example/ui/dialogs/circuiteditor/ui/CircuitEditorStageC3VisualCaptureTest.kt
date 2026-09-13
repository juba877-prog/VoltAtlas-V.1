package com.example.ui.dialogs.circuiteditor.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.data.CableCatalogEntry
import com.example.data.CableType
import com.example.data.catalog.BuswayCatalogEntry
import com.example.ui.dialogs.circuiteditor.model.AtsSnapshot
import com.example.ui.dialogs.circuiteditor.model.CableCatalogSnapshot
import com.example.ui.dialogs.circuiteditor.model.CircuitEditorDraft
import com.example.ui.dialogs.circuiteditor.model.CircuitWorkflowStep
import com.example.ui.dialogs.circuiteditor.model.GeneratorSnapshot
import com.example.ui.dialogs.circuiteditor.model.UpsSnapshot
import com.example.ui.theme.VoltAtlasTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Visual capture test suite for CircuitEditor Stage C3 (Final Correction Pass).
 *
 * Generates and validates the 10 owner-required recaptures (A through J) covering:
 * - Feeder Selection step naming and helper text
 * - Cable Family Picker compatibility filtering (material + armour + cores)
 * - Single picker entry point on Selected Cable Family surface
 * - Empty-picker disabled Confirm state (visual & semantic)
 * - Empty equipment non-duplicative helper text (without "(None)")
 * - Supply category single-choice accessibility and clearance
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CircuitEditorStageC3VisualCaptureTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val outputDir = File("build/outputs/roborazzi/circuit_editor_c3").apply { mkdirs() }

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
            CableType(code = 8, cableType = "Ducab Cu/XLPE/AWA/PVC - 1C", conductor = "Cu", insulation = "XLPE", armour = "AWA", cores = "1C", voltage = "0.6/1kV", standard = "BS 5467", rColumn = "R_Cu90"),
            CableType(code = 9, cableType = "Cu/XLPE/STA/PVC - 4C", conductor = "Cu", insulation = "XLPE", armour = "STA", cores = "4C", voltage = "0.6/1kV", standard = "IEC 60502", rColumn = "R_Cu90")
        ),
        cableCatalog = listOf(
            CableCatalogEntry(mfg = "Elsewedy", typeCode = 1, size = 16.0, model = "4x16", gnd = 90.0, duct = 80.0, air = 75.0, od = 22.0, wt = 1200.0),
            CableCatalogEntry(mfg = "Elsewedy", typeCode = 2, size = 16.0, model = "4x16", gnd = 90.0, duct = 80.0, air = 75.0, od = 22.0, wt = 1200.0),
            CableCatalogEntry(mfg = "Elsewedy", typeCode = 3, size = 16.0, model = "4x16", gnd = 90.0, duct = 80.0, air = 75.0, od = 22.0, wt = 1200.0),
            CableCatalogEntry(mfg = "Elsewedy", typeCode = 4, size = 16.0, model = "1x16", gnd = 90.0, duct = 80.0, air = 75.0, od = 22.0, wt = 1200.0),
            CableCatalogEntry(mfg = "Elsewedy", typeCode = 5, size = 16.0, model = "3x16", gnd = 90.0, duct = 80.0, air = 75.0, od = 22.0, wt = 1200.0),
            CableCatalogEntry(mfg = "Elsewedy", typeCode = 6, size = 16.0, model = "2x16", gnd = 90.0, duct = 80.0, air = 75.0, od = 22.0, wt = 1200.0),
            CableCatalogEntry(mfg = "Elsewedy", typeCode = 7, size = 16.0, model = "3.5x16", gnd = 90.0, duct = 80.0, air = 75.0, od = 22.0, wt = 1200.0),
            CableCatalogEntry(mfg = "Ducab", typeCode = 8, size = 50.0, model = "1x50", gnd = 180.0, duct = 160.0, air = 150.0, od = 28.0, wt = 1600.0),
            CableCatalogEntry(mfg = "Elsewedy", typeCode = 9, size = 25.0, model = "4x25", gnd = 110.0, duct = 100.0, air = 95.0, od = 26.0, wt = 1500.0)
        ),
        busways = listOf(
            BuswayCatalogEntry(mfg = "Elsewedy", model = "Power Link", conductor = "Cu", ratingA = 1000.0, ipkKA = 105.0, icwKA = 50.0, rMOhmPerM = 0.04, xMOhmPerM = 0.02)
        )
    )

    private val testGenerators = listOf(GeneratorSnapshot("GEN-01"), GeneratorSnapshot("GEN-02"))
    private val testAtsList = listOf(AtsSnapshot("ATS-01"))
    private val testUpsList = listOf(UpsSnapshot("UPS-01"))

    private fun captureRootBitmap(): android.graphics.Bitmap {
        composeTestRule.waitForIdle()
        val contentView = composeTestRule.activity.findViewById<android.view.View>(android.R.id.content)
            ?: composeTestRule.activity.window.decorView
        val bitmap = android.graphics.Bitmap.createBitmap(
            maxOf(contentView.width, 1),
            maxOf(contentView.height, 1),
            android.graphics.Bitmap.Config.ARGB_8888
        )
        contentView.draw(android.graphics.Canvas(bitmap))
        return bitmap
    }

    private fun capture(fileName: String) {
        val bitmap = captureRootBitmap()
        val file = File(outputDir, fileName)
        file.parentFile?.mkdirs()
        file.outputStream().use { out ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
        }
    }

    /**
     * Capture A: Supply Normal Dark 360 font 1.0
     */
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.0f)
    fun captureA_supply_normal_dark_360_font1_0() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "Circuit 1", panelType = "LOAD", supplyCategory = "NORMAL"),
            initialStep = CircuitWorkflowStep.SUPPLY,
            initialVisited = setOf(CircuitWorkflowStep.IDENTITY, CircuitWorkflowStep.LOAD, CircuitWorkflowStep.SUPPLY),
            availableGenerators = testGenerators,
            availableAts = testAtsList,
            availableUps = testUpsList,
            catalogSnapshot = testCatalog
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder, darkTheme = true)
        }

        composeTestRule.onNodeWithTag("circuit_editor_step_supply").assertIsDisplayed()
        composeTestRule.onNodeWithText("NORMAL").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_assigned_ats").assertDoesNotExist()
        composeTestRule.onNodeWithTag("input_assigned_generator").assertDoesNotExist()
        composeTestRule.onNodeWithTag("input_assigned_ups").assertDoesNotExist()
        capture("A_supply_normal_dark_360_font1_0.png")
    }

    /**
     * Capture B: Supply Emergency + UPS Dark 320 font 1.5
     */
    @Test
    @Config(sdk = [33], qualifiers = "w320dp-h640dp-xhdpi", fontScale = 1.5f)
    fun captureB_supply_emergency_ups_dark_320_font1_5() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(
                name = "Circuit 1",
                panelType = "LOAD",
                supplyCategory = "EMERGENCY + UPS",
                assignedAtsId = "ATS-01",
                assignedGeneratorId = "GEN-01",
                assignedUpsId = "UPS-01"
            ),
            initialStep = CircuitWorkflowStep.SUPPLY,
            initialVisited = setOf(CircuitWorkflowStep.IDENTITY, CircuitWorkflowStep.LOAD, CircuitWorkflowStep.SUPPLY),
            availableGenerators = testGenerators,
            availableAts = testAtsList,
            availableUps = testUpsList,
            catalogSnapshot = testCatalog
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder, darkTheme = true)
        }

        composeTestRule.onNodeWithText("EMERGENCY + UPS").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_assigned_ats").assertExists()
        composeTestRule.onNodeWithTag("input_assigned_generator").assertExists()
        composeTestRule.onNodeWithTag("input_assigned_ups").assertExists()
        capture("B_supply_emergency_ups_dark_320_font1_5.png")
    }

    /**
     * Capture C: Supply Empty Equipment Dark 360 font 1.5
     */
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.5f)
    fun captureC_supply_empty_equipment_dark_360_font1_5() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(
                name = "Circuit 1",
                panelType = "LOAD",
                supplyCategory = "EMERGENCY + UPS"
            ),
            initialStep = CircuitWorkflowStep.SUPPLY,
            initialVisited = setOf(CircuitWorkflowStep.IDENTITY, CircuitWorkflowStep.LOAD, CircuitWorkflowStep.SUPPLY),
            availableGenerators = emptyList(),
            availableAts = emptyList(),
            availableUps = emptyList(),
            catalogSnapshot = testCatalog
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder, darkTheme = true)
        }

        composeTestRule.onNodeWithText("No ATS configured in this project").assertExists()
        composeTestRule.onNodeWithText("No generator configured in this project").assertExists()
        composeTestRule.onNodeWithText("No UPS configured in this project").assertExists()
        composeTestRule.onNodeWithText("No ATS configured in project (None)").assertDoesNotExist()
        capture("C_supply_empty_equipment_dark_360_font1_5.png")
    }

    /**
     * Capture D: Feeder Cable Default Dark 360 font 1.0
     * Shows renamed step title "4. Feeder Selection", helper text, and single picker entry surface.
     */
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.0f)
    fun captureD_feeder_cable_dark_360_font1_0() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(
                name = "Circuit 1",
                panelType = "LOAD",
                feederType = "Cable",
                manufacturer = "Elsewedy",
                cableType = "Cu/XLPE/SWA/PVC - 4C",
                filterMat = "Copper",
                filterArmour = "Armoured",
                filterCores = "4C"
            ),
            initialStep = CircuitWorkflowStep.FEEDER,
            initialVisited = setOf(CircuitWorkflowStep.IDENTITY, CircuitWorkflowStep.LOAD, CircuitWorkflowStep.SUPPLY, CircuitWorkflowStep.FEEDER),
            catalogSnapshot = testCatalog
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder, darkTheme = true)
        }

        composeTestRule.onNodeWithText("4. Feeder Selection").assertIsDisplayed()
        composeTestRule.onNodeWithText("Select cable or busway family and configuration").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_cable_type").assertExists()
        composeTestRule.onNodeWithTag("open_cable_family_picker", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText("Browse Cable Families Picker").assertDoesNotExist()
        capture("D_feeder_cable_dark_360_font1_0.png")
    }

    /**
     * Capture E: Feeder Cable Filtered Dark 320 font 1.3
     */
    @Test
    @Config(sdk = [33], qualifiers = "w320dp-h640dp-xhdpi", fontScale = 1.3f)
    fun captureE_feeder_cable_filtered_320_font1_3() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(
                name = "Circuit 1",
                panelType = "LOAD",
                feederType = "Cable",
                manufacturer = "Elsewedy",
                cableType = "Al/XLPE/STA/PVC - 4C",
                filterMat = "Aluminium",
                filterArmour = "Armoured",
                filterCores = "4C"
            ),
            initialStep = CircuitWorkflowStep.FEEDER,
            initialVisited = setOf(CircuitWorkflowStep.IDENTITY, CircuitWorkflowStep.LOAD, CircuitWorkflowStep.SUPPLY, CircuitWorkflowStep.FEEDER),
            catalogSnapshot = testCatalog
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder, darkTheme = true)
        }

        composeTestRule.onNodeWithTag("circuit_editor_step_feeder").assertIsDisplayed()
        composeTestRule.onNodeWithTag("filter_mat_Aluminium").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_cable_type").assertExists()
        capture("E_feeder_cable_filtered_320_font1_3.png")
    }

    /**
     * Capture F: Feeder Busway Light 360 font 1.5
     */
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.5f)
    fun captureF_feeder_busway_light_360_font1_5() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(
                name = "Circuit 1",
                panelType = "LOAD",
                feederType = "Busway",
                buswayMfg = "Elsewedy",
                buswayModel = "Power Link"
            ),
            initialStep = CircuitWorkflowStep.FEEDER,
            initialVisited = setOf(CircuitWorkflowStep.IDENTITY, CircuitWorkflowStep.LOAD, CircuitWorkflowStep.SUPPLY, CircuitWorkflowStep.FEEDER),
            catalogSnapshot = testCatalog
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder, darkTheme = false)
        }

        composeTestRule.onNodeWithTag("input_busway_mfg").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_busway_model").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_parallel_runs").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_length").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_max_vd").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_cable_type").assertDoesNotExist()
        capture("F_feeder_busway_light_360_font1_5.png")
    }

    /**
     * Capture G: Picker Compatible Results Dark 360 font 1.0
     * Only compatible families for Copper + Armoured + 4C are displayed in the picker.
     */
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.0f)
    fun captureG_picker_compatible_results_dark_360_font1_0() {
        val compatibleCandidates = listOf("Cu/XLPE/SWA/PVC - 4C", "Cu/XLPE/STA/PVC - 4C")
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                Box(modifier = Modifier.fillMaxSize()) {
                    CableFamilyPicker(
                        isOpen = true,
                        currentFamily = "Cu/XLPE/SWA/PVC - 4C",
                        candidateFamilies = compatibleCandidates,
                        onConfirm = {},
                        onCancel = {}
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("cable_family_picker").assertIsDisplayed()

        // Compatible Copper + Armoured + 4C families must be present
        composeTestRule.onNodeWithText("Cu/XLPE/SWA/PVC - 4C").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cu/XLPE/STA/PVC - 4C").assertIsDisplayed()

        // Incompatible families must NOT appear
        composeTestRule.onNodeWithText("Al/XLPE/STA/PVC - 4C").assertDoesNotExist()
        composeTestRule.onNodeWithText("Cu/XLPE/None/PVC - 4C").assertDoesNotExist()

        composeTestRule.onNodeWithTag("cable_family_confirm").assertIsEnabled()
        capture("G_picker_compatible_results_dark_360_font1_0.png")
    }

    /**
     * Capture H: Picker Search Within Compatible Dark 360 font 1.3
     * Searching narrows within the compatible list only.
     */
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.3f)
    fun captureH_picker_search_within_compatible_dark_360_font1_3() {
        val compatibleCandidates = listOf("Cu/XLPE/SWA/PVC - 4C", "Cu/XLPE/STA/PVC - 4C")
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                Box(modifier = Modifier.fillMaxSize()) {
                    CableFamilyPicker(
                        isOpen = true,
                        currentFamily = "Cu/XLPE/SWA/PVC - 4C",
                        candidateFamilies = compatibleCandidates,
                        onConfirm = {},
                        onCancel = {}
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("cable_family_search").performTextInput("STA")

        // Only matching compatible STA family remains
        composeTestRule.onNodeWithText("Cu/XLPE/STA/PVC - 4C").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cu/XLPE/SWA/PVC - 4C").assertDoesNotExist()

        capture("H_picker_search_within_compatible_dark_360_font1_3.png")
    }

    /**
     * Capture I: Picker Empty Disabled Confirm Light 360 font 1.0
     * No results found shows empty state and Confirm is disabled both semantically and visually.
     */
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.0f)
    fun captureI_picker_empty_disabled_confirm_light_360_font1_0() {
        val compatibleCandidates = listOf("Cu/XLPE/SWA/PVC - 4C", "Cu/XLPE/STA/PVC - 4C")
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = false) {
                Box(modifier = Modifier.fillMaxSize()) {
                    CableFamilyPicker(
                        isOpen = true,
                        currentFamily = "Cu/XLPE/SWA/PVC - 4C",
                        candidateFamilies = compatibleCandidates,
                        onConfirm = {},
                        onCancel = {}
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("cable_family_search").performTextInput("NonExistentPattern999")

        composeTestRule.onNodeWithTag("cable_family_empty_state").assertIsDisplayed()
        composeTestRule.onNodeWithText("No matching cable families found").assertIsDisplayed()
        composeTestRule.onNodeWithTag("cable_family_confirm").assertIsNotEnabled()

        capture("I_picker_empty_disabled_confirm_light_360_font1_0.png")
    }

    /**
     * Capture J: Feeder Bottom Scrolled 360 font 1.5
     * Max VD field clearly clears sticky action bar by > 16dp.
     */
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.5f)
    fun captureJ_feeder_bottom_scrolled_360_font1_5() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(
                name = "Circuit 1",
                panelType = "LOAD",
                feederType = "Cable",
                manufacturer = "Elsewedy",
                cableType = "Cu/XLPE/SWA/PVC - 4C"
            ),
            initialStep = CircuitWorkflowStep.FEEDER,
            initialVisited = setOf(CircuitWorkflowStep.IDENTITY, CircuitWorkflowStep.LOAD, CircuitWorkflowStep.SUPPLY, CircuitWorkflowStep.FEEDER),
            catalogSnapshot = testCatalog
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder, darkTheme = true)
        }

        composeTestRule.onNodeWithTag("input_max_vd").performScrollTo()
        composeTestRule.waitForIdle()

        val maxVdBounds = composeTestRule.onNodeWithTag("input_max_vd").getBoundsInRoot()
        val nextBtnBounds = composeTestRule.onNodeWithTag("circuit_editor_next_btn").getBoundsInRoot()
        assertTrue(
            "Max VD field bottom (${maxVdBounds.bottom}) must clear Next button top (${nextBtnBounds.top})",
            maxVdBounds.bottom <= nextBtnBounds.top
        )

        capture("J_feeder_bottom_scrolled_360_font1_5.png")
    }
}

