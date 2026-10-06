package com.example.engine

import com.example.data.CircuitEntity
import com.example.data.catalog.CatalogRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * Characterization test suite for EE-03 (Cable Short-Circuit Thermal Withstand Clearing Time).
 *
 * Asserts the CURRENT unmodified behavior of VoltAtlas:
 * C1. Category defaults are assigned based solely on protective device category:
 *     MCB = 0.01 s, MCCB = 0.10 s, ACB = 0.30 s.
 * C2. SizingResult.clearingTime_s stores this category default.
 * C3. Downstream ShortCircuitEngine and ProtectionEngine consume SizingResult.clearingTime_s
 *     for the adiabatic withstand calculation (Iw and I²t).
 */
class CableThermalWithstandCharacterizationTest {

    companion object {
        @JvmField
        @org.junit.ClassRule
        val catalogStateReset = com.example.testing.CatalogStateResetRule()

        private fun findAssetFile(filename: String): java.io.File {
            val candidates = listOf(
                java.io.File("app/src/main/assets/$filename"),
                java.io.File("src/main/assets/$filename"),
                java.io.File("../app/src/main/assets/$filename")
            )
            return candidates.firstOrNull { it.exists() }
                ?: error("Asset not found: $filename")
        }

        private inline fun <reified T> loadCatalogArray(filename: String, map: (org.json.JSONObject) -> T): List<T> {
            val arr = org.json.JSONArray(findAssetFile(filename).readText(Charsets.UTF_8))
            return (0 until arr.length()).map { map(arr.getJSONObject(it)) }
        }

        @org.junit.BeforeClass
        @JvmStatic
        fun setUpClass() {
            com.example.data.Catalog.cableTypes = loadCatalogArray("cable_types.json") { obj ->
                com.example.data.CableType(
                    code = com.example.data.catalog.BreakerParser.parsePoles(obj.opt("typeCode") ?: obj.opt("code")),
                    cableType = obj.optString("cableType", "Standard"),
                    conductor = obj.optString("conductor", "Cu"),
                    insulation = obj.optString("insulation", "XLPE"),
                    armour = obj.optString("armour", "None"),
                    cores = obj.optString("cores", "4C"),
                    voltage = obj.optString("voltage", "0.6/1"),
                    standard = obj.optString("standard", "IEC 60502"),
                    rColumn = obj.optString("rColumn", "R_Cu90")
                )
            }
            com.example.data.Catalog.dataSheet = loadCatalogArray("data_sheet.json") { obj ->
                com.example.data.DataSheetEntry(
                    size = obj.getDouble("size"),
                    R_Cu20 = if (obj.has("R_Cu20")) obj.getDouble("R_Cu20") else obj.getDouble("R_Cu70"),
                    X = obj.getDouble("X"),
                    R_Al20 = if (obj.has("R_Al20")) obj.getDouble("R_Al20") else obj.getDouble("R_Al70"),
                    R_Cu_70_est = if (obj.has("R_Cu_70_est")) obj.getDouble("R_Cu_70_est") else obj.getDouble("R_Cu90"),
                    R_Al_70_est = if (obj.has("R_Al_70_est")) obj.getDouble("R_Al_70_est") else obj.getDouble("R_Al90")
                )
            }
            com.example.data.Catalog.cableCatalog = loadCatalogArray("cable_catalog.json") { obj ->
                com.example.data.CableCatalogEntry(
                    mfg = obj.optString("mfg", obj.optString("Manufacturer", "Generic")),
                    typeCode = com.example.data.catalog.BreakerParser.parsePoles(obj.opt("typeCode") ?: obj.opt("TypeCode") ?: obj.opt("Code")),
                    size = com.example.data.catalog.BreakerParser.parseDouble(obj.opt("size") ?: obj.opt("SZ (mm²)") ?: obj.opt("Size")),
                    model = obj.optString("model", obj.optString("Model", "")),
                    gnd = com.example.data.catalog.BreakerParser.parseDouble(obj.opt("gnd") ?: obj.opt("GND")),
                    duct = com.example.data.catalog.BreakerParser.parseDouble(obj.opt("duct") ?: obj.opt("DUCT")),
                    air = com.example.data.catalog.BreakerParser.parseDouble(obj.opt("air") ?: obj.opt("AIR")),
                    od = com.example.data.catalog.BreakerParser.parseDouble(obj.opt("od") ?: obj.opt("OD")),
                    wt = com.example.data.catalog.BreakerParser.parseDouble(obj.opt("wt") ?: obj.opt("WT"))
                )
            }
            com.example.data.Catalog.breakers = loadCatalogArray("breaker_catalog.json") { obj ->
                com.example.data.BreakerEntry(
                    mfg = obj.optString("mfg", obj.optString("Source", "Generic")),
                    type = obj.optString("type", "MCCB"),
                    series = obj.optString("series", obj.optString("Series", "")),
                    frame = if (obj.has("frameA")) obj.optString("frameA", "") else obj.optString("frame", obj.optString("Frame", "")),
                    version = obj.optString("version", obj.optString("Version", "")),
                    rating_A = com.example.data.catalog.BreakerParser.parseRating(obj.opt("ratingA") ?: obj.opt("rating_A") ?: obj.opt("In (A)")),
                    poles = com.example.data.catalog.BreakerParser.parsePoles(obj.opt("poles") ?: obj.opt("Poles")),
                    icu_kA = com.example.data.catalog.BreakerParser.parseDouble(obj.opt("icuKa") ?: obj.opt("icu_kA") ?: obj.opt("Icu@415V (kA)")),
                    ics_kA = com.example.data.catalog.BreakerParser.parseDouble(obj.opt("icsKa") ?: obj.opt("ics_kA") ?: obj.opt("Ics@415V (kA)")),
                    icm_kA = com.example.data.catalog.BreakerParser.parseDouble(obj.opt("icmKa") ?: obj.opt("icm_kA") ?: obj.opt("Icm@415V (kA)")),
                    tripUnit = obj.optString("tripUnit", obj.optString("Trip Unit", "Thermal-Magnetic"))
                )
            }
            com.example.data.Catalog.deratingData = org.json.JSONObject(findAssetFile("derating_data.json").readText(Charsets.UTF_8))
            com.example.data.Catalog.invalidateIndexes()
        }
    }

    @Test
    fun testC1_categoryDefaults_areAssignedByDeviceType() {
        // Circuit sized with device sized <= 63A defaults to MCB (0.01 s)
        val circuitMcb = CircuitEntity(
            id = 1,
            circuitName = "BRANCH-MCB",
            loadKVA = 5.0, // Ib ~ 7.2A -> MCB
            voltageV = 400,
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            installation = "Laid in Ducts",
            parallelRuns = 1,
            airTempC = 40,
            gndTempC = 40,
            burialDepth_m = 0.6,
            soilRes = 125,
            maxVDpct = 0.04,
            length_m = 15.0
        )
        val sizingMcb = CableSizingEngine.sizeCircuit(circuitMcb, 0.0)
        assertEquals("✓ PASS", sizingMcb.status)
        assertEquals("MCB deviceType expected", "MCB", sizingMcb.deviceType)
        assertEquals("MCB default clearing time must be 0.01s", 0.01, sizingMcb.clearingTime_s, 1e-6)

        // Circuit sized with device sized > 63A and <= 630A defaults to MCCB (0.10 s)
        val circuitMccb = CircuitEntity(
            id = 2,
            circuitName = "FEEDER-MCCB",
            loadKVA = 100.0, // Ib ~ 144A -> MCCB
            voltageV = 400,
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            installation = "Laid in Ducts",
            parallelRuns = 1,
            airTempC = 40,
            gndTempC = 40,
            burialDepth_m = 0.6,
            soilRes = 125,
            maxVDpct = 0.04,
            length_m = 50.0
        )
        val sizingMccb = CableSizingEngine.sizeCircuit(circuitMccb, 0.0)
        assertEquals("✓ PASS", sizingMccb.status)
        assertEquals("MCCB deviceType expected", "MCCB", sizingMccb.deviceType)
        assertEquals("MCCB default clearing time must be 0.10s", 0.10, sizingMccb.clearingTime_s, 1e-6)

        // Circuit sized with device sized > 630A defaults to ACB (0.30 s)
        val circuitAcb = CircuitEntity(
            id = 3,
            circuitName = "INCOMER-ACB",
            loadKVA = 600.0, // Ib ~ 866A -> ACB
            voltageV = 400,
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            installation = "Laid in Ducts",
            parallelRuns = 3,
            overrideDeviceType = "ACB",
            airTempC = 40,
            gndTempC = 40,
            burialDepth_m = 0.6,
            soilRes = 125,
            maxVDpct = 0.04,
            length_m = 30.0
        )
        val sizingAcb = CableSizingEngine.sizeCircuit(circuitAcb, 0.0)
        assertEquals("✓ PASS", sizingAcb.status)
        assertEquals("ACB deviceType expected", "ACB", sizingAcb.deviceType)
        assertEquals("ACB default clearing time must be 0.30s", 0.30, sizingAcb.clearingTime_s, 1e-6)
    }

    @Test
    fun testC2_sizingResultCarriesClearingTime() {
        val circuit = CircuitEntity(
            id = 10,
            circuitName = "TEST-FEEDER",
            loadKVA = 50.0,
            voltageV = 400,
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            installation = "Laid in Ducts",
            parallelRuns = 1,
            airTempC = 40,
            gndTempC = 40,
            burialDepth_m = 0.6,
            soilRes = 125,
            maxVDpct = 0.04,
            length_m = 25.0
        )
        val sizing = CableSizingEngine.sizeCircuit(circuit, 0.0)
        assertEquals("✓ PASS", sizing.status)
        assertTrue("Clearing time must be positive", sizing.clearingTime_s > 0.0)
        assertEquals("MCCB expected 0.10s", 0.10, sizing.clearingTime_s, 1e-6)
    }

    @Test
    fun testC3_downstreamEnginesConsumeSizingResultClearingTime() {
        val circuit = CircuitEntity(
            id = 20,
            circuitName = "FEEDER-20",
            loadKVA = 100.0,
            voltageV = 400,
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            installation = "Laid in Ducts",
            parallelRuns = 1,
            airTempC = 40,
            gndTempC = 40,
            burialDepth_m = 0.6,
            soilRes = 125,
            maxVDpct = 0.04,
            length_m = 50.0
        )
        val sizing = CableSizingEngine.sizeCircuit(circuit, 0.0)
        assertEquals("✓ PASS", sizing.status)
        assertEquals(0.10, sizing.clearingTime_s, 1e-6)

        // ShortCircuitEngine Iw check: Iw = (k * S) / sqrt(clearingTime_s) / 1000.0
        val txResult = TransformerResult(
            connectedLoad = 200.0,
            circuitsAssigned = 1,
            realPower = 160.0,
            demandLoad = 160.0,
            diversifiedLoad = 160.0,
            futureLoad = 160.0,
            designLoad = 160.0,
            requiredKVA = 200.0,
            recommendedSize = 200.0,
            selectedSize = 200.0,
            loadingPct = 80.0,
            flc = 288.68,
            zt = 32.0,
            rt = 3.18,
            xt = 31.84,
            isc3Phase = 7.58,
            isc1Phase = 6.56,
            status = "VERIFIED",
            z0 = 30.4,
            vectorGroup = "Dyn11",
            voltage = 400.0
        )
        val circuitWithSource = circuit.copy(fedFrom = "Transformer", transformerId = "TX-01")
        val scMap = ShortCircuitEngine.calculate(
            resolvedCircuits = listOf(circuitWithSource),
            circuitParents = emptyMap(),
            sizingResults = mapOf(circuit.id to sizing),
            transformerResults = mapOf("TX-01" to txResult)
        )
        val sc = scMap[circuit.id]!!
        val cableMm2 = sizing.conductorAreaMm2 ?: 0.0
        val expectedIwKA = (sizing.kFactor * cableMm2) / sqrt(sizing.clearingTime_s) / 1000.0
        assertEquals("ShortCircuitEngine must use sizing.clearingTime_s for Iw", expectedIwKA, sc.iwKA, 0.02)

        // ProtectionEngine adiabatic withstand check: k²S² >= Isc² * t
        val prot = ProtectionEngine.evaluateProtection(
            circuit = circuit,
            sizingResult = sizing,
            shortCircuitResult = sc
        )
        val s = sizing.conductorAreaMm2 ?: 0.0
        val k = sizing.kFactor
        val k2S2 = (k * s) * (k * s)
        val iscA = sc.iscKA * 1000.0
        val isc2t = iscA * iscA * sizing.clearingTime_s
        val expectedWithstandPass = k2S2 >= isc2t - 0.001
        val expectedStatus = if (expectedWithstandPass) "PASS" else "FAIL"
        assertEquals("ProtectionEngine must use sizing.clearingTime_s for adiabatic check", expectedStatus, prot.cableWithstandStatus)
    }
}
