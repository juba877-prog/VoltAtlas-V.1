package com.example.ui.dialogs.circuiteditor.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.ui.dialogs.circuiteditor.model.CircuitEditorDraft
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
 * Generates the 10 owner-required recaptures:
 * - 01: Review LOAD circuit top (360dp, font 1.0, dark)
 * - 02: Review LOAD circuit bottom (360dp, font 1.5, dark)
 * - 03: Review DB circuit panel bottom (320dp, font 1.5, dark)
 * - 04: Review validation error: blank name (360dp, font 1.0, dark)
 * - 05: Review validation error: zero length (360dp, font 1.0, dark)
 * - 06: Review Busway circuit (412dp, font 1.3, light)
 * - 07: Panel Header short-circuit available (360dp, font 1.0, dark)
 * - 08: Panel Header short-circuit unavailable (320dp, font 1.5, light)
 * - 09: Review EMERGENCY supply (360dp, font 1.3, dark)
 * - 10: Review UPS supply (360dp, font 1.3, light)
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
     * 01: Review, 6-step LOAD circuit top (360dp, font 1.0, dark)
     * Sticky bar shows Back + Save Circuit.
     */
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.0f)
    fun capture01_review_load_top_360_font1_0_dark() {
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
        capture("01_review_load_top_360_font1_0_dark.png")
    }

    /**
     * 02: Review, 6-step LOAD circuit bottom (360dp, font 1.5, dark)
     * Bottom-scrolled view showing clearance above sticky bar.
     */
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.5f)
    fun capture02_review_load_bottom_360_font1_5_dark() {
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
        capture("02_review_load_bottom_360_font1_5_dark.png")
    }

    /**
     * 03: Review, 7-step DB circuit with panel header data (320dp, font 1.5, dark)
     * Bottom-scrolled view showing panel header summary and sticky bar clearance.
     */
    @Test
    @Config(sdk = [33], qualifiers = "w320dp-h800dp-xhdpi", fontScale = 1.5f)
    fun capture03_review_panel_bottom_320_font1_5_dark() {
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
                numberOfWays = "18",
                feederDescription = "Substation incoming feed"
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
        capture("03_review_panel_bottom_320_font1_5_dark.png")
    }

    /**
     * 04: Review validation error: blank name (360dp, font 1.0, dark)
     * User tapped Save; validation error banner visible above sticky bar.
     */
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.0f)
    fun capture04_review_validation_blank_360_font1_0_dark() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "", panelType = "LOAD", length_m = "50"),
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
        composeTestRule.onNodeWithTag("save_circuit_btn").performClick()
        capture("04_review_validation_blank_360_font1_0_dark.png")
    }

    /**
     * 05: Review validation error: zero length (360dp, font 1.0, dark)
     * User tapped Save; validation error banner visible above sticky bar.
     */
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.0f)
    fun capture05_review_validation_length_360_font1_0_dark() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "Feeder Valid", panelType = "LOAD", length_m = "0"),
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
        composeTestRule.onNodeWithTag("save_circuit_btn").performClick()
        capture("05_review_validation_length_360_font1_0_dark.png")
    }

    /**
     * 06: Review Busway circuit (412dp, font 1.3, light)
     * Feeder card shows Busway details (manufacturer, model, length).
     */
    @Test
    @Config(sdk = [33], qualifiers = "w412dp-h915dp-xxhdpi", fontScale = 1.3f)
    fun capture06_review_busway_412_font1_3_light() {
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
        capture("06_review_busway_412_font1_3_light.png")
    }

    /**
     * 07: Panel Header short-circuit rating available (360dp, font 1.0, dark)
     * Read-only MD3 tonal card visible with calculated rating.
     */
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.0f)
    fun capture07_panel_sc_available_360_font1_0_dark() {
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
                feederDescription = "Main incoming supply from TX-01"
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
        capture("07_panel_sc_available_360_font1_0_dark.png")
    }

    /**
     * 08: Panel Header short-circuit rating unavailable (320dp, font 1.5, light)
     * Read-only MD3 tonal card visible, responsive stacking at 320dp / 1.5x.
     */
    @Test
    @Config(sdk = [33], qualifiers = "w320dp-h800dp-xhdpi", fontScale = 1.5f)
    fun capture08_panel_sc_unavailable_320_font1_5_light() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(
                name = "Distribution Board 1",
                panelType = "DB",
                busbarRatingA = "400",
                mainIncomingType = "MCCB",
                mainIncomingCount = "1",
                ipRating = "IP54",
                panelLocation = "Substation 1",
                numberOfWays = "12",
                feederDescription = "Feeder from TX-01"
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
            scRatingText = "Unavailable (Calculated after save)"
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder, darkTheme = false)
        }
        capture("08_panel_sc_unavailable_320_font1_5_light.png")
    }

    /**
     * 09: Review, EMERGENCY supply category (360dp, font 1.3, dark)
     * Supply card shows ATS and Generator, no UPS.
     */
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.3f)
    fun capture09_review_emergency_supply_360_font1_3_dark() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(
                name = "Emergency Sub-Panel",
                panelType = "LOAD",
                loadKVA = "50.0",
                pf = "0.90",
                supplyCategory = "EMERGENCY",
                assignedAtsId = "ATS-01",
                assignedGeneratorId = "GEN-01",
                feederType = "Cable",
                manufacturer = "Elsewedy",
                cableType = "Cu/XLPE/SWA/PVC - 4C",
                length_m = "30",
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
        capture("09_review_emergency_supply_360_font1_3_dark.png")
    }

    /**
     * 10: Review, UPS supply category (360dp, font 1.3, light)
     * Supply card shows UPS, no ATS or Generator.
     */
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.3f)
    fun capture10_review_ups_supply_360_font1_3_light() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(
                name = "Data Center Rack A",
                panelType = "LOAD",
                loadKVA = "15.0",
                pf = "0.95",
                supplyCategory = "UPS",
                assignedUpsId = "UPS-01",
                feederType = "Cable",
                manufacturer = "Elsewedy",
                cableType = "Cu/XLPE/SWA/PVC - 4C",
                length_m = "15",
                installation = "Laid in Trays"
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
            CircuitEditorTestHost(stateHolder = holder, darkTheme = false)
        }
        capture("10_review_ups_supply_360_font1_3_light.png")
    }
}
