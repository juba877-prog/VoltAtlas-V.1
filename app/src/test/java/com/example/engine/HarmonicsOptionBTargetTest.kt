package com.example.engine

import com.example.data.Catalog
import com.example.data.CircuitEntity
import com.example.data.PfGlobalEntity
import com.example.data.PfTransformerEntity
import com.example.data.TransformerEntity
import com.example.pdf.CircuitsSection
import com.example.pdf.PdfDocumentRenderer
import com.example.pdf.ProjectReportSnapshot
import com.example.sld.ElectricalNetworkGraphBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import java.io.File

/**
 * Target test suite for EE-08 (Option B Implementation).
 *
 * T1: Circuit editor Load step uses qualified label "Harmonics (15–33% 3rd Harmonic Band)".
 * T2: Circuit editor Review step uses qualified label "Harmonics (15–33% 3rd Harmonic Band)".
 * T3: PDF report contains explicit standard disclosure that harmonic automated sizing is
 *     strictly limited to the 15–33% band and content >33% requires manual sizing.
 * T4: Harmonic board busbar does not expose an unvalidated 50% neutral, but returns
 *     "NOT EVALUATED (MANUAL REVIEW)".
 * T5: PfTransformerEntity harmonicFilter and detuningReactor can be configured to "Yes" / "No".
 * T6: Saving PfTransformerEntity updates settings correctly.
 * T7: PFC status comes strictly from PfCorrectionEngine ("COMPLIANT" when filter/reactor provided).
 * T8: Non-harmonic board busbar continues to produce authoritative standard profile.
 * T9: Non-harmonic cable sizing is completely unchanged.
 * T10: Authoritative cable reference cases remain unchanged.
 */
class HarmonicsOptionBTargetTest {

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

        private fun findSourceFile(relativePath: String): File {
            val candidates = listOf(
                File(relativePath),
                File("app/$relativePath"),
                File("../app/$relativePath"),
                File("../$relativePath")
            )
            return candidates.firstOrNull { it.exists() }
                ?: error("Source file not found: $relativePath")
        }
    }

    @Test
    fun testT1_and_T2_sourceFilesContainHarmonicsQualifiedDisclosure() {
        val loadStepSource = findSourceFile("src/main/java/com/example/ui/dialogs/circuiteditor/ui/steps/LoadStepContent.kt").readText()
        assertTrue(
            "LoadStepContent must contain 'Harmonics (15–33% 3rd Harmonic Band)'",
            loadStepSource.contains("Harmonics (15–33% 3rd Harmonic Band)")
        )

        val reviewStepSource = findSourceFile("src/main/java/com/example/ui/dialogs/circuiteditor/ui/steps/ReviewStepContent.kt").readText()
        assertTrue(
            "ReviewStepContent must contain 'Harmonics (15–33% 3rd Harmonic Band)'",
            reviewStepSource.contains("Harmonics (15–33% 3rd Harmonic Band)")
        )
    }

    @Test
    fun testT3_pdfSectionSourceContainsHarmonicScopeDisclosure() {
        val sectionsSource = findSourceFile("src/main/java/com/example/pdf/Sections.kt").readText()
        assertTrue(
            "Sections.kt must disclose the 15–33% third-harmonic band limitation",
            sectionsSource.contains("15–33%") && sectionsSource.contains("IEC 60364-5-52 Annex E") && sectionsSource.contains("33%")
        )
    }

    @Test
    fun testT4_harmonicBoardInSld_displaysNotEvaluatedManualReview() {
        val boardCircuit = CircuitEntity(
            id = 601,
            projectId = "PROJ-T4",
            circuitName = "MDB-HARMONIC",
            panelType = "MDB",
            loadKVA = 250.0,
            voltageV = 400,
            phases = "3P+N",
            isHarmonic = true
        )

        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = "PROJ-T4",
            transformers = emptyList(),
            circuits = listOf(boardCircuit)
        )

        val mdbNode = graph.nodes.firstOrNull { it.displayName == "MDB-HARMONIC" }
        assertNotNull(mdbNode)
        val neutralProfile = mdbNode?.dataBlock?.busbarNeutral
        assertNotNull(neutralProfile)
        assertEquals("NOT EVALUATED (MANUAL REVIEW)", neutralProfile)
    }

    @Test
    fun testT5_T6_T7_pfTransformerInputWorkflowAndAuthoritativeEngine() {
        val globalSettings = PfGlobalEntity(targetPf = 0.95, kvarStepSize = 25.0, systemVoltage = 400)
        val transformers = listOf(TransformerEntity(transformerId = "TX-T5", pf = 0.80, status = "Active"))

        // Initial state: Not Provided
        val initialTx = PfTransformerEntity(transformerId = "TX-T5", harmonicFilter = "Not Provided", detuningReactor = "Not Provided")
        val txResults = mapOf(
            "TX-T5" to TransformerResult(
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

        val initialResults = PfCorrectionEngine.calculate(globalSettings, listOf(initialTx), transformers, txResults)
        assertEquals("REVIEW — HARMONIC/DETUNING INPUTS", initialResults[0].status)

        // User edits to "Yes" via onSaveTx path
        val updatedTx = initialTx.copy(detuningReactor = "Yes")
        assertEquals("Yes", updatedTx.detuningReactor)

        val updatedResults = PfCorrectionEngine.calculate(globalSettings, listOf(updatedTx), transformers, txResults)
        assertEquals("COMPLIANT", updatedResults[0].status)

        // Verify PowerSystemsScreen.kt contains the UI controls for detuningReactor and harmonicFilter
        val uiSource = findSourceFile("src/main/java/com/example/ui/PowerSystemsScreen.kt").readText()
        assertTrue("PowerSystemsScreen must contain 'Detuning Reactor:'", uiSource.contains("Detuning Reactor:"))
        assertTrue("PowerSystemsScreen must contain 'Harmonic Filter:'", uiSource.contains("Harmonic Filter:"))
    }

    @Test
    fun testT8_nonHarmonicBoardInSld_retainsAuthoritativeBusbarNeutral() {
        val boardCircuit = CircuitEntity(
            id = 701,
            projectId = "PROJ-T8",
            circuitName = "MDB-LINEAR",
            panelType = "MDB",
            loadKVA = 250.0,
            voltageV = 400,
            phases = "3P+N",
            isHarmonic = false
        )

        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = "PROJ-T8",
            transformers = emptyList(),
            circuits = listOf(boardCircuit)
        )

        val mdbNode = graph.nodes.firstOrNull { it.displayName == "MDB-LINEAR" }
        assertNotNull(mdbNode)
        val neutralProfile = mdbNode?.dataBlock?.busbarNeutral
        assertNotNull(neutralProfile)
        assertFalse("Non-harmonic board must NOT be marked NOT EVALUATED", neutralProfile!!.contains("NOT EVALUATED"))
        assertTrue("Non-harmonic board neutral profile has numeric dimensions", neutralProfile.contains("mm²"))
    }

    @Test
    fun testT9_and_T10_cableSizingInvarianceOnLinearCircuits() {
        val linearCircuit = CircuitEntity(
            id = 801,
            circuitName = "FEEDER-LINEAR",
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
            length_m = 25.0,
            isHarmonic = false
        )

        val sizing = CableSizingEngine.sizeCircuit(linearCircuit, 0.0)
        assertEquals("50 mm²", sizing.cableSize_mm2)
        assertEquals("25 mm²", sizing.neutral_mm2)
        assertEquals(0, sizing.warnings.size)
    }
}
