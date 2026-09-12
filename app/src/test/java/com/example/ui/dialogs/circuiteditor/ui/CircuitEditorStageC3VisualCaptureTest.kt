package com.example.ui.dialogs.circuiteditor.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
 * Visual capture test suite for CircuitEditor Stage C3.
 *
 * Generates and validates the 14 owner-required captures covering Supply & Backup,
 * Feeder Selection (Cable & Busway), and Cable Family Picker.
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

    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.0f)
    fun capture01_supply_normal_dark_360_font1_0() {
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
        composeTestRule.onNodeWithTag("input_assigned_ats").assertDoesNotExist()
        capture("01_supply_normal_dark_360_font1_0.png")
    }

    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.0f)
    fun capture02_supply_emergency_dark_360_font1_0() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(
                name = "Circuit 1",
                panelType = "LOAD",
                supplyCategory = "EMERGENCY",
                assignedAtsId = "ATS-01",
                assignedGeneratorId = "GEN-01"
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

        composeTestRule.onNodeWithTag("input_assigned_ats").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_assigned_generator").assertIsDisplayed()
        capture("02_supply_emergency_dark_360_font1_0.png")
    }

    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.0f)
    fun capture03_supply_ups_light_360_font1_0() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(
                name = "Circuit 1",
                panelType = "LOAD",
                supplyCategory = "UPS",
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
            CircuitEditorTestHost(stateHolder = holder, darkTheme = false)
        }

        composeTestRule.onNodeWithTag("input_assigned_ups").assertIsDisplayed()
        capture("03_supply_ups_light_360_font1_0.png")
    }

    @Test
    @Config(sdk = [33], qualifiers = "w320dp-h640dp-xhdpi", fontScale = 1.3f)
    fun capture04_supply_emergency_ups_dark_320_font1_3() {
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

        composeTestRule.onNodeWithTag("input_assigned_ats").assertExists()
        composeTestRule.onNodeWithTag("input_assigned_generator").assertExists()
        composeTestRule.onNodeWithTag("input_assigned_ups").assertExists()
        capture("04_supply_emergency_ups_dark_320_font1_3.png")
    }

    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.5f)
    fun capture05_supply_empty_equipment_dark_360_font1_5() {
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

        capture("05_supply_empty_equipment_dark_360_font1_5.png")
    }

    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.0f)
    fun capture06_feeder_cable_default_dark_360_font1_0() {
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

        composeTestRule.onNodeWithTag("input_manufacturer").assertIsDisplayed()
        capture("06_feeder_cable_default_dark_360_font1_0.png")
    }

    @Test
    @Config(sdk = [33], qualifiers = "w320dp-h640dp-xhdpi", fontScale = 1.3f)
    fun capture07_feeder_cable_filters_dark_320_font1_3() {
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

        capture("07_feeder_cable_filters_dark_320_font1_3.png")
    }

    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.0f)
    fun capture08_feeder_busway_dark_360_font1_0() {
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

        composeTestRule.onNodeWithTag("input_busway_mfg").assertIsDisplayed()
        capture("08_feeder_busway_dark_360_font1_0.png")
    }

    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.5f)
    fun capture09_feeder_busway_light_360_font1_5() {
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

        capture("09_feeder_busway_light_360_font1_5.png")
    }

    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.0f)
    fun capture10_cable_family_picker_dark_360_font1_0() {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                Box(modifier = Modifier.fillMaxSize()) {
                    CableFamilyPicker(
                        isOpen = true,
                        currentFamily = "Cu/XLPE/SWA/PVC - 4C",
                        candidateFamilies = testCatalog.cableTypes.map { it.cableType },
                        onConfirm = {},
                        onCancel = {}
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("cable_family_picker").assertIsDisplayed()
        capture("10_cable_family_picker_dark_360_font1_0.png")
    }

    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.3f)
    fun capture11_cable_family_picker_search_dark_360_font1_3() {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                Box(modifier = Modifier.fillMaxSize()) {
                    CableFamilyPicker(
                        isOpen = true,
                        currentFamily = "Cu/XLPE/SWA/PVC - 4C",
                        candidateFamilies = testCatalog.cableTypes.map { it.cableType },
                        onConfirm = {},
                        onCancel = {}
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("cable_family_search").performTextInput("STA")
        capture("11_cable_family_picker_search_dark_360_font1_3.png")
    }

    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.0f)
    fun capture12_cable_family_picker_empty_light_360_font1_0() {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = false) {
                Box(modifier = Modifier.fillMaxSize()) {
                    CableFamilyPicker(
                        isOpen = true,
                        currentFamily = "Cu/XLPE/SWA/PVC - 4C",
                        candidateFamilies = testCatalog.cableTypes.map { it.cableType },
                        onConfirm = {},
                        onCancel = {}
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("cable_family_search").performTextInput("NonExistentPattern999")
        composeTestRule.onNodeWithTag("cable_family_empty_state").assertIsDisplayed()
        capture("12_cable_family_picker_empty_light_360_font1_0.png")
    }

    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.5f)
    fun capture13_feeder_bottom_scrolled_360_font1_5() {
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

        capture("13_feeder_bottom_scrolled_360_font1_5.png")
    }

    @Test
    @Config(sdk = [33], qualifiers = "w412dp-h915dp-xhdpi", fontScale = 1.3f)
    fun capture14_supply_larger_phone_dark_412_font1_3() {
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

        composeTestRule.onNodeWithTag("input_supply_category").assertIsDisplayed()
        capture("14_supply_larger_phone_dark_412_font1_3.png")
    }
}
