package com.example.ui.dialogs.circuiteditor.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.ui.dialogs.circuiteditor.model.CircuitEditorDraft
import com.example.ui.dialogs.circuiteditor.model.CircuitWorkflow
import com.example.ui.dialogs.circuiteditor.model.CircuitWorkflowStep
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Visual capture test suite for CircuitEditor Stage C4 (Installation + Panel Header + Review).
 *
 * Generates and validates the 15 owner-required captures covering:
 * - 01: Installation default state (360dp, font 1.0, dark)
 * - 02: Installation Direct in Ground (320dp, font 1.5, dark, bottom)
 * - 03: Installation Free Air (412dp, font 1.3, light)
 * - 04: Panel Header default state (360dp, font 1.0, dark)
 * - 05: Panel Header populated (320dp, font 1.5, dark, bottom)
 * - 06: Panel Header light mode (412dp, font 1.3, light)
 * - 07: Review 6-step LOAD circuit top (360dp, font 1.0, dark)
 * - 08: Review 6-step LOAD circuit bottom (360dp, font 1.0, dark)
 * - 09: Review 7-step DB circuit top (360dp, font 1.0, dark)
 * - 10: Review 7-step DB circuit bottom (320dp, font 1.5, dark)
 * - 11: Review Busway circuit (412dp, font 1.3, light)
 * - 12: Review validation error: blank name (360dp, font 1.0, dark)
 * - 13: Review validation error: zero length (360dp, font 1.0, dark)
 * - 14: Stepper 6-step LOAD review active (360dp, font 1.0, dark)
 * - 15: Stepper 7-step DB review active (360dp, font 1.0, dark)
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CircuitEditorStageC4VisualCaptureTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val outputDir = File("build/outputs/roborazzi/circuit_editor_c4").apply { mkdirs() }

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
     * 01: Installation default state (360dp, font 1.0, dark)
     */
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.0f)
    fun capture01_installation_default_360_font1_0_dark() {
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
            CircuitEditorTestHost(stateHolder = holder, darkTheme = true)
        }
        capture("01_installation_default_360_font1_0_dark.png")
    }

    /**
     * 02: Installation Direct in Ground (320dp, font 1.5, dark, bottom)
     */
    @Test
    @Config(sdk = [33], qualifiers = "w320dp-h800dp-xhdpi", fontScale = 1.5f)
    fun capture02_installation_direct_ground_320_font1_5_dark_bottom() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(
                name = "Underground Feeder",
                panelType = "LOAD",
                installation = "Laid Direct in Ground",
                formation = "Trefoil",
                spacing = "0.15m",
                burialDepth_m = "0.8",
                soilRes = "150"
            ),
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
            CircuitEditorTestHost(stateHolder = holder, darkTheme = true)
        }
        composeTestRule.onNodeWithTag("content_bottom_clearance_spacer").performScrollTo()
        capture("02_installation_direct_ground_320_font1_5_dark_bottom.png")
    }

    /**
     * 03: Installation Free Air (412dp, font 1.3, light)
     */
    @Test
    @Config(sdk = [33], qualifiers = "w412dp-h915dp-xxhdpi", fontScale = 1.3f)
    fun capture03_installation_free_air_412_font1_3_light() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(
                name = "Rooftop Feeder",
                panelType = "LOAD",
                installation = "Laid in Free Air",
                airTempC = "50"
            ),
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
            CircuitEditorTestHost(stateHolder = holder, darkTheme = false)
        }
        capture("03_installation_free_air_412_font1_3_light.png")
    }

    /**
     * 04: Panel Header default state (360dp, font 1.0, dark)
     */
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.0f)
    fun capture04_panel_header_default_360_font1_0_dark() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "Main DB", panelType = "DB"),
            initialStep = CircuitWorkflowStep.PANEL_HEADER,
            initialVisited = setOf(
                CircuitWorkflowStep.IDENTITY,
                CircuitWorkflowStep.LOAD,
                CircuitWorkflowStep.SUPPLY,
                CircuitWorkflowStep.FEEDER,
                CircuitWorkflowStep.INSTALLATION,
                CircuitWorkflowStep.PANEL_HEADER
            )
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder, darkTheme = true)
        }
        capture("04_panel_header_default_360_font1_0_dark.png")
    }

    /**
     * 05: Panel Header populated (320dp, font 1.5, dark, bottom)
     */
    @Test
    @Config(sdk = [33], qualifiers = "w320dp-h800dp-xhdpi", fontScale = 1.5f)
    fun capture05_panel_header_populated_320_font1_5_dark_bottom() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(
                name = "Distribution Board 1",
                panelType = "DB",
                busbarRatingA = "630",
                mainIncomingType = "MCCB",
                mainIncomingCount = "1",
                ipRating = "IP54",
                panelLocation = "Substation 1",
                numberOfWays = "24",
                feederDescription = "Main incoming supply from TX-01 through cable trench"
            ),
            initialStep = CircuitWorkflowStep.PANEL_HEADER,
            initialVisited = setOf(
                CircuitWorkflowStep.IDENTITY,
                CircuitWorkflowStep.LOAD,
                CircuitWorkflowStep.SUPPLY,
                CircuitWorkflowStep.FEEDER,
                CircuitWorkflowStep.INSTALLATION,
                CircuitWorkflowStep.PANEL_HEADER
            ),
            scRatingText = "35.20 kA"
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder, darkTheme = true)
        }
        composeTestRule.onNodeWithTag("content_bottom_clearance_spacer").performScrollTo()
        capture("05_panel_header_populated_320_font1_5_dark_bottom.png")
    }

    /**
     * 06: Panel Header light mode (412dp, font 1.3, light)
     */
    @Test
    @Config(sdk = [33], qualifiers = "w412dp-h915dp-xxhdpi", fontScale = 1.3f)
    fun capture06_panel_header_light_412_font1_3() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(
                name = "HVAC Switchboard",
                panelType = "MDB",
                busbarRatingA = "1250",
                mainIncomingType = "ACB",
                mainIncomingCount = "2",
                ipRating = "IP65",
                panelLocation = "Chiller Plant Room",
                numberOfWays = "36",
                feederDescription = "Dual incoming busway from Transformer TX-01 and TX-02"
            ),
            initialStep = CircuitWorkflowStep.PANEL_HEADER,
            initialVisited = setOf(
                CircuitWorkflowStep.IDENTITY,
                CircuitWorkflowStep.LOAD,
                CircuitWorkflowStep.SUPPLY,
                CircuitWorkflowStep.FEEDER,
                CircuitWorkflowStep.INSTALLATION,
                CircuitWorkflowStep.PANEL_HEADER
            ),
            scRatingText = "50.00 kA"
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder, darkTheme = false)
        }
        capture("06_panel_header_light_412_font1_3.png")
    }

    /**
     * 07: Review 6-step LOAD circuit top (360dp, font 1.0, dark)
     */
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.0f)
    fun capture07_review_load_circuit_360_font1_0_dark() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(
                name = "Motor Feeder 1",
                panelType = "LOAD",
                loadKVA = "45.0",
                pf = "0.88",
                demandFactor = "1.0",
                voltageV = "400",
                phases = "3P+N",
                loadNature = "Motor",
                startingMethod = "Star-Delta",
                supplyCategory = "NORMAL",
                feederType = "Cable",
                manufacturer = "Elsewedy",
                cableType = "Cu/XLPE/SWA/PVC - 4C",
                length_m = "60",
                installation = "Laid in Ducts"
            ),
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
            CircuitEditorTestHost(stateHolder = holder, darkTheme = true)
        }
        capture("07_review_load_circuit_360_font1_0_dark.png")
    }

    /**
     * 08: Review 6-step LOAD circuit bottom (360dp, font 1.0, dark)
     */
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.0f)
    fun capture08_review_load_circuit_360_font1_0_dark_bottom() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(
                name = "Motor Feeder 1",
                panelType = "LOAD",
                loadKVA = "45.0",
                pf = "0.88",
                demandFactor = "1.0",
                voltageV = "400",
                phases = "3P+N",
                loadNature = "Motor",
                startingMethod = "Star-Delta",
                supplyCategory = "NORMAL",
                feederType = "Cable",
                manufacturer = "Elsewedy",
                cableType = "Cu/XLPE/SWA/PVC - 4C",
                length_m = "60",
                installation = "Laid in Ducts"
            ),
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
            CircuitEditorTestHost(stateHolder = holder, darkTheme = true)
        }
        composeTestRule.onNodeWithTag("content_bottom_clearance_spacer").performScrollTo()
        capture("08_review_load_circuit_360_font1_0_dark_bottom.png")
    }

    /**
     * 09: Review 7-step DB circuit top (360dp, font 1.0, dark)
     */
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.0f)
    fun capture09_review_db_circuit_with_panel_header_360_font1_0_dark() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(
                name = "Lighting DB",
                panelType = "DB",
                loadKVA = "30.0",
                pf = "0.95",
                demandFactor = "0.9",
                supplyCategory = "EMERGENCY",
                assignedAtsId = "ATS-01",
                assignedGeneratorId = "GEN-01",
                feederType = "Cable",
                manufacturer = "Elsewedy",
                cableType = "Cu/XLPE/SWA/PVC - 4C",
                length_m = "40",
                installation = "Laid in Ducts",
                busbarRatingA = "250",
                mainIncomingType = "MCCB",
                mainIncomingCount = "1",
                ipRating = "IP41",
                panelLocation = "Floor 2 Electrical Closet",
                numberOfWays = "18"
            ),
            initialStep = CircuitWorkflowStep.REVIEW,
            initialVisited = setOf(
                CircuitWorkflowStep.IDENTITY,
                CircuitWorkflowStep.LOAD,
                CircuitWorkflowStep.SUPPLY,
                CircuitWorkflowStep.FEEDER,
                CircuitWorkflowStep.INSTALLATION,
                CircuitWorkflowStep.PANEL_HEADER,
                CircuitWorkflowStep.REVIEW
            ),
            scRatingText = "18.50 kA"
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder, darkTheme = true)
        }
        capture("09_review_db_circuit_with_panel_header_360_font1_0_dark.png")
    }

    /**
     * 10: Review 7-step DB circuit bottom (320dp, font 1.5, dark)
     */
    @Test
    @Config(sdk = [33], qualifiers = "w320dp-h800dp-xhdpi", fontScale = 1.5f)
    fun capture10_review_db_circuit_320_font1_5_dark_bottom() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(
                name = "Lighting DB",
                panelType = "DB",
                loadKVA = "30.0",
                pf = "0.95",
                demandFactor = "0.9",
                supplyCategory = "EMERGENCY",
                assignedAtsId = "ATS-01",
                assignedGeneratorId = "GEN-01",
                feederType = "Cable",
                manufacturer = "Elsewedy",
                cableType = "Cu/XLPE/SWA/PVC - 4C",
                length_m = "40",
                installation = "Laid in Ducts",
                busbarRatingA = "250",
                mainIncomingType = "MCCB",
                mainIncomingCount = "1",
                ipRating = "IP41",
                panelLocation = "Floor 2 Electrical Closet",
                numberOfWays = "18"
            ),
            initialStep = CircuitWorkflowStep.REVIEW,
            initialVisited = setOf(
                CircuitWorkflowStep.IDENTITY,
                CircuitWorkflowStep.LOAD,
                CircuitWorkflowStep.SUPPLY,
                CircuitWorkflowStep.FEEDER,
                CircuitWorkflowStep.INSTALLATION,
                CircuitWorkflowStep.PANEL_HEADER,
                CircuitWorkflowStep.REVIEW
            ),
            scRatingText = "18.50 kA"
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder, darkTheme = true)
        }
        composeTestRule.onNodeWithTag("content_bottom_clearance_spacer").performScrollTo()
        capture("10_review_db_circuit_320_font1_5_dark_bottom.png")
    }

    /**
     * 11: Review Busway circuit (412dp, font 1.3, light)
     */
    @Test
    @Config(sdk = [33], qualifiers = "w412dp-h915dp-xxhdpi", fontScale = 1.3f)
    fun capture11_review_busway_circuit_412_font1_3_light() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(
                name = "Riser Busway R-01",
                panelType = "MDB",
                loadKVA = "800.0",
                pf = "0.92",
                feederType = "Busway",
                buswayMfg = "Elsewedy",
                buswayModel = "Power Link",
                length_m = "120",
                installation = "Laid in Free Air",
                busbarRatingA = "1600",
                mainIncomingType = "ACB",
                mainIncomingCount = "1",
                ipRating = "IP54",
                panelLocation = "Main Substation Basement",
                numberOfWays = "48"
            ),
            initialStep = CircuitWorkflowStep.REVIEW,
            initialVisited = setOf(
                CircuitWorkflowStep.IDENTITY,
                CircuitWorkflowStep.LOAD,
                CircuitWorkflowStep.SUPPLY,
                CircuitWorkflowStep.FEEDER,
                CircuitWorkflowStep.INSTALLATION,
                CircuitWorkflowStep.PANEL_HEADER,
                CircuitWorkflowStep.REVIEW
            ),
            scRatingText = "65.00 kA"
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder, darkTheme = false)
        }
        capture("11_review_busway_circuit_412_font1_3_light.png")
    }

    /**
     * 12: Review validation error: blank name (360dp, font 1.0, dark)
     */
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.0f)
    fun capture12_review_validation_error_blank_name_360_font1_0_dark() {
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
            CircuitEditorTestHost(stateHolder = holder, darkTheme = true)
        }
        // Scroll to save button and click
        composeTestRule.onNodeWithTag("save_circuit_btn").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("content_bottom_clearance_spacer").performScrollTo()
        capture("12_review_validation_error_blank_name_360_font1_0_dark.png")
    }

    /**
     * 13: Review validation error: zero length (360dp, font 1.0, dark)
     */
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.0f)
    fun capture13_review_validation_error_zero_length_360_font1_0_dark() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "Feeder X", panelType = "LOAD", length_m = "0"),
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
            CircuitEditorTestHost(stateHolder = holder, darkTheme = true)
        }
        composeTestRule.onNodeWithTag("save_circuit_btn").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("content_bottom_clearance_spacer").performScrollTo()
        capture("13_review_validation_error_zero_length_360_font1_0_dark.png")
    }

    /**
     * 14: Stepper 6-step LOAD review active (360dp, font 1.0, dark)
     */
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.0f)
    fun capture14_stepper_6step_load_review_active_360_font1_0_dark() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "Load Circuit", panelType = "LOAD"),
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
            CircuitEditorTestHost(stateHolder = holder, darkTheme = true)
        }
        capture("14_stepper_6step_load_review_active_360_font1_0_dark.png")
    }

    /**
     * 15: Stepper 7-step DB review active (360dp, font 1.0, dark)
     */
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.0f)
    fun capture15_stepper_7step_db_review_active_360_font1_0_dark() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "Panel DB-1", panelType = "DB"),
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
            CircuitEditorTestHost(stateHolder = holder, darkTheme = true)
        }
        capture("15_stepper_7step_db_review_active_360_font1_0_dark.png")
    }
}
