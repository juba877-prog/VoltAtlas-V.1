package com.example.engine

import com.example.data.Catalog
import com.example.data.CircuitEntity
import com.example.data.ConduitCatalogEntry
import com.example.data.DomainNormalization
import com.example.data.DomainNormalization.normalized
import com.example.engine.ConduitFillEngine
import com.example.engine.ConduitFillInput
import com.example.engine.ConduitFillResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import java.io.File
import kotlin.math.sqrt

/**
 * KT-06 Target Test:
 * Proves that non-standard but valid engineering domain strings:
 * - Phases: "3-Phase", "3 Phase", "Three-Phase", "3P+N", "3P" -> resolve to canonical 3-phase calculation
 * - Material: "Cu", "Copper", "cu", "COPPER" -> resolve to "Cu"
 * - Material: "Al", "Aluminum", "Aluminium", "al" -> resolve to "Al"
 * - Insulation: "XLPE", "xlpe" -> resolve to "XLPE"
 * - Insulation: "PVC", "pvc" -> resolve to "PVC"
 * - Conduit: "PVC", "Rigid PVC", "EMT", "EMT Steel", "GI", "GI Steel" -> resolve to catalog conduit types
 *
 * Proves that normalizing at entity/ingestion boundaries protects frozen Tier-F engines
 * from silent calculation divergence.
 */
class DomainStringNormalizationTargetTest {

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
            Catalog.conduits = loadCatalogArray("conduit_catalog.json") { obj ->
                ConduitCatalogEntry(
                    id = obj.getString("id"),
                    conduitType = obj.getString("conduitType"),
                    tradeSize = obj.getString("tradeSize"),
                    internalDiameterMm = obj.getDouble("internalDiameterMm")
                )
            }
            Catalog.invalidateIndexes()
        }
    }

    private fun baseCircuit(phases: String, cableType: String): CircuitEntity =
        CircuitEntity(
            id = 10,
            projectId = "p1",
            circuitName = "C10",
            loadKVA = 60.0,
            fedFrom = "MDB",
            phases = phases,
            voltageV = 400,
            pf = 0.8,
            safetyFactor = 1.0,
            cableType = cableType,
            installation = "Laid in Ducts",
            overrideCableSize = "50",
            length_m = 25.0
        )

    @Test
    fun testPhaseNormalization_resolvesToThreePhaseCalculation() {
        val threePhaseVariants = listOf(
            "3P+N",
            "3-Phase",
            "3 Phase",
            "Three-Phase",
            "THREE PHASE",
            "3ph+N",
            "3P",
            "3-phase 4-wire"
        )
        val expected3pIb = 60.0 * 1000.0 / (sqrt(3.0) * 400.0)

        for (variant in threePhaseVariants) {
            val normalizedPhase = DomainNormalization.normalizePhases(variant)
            assertTrue("'$variant' should normalize to 3P representation, got '$normalizedPhase'",
                normalizedPhase.contains("3P"))

            val circuit = baseCircuit(phases = variant, cableType = "Cu/XLPE/SWA/PVC - 4C").normalized()
            val result = CableSizingEngine.sizeCircuit(circuit)
            assertEquals("Ib for '$variant' must resolve to 3-phase calculation",
                expected3pIb, result.Ib_A, 0.01)
        }
    }

    @Test
    fun testSinglePhaseNormalization_resolvesToSinglePhaseCalculation() {
        val singlePhaseVariants = listOf(
            "1P+N",
            "1-Phase",
            "1 Phase",
            "Single-Phase",
            "SINGLE PHASE",
            "1ph",
            "1P"
        )
        val expected1pIb = 60.0 * 1000.0 / 400.0

        for (variant in singlePhaseVariants) {
            val normalizedPhase = DomainNormalization.normalizePhases(variant)
            assertTrue("'$variant' should normalize to 1P representation, got '$normalizedPhase'",
                normalizedPhase.startsWith("1P"))

            val circuit = baseCircuit(phases = variant, cableType = "Cu/XLPE/SWA/PVC - 4C").normalized()
            val result = CableSizingEngine.sizeCircuit(circuit)
            assertEquals("Ib for '$variant' must resolve to 1-phase calculation",
                expected1pIb, result.Ib_A, 0.01)
        }
    }

    @Test
    fun testHarmonicPhaseNormalization_appliesDeratingAcrossVariants() {
        val nonHarmonic = baseCircuit(phases = "3P+N", cableType = "Cu/XLPE/SWA/PVC - 4C").normalized()
        val baseIz = CableSizingEngine.sizeCircuit(nonHarmonic).capacity_derated_Iz_A

        val harmonicVariants = listOf("3P+N", "3-Phase", "Three-Phase", "3 Phase")
        for (variant in harmonicVariants) {
            val circuit = baseCircuit(phases = variant, cableType = "Cu/XLPE/SWA/PVC - 4C")
                .copy(isHarmonic = true)
                .normalized()
            val result = CableSizingEngine.sizeCircuit(circuit)
            assertEquals("Harmonic Iz for '$variant' must be scaled by 0.86",
                baseIz * 0.86, result.capacity_derated_Iz_A, 0.5)
        }
    }

    @Test
    fun testMaterialNormalization_cuSynonymsResolveToCopper() {
        val cuSynonyms = listOf("Cu", "Copper", "cu", "COPPER")
        for (synonym in cuSynonyms) {
            assertEquals("Cu", DomainNormalization.normalizeConductorMaterial(synonym))
        }

        // Test cableType string with Copper
        val rawCableType = "Copper/XLPE/SWA/PVC - 4C"
        val normalizedCableType = DomainNormalization.normalizeCableType(rawCableType)
        assertEquals("Cu/XLPE/SWA/PVC - 4C", normalizedCableType)

        val circuit = baseCircuit(phases = "3P+N", cableType = rawCableType).normalized()
        val result = CableSizingEngine.sizeCircuit(circuit)
        assertEquals(SizingResultStatus.PASS, result.resultStatus)
        assertEquals("Cu", result.cableMaterial)
        assertEquals("XLPE", result.insulation)
        assertEquals(143.0, result.kFactor, 0.1)
    }

    @Test
    fun testMaterialNormalization_alSynonymsResolveToAluminum() {
        val alSynonyms = listOf("Al", "Aluminum", "Aluminium", "al", "ALUMINUM")
        for (synonym in alSynonyms) {
            assertEquals("Al", DomainNormalization.normalizeConductorMaterial(synonym))
        }

        // Test cableType string with Aluminum
        val rawCableType = "Aluminum/XLPE/STA/PVC - 4C"
        val normalizedCableType = DomainNormalization.normalizeCableType(rawCableType)
        assertEquals("Al/XLPE/STA/PVC - 4C", normalizedCableType)

        val circuit = baseCircuit(phases = "3P+N", cableType = rawCableType).normalized()
        val result = CableSizingEngine.sizeCircuit(circuit)
        assertEquals(SizingResultStatus.PASS, result.resultStatus)
        assertEquals("Al", result.cableMaterial)
        assertEquals("XLPE", result.insulation)
        assertEquals(94.0, result.kFactor, 0.1)
    }

    @Test
    fun testInsulationNormalization_xlpeAndPvc() {
        val xlpeVariants = listOf("XLPE", "xlpe", "Cross-linked Polyethylene", "Cross-Linked")
        for (variant in xlpeVariants) {
            assertEquals("XLPE", DomainNormalization.normalizeInsulation(variant))
        }

        val pvcVariants = listOf("PVC", "pvc", "Polyvinyl Chloride")
        for (variant in pvcVariants) {
            assertEquals("PVC", DomainNormalization.normalizeInsulation(variant))
        }

        // Test cableType with lowercase xlpe / pvc
        assertEquals("Cu/XLPE/SWA/PVC - 4C", DomainNormalization.normalizeCableType("Cu/xlpe/swa/pvc - 4C"))
        assertEquals("Cu/PVC/PVC - 4C", DomainNormalization.normalizeCableType("copper/pvc/pvc - 4C"))
    }

    @Test
    fun testConduitTypeNormalization_catalogEquivalence() {
        assertEquals("Rigid PVC", DomainNormalization.normalizeConduitType("PVC"))
        assertEquals("Rigid PVC", DomainNormalization.normalizeConduitType("rigid pvc"))
        assertEquals("Rigid PVC", DomainNormalization.normalizeConduitType("Rigid-PVC"))
        assertEquals("Rigid PVC", DomainNormalization.normalizeConduitType("Rigid PVC"))

        assertEquals("EMT Steel", DomainNormalization.normalizeConduitType("EMT"))
        assertEquals("EMT Steel", DomainNormalization.normalizeConduitType("emt"))
        assertEquals("EMT Steel", DomainNormalization.normalizeConduitType("EMT Steel"))

        assertEquals("GI Steel (RMC)", DomainNormalization.normalizeConduitType("GI"))
        assertEquals("GI Steel (RMC)", DomainNormalization.normalizeConduitType("gi"))
        assertEquals("GI Steel (RMC)", DomainNormalization.normalizeConduitType("GI Steel"))
        assertEquals("GI Steel (RMC)", DomainNormalization.normalizeConduitType("RMC"))
        assertEquals("GI Steel (RMC)", DomainNormalization.normalizeConduitType("GI Steel (RMC)"))
    }

    @Test
    fun testConduitTypeNormalization_authoritativeEngineFiltering() {
        val testCatalog = Catalog.conduits
        assertTrue("Conduit catalog must be loaded", testCatalog.isNotEmpty())

        fun runConduit(conduitTypeInput: String, mainCableOD: Double = 12.0, cpcOD: Double = 6.0): ConduitFillResult {
            val normalizedType = DomainNormalization.normalizeConduitType(conduitTypeInput)
            val input = ConduitFillInput(
                circuitId = 101,
                circuitName = "TEST_CONDUIT",
                mainCableOD_mm = mainCableOD,
                manualCpcOD_mm = cpcOD,
                cpcArrangement = "SEPARATE_CONDUIT",
                designProfile = "PROJECT_DESIGN",
                conduitType = normalizedType
            )
            return ConduitFillEngine.calculate(input, testCatalog)
        }

        // A) Raw "PVC" -> canonical "Rigid PVC" -> engine
        val pvcResult = runConduit("PVC")
        assertTrue("Recommended conduit for 'PVC' must be a PVC catalog item, got: ${pvcResult.recommendedConduitId}",
            pvcResult.recommendedConduitId.startsWith("PVC_"))
        assertFalse("Recommended conduit for 'PVC' must not be EMT, got: ${pvcResult.recommendedConduitId}",
            pvcResult.recommendedConduitId.startsWith("EMT_"))
        assertFalse("Recommended conduit for 'PVC' must not be GI, got: ${pvcResult.recommendedConduitId}",
            pvcResult.recommendedConduitId.startsWith("GI_"))
        assertEquals("Rigid PVC", testCatalog.first { it.id == pvcResult.recommendedConduitId }.conduitType)

        // B) Canonical "Rigid PVC" -> engine (identical material isolation)
        val rigidPvcResult = runConduit("Rigid PVC")
        assertTrue("Recommended conduit for 'Rigid PVC' must be a PVC item",
            rigidPvcResult.recommendedConduitId.startsWith("PVC_"))
        assertEquals(pvcResult.recommendedConduitId, rigidPvcResult.recommendedConduitId)

        // C) EMT variants: "EMT", "emt", "EMT Steel"
        val emtVariants = listOf("EMT", "emt", "EMT Steel")
        for (variant in emtVariants) {
            val emtResult = runConduit(variant)
            assertTrue("Recommended conduit for '$variant' must be an EMT item, got: ${emtResult.recommendedConduitId}",
                emtResult.recommendedConduitId.startsWith("EMT_"))
            assertFalse("Recommended conduit for '$variant' must not be PVC",
                emtResult.recommendedConduitId.startsWith("PVC_"))
            assertFalse("Recommended conduit for '$variant' must not be GI",
                emtResult.recommendedConduitId.startsWith("GI_"))
            assertEquals("EMT Steel", testCatalog.first { it.id == emtResult.recommendedConduitId }.conduitType)
        }

        // D) GI/RMC variants: "GI", "gi", "RMC", "GI Steel (RMC)"
        val giVariants = listOf("GI", "gi", "RMC", "GI Steel", "GI Steel (RMC)")
        for (variant in giVariants) {
            val giResult = runConduit(variant)
            assertTrue("Recommended conduit for '$variant' must be a GI item, got: ${giResult.recommendedConduitId}",
                giResult.recommendedConduitId.startsWith("GI_"))
            assertFalse("Recommended conduit for '$variant' must not be PVC",
                giResult.recommendedConduitId.startsWith("PVC_"))
            assertFalse("Recommended conduit for '$variant' must not be EMT",
                giResult.recommendedConduitId.startsWith("EMT_"))
            assertEquals("GI Steel (RMC)", testCatalog.first { it.id == giResult.recommendedConduitId }.conduitType)
        }

        // E) Regression boundary: mixed catalog where defective engine previously picked smaller EMT
        val mixedCatalog = listOf(
            ConduitCatalogEntry("PVC_20", "Rigid PVC", "20 mm", 15.8),
            ConduitCatalogEntry("GI_20", "GI Steel (RMC)", "20 mm GI", 17.2),
            ConduitCatalogEntry("EMT_20", "EMT Steel", "20 mm EMT", 20.9),
            ConduitCatalogEntry("PVC_25", "Rigid PVC", "25 mm", 21.0)
        )
        val boundaryInput = ConduitFillInput(
            circuitId = 102,
            circuitName = "REGRESSION_BOUNDARY",
            mainCableOD_mm = 6.0,
            manualCpcOD_mm = 9.2,
            cpcArrangement = "SEPARATE_CONDUIT",
            designProfile = "PROJECT_DESIGN",
            conduitType = DomainNormalization.normalizeConduitType("PVC")
        )
        val boundaryResult = ConduitFillEngine.calculate(boundaryInput, mixedCatalog)
        assertEquals("P1-08 / ECT-D-EE-11 regression proof: 'PVC' request must pick PVC_25, not EMT_20",
            "PVC_25", boundaryResult.recommendedConduitId)
        assertEquals("25 mm", boundaryResult.recommendedTradeSize)
    }
}
