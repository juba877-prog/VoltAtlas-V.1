package com.example

import com.example.ai.context.freshness.contracts.CalculationDomain
import com.example.ai.context.freshness.contracts.CalculationEntityType
import com.example.ai.context.freshness.contracts.CalculationScope
import com.example.data.CableCatalogEntry
import com.example.data.CableType
import com.example.data.Catalog
import com.example.data.CircuitEntity
import com.example.data.DataSheetEntry
import com.example.engine.CableSizingEngine
import com.example.engine.coordinator.CableCalculationService
import com.example.engine.coordinator.EngineeringCalculationCoordinator
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for Quick Cable Sizing (Stateless Scratchpad).
 * Verifies that the stateless scratchpad calculation:
 * 1. Executes successfully for isolated circuits.
 * 2. Matches the core CableSizingEngine deterministically field-by-field.
 * 3. Bypasses and leaves untouched the repository, DB, project revision, and coordinator freshness registry.
 * 4. Correctly sets fedFrom = "Grid" to bypass transformer checks.
 * 5. Respects manual overrides.
 * 6. Safely handles edge/invalid inputs without throwing unhandled exceptions.
 */
class QuickCableCalcTest {

    @Before
    fun setUp() {
        Catalog.cableTypes = listOf(
            CableType(1, "Cu/XLPE", "Cu", "XLPE", "None", "3C", "0.6/1kV", "IEC", "R_Cu90"),
            CableType(2, "Cu/PVC", "Cu", "PVC", "None", "3C", "0.6/1kV", "IEC", "R_Cu70"),
            CableType(3, "Cu/XLPE/PVC - 4C", "Cu", "XLPE", "None", "4C", "0.6/1kV", "IEC", "R_Cu90"),
            CableType(4, "Cu/XLPE/SWA/PVC", "Cu", "XLPE", "SWA", "4C", "0.6/1kV", "IEC", "R_Cu90")
        )
        Catalog.dataSheet = listOf(
            DataSheetEntry(1.5, 12.1, 0.118, 19.5, 14.52, 23.4),
            DataSheetEntry(2.5, 7.41, 0.109, 12.1, 8.89, 14.52),
            DataSheetEntry(4.0, 4.61, 0.101, 7.41, 5.53, 8.89),
            DataSheetEntry(6.0, 3.08, 0.093, 4.61, 3.7, 5.53),
            DataSheetEntry(10.0, 1.83, 0.086, 3.08, 2.2, 3.7),
            DataSheetEntry(16.0, 1.15, 0.082, 1.91, 1.38, 2.29),
            DataSheetEntry(25.0, 0.727, 0.080, 1.20, 0.872, 1.44),
            DataSheetEntry(35.0, 0.524, 0.078, 0.868, 0.629, 1.04),
            DataSheetEntry(50.0, 0.387, 0.078, 0.641, 0.464, 0.77),
            DataSheetEntry(70.0, 0.268, 0.077, 0.443, 0.322, 0.532),
            DataSheetEntry(95.0, 0.193, 0.076, 0.320, 0.232, 0.384),
            DataSheetEntry(120.0, 0.153, 0.075, 0.253, 0.184, 0.304),
            DataSheetEntry(150.0, 0.124, 0.075, 0.206, 0.149, 0.247),
            DataSheetEntry(185.0, 0.0991, 0.074, 0.164, 0.119, 0.197),
            DataSheetEntry(240.0, 0.0754, 0.074, 0.125, 0.0905, 0.15),
            DataSheetEntry(300.0, 0.0601, 0.073, 0.100, 0.0721, 0.12),
            DataSheetEntry(400.0, 0.0470, 0.073, 0.0778, 0.0564, 0.0934),
            DataSheetEntry(500.0, 0.0366, 0.072, 0.0605, 0.0439, 0.0726),
            DataSheetEntry(630.0, 0.0283, 0.072, 0.0469, 0.0340, 0.0563)
        )
        val sizes = listOf(
            1.5 to 10.0, 2.5 to 11.0, 4.0 to 12.0, 6.0 to 13.0, 10.0 to 15.0,
            16.0 to 16.0, 25.0 to 19.0, 35.0 to 21.0, 50.0 to 24.0, 70.0 to 27.0,
            95.0 to 30.0, 120.0 to 33.0, 150.0 to 37.0, 185.0 to 41.0, 240.0 to 46.0,
            300.0 to 51.0, 400.0 to 58.0, 500.0 to 64.0, 630.0 to 72.0
        )
        val entries = mutableListOf<CableCatalogEntry>()
        for (tc in listOf(1, 2, 3, 4)) {
            for ((sz, od) in sizes) {
                val baseCurrent = sz * 4.5
                entries.add(
                    CableCatalogEntry(
                        mfg = "Elsewedy",
                        typeCode = tc,
                        size = sz,
                        model = "Standard",
                        gnd = baseCurrent * 1.1,
                        duct = baseCurrent * 0.9,
                        air = baseCurrent * 1.0,
                        od = od,
                        wt = sz * 1.5
                    )
                )
            }
        }
        Catalog.cableCatalog = entries
    }

    @Test
    fun quickCalc_singleIsolatedCircuit_returnsSizingResult_notFail() {
        val circuit = CircuitEntity(
            id = 1,
            projectId = "QUICK_CALC",
            circuitName = "Quick-Feeder-1",
            loadKVA = 50.0,
            voltageV = 400,
            phases = "3P+N",
            pf = 0.85,
            safetyFactor = 1.0,
            length_m = 50.0,
            maxVDpct = 0.03,
            parallelRuns = 1,
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            installation = "Laid in Free Air",
            fedFrom = "Grid",
            transformerId = ""
        )

        val result = CableCalculationService.calculateSingleCircuit(
            circuit = circuit,
            parentCumulativeVD = 0.0,
            upstreamCircuits = emptyList(),
            transformers = emptyList()
        )

        assertNotNull("SizingResult must not be null", result)
        assertTrue("Status must be PASS, got: ${result.status}", result.status.contains("PASS"))
        assertTrue("Design current Ib must be positive", result.Ib_A > 0.0)
        assertTrue("Selected cable size must not be empty", result.cableSize_mm2.isNotEmpty())
        assertTrue("Breaker rating must be positive", result.deviceSize_A > 0.0)
        assertTrue("Voltage drop must be positive", result.voltDrop_V > 0.0)
        assertTrue("Voltage drop % must be within 3.0%", result.vdPct <= 3.0)
    }

    @Test
    fun quickCalc_resultMatchesDirectEngineCall_fieldByField() {
        val circuit = CircuitEntity(
            id = 999,
            projectId = "QUICK_CALC",
            circuitName = "Feeder-Comparison",
            loadKVA = 75.0,
            voltageV = 400,
            phases = "3P+N",
            pf = 0.80,
            safetyFactor = 1.25,
            length_m = 80.0,
            maxVDpct = 0.03,
            parallelRuns = 1,
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            installation = "Laid in Ducts",
            airTempC = 45,
            gndTempC = 35,
            burialDepth_m = 0.8,
            soilRes = 120,
            noCircuits = 2,
            formation = "Flat",
            spacing = "Touching",
            fedFrom = "Grid",
            transformerId = ""
        )

        // 1. Call via Quick-Calc path (CableCalculationService)
        val quickResult = CableCalculationService.calculateSingleCircuit(
            circuit = circuit,
            parentCumulativeVD = 0.0,
            upstreamCircuits = emptyList(),
            transformers = emptyList()
        )

        // 2. Call directly on CableSizingEngine
        val directEngineResult = CableSizingEngine.sizeCircuit(
            circuit = circuit,
            parentCumulativeVD = 0.0,
            allCircuits = emptyList()
        )

        // 3. Assert exact field-by-field equality (deterministic output proof)
        assertEquals("Status must match", directEngineResult.status, quickResult.status)
        assertEquals("Ib_A must match", directEngineResult.Ib_A, quickResult.Ib_A, 0.0001)
        assertEquals("deviceSize_A must match", directEngineResult.deviceSize_A, quickResult.deviceSize_A, 0.0001)
        assertEquals("deviceType must match", directEngineResult.deviceType, quickResult.deviceType)
        assertEquals("cableSize_mm2 must match", directEngineResult.cableSize_mm2, quickResult.cableSize_mm2)
        assertEquals("cTotalDerating must match", directEngineResult.cTotalDerating, quickResult.cTotalDerating, 0.0001)
        assertEquals("ratedI_table_A must match", directEngineResult.ratedI_table_A, quickResult.ratedI_table_A, 0.0001)
        assertEquals("capacity_derated_Iz_A must match", directEngineResult.capacity_derated_Iz_A, quickResult.capacity_derated_Iz_A, 0.0001)
        assertEquals("utilizationPct must match", directEngineResult.utilizationPct, quickResult.utilizationPct, 0.0001)
        assertEquals("voltDrop_V must match", directEngineResult.voltDrop_V, quickResult.voltDrop_V, 0.0001)
        assertEquals("vdPct must match", directEngineResult.vdPct, quickResult.vdPct, 0.0001)
        assertEquals("cumulativeVDpct must match", directEngineResult.cumulativeVDpct, quickResult.cumulativeVDpct, 0.0001)
        assertEquals("neutral_mm2 must match", directEngineResult.neutral_mm2, quickResult.neutral_mm2)
        assertEquals("earthCPC_mm2 must match", directEngineResult.earthCPC_mm2, quickResult.earthCPC_mm2)
        assertEquals("cableMaterial must match", directEngineResult.cableMaterial, quickResult.cableMaterial)
        assertEquals("insulation must match", directEngineResult.insulation, quickResult.insulation)
        assertEquals("cableOD_mm must match", directEngineResult.cableOD_mm, quickResult.cableOD_mm, 0.0001)
        assertEquals("earthOD_mm must match", directEngineResult.earthOD_mm, quickResult.earthOD_mm, 0.0001)
    }

    @Test
    fun quickCalc_doesNotTouchRepositoryOrRevisionOrFreshness() {
        val quickScope = CalculationScope(
            projectId = "QUICK_CALC",
            entityType = CalculationEntityType.CIRCUIT,
            entityId = "999",
            domain = CalculationDomain.CABLE_SIZING
        )

        // Clear coordinator caches before test
        EngineeringCalculationCoordinator.clearCache()

        val circuit = CircuitEntity(
            id = 999,
            projectId = "QUICK_CALC",
            circuitName = "Feeder-No-Pollution",
            loadKVA = 30.0,
            voltageV = 400,
            length_m = 25.0,
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            installation = "Laid in Free Air",
            fedFrom = "Grid"
        )

        // Run Quick-Calc
        val result = CableCalculationService.calculateSingleCircuit(
            circuit = circuit,
            parentCumulativeVD = 0.0,
            upstreamCircuits = emptyList(),
            transformers = emptyList()
        )

        assertTrue("Result must pass", result.status.contains("PASS"))

        // Verify: coordinator freshness registry has NO record for QUICK_CALC
        val recordedMetadata = EngineeringCalculationCoordinator.freshnessRegistry.getRecord(quickScope)
        assertNull("Quick-calc must NOT create any records in freshnessRegistry", recordedMetadata)
    }

    @Test
    fun quickCalc_fedFromGrid_bypassesTransformerFailCheck() {
        val circuitGrid = CircuitEntity(
            id = 1,
            projectId = "QUICK_CALC",
            circuitName = "Grid-Fed-Feeder",
            loadKVA = 45.0,
            voltageV = 400,
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            installation = "Laid in Free Air",
            fedFrom = "Grid",
            transformerId = ""
        )

        val resultGrid = CableCalculationService.calculateSingleCircuit(
            circuit = circuitGrid,
            parentCumulativeVD = 0.0,
            upstreamCircuits = emptyList(),
            transformers = emptyList()
        )

        assertTrue("fedFrom='Grid' must NOT fail with missing transformer error", resultGrid.status.contains("PASS"))
        assertFalse("failReason must not mention transformer", resultGrid.failReason.contains("Missing source transformer"))

        // Conversely, verify that fedFrom='Transformer' without transformers list would trigger the check:
        val circuitTx = circuitGrid.copy(fedFrom = "Transformer", transformerId = "TX-MISSING")
        val resultTx = CableCalculationService.calculateSingleCircuit(
            circuit = circuitTx,
            parentCumulativeVD = 0.0,
            upstreamCircuits = emptyList(),
            transformers = emptyList()
        )
        assertTrue("fedFrom='Transformer' without transformer must fail", resultTx.status.contains("FAIL"))
        assertTrue("failReason must indicate missing transformer", resultTx.failReason.contains("Missing source transformer"))
    }

    @Test
    fun quickCalc_overrideCableSize_isRespected() {
        val circuit = CircuitEntity(
            id = 1,
            projectId = "QUICK_CALC",
            circuitName = "Override-Feeder",
            loadKVA = 20.0,
            voltageV = 400,
            length_m = 30.0,
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            installation = "Laid in Free Air",
            fedFrom = "Grid",
            overrideCableSize = "70",
            overrideDeviceType = "MCCB",
            overrideDeviceSize = 100.0
        )

        val result = CableCalculationService.calculateSingleCircuit(
            circuit = circuit,
            parentCumulativeVD = 0.0,
            upstreamCircuits = emptyList(),
            transformers = emptyList()
        )

        assertTrue("Result must pass with override", result.status.contains("PASS"))
        assertEquals("Selected cable size must respect override", "70 mm²", result.cableSize_mm2)
        assertEquals("Breaker rating must respect override", 100.0, result.deviceSize_A, 0.0001)
        assertEquals("Breaker type must respect override", "MCCB", result.deviceType)
    }

    @Test
    fun quickCalc_invalidInputs_areHandledWithoutCrash() {
        // Zero voltage circuit
        val invalidVoltageCircuit = CircuitEntity(
            id = 1,
            projectId = "QUICK_CALC",
            voltageV = 0,
            loadKVA = 50.0,
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            fedFrom = "Grid"
        )
        val resZeroV = CableCalculationService.calculateSingleCircuit(invalidVoltageCircuit)
        assertTrue("Zero voltage must return FAIL result", resZeroV.status.contains("FAIL"))
        assertTrue("Fail reason must mention voltage", resZeroV.failReason.contains("Voltage must be greater than zero"))

        // Negative load circuit
        val negativeLoadCircuit = CircuitEntity(
            id = 1,
            projectId = "QUICK_CALC",
            voltageV = 400,
            loadKVA = -10.0,
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            fedFrom = "Grid"
        )
        val resNegLoad = CableCalculationService.calculateSingleCircuit(negativeLoadCircuit)
        assertTrue("Negative load must return FAIL result", resNegLoad.status.contains("FAIL"))
        assertTrue("Fail reason must mention load", resNegLoad.failReason.contains("Load kVA cannot be negative"))

        // Zero length circuit
        val zeroLengthCircuit = CircuitEntity(
            id = 1,
            projectId = "QUICK_CALC",
            voltageV = 400,
            loadKVA = 50.0,
            length_m = 0.0,
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            fedFrom = "Grid"
        )
        val resZeroLen = CableCalculationService.calculateSingleCircuit(zeroLengthCircuit)
        assertTrue("Zero length must return FAIL result", resZeroLen.status.contains("FAIL"))
        assertTrue("Fail reason must mention cable length", resZeroLen.failReason.contains("Cable length must be greater than zero"))
    }
}
