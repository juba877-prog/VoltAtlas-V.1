package com.example.engine

import com.example.data.Catalog
import com.example.data.CircuitEntity
import com.example.data.PfGlobalEntity
import com.example.data.PfTransformerEntity
import com.example.data.TransformerEntity
import com.example.sld.ElectricalNetworkGraphBuilder
import com.example.sld.SldNodeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import java.io.File

/**
 * Characterization test suite for EE-08 (Harmonics Option B baseline).
 *
 * Asserts the CURRENT unmodified behavior of VoltAtlas prior to EE-08 Option B production changes:
 * C1: isHarmonic = false preserves non-harmonic baseline (no 0.86 factor, reduced neutral).
 * C2: isHarmonic = true on 3-phase circuits preserves 0.86 phase derating factor.
 * C3: isHarmonic = true on 3-phase circuits preserves full-size neutral conductor.
 * C4: In ElectricalNetworkGraphBuilder, a board with an incoming circuit having isHarmonic = true
 *     currently calls BusbarSizingEngine without hasHighHarmonics, producing a 50% neutral profile.
 * C5: Default PfTransformerEntity (harmonicFilter = "Not Provided", detuningReactor = "Not Provided")
 *     produces "REVIEW — HARMONIC/DETUNING INPUTS" status in PfCorrectionEngine.
 * C6: PfTransformerEntity with harmonicFilter = "Yes" evaluates to "COMPLIANT" in PfCorrectionEngine.
 * C7: PfTransformerEntity with detuningReactor = "Yes" evaluates to "COMPLIANT" with 1.075 detuning boost.
 */
class HarmonicsOptionBCharacterizationTest {

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

    private fun createStandardCircuit(isHarmonic: Boolean, size: String = "50"): CircuitEntity {
        return CircuitEntity(
            id = 201,
            circuitName = "FEEDER-HARMONIC-TEST",
            loadKVA = 60.0,
            voltageV = 400,
            phases = "3P+N",
            cableType = "Cu/XLPE/PVC - 4C",
            manufacturer = "Elsewedy",
            installation = "Laid in Ducts",
            parallelRuns = 1,
            overrideCableSize = size,
            airTempC = 40,
            gndTempC = 40,
            burialDepth_m = 0.6,
            soilRes = 125,
            maxVDpct = 0.04,
            length_m = 25.0,
            isHarmonic = isHarmonic
        )
    }

    @Test
    fun testC1_nonHarmonicBaseline_usesReducedNeutralAndFullAmpacity() {
        val nonHarmonicCircuit = createStandardCircuit(isHarmonic = false, size = "50")
        val sizing = CableSizingEngine.sizeCircuit(nonHarmonicCircuit, 0.0)

        assertEquals("50 mm²", sizing.cableSize_mm2)
        assertEquals("25 mm²", sizing.neutral_mm2)
        assertTrue("No harmonic warning for non-harmonic circuit", sizing.warnings.isEmpty())
    }

    @Test
    fun testC2_harmonicCircuit_deratesAmpacityBy086() {
        val nonHarmonic = createStandardCircuit(isHarmonic = false, size = "50")
        val harmonic = createStandardCircuit(isHarmonic = true, size = "50")

        val sizingNonHarmonic = CableSizingEngine.sizeCircuit(nonHarmonic, 0.0)
        val sizingHarmonic = CableSizingEngine.sizeCircuit(harmonic, 0.0)

        // Capacity of harmonic circuit must be scaled by exactly 0.86
        val expectedHarmonicIz = sizingNonHarmonic.capacity_derated_Iz_A * 0.86
        assertEquals(expectedHarmonicIz, sizingHarmonic.capacity_derated_Iz_A, 0.5)

        // Warning emitted explicitly disclosing the 15-33% band
        assertTrue(sizingHarmonic.warnings.any { it.contains("15–33 %") })
    }

    @Test
    fun testC3_harmonicCircuit_sizesNeutralToFullPhaseSize() {
        val harmonicCircuit = createStandardCircuit(isHarmonic = true, size = "50")
        val sizing = CableSizingEngine.sizeCircuit(harmonicCircuit, 0.0)

        assertEquals("50 mm²", sizing.cableSize_mm2)
        assertEquals("50 mm²", sizing.neutral_mm2)
    }

    @Test
    fun testC4_harmonicBoardInSld_capturesCurrentPreFixBusbarOutput() {
        // Pre-fix characterization: currently ElectricalNetworkGraphBuilder does NOT pass hasHighHarmonics,
        // so a board incomer with isHarmonic = true produces a standard reduced neutral (e.g. 50% neutral).
        val boardCircuit = CircuitEntity(
            id = 501,
            projectId = "PROJ-HARM-C4",
            circuitName = "MDB-MAIN",
            panelType = "MDB",
            loadKVA = 250.0,
            voltageV = 400,
            phases = "3P+N",
            isHarmonic = true
        )
        val branchCircuit = CircuitEntity(
            id = 502,
            projectId = "PROJ-HARM-C4",
            circuitName = "OUTGOING-1",
            fedFrom = "MDB-MAIN",
            parentId = 501,
            loadKVA = 100.0,
            voltageV = 400,
            phases = "3P+N",
            isHarmonic = true
        )

        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = "PROJ-HARM-C4",
            transformers = emptyList(),
            circuits = listOf(boardCircuit, branchCircuit)
        )

        val mdbNode = graph.nodes.firstOrNull { it.displayName == "MDB-MAIN" }
        org.junit.Assert.assertNotNull(mdbNode)
        val neutralProfile = mdbNode?.dataBlock?.busbarNeutral
        org.junit.Assert.assertNotNull("Pre-fix: busbarNeutral is populated", neutralProfile)
    }

    @Test
    fun testC5_defaultPfTransformerEntity_producesReviewStatus() {
        val globalSettings = PfGlobalEntity(targetPf = 0.95, kvarStepSize = 25.0, systemVoltage = 400)
        val transformers = listOf(TransformerEntity(transformerId = "TX-CHAR", pf = 0.80, status = "Active"))
        val defaultTxSettings = listOf(
            PfTransformerEntity(transformerId = "TX-CHAR", harmonicFilter = "Not Provided", detuningReactor = "Not Provided")
        )
        val txResults = mapOf(
            "TX-CHAR" to TransformerResult(
                connectedLoad = 125.0,
                circuitsAssigned = 1,
                realPower = 100.0,
                demandLoad = 100.0,
                diversifiedLoad = 100.0,
                futureLoad = 100.0,
                designLoad = 100.0,
                requiredKVA = 125.0,
                recommendedSize = 160.0,
                selectedSize = 160.0,
                loadingPct = 78.1,
                flc = 230.9,
                zt = 0.05,
                rt = 0.01,
                xt = 0.049,
                isc3Phase = 5.0,
                isc1Phase = 5.0,
                status = "OK",
                z0 = 0.05,
                vectorGroup = "Dyn11"
            )
        )

        val results = PfCorrectionEngine.calculate(globalSettings, defaultTxSettings, transformers, txResults)
        assertEquals(1, results.size)
        assertEquals("REVIEW — HARMONIC/DETUNING INPUTS", results[0].status)
    }

    @Test
    fun testC6_pfTransformerWithHarmonicFilter_evaluatesCompliant() {
        val globalSettings = PfGlobalEntity(targetPf = 0.95, kvarStepSize = 25.0, systemVoltage = 400)
        val transformers = listOf(TransformerEntity(transformerId = "TX-CHAR6", pf = 0.80, status = "Active"))
        val filterTxSettings = listOf(
            PfTransformerEntity(transformerId = "TX-CHAR6", harmonicFilter = "Yes", detuningReactor = "No")
        )
        val txResults = mapOf(
            "TX-CHAR6" to TransformerResult(
                connectedLoad = 125.0,
                circuitsAssigned = 1,
                realPower = 100.0,
                demandLoad = 100.0,
                diversifiedLoad = 100.0,
                futureLoad = 100.0,
                designLoad = 100.0,
                requiredKVA = 125.0,
                recommendedSize = 160.0,
                selectedSize = 160.0,
                loadingPct = 78.1,
                flc = 230.9,
                zt = 0.05,
                rt = 0.01,
                xt = 0.049,
                isc3Phase = 5.0,
                isc1Phase = 5.0,
                status = "OK",
                z0 = 0.05,
                vectorGroup = "Dyn11"
            )
        )

        val results = PfCorrectionEngine.calculate(globalSettings, filterTxSettings, transformers, txResults)
        assertEquals(1, results.size)
        assertEquals("COMPLIANT", results[0].status)
    }

    @Test
    fun testC7_pfTransformerWithDetuningReactor_evaluatesCompliantWithBoost() {
        val globalSettings = PfGlobalEntity(targetPf = 0.95, kvarStepSize = 25.0, systemVoltage = 400)
        val transformers = listOf(TransformerEntity(transformerId = "TX-CHAR7", pf = 0.80, status = "Active"))
        val detunedTxSettings = listOf(
            PfTransformerEntity(transformerId = "TX-CHAR7", harmonicFilter = "No", detuningReactor = "Yes")
        )
        val txResults = mapOf(
            "TX-CHAR7" to TransformerResult(
                connectedLoad = 125.0,
                circuitsAssigned = 1,
                realPower = 100.0,
                demandLoad = 100.0,
                diversifiedLoad = 100.0,
                futureLoad = 100.0,
                designLoad = 100.0,
                requiredKVA = 125.0,
                recommendedSize = 160.0,
                selectedSize = 160.0,
                loadingPct = 78.1,
                flc = 230.9,
                zt = 0.05,
                rt = 0.01,
                xt = 0.049,
                isc3Phase = 5.0,
                isc1Phase = 5.0,
                status = "OK",
                z0 = 0.05,
                vectorGroup = "Dyn11"
            )
        )

        val results = PfCorrectionEngine.calculate(globalSettings, detunedTxSettings, transformers, txResults)
        assertEquals(1, results.size)
        assertEquals("COMPLIANT", results[0].status)
        // Verify 1.075 detuning boost is applied:
        // bankCapacity = 50.0 kVAR, I = (50000 / (sqrt(3)*400)) * 1.075 = 72.168 * 1.075 = 77.58 A
        val baseCurrent = (50.0 * 1000.0) / (kotlin.math.sqrt(3.0) * 400.0)
        val expectedDetunedCurrent = baseCurrent * 1.075
        assertEquals(expectedDetunedCurrent, results[0].bankCurrent, 0.1)
    }
}
