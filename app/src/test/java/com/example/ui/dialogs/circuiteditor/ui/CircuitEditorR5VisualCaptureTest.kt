package com.example.ui.dialogs.circuiteditor.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.example.ui.dialogs.circuiteditor.model.CircuitEditorDraft
import com.example.ui.dialogs.circuiteditor.model.CircuitWorkflowStep
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Visual capture and bounds verification test suite for Stage R5:
 * Review Visual Remediation & Override Parity Audit.
 *
 * Generates and verifies the 9 owner-required captures:
 * 01: Review LOAD circuit top (360dp, font 1.0, dark)
 * 02: Review LOAD circuit installation header (360dp, font 1.5, dark)
 * 03: Review Panel Header equipment (320dp, font 1.5, dark)
 * 04: Review Panel Header equipment (412dp, font 1.3, light)
 * 05: Review LOAD bottom clearance (320dp, font 1.5, dark)
 * 06: Review Panel bottom clearance (360dp, font 1.5, dark)
 * 07: Review edit identity target (320dp, font 1.5, light)
 * 08: Review edit panel target (320dp, font 1.5, dark)
 * 09: Review back & save actions (360dp, font 1.5, dark)
 *
 * Evidence Label: NEW_EDITOR_RUNTIME_OBSERVED
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CircuitEditorR5VisualCaptureTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val outputDir = File("build/outputs/roborazzi/circuit_editor_r5").apply { mkdirs() }
    private val brainDir = File("C:/Users/mahmoud.mohamed/.gemini/antigravity/brain/1abfe00f-638e-4dd5-93ae-22beb68021fd")

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
        if (brainDir.exists()) {
            file.copyTo(File(brainDir, fileName), overwrite = true)
        }
    }

    private fun createLoadDraft() = CircuitEditorDraft(
        name = "Pump Motor 1",
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
    )

    private fun createPanelDraft() = CircuitEditorDraft(
        name = "Main Distribution Board",
        panelType = "MDB",
        loadKVA = "250.0",
        pf = "0.90",
        demandFactor = "0.85",
        voltageV = "400",
        phases = "3P+N",
        loadNature = "Static",
        supplyCategory = "EMERGENCY + UPS",
        assignedGeneratorId = "GEN-01",
        assignedAtsId = "ATS-01",
        assignedUpsId = "UPS-01",
        feederType = "Cable",
        manufacturer = "Elsewedy",
        cableType = "Cu/XLPE/SWA/PVC - 4C",
        length_m = "40",
        installation = "Laid in Free Air",
        busbarRatingA = "630",
        mainIncomingType = "MCCB",
        mainIncomingCount = "1",
        ipRating = "IP54",
        panelLocation = "Main Substation",
        cableEntry = "Bottom",
        numberOfWays = "24",
        feederDescription = "Incoming Supply"
    )

    // =========================================================================
    // Capture 01: Review LOAD circuit top (360dp, font 1.0, dark)
    // =========================================================================
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.0f)
    fun capture01_review_load_top_360_font1_0_dark() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = createLoadDraft(),
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

        val topBar = composeTestRule.onNodeWithTag("circuit_editor_top_fixed_bar").getBoundsInRoot()
        val viewport = composeTestRule.onNodeWithTag("circuit_editor_scroll_viewport").getBoundsInRoot()
        val actionBar = composeTestRule.onNodeWithTag("save_circuit_btn").getBoundsInRoot()

        assertTrue("Viewport must be below top bar", viewport.top >= topBar.bottom)
        composeTestRule.onNodeWithTag("circuit_editor_step_header").assertIsDisplayed()
        composeTestRule.onNodeWithTag("review_identity_section").assertIsDisplayed()

        capture("01_review_load_top_360_font1_0_dark.png")
    }

    // =========================================================================
    // Capture 02: Review LOAD circuit installation header (360dp, font 1.5, dark)
    // =========================================================================
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.5f)
    fun capture02_review_load_installation_header_360_font1_5_dark() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = createLoadDraft(),
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

        // Programmatic scrolling to Section 5
        val installSection = composeTestRule.onNodeWithTag("review_installation_section")
        installSection.performScrollTo()
        composeTestRule.waitForIdle()

        val viewport = composeTestRule.onNodeWithTag("circuit_editor_scroll_viewport").getBoundsInRoot()
        val installBounds = installSection.getBoundsInRoot()
        val editTarget = composeTestRule.onNodeWithTag("review_edit_installation").getBoundsInRoot()

        assertTrue("Installation section top must be at or below scroll viewport top", installBounds.top >= viewport.top)
        assertTrue("Installation section top must be visible inside viewport", installBounds.top < viewport.bottom)
        assertTrue("Edit target width >= 48dp at font 1.5", (editTarget.right - editTarget.left) >= 48.dp)
        assertTrue("Edit target height >= 48dp at font 1.5", (editTarget.bottom - editTarget.top) >= 48.dp)
        assertTrue("Edit target inside card", editTarget.right <= installBounds.right)

        capture("02_review_load_installation_header_360_font1_5_dark.png")
    }

    // =========================================================================
    // Capture 03: Review Panel Header equipment (320dp, font 1.5, dark)
    // =========================================================================
    @Test
    @Config(sdk = [33], qualifiers = "w320dp-h800dp-xhdpi", fontScale = 1.5f)
    fun capture03_review_panel_header_320_font1_5_dark() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = createPanelDraft(),
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

        // Programmatic scrolling to Section 6 Panel Header
        val panelSection = composeTestRule.onNodeWithTag("review_panel_header_section")
        panelSection.performScrollTo()
        composeTestRule.waitForIdle()

        val viewport = composeTestRule.onNodeWithTag("circuit_editor_scroll_viewport").getBoundsInRoot()
        val panelBounds = panelSection.getBoundsInRoot()
        val editTarget = composeTestRule.onNodeWithTag("review_edit_panel_header").getBoundsInRoot()

        assertTrue("Panel header section top must be at or below viewport top", panelBounds.top >= viewport.top)
        assertTrue("Edit target width >= 48dp at 320dp font 1.5", (editTarget.right - editTarget.left) >= 48.dp)
        assertTrue("Edit target height >= 48dp at 320dp font 1.5", (editTarget.bottom - editTarget.top) >= 48.dp)
        assertTrue("Edit target inside card right", editTarget.right <= panelBounds.right)

        capture("03_review_panel_header_320_font1_5_dark.png")
    }

    // =========================================================================
    // Capture 04: Review Panel Header equipment (412dp, font 1.3, light)
    // =========================================================================
    @Test
    @Config(sdk = [33], qualifiers = "w412dp-h915dp-xxhdpi", fontScale = 1.3f)
    fun capture04_review_panel_header_412_font1_3_light() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = createPanelDraft(),
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
            CircuitEditorTestHost(stateHolder = holder, darkTheme = false)
        }

        val panelSection = composeTestRule.onNodeWithTag("review_panel_header_section")
        panelSection.performScrollTo()
        composeTestRule.waitForIdle()

        val editTarget = composeTestRule.onNodeWithTag("review_edit_panel_header").getBoundsInRoot()
        assertTrue("Edit target width >= 48dp at 412dp font 1.3", (editTarget.right - editTarget.left) >= 48.dp)
        assertTrue("Edit target height >= 48dp at 412dp font 1.3", (editTarget.bottom - editTarget.top) >= 48.dp)

        capture("04_review_panel_header_412_font1_3_light.png")
    }

    // =========================================================================
    // Capture 05: Review LOAD bottom clearance (320dp, font 1.5, dark)
    // =========================================================================
    @Test
    @Config(sdk = [33], qualifiers = "w320dp-h800dp-xhdpi", fontScale = 1.5f)
    fun capture05_review_load_bottom_320_font1_5_dark() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = createLoadDraft(),
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

        // Scroll to bottom spacer
        composeTestRule.onNodeWithTag("content_bottom_clearance_spacer").performScrollTo()
        composeTestRule.waitForIdle()

        val saveBtn = composeTestRule.onNodeWithTag("save_circuit_btn").getBoundsInRoot()
        val installSection = composeTestRule.onNodeWithTag("review_installation_section").getBoundsInRoot()
        val root = composeTestRule.onNodeWithTag("circuit_editor_root").getBoundsInRoot()

        assertTrue("Last content bottom must be <= saveBtn.top - 16dp", installSection.bottom <= saveBtn.top - 16.dp)
        assertTrue("Save button bottom must be inside visible root", saveBtn.bottom <= root.bottom)

        capture("05_review_load_bottom_320_font1_5_dark.png")
    }

    // =========================================================================
    // Capture 06: Review Panel bottom clearance (360dp, font 1.5, dark)
    // =========================================================================
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.5f)
    fun capture06_review_panel_bottom_360_font1_5_dark() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = createPanelDraft(),
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

        composeTestRule.onNodeWithTag("content_bottom_clearance_spacer").performScrollTo()
        composeTestRule.waitForIdle()

        val saveBtn = composeTestRule.onNodeWithTag("save_circuit_btn").getBoundsInRoot()
        val panelSection = composeTestRule.onNodeWithTag("review_panel_header_section").getBoundsInRoot()
        val root = composeTestRule.onNodeWithTag("circuit_editor_root").getBoundsInRoot()

        assertTrue("Panel section bottom must be <= saveBtn.top - 16dp", panelSection.bottom <= saveBtn.top - 16.dp)
        assertTrue("Save button bottom inside root", saveBtn.bottom <= root.bottom)

        capture("06_review_panel_bottom_360_font1_5_dark.png")
    }

    // =========================================================================
    // Capture 07: Review edit identity target (320dp, font 1.5, light)
    // =========================================================================
    @Test
    @Config(sdk = [33], qualifiers = "w320dp-h800dp-xhdpi", fontScale = 1.5f)
    fun capture07_review_edit_identity_target_320_font1_5_light() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = createLoadDraft(),
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

        val card = composeTestRule.onNodeWithTag("review_identity_section").getBoundsInRoot()
        val editTarget = composeTestRule.onNodeWithTag("review_edit_identity").getBoundsInRoot()

        assertTrue("Identity editTarget width >= 48dp", (editTarget.right - editTarget.left) >= 48.dp)
        assertTrue("Identity editTarget height >= 48dp", (editTarget.bottom - editTarget.top) >= 48.dp)
        assertTrue("Identity editTarget.right <= card.right", editTarget.right <= card.right)
        assertTrue("Identity editTarget.top >= card.top", editTarget.top >= card.top)

        capture("07_review_edit_identity_target_320_font1_5_light.png")
    }

    // =========================================================================
    // Capture 08: Review edit panel target (320dp, font 1.5, dark)
    // =========================================================================
    @Test
    @Config(sdk = [33], qualifiers = "w320dp-h800dp-xhdpi", fontScale = 1.5f)
    fun capture08_review_edit_panel_target_320_font1_5_dark() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = createPanelDraft(),
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

        val panelSection = composeTestRule.onNodeWithTag("review_panel_header_section")
        panelSection.performScrollTo()
        composeTestRule.waitForIdle()

        val card = panelSection.getBoundsInRoot()
        val editTarget = composeTestRule.onNodeWithTag("review_edit_panel_header").getBoundsInRoot()

        assertTrue("Panel editTarget width >= 48dp", (editTarget.right - editTarget.left) >= 48.dp)
        assertTrue("Panel editTarget height >= 48dp", (editTarget.bottom - editTarget.top) >= 48.dp)
        assertTrue("Panel editTarget.right <= card.right", editTarget.right <= card.right)
        assertTrue("Panel editTarget.top >= card.top", editTarget.top >= card.top)

        capture("08_review_edit_panel_target_320_font1_5_dark.png")
    }

    // =========================================================================
    // Capture 09: Review back & save actions (360dp, font 1.5, dark)
    // =========================================================================
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.5f)
    fun capture09_review_back_save_360_font1_5_dark() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = createLoadDraft(),
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

        val backBtn = composeTestRule.onNodeWithTag("circuit_editor_back_btn").getBoundsInRoot()
        val saveBtn = composeTestRule.onNodeWithTag("save_circuit_btn").getBoundsInRoot()
        val root = composeTestRule.onNodeWithTag("circuit_editor_root").getBoundsInRoot()

        assertTrue("Back button width >= 48dp", (backBtn.right - backBtn.left) >= 48.dp)
        assertTrue("Back button height >= 48dp", (backBtn.bottom - backBtn.top) >= 48.dp)
        assertTrue("Save button width >= 48dp", (saveBtn.right - saveBtn.left) >= 48.dp)
        assertTrue("Save button height >= 48dp", (saveBtn.bottom - saveBtn.top) >= 48.dp)
        assertTrue("Back button inside root bottom", backBtn.bottom <= root.bottom)
        assertTrue("Save button inside root bottom", saveBtn.bottom <= root.bottom)

        capture("09_review_back_save_360_font1_5_dark.png")
    }
}
