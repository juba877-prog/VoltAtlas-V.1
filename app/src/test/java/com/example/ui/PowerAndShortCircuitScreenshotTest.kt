package com.example.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.data.CircuitEntity
import com.example.engine.BoqItem
import com.example.engine.ProcurementItem
import com.example.engine.ShortCircuitResult
import com.example.ui.screens.BoqProcurementScreen
import com.example.ui.theme.VoltAtlasTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PowerAndShortCircuitScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val outputDir = File("build/outputs/roborazzi")

    private fun mockUiState(
        circuits: List<CircuitEntity> = listOf(
            CircuitEntity(id = 1, circuitName = "DP-G3"),
            CircuitEntity(id = 2, circuitName = "UPP-G3-1 (WARN)"),
            CircuitEntity(id = 3, circuitName = "UPP-G3-2"),
            CircuitEntity(id = 4, circuitName = "HVAC-PANEL-FAIL")
        ),
        scResults: Map<Int, ShortCircuitResult> = mapOf(
            1 to ShortCircuitResult(
                circuitId = 1,
                sourceName = "TX-01",
                cableMm2 = "70 mm²",
                iscKA = 2.16,
                iwKA = 31.65,
                deviceKA = 36.0,
                deviceCheck = "PASS",
                cableCheck = "PASS",
                marginPct = 1365.3,
                status = "PASS",
                note = "Breaking capacity assumed (not from catalog) — verify device rating."
            ),
            2 to ShortCircuitResult(
                circuitId = 2,
                sourceName = "TX-01",
                cableMm2 = "10 mm²",
                iscKA = 4.30,
                iwKA = 4.52,
                deviceKA = 36.0,
                deviceCheck = "WARNING",
                cableCheck = "PASS",
                marginPct = 5.1,
                status = "WARNING",
                note = "Cable thermal withstand margin is below 10%."
            ),
            3 to ShortCircuitResult(
                circuitId = 3,
                sourceName = "TX-01",
                cableMm2 = "10 mm²",
                iscKA = 1.28,
                iwKA = 4.52,
                deviceKA = 36.0,
                deviceCheck = "PASS",
                cableCheck = "PASS",
                marginPct = 253.1,
                status = "PASS",
                note = ""
            ),
            4 to ShortCircuitResult(
                circuitId = 4,
                sourceName = "GEN-01",
                cableMm2 = "4 mm²",
                iscKA = 18.50,
                iwKA = 12.00,
                deviceKA = 15.0,
                deviceCheck = "FAIL",
                cableCheck = "FAIL",
                marginPct = -35.1,
                status = "FAIL",
                note = "Isc exceeds device rated breaking capacity (Icu)."
            )
        ),
        boq: List<BoqItem> = listOf(
            BoqItem(
                cableType = "Cu / XLPE / PVC / SWA",
                size = "4x70 mm²",
                productCode = "CAB-CU-70",
                manufacturer = "Elsewedy",
                totalLength_m = 120.0,
                unitWeight_g_m = 3200.0,
                totalWeight_kg = 850.0,
                runsCount = 1,
                status = "OK"
            ),
            BoqItem(
                cableType = "Cu / PVC / PVC",
                size = "4x10 mm²",
                productCode = "CAB-CU-10",
                manufacturer = "Elsewedy",
                totalLength_m = 105.0,
                unitWeight_g_m = 650.0,
                totalWeight_kg = 210.0,
                runsCount = 2,
                status = "OK"
            )
        ),
        procurement: List<ProcurementItem> = listOf(
            ProcurementItem(
                circuitName = "DP-G3",
                cableType = "Cu / XLPE / PVC / SWA",
                cableSize = "4x70 mm²",
                glandModel = "CMP-CW32",
                glandDesc = "Brass Outdoor Armoured",
                lugsType = "Cu 70 mm² M10",
                cableOD_mm = 34.0,
                requiredTrayWidth_mm = 150.0,
                status = "OK"
            )
        )
    ): UiState.Success {
        return UiState.Success(
            circuits = circuits,
            transformers = emptyList(),
            sizingResults = emptyMap(),
            transformerResults = emptyMap(),
            boq = boq,
            drums = emptyList(),
            procurement = procurement,
            panels = emptyList(),
            generatorResult = null,
            atsResult = null,
            upsResult = null,
            pfCorrectionResults = emptyList(),
            shortCircuitResults = scResults
        )
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun boqProcurementScreen_topBar_narrowPortrait_dark() {
        if (!outputDir.exists()) outputDir.mkdirs()
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BoqProcurementScreen(
                        state = mockUiState()
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(File(outputDir, "BoqProcurementScreen_TopBar_NarrowPortrait_Dark.png").path)
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun boqProcurementScreen_topBar_narrowPortrait_light() {
        if (!outputDir.exists()) outputDir.mkdirs()
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BoqProcurementScreen(
                        state = mockUiState()
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(File(outputDir, "BoqProcurementScreen_TopBar_NarrowPortrait_Light.png").path)
    }

    @Test
    @Config(qualifiers = "w800dp-h360dp-xhdpi")
    fun boqProcurementScreen_topBar_landscape_dark() {
        if (!outputDir.exists()) outputDir.mkdirs()
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BoqProcurementScreen(
                        state = mockUiState()
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(File(outputDir, "BoqProcurementScreen_TopBar_Landscape_Dark.png").path)
    }

    @Test
    @Config(qualifiers = "w800dp-h360dp-xhdpi")
    fun boqProcurementScreen_topBar_landscape_light() {
        if (!outputDir.exists()) outputDir.mkdirs()
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BoqProcurementScreen(
                        state = mockUiState()
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(File(outputDir, "BoqProcurementScreen_TopBar_Landscape_Light.png").path)
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun protectionScreen_topBar_narrowPortrait_dark() {
        if (!outputDir.exists()) outputDir.mkdirs()
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ProtectionScreen(
                        state = mockUiState()
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(File(outputDir, "ProtectionScreen_TopBar_NarrowPortrait_Dark.png").path)
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-xhdpi")
    fun protectionScreen_topBar_narrowPortrait_light() {
        if (!outputDir.exists()) outputDir.mkdirs()
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ProtectionScreen(
                        state = mockUiState()
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(File(outputDir, "ProtectionScreen_TopBar_NarrowPortrait_Light.png").path)
    }

    @Test
    @Config(qualifiers = "w800dp-h360dp-xhdpi")
    fun protectionScreen_topBar_landscape_dark() {
        if (!outputDir.exists()) outputDir.mkdirs()
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ProtectionScreen(
                        state = mockUiState()
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(File(outputDir, "ProtectionScreen_TopBar_Landscape_Dark.png").path)
    }

    @Test
    @Config(qualifiers = "w800dp-h360dp-xhdpi")
    fun protectionScreen_topBar_landscape_light() {
        if (!outputDir.exists()) outputDir.mkdirs()
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ProtectionScreen(
                        state = mockUiState()
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(File(outputDir, "ProtectionScreen_TopBar_Landscape_Light.png").path)
    }

    @Test
    @Config(qualifiers = "w380dp-h820dp-xhdpi")
    fun powerScreen_topBar_portrait_dark() {
        if (!outputDir.exists()) outputDir.mkdirs()
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PowerSystemsScreen(
                        state = mockUiState()
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(File(outputDir, "PowerScreen_TopBar_Portrait_Dark.png").path)
    }

    @Test
    @Config(qualifiers = "w380dp-h820dp-xhdpi")
    fun powerScreen_topBar_portrait_light() {
        if (!outputDir.exists()) outputDir.mkdirs()
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PowerSystemsScreen(
                        state = mockUiState()
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(File(outputDir, "PowerScreen_TopBar_Portrait_Light.png").path)
    }

    @Test
    @Config(qualifiers = "w820dp-h380dp-xhdpi")
    fun powerScreen_topBar_landscape_dark() {
        if (!outputDir.exists()) outputDir.mkdirs()
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PowerSystemsScreen(
                        state = mockUiState()
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(File(outputDir, "PowerScreen_TopBar_Landscape_Dark.png").path)
    }

    @Test
    @Config(qualifiers = "w820dp-h380dp-xhdpi")
    fun powerScreen_topBar_landscape_light() {
        if (!outputDir.exists()) outputDir.mkdirs()
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PowerSystemsScreen(
                        state = mockUiState()
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(File(outputDir, "PowerScreen_TopBar_Landscape_Light.png").path)
    }

    @Test
    @Config(qualifiers = "w380dp-h1000dp-xhdpi")
    fun shortCircuit_cards_portrait_dark() {
        if (!outputDir.exists()) outputDir.mkdirs()
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ShortCircuitPanel(
                        state = mockUiState()
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(File(outputDir, "ShortCircuit_Cards_Portrait_Dark.png").path)
    }

    @Test
    @Config(qualifiers = "w380dp-h1000dp-xhdpi")
    fun shortCircuit_cards_portrait_light() {
        if (!outputDir.exists()) outputDir.mkdirs()
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ShortCircuitPanel(
                        state = mockUiState()
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(File(outputDir, "ShortCircuit_Cards_Portrait_Light.png").path)
    }

    @Test
    @Config(qualifiers = "w820dp-h380dp-xhdpi")
    fun shortCircuit_cards_landscape_dark() {
        if (!outputDir.exists()) outputDir.mkdirs()
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ShortCircuitPanel(
                        state = mockUiState()
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(File(outputDir, "ShortCircuit_Cards_Landscape_Dark.png").path)
    }

    @Test
    @Config(qualifiers = "w820dp-h380dp-xhdpi")
    fun shortCircuit_cards_landscape_light() {
        if (!outputDir.exists()) outputDir.mkdirs()
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ShortCircuitPanel(
                        state = mockUiState()
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(File(outputDir, "ShortCircuit_Cards_Landscape_Light.png").path)
    }

    @Test
    @Config(qualifiers = "w380dp-h900dp-xhdpi")
    fun shortCircuit_cards_renamedFixture_generalizability() {
        if (!outputDir.exists()) outputDir.mkdirs()
        val customCircuits = listOf(
            CircuitEntity(id = 101, circuitName = "Main Substation Feeder 1"),
            CircuitEntity(id = 102, circuitName = "HVAC Chiller Bus 2"),
            CircuitEntity(id = 103, circuitName = "Emergency Core Panel")
        )
        val customScResults = mapOf(
            101 to ShortCircuitResult(
                circuitId = 101,
                sourceName = "GRID-SUB-01",
                cableMm2 = "300 mm²",
                iscKA = 32.40,
                iwKA = 48.00,
                deviceKA = 50.0,
                deviceCheck = "PASS",
                cableCheck = "PASS",
                marginPct = 48.1,
                status = "PASS",
                note = ""
            ),
            102 to ShortCircuitResult(
                circuitId = 102,
                sourceName = "CHILLER-TX-02",
                cableMm2 = "185 mm²",
                iscKA = 24.10,
                iwKA = 26.00,
                deviceKA = 25.0,
                deviceCheck = "WARNING",
                cableCheck = "PASS",
                marginPct = 3.7,
                status = "WARNING",
                note = "Device breaking capacity margin is below 10%."
            ),
            103 to ShortCircuitResult(
                circuitId = 103,
                sourceName = "DIESEL-GEN-02",
                cableMm2 = "35 mm²",
                iscKA = 12.80,
                iwKA = 8.50,
                deviceKA = 10.0,
                deviceCheck = "FAIL",
                cableCheck = "FAIL",
                marginPct = -33.6,
                status = "FAIL",
                note = "Thermal withstand Iw < Isc."
            )
        )
        composeTestRule.setContent {
            VoltAtlasTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ShortCircuitPanel(
                        state = mockUiState(customCircuits, customScResults)
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage(File(outputDir, "ShortCircuit_Cards_RenamedFixture.png").path)
    }
}
