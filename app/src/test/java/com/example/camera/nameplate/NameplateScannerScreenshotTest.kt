package com.example.camera.nameplate

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.ui.theme.VoltAtlasTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Roborazzi screenshot test suite for Equipment Nameplate Scanner UI.
 * Verifies visual presentation across viewfinder reticle, detected OCR specs review,
 * and dark/light modes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NameplateScannerScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val outputDir = File("build/outputs/roborazzi").apply { mkdirs() }

    @Test
    @Config(qualifiers = "w380dp-h850dp-xhdpi")
    fun testNameplateScanner_viewfinder_portrait_dark() {
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    EquipmentNameplateScannerContent(
                        scanResult = null,
                        onClose = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(File(outputDir, "NameplateScanner_Viewfinder_Portrait_Dark.png").path)
    }

    @Test
    @Config(qualifiers = "w380dp-h850dp-xhdpi")
    fun testNameplateScanner_motorSpecsDetected_portrait_dark() {
        val detectedMotor = NameplateScanResult(
            equipmentType = NameplateEquipmentType.MOTOR,
            powerKw = 37.0,
            voltageV = 400.0,
            currentAmps = 68.5,
            powerFactor = 0.86,
            efficiencyPct = 92.4,
            rpm = 1475,
            frequencyHz = 50.0,
            confidenceScore = 0.95f,
            rawMatchedLines = listOf("3~ MOTOR 37kW 400V 68.5A", "cos φ 0.86 92.4% 1475RPM")
        )

        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    EquipmentNameplateScannerContent(
                        scanResult = detectedMotor,
                        onClose = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(File(outputDir, "NameplateScanner_MotorSpecs_Portrait_Dark.png").path)
    }

    @Test
    @Config(qualifiers = "w380dp-h850dp-xhdpi")
    fun testNameplateScanner_transformerSpecsDetected_portrait_light() {
        val detectedTx = NameplateScanResult(
            equipmentType = NameplateEquipmentType.TRANSFORMER,
            kvaRating = 1000.0,
            voltageV = 400.0,
            currentAmps = 1443.0,
            impedancePercentZ = 5.0,
            frequencyHz = 50.0,
            confidenceScore = 0.92f,
            rawMatchedLines = listOf("DISTRIBUTION TRANSFORMER 1000 kVA", "400V 1443A Uk 5.0%")
        )

        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    EquipmentNameplateScannerContent(
                        scanResult = detectedTx,
                        onClose = {}
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(File(outputDir, "NameplateScanner_TransformerSpecs_Portrait_Light.png").path)
    }
}
