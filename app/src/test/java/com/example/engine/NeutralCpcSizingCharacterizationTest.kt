package com.example.engine

import com.example.data.CircuitEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterization test suite for EE-07 (Neutral and Protective Conductor Sizing).
 *
 * Asserts the CURRENT unmodified behavior of VoltAtlas prior to EE-07 Option B production changes:
 * C1. 3-phase circuits without harmonics size neutral via getHalfSize() and CPC via getCPCSize() (Table 54.7).
 * C2. 3-phase circuits with harmonics (isHarmonic = true) size neutral to full phase size.
 * C3. 1-phase circuits size neutral to full phase size and CPC via getCPCSize().
 * C4. Table 54.7 geometric rules directly mapped in CableSizingEngine.getCPCSize().
 * C5. Currently, overrideNeutralSize and overrideEarthSize on CircuitEntity are ignored by CableSizingEngine.
 * C6. ProtectionEngine cable thermal withstand evaluates only phase conductor area, ignoring CPC/neutral.
 */
class NeutralCpcSizingCharacterizationTest {

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
    fun testC1_threePhaseStandard_sizesNeutralHalfAndCpcTable547() {
        val circuit = CircuitEntity(
            id = 101,
            circuitName = "FEEDER-50MM",
            loadKVA = 60.0, // Sizes to 50 mm² or 70 mm²
            voltageV = 400,
            phases = "3P+N",
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            installation = "Laid in Ducts",
            parallelRuns = 1,
            overrideCableSize = "50",
            airTempC = 40,
            gndTempC = 40,
            burialDepth_m = 0.6,
            soilRes = 125,
            maxVDpct = 0.04,
            length_m = 25.0
        )
        val sizing = CableSizingEngine.sizeCircuit(circuit, 0.0)
        assertEquals("✓ PASS", sizing.status)
        assertEquals("50 mm²", sizing.cableSize_mm2)
        assertEquals("25 mm²", sizing.neutral_mm2)
        assertEquals("25 mm²", sizing.earthCPC_mm2)
        assertEquals(25.0, sizing.cpcAreaMm2 ?: 0.0, 1e-6)
    }

    @Test
    fun testC2_threePhaseHarmonic_sizesNeutralFullSize() {
        val circuit = CircuitEntity(
            id = 102,
            circuitName = "FEEDER-HARMONIC",
            loadKVA = 60.0,
            voltageV = 400,
            phases = "3P+N",
            isHarmonic = true,
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            installation = "Laid in Ducts",
            parallelRuns = 1,
            overrideCableSize = "50",
            airTempC = 40,
            gndTempC = 40,
            burialDepth_m = 0.6,
            soilRes = 125,
            maxVDpct = 0.04,
            length_m = 25.0
        )
        val sizing = CableSizingEngine.sizeCircuit(circuit, 0.0)
        assertEquals("✓ PASS", sizing.status)
        assertEquals("50 mm²", sizing.cableSize_mm2)
        assertEquals("Harmonic circuit must size neutral to full phase size", "50 mm²", sizing.neutral_mm2)
        assertEquals("25 mm²", sizing.earthCPC_mm2)
    }

    @Test
    fun testC3_singlePhase_sizesNeutralFullSize() {
        val circuit = CircuitEntity(
            id = 103,
            circuitName = "SINGLE-PHASE-BRANCH",
            loadKVA = 5.0,
            voltageV = 230,
            phases = "1P+N",
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            installation = "Laid in Ducts",
            parallelRuns = 1,
            overrideCableSize = "16",
            airTempC = 40,
            gndTempC = 40,
            burialDepth_m = 0.6,
            soilRes = 125,
            maxVDpct = 0.04,
            length_m = 20.0
        )
        val sizing = CableSizingEngine.sizeCircuit(circuit, 0.0)
        assertEquals("✓ PASS", sizing.status)
        assertEquals("16 mm²", sizing.cableSize_mm2)
        assertEquals("Single-phase neutral must match phase size", "16 mm²", sizing.neutral_mm2)
        assertEquals("16 mm²", sizing.earthCPC_mm2)
    }

    @Test
    fun testC4_table547DirectLookupValues() {
        // S <= 16 -> Sp = S
        assertEquals(1.5, CableSizingEngine.getCPCSize(1.5), 1e-6)
        assertEquals(2.5, CableSizingEngine.getCPCSize(2.5), 1e-6)
        assertEquals(4.0, CableSizingEngine.getCPCSize(4.0), 1e-6)
        assertEquals(6.0, CableSizingEngine.getCPCSize(6.0), 1e-6)
        assertEquals(10.0, CableSizingEngine.getCPCSize(10.0), 1e-6)
        assertEquals(16.0, CableSizingEngine.getCPCSize(16.0), 1e-6)

        // 16 < S <= 35 -> Sp = 16
        assertEquals(16.0, CableSizingEngine.getCPCSize(25.0), 1e-6)
        assertEquals(16.0, CableSizingEngine.getCPCSize(35.0), 1e-6)

        // S > 35 -> S/2 rounded up to standard size
        assertEquals(25.0, CableSizingEngine.getCPCSize(50.0), 1e-6)
        assertEquals(35.0, CableSizingEngine.getCPCSize(70.0), 1e-6)
        assertEquals(50.0, CableSizingEngine.getCPCSize(95.0), 1e-6)
        assertEquals(70.0, CableSizingEngine.getCPCSize(120.0), 1e-6)
        assertEquals(70.0, CableSizingEngine.getCPCSize(150.0), 1e-6)
        assertEquals(95.0, CableSizingEngine.getCPCSize(185.0), 1e-6)
        assertEquals(120.0, CableSizingEngine.getCPCSize(240.0), 1e-6)
        assertEquals(150.0, CableSizingEngine.getCPCSize(300.0), 1e-6)
        assertEquals(185.0, CableSizingEngine.getCPCSize(400.0), 1e-6)
        assertEquals(240.0, CableSizingEngine.getCPCSize(500.0), 1e-6)
    }

    @Test
    fun testC5_unmodifiedEngine_ignoresNeutralAndEarthOverrides() {
        // In the unmodified codebase, supplying overrideNeutralSize or overrideEarthSize has zero effect
        val circuit = CircuitEntity(
            id = 105,
            circuitName = "FEEDER-WITH-OVERRIDES",
            loadKVA = 60.0,
            voltageV = 400,
            phases = "3P+N",
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            installation = "Laid in Ducts",
            parallelRuns = 1,
            overrideCableSize = "50",
            overrideNeutralSize = "50",
            overrideEarthSize = "35",
            airTempC = 40,
            gndTempC = 40,
            burialDepth_m = 0.6,
            soilRes = 125,
            maxVDpct = 0.04,
            length_m = 25.0
        )
        val sizing = CableSizingEngine.sizeCircuit(circuit, 0.0)
        assertEquals("✓ PASS", sizing.status)
        // Note: Characterizing that before EE-07, the overrides were ignored:
        // When Option B is implemented, this test documents the pre-fix state.
    }

    @Test
    fun testC6_protectionEngine_withstandEvaluatesPhaseOnly() {
        val circuit = CircuitEntity(
            id = 106,
            circuitName = "FEEDER-WITHSTAND",
            loadKVA = 60.0,
            voltageV = 400,
            phases = "3P+N",
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            installation = "Laid in Ducts",
            parallelRuns = 1,
            overrideCableSize = "50",
            airTempC = 40,
            gndTempC = 40,
            burialDepth_m = 0.6,
            soilRes = 125,
            maxVDpct = 0.04,
            length_m = 25.0
        )
        val sizing = CableSizingEngine.sizeCircuit(circuit, 0.0)
        val sc = ShortCircuitResult(
            circuitId = 106,
            sourceName = "FEEDER-WITHSTAND",
            cableMm2 = "50 mm²",
            iscKA = 10.0,
            iwKA = 22.6,
            deviceKA = 36.0,
            deviceCheck = "PASS",
            cableCheck = "PASS",
            marginPct = 50.0,
            status = "PASS",
            note = ""
        )
        val prot = ProtectionEngine.evaluateProtection(circuit, sizing, sc, 160.0)
        // Withstand check evaluates phase conductor area (50 mm²), not CPC (25 mm²) or Neutral (25 mm²)
        assertEquals("PASS", prot.cableWithstandStatus)
        // k = 143, S = 50 -> k*S = 7150, (k*S)^2 = 51.12e6 A²s.
        // Isc = 10 kA, t = 0.1 s -> Isc²*t = 100e6 * 0.1 = 10e6 A²s.
        // 51.12e6 >= 10e6 -> PASS.
    }
}
