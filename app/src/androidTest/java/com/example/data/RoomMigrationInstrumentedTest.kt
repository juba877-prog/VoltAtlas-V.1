package com.example.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * KT-02 — real Room migration verification (**instrumented**).
 *
 * This is the project's first coverage that executes a migration against a database created from a
 * Room schema JSON and then validates the result against the Room-generated schema for the target
 * version. It uses the real `androidx.room.testing.MigrationTestHelper`: real SQLite, real DDL/DML,
 * real Room schema validation. No mocks, no hand-written approximation of the schema, no in-memory
 * substitute.
 *
 * ## Why this lives in `androidTest` and not in the JVM unit suite
 *
 * Room 2.8.5's `SupportSQLiteMigrationTestHelper.createDatabase(name, version)` cannot run under
 * Robolectric. Measured, not assumed:
 *
 * ```
 * java.lang.IllegalArgumentException: This driver is configured to open a database named
 *   'kt02-migration-test' but '<sandbox>/databases/kt02-migration-test' was requested.
 *     at androidx.sqlite.driver.SupportSQLiteDriver.open(SupportSQLiteDriver.android.kt:48)
 *     at androidx.room.testing.SupportSQLiteMigrationTestHelper$SupportTestConnectionManager
 *         .openConnection(MigrationTestHelper.android.kt:559)
 * ```
 *
 * The schema asset itself loads correctly under Robolectric — verified directly by invoking
 * `AndroidMigrationTestHelper.loadSchema(25)`, which returns a `SchemaBundle`. The failure is
 * strictly in Room's database-name/path resolution, which only behaves correctly on a real Android
 * runtime. Running this on a device is therefore the correct execution site, and moving it into the
 * JVM suite would have meant replacing Room's helper with a hand-rolled substitute, which the plan
 * forbids.
 *
 * **Consequence: this test is `NOT TESTED` until a device or emulator is attached.** See
 * `docs/audit/KT-02.md`.
 *
 * ## Why only one hop is covered
 *
 * `AppDatabase` shipped with `exportSchema = false`, so no schema history was ever committed and
 * `app/schemas/` did not exist. `MigrationTestHelper` can only create a database at a historical
 * version if that version's schema JSON exists. A mechanical classification of all 25 migrations by
 * DDL/DML content (`scripts/audit/classify_migrations.ps1`) established:
 *
 * - `MIGRATION_25_26` contains **no DDL** — eight `UPDATE` statements only. A migration that
 *   creates, alters, drops or renames nothing cannot change the schema, so version 25 is
 *   structurally identical to version 26 and is reconstructed from `26.json` by changing exactly one
 *   line. See `app/schemas/com.example.data.AppDatabase/25.RECONSTRUCTED.md`.
 * - The other **24** migrations contain DDL. Their pre-state cannot be derived from the current
 *   entity classes without inventing it, so versions **1–24 are NOT RECONSTRUCTABLE**.
 *
 * The reconstruction is not taken on faith. Every test below validates the migrated database against
 * the **compiler-generated** `26.json` with `validateDroppedTables = true`. If v25 really differed
 * from v26, that validation would fail.
 *
 * ## What this does NOT prove
 *
 * It does not prove migrations 1→2 … 24→25 are correct. It cannot, from repository evidence alone.
 * [documentedNotReconstructableVersions] asserts that gap so it stays visible.
 */
@RunWith(AndroidJUnit4::class)
class RoomMigrationInstrumentedTest {

    private fun newHelper(): MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    // ---------------------------------------------------------------------------------------
    // Latest migration: 25 -> 26
    // ---------------------------------------------------------------------------------------

    /**
     * Runs the real `MIGRATION_25_26` and asks Room to validate the result against the
     * compiler-generated `26.json`. A schema mismatch, a dropped table or a wrong `user_version`
     * all fail here.
     */
    @Test
    fun migration25To26_validatesAgainstGeneratedSchema26() {
        val helper = MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory()
        )
        helper.createDatabase(DB, 25).close()
        // Throws if the migrated schema differs from 26.json in any way.
        helper.runMigrationsAndValidate(DB, 26, true, MIGRATION_25_26).close()
    }

    /**
     * Data preservation across 25→26 for every project-scoped domain present at v25, plus the actual
     * business semantics of the migration.
     *
     * `MIGRATION_25_26` converts legacy inclusion booleans into explicit assignments:
     * - unambiguous (exactly one generator/ATS/UPS in the project) -> write the explicit assignment
     * - ambiguous (more than one, or none) -> clear the boolean so no phantom adoption occurs
     *
     * Both branches are asserted against real row content.
     */
    @Test
    fun migration25To26_preservesAllProjectScopedDomains() {
        val helper = MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory()
        )
        val db = helper.createDatabase(DB, 25)

        // -- projects ---------------------------------------------------------------------------
        listOf("PRJ-A" to "Project A", "PRJ-B" to "Project B", "PRJ-C" to "Project C")
            .forEachIndexed { i, (id, name) ->
                db.execSQL(
                    "INSERT INTO `projects` (`projectId`,`projectName`,`createdAt`,`updatedAt`," +
                        "`revision`) VALUES ('$id','$name',${1000 * (i + 1)},${1000 * (i + 1)},${i + 1})"
                )
            }

        // -- transformers -----------------------------------------------------------------------
        db.execSQL(
            "INSERT INTO `transformers` (`projectId`,`transformerId`,`transformerName`,`kVA`," +
                "`voltage`,`percentZ`,`xr`,`txType`,`sourceType`,`status`,`sizeMode`,`manualKVA`," +
                "`demandFactor`,`diversity`,`growth`,`safety`,`vectorGroup`,`z0OverZ1`,`pf`," +
                "`primaryVoltageV`,`gridFaultMVA`) VALUES " +
                "('PRJ-A','TX-A1','Main TX',630.0,400,6.0,10.0,'Oil','MV','Active','Auto'," +
                "630.0,0.8,1.0,1.0,1.0,'Dyn11',1.0,0.85,11000,250.0)"
        )

        // -- power-systems settings -------------------------------------------------------------
        // PRJ-A: exactly one of each -> UNAMBIGUOUS, must be assigned.
        insertGenerator(db, "PRJ-A", 1, "GEN-A", "Generator A")
        insertAts(db, "PRJ-A", 1, "ATS-A", "ATS A")
        insertUps(db, "PRJ-A", 1, "UPS-A", "UPS A")
        // PRJ-B: TWO generators -> AMBIGUOUS, boolean must be cleared.
        // Row ids must differ: the primary key is (projectId, id).
        insertGenerator(db, "PRJ-B", 1, "GEN-B1", "Generator B1")
        insertGenerator(db, "PRJ-B", 2, "GEN-B2", "Generator B2")
        // PRJ-C: none -> AMBIGUOUS (count 0), boolean must be cleared.

        // -- circuits carrying the legacy flags the migration consumes --------------------------
        insertCircuit(db, "PRJ-A", 1, "CKT-A", gen = true, ats = true, ups = true)
        insertCircuit(db, "PRJ-B", 2, "CKT-B", gen = true, ats = false, ups = false)
        insertCircuit(db, "PRJ-C", 3, "CKT-C", gen = true, ats = false, ups = false)

        // -- domains the migration does NOT touch, which must survive untouched ------------------
        db.execSQL(
            "INSERT INTO `sld_node_positions` (`projectId`,`nodeId`,`x`,`y`,`portOverrides`) " +
                "VALUES ('PRJ-A','NODE-1',12.5,-7.25,'{\"NORMAL\":\"LEFT\"}')"
        )
        db.execSQL(
            "INSERT INTO `conduit_settings` (`projectId`,`circuitId`,`routingMode`,`designProfile`," +
                "`customFillLimitPct`,`isDirectBuried`,`cpcArrangement`) " +
                "VALUES ('PRJ-A',1,'ONE_PER_CONDUIT','PROJECT_DESIGN',40.0,0,'INTERNAL_CORE')"
        )
        db.execSQL(
            "INSERT INTO `earthing_settings` (`projectId`,`id`,`soilResistivity_ohmM`,`rodLength_m`," +
                "`rodDiameter_mm`,`rodSpacing_m`,`ringConductor_mm2`,`ringBurialDepth_m`,`kFactor`," +
                "`projectType`,`disconnectionTime_s`,`soilResistivitySource`,`designMarginPct`," +
                "`isAutoDesignEnabled`) VALUES " +
                "('PRJ-A',1,125.0,3.0,17.0,5.0,25.0,0.6,159.0,'Commercial',0.4,'ASSUMED',10.0,0)"
        )
        db.close()

        // `runMigrationsAndValidate` returns the SAME handle, now migrated to v26. It must be used
        // for the assertions: calling `createDatabase(DB, 26)` afterwards would RECREATE an empty
        // database from the schema JSON and silently discard the migrated rows.
        val after = helper.runMigrationsAndValidate(DB, 26, true, MIGRATION_25_26)

        // -- projects preserved, including `revision` -------------------------------------------
        assertEquals(3, count(after, "projects"))
        after.query("SELECT `projectName`,`revision` FROM `projects` WHERE `projectId`='PRJ-A'").apply {
            assertTrue(moveToFirst())
            assertEquals("Project A", getString(0))
            assertEquals(1, getInt(1))
            close()
        }

        // -- transformer preserved, including the v24-added nullable columns ----------------------
        assertEquals(1, count(after, "transformers"))
        after.query(
            "SELECT `transformerName`,`kVA`,`primaryVoltageV`,`gridFaultMVA` FROM `transformers`"
        ).apply {
            assertTrue(moveToFirst())
            assertEquals("Main TX", getString(0))
            assertEquals(630.0, getDouble(1), 0.001)
            assertEquals(11000, getInt(2))
            assertEquals(250.0, getDouble(3), 0.001)
            close()
        }

        // -- power-systems settings preserved ---------------------------------------------------
        // 3 generators: one in PRJ-A (unambiguous) and two in PRJ-B (ambiguous). All three rows
        // must survive the migration — the migration rewrites flags on `circuits`, never on
        // `generator_settings`.
        assertEquals(3, count(after, "generator_settings"))
        assertEquals(1, count(after, "ats_settings"))
        assertEquals(1, count(after, "ups_settings"))

        // -- untouched domains preserved ---------------------------------------------------------
        assertEquals(1, count(after, "sld_node_positions"))
        assertEquals(1, count(after, "conduit_settings"))
        assertEquals(1, count(after, "earthing_settings"))
        after.query("SELECT `x`,`y` FROM `sld_node_positions` WHERE `nodeId`='NODE-1'").apply {
            assertTrue(moveToFirst())
            assertEquals(12.5, getDouble(0), 0.001)
            assertEquals(-7.25, getDouble(1), 0.001)
            close()
        }

        // -- circuits: none lost -------------------------------------------------------------------
        assertEquals(3, count(after, "circuits"))

        // PRJ-A: unambiguous -> explicit assignment written.
        after.query(
            "SELECT `assignedGeneratorId`,`assignedAtsId`,`assignedUpsId` FROM `circuits` " +
                "WHERE `projectId`='PRJ-A'"
        ).apply {
            assertTrue(moveToFirst())
            assertEquals("GEN-A", getString(0))
            assertEquals("ATS-A", getString(1))
            assertEquals("UPS-A", getString(2))
            close()
        }

        // PRJ-B: two generators -> ambiguous -> no assignment, legacy flag cleared.
        after.query(
            "SELECT `assignedGeneratorId`,`generatorIncluded` FROM `circuits` WHERE `projectId`='PRJ-B'"
        ).apply {
            assertTrue(moveToFirst())
            assertNull("ambiguous generator must NOT be assigned", getString(0))
            assertEquals("stale legacy generator flag must be cleared", 0, getInt(1))
            close()
        }

        // PRJ-C: zero generators -> ambiguous -> no assignment, legacy flag cleared.
        after.query(
            "SELECT `assignedGeneratorId`,`generatorIncluded` FROM `circuits` WHERE `projectId`='PRJ-C'"
        ).apply {
            assertTrue(moveToFirst())
            assertNull("zero-instance generator must NOT be assigned", getString(0))
            assertEquals(0, getInt(1))
            close()
        }

        after.close()
    }

    /**
     * Discrimination check: a circuit that is ALREADY explicitly assigned must survive the migration
     * unchanged. A naively written migration would clear its legacy flag because two generators
     * exist in the project.
     */
    @Test
    fun migration25To26_doesNotOverwriteAnExistingExplicitAssignment() {
        val helper = MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory()
        )
        val db = helper.createDatabase(DB, 25)
        db.execSQL(
            "INSERT INTO `projects` (`projectId`,`projectName`,`createdAt`,`updatedAt`,`revision`) " +
                "VALUES ('PRJ-D','Project D',4000,4000,2)"
        )
        // Two generators exist, so the legacy branch would clear the flag...
        insertGenerator(db, "PRJ-D", 1, "GEN-D1", "G1")
        insertGenerator(db, "PRJ-D", 2, "GEN-D2", "G2")
        // ...but this circuit was already migrated to an explicit assignment.
        insertCircuit(
            db, "PRJ-D", 9, "CKT-D",
            gen = true, ats = false, ups = false, assignedGeneratorId = "GEN-D2"
        )
        db.close()

        // `runMigrationsAndValidate` returns the SAME handle, now migrated to v26. It must be used
        // for the assertions: calling `createDatabase(DB, 26)` afterwards would RECREATE an empty
        // database from the schema JSON and silently discard the migrated rows.
        val after = helper.runMigrationsAndValidate(DB, 26, true, MIGRATION_25_26)
        after.query(
            "SELECT `assignedGeneratorId`,`generatorIncluded` FROM `circuits` WHERE `projectId`='PRJ-D'"
        ).apply {
            assertTrue(moveToFirst())
            assertEquals(
                "an already-explicit assignment must survive unchanged",
                "GEN-D2", getString(0)
            )
            assertEquals(
                "the legacy flag must NOT be cleared once assignment is explicit",
                1, getInt(1)
            )
            close()
        }
        after.close()
    }

    /**
     * The migration must never resurrect an equipment instance as a phantom assignment. A project
     * with two generators and one ATS must leave the generator unassigned but still assign the ATS.
     */
    @Test
    fun migration25To26_assignsEachDomainIndependently() {
        val helper = MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory()
        )
        val db = helper.createDatabase(DB, 25)
        db.execSQL(
            "INSERT INTO `projects` (`projectId`,`projectName`,`createdAt`,`updatedAt`,`revision`) " +
                "VALUES ('PRJ-E','Project E',5000,5000,1)"
        )
        insertGenerator(db, "PRJ-E", 1, "GEN-E1", "G1")
        insertGenerator(db, "PRJ-E", 2, "GEN-E2", "G2")
        insertAts(db, "PRJ-E", 1, "ATS-E1", "A1")
        insertCircuit(db, "PRJ-E", 11, "CKT-E", gen = true, ats = true, ups = false)
        db.close()

        // `runMigrationsAndValidate` returns the SAME handle, now migrated to v26. It must be used
        // for the assertions: calling `createDatabase(DB, 26)` afterwards would RECREATE an empty
        // database from the schema JSON and silently discard the migrated rows.
        val after = helper.runMigrationsAndValidate(DB, 26, true, MIGRATION_25_26)
        after.query(
            "SELECT `assignedGeneratorId`,`assignedAtsId` FROM `circuits` WHERE `projectId`='PRJ-E'"
        ).apply {
            assertTrue(moveToFirst())
            assertNull("ambiguous generator stays unassigned", getString(0))
            assertEquals("unambiguous ATS is still assigned", "ATS-E1", getString(1))
            close()
        }
        after.close()
    }

    // ---------------------------------------------------------------------------------------
    // Coverage-gap documentation
    // ---------------------------------------------------------------------------------------

    /**
     * Asserts the recorded coverage limitation instead of hiding it.
     *
     * If a schema JSON for a historical version is ever committed — for example by a real migration
     * run on a device — this test fails and forces the migration matrix in `docs/audit/KT-02.md` to
     * be updated. It is a tripwire, not decoration.
     *
     * The set is read from the **APK assets**, not the host filesystem. This test runs on a device,
     * where the working directory is `/` and a source-tree path such as
     * `app/schemas/...` does not exist. The assets are the same files
     * `MigrationTestHelper` itself loads, so this asserts against exactly what the migration run
     * actually sees.
     */
    @Test
    fun documentedNotReconstructableVersions() {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val folder = "com.example.data.AppDatabase"
        val onDisk = assets.list(folder)
            ?.filter { it.endsWith(".json") }
            ?.map { it.removeSuffix(".json").toInt() }
            ?.sorted()
            ?: emptyList()

        assertTrue(
            "No schema JSON found in assets under '$folder'. Room schema export must be wired to the " +
                "androidTest/debug asset source sets, otherwise MigrationTestHelper cannot load a " +
                "schema at all.",
            onDisk.isNotEmpty()
        )

        assertEquals(
            "The set of committed schema JSONs changed. Versions 1-24 may now be reconstructable — " +
                "update docs/audit/KT-02.md and extend this suite before claiming wider coverage.",
            SCHEMA_VERSIONS_WITH_JSON,
            onDisk
        )
        assertTrue(
            "Versions ${NOT_RECONSTRUCTABLE_VERSIONS.first()}..${NOT_RECONSTRUCTABLE_VERSIONS.last()} " +
                "remain NOT RECONSTRUCTABLE because MIGRATION_1_2..MIGRATION_24_25 contain DDL and " +
                "no historical schema JSON was ever committed.",
            onDisk.none { it in NOT_RECONSTRUCTABLE_VERSIONS }
        )
    }

    // ---------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------

    private fun count(db: SupportSQLiteDatabase, table: String): Int =
        db.query("SELECT COUNT(*) FROM `$table`").use { c ->
            c.moveToFirst()
            c.getInt(0)
        }

    private fun insertGenerator(db: SupportSQLiteDatabase, projectId: String, rowId: Int, id: String, name: String) {
        db.execSQL(
            "INSERT INTO `generator_settings` (`projectId`,`id`,`generatorId`,`generatorName`," +
                "`additionalEssentialLoadKW`,`largestMotorKW`,`motorStartMethod`,`motorPf`," +
                "`generatorPf`,`demandFactor`,`diversityFactor`,`stepLoadPct`,`oversizingFactor`," +
                "`enableManualOverride`,`manualSizeKVA`) VALUES " +
                "('$projectId',$rowId,'$id','$name',10.0,5.0,'DOL',0.85,0.8,0.8,1.0,50.0,1.0,0,NULL)"
        )
    }

    private fun insertAts(db: SupportSQLiteDatabase, projectId: String, rowId: Int, id: String, name: String) {
        db.execSQL(
            "INSERT INTO `ats_settings` (`projectId`,`id`,`atsId`,`atsName`,`systemVoltage`," +
                "`systemFrequency`,`numberOfSources`,`atsLocation`,`transferType`," +
                "`utilizationCategory`,`atsClass`,`loadCoverage`,`ambientTemp`," +
                "`designSafetyFactor`,`futureGrowthFactor`,`approvedManufacturer`," +
                "`mainsFailureDetectionDelay`,`generatorStartSignalDelay`,`generatorWarmUp`," +
                "`mainsReturnDetectionDelay`,`reTransferDelay`,`generatorCooldown`," +
                "`enclosureIpRating`,`enableManualOverride`) VALUES " +
                "('$projectId',$rowId,'$id','$name',400,50,2,'After Main Device','Open Transition'," +
                "'AC-31A','PC','Emergency',45,1.25,10.0,'ABB',3,0,7,60,10,300,'IP54',0)"
        )
    }

    private fun insertUps(db: SupportSQLiteDatabase, projectId: String, rowId: Int, id: String, name: String) {
        db.execSQL(
            "INSERT INTO `ups_settings` (`projectId`,`id`,`upsId`,`upsName`,`systemVoltage`," +
                "`systemFrequency`,`upsPf`,`upsEfficiency`,`batteryType`,`autonomyMinutes`," +
                "`batteryEodVoltage`,`ambientTemp`,`tempDeratingFactor`,`agingFactor`," +
                "`designMarginPct`,`enableManualOverride`) VALUES " +
                "('$projectId',$rowId,'$id','$name',400,50,0.9,94.0,'VRLA',15,1.67,25,1.0,1.25,20.0,0)"
        )
    }

    /**
     * Inserts a circuit carrying the legacy inclusion flags that `MIGRATION_25_26` consumes.
     *
     * `circuits` declares 24 NOT NULL columns with no SQL default, so all of them are supplied
     * explicitly. The values are `CircuitEntity`'s own declared defaults (`Entities.kt`), i.e. what a
     * circuit row created by the app itself contains — not invented engineering data. Only the
     * columns the migration actually reads (the three inclusion flags and `assignedGeneratorId`)
     * vary per test.
     */
    private fun insertCircuit(
        db: SupportSQLiteDatabase,
        projectId: String,
        id: Int,
        name: String,
        gen: Boolean,
        ats: Boolean,
        ups: Boolean,
        assignedGeneratorId: String? = null
    ) {
        db.execSQL(
            "INSERT INTO `circuits` (`id`,`circuitName`,`projectId`,`loadKVA`,`fedFrom`," +
                "`transformerId`,`loadType`,`phases`,`voltageV`,`pf`,`safetyFactor`,`manufacturer`," +
                "`cableType`,`installation`,`parallelRuns`,`airTempC`,`gndTempC`,`burialDepth_m`," +
                "`soilRes`,`noCircuits`,`formation`,`spacing`,`length_m`,`maxVDpct`,`isHarmonic`," +
                "`generatorIncluded`,`atsIncluded`,`upsBacked`,`assignedGeneratorId`) " +
                "VALUES ($id,'$name','$projectId',25.0,'Transformer','TX-01','Normal','3P+N',400," +
                "0.8,1.25,'Elsewedy','Cu/XLPE/SWA/PVC','Laid in Ducts',1,40,40,0.6,125,1,'Flat'," +
                "'Touching',50.0,0.03,0," +
                "${gen.sql()},${ats.sql()},${ups.sql()},${assignedGeneratorId.sqlString()})"
        )
    }

    private fun Boolean.sql(): String = if (this) "1" else "0"

    private fun String?.sqlString(): String =
        if (this == null) "NULL" else "'" + this.replace("'", "''") + "'"

    private companion object {
        const val DB = "kt02-migration-test"

        /**
         * Schema versions for which a JSON exists in `app/schemas/com.example.data.AppDatabase/`.
         * 26 is compiler-generated; 25 is reconstructed (see `25.RECONSTRUCTED.md`).
         */
        val SCHEMA_VERSIONS_WITH_JSON = listOf(25, 26)

        /** Versions 1..24 have no schema JSON and therefore cannot be exercised by this helper. */
        val NOT_RECONSTRUCTABLE_VERSIONS = (1..24).toList()
    }
}