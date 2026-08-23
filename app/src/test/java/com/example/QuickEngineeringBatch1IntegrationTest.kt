package com.example

import com.example.ai.context.freshness.contracts.CalculationDomain
import com.example.ai.context.freshness.contracts.CalculationEntityType
import com.example.ai.context.freshness.contracts.CalculationScope
import com.example.data.*
import com.example.data.catalog.CatalogRepository
import com.example.engine.LightingCalculationInput
import com.example.engine.LightingEngine
import com.example.engine.TransformerEngine
import com.example.engine.converters.CapacitorConnectionType
import com.example.engine.converters.ElectricalConverters
import com.example.engine.coordinator.CableCalculationService
import com.example.engine.coordinator.EngineeringCalculationCoordinator
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * Layer 1 & 2 Unit & Adapter Tests for Batch 1 Quick Engineering.
 * Verifies all 8 standalone calculation authorities:
 * 1. Quick Cable Sizing (Real Catalog-backed, In-Memory)
 * 2. Voltage Drop (1P & 3P)
 * 3. Load Current (Two-way 1P & 3P)
 * 4. Power Triangle (Bidirectional P, Q, S)
 * 5. Power Factor Correction (Capacitor kVAR, ΔI, µF)
 * 6. Transformer Quick Calculation (Transient Adapter)
 * 7. Transformer Short-Circuit (Nominal c=1.00 vs IEC 60909 c=1.05)
 * 8. Lighting / Lux Calculation (Lumen Method)
 */
class QuickEngineeringBatch1IntegrationTest {

    companion object {
        private fun findAssetFile(filename: String): File {
            val candidates = listOf(
                File("app/src/main/assets/$filename"),
                File("src/main/assets/$filename"),
                File("../app/src/main/assets/$filename")
            )
            return candidates.firstOrNull { it.exists() }
                ?: throw java.io.FileNotFoundException("Asset file not found: $filename")
        }

        @BeforeClass
        @JvmStatic
        fun loadRealCatalogData() {
            val repo = CatalogRepository()
            val cableCatalogJson = findAssetFile("cable_catalog.json").readText()
            val cableTypesDataJson = findAssetFile("cable_types_data.json").readText()
            val breakerCatalogJson = findAssetFile("breaker_catalog.json").readText()
            val accessoryCatalogJson = findAssetFile("accessory_catalog.json").readText()
            val deratingDataJson = findAssetFile("derating_data.json").readText()
            val transformersSeedJson = findAssetFile("transformers_seed.json").readText()

            repo.loadAllFromStrings(
                cableCatalogJson = cableCatalogJson,
                cableTypesDataJson = cableTypesDataJson,
                breakerCatalogJson = breakerCatalogJson,
                accessoryCatalogJson = accessoryCatalogJson,
                deratingDataJson = deratingDataJson,
                transformersSeedJson = transformersSeedJson
            )

            val cableTypesJson = findAssetFile("cable_types.json").readText()
            val ctArray = JSONArray(cableTypesJson)
            val ctList = mutableListOf<CableType>()
            for (i in 0 until ctArray.length()) {
                val obj = ctArray.getJSONObject(i)
                ctList.add(
                    CableType(
                        code = obj.getInt("code"),
                        cableType = obj.getString("cableType"),
                        conductor = obj.getString("conductor"),
                        insulation = obj.getString("insulation"),
                        armour = obj.getString("armour"),
                        cores = obj.getString("cores"),
                        voltage = obj.getString("voltage"),
                        standard = obj.getString("standard"),
                        rColumn = obj.getString("rColumn")
                    )
                )
            }
            Catalog.cableTypes = ctList

            val dataSheetJson = findAssetFile("data_sheet.json").readText()
            val dsArray = JSONArray(dataSheetJson)
            val dsList = mutableListOf<DataSheetEntry>()
            for (i in 0 until dsArray.length()) {
                val obj = dsArray.getJSONObject(i)
                dsList.add(
                    DataSheetEntry(
                        size = obj.getDouble("size"),
                        R_Cu70 = obj.getDouble("R_Cu70"),
                        X = obj.getDouble("X"),
                        R_Al70 = obj.getDouble("R_Al70"),
                        R_Cu90 = obj.getDouble("R_Cu90"),
                        R_Al90 = obj.getDouble("R_Al90")
                    )
                )
            }
            Catalog.dataSheet = dsList
            Catalog.deratingData = JSONObject(deratingDataJson)

            val ccArray = JSONArray(cableCatalogJson)
            val ccList = mutableListOf<CableCatalogEntry>()
            for (i in 0 until ccArray.length()) {
                val obj = ccArray.getJSONObject(i)
                ccList.add(
                    CableCatalogEntry(
                        mfg = obj.getString("mfg"),
                        typeCode = obj.getInt("typeCode"),
                        size = obj.getDouble("size"),
                        model = obj.optString("model", "IEC-Generic-${obj.optDouble("size")}"),
                        gnd = obj.getDouble("gnd"),
                        duct = obj.getDouble("duct"),
                        air = obj.getDouble("air"),
                        od = obj.optDouble("od", 0.0),
                        wt = obj.optDouble("wt", 0.0)
                    )
                )
            }
            Catalog.cableCatalog = ccList
        }
    }

    @Test
    fun tool1_cableSizing_runsInPureMemory_noRoomPollution() {
        EngineeringCalculationCoordinator.clearCache()

        val transientCircuit = CircuitEntity(
            id = 1,
            projectId = "QUICK_CALC",
            circuitName = "Transient Feeder",
            loadKVA = 35.0,
            voltageV = 400,
            phases = "3P+N",
            pf = 0.85,
            length_m = 45.0,
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            installation = "Laid in Free Air",
            fedFrom = "Grid"
        )

        val result = CableCalculationService.calculateSingleCircuit(
            circuit = transientCircuit,
            parentCumulativeVD = 0.0,
            upstreamCircuits = emptyList(),
            transformers = emptyList()
        )

        assertNotNull(result)
        assertTrue("Result must PASS, got: ${result.status}", result.status.contains("PASS"))
        assertTrue("Operating current must be positive", result.Ib_A > 0.0)
        assertTrue("Voltage drop % must be realistic", result.vdPct < 3.0)

        // Verify that no records are created in coordinator freshness registry
        val quickScope = CalculationScope("QUICK_CALC", CalculationEntityType.CIRCUIT, "1", CalculationDomain.CABLE_SIZING)
        assertNull("Quick calculation must not register freshness records", EngineeringCalculationCoordinator.freshnessRegistry.getRecord(quickScope))
    }

    @Test
    fun tool1_cableSizing_provenance_usesAuthoritativeCatalogTables() {
        // Assert catalog tables are populated from real assets
        assertTrue("Catalog cable sizes must not be empty", Catalog.cableCatalog.isNotEmpty())
        assertTrue("Catalog cable types must not be empty", Catalog.cableTypes.isNotEmpty())
        assertTrue("Catalog datasheet must not be empty", Catalog.dataSheet.isNotEmpty())

        val circuit = CircuitEntity(
            id = 2,
            projectId = "QUICK_CALC",
            loadKVA = 80.0,
            voltageV = 400,
            length_m = 60.0,
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            installation = "Laid in Free Air",
            fedFrom = "Grid"
        )

        val result = CableCalculationService.calculateSingleCircuit(circuit)
        assertTrue(result.status.contains("PASS"))
        assertNotNull("Cable size must be resolved from catalog", result.cableSize_mm2)
        assertTrue("Cable size must contain mm² unit", result.cableSize_mm2.contains("mm²") || result.cableSize_mm2.contains("mm\u00B2"))
        assertTrue("R used must be positive from data sheet", result.rUsed > 0.0)
        assertTrue("X used must be positive from data sheet", result.xUsed > 0.0)
    }

    @Test
    fun tool2_voltageDrop_threePhaseAndSinglePhase() {
        // 3-Phase: mV/A/m = √3 * (0.727 * 0.8 + 0.08 * 0.6) = 1.0905 mV/A/m
        val res3P = ElectricalConverters.cableVoltageDrop(
            rOhmPerKm = 0.727,
            xOhmPerKm = 0.08,
            amps = 100.0,
            lengthM = 50.0,
            cosPhi = 0.8,
            isThreePhase = true,
            voltageV = 400.0
        )
        assertEquals(1.091, res3P.mVperAm, 0.02)
        assertEquals(5.45, res3P.vDrop, 0.1)
        assertEquals(1.36, res3P.vDropPct, 0.05)

        // 1-Phase: mV/A/m = 2 * (0.727 * 0.8 + 0.08 * 0.6) = 1.259 mV/A/m
        val res1P = ElectricalConverters.cableVoltageDrop(
            rOhmPerKm = 0.727,
            xOhmPerKm = 0.08,
            amps = 50.0,
            lengthM = 30.0,
            cosPhi = 0.8,
            isThreePhase = false,
            voltageV = 230.0
        )
        assertEquals(1.259, res1P.mVperAm, 0.02)
        assertEquals(1.889, res1P.vDrop, 0.05)
        assertEquals(0.82, res1P.vDropPct, 0.05)

        // Zero current guard
        val resZero = ElectricalConverters.cableVoltageDrop(0.727, 0.08, 0.0, 50.0)
        assertEquals(0.0, resZero.vDrop, 0.001)
    }

    @Test
    fun tool3_loadCurrent_twoWayConversion() {
        // 3-Phase: 100 kW @ 400V, cosPhi=0.85 -> S = 100/0.85 = 117.65 kVA, I = 100000 / (√3 * 400 * 0.85) = 169.82 A
        val currentRes = ElectricalConverters.powerToCurrent(100.0, 400.0, 0.85, isThreePhase = true)
        assertEquals(169.82, currentRes.amps, 0.1)
        assertEquals(117.65, currentRes.sKva, 0.1)

        // Reverse conversion: 169.82 A @ 400V, cosPhi=0.85 -> 100 kW
        val powerRes = ElectricalConverters.currentToPower(currentRes.amps, 400.0, 0.85, isThreePhase = true)
        assertEquals(100.0, powerRes.pKw, 0.1)
        assertEquals(117.65, powerRes.sKva, 0.1)

        // 1-Phase: 10 kW @ 230V, cosPhi=0.9 -> I = 10000 / (230 * 0.9) = 48.31 A
        val current1P = ElectricalConverters.powerToCurrent(10.0, 230.0, 0.9, isThreePhase = false)
        assertEquals(48.31, current1P.amps, 0.1)
    }

    @Test
    fun tool4_powerTriangle_bidirectional() {
        // Given S = 200 kVA, cosPhi = 0.8 -> P = 160 kW, Q = 120 kVAR
        val resS = ElectricalConverters.powerTriangleFromS(200.0, 0.8)
        assertEquals(160.0, resS.pKw, 0.01)
        assertEquals(120.0, resS.qKvar, 0.01)
        assertEquals(200.0, resS.sKva, 0.01)
        assertEquals(0.8, resS.cosPhi, 0.01)
        assertEquals(0.6, resS.sinPhi, 0.01)

        // Given P = 160 kW, Q = 120 kVAR -> S = 200 kVA, cosPhi = 0.8
        val resPQ = ElectricalConverters.powerTriangleFromPQ(160.0, 120.0)
        assertEquals(200.0, resPQ.sKva, 0.01)
        assertEquals(0.8, resPQ.cosPhi, 0.01)
    }

    @Test
    fun tool5_powerFactorCorrection_andCapacitorEquivalent() {
        // P = 250 kW, cosPhi1 = 0.75 -> cosPhi2 = 0.95 @ 400V
        // tan(acos(0.75)) = 0.8819, tan(acos(0.95)) = 0.3287
        // Qc = 250 * (0.8819 - 0.3287) = 138.3 kVAR
        val pfc = ElectricalConverters.powerFactorCorrection(250.0, 0.75, 0.95, 400.0)
        assertEquals(138.3, pfc.qcKvar, 0.5)
        assertTrue("Current reduction ΔI must be positive", pfc.deltaAmps > 0.0)

        // Delta Capacitor equivalent in µF for 138.3 kVAR @ 400V 50Hz
        val uf = ElectricalConverters.kvarToMicroFarad(pfc.qcKvar, 400.0, 50.0, CapacitorConnectionType.DELTA)
        assertTrue("µF must be positive", uf > 0.0)

        // Roundtrip verification
        val kvarBack = ElectricalConverters.microFaradToKvar(uf, 400.0, 50.0, CapacitorConnectionType.DELTA)
        assertEquals(pfc.qcKvar, kvarBack, 0.01)
    }

    @Test
    fun tool6_transformerQuick_transientAdapter() {
        // Transient in-memory Transformer Entity
        val transientTx = TransformerEntity(
            transformerId = "TX-QUICK",
            manualKVA = 1000.0,
            sizeMode = "Manual",
            voltage = 400,
            percentZ = 5.0,
            xr = 6.0,
            status = "Active",
            vectorGroup = "Dyn11"
        )

        val result = TransformerEngine.calculate(transientTx, emptyList())

        assertEquals(1000.0, result.selectedSize, 0.01)
        // FLC = 1000 * 1000 / (√3 * 400) = 1443.375 A
        assertEquals(1443.38, result.flc, 0.5)
        // Zt (mΩ) = (400² / 1,000,000) * 5/100 * 1000 = 8.0 mΩ
        assertEquals(8.0, result.zt, 0.01)
        assertTrue("Rt must be positive", result.rt > 0.0)
        assertTrue("Xt must be positive", result.xt > 0.0)
    }

    @Test
    fun tool7_transformerShortCircuit_clearNominalVsIecDistinction() {
        val kva = 1000.0
        val zPct = 5.0
        val vSec = 400.0

        // 1. Nominal / Fast estimate (c = 1.00):
        // I_FLC = 1443.38 A
        // I_sc = 1443.38 / (5 / 100) / 1000 = 28.867 kA
        val quickSc = ElectricalConverters.txQuickShortCircuit(kva, zPct, vSec)
        assertEquals(1443.38, quickSc.iFlcAmps, 0.5)
        assertEquals(28.87, quickSc.iScKa, 0.05)

        // 2. Rigorous IEC 60909 (c = 1.05):
        // Zt = 8.0 mΩ
        // Isc_3ph = 1.05 * 400 / (√3 * 8.0) = 420 / 13.8564 = 30.31 kA
        val transientTx = TransformerEntity(
            transformerId = "TX-QUICK",
            manualKVA = kva,
            sizeMode = "Manual",
            voltage = vSec.toInt(),
            percentZ = zPct,
            xr = 6.0,
            status = "Active",
            vectorGroup = "Dyn11"
        )
        val iecResult = TransformerEngine.calculate(transientTx, emptyList())
        assertEquals(30.31, iecResult.isc3Phase, 0.05)

        // Assert that Nominal != IEC 60909 and difference is strictly due to c = 1.05 factor
        assertEquals(iecResult.isc3Phase, quickSc.iScKa * 1.05, 0.05)
    }

    @Test
    fun tool8_lighting_pureMath_isolated() {
        val input = LightingCalculationInput(
            length = 8.0,
            width = 5.0,
            height = 2.8,
            targetLux = 400.0,
            maintenanceFactor = 0.8,
            utilizationFactor = 0.6,
            lumensPerFixture = 3000.0,
            wattsPerFixture = 24.0
        )

        val result = LightingEngine.calculate(input)

        assertEquals(40.0, result.area_m2, 0.01)
        // Total Lumens = (400 * 40) / (0.6 * 0.8) = 16000 / 0.48 = 33333.3 lm
        assertEquals(33333.3, result.totalLumensNeeded, 0.5)
        // Fixture count = ceil(33333.3 / 3000) = ceil(11.11) = 12
        assertEquals(12, result.fixturesCount)
        assertEquals(288.0, result.totalPower_W, 0.01)
        assertEquals(7.2, result.powerDensity_W_m2, 0.05)
    }
}
