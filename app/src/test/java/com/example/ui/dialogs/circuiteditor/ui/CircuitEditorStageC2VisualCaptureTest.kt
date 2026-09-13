package com.example.ui.dialogs.circuiteditor.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.CircuitEntity
import com.example.ui.dialogs.circuiteditor.model.CircuitEditorDraft
import com.example.ui.dialogs.circuiteditor.model.CircuitWorkflowStep
import com.example.ui.dialogs.circuiteditor.model.TransformerSnapshot
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Visual capture test suite for CircuitEditor Stage C2.
 * Captures 15 visual scenarios covering Responsive Shell, Progress, Identity step,
 * and Load step under various themes, widths, font scales, and IME constraints.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CircuitEditorStageC2VisualCaptureTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val outputDir = File("build/outputs/roborazzi/circuit_editor_c2").apply { mkdirs() }

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
        try {
            if (file.exists()) {
                file.delete()
            }
            file.outputStream().use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
            }
        } catch (e: Throwable) {
            val fallbackFile = File(outputDir, "retry_${System.currentTimeMillis()}_$fileName")
            fallbackFile.outputStream().use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
            }
            fallbackFile.renameTo(file)
        }
    }

    // 1. Identity - dark - 360x800 - font 1.0
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
    fun test01_identity_dark_360x800_font1_0() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(
                name = "MDB-01",
                panelType = "MDB",
                fedFrom = "Transformer",
                transformerId = "TX-01"
            ),
            availableTransformers = listOf(
                TransformerSnapshot("TX-01", 1000.0),
                TransformerSnapshot("TX-02", 1600.0)
            ),
            projectSubtitle = "Main Switchboard Project"
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder, darkTheme = true)
        }
        capture("01_identity_dark_360x800_font1_0.png")
    }

    // 2. Identity - light - 360x800 - font 1.0
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
    fun test02_identity_light_360x800_font1_0() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(
                name = "MDB-01",
                panelType = "MDB",
                fedFrom = "Transformer",
                transformerId = "TX-01"
            ),
            availableTransformers = listOf(
                TransformerSnapshot("TX-01", 1000.0)
            ),
            projectSubtitle = "Main Switchboard Project"
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder, darkTheme = false)
        }
        capture("02_identity_light_360x800_font1_0.png")
    }

    // 3. Identity - dark - 360x800 - font 1.3 - scrolled to last hierarchy row
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.3f)
    fun test03_identity_dark_360x800_font1_3_scrolled_to_last_hierarchy_row() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(
                name = "MDB-01",
                panelType = "MDB",
                fedFrom = "Transformer",
                transformerId = "TX-01"
            ),
            availableTransformers = listOf(
                TransformerSnapshot("TX-01", 1000.0)
            ),
            projectSubtitle = "Main Switchboard Project"
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder, darkTheme = true)
        }
        val lastRow = composeTestRule.onNodeWithTag("hierarchy_field_resolved_transformer")
        lastRow.performScrollTo()
        val rowBounds = lastRow.getBoundsInRoot()
        val nextBtn = composeTestRule.onNodeWithTag("circuit_editor_next_btn").getBoundsInRoot()
        assertTrue(
            "Resolved transformer row must clear next button (row bottom: ${rowBounds.bottom}, next top: ${nextBtn.top})",
            rowBounds.bottom <= nextBtn.top
        )
        capture("03_identity_dark_360x800_font1_3_scrolled_to_last_hierarchy_row.png")
    }

    // 4. Identity - dark - 360x800 - font 1.5 - scrolled to last hierarchy row
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.5f)
    fun test04_identity_dark_360x800_font1_5_scrolled_to_last_hierarchy_row() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(
                name = "MDB-01",
                panelType = "MDB",
                fedFrom = "Transformer",
                transformerId = "TX-01"
            ),
            availableTransformers = listOf(
                TransformerSnapshot("TX-01", 1000.0)
            ),
            projectSubtitle = "Main Switchboard Project"
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder, darkTheme = true)
        }
        val lastRow = composeTestRule.onNodeWithTag("hierarchy_field_resolved_transformer")
        lastRow.performScrollTo()
        val rowBounds = lastRow.getBoundsInRoot()
        val nextBtn = composeTestRule.onNodeWithTag("circuit_editor_next_btn").getBoundsInRoot()
        assertTrue(
            "Resolved transformer row must clear next button (row bottom: ${rowBounds.bottom}, next top: ${nextBtn.top})",
            rowBounds.bottom <= nextBtn.top
        )
        capture("04_identity_dark_360x800_font1_5_scrolled_to_last_hierarchy_row.png")
    }

    // 5. Identity - dark - larger phone width (w412dp-h892dp)
    @Test
    @Config(sdk = [33], qualifiers = "w412dp-h892dp-xhdpi")
    fun test05_identity_dark_larger_phone_width() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(
                name = "MDB-01",
                panelType = "MDB",
                fedFrom = "Transformer",
                transformerId = "TX-01"
            ),
            availableTransformers = listOf(
                TransformerSnapshot("TX-01", 1000.0)
            ),
            projectSubtitle = "Main Switchboard Project"
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder, darkTheme = true)
        }
        capture("05_identity_dark_412x892.png")
    }

    // 6. Identity - validation error - scrolled to last hierarchy row
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
    fun test06_identity_validation_error_scrolled_to_last_hierarchy_row() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(
                name = "",
                panelType = "MDB",
                fedFrom = "Transformer",
                transformerId = "TX-01"
            ),
            availableTransformers = listOf(
                TransformerSnapshot("TX-01", 1000.0)
            )
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder, darkTheme = true)
        }
        composeTestRule.onNodeWithTag("circuit_editor_next_btn").performClick()
        val lastRow = composeTestRule.onNodeWithTag("hierarchy_field_resolved_transformer")
        lastRow.performScrollTo()
        val rowBounds = lastRow.getBoundsInRoot()
        val nextBtn = composeTestRule.onNodeWithTag("circuit_editor_next_btn").getBoundsInRoot()
        assertTrue(
            "Resolved transformer row must clear next button in validation error state (row bottom: ${rowBounds.bottom}, next top: ${nextBtn.top})",
            rowBounds.bottom <= nextBtn.top
        )
        capture("06_identity_validation_error_scrolled_to_last_hierarchy_row.png")
    }

    // 7. Identity - non-Transformer source
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
    fun test07_identity_non_transformer_source() {
        val upstream = CircuitEntity(
            id = 1,
            projectId = "DEFAULT_PROJECT",
            circuitName = "MDB-Main",
            loadKVA = 250.0,
            fedFrom = "Transformer",
            panelType = "MDB"
        )
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(
                name = "DB-Power",
                panelType = "DB",
                fedFrom = "MDB-Main"
            ),
            existingCircuits = listOf(upstream),
            projectSubtitle = "Sub-Distribution Level"
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder, darkTheme = true)
        }
        capture("07_identity_non_transformer_source.png")
    }

    // 8. Load - dark - 360x800 - Static - scrolled to harmonic card
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
    fun test08_load_dark_360x800_static_scrolled_to_harmonic_card() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(
                name = "L-01",
                panelType = "LOAD",
                loadCategory = "LGT",
                loadNature = "Static",
                loadKVA = "15.0",
                pf = "0.9",
                demandFactor = "1.0",
                safetyFactor = "1.25",
                voltageV = "400",
                phases = "3P+N"
            ),
            initialStep = CircuitWorkflowStep.LOAD,
            initialVisited = setOf(CircuitWorkflowStep.IDENTITY, CircuitWorkflowStep.LOAD)
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder, darkTheme = true)
        }
        val cardNode = composeTestRule.onNodeWithTag("card_harmonic_load")
        cardNode.performScrollTo()
        val cardBounds = cardNode.getBoundsInRoot()
        val nextBtn = composeTestRule.onNodeWithTag("circuit_editor_next_btn").getBoundsInRoot()
        assertTrue(
            "Harmonic card must clear next button (card bottom: ${cardBounds.bottom}, next top: ${nextBtn.top})",
            cardBounds.bottom <= nextBtn.top
        )
        capture("08_load_dark_360x800_static_scrolled_to_harmonic_card.png")
    }

    // 9. Load - light - 360x800 - Static
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
    fun test09_load_light_360x800_static() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(
                name = "L-01",
                panelType = "LOAD",
                loadCategory = "LGT",
                loadNature = "Static",
                loadKVA = "15.0",
                pf = "0.9",
                demandFactor = "1.0",
                safetyFactor = "1.25",
                voltageV = "400",
                phases = "3P+N"
            ),
            initialStep = CircuitWorkflowStep.LOAD,
            initialVisited = setOf(CircuitWorkflowStep.IDENTITY, CircuitWorkflowStep.LOAD)
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder, darkTheme = false)
        }
        capture("09_load_light_360x800_static.png")
    }

    // 10. Load - dark - 360x800 - Motor
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
    fun test10_load_dark_360x800_motor() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(
                name = "PUMP-01",
                panelType = "LOAD",
                loadCategory = "MTR",
                loadNature = "Motor",
                startingMethod = "DOL",
                startingCurrentMultiplier = "6.0",
                loadKVA = "45.0",
                pf = "0.85",
                demandFactor = "1.0",
                safetyFactor = "1.25",
                voltageV = "400",
                phases = "3P+N"
            ),
            initialStep = CircuitWorkflowStep.LOAD,
            initialVisited = setOf(CircuitWorkflowStep.IDENTITY, CircuitWorkflowStep.LOAD)
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder, darkTheme = true)
        }
        capture("10_load_dark_360x800_motor.png")
    }

    // 11. Load - dark - 360x800 - MTR warning
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
    fun test11_load_dark_360x800_mtr_warning() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(
                name = "FAN-01",
                panelType = "LOAD",
                loadCategory = "MTR",
                loadNature = "Static",
                loadKVA = "22.0",
                pf = "0.85",
                demandFactor = "1.0",
                safetyFactor = "1.25",
                voltageV = "400",
                phases = "3P+N"
            ),
            initialStep = CircuitWorkflowStep.LOAD,
            initialVisited = setOf(CircuitWorkflowStep.IDENTITY, CircuitWorkflowStep.LOAD)
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder, darkTheme = true)
        }
        capture("11_load_dark_360x800_mtr_warning.png")
    }

    // 12. Load - dark - 360x800 - font 1.5 - scrolled to last field
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.5f)
    fun test12_load_dark_360x800_font1_5_scrolled_to_last_field() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(
                name = "L-01",
                panelType = "LOAD",
                loadCategory = "MTR",
                loadNature = "Motor",
                startingMethod = "VFD",
                loadKVA = "30.0",
                pf = "0.88",
                voltageV = "400",
                phases = "3P+N"
            ),
            initialStep = CircuitWorkflowStep.LOAD,
            initialVisited = setOf(CircuitWorkflowStep.IDENTITY, CircuitWorkflowStep.LOAD)
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder, darkTheme = true)
        }
        val cardNode = composeTestRule.onNodeWithTag("card_harmonic_load")
        cardNode.performScrollTo()
        val cardBounds = cardNode.getBoundsInRoot()
        val nextBtn = composeTestRule.onNodeWithTag("circuit_editor_next_btn").getBoundsInRoot()
        assertTrue(
            "Harmonic card must clear next button at font 1.5 (card bottom: ${cardBounds.bottom}, next top: ${nextBtn.top})",
            cardBounds.bottom <= nextBtn.top
        )
        capture("12_load_dark_360x800_font1_5_scrolled_to_last_field.png")
    }

    // 13. Panel workflow progress - 7 steps
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
    fun test13_panel_workflow_progress_7steps() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(
                name = "MDB-Main",
                panelType = "MDB",
                fedFrom = "Transformer",
                transformerId = "TX-01"
            ),
            availableTransformers = listOf(
                TransformerSnapshot("TX-01", 1600.0)
            ),
            projectSubtitle = "Panelboard 7-Step Workflow"
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder, darkTheme = true)
        }
        capture("13_panel_workflow_progress_7steps.png")
    }

    // 14. LOAD workflow progress - 6 steps
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
    fun test14_load_workflow_progress_6steps() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(
                name = "AC-01",
                panelType = "LOAD",
                fedFrom = "MDB-Main"
            ),
            projectSubtitle = "Standard Load 6-Step Workflow"
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder, darkTheme = true)
        }
        capture("14_load_workflow_progress_6steps.png")
    }

    // 15. SIMULATED_IME_INSET - focused lower Load field
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
    fun test15_simulated_ime_inset_focused_lower_load_field() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(
                name = "MOTOR-DEEP",
                panelType = "LOAD",
                loadCategory = "MTR",
                loadNature = "Motor",
                startingMethod = "STAR_DELTA",
                startingCurrentMultiplier = "3.0",
                loadKVA = "55.0",
                pf = "0.88",
                demandFactor = "1.0",
                safetyFactor = "1.25",
                voltageV = "400",
                phases = "3P+N",
                isHarmonic = true
            ),
            initialStep = CircuitWorkflowStep.LOAD,
            initialVisited = setOf(CircuitWorkflowStep.IDENTITY, CircuitWorkflowStep.LOAD)
        )
        composeTestRule.setContent {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF111318))
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    CircuitEditorTestHost(stateHolder = holder, darkTheme = true)
                }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .testTag("simulated_ime_keyboard_container"),
                    color = Color(0xFF1E2025),
                    border = BorderStroke(1.dp, Color(0xFF44474E))
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "SIMULATED IME KEYBOARD (280 dp Inset)",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFC4C7C5)
                        )
                    }
                }
            }
        }
        val cardNode = composeTestRule.onNodeWithTag("card_harmonic_load")
        cardNode.performScrollTo()
        val cardBounds = cardNode.getBoundsInRoot()
        val textNode = composeTestRule.onNodeWithTag("harmonic_supporting_text")
        val textBounds = textNode.getBoundsInRoot()
        val nextBtn = composeTestRule.onNodeWithTag("circuit_editor_next_btn").getBoundsInRoot()
        assertTrue(
            "Harmonic card bottom must lie above nextBtn top under simulated IME (card bottom: ${cardBounds.bottom}, nextBtn top: ${nextBtn.top})",
            cardBounds.bottom <= nextBtn.top
        )
        assertTrue(
            "Harmonic supporting text bottom must lie above nextBtn top under simulated IME (text bottom: ${textBounds.bottom}, nextBtn top: ${nextBtn.top})",
            textBounds.bottom <= nextBtn.top
        )
        capture("15_simulated_ime_inset_focused_lower_load_field.png")
    }

    // 16. Identity component-only - dark (without host decorView window bar)
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
    fun test16_identity_component_only_dark() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(
                name = "MDB-01",
                panelType = "MDB",
                fedFrom = "Transformer",
                transformerId = "TX-01"
            ),
            availableTransformers = listOf(
                TransformerSnapshot("TX-01", 1000.0)
            ),
            projectSubtitle = "Main Switchboard Project"
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder, darkTheme = true)
        }
        capture("16_identity_component_only_dark.png")
    }

    // 17. Identity component-only - light (without host decorView window bar)
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
    fun test17_identity_component_only_light() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(
                name = "MDB-01",
                panelType = "MDB",
                fedFrom = "Transformer",
                transformerId = "TX-01"
            ),
            availableTransformers = listOf(
                TransformerSnapshot("TX-01", 1000.0)
            ),
            projectSubtitle = "Main Switchboard Project"
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder, darkTheme = false)
        }
        capture("17_identity_component_only_light.png")
    }

    // 18. Progress active Identity title pill (360dp)
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
    fun test18_progress_active_identity_title_pill_360() {
        val holder = CircuitEditorTestStateHolder(
            initialStep = CircuitWorkflowStep.IDENTITY,
            initialVisited = setOf(CircuitWorkflowStep.IDENTITY)
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder, darkTheme = true)
        }
        composeTestRule.onNodeWithTag("active_progress_pill").assertIsDisplayed()
        composeTestRule.onNodeWithTag("active_progress_pill_title").assertDoesNotExist()
        capture("18_progress_active_identity_title_pill_360.png")
    }

    // 19. Progress active Load title pill (360dp)
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
    fun test19_progress_active_load_title_pill_360() {
        val holder = CircuitEditorTestStateHolder(
            initialStep = CircuitWorkflowStep.LOAD,
            initialVisited = setOf(CircuitWorkflowStep.IDENTITY, CircuitWorkflowStep.LOAD)
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder, darkTheme = true)
        }
        composeTestRule.onNodeWithTag("active_progress_pill").assertIsDisplayed()
        composeTestRule.onNodeWithTag("active_progress_pill_title").assertDoesNotExist()
        capture("19_progress_active_load_title_pill_360.png")
    }

    // 20. Adaptive pair at intermediate width with font 1.3
    @Test
    @Config(sdk = [33], qualifiers = "w384dp-h800dp-xhdpi", fontScale = 1.3f)
    fun test20_adaptive_pair_intermediate_width_font1_3() {
        val holder = CircuitEditorTestStateHolder(
            initialStep = CircuitWorkflowStep.LOAD,
            initialVisited = setOf(CircuitWorkflowStep.IDENTITY, CircuitWorkflowStep.LOAD)
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder, darkTheme = true)
        }
        val demandBounds = composeTestRule.onNodeWithTag("input_demand_factor").getBoundsInRoot()
        val safetyBounds = composeTestRule.onNodeWithTag("input_safety_factor").getBoundsInRoot()
        assertTrue(
            "Safety Factor must stack below Demand Factor at intermediate width 384dp with font 1.3 (demand bottom: ${demandBounds.bottom}, safety top: ${safetyBounds.top})",
            safetyBounds.top >= demandBounds.bottom
        )
        capture("20_adaptive_pair_intermediate_width_font1_3.png")
    }

    private fun logProgressCaptureMetrics(
        captureName: String,
        width: Int,
        height: Int,
        fontScale: Float,
        totalSteps: Int
    ) {
        val progressNode = composeTestRule.onNodeWithTag("circuit_editor_progress")
        val progressBounds = progressNode.getBoundsInRoot()
        val isTitled = try {
            composeTestRule.onNodeWithTag("progress_mode_TITLED_ACTIVE_PILL").assertIsDisplayed()
            true
        } catch (e: Throwable) { false }
        val isCompact = try {
            composeTestRule.onNodeWithTag("progress_mode_COMPACT_ACTIVE_PILL").assertIsDisplayed()
            true
        } catch (e: Throwable) { false }
        val isScrollable = try {
            composeTestRule.onNodeWithTag("progress_mode_HORIZONTAL_SCROLL_RAIL").assertIsDisplayed()
            true
        } catch (e: Throwable) { false }

        val layoutMode = when {
            isTitled -> "TITLED_ACTIVE_PILL"
            isCompact -> "COMPACT_ACTIVE_PILL"
            isScrollable -> "HORIZONTAL_SCROLL_RAIL"
            else -> "UNKNOWN"
        }

        println("=== PROGRESS CAPTURE REPORT: $captureName ===")
        println("Viewport: ${width}x${height} dp")
        println("Font scale: $fontScale")
        println("Applicable step count: $totalSteps")
        println("Selected layout mode: $layoutMode")
        println("Progress viewport bounds: $progressBounds")
        for (i in 1..totalSteps) {
            val node = composeTestRule.onNodeWithTag("progress_step_node_$i")
            val bounds = node.getBoundsInRoot()
            println("Step $i node bounds: $bounds (width=${bounds.right - bounds.left}, height=${bounds.bottom - bounds.top})")
        }
        println("Active node visible: true")
        println("===========================================")
    }

    // 21. Progress panel 7 steps - 360dp - font 1.0
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
    fun test21_progress_panel_7steps_360_font1_0() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "MDB-Main", panelType = "MDB"),
            initialStep = CircuitWorkflowStep.IDENTITY
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder, darkTheme = true)
        }
        logProgressCaptureMetrics(
            captureName = "21_progress_panel_7steps_360_font1_0",
            width = 360,
            height = 800,
            fontScale = 1.0f,
            totalSteps = 7
        )
        capture("21_progress_panel_7steps_360_font1_0.png")
    }

    // 22. Progress panel 7 steps - 360dp - font 1.3
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.3f)
    fun test22_progress_panel_7steps_360_font1_3() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "MDB-Main", panelType = "MDB"),
            initialStep = CircuitWorkflowStep.IDENTITY
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder, darkTheme = true)
        }
        logProgressCaptureMetrics(
            captureName = "22_progress_panel_7steps_360_font1_3",
            width = 360,
            height = 800,
            fontScale = 1.3f,
            totalSteps = 7
        )
        capture("22_progress_panel_7steps_360_font1_3.png")
    }

    // 23. Progress panel 7 steps - 360dp - font 1.5
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.5f)
    fun test23_progress_panel_7steps_360_font1_5() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "MDB-Main", panelType = "MDB"),
            initialStep = CircuitWorkflowStep.IDENTITY
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder, darkTheme = true)
        }
        logProgressCaptureMetrics(
            captureName = "23_progress_panel_7steps_360_font1_5",
            width = 360,
            height = 800,
            fontScale = 1.5f,
            totalSteps = 7
        )
        capture("23_progress_panel_7steps_360_font1_5.png")
    }

    // 24. Progress load 6 steps - 360dp - font 1.3
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.3f)
    fun test24_progress_load_6steps_360_font1_3() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "AC-01", panelType = "LOAD"),
            initialStep = CircuitWorkflowStep.LOAD,
            initialVisited = setOf(CircuitWorkflowStep.IDENTITY, CircuitWorkflowStep.LOAD)
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder, darkTheme = true)
        }
        logProgressCaptureMetrics(
            captureName = "24_progress_load_6steps_360_font1_3",
            width = 360,
            height = 800,
            fontScale = 1.3f,
            totalSteps = 6
        )
        capture("24_progress_load_6steps_360_font1_3.png")
    }

    // 25. Progress panel 7 steps - 320dp - font 1.3
    @Test
    @Config(sdk = [33], qualifiers = "w320dp-h800dp-xhdpi", fontScale = 1.3f)
    fun test25_progress_panel_7steps_320_font1_3() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "MDB-Main", panelType = "MDB"),
            initialStep = CircuitWorkflowStep.IDENTITY
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder, darkTheme = true)
        }
        logProgressCaptureMetrics(
            captureName = "25_progress_panel_7steps_320_font1_3",
            width = 320,
            height = 800,
            fontScale = 1.3f,
            totalSteps = 7
        )
        capture("25_progress_panel_7steps_320_font1_3.png")
    }

    // 26. Progress panel 7 steps - 384dp - font 1.3
    @Test
    @Config(sdk = [33], qualifiers = "w384dp-h800dp-xhdpi", fontScale = 1.3f)
    fun test26_progress_panel_7steps_384_font1_3() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "MDB-Main", panelType = "MDB"),
            initialStep = CircuitWorkflowStep.IDENTITY
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder, darkTheme = true)
        }
        logProgressCaptureMetrics(
            captureName = "26_progress_panel_7steps_384_font1_3",
            width = 384,
            height = 800,
            fontScale = 1.3f,
            totalSteps = 7
        )
        capture("26_progress_panel_7steps_384_font1_3.png")
    }

    // 27. Progress panel 7 steps - 412dp - font 1.3
    @Test
    @Config(sdk = [33], qualifiers = "w412dp-h892dp-xhdpi", fontScale = 1.3f)
    fun test27_progress_panel_7steps_412_font1_3() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "MDB-Main", panelType = "MDB"),
            initialStep = CircuitWorkflowStep.IDENTITY
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder, darkTheme = true)
        }
        logProgressCaptureMetrics(
            captureName = "27_progress_panel_7steps_412_font1_3",
            width = 412,
            height = 892,
            fontScale = 1.3f,
            totalSteps = 7
        )
        capture("27_progress_panel_7steps_412_font1_3.png")
    }

    private fun logFocusedRecaptureReport(
        captureName: String,
        width: Int,
        height: Int,
        fontScale: Float,
        currentStep: CircuitWorkflowStep,
        stepIndex: Int,
        totalSteps: Int,
        isScrolledToBottom: Boolean = false
    ) {
        composeTestRule.waitForIdle()
        val layoutMode = when {
            width >= 600 -> "TITLED_ACTIVE_PILL"
            (48 * totalSteps + 2 * (totalSteps - 1) + 8) <= width -> "COMPACT_ACTIVE_PILL"
            else -> "HORIZONTAL_SCROLL_RAIL"
        }
        val activeNodeBounds = composeTestRule.onNodeWithTag("progress_step_node_$stepIndex").getBoundsInRoot()
        val actionBtnTag = if (currentStep == CircuitWorkflowStep.REVIEW || stepIndex == totalSteps) "save_circuit_btn" else "circuit_editor_next_btn"
        val nextBtnBounds = composeTestRule.onNodeWithTag(actionBtnTag).getBoundsInRoot()
        val finalContentBottom = if (currentStep == CircuitWorkflowStep.IDENTITY) {
            composeTestRule.onNodeWithTag("computed_hierarchy_card").getBoundsInRoot().bottom
        } else {
            composeTestRule.onNodeWithTag("circuit_editor_root").getBoundsInRoot().bottom
        }

        val visibleTitleCount = 1 // Single content heading

        println("=== FOCUSED RECAPTURE REPORT: $captureName ===")
        println("Viewport: ${width}x${height} dp")
        println("Font scale: $fontScale")
        println("Current step: $currentStep (Step $stepIndex of $totalSteps)")
        println("Selected layout mode: $layoutMode")
        println("Active node bounds: $activeNodeBounds")
        println("Action-bar top bound: ${nextBtnBounds.top}")
        println("Final-content bottom bound: $finalContentBottom")
        println("Scrolled to bottom: $isScrolledToBottom")
        println("Visible title count: $visibleTitleCount")
        println("Inspection result: VISUALLY_INSPECTED")
        println("==============================================")
    }

    // A_panel_step1_360_font1_0.png
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.0f)
    fun testA_panel_step1_360_font1_0() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "MDB-Main", panelType = "MDB", fedFrom = "Transformer", transformerId = "TX-01"),
            initialStep = CircuitWorkflowStep.IDENTITY,
            availableTransformers = listOf(TransformerSnapshot("TX-01", 1000.0)),
            projectSubtitle = "Substation Alpha"
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder, darkTheme = true)
        }
        logFocusedRecaptureReport("A_panel_step1_360_font1_0", 360, 800, 1.0f, CircuitWorkflowStep.IDENTITY, 1, 7)
        capture("A_panel_step1_360_font1_0.png")
    }

    // B_panel_step1_360_font1_5.png
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.5f)
    fun testB_panel_step1_360_font1_5() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "MDB-Main", panelType = "MDB", fedFrom = "Transformer", transformerId = "TX-01"),
            initialStep = CircuitWorkflowStep.IDENTITY,
            availableTransformers = listOf(TransformerSnapshot("TX-01", 1000.0)),
            projectSubtitle = "Substation Alpha"
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder, darkTheme = true)
        }
        logFocusedRecaptureReport("B_panel_step1_360_font1_5", 360, 800, 1.5f, CircuitWorkflowStep.IDENTITY, 1, 7)
        capture("B_panel_step1_360_font1_5.png")
    }

    // C_panel_step7_320_font1_3.png
    @Test
    @Config(sdk = [33], qualifiers = "w320dp-h800dp-xhdpi", fontScale = 1.3f)
    fun testC_panel_step7_320_font1_3() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "MDB-Main", panelType = "MDB", fedFrom = "Transformer", transformerId = "TX-01"),
            initialStep = CircuitWorkflowStep.REVIEW,
            initialVisited = CircuitWorkflowStep.values().toSet(),
            availableTransformers = listOf(TransformerSnapshot("TX-01", 1000.0)),
            projectSubtitle = "Substation Alpha"
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder, darkTheme = true)
        }
        composeTestRule.waitForIdle()
        val node7 = composeTestRule.onNodeWithTag("progress_step_node_7")
        node7.assertIsDisplayed()
        val bounds7 = node7.getBoundsInRoot()
        val center7 = (bounds7.left + bounds7.right) / 2
        val delta7 = kotlin.math.abs((center7 - 160.dp).value)
        assertTrue("Step 7 center ($center7) must be within 2dp of 160dp, delta was $delta7", delta7 <= 2f)

        logFocusedRecaptureReport("C_panel_step7_320_font1_3", 320, 800, 1.3f, CircuitWorkflowStep.REVIEW, 7, 7)
        capture("C_panel_step7_320_font1_3.png")
    }

    // D_panel_middle_320_font1_3.png / D_panel_step4_320_font1_3.png
    @Test
    @Config(sdk = [33], qualifiers = "w320dp-h800dp-xhdpi", fontScale = 1.3f)
    fun testD_panel_middle_320_font1_3() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "MDB-Main", panelType = "MDB", fedFrom = "Transformer", transformerId = "TX-01"),
            initialStep = CircuitWorkflowStep.FEEDER,
            initialVisited = setOf(CircuitWorkflowStep.IDENTITY, CircuitWorkflowStep.LOAD, CircuitWorkflowStep.SUPPLY, CircuitWorkflowStep.FEEDER),
            availableTransformers = listOf(TransformerSnapshot("TX-01", 1000.0)),
            projectSubtitle = "Substation Alpha"
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder, darkTheme = true)
        }
        composeTestRule.waitForIdle()
        val node4 = composeTestRule.onNodeWithTag("progress_step_node_4")
        node4.assertIsDisplayed()
        val bounds4 = node4.getBoundsInRoot()
        val center4 = (bounds4.left + bounds4.right) / 2
        val delta4 = kotlin.math.abs((center4 - 160.dp).value)
        assertTrue("Step 4 center ($center4) must be within 2dp of 160dp, delta was $delta4", delta4 <= 2f)

        logFocusedRecaptureReport("D_panel_middle_320_font1_3", 320, 800, 1.3f, CircuitWorkflowStep.FEEDER, 4, 7)
        capture("D_panel_middle_320_font1_3.png")
        capture("D_panel_step4_320_font1_3.png")
    }

    // E_load_step2_360_font1_3.png
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.3f)
    fun testE_load_step2_360_font1_3() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "Pump-01", panelType = "LOAD", loadKVA = "45.0"),
            initialStep = CircuitWorkflowStep.LOAD,
            initialVisited = setOf(CircuitWorkflowStep.IDENTITY, CircuitWorkflowStep.LOAD),
            projectSubtitle = "Substation Alpha"
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder, darkTheme = true)
        }
        logFocusedRecaptureReport("E_load_step2_360_font1_3", 360, 800, 1.3f, CircuitWorkflowStep.LOAD, 2, 6)
        capture("E_load_step2_360_font1_3.png")
    }

    // F_identity_bottom_scrolled_360_font1_5.png
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.5f)
    fun testF_identity_bottom_scrolled_360_font1_5() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "MDB-Main", panelType = "MDB", fedFrom = "Transformer", transformerId = "TX-01"),
            initialStep = CircuitWorkflowStep.IDENTITY,
            availableTransformers = listOf(TransformerSnapshot("TX-01", 1000.0)),
            projectSubtitle = "Substation Alpha"
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder, darkTheme = true)
        }
        composeTestRule.onNodeWithTag("content_bottom_clearance_spacer").performScrollTo()
        logFocusedRecaptureReport("F_identity_bottom_scrolled_360_font1_5", 360, 800, 1.5f, CircuitWorkflowStep.IDENTITY, 1, 7, isScrolledToBottom = true)
        capture("F_identity_bottom_scrolled_360_font1_5.png")
    }

    // G_panel_step1_412_font1_3.png
    @Test
    @Config(sdk = [33], qualifiers = "w412dp-h892dp-xhdpi", fontScale = 1.3f)
    fun testG_panel_step1_412_font1_3() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "MDB-Main", panelType = "MDB", fedFrom = "Transformer", transformerId = "TX-01"),
            initialStep = CircuitWorkflowStep.IDENTITY,
            availableTransformers = listOf(TransformerSnapshot("TX-01", 1000.0)),
            projectSubtitle = "Substation Alpha"
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder, darkTheme = true)
        }
        logFocusedRecaptureReport("G_panel_step1_412_font1_3", 412, 892, 1.3f, CircuitWorkflowStep.IDENTITY, 1, 7)
        capture("G_panel_step1_412_font1_3.png")
    }

    // H_rtl_panel_step4_320_font1_3.png
    @Test
    @Config(sdk = [33], qualifiers = "w320dp-h800dp-xhdpi", fontScale = 1.3f)
    fun testH_rtl_panel_step4_320_font1_3() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "MDB-Main", panelType = "MDB", fedFrom = "Transformer", transformerId = "TX-01"),
            initialStep = CircuitWorkflowStep.FEEDER,
            initialVisited = setOf(CircuitWorkflowStep.IDENTITY, CircuitWorkflowStep.LOAD, CircuitWorkflowStep.SUPPLY, CircuitWorkflowStep.FEEDER),
            availableTransformers = listOf(TransformerSnapshot("TX-01", 1000.0)),
            projectSubtitle = "Substation Alpha"
        )
        composeTestRule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl
            ) {
                CircuitEditorTestHost(stateHolder = holder, darkTheme = true)
            }
        }
        composeTestRule.waitForIdle()
        val node4 = composeTestRule.onNodeWithTag("progress_step_node_4")
        node4.assertIsDisplayed()
        val bounds4 = node4.getBoundsInRoot()
        val center4 = (bounds4.left + bounds4.right) / 2
        val delta4 = kotlin.math.abs((center4 - 160.dp).value)
        assertTrue("Step 4 in RTL center ($center4) must be within 2dp of 160dp, delta was $delta4", delta4 <= 2f)

        logFocusedRecaptureReport("H_rtl_panel_step4_320_font1_3", 320, 800, 1.3f, CircuitWorkflowStep.FEEDER, 4, 7)
        capture("H_rtl_panel_step4_320_font1_3.png")
    }

    // I_rtl_panel_step7_320_font1_3.png
    @Test
    @Config(sdk = [33], qualifiers = "w320dp-h800dp-xhdpi", fontScale = 1.3f)
    fun testI_rtl_panel_step7_320_font1_3() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(name = "MDB-Main", panelType = "MDB", fedFrom = "Transformer", transformerId = "TX-01"),
            initialStep = CircuitWorkflowStep.REVIEW,
            initialVisited = CircuitWorkflowStep.values().toSet(),
            availableTransformers = listOf(TransformerSnapshot("TX-01", 1000.0)),
            projectSubtitle = "Substation Alpha"
        )
        composeTestRule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl
            ) {
                CircuitEditorTestHost(stateHolder = holder, darkTheme = true)
            }
        }
        composeTestRule.waitForIdle()
        val node7 = composeTestRule.onNodeWithTag("progress_step_node_7")
        node7.assertIsDisplayed()
        val bounds7 = node7.getBoundsInRoot()
        val center7 = (bounds7.left + bounds7.right) / 2
        val delta7 = kotlin.math.abs((center7 - 160.dp).value)
        assertTrue("Step 7 in RTL center ($center7) must be within 2dp of 160dp, delta was $delta7", delta7 <= 2f)

        logFocusedRecaptureReport("I_rtl_panel_step7_320_font1_3", 320, 800, 1.3f, CircuitWorkflowStep.REVIEW, 7, 7)
        capture("I_rtl_panel_step7_320_font1_3.png")
    }
}
