package com.example.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.data.GeneratorEntity
import com.example.ui.theme.VoltAtlasTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File


@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xhdpi")
class PowerSystemsZeroStateScreenshotTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val outputDir = File("build/outputs/roborazzi")

    @Before
    fun setUp() {
        if (!outputDir.exists()) outputDir.mkdirs()
    }

    private fun captureAndSave(name: String) {
        composeTestRule.waitForIdle()
        val decorView = composeTestRule.activity.window.decorView
        val width = maxOf(decorView.width, 1)
        val height = maxOf(decorView.height, 1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        decorView.draw(canvas)

        val latestDialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
        latestDialog?.window?.decorView?.let { dialogView ->
            dialogView.draw(canvas)
        }

        val file = File(outputDir, name)
        file.parentFile?.mkdirs()
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
        }
    }

    private fun stateWithGens(gens: List<GeneratorEntity>) = UiState.Success(
        circuits = emptyList(),
        transformers = emptyList(),
        sizingResults = emptyMap(),
        transformerResults = emptyMap(),
        boq = emptyList(),
        drums = emptyList(),
        procurement = emptyList(),
        panels = emptyList(),
        generators = gens,
        atsList = emptyList(),
        upsList = emptyList(),
        pfCorrectionResults = emptyList()
    )

    @Test
    fun capture_1_singleInstanceRemaining_deleteButtonVisible() {
        val singleGen = listOf(GeneratorEntity(generatorId = "GEN-01", generatorName = "Generator 1"))
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PowerSystemsScreen(
                        state = stateWithGens(singleGen)
                    )
                }
            }
        }
        captureAndSave("delete_to_zero_1_single_remaining.png")
    }

    @Test
    fun capture_2_deleteConfirmationDialog() {
        val singleGen = listOf(GeneratorEntity(generatorId = "GEN-01", generatorName = "Generator 1"))
        val confirmation = EquipmentDeleteConfirmation(
            equipmentType = "Generator",
            equipmentId = "GEN-01",
            equipmentName = "Generator 1",
            affectedCircuits = listOf("Circuit 1 (Emergency)"),
            affectedSourceLinks = listOf("ATS-01 loses emergency source link"),
            groupDissolution = null
        )
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PowerSystemsScreen(
                        state = stateWithGens(singleGen),
                        pendingDeleteConfirmation = confirmation
                    )
                }
            }
        }
        captureAndSave("delete_to_zero_2_confirm_dialog.png")
    }

    @Test
    fun capture_3_emptyStateAfterDeleteToZero() {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PowerSystemsScreen(
                        state = stateWithGens(emptyList())
                    )
                }
            }
        }
        captureAndSave("delete_to_zero_3_empty_state.png")
    }

    @Test
    fun capture_4_reAddedNewInstanceFresh() {
        val newGen = listOf(GeneratorEntity(generatorId = "GEN-01", generatorName = "Generator 1"))
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PowerSystemsScreen(
                        state = stateWithGens(newGen)
                    )
                }
            }
        }
        captureAndSave("delete_to_zero_4_readded_clean.png")
    }
}
