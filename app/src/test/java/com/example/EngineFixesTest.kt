package com.example

import com.example.data.*
import com.example.engine.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.json.JSONObject

class EngineFixesTest {

    private var originalCableTypes: List<CableType> = emptyList()
    private var originalDataSheet: List<DataSheetEntry> = emptyList()
    private var originalCableCatalog: List<CableCatalogEntry> = emptyList()

    @Before
    fun setupCatalog() {
        // Back up original values
        originalCableTypes = Catalog.cableTypes
        originalDataSheet = Catalog.dataSheet
        originalCableCatalog = Catalog.cableCatalog

        // Unconditionally set up mock catalog for this test class
        Catalog.cableTypes = listOf(
            CableType(1, "Cu/XLPE", "Cu", "XLPE", "None", "3C", "0.6/1kV", "IEC", "R_Cu90"),
            CableType(2, "Cu/PVC", "Cu", "PVC", "None", "3C", "0.6/1kV", "IEC", "R_Cu70")
        )
        Catalog.dataSheet = listOf(
            DataSheetEntry(16.0, 1.15, 0.08, 1.91, 1.24, 2.06),
            DataSheetEntry(25.0, 0.727, 0.075, 1.20, 0.783, 1.29),
            DataSheetEntry(35.0, 0.524, 0.075, 0.868, 0.564, 0.933),
            DataSheetEntry(70.0, 0.268, 0.072, 0.443, 0.288, 0.476)
        )
        Catalog.cableCatalog = listOf(
            CableCatalogEntry(mfg = "Elsewedy", typeCode = 1, size = 16.0, model = "Model-16", gnd = 20.0, duct = 20.0, air = 20.0, od = 16.0, wt = 1.0),
            CableCatalogEntry(mfg = "Elsewedy", typeCode = 1, size = 25.0, model = "Model-25", gnd = 30.0, duct = 30.0, air = 30.0, od = 20.0, wt = 1.2),
            CableCatalogEntry(mfg = "Elsewedy", typeCode = 1, size = 35.0, model = "Model-35", gnd = 40.0, duct = 40.0, air = 40.0, od = 24.0, wt = 1.5),
            CableCatalogEntry(mfg = "Elsewedy", typeCode = 1, size = 70.0, model = "Model-70", gnd = 80.0, duct = 80.0, air = 80.0, od = 30.0, wt = 2.5)
        )
    }

    @org.junit.After
    fun tearDownCatalog() {
        // Restore original values
        Catalog.cableTypes = originalCableTypes
        Catalog.dataSheet = originalDataSheet
        Catalog.cableCatalog = originalCableCatalog
    }

    @Test
    fun testSinglePhaseNeutralSizing() {
        // Single-phase circuit: neutral size should match phase size (100% capacity)
        val circuit = CircuitEntity(
            id = 1,
            circuitName = "SinglePhaseCircuit",
            loadKVA = 2.0,
            voltageV = 230,
            phases = "1P+N", // Single phase
            pf = 0.85,
            length_m = 50.0,
            cableType = "Cu/XLPE",
            manufacturer = "Elsewedy"
        )

        val result = CableSizingEngine.sizeCircuit(circuit)
        assertEquals("✓ PASS", result.status)
        
        // Neutral size should be exactly equal to phase size
        assertEquals(result.cableSize_mm2, result.neutral_mm2)
    }

    @Test
    fun testCpcSizeParsingInProtectionEngine() {
        val circuit = CircuitEntity(
            id = 2,
            circuitName = "TestCPC",
            length_m = 10.0,
            phases = "3P+N"
        )
        
        // Sizing result with formatted CPC string
        val sizingResult = SizingResult(
            Ib_A = 10.0,
            deviceSize_A = 16.0,
            deviceType = "MCB",
            cTotalDerating = 1.0,
            iRequired_perCable_A = 10.0,
            productCode = "Model-70",
            cableSize_mm2 = "70 mm²",
            ratedI_table_A = 80.0,
            capacity_derated_Iz_A = 80.0,
            utilizationPct = 12.5,
            neutral_mm2 = "35 mm²",
            earthCPC_mm2 = "35 mm² (Cu)", // descriptive string
            voltDrop_V = 0.5,
            vdPct = 0.1,
            cumulativeVDpct = 0.1,
            status = "✓ PASS",
            failReason = "",
            cableMaterial = "Cu",
            rUsed = 0.268,
            xUsed = 0.072
        , resultStatus = com.example.engine.SizingResultStatus.PASS, isAmpacityOk = true, isVoltageDropOk = true, primaryFailureCode = null)

        // Run Zs calculation
        val adsResult = ProtectionEngine.calculateAds(circuit, sizingResult, ze_ohm = 0.0)
        
        // R1 of 70mm2 Cu is 0.268 ohm/km (Cu70)
        // R2 of 35mm2 Cu is 0.524 ohm/km (Cu70)
        // Check if correct CPC resistance of 0.524 is used (r2_ohm_km = 0.524)
        // If the parsing was broken, r2 would equal r1 (1.15)
        assertEquals(0.268, adsResult.r1_ohm_km, 0.001)
        assertEquals(0.524, adsResult.r2_ohm_km, 0.001)
    }

    @Test
    fun testUpsSizingApparentPowerCapacity() {
        // Sizing critical load with PF = 0.8
        val circuit = CircuitEntity(id = 3, circuitName = "UPS-Load", loadKVA = 100.0, pf = 0.8, upsBacked = true)
        val settings = UpsEntity(projectId = "DEFAULT_PROJECT", id = 1, upsPf = 0.9, designMarginPct = 0.0, autonomyMinutes = 30)

        val result = UpsEngine.calculate(settings, listOf(circuit))
        
        // Load kVA is 100.0 kVA. Since load PF (0.8) is less than UPS PF (0.9):
        // Active power = 100.0 * 0.8 = 80.0 kW.
        // If we only divided active power by UPS PF, required apparent power would be 80.0 / 0.9 = 88.8 kVA.
        // Under our fix, it must be maxOf(100.0, 88.8) = 100.0 kVA to satisfy apparent power inverter limits.
        assertEquals(100.0, result.upsApparentPowerReq, 0.01)
    }

    @Test
    fun testGeneratorSizingDoubleDerating() {
        // Essential load kVA = 100.0
        val circuit = CircuitEntity(id = 4, circuitName = "Gen-Load", loadKVA = 100.0, generatorIncluded = true, assignedGeneratorId = "GEN-01")
        val settings = GeneratorEntity(projectId = "DEFAULT_PROJECT", id = 1, generatorPf = 0.8, oversizingFactor = 1.0, stepLoadPct = 0.0, demandFactor = 1.0)

        val result = GeneratorEngine.calculate(settings, listOf(circuit), totalConnectedLoad = 100.0)
        
        // Excel R26 formula: required_kVA = MAX(essential_kVA, stepLoad_kVA) / GenPF (100.0 / 0.8 = 125.0 kVA)
        assertEquals(125.0, result.requiredGenerator, 0.01)
    }

    @Test
    fun testPfcDetunedReactorCurrentBoost() {
        val tx = TransformerEntity(projectId = "DEFAULT_PROJECT", transformerId = "TX-1", pf = 0.8, status = "Active")
        val global = PfGlobalEntity(projectId = "DEFAULT_PROJECT", id = 1, targetPf = 0.95, kvarStepSize = 25.0, systemVoltage = 400)
        val txResults = mapOf("TX-1" to TransformerResult(connectedLoad = 125.0, circuitsAssigned = 1, realPower = 100.0, demandLoad = 100.0, diversifiedLoad = 100.0, futureLoad = 100.0, designLoad = 100.0, requiredKVA = 125.0, recommendedSize = 160.0, selectedSize = 160.0, loadingPct = 78.0, flc = 230.0, zt = 10.0, rt = 3.0, xt = 9.0, isc3Phase = 23.0, isc1Phase = 15.0, status = "PASS", z0 = 9.0, vectorGroup = "Dyn11"))
        
        // Detuning reactor set to "Yes"
        val settingsWithReactor = listOf(PfTransformerEntity(projectId = "DEFAULT_PROJECT", transformerId = "TX-1", detuningReactor = "Yes"))
        val resultWithReactor = PfCorrectionEngine.calculate(global, settingsWithReactor, listOf(tx), txResults)[0]
        
        // Detuning reactor set to "No"
        val settingsNoReactor = listOf(PfTransformerEntity(projectId = "DEFAULT_PROJECT", transformerId = "TX-1", detuningReactor = "No"))
        val resultNoReactor = PfCorrectionEngine.calculate(global, settingsNoReactor, listOf(tx), txResults)[0]
        
        // With detuning reactor, current should be 1.075x higher than without it
        assertTrue(resultWithReactor.bankCurrent > resultNoReactor.bankCurrent)
        assertEquals(resultNoReactor.bankCurrent * 1.075, resultWithReactor.bankCurrent, 0.01)
    }
}
