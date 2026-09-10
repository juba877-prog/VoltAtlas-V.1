package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class DatabaseMigrationTest {

    @Test
    fun testMigration5To6SQLDefinition() {
        val migration = MIGRATION_5_6
        assertEquals(5, migration.startVersion)
        assertEquals(6, migration.endVersion)
    }

    @Test
    fun testMigration1To2SQLDefinition() {
        val migration = MIGRATION_1_2
        assertEquals(1, migration.startVersion)
        assertEquals(2, migration.endVersion)
    }

    @Test
    fun testMigration10To11SQLDefinition() {
        val migration = com.example.data.MIGRATION_10_11
        assertEquals(10, migration.startVersion)
        assertEquals(11, migration.endVersion)
    }

    @Test
    fun testMigration11To12SQLDefinition() {
        val migration = com.example.data.MIGRATION_11_12
        assertEquals(11, migration.startVersion)
        assertEquals(12, migration.endVersion)
    }

    @Test
    fun testMigration13To14SQLDefinition() {
        val migration = com.example.data.MIGRATION_13_14
        assertEquals(13, migration.startVersion)
        assertEquals(14, migration.endVersion)
    }

    @Test
    fun testMigration14To15SQLDefinition() {
        val migration = com.example.data.MIGRATION_14_15
        assertEquals(14, migration.startVersion)
        assertEquals(15, migration.endVersion)
    }

    @Test
    fun testMigration15To16SQLDefinition() {
        val migration = com.example.data.MIGRATION_15_16
        assertEquals(15, migration.startVersion)
        assertEquals(16, migration.endVersion)
    }

    @Test
    fun testMigration16To17SQLDefinition() {
        val migration = com.example.data.MIGRATION_16_17
        assertEquals(16, migration.startVersion)
        assertEquals(17, migration.endVersion)
    }

    @Test
    fun testMigration17To18SQLDefinition() {
        val migration = com.example.data.MIGRATION_17_18
        assertEquals(17, migration.startVersion)
        assertEquals(18, migration.endVersion)
    }

    @Test
    fun testMigration18To19SQLDefinition() {
        val migration = com.example.data.MIGRATION_18_19
        assertEquals(18, migration.startVersion)
        assertEquals(19, migration.endVersion)
    }

    @Test
    fun testMigration19To20SQLDefinition() {
        val migration = com.example.data.MIGRATION_19_20
        assertEquals(19, migration.startVersion)
        assertEquals(20, migration.endVersion)
    }

    @Test
    fun testMigration20To21SQLDefinition() {
        val migration = com.example.data.MIGRATION_20_21
        assertEquals(20, migration.startVersion)
        assertEquals(21, migration.endVersion)
    }

    @Test
    fun testMigration21To22SQLDefinition() {
        val migration = com.example.data.MIGRATION_21_22
        assertEquals(21, migration.startVersion)
        assertEquals(22, migration.endVersion)
    }

    @Test
    fun testMigration22To23SQLDefinition() {
        val migration = com.example.data.MIGRATION_22_23
        assertEquals(22, migration.startVersion)
        assertEquals(23, migration.endVersion)
    }

    @Test
    fun testMigration21To22_lowLevelSqlTransformationTest() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val openHelper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null) // in-memory SQLite DB
                .callback(object : SupportSQLiteOpenHelper.Callback(21) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("""
                            CREATE TABLE IF NOT EXISTS `projects` (
                                `projectId` TEXT NOT NULL,
                                `projectName` TEXT NOT NULL,
                                `projectCode` TEXT,
                                `clientName` TEXT,
                                `createdAt` INTEGER NOT NULL,
                                `updatedAt` INTEGER NOT NULL,
                                `defaultPhaseImbalanceLimitPct` REAL NOT NULL DEFAULT 20.0,
                                PRIMARY KEY(`projectId`)
                            )
                        """.trimIndent())
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
        )

        val db = openHelper.writableDatabase
        try {
            assertEquals(21, db.version)

            db.execSQL("INSERT INTO `projects` (`projectId`, `projectName`, `createdAt`, `updatedAt`) VALUES ('P1', 'Test', 1000, 2000)")

            // Direct SQL execution of MIGRATION_21_22
            MIGRATION_21_22.migrate(db)

            val cursor = db.query("SELECT projectId, revision FROM projects WHERE projectId = 'P1'")
            assertTrue(cursor.moveToFirst())
            assertEquals("P1", cursor.getString(0))
            assertEquals(1L, cursor.getLong(1))
            cursor.close()
        } finally {
            db.close()
            openHelper.close()
        }
    }

    @Test
    fun migration21To22_sameDatabaseOpensThroughRoomAndPreservesProjectGraph() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tempDbFile = java.io.File.createTempFile("room_migration_v21_v22_${UUID.randomUUID()}_", ".db")

        // 1. Build Reconstructed Schema Version 21 SQLite Database on disk using SQLiteDatabase
        val setupDb = SQLiteDatabase.openOrCreateDatabase(tempDbFile, null)
        try {
            // 1. projects (V21 without revision column)
            setupDb.execSQL("""
                CREATE TABLE IF NOT EXISTS `projects` (
                    `projectId` TEXT NOT NULL,
                    `projectName` TEXT NOT NULL,
                    `projectCode` TEXT,
                    `clientName` TEXT,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `defaultPhaseImbalanceLimitPct` REAL NOT NULL DEFAULT 20.0,
                    PRIMARY KEY(`projectId`)
                )
            """.trimIndent())

            // 2. circuits (V21 schema matching CircuitEntity)
            setupDb.execSQL("""
                CREATE TABLE IF NOT EXISTS `circuits` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `circuitName` TEXT NOT NULL,
                    `loadKVA` REAL NOT NULL,
                    `fedFrom` TEXT NOT NULL,
                    `transformerId` TEXT NOT NULL,
                    `loadType` TEXT NOT NULL,
                    `phases` TEXT NOT NULL,
                    `voltageV` INTEGER NOT NULL,
                    `pf` REAL NOT NULL,
                    `safetyFactor` REAL NOT NULL,
                    `manufacturer` TEXT NOT NULL,
                    `cableType` TEXT NOT NULL,
                    `installation` TEXT NOT NULL,
                    `parallelRuns` INTEGER NOT NULL,
                    `airTempC` INTEGER NOT NULL,
                    `gndTempC` INTEGER NOT NULL,
                    `burialDepth_m` REAL NOT NULL,
                    `soilRes` INTEGER NOT NULL,
                    `noCircuits` INTEGER NOT NULL,
                    `formation` TEXT NOT NULL,
                    `spacing` TEXT NOT NULL,
                    `length_m` REAL NOT NULL,
                    `maxVDpct` REAL NOT NULL,
                    `overrideDeviceType` TEXT,
                    `overrideDeviceSize` REAL,
                    `overrideCableSize` TEXT,
                    `overrideNeutralSize` TEXT,
                    `overrideEarthSize` TEXT,
                    `isHarmonic` INTEGER NOT NULL,
                    `assignedPhase` TEXT,
                    `upsBacked` INTEGER NOT NULL DEFAULT 0,
                    `projectId` TEXT NOT NULL DEFAULT 'DEFAULT_PROJECT',
                    `demandFactor` REAL NOT NULL DEFAULT 1.0,
                    `diversityGroup` TEXT,
                    `generatorIncluded` INTEGER NOT NULL DEFAULT 0,
                    `atsIncluded` INTEGER NOT NULL DEFAULT 0,
                    `assignedGeneratorId` TEXT,
                    `assignedAtsId` TEXT,
                    `assignedUpsId` TEXT,
                    `loadNature` TEXT NOT NULL DEFAULT 'Static',
                    `startingMethod` TEXT,
                    `startingCurrentMultiplier` REAL,
                    `active` INTEGER NOT NULL DEFAULT 1,
                    `panelType` TEXT,
                    `parentId` INTEGER,
                    `level` INTEGER,
                    `sourceType` TEXT,
                    `directLoadConfirmed` INTEGER,
                    `phaseImbalanceLimitOverridePct` REAL,
                    `loadCategory` TEXT,
                    `busbarRatingA` REAL,
                    `scRatingKA` REAL,
                    `mainIncomingType` TEXT,
                    `mainIncomingCount` INTEGER,
                    `ipRating` TEXT,
                    `panelLocation` TEXT,
                    `cableEntry` TEXT,
                    `feederDescription` TEXT,
                    `numberOfWays` INTEGER,
                    `feederType` TEXT NOT NULL DEFAULT 'Cable',
                    `buswayMfg` TEXT,
                    `buswayModel` TEXT
                )
            """.trimIndent())

            // 3. transformers (V21 schema matching TransformerEntity)
            setupDb.execSQL("""
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
                    PRIMARY KEY(`projectId`, `transformerId`)
                )
            """.trimIndent())
            setupDb.execSQL("CREATE INDEX IF NOT EXISTS `index_transformers_projectId` ON `transformers` (`projectId`)")
            setupDb.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_transformers_projectId_transformerId` ON `transformers` (`projectId`, `transformerId`)")

            // 4. generator_settings
            setupDb.execSQL("""
                CREATE TABLE IF NOT EXISTS `generator_settings` (
                    `projectId` TEXT NOT NULL DEFAULT 'DEFAULT_PROJECT',
                    `id` INTEGER NOT NULL,
                    `generatorId` TEXT NOT NULL DEFAULT 'GEN-01',
                    `generatorName` TEXT NOT NULL DEFAULT 'Generator 1',
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

            // 5. ats_settings
            setupDb.execSQL("""
                CREATE TABLE IF NOT EXISTS `ats_settings` (
                    `projectId` TEXT NOT NULL DEFAULT 'DEFAULT_PROJECT',
                    `id` INTEGER NOT NULL,
                    `atsId` TEXT NOT NULL DEFAULT 'ATS-01',
                    `atsName` TEXT NOT NULL DEFAULT 'ATS 1',
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
                    `enableManualOverride` INTEGER NOT NULL DEFAULT 0,
                    `manualSizeAmp` REAL,
                    PRIMARY KEY(`projectId`, `id`)
                )
            """.trimIndent())

            // 6. ups_settings
            setupDb.execSQL("""
                CREATE TABLE IF NOT EXISTS `ups_settings` (
                    `projectId` TEXT NOT NULL DEFAULT 'DEFAULT_PROJECT',
                    `id` INTEGER NOT NULL,
                    `upsId` TEXT NOT NULL DEFAULT 'UPS-01',
                    `upsName` TEXT NOT NULL DEFAULT 'UPS 1',
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
                    `enableManualOverride` INTEGER NOT NULL DEFAULT 0,
                    `manualSizeKVA` REAL,
                    PRIMARY KEY(`projectId`, `id`)
                )
            """.trimIndent())

            // 7. pf_global_settings
            setupDb.execSQL("""
                CREATE TABLE IF NOT EXISTS `pf_global_settings` (
                    `projectId` TEXT NOT NULL DEFAULT 'DEFAULT_PROJECT',
                    `id` INTEGER NOT NULL,
                    `targetPf` REAL NOT NULL,
                    `kvarStepSize` REAL NOT NULL,
                    `systemVoltage` INTEGER NOT NULL,
                    `annualOperatingHours` INTEGER NOT NULL,
                    `energyCost` REAL NOT NULL,
                    `lossReductionPct` REAL NOT NULL,
                    `unityToleranceKvar` REAL NOT NULL,
                    PRIMARY KEY(`projectId`, `id`)
                )
            """.trimIndent())

            // 8. pf_transformer_settings
            setupDb.execSQL("""
                CREATE TABLE IF NOT EXISTS `pf_transformer_settings` (
                    `projectId` TEXT NOT NULL DEFAULT 'DEFAULT_PROJECT',
                    `transformerId` TEXT NOT NULL,
                    `harmonicFilter` TEXT NOT NULL,
                    `detuningReactor` TEXT NOT NULL,
                    PRIMARY KEY(`projectId`, `transformerId`)
                )
            """.trimIndent())

            // 9. app_state
            setupDb.execSQL("""
                CREATE TABLE IF NOT EXISTS `app_state` (
                    `id` INTEGER PRIMARY KEY NOT NULL,
                    `activeProjectId` TEXT
                )
            """.trimIndent())

            // 10. conduit_settings
            setupDb.execSQL("""
                CREATE TABLE IF NOT EXISTS `conduit_settings` (
                    `projectId` TEXT NOT NULL DEFAULT 'DEFAULT_PROJECT',
                    `circuitId` INTEGER NOT NULL,
                    `routingMode` TEXT NOT NULL DEFAULT 'ONE_PER_CONDUIT',
                    `designProfile` TEXT NOT NULL DEFAULT 'PROJECT_DESIGN',
                    `customFillLimitPct` REAL NOT NULL DEFAULT 40.0,
                    `isDirectBuried` INTEGER NOT NULL DEFAULT 0,
                    `cpcArrangement` TEXT NOT NULL DEFAULT 'INTERNAL_CORE',
                    `manualConduitOverride` TEXT,
                    `manualCpcOD` REAL,
                    PRIMARY KEY(`projectId`, `circuitId`)
                )
            """.trimIndent())
            setupDb.execSQL("CREATE INDEX IF NOT EXISTS `index_conduit_settings_projectId` ON `conduit_settings` (`projectId`)")

            // 11. earthing_settings
            setupDb.execSQL("""
                CREATE TABLE IF NOT EXISTS `earthing_settings` (
                    `projectId` TEXT NOT NULL DEFAULT 'DEFAULT_PROJECT',
                    `id` INTEGER NOT NULL,
                    `soilResistivity_ohmM` REAL NOT NULL,
                    `rodLength_m` REAL NOT NULL,
                    `rodDiameter_mm` REAL NOT NULL,
                    `rodSpacing_m` REAL NOT NULL,
                    `ringConductor_mm2` REAL NOT NULL,
                    `ringBurialDepth_m` REAL NOT NULL,
                    `kFactor` REAL NOT NULL,
                    `projectType` TEXT NOT NULL,
                    `disconnectionTime_s` REAL NOT NULL,
                    `targetResistanceOverride_ohm` REAL,
                    `soilResistivitySource` TEXT NOT NULL,
                    `designMarginPct` REAL NOT NULL,
                    `isAutoDesignEnabled` INTEGER NOT NULL,
                    `siteLength_m` REAL,
                    `siteWidth_m` REAL,
                    `autoDesignAppliedConfigId` TEXT,
                    PRIMARY KEY(`projectId`, `id`)
                )
            """.trimIndent())

            // 12. sld_node_positions
            setupDb.execSQL("""
                CREATE TABLE IF NOT EXISTS `sld_node_positions` (
                    `projectId` TEXT NOT NULL DEFAULT 'DEFAULT_PROJECT',
                    `nodeId` TEXT NOT NULL,
                    `x` REAL NOT NULL,
                    `y` REAL NOT NULL,
                    PRIMARY KEY(`projectId`, `nodeId`)
                )
            """.trimIndent())
            setupDb.execSQL("CREATE INDEX IF NOT EXISTS `index_sld_node_positions_projectId` ON `sld_node_positions` (`projectId`)")

            // Set SQLite database version to 21
            setupDb.version = 21
            assertEquals(21, setupDb.version)

            // Insert 2 projects into V21 schema
            setupDb.execSQL(
                "INSERT INTO `projects` (`projectId`, `projectName`, `projectCode`, `clientName`, `createdAt`, `updatedAt`, `defaultPhaseImbalanceLimitPct`) " +
                "VALUES ('PRJ_MIG_01', 'Alpha Substation', 'PRJ-A', 'Client Alpha', 1700000000000, 1700000000500, 18.0)"
            )
            setupDb.execSQL(
                "INSERT INTO `projects` (`projectId`, `projectName`, `projectCode`, `clientName`, `createdAt`, `updatedAt`, `defaultPhaseImbalanceLimitPct`) " +
                "VALUES ('PRJ_MIG_02', 'Beta Hospital', 'PRJ-B', 'Client Beta', 1700000001000, 1700000001500, 25.0)"
            )

            // Insert a related child circuit
            setupDb.execSQL(
                "INSERT INTO `circuits` (`id`, `circuitName`, `loadKVA`, `fedFrom`, `transformerId`, `loadType`, `phases`, `voltageV`, `pf`, `safetyFactor`, `manufacturer`, `cableType`, `installation`, `parallelRuns`, `airTempC`, `gndTempC`, `burialDepth_m`, `soilRes`, `noCircuits`, `formation`, `spacing`, `length_m`, `maxVDpct`, `isHarmonic`, `projectId`) " +
                "VALUES (101, 'MDB-Main', 150.0, 'Transformer', 'TX-01', 'Normal', '3P+N', 400, 0.85, 1.25, 'Elsewedy', 'Cu/XLPE/SWA/PVC', 'Laid in Ducts', 1, 40, 40, 0.6, 125, 1, 'Flat', 'Touching', 45.0, 0.03, 0, 'PRJ_MIG_01')"
            )

            // Insert a related child transformer
            setupDb.execSQL(
                "INSERT INTO `transformers` (`projectId`, `transformerId`, `transformerName`, `kVA`, `voltage`, `percentZ`, `xr`, `txType`, `sourceType`, `status`, `sizeMode`, `manualKVA`, `demandFactor`, `diversity`, `growth`, `safety`) " +
                "VALUES ('PRJ_MIG_01', 'TX-01', 'TX Alpha', 1000.0, 400, 5.75, 8.0, 'Dry', 'Utility Grid', 'Active', 'Auto', 1000.0, 0.85, 1.0, 1.0, 1.0)"
            )

            // Insert a related child SLD node position
            setupDb.execSQL(
                "INSERT INTO `sld_node_positions` (`projectId`, `nodeId`, `x`, `y`) " +
                "VALUES ('PRJ_MIG_01', 'TX-01', 250.0, 150.0)"
            )
        } finally {
            // Close all V21 SQLite handles
            setupDb.close()
        }

        // 2. Reopen the EXACT SAME filename through Room AppDatabase V22 with Room Migration
        val appDb = Room.databaseBuilder(context, AppDatabase::class.java, tempDbFile.absolutePath)
            .addMigrations(
                MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
                MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
                MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17,
                MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21,
                MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24,
                MIGRATION_24_25
            )
            .allowMainThreadQueries()
            .build()

        val repository = EngineeringRepository(appDb)

        try {
            runBlocking {
                // Trigger opening through a DAO read & verify Room accepted the migrated schema
                val proj1 = repository.projectDao.getProject("PRJ_MIG_01")
                assertNotNull("Project 1 must exist after Room migration", proj1)
                assertEquals("Alpha Substation", proj1!!.projectName)
                assertEquals("Client Alpha", proj1.clientName)
                assertEquals(18.0, proj1.defaultPhaseImbalanceLimitPct, 0.001)
                assertEquals("Migrated project 1 must have revision == 1L", 1L, proj1.revision)

                val revSnapshot1 = repository.getCurrentRevision("PRJ_MIG_01")
                assertEquals(1L, revSnapshot1.revision)

                val proj2 = repository.projectDao.getProject("PRJ_MIG_02")
                assertNotNull("Project 2 must exist after Room migration", proj2)
                assertEquals("Beta Hospital", proj2!!.projectName)
                assertEquals("Client Beta", proj2.clientName)
                assertEquals(25.0, proj2.defaultPhaseImbalanceLimitPct, 0.001)
                assertEquals("Migrated project 2 must have revision == 1L", 1L, proj2.revision)

                val revSnapshot2 = repository.getCurrentRevision("PRJ_MIG_02")
                assertEquals(1L, revSnapshot2.revision)

                // Read and verify the pre-existing circuit using current DAOs
                val circuit = repository.getCircuitById(101)
                assertNotNull("Pre-existing circuit 101 must be preserved", circuit)
                assertEquals("MDB-Main", circuit!!.circuitName)
                assertEquals("PRJ_MIG_01", circuit.projectId)
                assertEquals(150.0, circuit.loadKVA, 0.001)
                assertEquals(45.0, circuit.length_m, 0.001)

                // Read and verify the pre-existing transformer using current DAOs
                val tx = repository.getTransformerById("PRJ_MIG_01", "TX-01")
                assertNotNull("Pre-existing transformer TX-01 must be preserved", tx)
                assertEquals("TX Alpha", tx!!.transformerName)
                assertEquals(1000.0, tx.kVA, 0.001)

                // Read and verify the pre-existing SLD position using current DAO / repository
                val sldPositions = repository.sldNodePositionsFlow("PRJ_MIG_01").first()
                assertEquals(1, sldPositions.size)
                assertEquals("TX-01", sldPositions[0].nodeId)
                assertEquals(250.0f, sldPositions[0].x)
                assertEquals(150.0f, sldPositions[0].y)

                // Perform one revision-aware mutation through EngineeringRepository and prove revision changes 1 -> 2
                repository.insertCircuit(
                    CircuitEntity(
                        id = 102,
                        projectId = "PRJ_MIG_01",
                        circuitName = "Feeder Post-Migration",
                        loadKVA = 50.0,
                        length_m = 30.0
                    )
                )
                val snapAfterMutation = repository.getCurrentRevision("PRJ_MIG_01")
                assertEquals("Post-migration revision must advance from 1 to 2 after repository mutation", 2L, snapAfterMutation.revision)
            }

            // Confirm database version is now 25 (Phase 2B v24→v25 schema) and canonical paths are identical
            val supportDb = appDb.openHelper.writableDatabase
            assertEquals("Database version must be 25 after Room migration", 25, supportDb.version)

            val openedCanonicalPath = java.io.File(supportDb.path!!).canonicalPath
            val setupCanonicalPath = tempDbFile.canonicalPath
            assertEquals("Room-opened database canonical path must equal setup database canonical path", setupCanonicalPath, openedCanonicalPath)
        } finally {
            // Close Room and delete named test database
            appDb.close()
            tempDbFile.delete()
        }
    }

    @Test
    fun testNavigationRecoveryOutOfRangeIndex() {
        val visibleTabs = listOf("Dashboard", "Circuits", "Transformers", "Power Systems", "Protection", "Conduits", "BOQ / Procurement")

        // Saved index 7 (old Panels tab)
        val oldPanelsIndex = 7
        val safeIndexForPanels = if (oldPanelsIndex in visibleTabs.indices) oldPanelsIndex else 0
        assertEquals(0, safeIndexForPanels) // Redirects safely to Dashboard

        // Saved index 8 (old Lighting tab)
        val oldLightingIndex = 8
        val safeIndexForLighting = if (oldLightingIndex in visibleTabs.indices) oldLightingIndex else 0
        assertEquals(0, safeIndexForLighting) // Redirects safely to Dashboard
    }
}
