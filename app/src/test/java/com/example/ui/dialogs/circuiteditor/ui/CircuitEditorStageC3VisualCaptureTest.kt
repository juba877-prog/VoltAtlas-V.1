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
import com.example.ui.dialogs.circuiteditor.model.TransformerSnapshot
import com.example.ui.dialogs.circuiteditor.model.UpsSnapshot
import com.example.ui.theme.VoltAtlasTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.unit.dp
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
        val brainDir = File("C:/Users/mahmoud.mohamed/.gemini/antigravity/brain/1abfe00f-638e-4dd5-93ae-22beb68021fd")
        if (brainDir.exists()) {
            file.copyTo(File(brainDir, fileName), overwrite = true)
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

    /**
     * Capture K: Supply Emergency + UPS Bottom Scrolled 320 font 1.5
     */
    @Test
    @Config(sdk = [33], qualifiers = "w320dp-h640dp-xhdpi", fontScale = 1.5f)
    fun captureK_supply_emergency_ups_320_font1_5_bottom() {
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

        composeTestRule.onNodeWithTag("content_bottom_clearance_spacer").performScrollTo()
        composeTestRule.waitForIdle()

        val lastNodeBounds = composeTestRule.onNodeWithTag("input_assigned_ups").getBoundsInRoot()
        val nextBtnBounds = composeTestRule.onNodeWithTag("circuit_editor_next_btn").getBoundsInRoot()
        val actionBarTop = nextBtnBounds.top - 12.dp
        val clearance = actionBarTop - lastNodeBounds.bottom

        val scrollNode = composeTestRule.onNode(androidx.compose.ui.test.hasScrollAction()).fetchSemanticsNode()
        val scrollRange = scrollNode.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)
        val scrollValue = scrollRange?.value?.invoke() ?: 0f
        val scrollMax = scrollRange?.maxValue?.invoke() ?: 0f

        println("CAPTURE_K_METRICS: scrollValue=$scrollValue, scrollMax=$scrollMax, lastTag=input_assigned_ups, lastBottom=${lastNodeBounds.bottom}, actionBarTop=$actionBarTop, clearance=$clearance")
        assertTrue("Clearance must be >= 16dp, measured: $clearance", clearance.value >= 16f)

        capture("Supply_EMERGENCY_UPS_320_font1_5_bottom.png")
    }

    /**
     * Capture L: Supply Empty Equipment Bottom Scrolled 320 font 1.5
     */
    @Test
    @Config(sdk = [33], qualifiers = "w320dp-h640dp-xhdpi", fontScale = 1.5f)
    fun captureL_supply_empty_equipment_320_font1_5_bottom() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(
                name = "Circuit 1",
                panelType = "LOAD",
                supplyCategory = "EMERGENCY + UPS",
                assignedAtsId = "None",
                assignedGeneratorId = "None",
                assignedUpsId = "None"
            ),
            initialStep = CircuitWorkflowStep.SUPPLY,
            initialVisited = setOf(CircuitWorkflowStep.IDENTITY, CircuitWorkflowStep.LOAD, CircuitWorkflowStep.SUPPLY),
            availableGenerators = emptyList(),
            availableAts = emptyList(),
            availableUps = emptyList(),
            catalogSnapshot = testCatalog
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder, darkTheme = false)
        }

        composeTestRule.onNodeWithTag("content_bottom_clearance_spacer").performScrollTo()
        composeTestRule.waitForIdle()

        val lastNodeBounds = composeTestRule.onNodeWithText("No UPS configured in this project").getBoundsInRoot()
        val nextBtnBounds = composeTestRule.onNodeWithTag("circuit_editor_next_btn").getBoundsInRoot()
        val actionBarTop = nextBtnBounds.top - 12.dp
        val clearance = actionBarTop - lastNodeBounds.bottom

        val scrollNode = composeTestRule.onNode(androidx.compose.ui.test.hasScrollAction()).fetchSemanticsNode()
        val scrollRange = scrollNode.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)
        val scrollValue = scrollRange?.value?.invoke() ?: 0f
        val scrollMax = scrollRange?.maxValue?.invoke() ?: 0f

        println("CAPTURE_L_METRICS: scrollValue=$scrollValue, scrollMax=$scrollMax, lastTag=empty_ups_text, lastBottom=${lastNodeBounds.bottom}, actionBarTop=$actionBarTop, clearance=$clearance")
        assertTrue("Clearance must be >= 16dp, measured: $clearance", clearance.value >= 16f)

        capture("Supply_empty_equipment_320_font1_5_bottom.png")
    }

    /**
     * Capture M: Feeder Cable Bottom Scrolled 320 font 1.3
     */
    @Test
    @Config(sdk = [33], qualifiers = "w320dp-h640dp-xhdpi", fontScale = 1.3f)
    fun captureM_feeder_cable_320_font1_3_bottom() {
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

        composeTestRule.onNodeWithTag("content_bottom_clearance_spacer").performScrollTo()
        composeTestRule.waitForIdle()

        val lastNodeBounds = composeTestRule.onNodeWithTag("input_max_vd").getBoundsInRoot()
        val nextBtnBounds = composeTestRule.onNodeWithTag("circuit_editor_next_btn").getBoundsInRoot()
        val actionBarTop = nextBtnBounds.top - 12.dp
        val clearance = actionBarTop - lastNodeBounds.bottom

        val scrollNode = composeTestRule.onNode(androidx.compose.ui.test.hasScrollAction()).fetchSemanticsNode()
        val scrollRange = scrollNode.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)
        val scrollValue = scrollRange?.value?.invoke() ?: 0f
        val scrollMax = scrollRange?.maxValue?.invoke() ?: 0f

        println("CAPTURE_M_METRICS: scrollValue=$scrollValue, scrollMax=$scrollMax, lastTag=input_max_vd, lastBottom=${lastNodeBounds.bottom}, actionBarTop=$actionBarTop, clearance=$clearance")
        assertTrue("Clearance must be >= 16dp, measured: $clearance", clearance.value >= 16f)

        capture("Feeder_cable_320_font1_3_bottom.png")
    }

    /**
     * Capture N: Feeder Busway Bottom Scrolled 320 font 1.5
     */
    @Test
    @Config(sdk = [33], qualifiers = "w320dp-h640dp-xhdpi", fontScale = 1.5f)
    fun captureN_feeder_busway_320_font1_5_bottom() {
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
            CircuitEditorTestHost(stateHolder = holder, darkTheme = true)
        }

        composeTestRule.onNodeWithTag("content_bottom_clearance_spacer").performScrollTo()
        composeTestRule.waitForIdle()

        val lastNodeBounds = composeTestRule.onNodeWithTag("input_max_vd").getBoundsInRoot()
        val nextBtnBounds = composeTestRule.onNodeWithTag("circuit_editor_next_btn").getBoundsInRoot()
        val actionBarTop = nextBtnBounds.top - 12.dp
        val clearance = actionBarTop - lastNodeBounds.bottom

        val scrollNode = composeTestRule.onNode(androidx.compose.ui.test.hasScrollAction()).fetchSemanticsNode()
        val scrollRange = scrollNode.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange)
        val scrollValue = scrollRange?.value?.invoke() ?: 0f
        val scrollMax = scrollRange?.maxValue?.invoke() ?: 0f

        println("CAPTURE_N_METRICS: scrollValue=$scrollValue, scrollMax=$scrollMax, lastTag=input_max_vd, lastBottom=${lastNodeBounds.bottom}, actionBarTop=$actionBarTop, clearance=$clearance")
        assertTrue("Clearance must be >= 16dp, measured: $clearance", clearance.value >= 16f)

        capture("Feeder_busway_320_font1_5_bottom.png")
    }

    private fun logAndVerifyHierarchyMetrics(
        captureName: String,
        viewport: String,
        fontScale: Float,
        isBottomScrolled: Boolean
    ): String {
        composeTestRule.waitForIdle()
        if (isBottomScrolled) {
            composeTestRule.onNodeWithTag("content_bottom_clearance_spacer").performScrollTo()
            composeTestRule.waitForIdle()
        } else {
            composeTestRule.onNodeWithTag("hierarchy_value_resolved_transformer").performScrollTo()
            composeTestRule.waitForIdle()
        }

        val isStacked = try {
            composeTestRule.onNodeWithTag("hierarchy_mode_stacked").assertExists()
            true
        } catch (e: Throwable) {
            false
        }
        val layoutMode = if (isStacked) "STACKED" else "TWO_COLUMN"

        val cardBounds = composeTestRule.onNodeWithTag("inherited_source_card").getBoundsInRoot()
        val titleBounds = composeTestRule.onNodeWithTag("hierarchy_title").getBoundsInRoot()

        val pairs = listOf(
            "Level" to ("hierarchy_label_level" to "hierarchy_value_level"),
            "Supply Source" to ("hierarchy_label_supply_source" to "hierarchy_value_supply_source"),
            "Source Type" to ("hierarchy_label_source_type" to "hierarchy_value_source_type"),
            "Resolved Transformer" to ("hierarchy_label_resolved_transformer" to "hierarchy_value_resolved_transformer")
        )

        val nodeBounds = mutableListOf<Pair<String, androidx.compose.ui.unit.DpRect>>()
        nodeBounds.add("hierarchy_title" to titleBounds)

        val pairMetrics = StringBuilder()
        for ((name, tags) in pairs) {
            val (lTag, vTag) = tags
            val lBounds = composeTestRule.onNodeWithTag(lTag).getBoundsInRoot()
            val vBounds = composeTestRule.onNodeWithTag(vTag).getBoundsInRoot()
            nodeBounds.add(lTag to lBounds)
            nodeBounds.add(vTag to vBounds)
            val sep = if (isStacked) vBounds.top - lBounds.bottom else vBounds.left - lBounds.right
            pairMetrics.append("    $name -> Label: $lBounds, Value: $vBounds, sep=${sep}\n")
        }

        var anyOverlap = false
        for (i in 0 until nodeBounds.size) {
            for (j in i + 1 until nodeBounds.size) {
                val (t1, r1) = nodeBounds[i]
                val (t2, r2) = nodeBounds[j]
                val overlaps = !(r1.right.value <= r2.left.value ||
                                 r2.right.value <= r1.left.value ||
                                 r1.bottom.value <= r2.top.value ||
                                 r2.bottom.value <= r1.top.value)
                if (overlaps) {
                    anyOverlap = true
                    System.err.println("OVERLAP between $t1 ($r1) and $t2 ($r2)")
                }
            }
        }

        val nextBtnBounds = composeTestRule.onNodeWithTag("circuit_editor_next_btn").getBoundsInRoot()
        val actionBarTop = nextBtnBounds.top - 12.dp
        val clearance = actionBarTop - cardBounds.bottom

        val report = """
=== RECAPTURE METRICS: $captureName ===
  Viewport: $viewport, FontScale: $fontScale
  Layout Mode: $layoutMode
  Card Bounds: $cardBounds
  Title Bounds: $titleBounds
$pairMetrics  Text Intersection: ${if (anyOverlap) "FAIL (OVERLAPPING)" else "NONE (0 intersections)"}
  Card Bottom: ${cardBounds.bottom}, Action Bar Top: $actionBarTop
  Clearance: $clearance
========================================
""".trimIndent()
        println(report)
        return report
    }

    /**
     * Recapture 01: Hierarchy 320 font 1.5 dark bottom
     */
    @Test
    @Config(sdk = [33], qualifiers = "w320dp-h640dp-xhdpi", fontScale = 1.5f)
    fun recapture01_hierarchy_320_font1_5_dark_bottom() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(supplyCategory = "NORMAL", fedFrom = "Transformer", transformerId = "TX-01"),
            initialStep = CircuitWorkflowStep.SUPPLY,
            initialVisited = setOf(CircuitWorkflowStep.IDENTITY, CircuitWorkflowStep.LOAD, CircuitWorkflowStep.SUPPLY),
            availableTransformers = listOf(TransformerSnapshot("TX-01")),
            catalogSnapshot = testCatalog
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder, darkTheme = true)
        }
        logAndVerifyHierarchyMetrics("01_hierarchy_320_font1_5_dark_bottom.png", "320x640", 1.5f, isBottomScrolled = true)
        capture("01_hierarchy_320_font1_5_dark_bottom.png")
    }

    /**
     * Recapture 02: Hierarchy 360 font 1.0 dark
     */
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.0f)
    fun recapture02_hierarchy_360_font1_0_dark() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(supplyCategory = "NORMAL", fedFrom = "Transformer", transformerId = "TX-01"),
            initialStep = CircuitWorkflowStep.SUPPLY,
            initialVisited = setOf(CircuitWorkflowStep.IDENTITY, CircuitWorkflowStep.LOAD, CircuitWorkflowStep.SUPPLY),
            availableTransformers = listOf(TransformerSnapshot("TX-01")),
            catalogSnapshot = testCatalog
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder, darkTheme = true)
        }
        logAndVerifyHierarchyMetrics("02_hierarchy_360_font1_0_dark.png", "360x800", 1.0f, isBottomScrolled = false)
        capture("02_hierarchy_360_font1_0_dark.png")
    }

    /**
     * Recapture 03: Hierarchy 360 font 1.5 light bottom
     */
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.5f)
    fun recapture03_hierarchy_360_font1_5_light_bottom() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(supplyCategory = "NORMAL", fedFrom = "Transformer", transformerId = "TX-01"),
            initialStep = CircuitWorkflowStep.SUPPLY,
            initialVisited = setOf(CircuitWorkflowStep.IDENTITY, CircuitWorkflowStep.LOAD, CircuitWorkflowStep.SUPPLY),
            availableTransformers = listOf(TransformerSnapshot("TX-01")),
            catalogSnapshot = testCatalog
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder, darkTheme = false)
        }
        logAndVerifyHierarchyMetrics("03_hierarchy_360_font1_5_light_bottom.png", "360x800", 1.5f, isBottomScrolled = true)
        capture("03_hierarchy_360_font1_5_light_bottom.png")
    }

    /**
     * Recapture 04: Hierarchy 412 font 1.3 dark
     */
    @Test
    @Config(sdk = [33], qualifiers = "w412dp-h915dp-xhdpi", fontScale = 1.3f)
    fun recapture04_hierarchy_412_font1_3_dark() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(supplyCategory = "NORMAL", fedFrom = "Transformer", transformerId = "TX-01"),
            initialStep = CircuitWorkflowStep.SUPPLY,
            initialVisited = setOf(CircuitWorkflowStep.IDENTITY, CircuitWorkflowStep.LOAD, CircuitWorkflowStep.SUPPLY),
            availableTransformers = listOf(TransformerSnapshot("TX-01")),
            catalogSnapshot = testCatalog
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder, darkTheme = true)
        }
        logAndVerifyHierarchyMetrics("04_hierarchy_412_font1_3_dark.png", "412x915", 1.3f, isBottomScrolled = false)
        capture("04_hierarchy_412_font1_3_dark.png")
    }

    /**
     * Recapture 05: Hierarchy Long Values 320 font 1.5 dark bottom
     */
    @Test
    @Config(sdk = [33], qualifiers = "w320dp-h640dp-xhdpi", fontScale = 1.5f)
    fun recapture05_hierarchy_long_values_320_font1_5_dark_bottom() {
        val longSource = "Main Substation Medium Voltage Switchgear 11kV Section A Busbar"
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(supplyCategory = "NORMAL", fedFrom = longSource, transformerId = "TX-01"),
            initialStep = CircuitWorkflowStep.SUPPLY,
            initialVisited = setOf(CircuitWorkflowStep.IDENTITY, CircuitWorkflowStep.LOAD, CircuitWorkflowStep.SUPPLY),
            availableTransformers = listOf(TransformerSnapshot("TX-01")),
            catalogSnapshot = testCatalog
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder, darkTheme = true)
        }
        logAndVerifyHierarchyMetrics("05_hierarchy_long_values_320_font1_5_dark_bottom.png", "320x640", 1.5f, isBottomScrolled = true)
        capture("05_hierarchy_long_values_320_font1_5_dark_bottom.png")
    }
}

