package com.example.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class MigrationFailFastTest {

    private fun openHelper(version: Int, onCreate: (SupportSQLiteDatabase) -> Unit): SupportSQLiteDatabase {
        val factory = FrameworkSQLiteOpenHelperFactory()
        val config = SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext<Context>())
            .name(null) // in-memory
            .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) = onCreate(db)
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        return factory.create(config).writableDatabase
    }

    private fun createLegacyPowerSystemTables(db: SupportSQLiteDatabase, legacyShape: Boolean) {
        val extraTxCols = if (legacyShape) "" else ", `vectorGroup` TEXT NOT NULL DEFAULT 'Dyn11', `z0OverZ1` REAL NOT NULL DEFAULT 1.0, `pf` REAL NOT NULL DEFAULT 0.8"
        db.execSQL("""CREATE TABLE `transformers` (
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
                    `safety` REAL NOT NULL$extraTxCols,
                    PRIMARY KEY(`transformerId`)
                )""".trimIndent())
        db.execSQL("""CREATE TABLE `generator_settings` (
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
                    PRIMARY KEY(`id`)
                )""".trimIndent())
        db.execSQL("""CREATE TABLE `ats_settings` (
                    `id` INTEGER NOT NULL, `systemVoltage` INTEGER NOT NULL, `systemFrequency` INTEGER NOT NULL,
                    `numberOfSources` INTEGER NOT NULL, `atsLocation` TEXT NOT NULL, `transferType` TEXT NOT NULL,
                    `utilizationCategory` TEXT NOT NULL, `atsClass` TEXT NOT NULL, `loadCoverage` TEXT NOT NULL,
                    `ambientTemp` INTEGER NOT NULL, `designSafetyFactor` REAL NOT NULL, `futureGrowthFactor` REAL NOT NULL,
                    `approvedManufacturer` TEXT NOT NULL, `mainsFailureDetectionDelay` INTEGER NOT NULL,
                    `generatorStartSignalDelay` INTEGER NOT NULL, `generatorWarmUp` INTEGER NOT NULL,
                    `mainsReturnDetectionDelay` INTEGER NOT NULL, `reTransferDelay` INTEGER NOT NULL,
                    `generatorCooldown` INTEGER NOT NULL, `enclosureIpRating` TEXT NOT NULL,
                    PRIMARY KEY(`id`)
                )""".trimIndent())
        db.execSQL("""CREATE TABLE `ups_settings` (
                    `id` INTEGER NOT NULL, `systemVoltage` INTEGER NOT NULL, `systemFrequency` INTEGER NOT NULL,
                    `upsPf` REAL NOT NULL, `upsEfficiency` REAL NOT NULL, `batteryType` TEXT NOT NULL,
                    `autonomyMinutes` INTEGER NOT NULL, `batteryEodVoltage` REAL NOT NULL,
                    `ambientTemp` INTEGER NOT NULL, `tempDeratingFactor` REAL NOT NULL,
                    `agingFactor` REAL NOT NULL, `designMarginPct` REAL NOT NULL,
                    PRIMARY KEY(`id`)
                )""".trimIndent())
        db.execSQL("""CREATE TABLE `pf_global_settings` (
                    `id` INTEGER NOT NULL, `targetPf` REAL NOT NULL, `kvarStepSize` REAL NOT NULL,
                    `systemVoltage` INTEGER NOT NULL, `annualOperatingHours` INTEGER NOT NULL,
                    `energyCost` REAL NOT NULL, `lossReductionPct` REAL NOT NULL, `unityToleranceKvar` REAL NOT NULL,
                    PRIMARY KEY(`id`)
                )""".trimIndent())
        db.execSQL("""CREATE TABLE `pf_transformer_settings` (
                    `transformerId` TEXT NOT NULL, `harmonicFilter` TEXT NOT NULL, `detuningReactor` TEXT NOT NULL,
                    PRIMARY KEY(`transformerId`)
                )""".trimIndent())
    }
    private fun seedLegacyRows(db: SupportSQLiteDatabase, legacyShape: Boolean) {
        if (!legacyShape) {
db.execSQL("INSERT INTO `transformers` (`projectId`, `transformerId`, `transformerName`, `kVA`, `voltage`, `percentZ`, `xr`, `txType`, `sourceType`, `status`, `sizeMode`, `manualKVA`, `demandFactor`, `diversity`, `growth`, `safety`, `vectorGroup`, `z0OverZ1`, `pf`) VALUES ('P1', 'TX-01', 'TX Alpha', 1000.0, 400, 5.75, 8.0, 'Dry', 'Utility Grid', 'Active', 'Auto', 1000.0, 0.85, 1.0, 1.0, 1.25, 'Yzn1', 1.2, 0.85)")
        db.execSQL("INSERT INTO `transformers` (`projectId`, `transformerId`, `transformerName`, `kVA`, `voltage`, `percentZ`, `xr`, `txType`, `sourceType`, `status`, `sizeMode`, `manualKVA`, `demandFactor`, `diversity`, `growth`, `safety`, `vectorGroup`, `z0OverZ1`, `pf`) VALUES ('P1', 'TX-02', 'TX Beta', 630.0, 400, 4.5, 6.0, 'Oil', 'Generator', 'Active', 'Auto', 630.0, 0.9, 1.1, 1.05, 1.3, 'Dyn11', 1.0, 0.8)")
        db.execSQL("INSERT INTO `generator_settings` (`id`, `additionalEssentialLoadKW`, `largestMotorKW`, `motorStartMethod`, `motorPf`, `generatorPf`, `demandFactor`, `diversityFactor`, `stepLoadPct`, `oversizingFactor`, `enableManualOverride`, `manualSizeKVA`) VALUES (1, 10.0, 30.0, 'DOL', 0.8, 0.8, 0.9, 1.0, 60.0, 1.1, 0, NULL)")
        } else {
            db.execSQL("INSERT INTO `transformers` (`projectId`, `transformerId`, `transformerName`, `kVA`, `voltage`, `percentZ`, `xr`, `txType`, `sourceType`, `status`, `sizeMode`, `manualKVA`, `demandFactor`, `diversity`, `growth`, `safety`) VALUES ('P1', 'TX-01', 'TX Alpha', 1000.0, 400, 5.75, 8.0, 'Dry', 'Utility Grid', 'Active', 'Auto', 1000.0, 0.85, 1.0, 1.0, 1.25)")
            db.execSQL("INSERT INTO `transformers` (`projectId`, `transformerId`, `transformerName`, `kVA`, `voltage`, `percentZ`, `xr`, `txType`, `sourceType`, `status`, `sizeMode`, `manualKVA`, `demandFactor`, `diversity`, `growth`, `safety`) VALUES ('P1', 'TX-02', 'TX Beta', 630.0, 400, 4.5, 6.0, 'Oil', 'Generator', 'Active', 'Auto', 630.0, 0.9, 1.1, 1.05, 1.3)")
        }
        db.execSQL("INSERT INTO `ats_settings` (`id`, `systemVoltage`, `systemFrequency`, `numberOfSources`, `atsLocation`, `transferType`, `utilizationCategory`, `atsClass`, `loadCoverage`, `ambientTemp`, `designSafetyFactor`, `futureGrowthFactor`, `approvedManufacturer`, `mainsFailureDetectionDelay`, `generatorStartSignalDelay`, `generatorWarmUp`, `mainsReturnDetectionDelay`, `reTransferDelay`, `generatorCooldown`, `enclosureIpRating`) VALUES (1, 400, 50, 2, 'Main Panel', 'Open', 'B', '1', 'Essential', 40, 1.25, 1.2, 'Socomec', 100, 200, 300, 400, 500, 600, 'IP54')")
        db.execSQL("INSERT INTO `ups_settings` (`id`, `systemVoltage`, `systemFrequency`, `upsPf`, `upsEfficiency`, `batteryType`, `autonomyMinutes`, `batteryEodVoltage`, `ambientTemp`, `tempDeratingFactor`, `agingFactor`, `designMarginPct`) VALUES (1, 400, 50, 0.8, 0.92, 'VRLA', 30, 1.75, 25, 0.95, 1.1, 15.0)")
        db.execSQL("INSERT INTO `pf_global_settings` (`id`, `targetPf`, `kvarStepSize`, `systemVoltage`, `annualOperatingHours`, `energyCost`, `lossReductionPct`, `unityToleranceKvar`) VALUES (1, 0.95, 25.0, 400, 6000, 0.1, 12.0, 5.0)")
        db.execSQL("INSERT INTO `pf_transformer_settings` (`transformerId`, `harmonicFilter`, `detuningReactor`) VALUES ('TX-01', 'None', 'None')")
    }
    private fun scalar(db: SupportSQLiteDatabase, sql: String): Long {
        val cursor = db.query(sql)
        try {
            cursor.moveToFirst()
            return cursor.getLong(0)
        } finally {
            cursor.close()
        }
    }

    private fun tableExists(db: SupportSQLiteDatabase, table: String): Boolean {
        val cursor = db.query("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = '$table'")
        try {
            cursor.moveToFirst()
            return cursor.getLong(0) > 0
        } finally {
            cursor.close()
        }
    }

    @Test
    fun migration4To5_rebuildPreservesEverySourceRow() {
        val db = openHelper(4) { createLegacyPowerSystemTables(it, legacyShape = false) }
        try {
            seedLegacyRows(db, legacyShape = false)
            db.beginTransaction()
            try {
                MIGRATION_4_5.migrate(db)
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }

            // Row parity: every source row survived the rebuild.
            assertEquals(2L, scalar(db, "SELECT COUNT(*) FROM `transformers`"))
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM `generator_settings` WHERE `projectId` = 'DEFAULT_PROJECT'"))
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM `ats_settings`"))
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM `ups_settings`"))
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM `pf_global_settings`"))
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM `pf_transformer_settings`"))

            // Exact value preservation through the copy (including semantic columns).
            val cursor = db.query("SELECT `transformerName`, `kVA`, `percentZ`, `vectorGroup`, `z0OverZ1`, `pf` FROM `transformers` WHERE `transformerId` = 'TX-01'")
            try {
                assertTrue(cursor.moveToFirst())
                assertEquals("TX Alpha", cursor.getString(0))
                assertEquals(1000.0, cursor.getDouble(1), 0.000001)
                assertEquals(5.75, cursor.getDouble(2), 0.000001)
                assertEquals("Yzn1", cursor.getString(3))
                assertEquals(1.2, cursor.getDouble(4), 0.000001)
                assertEquals(0.85, cursor.getDouble(5), 0.000001)
            } finally {
                cursor.close()
            }

            // No partially rebuilt temporary tables may remain.
            assertFalse(tableExists(db, "transformers_new"))
            assertFalse(tableExists(db, "generator_settings_new"))
            assertFalse(tableExists(db, "ats_settings_new"))
            assertFalse(tableExists(db, "ups_settings_new"))
            assertFalse(tableExists(db, "pf_global_settings_new"))
            assertFalse(tableExists(db, "pf_transformer_settings_new"))
        } finally {
            db.close()
        }
    }
    @Test
    fun migration4To5_injectedCopyFailureAbortsAndPreservesOldData() {
        // Legacy shape: the transformers table is missing a column the mandatory copy
        // references (growth), so the INSERT...SELECT must fail and abort the migration.
        val db = openHelper(4) { createLegacyPowerSystemTables(it, legacyShape = true) }
        try {
            seedLegacyRows(db, legacyShape = true)
            db.beginTransaction()
            var aborted: Exception? = null
            try {
                MIGRATION_4_5.migrate(db)
                db.setTransactionSuccessful()
                fail("Mandatory copy failure must abort the migration, not be swallowed")
            } catch (expected: android.database.sqlite.SQLiteException) {
                aborted = expected
            } finally {
                // No setTransactionSuccessful -> full rollback of any partial rebuild.
                db.endTransaction()
            }
            assertTrue("Expected the injected copy failure to surface", aborted != null)

            // Rollback evidence: old table intact with all rows; no partial temp tables.
            assertTrue(tableExists(db, "transformers"))
            assertEquals(2L, scalar(db, "SELECT COUNT(*) FROM `transformers`"))
            assertEquals("TX Alpha", queryTransformerName(db, "TX-01"))
            assertFalse(tableExists(db, "transformers_new"))
            assertFalse(tableExists(db, "generator_settings_new"))
        } finally {
            db.close()
        }
    }

    private fun queryTransformerName(db: SupportSQLiteDatabase, txId: String): String {
        val cursor = db.query("SELECT `transformerName` FROM `transformers` WHERE `transformerId` = '$txId'")
        try {
            cursor.moveToFirst()
            return cursor.getString(0)
        } finally {
            cursor.close()
        }
    }

    @Test
    fun duplicateColumnPreState_convergesWithoutException() {
        // A database that already carries upsBacked (overlapping historical hop) is a
        // legitimate pre-state: the guard must skip cleanly instead of throwing.
        val db = openHelper(1) {
            it.execSQL("CREATE TABLE `circuits` (`id` INTEGER NOT NULL, `circuitName` TEXT NOT NULL, `upsBacked` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`))")
            it.execSQL("INSERT INTO `circuits` (`id`, `circuitName`, `upsBacked`) VALUES (1, 'Feeder-A', 0)")
        }
        try {
            MIGRATION_1_2.migrate(db)
            var upsBackedColumns = 0
            val pragma = db.query("PRAGMA table_info(`circuits`)")
            try {
                val nameIdx = pragma.getColumnIndexOrThrow("name")
                while (pragma.moveToNext()) {
                    if (pragma.getString(nameIdx) == "upsBacked") upsBackedColumns++
                }
            } finally {
                pragma.close()
            }
            assertEquals(1, upsBackedColumns)
            assertEquals(1L, scalar(db, "SELECT COUNT(*) FROM `circuits`"))
        } finally {
            db.close()
        }
    }

    @Test
    fun migration2To3_duplicateUpsBackedPreState_createsMandatorySettingsTables() {
        val db = openHelper(2) {
            it.execSQL("CREATE TABLE `circuits` (`id` INTEGER NOT NULL, `circuitName` TEXT NOT NULL, `upsBacked` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`))")
        }
        try {
            MIGRATION_2_3.migrate(db)
            assertTrue(tableExists(db, "generator_settings"))
            assertTrue(tableExists(db, "ats_settings"))
            assertTrue(tableExists(db, "ups_settings"))
            assertTrue(tableExists(db, "pf_global_settings"))
            assertTrue(tableExists(db, "pf_transformer_settings"))
        } finally {
            db.close()
        }
    }

    @Test
    fun unexpectedSqlFailure_isNeverSuppressed() {
        // circuits does not exist at all: PRAGMA finds no column, the mandatory ALTER must
        // propagate its failure instead of being caught and logged.
        val db = openHelper(1) { }
        try {
            try {
                MIGRATION_1_2.migrate(db)
                fail("Missing mandatory table must abort the migration")
            } catch (expected: android.database.sqlite.SQLiteException) {
                assertTrue(expected.message!!.contains("no such table"))
            }
        } finally {
            db.close()
        }
    }
}