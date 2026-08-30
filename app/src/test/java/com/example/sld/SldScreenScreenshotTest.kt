package com.example.sld

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import com.example.data.AtsEntity
import com.example.data.CircuitEntity
import com.example.data.TransformerEntity
import com.example.engine.AtsResult
import com.example.engine.GeneratorResult
import com.example.engine.TransformerResult
import com.example.ui.theme.VoltAtlasTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel6)
class SldScreenScreenshotTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val transformers = listOf(
        TransformerEntity(
            projectId = "proj_1",
            transformerId = "TX-01",
            kVA = 1000.0,
            voltage = 400
        )
    )
    private val transformerResults = mapOf(
        "TX-01" to TransformerResult(
            connectedLoad = 800.0,
            circuitsAssigned = 5,
            realPower = 800.0,
            demandLoad = 600.0,
            diversifiedLoad = 600.0,
            futureLoad = 0.0,
            designLoad = 600.0,
            requiredKVA = 600.0,
            recommendedSize = 1000.0,
            selectedSize = 1000.0,
            loadingPct = 60.0,
            flc = 1443.37,
            zt = 5.0,
            rt = 0.0,
            xt = 0.0,
            isc3Phase = 28.87,
            isc1Phase = 0.0,
            status = "PASS",
            z0 = 0.0,
            vectorGroup = "Dyn11"
        )
    )
    private val atsEntity = AtsEntity(projectId = "proj_1", systemVoltage = 400)
    private val atsResult = AtsResult(finalRating = 1600.0, governingCurrent = 1443.0, status = "PASS")
    private val genResult = GeneratorResult(finalSize = 1000.0, loadingPct = 50.0, status = "PASS")
    private val circuits = listOf(
        CircuitEntity(id = 1, projectId = "proj_1", circuitName = "MDB-01", fedFrom = "Transformer", atsIncluded = true, generatorIncluded = true),
        CircuitEntity(id = 2, projectId = "proj_1", circuitName = "SMDB-01", fedFrom = "MDB-01")
    )

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun captureSldDarkMode() {
        val outputDir = File("build/outputs/roborazzi")
        if (!outputDir.exists()) outputDir.mkdirs()

        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                com.example.ui.SldScreen(
                    circuits = circuits,
                    transformers = transformers,
                    generatorResult = genResult,
                    atsEntity = atsEntity,
                    atsResult = atsResult,
                    transformerResults = transformerResults
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("sld_screen_container").captureRoboImage(file = File(outputDir, "sld_dark_mode.png"))
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun captureSldLightMode() {
        val outputDir = File("build/outputs/roborazzi")
        if (!outputDir.exists()) outputDir.mkdirs()

        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = false) {
                com.example.ui.SldScreen(
                    circuits = circuits,
                    transformers = transformers,
                    generatorResult = genResult,
                    atsEntity = atsEntity,
                    atsResult = atsResult,
                    transformerResults = transformerResults
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("sld_screen_container").captureRoboImage(file = File(outputDir, "sld_light_mode.png"))
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun captureSymbolGalleryDark() {
        val outputDir = File("build/outputs/roborazzi")
        if (!outputDir.exists()) outputDir.mkdirs()

        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                SldSymbolGallery()
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("symbol_gallery_container").captureRoboImage(file = File(outputDir, "symbol_gallery_dark.png"))
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun captureSymbolGalleryLight() {
        val outputDir = File("build/outputs/roborazzi")
        if (!outputDir.exists()) outputDir.mkdirs()

        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = false) {
                SldSymbolGallery()
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("symbol_gallery_container").captureRoboImage(file = File(outputDir, "symbol_gallery_light.png"))
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun captureToolbarFeatures() {
        val outputDir = File("build/outputs/roborazzi")
        if (!outputDir.exists()) outputDir.mkdirs()

        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                com.example.ui.SldScreen(
                    circuits = circuits,
                    transformers = transformers,
                    generatorResult = genResult,
                    atsEntity = atsEntity,
                    atsResult = atsResult,
                    transformerResults = transformerResults
                )
            }
        }
        composeTestRule.waitForIdle()

        // Test Mode Dropdown
        composeTestRule.onNodeWithTag("sld_mode_dropdown_trigger").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("sld_screen_container").captureRoboImage(file = File(outputDir, "sld_mode_dropdown.png"))

        // Select "Voltage Drop" mode from dropdown
        composeTestRule.onNodeWithText("Voltage Drop").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("sld_screen_container").captureRoboImage(file = File(outputDir, "sld_voltage_drop_mode.png"))

        // Test Validation Toggle
        composeTestRule.onNodeWithTag("sld_validation_toggle_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("sld_screen_container").captureRoboImage(file = File(outputDir, "sld_validation_panel.png"))

        // Test Fit Button
        composeTestRule.onNodeWithTag("sld_fit_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("sld_screen_container").captureRoboImage(file = File(outputDir, "sld_fit_executed.png"))
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun captureScenario1_SingleAts() {
        val outputDir = File("build/outputs/roborazzi")
        if (!outputDir.exists()) outputDir.mkdirs()

        val singleAtsList = listOf(
            AtsEntity(projectId = "proj_1", atsId = "ATS-01", atsName = "ATS-Main", normalSourceId = "TX-01", emergencySourceId = "GEN-01")
        )
        val singleGenList = listOf(
            com.example.data.GeneratorEntity(projectId = "proj_1", generatorId = "GEN-01", generatorName = "Standby Gen", manualSizeKVA = 800.0)
        )
        val s1Circuits = listOf(
            CircuitEntity(id = 1, projectId = "proj_1", circuitName = "MDB-Main", fedFrom = "Transformer", transformerId = "TX-01", panelType = "MDB"),
            CircuitEntity(id = 2, projectId = "proj_1", circuitName = "SMDB-1", fedFrom = "MDB-Main", transformerId = "TX-01", panelType = "SMDB"),
            CircuitEntity(id = 3, projectId = "proj_1", circuitName = "DB-Power", fedFrom = "MDB-Main", transformerId = "TX-01", panelType = "DB")
        )

        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                com.example.ui.SldScreen(
                    circuits = s1Circuits,
                    transformers = transformers,
                    generators = singleGenList,
                    atsList = singleAtsList,
                    transformerResults = transformerResults
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("sld_screen_container").captureRoboImage(file = File(outputDir, "scenario_1_single_ats.png"))
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun captureScenario2_MultiAtsMultiGenerator() {
        val outputDir = File("build/outputs/roborazzi")
        if (!outputDir.exists()) outputDir.mkdirs()

        val multiAtsList = listOf(
            AtsEntity(projectId = "proj_1", atsId = "ATS-01", atsName = "ATS-Main", normalSourceId = "TX-01", emergencySourceId = "GEN-01"),
            AtsEntity(projectId = "proj_1", atsId = "ATS-02", atsName = "ATS-Essential", normalSourceId = "TX-01", emergencySourceId = "GEN-02")
        )
        val multiGenList = listOf(
            com.example.data.GeneratorEntity(projectId = "proj_1", generatorId = "GEN-01", generatorName = "Gen 1", manualSizeKVA = 500.0),
            com.example.data.GeneratorEntity(projectId = "proj_1", generatorId = "GEN-02", generatorName = "Gen 2", manualSizeKVA = 500.0)
        )
        val s2Circuits = listOf(
            CircuitEntity(id = 1, projectId = "proj_1", circuitName = "MDB-Main", fedFrom = "Transformer", transformerId = "TX-01", panelType = "MDB"),
            CircuitEntity(id = 2, projectId = "proj_1", circuitName = "Essential-DB", fedFrom = "ATS-Essential", assignedAtsId = "ATS-02", panelType = "DB")
        )

        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                com.example.ui.SldScreen(
                    circuits = s2Circuits,
                    transformers = transformers,
                    generators = multiGenList,
                    atsList = multiAtsList,
                    transformerResults = transformerResults
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("sld_screen_container").captureRoboImage(file = File(outputDir, "scenario_2_multi_ats_generator.png"))
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun captureScenario3_BuswayCircuitPresent() {
        val outputDir = File("build/outputs/roborazzi")
        if (!outputDir.exists()) outputDir.mkdirs()

        val s3Circuits = listOf(
            CircuitEntity(id = 1, projectId = "proj_1", circuitName = "MDB-Main", fedFrom = "Transformer", transformerId = "TX-01", panelType = "MDB"),
            CircuitEntity(id = 2, projectId = "proj_1", circuitName = "Riser-Busway-1", fedFrom = "MDB-Main", transformerId = "TX-01", overrideCableSize = "800A Busway Trunk", panelType = "SMDB"),
            CircuitEntity(id = 3, projectId = "proj_1", circuitName = "DB-Level-5", fedFrom = "Riser-Busway-1", transformerId = "TX-01", panelType = "DB")
        )

        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                com.example.ui.SldScreen(
                    circuits = s3Circuits,
                    transformers = transformers,
                    transformerResults = transformerResults
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("sld_screen_container").captureRoboImage(file = File(outputDir, "scenario_3_busway_present.png"))
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun captureScenario4_WideBusbarHighFeederCount() {
        val outputDir = File("build/outputs/roborazzi")
        if (!outputDir.exists()) outputDir.mkdirs()

        val s4Circuits = listOf(
            CircuitEntity(id = 1, projectId = "proj_1", circuitName = "MDB-Main", fedFrom = "Transformer", transformerId = "TX-01", panelType = "MDB"),
            CircuitEntity(id = 10, projectId = "proj_1", circuitName = "SMDB-North", fedFrom = "MDB-Main", transformerId = "TX-01", panelType = "SMDB"),
            CircuitEntity(id = 20, projectId = "proj_1", circuitName = "SMDB-South", fedFrom = "MDB-Main", transformerId = "TX-01", panelType = "SMDB"),
            CircuitEntity(id = 30, projectId = "proj_1", circuitName = "DB-HVAC", fedFrom = "MDB-Main", transformerId = "TX-01", panelType = "DB"),
            CircuitEntity(id = 40, projectId = "proj_1", circuitName = "DB-Elevators", fedFrom = "MDB-Main", transformerId = "TX-01", panelType = "DB"),
            CircuitEntity(id = 50, projectId = "proj_1", circuitName = "Chiller-Motor", fedFrom = "MDB-Main", transformerId = "TX-01", loadType = "MOTOR")
        )

        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                com.example.ui.SldScreen(
                    circuits = s4Circuits,
                    transformers = transformers,
                    transformerResults = transformerResults
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("sld_screen_container").captureRoboImage(file = File(outputDir, "scenario_4_wide_busbar_high_feeder.png"))
    }
}

