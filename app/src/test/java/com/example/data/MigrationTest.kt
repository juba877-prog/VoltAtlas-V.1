package com.example.data

import android.app.Application
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import com.example.ui.EngineeringViewModel
import com.example.ui.UiState
import com.example.ui.StartupState
import org.robolectric.shadows.ShadowLooper
import com.example.engine.coordinator.CableCalculationService
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private fun idle() {
        ShadowLooper.idleMainLooper()
    }

    private fun idle(ms: Long) {
        ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)
    }

    private fun idleAndSleep(ms: Long = 100) {
        java.lang.Thread.sleep(ms)
        ShadowLooper.idleMainLooper()
    }

    private suspend fun delayAndIdle(ms: Long = 100) {
        kotlinx.coroutines.delay(ms)
        ShadowLooper.idleMainLooper()
    }

    @After
    fun tearDown() {
        // KT-12: this class calls AppDatabase.getDatabase() directly, which caches the instance
        // process-wide together with an absolute DB file path inside the current Robolectric
        // sandbox. Robolectric rotates that sandbox per @Config while Gradle reuses one JVM, so a
        // surviving instance points at a path that no longer exists and later suites fail with
        // SQLITE_CANTOPEN (code 14). AppDatabase needs no import: same package.
        AppDatabase.resetInstanceForTesting()
    }

    @Test
    fun testMigration4To5AndSchemaParity() {
        // Prepare helper to open an in-memory DB and set up v4 schema
        val openHelper = FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext())
                .name(null) // null name = in-memory SQLite DB
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(4) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        // 1) transformers v4 table
                        db.execSQL("""
                            CREATE TABLE IF NOT EXISTS `transformers` (
                                `projectId` TEXT NOT NULL DEFAULT 'DEFAULT_PROJECT',
                                `transformerId` TEXT NOT NULL,
                                `transformerName` TEXT NOT NULL,
                                `kVA` REAL NOT NULL,
                                `voltage` INTEGER NOT NULL,
                                `percentZ` REAL NOT NULL,
                                `xr` REAL NOT NULL,
                                `txType` TEXT NOT NULL,
                                `sourceType` TEXT NOT NULL,
                                `status` TEXT NOT NULL,
                                `sizeMode` TEXT NOT NULL,
                                `manualKVA` REAL NOT NULL,
                                `demandFactor` REAL NOT NULL,
                                `diversity` REAL NOT NULL,
                                `growth` REAL NOT NULL,
                                `safety` REAL NOT NULL,
                                `vectorGroup` TEXT NOT NULL DEFAULT 'Dyn11',
                                `z0OverZ1` REAL NOT NULL DEFAULT 1.0,
                                `pf` REAL NOT NULL DEFAULT 0.8,
                                PRIMARY KEY(`transformerId`)
                            )
                        """.trimIndent())

                        // 2) generator_settings v4 table
                        db.execSQL("""
                            CREATE TABLE IF NOT EXISTS `generator_settings` (
                                `id` INTEGER NOT NULL PRIMARY KEY,
                                `additionalEssentialLoadKW` REAL NOT NULL,
                                `largestMotorKW` REAL NOT NULL,
                                `motorStartMethod` TEXT NOT NULL,
                                `motorPf` REAL NOT NULL,
                                `generatorPf` REAL NOT NULL,
                                `demandFactor` REAL NOT NULL,
                                `diversityFactor` REAL NOT NULL,
                                `stepLoadPct` REAL NOT NULL,
                                `oversizingFactor` REAL NOT NULL,
                                `enableManualOverride` INTEGER NOT NULL,
                                `manualSizeKVA` REAL
                            )
                        """.trimIndent())

                        // 3) ats_settings v4 table
                        db.execSQL("""
                            CREATE TABLE IF NOT EXISTS `ats_settings` (
                                `id` INTEGER NOT NULL PRIMARY KEY,
                                `systemVoltage` INTEGER NOT NULL,
                                `systemFrequency` INTEGER NOT NULL,
                                `numberOfSources` INTEGER NOT NULL,
                                `atsLocation` TEXT NOT NULL,
                                `transferType` TEXT NOT NULL,
                                `utilizationCategory` TEXT NOT NULL,
                                `atsClass` TEXT NOT NULL,
                                `loadCoverage` TEXT NOT NULL,
                                `ambientTemp` INTEGER NOT NULL,
                                `designSafetyFactor` REAL NOT NULL,
                                `futureGrowthFactor` REAL NOT NULL,
                                `approvedManufacturer` TEXT NOT NULL,
                                `mainsFailureDetectionDelay` INTEGER NOT NULL,
                                `generatorStartSignalDelay` INTEGER NOT NULL,
                                `generatorWarmUp` INTEGER NOT NULL,
                                `mainsReturnDetectionDelay` INTEGER NOT NULL,
                                `reTransferDelay` INTEGER NOT NULL,
                                `generatorCooldown` INTEGER NOT NULL,
                                `enclosureIpRating` TEXT NOT NULL
                            )
                        """.trimIndent())

                        // 4) ups_settings v4 table
                        db.execSQL("""
                            CREATE TABLE IF NOT EXISTS `ups_settings` (
                                `id` INTEGER NOT NULL PRIMARY KEY,
                                `systemVoltage` INTEGER NOT NULL,
                                `systemFrequency` INTEGER NOT NULL,
                                `upsPf` REAL NOT NULL,
                                `upsEfficiency` REAL NOT NULL,
                                `batteryType` TEXT NOT NULL,
                                `autonomyMinutes` INTEGER NOT NULL,
                                `batteryEodVoltage` REAL NOT NULL,
                                `ambientTemp` INTEGER NOT NULL,
                                `tempDeratingFactor` REAL NOT NULL,
                                `agingFactor` REAL NOT NULL,
                                `designMarginPct` REAL NOT NULL
                            )
                        """.trimIndent())

                        // 5) pf_global_settings v4 table
                        db.execSQL("""
                            CREATE TABLE IF NOT EXISTS `pf_global_settings` (
                                `id` INTEGER NOT NULL PRIMARY KEY,
                                `targetPf` REAL NOT NULL,
                                `kvarStepSize` REAL NOT NULL,
                                `systemVoltage` INTEGER NOT NULL,
                                `annualOperatingHours` INTEGER NOT NULL,
                                `energyCost` REAL NOT NULL,
                                `lossReductionPct` REAL NOT NULL,
                                `unityToleranceKvar` REAL NOT NULL
                            )
                        """.trimIndent())

                        // 6) pf_transformer_settings v4 table
                        db.execSQL("""
                            CREATE TABLE IF NOT EXISTS `pf_transformer_settings` (
                                `transformerId` TEXT NOT NULL PRIMARY KEY,
                                `harmonicFilter` TEXT NOT NULL,
                                `detuningReactor` TEXT NOT NULL
                            )
                        """.trimIndent())
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                        // Unused during creation
                    }
                })
                .build()
        )

        val db = openHelper.writableDatabase

        // Insert legacy data into v4 tables
        db.execSQL("""
            INSERT INTO `transformers` 
            (`projectId`, `transformerId`, `transformerName`, `kVA`, `voltage`, `percentZ`, `xr`, `txType`, `sourceType`, `status`, `sizeMode`, `manualKVA`, `demandFactor`, `diversity`, `growth`, `safety`, `vectorGroup`, `z0OverZ1`, `pf`)
            VALUES ('DEFAULT_PROJECT', 'TX-01', 'Legacy Transformer', 500.0, 400, 4.0, 10.0, 'Oil', 'Medium Voltage', 'Active', 'Auto', 500.0, 0.8, 1.0, 1.0, 1.0, 'Dyn11', 1.0, 0.8)
        """.trimIndent())

        db.execSQL("""
            INSERT INTO `generator_settings` 
            (`id`, `additionalEssentialLoadKW`, `largestMotorKW`, `motorStartMethod`, `motorPf`, `generatorPf`, `demandFactor`, `diversityFactor`, `stepLoadPct`, `oversizingFactor`, `enableManualOverride`, `manualSizeKVA`)
            VALUES (1, 10.0, 5.0, 'DOL', 0.85, 0.8, 0.8, 1.0, 50.0, 1.0, 0, NULL)
        """.trimIndent())

        db.execSQL("""
            INSERT INTO `ats_settings` 
            (`id`, `systemVoltage`, `systemFrequency`, `numberOfSources`, `atsLocation`, `transferType`, `utilizationCategory`, `atsClass`, `loadCoverage`, `ambientTemp`, `designSafetyFactor`, `futureGrowthFactor`, `approvedManufacturer`, `mainsFailureDetectionDelay`, `generatorStartSignalDelay`, `generatorWarmUp`, `mainsReturnDetectionDelay`, `reTransferDelay`, `generatorCooldown`, `enclosureIpRating`)
            VALUES (1, 400, 50, 2, 'After Main Device', 'Open Transition', 'AC-31A', 'PC', 'Emergency', 45, 1.25, 10.0, 'ABB', 3, 0, 7, 60, 10, 300, 'IP54')
        """.trimIndent())

        db.execSQL("""
            INSERT INTO `ups_settings` 
            (`id`, `systemVoltage`, `systemFrequency`, `upsPf`, `upsEfficiency`, `batteryType`, `autonomyMinutes`, `batteryEodVoltage`, `ambientTemp`, `tempDeratingFactor`, `agingFactor`, `designMarginPct`)
            VALUES (1, 400, 50, 0.9, 94.0, 'VRLA', 15, 1.67, 25, 1.0, 1.25, 20.0)
        """.trimIndent())

        db.execSQL("""
            INSERT INTO `pf_global_settings` 
            (`id`, `targetPf`, `kvarStepSize`, `systemVoltage`, `annualOperatingHours`, `energyCost`, `lossReductionPct`, `unityToleranceKvar`)
            VALUES (1, 0.97, 50.0, 400, 6000, 1.5, 0.03, 0.5)
        """.trimIndent())

        db.execSQL("""
            INSERT INTO `pf_transformer_settings` 
            (`transformerId`, `harmonicFilter`, `detuningReactor`)
            VALUES ('TX-01', 'Not Provided', 'Not Provided')
        """.trimIndent())

        // Now run the actual migration MIGRATION_4_5
        MIGRATION_4_5.migrate(db)

        // Verify transformers schema & composite primary keys
        val txCursor = db.query("SELECT * FROM transformers")
        assertTrue(txCursor.moveToFirst())
        assertEquals("DEFAULT_PROJECT", txCursor.getString(txCursor.getColumnIndexOrThrow("projectId")))
        assertEquals("TX-01", txCursor.getString(txCursor.getColumnIndexOrThrow("transformerId")))
        assertEquals("Legacy Transformer", txCursor.getString(txCursor.getColumnIndexOrThrow("transformerName")))
        txCursor.close()

        // Verify generator_settings
        val genCursor = db.query("SELECT * FROM generator_settings")
        assertTrue(genCursor.moveToFirst())
        assertEquals("DEFAULT_PROJECT", genCursor.getString(genCursor.getColumnIndexOrThrow("projectId")))
        assertEquals(1, genCursor.getInt(genCursor.getColumnIndexOrThrow("id")))
        assertEquals(10.0, genCursor.getDouble(genCursor.getColumnIndexOrThrow("additionalEssentialLoadKW")), 0.01)
        genCursor.close()

        // Verify ats_settings
        val atsCursor = db.query("SELECT * FROM ats_settings")
        assertTrue(atsCursor.moveToFirst())
        assertEquals("DEFAULT_PROJECT", atsCursor.getString(atsCursor.getColumnIndexOrThrow("projectId")))
        assertEquals(1, atsCursor.getInt(atsCursor.getColumnIndexOrThrow("id")))
        atsCursor.close()

        // Verify ups_settings
        val upsCursor = db.query("SELECT * FROM ups_settings")
        assertTrue(upsCursor.moveToFirst())
        assertEquals("DEFAULT_PROJECT", upsCursor.getString(upsCursor.getColumnIndexOrThrow("projectId")))
        assertEquals(1, upsCursor.getInt(upsCursor.getColumnIndexOrThrow("id")))
        upsCursor.close()

        // Verify pf_global_settings
        val pfgCursor = db.query("SELECT * FROM pf_global_settings")
        assertTrue(pfgCursor.moveToFirst())
        assertEquals("DEFAULT_PROJECT", pfgCursor.getString(pfgCursor.getColumnIndexOrThrow("projectId")))
        assertEquals(1, pfgCursor.getInt(pfgCursor.getColumnIndexOrThrow("id")))
        pfgCursor.close()

        // Verify pf_transformer_settings
        val pftCursor = db.query("SELECT * FROM pf_transformer_settings")
        assertTrue(pftCursor.moveToFirst())
        assertEquals("DEFAULT_PROJECT", pftCursor.getString(pftCursor.getColumnIndexOrThrow("projectId")))
        assertEquals("TX-01", pftCursor.getString(pftCursor.getColumnIndexOrThrow("transformerId")))
        pftCursor.close()

        println("MIGRATION_VERIFIED_A_PASS")
    }

    @Test
    fun testIsolationAndCoexistence() = runBlocking {
        AppDatabase.useTestExecutors = true
        val app = ApplicationProvider.getApplicationContext<Application>()
        Catalog.loadAll(app)
        val db = AppDatabase.getDatabase(app)
        db.circuitDao().deleteCircuitsForProject("PROJECT_A")
        db.circuitDao().deleteCircuitsForProject("PROJECT_B")
        db.transformerDao().deleteTransformersForProject("PROJECT_A")
        db.transformerDao().deleteTransformersForProject("PROJECT_B")
        db.projectDao().deleteProject("PROJECT_A")
        db.projectDao().deleteProject("PROJECT_B")
        val viewModel = EngineeringViewModel(app)

        // Wait for ViewModel startup initialization to be 100% Ready (so it doesn't race and overwrite projectId with null).
        // Poll until any TERMINAL startup state is reached — the startup flow also
        // passes through LoadingProjects / RestoringApplicationState while it awaits
        // the catalog load (Catalog.loadAll on Dispatchers.IO), which can outlast a
        // wait that only watches OpeningDatabase/ValidatingCableCatalog under suite
        // load.
        val isTerminalStartupState = { state: StartupState ->
            state is StartupState.Ready || state is StartupState.NoProjects ||
                state is StartupState.ReadyWithWarnings || state is StartupState.DatabaseError ||
                state is StartupState.CatalogError || state is StartupState.FatalError
        }
        var elapsed = 0
        while (!isTerminalStartupState(viewModel.startupState.value) && elapsed < 120) {
            println("CURRENT_STARTUP_STATE: ${viewModel.startupState.value}")
            delayAndIdle(100)
            elapsed++
        }
        assertTrue("Startup state should finish initialization", isTerminalStartupState(viewModel.startupState.value))


        // Scenario H: Null activeProjectId causes no writes and reports visible error
        assertNull(viewModel.activeProjectId.value)
        assertNull(viewModel.projectError.value)

        val dummyTx = TransformerEntity(
            projectId = "DEFAULT_PROJECT",
            transformerId = "TX-01",
            transformerName = "Should Not Exist",
            kVA = 500.0,
            voltage = 400,
            percentZ = 4.0,
            xr = 10.0,
            txType = "Oil",
            sourceType = "Medium Voltage",
            status = "Active",
            sizeMode = "Auto",
            manualKVA = 500.0,
            demandFactor = 0.8,
            diversity = 1.0,
            growth = 1.0,
            safety = 1.0
        )

        // Attempting to insert when projectId is null
        viewModel.insertTransformer(dummyTx)
        delayAndIdle()
        assertNotNull(viewModel.projectError.value)
        assertTrue(viewModel.projectError.value!!.contains("Cannot insert transformer"))

        // Reset error
        viewModel.projectError.value = null

        // Scenario B: Project A and Project B coexistence
        // Create projects in DB
        db.projectDao().upsertProject(ProjectEntity("PROJECT_A", "Project A", createdAt = 0, updatedAt = 0))
        db.projectDao().upsertProject(ProjectEntity("PROJECT_B", "Project B", createdAt = 0, updatedAt = 0))

        // Set active project to A
        viewModel.openProject("PROJECT_A")
        delayAndIdle()
        assertEquals("PROJECT_A", viewModel.activeProjectId.value)

        // Insert TX-01 in Project A
        val txA = dummyTx.copy(projectId = "PROJECT_A", transformerName = "Transformer A")
        viewModel.insertTransformer(txA)
        delayAndIdle()

        // Switch to Project B
        viewModel.openProject("PROJECT_B")
        delayAndIdle()
        assertEquals("PROJECT_B", viewModel.activeProjectId.value)

        // Insert TX-01 in Project B (same ID, different name and capacity)
        val txB = dummyTx.copy(projectId = "PROJECT_B", transformerName = "Transformer B", kVA = 1000.0)
        viewModel.insertTransformer(txB)
        delayAndIdle()

        // Retrieve both from DB and verify coexistence
        val txListA = db.transformerDao().getTransformersForProject("PROJECT_A").first()
        val txListB = db.transformerDao().getTransformersForProject("PROJECT_B").first()

        assertEquals(1, txListA.size)
        assertEquals(1, txListB.size)
        assertEquals("Transformer A", txListA[0].transformerName)
        assertEquals("Transformer B", txListB[0].transformerName)
        assertEquals(500.0, txListA[0].kVA, 0.01)
        assertEquals(1000.0, txListB[0].kVA, 0.01)

        println("COEXISTENCE_VERIFIED_B_PASS")

        // Scenario C: Editing B/TX-01 does not change A/TX-01
        viewModel.openProject("PROJECT_B")
        delayAndIdle()
        val updatedTxB = txB.copy(transformerName = "Transformer B Edited", kVA = 1250.0)
        viewModel.updateTransformer(updatedTxB)
        delayAndIdle()

        val txListA_afterEdit = db.transformerDao().getTransformersForProject("PROJECT_A").first()
        val txListB_afterEdit = db.transformerDao().getTransformersForProject("PROJECT_B").first()

        assertEquals("Transformer A", txListA_afterEdit[0].transformerName)
        assertEquals(500.0, txListA_afterEdit[0].kVA, 0.01)
        assertEquals("Transformer B Edited", txListB_afterEdit[0].transformerName)
        assertEquals(1250.0, txListB_afterEdit[0].kVA, 0.01)

        println("EDIT_ISOLATION_VERIFIED_C_PASS")

        // Scenario F: Generator/ATS/UPS/PF settings differ between A and B
        viewModel.openProject("PROJECT_A")
        delayAndIdle()
        val genA = GeneratorEntity(projectId = "PROJECT_A", additionalEssentialLoadKW = 100.0)
        viewModel.saveGeneratorSettings(genA)
        delayAndIdle()

        viewModel.openProject("PROJECT_B")
        delayAndIdle()
        val genB = GeneratorEntity(projectId = "PROJECT_B", additionalEssentialLoadKW = 250.0)
        viewModel.saveGeneratorSettings(genB)
        delayAndIdle()

        val genA_db = db.powerSystemsDao().getGeneratorSettings("PROJECT_A").first()
        val genB_db = db.powerSystemsDao().getGeneratorSettings("PROJECT_B").first()

        assertNotNull(genA_db)
        assertNotNull(genB_db)
        assertEquals(100.0, genA_db!!.additionalEssentialLoadKW, 0.01)
        assertEquals(250.0, genB_db!!.additionalEssentialLoadKW, 0.01)

        println("SETTINGS_ISOLATION_VERIFIED_F_PASS")

        // Scenario E: Start New Empty Project in B clears only B
        // Add circuit to B
        val circuitB = CircuitEntity(
            id = 100,
            circuitName = "Circuit B",
            loadKVA = 20.0,
            fedFrom = "Transformer",
            transformerId = "TX-01",
            loadType = "Normal",
            phases = "3P+N",
            voltageV = 400,
            pf = 0.8,
            safetyFactor = 1.0,
            manufacturer = "Elsewedy",
            cableType = "Al/XLPE/STA/PVC - 4C",
            installation = "Laid in Free Air",
            parallelRuns = 1,
            airTempC = 40,
            gndTempC = 40,
            burialDepth_m = 0.6,
            soilRes = 125,
            noCircuits = 1,
            formation = "Flat",
            spacing = "Touching",
            length_m = 50.0,
            maxVDpct = 0.03,
            projectId = "PROJECT_B"
        )
        db.circuitDao().insertCircuit(circuitB)

        // Add circuit to A
        val circuitA = CircuitEntity(
            id = 101,
            circuitName = "Circuit A",
            loadKVA = 15.0,
            fedFrom = "Transformer",
            transformerId = "TX-01",
            loadType = "Normal",
            phases = "3P+N",
            voltageV = 400,
            pf = 0.8,
            safetyFactor = 1.0,
            manufacturer = "Elsewedy",
            cableType = "Al/XLPE/STA/PVC - 4C",
            installation = "Laid in Free Air",
            parallelRuns = 1,
            airTempC = 40,
            gndTempC = 40,
            burialDepth_m = 0.6,
            soilRes = 125,
            noCircuits = 1,
            formation = "Flat",
            spacing = "Touching",
            length_m = 40.0,
            maxVDpct = 0.03,
            projectId = "PROJECT_A"
        )
        db.circuitDao().insertCircuit(circuitA)

        // Start new empty project in B
        viewModel.openProject("PROJECT_B")
        delayAndIdle()
        viewModel.startNewEmptyProject()

        // Wait for B to be cleared
        var circuitsB_afterEmpty = db.circuitDao().getCircuitsListForProject("PROJECT_B")
        var txsB_afterEmpty = db.transformerDao().getTransformersForProject("PROJECT_B").first()
        var pollE = 0
        while ((circuitsB_afterEmpty.size != 0 || txsB_afterEmpty.size != 0) && pollE < 50) {
            delayAndIdle(50)
            circuitsB_afterEmpty = db.circuitDao().getCircuitsListForProject("PROJECT_B")
            txsB_afterEmpty = db.transformerDao().getTransformersForProject("PROJECT_B").first()
            pollE++
        }
        assertEquals(0, circuitsB_afterEmpty.size)
        assertEquals(0, txsB_afterEmpty.size)

        // A should remain completely untouched
        val circuitsA_afterEmpty = db.circuitDao().getCircuitsListForProject("PROJECT_A")
        val txsA_afterEmpty = db.transformerDao().getTransformersForProject("PROJECT_A").first()
        assertEquals(1, circuitsA_afterEmpty.size)
        assertEquals(1, txsA_afterEmpty.size)
        assertEquals("Circuit A", circuitsA_afterEmpty[0].circuitName)
        assertEquals("Transformer A", txsA_afterEmpty[0].transformerName)

        println("START_NEW_EMPTY_PROJECT_VERIFIED_E_PASS")

        // Scenario D: Deleting B does not affect A
        viewModel.deleteProject("PROJECT_B")

        // Wait for B to be deleted
        var circuitsB_afterDel = db.circuitDao().getCircuitsListForProject("PROJECT_B")
        var txsB_afterDel = db.transformerDao().getTransformersForProject("PROJECT_B").first()
        var pollD = 0
        while ((circuitsB_afterDel.size != 0 || txsB_afterDel.size != 0) && pollD < 50) {
            delayAndIdle(50)
            circuitsB_afterDel = db.circuitDao().getCircuitsListForProject("PROJECT_B")
            txsB_afterDel = db.transformerDao().getTransformersForProject("PROJECT_B").first()
            pollD++
        }
        assertEquals(0, circuitsB_afterDel.size)
        assertEquals(0, txsB_afterDel.size)

        // A should still remain untouched
        val circuitsA_afterDel = db.circuitDao().getCircuitsListForProject("PROJECT_A")
        val txsA_afterDel = db.transformerDao().getTransformersForProject("PROJECT_A").first()
        assertEquals(1, circuitsA_afterDel.size)
        assertEquals(1, txsA_afterDel.size)

        println("DELETE_ISOLATION_VERIFIED_D_PASS")

        // Scenario I: Missing transformer is reported and not fabricated
        // Verify the deterministic engine guard directly — this is the same code path
        // the ViewModel uses, but avoids WhileSubscribed flow timing under suite load.
        val circuitA_missingTx = CircuitEntity(
            id = 102,
            circuitName = "Circuit with missing TX",
            loadKVA = 10.0,
            fedFrom = "Transformer",
            transformerId = "TX-99", // Non-existent
            loadType = "Normal",
            phases = "3P+N",
            voltageV = 400,
            pf = 0.8,
            safetyFactor = 1.0,
            manufacturer = "Elsewedy",
            cableType = "Al/XLPE/STA/PVC - 4C",
            installation = "Laid in Free Air",
            parallelRuns = 1,
            airTempC = 40,
            gndTempC = 40,
            burialDepth_m = 0.6,
            soilRes = 125,
            noCircuits = 1,
            formation = "Flat",
            spacing = "Touching",
            length_m = 30.0,
            maxVDpct = 0.03,
            projectId = "PROJECT_A"
        )

        // Call the deterministic sizing engine directly with an empty transformer list
        // — this is the exact same guard CableCalculationService applies during recalculate()
        val sizingResult = CableCalculationService.calculateSingleCircuit(
            circuit = circuitA_missingTx,
            transformers = emptyList() // TX-99 does not exist
        )

        assertEquals("✗ FAIL", sizingResult.status)
        assertTrue(
            "Expected failReason to mention missing TX-99 but was: ${sizingResult.failReason}",
            sizingResult.failReason.contains("Missing source transformer 'TX-99'")
        )

        println("MISSING_TRANSFORMER_GUARD_VERIFIED_I_PASS")
    }

    @Test
    fun testMigration18To19_ExistingProjectLoadsWithDefaults() {
        val openHelper = FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext())
                .name(null)
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(18) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("""
                            CREATE TABLE IF NOT EXISTS `generator_settings` (
                                `projectId` TEXT NOT NULL DEFAULT 'DEFAULT_PROJECT',
                                `id` INTEGER NOT NULL,
                                `additionalEssentialLoadKW` REAL NOT NULL,
                                `largestMotorKW` REAL NOT NULL,
                                `motorStartMethod` TEXT NOT NULL,
                                `motorPf` REAL NOT NULL,
                                `generatorPf` REAL NOT NULL,
                                `demandFactor` REAL NOT NULL,
                                `diversityFactor` REAL NOT NULL,
                                `stepLoadPct` REAL NOT NULL,
                                `oversizingFactor` REAL NOT NULL,
                                `enableManualOverride` INTEGER NOT NULL,
                                `manualSizeKVA` REAL,
                                PRIMARY KEY(`projectId`, `id`)
                            )
                        """.trimIndent())
                        db.execSQL("""
                            CREATE TABLE IF NOT EXISTS `ats_settings` (
                                `projectId` TEXT NOT NULL DEFAULT 'DEFAULT_PROJECT',
                                `id` INTEGER NOT NULL,
                                `systemVoltage` INTEGER NOT NULL,
                                `systemFrequency` INTEGER NOT NULL,
                                `numberOfSources` INTEGER NOT NULL,
                                `atsLocation` TEXT NOT NULL,
                                `transferType` TEXT NOT NULL,
                                `utilizationCategory` TEXT NOT NULL,
                                `atsClass` TEXT NOT NULL,
                                `loadCoverage` TEXT NOT NULL,
                                `ambientTemp` INTEGER NOT NULL,
                                `designSafetyFactor` REAL NOT NULL,
                                `futureGrowthFactor` REAL NOT NULL,
                                `approvedManufacturer` TEXT NOT NULL,
                                `mainsFailureDetectionDelay` INTEGER NOT NULL,
                                `generatorStartSignalDelay` INTEGER NOT NULL,
                                `generatorWarmUp` INTEGER NOT NULL,
                                `mainsReturnDetectionDelay` INTEGER NOT NULL,
                                `reTransferDelay` INTEGER NOT NULL,
                                `generatorCooldown` INTEGER NOT NULL,
                                `enclosureIpRating` TEXT NOT NULL,
                                `normalSourceId` TEXT,
                                `emergencySourceId` TEXT,
                                `outputBoardId` INTEGER,
                                PRIMARY KEY(`projectId`, `id`)
                            )
                        """.trimIndent())
                        db.execSQL("""
                            CREATE TABLE IF NOT EXISTS `ups_settings` (
                                `projectId` TEXT NOT NULL DEFAULT 'DEFAULT_PROJECT',
                                `id` INTEGER NOT NULL,
                                `systemVoltage` INTEGER NOT NULL,
                                `systemFrequency` INTEGER NOT NULL,
                                `upsPf` REAL NOT NULL,
                                `upsEfficiency` REAL NOT NULL,
                                `batteryType` TEXT NOT NULL,
                                `autonomyMinutes` INTEGER NOT NULL,
                                `batteryEodVoltage` REAL NOT NULL,
                                `ambientTemp` INTEGER NOT NULL,
                                `tempDeratingFactor` REAL NOT NULL,
                                `agingFactor` REAL NOT NULL,
                                `designMarginPct` REAL NOT NULL,
                                PRIMARY KEY(`projectId`, `id`)
                            )
                        """.trimIndent())
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
        )

        val db = openHelper.writableDatabase
        db.execSQL("INSERT INTO `generator_settings` (`projectId`, `id`, `additionalEssentialLoadKW`, `largestMotorKW`, `motorStartMethod`, `motorPf`, `generatorPf`, `demandFactor`, `diversityFactor`, `stepLoadPct`, `oversizingFactor`, `enableManualOverride`, `manualSizeKVA`) VALUES ('PROJ-MIG', 1, 150.0, 45.0, 'DOL', 0.85, 0.8, 0.8, 1.0, 50.0, 1.0, 0, NULL)")
        db.execSQL("INSERT INTO `ats_settings` (`projectId`, `id`, `systemVoltage`, `systemFrequency`, `numberOfSources`, `atsLocation`, `transferType`, `utilizationCategory`, `atsClass`, `loadCoverage`, `ambientTemp`, `designSafetyFactor`, `futureGrowthFactor`, `approvedManufacturer`, `mainsFailureDetectionDelay`, `generatorStartSignalDelay`, `generatorWarmUp`, `mainsReturnDetectionDelay`, `reTransferDelay`, `generatorCooldown`, `enclosureIpRating`, `normalSourceId`, `emergencySourceId`, `outputBoardId`) VALUES ('PROJ-MIG', 1, 400, 50, 2, 'After Main Device', 'Open Transition', 'AC-31A (Resistive)', 'PC (Transfer Switch)', 'Emergency Only', 45, 1.25, 10.0, 'ABB', 3, 0, 7, 60, 10, 300, 'IP54', NULL, NULL, NULL)")
        db.execSQL("INSERT INTO `ups_settings` (`projectId`, `id`, `systemVoltage`, `systemFrequency`, `upsPf`, `upsEfficiency`, `batteryType`, `autonomyMinutes`, `batteryEodVoltage`, `ambientTemp`, `tempDeratingFactor`, `agingFactor`, `designMarginPct`) VALUES ('PROJ-MIG', 1, 400, 50, 0.9, 94.0, 'VRLA', 20, 1.67, 25, 1.0, 1.25, 20.0)")

        // Run Migration 18 -> 19
        MIGRATION_18_19.migrate(db)

        // Verify Generator Migration
        val genCursor = db.query("SELECT * FROM `generator_settings` WHERE `projectId` = 'PROJ-MIG' AND `id` = 1")
        assertTrue(genCursor.moveToFirst())
        assertEquals(150.0, genCursor.getDouble(genCursor.getColumnIndexOrThrow("additionalEssentialLoadKW")), 0.01)
        assertEquals("GEN-01", genCursor.getString(genCursor.getColumnIndexOrThrow("generatorId")))
        assertEquals("Generator 1", genCursor.getString(genCursor.getColumnIndexOrThrow("generatorName")))
        genCursor.close()

        // Verify ATS Migration
        val atsCursor = db.query("SELECT * FROM `ats_settings` WHERE `projectId` = 'PROJ-MIG' AND `id` = 1")
        assertTrue(atsCursor.moveToFirst())
        assertEquals(400, atsCursor.getInt(atsCursor.getColumnIndexOrThrow("systemVoltage")))
        assertEquals("ATS-01", atsCursor.getString(atsCursor.getColumnIndexOrThrow("atsId")))
        assertEquals("ATS 1", atsCursor.getString(atsCursor.getColumnIndexOrThrow("atsName")))
        assertEquals(0, atsCursor.getInt(atsCursor.getColumnIndexOrThrow("enableManualOverride")))
        assertTrue(atsCursor.isNull(atsCursor.getColumnIndexOrThrow("manualSizeAmp")))
        atsCursor.close()

        // Verify UPS Migration
        val upsCursor = db.query("SELECT * FROM `ups_settings` WHERE `projectId` = 'PROJ-MIG' AND `id` = 1")
        assertTrue(upsCursor.moveToFirst())
        assertEquals(20, upsCursor.getInt(upsCursor.getColumnIndexOrThrow("autonomyMinutes")))
        assertEquals("UPS-01", upsCursor.getString(upsCursor.getColumnIndexOrThrow("upsId")))
        assertEquals("UPS 1", upsCursor.getString(upsCursor.getColumnIndexOrThrow("upsName")))
        assertEquals(0, upsCursor.getInt(upsCursor.getColumnIndexOrThrow("enableManualOverride")))
        assertTrue(upsCursor.isNull(upsCursor.getColumnIndexOrThrow("manualSizeKVA")))
        upsCursor.close()

        db.close()
        println("MIGRATION_18_TO_19_FUNCTIONAL_VERIFICATION_PASS")
    }
}
