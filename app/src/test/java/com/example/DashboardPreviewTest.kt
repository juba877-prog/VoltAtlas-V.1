package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.ui.DashboardScreen
import com.example.ui.UiState
import com.example.data.CircuitEntity
import com.example.engine.SizingResult
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
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
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class DashboardPreviewTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun render_dashboard_preview() {
        val dummyCircuits = listOf(
            CircuitEntity(
                id = 1, circuitName = "MDB-01", loadKVA = 150.0, fedFrom = "Transformer",
                transformerId = "TX-01", loadType = "NORMAL", phases = "3P+N", voltageV = 400,
                pf = 0.85, safetyFactor = 1.0, manufacturer = "Elsewedy", cableType = "Cu/XLPE/SWA/PVC - 4C",
                installation = "Laid in Ducts", parallelRuns = 1, airTempC = 40, gndTempC = 40,
                burialDepth_m = 0.6, soilRes = 125, noCircuits = 1, formation = "Flat", spacing = "Touching",
                length_m = 50.0, maxVDpct = 0.03
            ),
            CircuitEntity(
                id = 2, circuitName = "DB-LIGHTING", loadKVA = 25.0, fedFrom = "MDB-01",
                transformerId = "TX-01", loadType = "NORMAL", phases = "1P+N", voltageV = 230,
                pf = 0.9, safetyFactor = 1.0, manufacturer = "Elsewedy", cableType = "Cu/XLPE/PVC - 2C",
                installation = "Laid in Ducts", parallelRuns = 1, airTempC = 40, gndTempC = 40,
                burialDepth_m = 0.6, soilRes = 125, noCircuits = 1, formation = "Flat", spacing = "Touching",
                length_m = 30.0, maxVDpct = 0.02
            )
        )

        val dummySizing = mapOf(
            1 to SizingResult(
                Ib_A = 255.0, deviceSize_A = 315.0, deviceType = "MCCB", cTotalDerating = 1.0,
                iRequired_perCable_A = 255.0, productCode = "CU/XLPE/SWA/PVC-185", cableSize_mm2 = "185 mm²",
                ratedI_table_A = 380.0, capacity_derated_Iz_A = 380.0, utilizationPct = 67.0,
                neutral_mm2 = "95 mm²", earthCPC_mm2 = "95 mm²", voltDrop_V = 2.4, vdPct = 0.6,
                cumulativeVDpct = 0.6, status = "✓ PASS", cableOD_mm = 52.0
            ),
            2 to SizingResult(
                Ib_A = 120.0, deviceSize_A = 125.0, deviceType = "MCB", cTotalDerating = 1.0,
                iRequired_perCable_A = 120.0, productCode = "CU/XLPE/PVC-35", cableSize_mm2 = "35 mm²",
                ratedI_table_A = 150.0, capacity_derated_Iz_A = 150.0, utilizationPct = 80.0,
                neutral_mm2 = "35 mm²", earthCPC_mm2 = "16 mm²", voltDrop_V = 1.8, vdPct = 0.78,
                cumulativeVDpct = 1.38, status = "✓ PASS", cableOD_mm = 22.0
            )
        )

        val dummyState = UiState.Success(
            circuits = dummyCircuits,
            transformers = emptyList(),
            sizingResults = dummySizing,
            transformerResults = emptyMap(),
            boq = emptyList(),
            drums = emptyList(),
            procurement = emptyList(),
            panels = emptyList(),
            generatorResult = null,
            atsResult = null,
            upsResult = null,
            pfCorrectionResults = emptyList()
        )

        composeTestRule.setContent {
            DashboardScreen(state = dummyState, onNavigate = {})
        }

        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/dashboard_preview.png")
    }
}
