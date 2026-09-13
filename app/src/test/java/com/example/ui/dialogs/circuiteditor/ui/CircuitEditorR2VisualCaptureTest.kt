package com.example.ui.dialogs.circuiteditor.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
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
 * R2 visual captures over the real host (sticky headers, scroll viewport, action bar).
 *
 * Classification: every image is a ROBOLECTRIC capture. Nothing here is labeled
 * DEVICE_VERIFIED. Emulator/physical-device validation remains deferred by owner decision.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CircuitEditorR2VisualCaptureTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val outputDir = File("build/outputs/roborazzi/circuit_editor_r2").apply { mkdirs() }
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

    private fun logBounds(tag: String, captureName: String, primaryTag: String? = null) {
        val root = composeTestRule.onNodeWithTag("circuit_editor_root").getBoundsInRoot()
        val bar = composeTestRule.onNodeWithTag("circuit_editor_action_bar").getBoundsInRoot()
        val viewport = composeTestRule.onNodeWithTag("circuit_editor_scroll_viewport").getBoundsInRoot()
        val primary = primaryTag?.let { composeTestRule.onNodeWithTag(it).getBoundsInRoot() }
        val primaryHeight = primary?.let { (it.bottom - it.top).value } ?: 0f
        println("[R2_CAPTURE_LEDGER] $captureName | root: [${root.left}, ${root.top}, ${root.right}, ${root.bottom}] | bar: [${bar.left}, ${bar.top}, ${bar.right}, ${bar.bottom}] | viewport: [${viewport.left}, ${viewport.top}, ${viewport.right}, ${viewport.bottom}] | primary: $primaryTag [${primary?.left}, ${primary?.top}, ${primary?.right}, ${primary?.bottom}] (${primaryHeight}dp)")
    }

    // 01: Identity (360dp, font 1.0, dark). Complete Next must be inside the window.
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.0f)
    fun capture01_identity_360_dark() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "New Load", panelType = "LOAD", fedFrom = "Transformer")
        )
        composeTestRule.setContent { CircuitEditorTestHost(stateHolder = holder) }
        logBounds("capture01", "01_identity_360_dark", "circuit_editor_next_btn")
        capture("01_identity_360_dark.png")
    }

    // 02: Load (360dp, font 1.0, dark). Complete Back + Next.
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.0f)
    fun capture02_load_360_dark() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "Load A", panelType = "LOAD", loadKVA = "25.0"),
            initialStep = CircuitWorkflowStep.LOAD,
            initialVisited = setOf(CircuitWorkflowStep.IDENTITY, CircuitWorkflowStep.LOAD)
        )
        composeTestRule.setContent { CircuitEditorTestHost(stateHolder = holder) }
        logBounds("capture02", "02_load_360_dark", "circuit_editor_next_btn")
        capture("02_load_360_dark.png")
    }

    // 03: Load bottom at 320dp font 1.5 dark (bottom scrolled).
    @Test
    @Config(sdk = [33], qualifiers = "w320dp-h800dp-xhdpi", fontScale = 1.5f)
    fun capture03_load_320_font1_5_dark_bottom() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "Load B", panelType = "LOAD"),
            initialStep = CircuitWorkflowStep.LOAD,
            initialVisited = setOf(CircuitWorkflowStep.IDENTITY, CircuitWorkflowStep.LOAD)
        )
        composeTestRule.setContent { CircuitEditorTestHost(stateHolder = holder) }
        composeTestRule.onNodeWithTag("content_bottom_clearance_spacer").performScrollTo()
        logBounds("capture03", "03_load_320_font1_5_dark_bottom", "circuit_editor_next_btn")
        capture("03_load_320_font1_5_dark_bottom.png")
    }

    // 04: Feeder with Cable Family Picker open (360dp dark).
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.0f)
    fun capture04_feeder_360_picker_open_dark() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(
                name = "Feeder C", panelType = "LOAD", feederType = "Cable",
                manufacturer = "Elsewedy", cableType = "Cu/XLPE/SWA/PVC - 4C"
            ),
            initialStep = CircuitWorkflowStep.FEEDER,
            initialVisited = setOf(
                CircuitWorkflowStep.IDENTITY, CircuitWorkflowStep.LOAD,
                CircuitWorkflowStep.SUPPLY, CircuitWorkflowStep.FEEDER
            )
        )
        composeTestRule.setContent { CircuitEditorTestHost(stateHolder = holder) }
        composeTestRule.onNodeWithTag("open_cable_family_picker", useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()
        logBounds("capture04", "04_feeder_360_picker_open_dark", "cable_family_confirm")
        capture("04_feeder_360_picker_open_dark.png")
    }

    // 05: Feeder after closing the picker (picker closed restores the complete action bar).
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.0f)
    fun capture05_feeder_360_picker_closed_dark() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(
                name = "Feeder D", panelType = "LOAD", feederType = "Cable",
                manufacturer = "Elsewedy", cableType = "Cu/XLPE/SWA/PVC - 4C"
            ),
            initialStep = CircuitWorkflowStep.FEEDER,
            initialVisited = setOf(
                CircuitWorkflowStep.IDENTITY, CircuitWorkflowStep.LOAD,
                CircuitWorkflowStep.SUPPLY, CircuitWorkflowStep.FEEDER
            )
        )
        composeTestRule.setContent { CircuitEditorTestHost(stateHolder = holder) }
        composeTestRule.onNodeWithTag("open_cable_family_picker", useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("cable_family_cancel").performClick()
        composeTestRule.waitForIdle()
        logBounds("capture05", "05_feeder_360_picker_closed_dark", "circuit_editor_next_btn")
        capture("05_feeder_360_picker_closed_dark.png")
    }

    // 06: Panel Header applicable (DB) bottom at 320dp font 1.5 light.
    @Test
    @Config(sdk = [33], qualifiers = "w320dp-h800dp-xhdpi", fontScale = 1.5f)
    fun capture06_panel_header_320_font1_5_light_bottom() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "DB-6", panelType = "DB"),
            initialStep = CircuitWorkflowStep.PANEL_HEADER,
            initialVisited = setOf(
                CircuitWorkflowStep.IDENTITY, CircuitWorkflowStep.LOAD, CircuitWorkflowStep.SUPPLY,
                CircuitWorkflowStep.FEEDER, CircuitWorkflowStep.INSTALLATION, CircuitWorkflowStep.PANEL_HEADER
            )
        )
        composeTestRule.setContent { CircuitEditorTestHost(stateHolder = holder, darkTheme = false) }
        composeTestRule.onNodeWithTag("content_bottom_clearance_spacer").performScrollTo()
        logBounds("capture06", "06_panel_header_320_font1_5_light_bottom", "circuit_editor_next_btn")
        capture("06_panel_header_320_font1_5_light_bottom.png")
    }

    // 07: Review (360dp, font 1.5, dark, bottom-scrolled). Complete Back + Save Circuit, no Next.
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.5f)
    fun capture07_review_360_font1_5_dark_bottom() {
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
                CircuitWorkflowStep.IDENTITY, CircuitWorkflowStep.LOAD,
                CircuitWorkflowStep.SUPPLY, CircuitWorkflowStep.FEEDER,
                CircuitWorkflowStep.INSTALLATION, CircuitWorkflowStep.REVIEW
            )
        )
        composeTestRule.setContent { CircuitEditorTestHost(stateHolder = holder, darkTheme = true) }
        composeTestRule.onNodeWithTag("content_bottom_clearance_spacer").performScrollTo()
        logBounds("capture07", "07_review_360_font1_5_dark_bottom", "save_circuit_btn")
        capture("07_review_360_font1_5_dark_bottom.png")
    }

    // 08: Identity (412dp, font 1.3, light). Complete Next visible under larger phone/light theme.
    @Test
    @Config(sdk = [33], qualifiers = "w412dp-h915dp-xxhdpi", fontScale = 1.3f)
    fun capture08_identity_412_font1_3_light() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "Main Panel", panelType = "LOAD", fedFrom = "Transformer")
        )
        composeTestRule.setContent { CircuitEditorTestHost(stateHolder = holder, darkTheme = false) }
        logBounds("capture08", "08_identity_412_font1_3_light", "circuit_editor_next_btn")
        capture("08_identity_412_font1_3_light.png")
    }

    // 09: Load with IME open (360dp, font 1.0, dark). Focus an editable field.
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.0f)
    fun capture09_load_360_ime_open_dark() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "Load IME", panelType = "LOAD", loadKVA = "25.0"),
            initialStep = CircuitWorkflowStep.LOAD,
            initialVisited = setOf(CircuitWorkflowStep.IDENTITY, CircuitWorkflowStep.LOAD)
        )
        composeTestRule.setContent { CircuitEditorTestHost(stateHolder = holder, darkTheme = true) }
        composeTestRule.onNode(hasParent(hasTestTag("input_load_kva")) and hasSetTextAction()).performClick()
        composeTestRule.waitForIdle()
        logBounds("capture09", "09_load_360_ime_open_dark", "circuit_editor_next_btn")
        capture("09_load_360_ime_open_dark.png")
    }
}
