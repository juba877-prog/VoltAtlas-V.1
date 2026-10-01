package com.example

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Phase 6 Batch 1 regression tests — P0-12 (audit C-04, C-06):
 *  import fallbacks must equal the entity schema defaults, every applied default must emit a
 *  `DEFAULTED_BY_SCHEMA` notice, and the legacy `soilResistivitySource` token "DEFAULT_TABLE"
 *  must migrate to ASSUMED so the engine's assumed-soil disclosure fires instead of being
 *  silently suppressed (the old kFactor=1.0 fallback inflated earth-conductor S ~159×).
 *
 * Authorities preserved by these tests:
 *  - A1-R (prior fix): a missing `installation` is stored BLANK + warned — never defaulted.
 *  - Current-format (v2) export → import writes every aligned field, so round-trip must emit
 *    ZERO default notices and preserve explicit values.
 */
@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class ImportDefaultParityTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: EngineeringRepository

    @Before
    fun setUp() {
        AppDatabase.useTestExecutors = true
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = AppDatabase.getDatabase(context)
        repository = EngineeringRepository(database)
    }

    @After
    fun tearDown() {
        AppDatabase.useTestExecutors = false
    }

    private fun ProjectImportResult.defaulted(
        entityType: String,
        identifier: String?,
        field: String
    ): MigrationNotice? = notices.find {
        it.classification == MigrationFieldClassification.DEFAULTED_BY_SCHEMA &&
            it.entityType == entityType &&
            it.fieldName == field &&
            (identifier == null || it.entityIdentifier == identifier)
    }

    private fun ProjectImportResult.defaultedFields(entityType: String, identifier: String?): Set<String> =
        notices.filter {
            it.classification == MigrationFieldClassification.DEFAULTED_BY_SCHEMA &&
                it.entityType == entityType &&
                (identifier == null || it.entityIdentifier == identifier)
        }.map { it.fieldName }.toSet()

    // =====================================================================
    // 1. Legacy JSON missing the cited fields ⇒ schema defaults + notices
    // =====================================================================
    @Test
    fun legacyImportMissingAlignedFields_appliesSchemaDefaultsAndNotices() = runBlocking {
        val json = """
        {
          "version": 1,
          "appName": "VoltAtlas",
          "project": { "projectName": "Import Default Parity" },
          "circuits": [
            { "id": 10, "circuitName": "MDB-Main", "fedFrom": "Transformer", "loadKVA": 100.0, "installation": "Laid in Ducts" },
            { "id": 11, "circuitName": "DB-Lighting", "fedFrom": "MDB-Main", "loadKVA": 20.0, "installation": "Laid in Ducts" }
          ],
          "pfGlobal": { },
          "earthingSettings": { }
        }
        """.trimIndent()

        val result = repository.importProjectFromJsonDetailed(json)
        assertEquals(ImportStatus.SUCCESS, result.status)
        assertNotNull(result.projectId)
        val newProjId = result.projectId!!

        // ---- circuits: fallbacks == CircuitEntity constructor defaults ----
        val circuits = database.circuitDao().getCircuitsListForProject(newProjId)
        assertEquals(2, circuits.size)
        val main = circuits.first { it.circuitName == "MDB-Main" }
        val pure = CircuitEntity(projectId = newProjId, circuitName = "MDB-Main", fedFrom = "Transformer", loadKVA = 100.0)
        assertEquals("parity: pf", pure.pf, main.pf, 0.0)
        assertEquals("parity: safetyFactor", pure.safetyFactor, main.safetyFactor, 0.0)
        assertEquals("parity: manufacturer", pure.manufacturer, main.manufacturer)
        assertEquals("parity: cableType", pure.cableType, main.cableType)
        assertEquals("parity: maxVDpct", pure.maxVDpct, main.maxVDpct, 0.0)
        // exact schema values — guards silent drift of the defaults themselves
        assertEquals(0.8, main.pf, 0.0)
        assertEquals(1.25, main.safetyFactor, 0.0)
        assertEquals("Elsewedy", main.manufacturer)
        assertEquals("Cu/XLPE/SWA/PVC", main.cableType)
        assertEquals(0.03, main.maxVDpct, 0.0)
        // installation was carried in the file ⇒ untouched
        assertEquals("Laid in Ducts", main.installation)

        val circuitDefaults = result.defaultedFields("Circuit", "MDB-Main")
        assertEquals(
            "unexpected defaulted-field set for circuits",
            setOf("pf", "safetyFactor", "manufacturer", "cableType", "maxVDpct"),
            circuitDefaults
        )
        // A1-R authority: installation is never defaulted nor noticed — blank + warning instead
        assertTrue(result.notices.none { it.entityType == "Circuit" && it.fieldName == "installation" })

        // ---- pfGlobal: fallbacks == PfGlobalEntity constructor defaults ----
        val pfGlobal = database.powerSystemsDao().getPfGlobalSettingsDirect(newProjId)
        assertNotNull("pfGlobal section must be imported", pfGlobal)
        val purePf = PfGlobalEntity(projectId = newProjId)
        assertEquals("parity: targetPf", purePf.targetPf, pfGlobal!!.targetPf, 0.0)
        assertEquals("parity: kvarStepSize", purePf.kvarStepSize, pfGlobal.kvarStepSize, 0.0)
        assertEquals("parity: hours", purePf.annualOperatingHours, pfGlobal.annualOperatingHours)
        assertEquals("parity: energyCost", purePf.energyCost, pfGlobal.energyCost, 0.0)
        assertEquals("parity: lossReductionPct", purePf.lossReductionPct, pfGlobal.lossReductionPct, 0.0)
        assertEquals("parity: unityTolerance", purePf.unityToleranceKvar, pfGlobal.unityToleranceKvar, 0.0)
        // exact schema values (old fallbacks were 0.95 / 25.0 / 8760 / 0.12 / 15.0 / 5.0)
        assertEquals(0.97, pfGlobal.targetPf, 0.0)
        assertEquals(50.0, pfGlobal.kvarStepSize, 0.0)
        assertEquals(6000, pfGlobal.annualOperatingHours)
        assertEquals(1.5, pfGlobal.energyCost, 0.0)
        assertEquals(0.03, pfGlobal.lossReductionPct, 0.0)
        assertEquals(0.5, pfGlobal.unityToleranceKvar, 0.0)
        assertEquals(
            setOf("targetPf", "kvarStepSize", "annualOperatingHours", "energyCost", "lossReductionPct", "unityToleranceKvar"),
            result.defaultedFields("PfGlobal", newProjId)
        )

        // ---- earthing: fallbacks == EarthingEntity constructor defaults ----
        val earthing = database.powerSystemsDao().getEarthingSettingsDirect(newProjId)
        assertNotNull("earthingSettings section must be imported", earthing)
        val pureE = EarthingEntity(projectId = newProjId)
        assertEquals("parity: rho", pureE.soilResistivity_ohmM, earthing!!.soilResistivity_ohmM, 0.0)
        assertEquals("parity: rod dia", pureE.rodDiameter_mm, earthing.rodDiameter_mm, 0.0)
        assertEquals("parity: rod spacing", pureE.rodSpacing_m, earthing.rodSpacing_m, 0.0)
        assertEquals("parity: ring conductor", pureE.ringConductor_mm2, earthing.ringConductor_mm2, 0.0)
        assertEquals("parity: ring burial", pureE.ringBurialDepth_m, earthing.ringBurialDepth_m, 0.0)
        assertEquals("parity: kFactor", pureE.kFactor, earthing.kFactor, 0.0)
        assertEquals("parity: margin", pureE.designMarginPct, earthing.designMarginPct, 0.0)
        // exact schema values (old fallbacks were 100.0 / 16.0 / 6.0 / 70.0 / 0.8 / 1.0 / 20.0)
        assertEquals(50.0, earthing.soilResistivity_ohmM, 0.0)
        assertEquals(17.0, earthing.rodDiameter_mm, 0.0)
        assertEquals(5.0, earthing.rodSpacing_m, 0.0)
        assertEquals(25.0, earthing.ringConductor_mm2, 0.0)
        assertEquals(0.6, earthing.ringBurialDepth_m, 0.0)
        assertEquals("kFactor must be the copper default 159.0 (old fallback 1.0 inflated S ~159×)", 159.0, earthing.kFactor, 0.0)
        assertEquals(10.0, earthing.designMarginPct, 0.0)
        assertEquals("assumed soil must be disclosed, never a bogus token", "ASSUMED", earthing.soilResistivitySource)
        assertEquals(
            setOf(
                "soilResistivity_ohmM", "rodDiameter_mm", "rodSpacing_m", "ringConductor_mm2",
                "ringBurialDepth_m", "kFactor", "designMarginPct", "soilResistivitySource"
            ),
            result.defaultedFields("Earthing", newProjId)
        )
        assertTrue(
            "kFactor notice must call out the 159× inflation",
            result.defaulted("Earthing", newProjId, "kFactor")!!.message.contains("159")
        )
    }

    // =====================================================================
    // 2. Legacy DEFAULT_TABLE soil source ⇒ ASSUMED + MIGRATED notice;
    //    explicit values in the file are never overridden.
    // =====================================================================
    @Test
    fun legacySoilSourceDefaultTable_migratesToAssumed_explicitValuesSurvive() = runBlocking {
        val json = """
        {
          "version": 1,
          "appName": "VoltAtlas",
          "project": { "projectName": "Soil Legacy Project" },
          "circuits": [
            { "id": 10, "circuitName": "MDB-Main", "fedFrom": "Transformer", "loadKVA": 100.0, "installation": "Laid in Ducts",
              "pf": 0.9, "safetyFactor": 1.0, "manufacturer": "Nader", "cableType": "Cu/XLPE/PVC - 4C", "maxVDpct": 0.02 },
            { "id": 11, "circuitName": "DB-Lighting", "fedFrom": "MDB-Main", "loadKVA": 20.0, "installation": "Laid in Ducts" }
          ],
          "pfGlobal": { "targetPf": 0.9, "kvarStepSize": 25.0, "annualOperatingHours": 4000, "energyCost": 0.8, "lossReductionPct": 0.05, "unityToleranceKvar": 1.0 },
          "earthingSettings": { "soilResistivitySource": "DEFAULT_TABLE", "kFactor": 121.74, "designMarginPct": 15.0 }
        }
        """.trimIndent()

        val result = repository.importProjectFromJsonDetailed(json)
        assertEquals(ImportStatus.SUCCESS, result.status)
        assertNotNull(result.projectId)
        val newProjId = result.projectId!!

        // legacy token migrated to a valid source and explicitly disclosed
        val earthing = database.powerSystemsDao().getEarthingSettingsDirect(newProjId)!!
        assertEquals("DEFAULT_TABLE is not a valid token — must record ASSUMED", "ASSUMED", earthing.soilResistivitySource)
        val migrated = result.notices.find {
            it.entityType == "Earthing" && it.fieldName == "soilResistivitySource" &&
                it.classification == MigrationFieldClassification.MIGRATED
        }
        assertNotNull("legacy DEFAULT_TABLE must produce a MIGRATED notice", migrated)
        assertTrue(migrated!!.message.contains("DEFAULT_TABLE"))

        // explicitly carried values are never overridden and never noticed
        assertEquals(121.74, earthing.kFactor, 0.0)
        assertEquals(15.0, earthing.designMarginPct, 0.0)
        assertTrue(result.notices.none { it.entityType == "Earthing" && it.fieldName == "kFactor" })
        assertTrue(result.notices.none { it.entityType == "Earthing" && it.fieldName == "designMarginPct" })

        val main = database.circuitDao().getCircuitsListForProject(newProjId).first { it.circuitName == "MDB-Main" }
        assertEquals(0.9, main.pf, 0.0)
        assertEquals(1.0, main.safetyFactor, 0.0)
        assertEquals("Nader", main.manufacturer)
        assertEquals("Cu/XLPE/PVC - 4C", main.cableType)
        assertEquals(0.02, main.maxVDpct, 0.0)
        assertTrue(
            "no default may be noticed where the file carried a value",
            result.notices.none {
                it.entityType == "Circuit" && it.entityIdentifier == "MDB-Main" &&
                    it.classification == MigrationFieldClassification.DEFAULTED_BY_SCHEMA
            }
        )

        val pfGlobal = database.powerSystemsDao().getPfGlobalSettingsDirect(newProjId)!!
        assertEquals(0.9, pfGlobal.targetPf, 0.0)
        assertEquals(25.0, pfGlobal.kvarStepSize, 0.0)
        assertEquals(4000, pfGlobal.annualOperatingHours)
        assertEquals(0.8, pfGlobal.energyCost, 0.0)
        assertEquals(0.05, pfGlobal.lossReductionPct, 0.0)
        assertEquals(1.0, pfGlobal.unityToleranceKvar, 0.0)
        assertTrue(result.notices.none { it.entityType == "PfGlobal" })
    }

    // =====================================================================
    // 3. Current-format (v2) round-trip ⇒ ZERO default notices, values kept
    // =====================================================================
    @Test
    fun currentFormatRoundTrip_emitsZeroDefaultNoticesAndPreservesValues() = runBlocking {
        val projectId = repository.createProject("Round Trip Parity", "Client")
        database.circuitDao().insertCircuit(
            CircuitEntity(
                circuitName = "MDB-RT",
                projectId = projectId,
                loadKVA = 100.0,
                voltageV = 415,
                pf = 0.9,
                safetyFactor = 1.1,
                manufacturer = "Nader",
                cableType = "Cu/XLPE/SWA/PVC - 4C",
                installation = "Laid Direct in Ground",
                maxVDpct = 0.025
            )
        )
        database.powerSystemsDao().savePfGlobalSettings(
            PfGlobalEntity(projectId = projectId, targetPf = 0.95, energyCost = 2.5)
        )
        database.powerSystemsDao().saveEarthingSettings(
            EarthingEntity(projectId = projectId, kFactor = 121.74, soilResistivitySource = "MEASURED")
        )

        val exported = repository.exportProjectJson(projectId)
        val reimported = repository.importProjectFromJsonDetailed(exported)
        assertEquals(ImportStatus.SUCCESS, reimported.status)
        assertNotNull(reimported.projectId)
        val rtProjId = reimported.projectId!!

        // the exporter writes every aligned field ⇒ no schema default may fire on round-trip
        assertTrue(
            "v2 round-trip must emit no DEFAULTED_BY_SCHEMA notices: ${reimported.notices}",
            reimported.notices.none { it.classification == MigrationFieldClassification.DEFAULTED_BY_SCHEMA }
        )
        assertTrue(reimported.notices.none { it.entityType == "Earthing" && it.classification == MigrationFieldClassification.MIGRATED })

        val rt = database.circuitDao().getCircuitsListForProject(rtProjId).first { it.circuitName == "MDB-RT" }
        assertEquals(0.9, rt.pf, 0.0)
        assertEquals(1.1, rt.safetyFactor, 0.0)
        assertEquals("Nader", rt.manufacturer)
        assertEquals("Cu/XLPE/SWA/PVC - 4C", rt.cableType)
        assertEquals("Laid Direct in Ground", rt.installation)
        assertEquals(0.025, rt.maxVDpct, 0.0)
        assertEquals(415, rt.voltageV)

        val e = database.powerSystemsDao().getEarthingSettingsDirect(rtProjId)!!
        assertEquals(121.74, e.kFactor, 0.0)
        assertEquals("MEASURED", e.soilResistivitySource)

        val pf = database.powerSystemsDao().getPfGlobalSettingsDirect(rtProjId)!!
        assertEquals(0.95, pf.targetPf, 0.0)
        assertEquals(2.5, pf.energyCost, 0.0)
    }

    // =====================================================================
    // 4. Only the documented legacy token is migrated — any other carried
    //    value round-trips verbatim (field-by-field integrity, C-04 scope).
    // =====================================================================
    @Test
    fun carriedNonLegacySoilSource_isPreservedVerbatimWithoutNotice() = runBlocking {
        val json = """
        {
          "version": 1,
          "appName": "VoltAtlas",
          "project": { "projectName": "Soil Token Preserve" },
          "earthingSettings": { "soilResistivitySource": "Soil Analysis" }
        }
        """.trimIndent()

        val result = repository.importProjectFromJsonDetailed(json)
        assertNotNull(result.projectId)
        val projId = result.projectId!!
        val earthing = database.powerSystemsDao().getEarthingSettingsDirect(projId)!!
        assertEquals(
            "only 'DEFAULT_TABLE' is migrated; every other carried value survives verbatim",
            "Soil Analysis",
            earthing.soilResistivitySource
        )
        assertTrue(result.notices.none { it.entityType == "Earthing" && it.fieldName == "soilResistivitySource" })
    }
}
