package com.example.ui.dialogs.circuiteditor.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
 * Visual capture and bounds verification test suite for Stage R6:
 * Review Visual Remediation, Scroll Positioning & Override Parity.
 *
 * Generates and verifies the 7 owner-required captures:
 * 01: Review LOAD circuit top (360dp, font 1.0, dark)
 * 02: Review Installation target (360dp, font 1.5, dark)
 * 03: Review Panel target (320dp, font 1.5, dark)
 * 04: Review Panel target (412dp, font 1.3, light)
 * 05: Review LOAD bottom clearance (320dp, font 1.5, dark)
 * 06: Review Panel bottom clearance (360dp, font 1.5, dark)
 * 07: Review back & save actions (360dp, font 1.5, dark)
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

    private fun getVerticalScrollInfo(): Pair<Float, Float> {
        val scrollNode = composeTestRule.onAllNodes(hasScrollAction())
            .fetchSemanticsNodes()
            .firstOrNull { it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) != null }
        val scrollRange = scrollNode?.config?.getOrNull(SemanticsProperties.VerticalScrollAxisRange)
        val value = scrollRange?.value?.invoke() ?: 0f
        val maxValue = scrollRange?.maxValue?.invoke() ?: 0f
        return Pair(value, maxValue)
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
        val root = composeTestRule.onNodeWithTag("circuit_editor_root").getBoundsInRoot()

        assertTrue("Scroll viewport starts at or below top app bar", viewport.top >= topBar.bottom)
        assertTrue("Scroll viewport ends at or above sticky bottom bar", viewport.bottom <= actionBar.top)
        assertTrue("Save button inside root bottom", actionBar.bottom <= root.bottom)

        capture("01_review_load_top_360_font1_0_dark.png")
    }

    // =========================================================================
    // Capture 02: Review Installation target (360dp, font 1.5, dark)
    // =========================================================================
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.5f)
    fun capture02_review_installation_target_360_font1_5_dark() {
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

        // Target Section 5 Installation Conditions header
        val headerNode = composeTestRule.onNodeWithTag("review_installation_section_header")
        headerNode.performScrollTo()
        composeTestRule.waitForIdle()

        val viewport = composeTestRule.onNodeWithTag("circuit_editor_scroll_viewport").getBoundsInRoot()
        val header = headerNode.getBoundsInRoot()
        val title = composeTestRule.onNodeWithTag("review_installation_section_title").getBoundsInRoot()
        val editAction = composeTestRule.onNodeWithTag("review_edit_installation").getBoundsInRoot()

        val (scrollValue, maxScroll) = getVerticalScrollInfo()

        println("CAPTURE_02_TARGET: viewport=$viewport, header=$header, title=$title, editAction=$editAction, scroll=$scrollValue/$maxScroll")

        // Full title semantics verified
        composeTestRule.onNodeWithText("5. Installation Conditions").assertExists()

        // Contract: header and editAction fully inside viewport
        assertTrue("header.top >= viewport.top", header.top >= viewport.top)
        assertTrue("header.bottom <= viewport.bottom", header.bottom <= viewport.bottom)
        assertTrue("editAction.top >= viewport.top", editAction.top >= viewport.top)
        assertTrue("editAction.bottom <= viewport.bottom", editAction.bottom <= viewport.bottom)
        assertTrue("title.top >= viewport.top", title.top >= viewport.top)
        assertTrue("title.bottom <= viewport.bottom", title.bottom <= viewport.bottom)

        // Touch target >= 48dp
        val editWidth = editAction.right - editAction.left
        val editHeight = editAction.bottom - editAction.top
        assertTrue("editAction width >= 48dp", editWidth >= 48.dp)
        assertTrue("editAction height >= 48dp", editHeight >= 48.dp)

        capture("02_review_installation_target_360_font1_5_dark.png")
    }

    // =========================================================================
    // Capture 03: Review Panel target (320dp, font 1.5, dark)
    // =========================================================================
    @Test
    @Config(sdk = [33], qualifiers = "w320dp-h800dp-xhdpi", fontScale = 1.5f)
    fun capture03_review_panel_target_320_font1_5_dark() {
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

        // Target Section 6 Panel Header Equipment header
        val headerNode = composeTestRule.onNodeWithTag("review_panel_header_section_header")
        headerNode.performScrollTo()
        composeTestRule.waitForIdle()

        val viewport = composeTestRule.onNodeWithTag("circuit_editor_scroll_viewport").getBoundsInRoot()
        val header = headerNode.getBoundsInRoot()
        val title = composeTestRule.onNodeWithTag("review_panel_header_section_title").getBoundsInRoot()
        val editAction = composeTestRule.onNodeWithTag("review_edit_panel_header").getBoundsInRoot()

        val (scrollValue, maxScroll) = getVerticalScrollInfo()

        println("CAPTURE_03_TARGET: viewport=$viewport, header=$header, title=$title, editAction=$editAction, scroll=$scrollValue/$maxScroll")

        // Full title semantics verified
        composeTestRule.onNodeWithText("6. Panel Header Equipment").assertExists()

        // Contract: header and editAction fully inside viewport
        assertTrue("header.top >= viewport.top", header.top >= viewport.top)
        assertTrue("header.bottom <= viewport.bottom", header.bottom <= viewport.bottom)
        assertTrue("editAction.top >= viewport.top", editAction.top >= viewport.top)
        assertTrue("editAction.bottom <= viewport.bottom", editAction.bottom <= viewport.bottom)
        assertTrue("title.top >= viewport.top", title.top >= viewport.top)
        assertTrue("title.bottom <= viewport.bottom", title.bottom <= viewport.bottom)

        // Touch target >= 48dp
        val editWidth = editAction.right - editAction.left
        val editHeight = editAction.bottom - editAction.top
        assertTrue("editAction width >= 48dp", editWidth >= 48.dp)
        assertTrue("editAction height >= 48dp", editHeight >= 48.dp)

        capture("03_review_panel_target_320_font1_5_dark.png")
    }

    // =========================================================================
    // Capture 04: Review Panel target (412dp, font 1.3, light)
    // =========================================================================
    @Test
    @Config(sdk = [33], qualifiers = "w412dp-h915dp-xxhdpi", fontScale = 1.3f)
    fun capture04_review_panel_target_412_font1_3_light() {
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

        // Target Section 6 Panel Header Equipment header
        val headerNode = composeTestRule.onNodeWithTag("review_panel_header_section_header")
        headerNode.performScrollTo()
        composeTestRule.waitForIdle()

        val viewport = composeTestRule.onNodeWithTag("circuit_editor_scroll_viewport").getBoundsInRoot()
        val header = headerNode.getBoundsInRoot()
        val title = composeTestRule.onNodeWithTag("review_panel_header_section_title").getBoundsInRoot()
        val editAction = composeTestRule.onNodeWithTag("review_edit_panel_header").getBoundsInRoot()

        val (scrollValue, maxScroll) = getVerticalScrollInfo()

        println("CAPTURE_04_TARGET: viewport=$viewport, header=$header, title=$title, editAction=$editAction, scroll=$scrollValue/$maxScroll")

        // Full title semantics verified
        composeTestRule.onNodeWithText("6. Panel Header Equipment").assertExists()

        // Contract: header and editAction fully inside viewport
        assertTrue("header.top >= viewport.top", header.top >= viewport.top)
        assertTrue("header.bottom <= viewport.bottom", header.bottom <= viewport.bottom)
        assertTrue("editAction.top >= viewport.top", editAction.top >= viewport.top)
        assertTrue("editAction.bottom <= viewport.bottom", editAction.bottom <= viewport.bottom)
        assertTrue("title.top >= viewport.top", title.top >= viewport.top)
        assertTrue("title.bottom <= viewport.bottom", title.bottom <= viewport.bottom)

        // Touch target >= 48dp
        val editWidth = editAction.right - editAction.left
        val editHeight = editAction.bottom - editAction.top
        assertTrue("editAction width >= 48dp", editWidth >= 48.dp)
        assertTrue("editAction height >= 48dp", editHeight >= 48.dp)

        capture("04_review_panel_target_412_font1_3_light.png")
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

        // Scroll to the bottom clearance spacer
        composeTestRule.onNodeWithTag("content_bottom_clearance_spacer").performScrollTo()
        composeTestRule.waitForIdle()

        val saveBtn = composeTestRule.onNodeWithTag("save_circuit_btn").getBoundsInRoot()
        val installSection = composeTestRule.onNodeWithTag("review_installation_section").getBoundsInRoot()
        val root = composeTestRule.onNodeWithTag("circuit_editor_root").getBoundsInRoot()

        val clearance = saveBtn.top - installSection.bottom
        println("CAPTURE_05_BOTTOM: installBottom=${installSection.bottom}, saveTop=${saveBtn.top}, clearance=$clearance")

        assertTrue("Last content bottom must be <= saveBtn.top - 16dp", installSection.bottom <= saveBtn.top - 16.dp)
        assertTrue("Save button bottom inside root", saveBtn.bottom <= root.bottom)

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

        // Scroll to bottom clearance spacer
        composeTestRule.onNodeWithTag("content_bottom_clearance_spacer").performScrollTo()
        composeTestRule.waitForIdle()

        val saveBtn = composeTestRule.onNodeWithTag("save_circuit_btn").getBoundsInRoot()
        val panelSection = composeTestRule.onNodeWithTag("review_panel_header_section").getBoundsInRoot()
        val root = composeTestRule.onNodeWithTag("circuit_editor_root").getBoundsInRoot()

        val clearance = saveBtn.top - panelSection.bottom
        println("CAPTURE_06_BOTTOM: panelBottom=${panelSection.bottom}, saveTop=${saveBtn.top}, clearance=$clearance")

        assertTrue("Panel section bottom must be <= saveBtn.top - 16dp", panelSection.bottom <= saveBtn.top - 16.dp)
        assertTrue("Save button bottom inside root", saveBtn.bottom <= root.bottom)

        capture("06_review_panel_bottom_360_font1_5_dark.png")
    }

    // =========================================================================
    // Capture 07: Review back & save actions (360dp, font 1.5, dark)
    // =========================================================================
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.5f)
    fun capture07_review_back_save_360_font1_5_dark() {
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

        val backWidth = backBtn.right - backBtn.left
        val backHeight = backBtn.bottom - backBtn.top
        val saveWidth = saveBtn.right - saveBtn.left
        val saveHeight = saveBtn.bottom - saveBtn.top

        println("CAPTURE_07_ACTIONS: back=(${backWidth}x${backHeight}), save=(${saveWidth}x${saveHeight}), rootBottom=${root.bottom}")

        assertTrue("Back button width >= 48dp", backWidth >= 48.dp)
        assertTrue("Back button height >= 48dp", backHeight >= 48.dp)
        assertTrue("Save button width >= 48dp", saveWidth >= 48.dp)
        assertTrue("Save button height >= 48dp", saveHeight >= 48.dp)
        assertTrue("Back button inside root bottom", backBtn.bottom <= root.bottom)
        assertTrue("Save button inside root bottom", saveBtn.bottom <= root.bottom)

        capture("07_review_back_save_360_font1_5_dark.png")
    }
}
