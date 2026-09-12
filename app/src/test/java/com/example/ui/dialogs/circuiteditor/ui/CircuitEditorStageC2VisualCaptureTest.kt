package com.example.ui.dialogs.circuiteditor.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.example.data.CircuitEntity
import com.example.ui.dialogs.circuiteditor.model.CircuitEditorDraft
import com.example.ui.dialogs.circuiteditor.model.CircuitWorkflowStep
import com.example.ui.dialogs.circuiteditor.model.TransformerSnapshot
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
        val decorView = composeTestRule.activity.window.decorView
        val bitmap = android.graphics.Bitmap.createBitmap(
            maxOf(decorView.width, 1),
            maxOf(decorView.height, 1),
            android.graphics.Bitmap.Config.ARGB_8888
        )
        decorView.draw(android.graphics.Canvas(bitmap))
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

    // 3. Identity - dark - 360x800 - font 1.3
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.3f)
    fun test03_identity_dark_360x800_font1_3() {
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
        capture("03_identity_dark_360x800_font1_3.png")
    }

    // 4. Identity - dark - 360x800 - font 1.5
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.5f)
    fun test04_identity_dark_360x800_font1_5() {
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
        capture("04_identity_dark_360x800_font1_5.png")
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

    // 6. Identity - validation error
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
    fun test06_identity_validation_error() {
        val holder = CircuitEditorTestStateHolder(
            initialDraft = CircuitEditorDraft(
                name = "",
                panelType = "MDB",
                fedFrom = "Transformer"
            )
        )
        composeTestRule.setContent {
            CircuitEditorTestHost(stateHolder = holder, darkTheme = true)
        }
        composeTestRule.onNodeWithTag("circuit_editor_next_btn").performClick()
        capture("06_identity_validation_error.png")
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

    // 8. Load - dark - 360x800 - Static
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi")
    fun test08_load_dark_360x800_static() {
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
        capture("08_load_dark_360x800_static.png")
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

    // 12. Load - dark - 360x800 - font 1.5
    @Test
    @Config(sdk = [33], qualifiers = "w360dp-h800dp-xhdpi", fontScale = 1.5f)
    fun test12_load_dark_360x800_font1_5() {
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
        capture("12_load_dark_360x800_font1_5.png")
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
    fun test15_simulated_ime_inset_lower_load_field() {
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
            CircuitEditorTestHost(
                stateHolder = holder,
                darkTheme = true,
                modifier = Modifier.padding(bottom = 280.dp)
            )
        }
        composeTestRule.onNodeWithTag("checkbox_is_harmonic").performScrollTo()
        capture("15_simulated_ime_inset_lower_load_field.png")
    }
}
