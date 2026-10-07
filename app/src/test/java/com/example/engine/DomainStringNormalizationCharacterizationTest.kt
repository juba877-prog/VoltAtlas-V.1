package com.example.engine

import com.example.data.Catalog
import com.example.data.CircuitEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import java.io.File
import kotlin.math.sqrt

/**
 * Characterization test suite for KT-06: String-Typed Domain State Governing Engineering Logic.
 *
 * Demonstrates the existing raw-string branching behavior in CableSizingEngine:
 * 1. String-based phase detection: `circuit.phases.trim().uppercase().contains("3P")`
 *    - "3P+N" -> evaluates to true (3-phase Ib = S / (sqrt(3)*V))
 *    - "3-Phase", "3 Phase", "Three-phase" -> evaluates to false, silently falling back to 1-phase (Ib = S / V)
 * 2. Prefix-based harmonic factor check: `circuit.phases.trim().startsWith("3")`
 *    - "3P+N" with isHarmonic=true -> factor 0.86 applied
 *    - "Three-Phase" with isHarmonic=true -> factor 1.0 applied (prefix check misses)
 * 3. Exact-match catalog lookups for cableType:
 *    - "Cu/XLPE/SWA/PVC" -> matched in catalog
 *    - "Copper/XLPE/SWA/PVC" -> fails with CATALOG_MISSING
 *    - "Aluminum/XLPE/STA/PVC" -> fails with CATALOG_MISSING
 */
class DomainStringNormalizationCharacterizationTest {

    companion object {
        @JvmField
        @ClassRule
        val catalogStateReset = com.example.testing.CatalogStateResetRule()

        private fun findAssetFile(filename: String): File {
            val candidates = listOf(
                File("app/src/main/assets/$filename"),
                File("src/main/assets/$filename"),
                File("../app/src/main/assets/$filename")
            )
            return candidates.firstOrNull { it.exists() }
                ?: error("Asset not found: $filename")
        }

        private inline fun <reified T> loadCatalogArray(filename: String, map: (org.json.JSONObject) -> T): List<T> {
            val arr = org.json.JSONArray(findAssetFile(filename).readText(Charsets.UTF_8))
            return (0 until arr.length()).map { map(arr.getJSONObject(it)) }
        }

        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            Catalog.cableTypes = loadCatalogArray("cable_types.json") { obj ->
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
            Catalog.dataSheet = loadCatalogArray("data_sheet.json") { obj ->
                com.example.data.DataSheetEntry(
                    size = obj.getDouble("size"),
                    R_Cu20 = if (obj.has("R_Cu20")) obj.getDouble("R_Cu20") else obj.getDouble("R_Cu70"),
                    X = obj.getDouble("X"),
                    R_Al20 = if (obj.has("R_Al20")) obj.getDouble("R_Al20") else obj.getDouble("R_Al70"),
                    R_Cu_70_est = if (obj.has("R_Cu_70_est")) obj.getDouble("R_Cu_70_est") else obj.getDouble("R_Cu90"),
                    R_Al_70_est = if (obj.has("R_Al_70_est")) obj.getDouble("R_Al_70_est") else obj.getDouble("R_Al90")
                )
            }
            Catalog.cableCatalog = loadCatalogArray("cable_catalog.json") { obj ->
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
            Catalog.breakers = loadCatalogArray("breaker_catalog.json") { obj ->
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
            Catalog.deratingData = org.json.JSONObject(findAssetFile("derating_data.json").readText(Charsets.UTF_8))
            Catalog.invalidateIndexes()
        }
    }

    private fun baseCircuit(phases: String = "3P+N", cableType: String = "Cu/XLPE/SWA/PVC - 4C"): CircuitEntity =
        CircuitEntity(
            id = 1,
            projectId = "p1",
            circuitName = "C1",
            loadKVA = 100.0,
            fedFrom = "MDB",
            phases = phases,
            voltageV = 400,
            pf = 0.8,
            safetyFactor = 1.0,
            cableType = cableType,
            installation = "Laid in Ducts",
            length_m = 50.0
        )

    @Test
    fun testPhaseStringVariantVulnerability_contains3P() {
        // Standard "3P+N" correctly triggers 3-phase calculation:
        // Ib = 100 * 1000 / (sqrt(3) * 400) = 144.3375 A
        val stdCircuit = baseCircuit(phases = "3P+N")
        val stdResult = CableSizingEngine.sizeCircuit(stdCircuit)
        val expected3pIb = 100.0 * 1000.0 / (sqrt(3.0) * 400.0)
        assertEquals(expected3pIb, stdResult.Ib_A, 0.01)

        // Non-standard string "3-Phase" does NOT contain "3P":
        // Silently falls back to 1-phase calculation: Ib = 100 * 1000 / 400 = 250.0 A
        val variantCircuit1 = baseCircuit(phases = "3-Phase")
        val variantResult1 = CableSizingEngine.sizeCircuit(variantCircuit1)
        val expected1pIb = 100.0 * 1000.0 / 400.0
        assertEquals(expected1pIb, variantResult1.Ib_A, 0.01)

        // "3 Phase" also lacks "3P" and falls back to 1-phase
        val variantCircuit2 = baseCircuit(phases = "3 Phase")
        val variantResult2 = CableSizingEngine.sizeCircuit(variantCircuit2)
        assertEquals(expected1pIb, variantResult2.Ib_A, 0.01)

        // "Three-phase" also lacks "3P" and falls back to 1-phase
        val variantCircuit3 = baseCircuit(phases = "Three-phase")
        val variantResult3 = CableSizingEngine.sizeCircuit(variantCircuit3)
        assertEquals(expected1pIb, variantResult3.Ib_A, 0.01)
    }

    @Test
    fun testHarmonicPhaseFactorVulnerability_startsWith3() {
        val nonHarmonic = baseCircuit(phases = "3P+N").copy(isHarmonic = false, overrideCableSize = "50", loadKVA = 60.0, length_m = 25.0)
        val harmonicCircuit3P = nonHarmonic.copy(isHarmonic = true)
        val harmonicCircuitThree = nonHarmonic.copy(isHarmonic = true, phases = "Three-phase")

        val resultNonHarmonic = CableSizingEngine.sizeCircuit(nonHarmonic)
        val result3P = CableSizingEngine.sizeCircuit(harmonicCircuit3P)
        val resultThree = CableSizingEngine.sizeCircuit(harmonicCircuitThree)

        // "3P+N" starts with "3" -> 0.86 harmonic factor applied:
        assertEquals(resultNonHarmonic.capacity_derated_Iz_A * 0.86, result3P.capacity_derated_Iz_A, 0.5)

        // "Three-phase" does NOT start with "3" -> harmonic factor is NOT applied (equals non-harmonic Iz):
        assertEquals(resultNonHarmonic.capacity_derated_Iz_A, resultThree.capacity_derated_Iz_A, 0.5)
    }

    @Test
    fun testConductorMaterialSynonymsVulnerability_catalogLookup() {
        // "Cu/XLPE/SWA/PVC - 4C" matches catalog exactly
        val cuCircuit = baseCircuit(cableType = "Cu/XLPE/SWA/PVC - 4C")
        val cuResult = CableSizingEngine.sizeCircuit(cuCircuit)
        assertEquals(SizingResultStatus.PASS, cuResult.resultStatus)
        assertEquals("Cu", cuResult.cableMaterial)

        // "Copper/XLPE/SWA/PVC - 4C" does NOT match catalog key "Cu/XLPE/SWA/PVC - 4C"
        val copperCircuit = baseCircuit(cableType = "Copper/XLPE/SWA/PVC - 4C")
        val copperResult = CableSizingEngine.sizeCircuit(copperCircuit)
        assertEquals(SizingResultStatus.FAIL, copperResult.resultStatus)
        assertEquals(CableFailureCode.CATALOG_MISSING, copperResult.primaryFailureCode)

        // "Aluminum/XLPE/STA/PVC - 4C" does NOT match catalog key "Al/XLPE/STA/PVC - 4C"
        val alCircuit = baseCircuit(cableType = "Aluminum/XLPE/STA/PVC - 4C")
        val alResult = CableSizingEngine.sizeCircuit(alCircuit)
        assertEquals(SizingResultStatus.FAIL, alResult.resultStatus)
        assertEquals(CableFailureCode.CATALOG_MISSING, alResult.primaryFailureCode)
    }
}
