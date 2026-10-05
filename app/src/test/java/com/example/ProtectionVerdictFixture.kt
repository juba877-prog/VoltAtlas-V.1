package com.example

import com.example.data.AccessoryEntry
import com.example.data.BreakerEntry
import com.example.data.CableCatalogEntry
import com.example.data.CableType
import com.example.data.Catalog
import com.example.data.CircuitEntity
import com.example.data.DataSheetEntry
import com.example.data.TransformerEntity
import com.example.data.catalog.CatalogRepository
import com.example.engine.CableSizingEngine
import com.example.engine.ProtectionEngine
import com.example.engine.ShortCircuitEngine
import com.example.engine.TransformerEngine
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import java.io.File

/**
 * ECT-EE-06 shared fixture: the **real production calculation chain** used by every
 * EE-06 test.
 *
 * ============================================================================
 * WHY THIS LIVES IN ITS OWN FILE
 * ============================================================================
 *
 * It deliberately references **only pre-existing APIs** — `CableSizingEngine`,
 * `ShortCircuitEngine`, `ProtectionEngine`, `TransformerEngine` — and no EE-06
 * verdict API. That lets it compile unchanged against the *unmodified* checkout,
 * which is what makes the §4 target test runnable on before-SHA `4da9e03` to prove
 * the defect is real (and to prove the target test FAILS there, as §4 requires).
 *
 * ============================================================================
 * WHAT IS REAL HERE
 * ============================================================================
 *
 *  * Catalogue: loaded verbatim from the shipped `app/src/main/assets` JSON files,
 *    exactly as `QaTestSuite` / `EngineeringCalculationCoordinatorTest` do, with
 *    [Catalog.invalidateIndexes] called so a previously-leaked index can never leak
 *    into this run (defect VOLT-TESTISO-001).
 *  * Circuit: committed QA reference case read from `qa_reference_cases.json` at
 *    runtime — never transcribed constants.
 *  * Transformer: [TransformerEntity] with its own default `percentZ = 5.0` and
 *    `xr = 6.0`, sized by the real `TransformerEngine.calculate`.
 *  * Isc: real `ShortCircuitEngine.calculate` (EE-02 near-end semantics, frozen).
 *  * Protection: real `ProtectionEngine.evaluateProtection`.
 *
 * See the class header of `ProtectionVerdictCharacterizationTest` for the documented
 * topology deviation (`fedFrom = "Transformer"`) and why the numbers arise naturally.
 */
object ProtectionVerdictFixture {

    private lateinit var repository: CatalogRepository
    private lateinit var qaCases: JSONArray
    private fun findFile(vararg names: String): File {
        for (name in names) {
            for (dir in listOf("app/src/main/assets", "src/main/assets")) {
                val f = File("$dir/$name")
                if (f.exists()) return f
            }
        }
        error("Asset not found: ${names.joinToString()}")
    }

    private inline fun <reified T> loadJson(name: String, map: (JSONObject) -> T): List<T> {
        val array = JSONArray(findFile(name).readText(Charsets.UTF_8))
        return (0 until array.length()).map { map(array.getJSONObject(it)) }
    }

    /**
     * Installs the shipped catalogue into the process-wide [Catalog] singleton and loads
     * the [CatalogRepository].
     *
     * Called from the `@BeforeClass` of every EE-06 test class and intentionally **not**
     * guarded by a "already ran" flag. `CatalogStateResetRule` is snapshot-on-entry /
     * restore-on-exit, so a test class that runs second inherits whatever the previous
     * class handed back — which for the first class in the fork is the empty baseline.
     * An idempotency flag would therefore let the second class observe an empty catalogue
     * and fail for a reason that has nothing to do with the code under test.
     * Re-running the loader is cheap (six local JSON files) and always yields the same
     * result, so correctness wins over the micro-optimisation.
     */
    @JvmStatic
    @Synchronized
    fun install() {
        Catalog.cableTypes = loadJson("cable_types.json") { obj ->
            CableType(
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
        Catalog.dataSheet = loadJson("data_sheet.json") { obj ->
            DataSheetEntry(
                size = obj.getDouble("size"),
                R_Cu20 = if (obj.has("R_Cu20")) obj.getDouble("R_Cu20") else obj.getDouble("R_Cu70"),
                X = obj.getDouble("X"),
                R_Al20 = if (obj.has("R_Al20")) obj.getDouble("R_Al20") else obj.getDouble("R_Al70"),
                R_Cu_70_est = if (obj.has("R_Cu_70_est")) obj.getDouble("R_Cu_70_est") else obj.getDouble("R_Cu90"),
                R_Al_70_est = if (obj.has("R_Al_70_est")) obj.getDouble("R_Al_70_est") else obj.getDouble("R_Al90")
            )
        }
        Catalog.cableCatalog = loadJson("cable_catalog.json") { obj ->
            CableCatalogEntry(
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
        Catalog.breakers = loadJson("breaker_catalog.json") { obj ->
            BreakerEntry(
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
        Catalog.accessories = loadJson("accessory_catalog.json") { obj ->
            AccessoryEntry(
                mfg = obj.optString("mfg", obj.optString("Manufacturer", "Generic")),
                category = obj.optString("category", obj.optString("Category", "")),
                subCategory = obj.optString("subCategory", obj.optString("Sub-Category", "")),
                partNumber = obj.optString("partNumber", obj.optString("Part Number", "")),
                desc = obj.optString("desc", obj.optString("Description", "")),
                sizeMin = com.example.data.catalog.BreakerParser.parseDouble(obj.opt("sizeMin")),
                sizeMax = com.example.data.catalog.BreakerParser.parseDouble(obj.opt("sizeMax")),
                odMin = com.example.data.catalog.BreakerParser.parseDouble(obj.opt("odMin")),
                odMax = com.example.data.catalog.BreakerParser.parseDouble(obj.opt("odMax")),
                cores = obj.optString("cores", obj.optString("Cores", "Any")),
                conductor = if (obj.has("conductor") && !obj.isNull("conductor")) obj.optString("conductor") else null,
                armourType = if (obj.has("armourType") && !obj.isNull("armourType")) obj.optString("armourType") else null,
                threadSize = if (obj.has("threadSize") && !obj.isNull("threadSize")) obj.optString("threadSize") else null,
                boltSize = if (obj.has("boltSize") && !obj.isNull("boltSize")) obj.optString("boltSize") else null
            )
        }
        Catalog.deratingData = JSONObject(findFile("derating_data.json").readText(Charsets.UTF_8))
        Catalog.invalidateIndexes()

        repository = CatalogRepository()
        val diag = repository.loadAllFromStrings(
            findFile("cable_catalog.json").readText(),
            findFile("cable_types_data.json").readText(),
            findFile("breaker_catalog.json").readText(),
            findFile("accessory_catalog.json").readText(),
            findFile("derating_data.json").readText(),
            findFile("transformers_seed.json").readText()
        )
        assertEquals("shipped catalogue must load COMPLETE", "COMPLETE", diag.status)

        qaCases = JSONObject(findFile("qa_reference_cases.json").readText(Charsets.UTF_8))
            .getJSONArray("cableCases")
    }

    /** QA reference case (e.g. `TC-CABLE-003`), read from committed project data. */
    fun qaCase(id: String): JSONObject {
        install()
        for (i in 0 until qaCases.length()) {
            val o = qaCases.getJSONObject(i)
            if (o.optString("id") == id) return o
        }
        error("QA reference case not found: $id")
    }

    /**
     * Builds the QA case exactly as `CableSizingEngineTest` does, with the single
     * documented topology deviation: the circuit is modelled directly on the transformer
     * LV busbar (`fedFrom = "Transformer"`, `transformerId = "TX-01"`) so that
     * `ShortCircuitEngine` resolves a real source through
     * `HierarchyResolver.resolveAncestors`. The load, cable, derating and device-override
     * inputs are the committed case verbatim.
     */
    fun buildCircuit(input: JSONObject): CircuitEntity = CircuitEntity(
        id = input.optInt("id", 1),
        circuitName = input.getString("circuitName"),
        loadKVA = input.getDouble("loadKVA"),
        pf = input.getDouble("pf"),
        phases = input.getString("phases"),
        voltageV = input.optInt("voltageV", 400),
        safetyFactor = input.optDouble("safetyFactor", 1.0),
        length_m = input.getDouble("length_m"),
        installation = input.getString("installation"),
        cableType = input.getString("cableType"),
        manufacturer = input.optString("manufacturer", "Elsewedy"),
        parallelRuns = input.optInt("parallelRuns", 1),
        airTempC = input.optInt("airTempC", 40),
        gndTempC = input.optInt("gndTempC", 40),
        burialDepth_m = input.optDouble("burialDepth_m", 0.6),
        soilRes = input.optInt("soilRes", 125),
        noCircuits = input.optInt("noCircuits", 1),
        spacing = input.optString("spacing", "Touching"),
        formation = input.optString("formation", "Flat"),
        maxVDpct = input.optDouble("maxVDpct", 0.03),
        overrideDeviceType = input.optString("overrideDeviceType").ifBlank { null },
        overrideDeviceSize = if (input.has("overrideDeviceSize") && !input.isNull("overrideDeviceSize")) input.getDouble("overrideDeviceSize") else null,
        overrideCableSize = input.optString("overrideCableSize").ifBlank { null },
        fedFrom = "Transformer",
        transformerId = "TX-01"
    )

    /**
     * The full, real production chain for one circuit:
     * sizing → transformer → short-circuit → protection → presentation inputs.
     *
     * @param qaCaseId committed QA reference case. `TC-CABLE-003` is the false-PASS
     *   scenario (16 A MCB, Icu 10 kA, ≈19.1 kA available); `TC-CABLE-006` is the
     *   genuine PASS regression.
     * @param txKva transformer rating, a member of `TransformerEngine.STANDARD_TX_SIZES`.
     */
    fun runPipeline(
        qaCaseId: String = "TC-CABLE-003",
        txKva: Double = 630.0
    ): Pipeline {
        install()
        val qa = qaCase(qaCaseId)
        val circuit = buildCircuit(qa.getJSONObject("input"))

        val sizing = CableSizingEngine.sizeCircuit(circuit, 0.0, repository)
        assertEquals("real sizing result", true, sizing != null)

        val txEntity = TransformerEntity(
            projectId = "PRJ_EE06",
            transformerId = "TX-01",
            kVA = txKva,
            voltage = 400,
            sizeMode = "Manual",
            manualKVA = txKva,
            status = "Active"
        )
        val txResult = TransformerEngine.calculate(txEntity, listOf(circuit))

        val scResults = ShortCircuitEngine.calculate(
            resolvedCircuits = listOf(circuit),
            circuitParents = emptyMap(),
            sizingResults = mapOf(circuit.id to sizing),
            transformerResults = mapOf("TX-01" to txResult)
        )
        val sc = scResults[circuit.id]
        assertEquals("real short-circuit result", true, sc != null)

        val protection = ProtectionEngine.evaluateProtection(
            circuit = circuit,
            sizingResult = sizing,
            shortCircuitResult = sc
        )

        return Pipeline(circuit, sizing, txResult, sc!!, protection)
    }

    /** Same circuit and sizing, but protection evaluated with no short-circuit result. */
    fun runProtectionWithoutIsc(): com.example.engine.ProtectionResult {
        val p = runPipeline()
        return ProtectionEngine.evaluateProtection(
            circuit = p.circuit,
            sizingResult = p.sizing,
            shortCircuitResult = null
        )
    }

    data class Pipeline(
        val circuit: CircuitEntity,
        val sizing: com.example.engine.SizingResult,
        val tx: com.example.engine.TransformerResult,
        val sc: com.example.engine.ShortCircuitResult,
        val protection: com.example.engine.ProtectionResult
    )
}
