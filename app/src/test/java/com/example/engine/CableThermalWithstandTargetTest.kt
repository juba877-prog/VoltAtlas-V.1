package com.example.engine

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.*
import com.example.testing.CatalogStateResetRule
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import kotlin.math.sqrt

/**
 * Target Test Suite for EE-03 (Cable Short-Circuit Thermal Withstand Clearing Time).
 *
 * Verifies Owner Decision D3 (Option B):
 * - Applied clearing time is explicit in SizingResult:
 *   clearingTime_s and isClearingTimeOverridden.
 * - T1: Null override preserves protective device category defaults:
 *       MCB = 0.01 s, MCCB = 0.10 s, ACB = 0.30 s.
 * - T2: Valid explicit override replaces clearing time and sets isClearingTimeOverridden = true.
 * - T3: Invalid overrides (<= 0.0, NaN, Infinity) are rejected with INCOMPLETE_INPUT.
 * - T4: Downstream withstand calculation margin flip:
 *       A long clearing time causes adiabatic withstand check to FAIL,
 *       while a short clearing time PASSES.
 * - T5: Export/import round-trip preserves explicit overrideClearingTime_s.
 * - T6: Export/import round-trip handles null overrideClearingTime_s cleanly without notice pollution.
 * - T7: Migration 26 -> 27 upgrades existing schema and supports column read/write.
 */
@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class CableThermalWithstandTargetTest {

    companion object {
        @JvmField
        @ClassRule
        val catalogStateReset = CatalogStateResetRule()

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

        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            Catalog.cableTypes = loadCatalogArray("cable_types.json") { obj ->
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
            Catalog.dataSheet = loadCatalogArray("data_sheet.json") { obj ->
                DataSheetEntry(
                    size = obj.getDouble("size"),
                    R_Cu20 = if (obj.has("R_Cu20")) obj.getDouble("R_Cu20") else obj.getDouble("R_Cu70"),
                    X = obj.getDouble("X"),
                    R_Al20 = if (obj.has("R_Al20")) obj.getDouble("R_Al20") else obj.getDouble("R_Al70"),
                    R_Cu_70_est = if (obj.has("R_Cu_70_est")) obj.getDouble("R_Cu_70_est") else obj.getDouble("R_Cu90"),
                    R_Al_70_est = if (obj.has("R_Al_70_est")) obj.getDouble("R_Al_70_est") else obj.getDouble("R_Al90")
                )
            }
            Catalog.cableCatalog = loadCatalogArray("cable_catalog.json") { obj ->
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
            Catalog.breakers = loadCatalogArray("breaker_catalog.json") { obj ->
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
            Catalog.deratingData = org.json.JSONObject(findAssetFile("derating_data.json").readText(Charsets.UTF_8))
            Catalog.invalidateIndexes()
        }
    }

    @After
    fun tearDown() {
        AppDatabase.resetInstanceForTesting()
    }

    @Test
    fun testT1_nullOverride_retainsCategoryDefaults() {
        // MCB case
        val cMcb = CircuitEntity(
            id = 1,
            circuitName = "BRANCH-MCB",
            loadKVA = 5.0,
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
            length_m = 15.0,
            overrideClearingTime_s = null
        )
        val resMcb = CableSizingEngine.sizeCircuit(cMcb, 0.0)
        assertEquals(0.01, resMcb.clearingTime_s, 1e-6)
        assertFalse(resMcb.isClearingTimeOverridden)

        // MCCB case
        val cMccb = cMcb.copy(id = 2, circuitName = "FEEDER-MCCB", loadKVA = 100.0, length_m = 50.0)
        val resMccb = CableSizingEngine.sizeCircuit(cMccb, 0.0)
        assertEquals(0.10, resMccb.clearingTime_s, 1e-6)
        assertFalse(resMccb.isClearingTimeOverridden)

        // ACB case
        val cAcb = cMcb.copy(id = 3, circuitName = "INCOMER-ACB", loadKVA = 600.0, overrideDeviceType = "ACB", length_m = 30.0)
        val resAcb = CableSizingEngine.sizeCircuit(cAcb, 0.0)
        assertEquals(0.30, resAcb.clearingTime_s, 1e-6)
        assertFalse(resAcb.isClearingTimeOverridden)
    }

    @Test
    fun testT2_validExplicitOverride_appliesAndSetsFlag() {
        val c1 = CircuitEntity(
            id = 10,
            circuitName = "OVERRIDE-01",
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
            length_m = 50.0,
            overrideClearingTime_s = 0.05
        )
        val res = CableSizingEngine.sizeCircuit(c1, 0.0)
        assertEquals(0.05, res.clearingTime_s, 1e-6)
        assertTrue(res.isClearingTimeOverridden)

        val c2 = c1.copy(overrideClearingTime_s = 0.25)
        val res2 = CableSizingEngine.sizeCircuit(c2, 0.0)
        assertEquals(0.25, res2.clearingTime_s, 1e-6)
        assertTrue(res2.isClearingTimeOverridden)
    }

    @Test
    fun testT3_invalidOverride_rejectedSafely() {
        val base = CircuitEntity(
            id = 20,
            circuitName = "INVALID-CLEARING-TIME",
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

        // <= 0.0
        val resZero = CableSizingEngine.sizeCircuit(base.copy(overrideClearingTime_s = 0.0), 0.0)
        assertEquals(SizingResultStatus.INCOMPLETE, resZero.resultStatus)
        assertEquals(CableFailureCode.INCOMPLETE_INPUT, resZero.primaryFailureCode)
        assertTrue(resZero.failReason.contains("clearing time"))

        val resNeg = CableSizingEngine.sizeCircuit(base.copy(overrideClearingTime_s = -0.05), 0.0)
        assertEquals(SizingResultStatus.INCOMPLETE, resNeg.resultStatus)
        assertEquals(CableFailureCode.INCOMPLETE_INPUT, resNeg.primaryFailureCode)

        // NaN
        val resNan = CableSizingEngine.sizeCircuit(base.copy(overrideClearingTime_s = Double.NaN), 0.0)
        assertEquals(SizingResultStatus.INCOMPLETE, resNan.resultStatus)
        assertEquals(CableFailureCode.INCOMPLETE_INPUT, resNan.primaryFailureCode)

        // Infinity
        val resInf = CableSizingEngine.sizeCircuit(base.copy(overrideClearingTime_s = Double.POSITIVE_INFINITY), 0.0)
        assertEquals(SizingResultStatus.INCOMPLETE, resInf.resultStatus)
        assertEquals(CableFailureCode.INCOMPLETE_INPUT, resInf.primaryFailureCode)
    }

    @Test
    fun testT4_downstreamWithstandMarginFlip() {
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

        // Case A: Short clearing time (0.02s) on a 50kVA feeder at 2m -> withstand passes
        val cShort = CircuitEntity(
            id = 30,
            circuitName = "FEEDER-WITHSTAND-PASS",
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
            length_m = 2.0,
            fedFrom = "Transformer",
            transformerId = "TX-01",
            overrideClearingTime_s = 0.02
        )
        val sizingShort = CableSizingEngine.sizeCircuit(cShort, 0.0)
        assertEquals(0.02, sizingShort.clearingTime_s, 1e-6)
        val scShort = ShortCircuitEngine.calculate(
            resolvedCircuits = listOf(cShort),
            circuitParents = emptyMap(),
            sizingResults = mapOf(cShort.id to sizingShort),
            transformerResults = mapOf("TX-01" to txResult)
        )[cShort.id]!!
        val protShort = ProtectionEngine.evaluateProtection(cShort, sizingShort, scShort)
        assertEquals("PASS", protShort.cableWithstandStatus)

        // Case B: Long clearing time (1.0s) on same circuit -> thermal energy exceeds withstand -> FAIL
        val cLong = cShort.copy(overrideClearingTime_s = 1.0)
        val sizingLong = CableSizingEngine.sizeCircuit(cLong, 0.0)
        assertEquals(1.0, sizingLong.clearingTime_s, 1e-6)
        val scLong = ShortCircuitEngine.calculate(
            resolvedCircuits = listOf(cLong),
            circuitParents = emptyMap(),
            sizingResults = mapOf(cLong.id to sizingLong),
            transformerResults = mapOf("TX-01" to txResult)
        )[cLong.id]!!
        val protLong = ProtectionEngine.evaluateProtection(cLong, sizingLong, scLong)
        assertEquals("FAIL", protLong.cableWithstandStatus)
    }

    @Test
    fun testT5_exportImportPreservesOverride() = runBlocking {
        AppDatabase.useTestExecutors = true
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = AppDatabase.getDatabase(context)
        val repo = EngineeringRepository(db)

        val projectId = repo.createProject("EE03 Target Test Project", "Client")
        repo.insertCircuit(
            CircuitEntity(
                projectId = projectId,
                circuitName = "F-OVERRIDE",
                loadKVA = 75.0,
                overrideClearingTime_s = 0.08
            )
        )

        val exportedJson = repo.exportProjectJson(projectId)
        assertTrue(exportedJson.contains("\"overrideClearingTime_s\": 0.08") || exportedJson.contains("\"overrideClearingTime_s\":0.08"))

        val importResult = repo.importProjectFromJsonDetailed(exportedJson)
        assertEquals(ImportStatus.SUCCESS, importResult.status)
        val importedProjId = importResult.projectId!!

        val importedCircuits = db.circuitDao().getCircuitsListForProject(importedProjId)
        val importedCircuit = importedCircuits.first { it.circuitName == "F-OVERRIDE" }
        assertNotNull(importedCircuit.overrideClearingTime_s)
        assertEquals(0.08, importedCircuit.overrideClearingTime_s!!, 1e-6)
    }

    @Test
    fun testT6_exportImportHandlesNullOverrideCleanly() = runBlocking {
        AppDatabase.useTestExecutors = true
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = AppDatabase.getDatabase(context)
        val repo = EngineeringRepository(db)

        val projectId = repo.createProject("EE03 Null Override Test", "Client")
        repo.insertCircuit(
            CircuitEntity(
                projectId = projectId,
                circuitName = "F-AUTO-CLEARING",
                loadKVA = 50.0,
                overrideClearingTime_s = null
            )
        )

        val exportedJson = repo.exportProjectJson(projectId)
        val importResult = repo.importProjectFromJsonDetailed(exportedJson)
        assertEquals(ImportStatus.SUCCESS, importResult.status)
        val importedProjId = importResult.projectId!!

        // Must not emit DEFAULTED_BY_SCHEMA for overrideClearingTime_s
        assertFalse(
            importResult.notices.any {
                it.fieldName == "overrideClearingTime_s" &&
                it.classification == MigrationFieldClassification.DEFAULTED_BY_SCHEMA
            }
        )

        val importedCircuits = db.circuitDao().getCircuitsListForProject(importedProjId)
        val importedCircuit = importedCircuits.first { it.circuitName == "F-AUTO-CLEARING" }
        assertNull(importedCircuit.overrideClearingTime_s)
    }

    @Test
    fun testT7_migration26To27Upgrade() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val openHelper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(26) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("""
                            CREATE TABLE IF NOT EXISTS `circuits` (
                                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                `circuitName` TEXT NOT NULL,
                                `loadKVA` REAL NOT NULL,
                                `fedFrom` TEXT NOT NULL DEFAULT 'Transformer',
                                `transformerId` TEXT NOT NULL DEFAULT '',
                                `loadType` TEXT NOT NULL DEFAULT 'Normal',
                                `phases` TEXT NOT NULL DEFAULT '3P+N',
                                `voltageV` INTEGER NOT NULL DEFAULT 400,
                                `pf` REAL NOT NULL DEFAULT 0.85,
                                `safetyFactor` REAL NOT NULL DEFAULT 1.25,
                                `manufacturer` TEXT NOT NULL DEFAULT 'Elsewedy',
                                `cableType` TEXT NOT NULL DEFAULT 'Cu/XLPE/PVC - 4C',
                                `installation` TEXT NOT NULL DEFAULT 'Laid in Ducts',
                                `parallelRuns` INTEGER NOT NULL DEFAULT 1,
                                `airTempC` INTEGER NOT NULL DEFAULT 40,
                                `gndTempC` INTEGER NOT NULL DEFAULT 40,
                                `burialDepth_m` REAL NOT NULL DEFAULT 0.6,
                                `soilRes` INTEGER NOT NULL DEFAULT 125,
                                `noCircuits` INTEGER NOT NULL DEFAULT 1,
                                `formation` TEXT NOT NULL DEFAULT 'Flat',
                                `spacing` TEXT NOT NULL DEFAULT 'Touching',
                                `length_m` REAL NOT NULL DEFAULT 30.0,
                                `maxVDpct` REAL NOT NULL DEFAULT 0.04,
                                `isHarmonic` INTEGER NOT NULL DEFAULT 0,
                                `projectId` TEXT NOT NULL DEFAULT 'default_project'
                            )
                        """.trimIndent())
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
        )

        val db = openHelper.writableDatabase
        try {
            assertEquals(26, db.version)
            db.execSQL("INSERT INTO `circuits` (`circuitName`, `loadKVA`) VALUES ('MIG-C1', 40.0)")

            // Execute migration
            MIGRATION_26_27.migrate(db)

            // Verify column exists and defaults to NULL
            val cursor = db.query("SELECT circuitName, overrideClearingTime_s FROM circuits WHERE circuitName = 'MIG-C1'")
            assertTrue(cursor.moveToFirst())
            assertEquals("MIG-C1", cursor.getString(0))
            assertTrue(cursor.isNull(1))
            cursor.close()

            // Verify we can update and store an override value
            db.execSQL("UPDATE circuits SET overrideClearingTime_s = 0.15 WHERE circuitName = 'MIG-C1'")
            val cursorUpdated = db.query("SELECT overrideClearingTime_s FROM circuits WHERE circuitName = 'MIG-C1'")
            assertTrue(cursorUpdated.moveToFirst())
            assertEquals(0.15, cursorUpdated.getDouble(0), 1e-6)
            cursorUpdated.close()
        } finally {
            db.close()
            openHelper.close()
        }
    }
}
