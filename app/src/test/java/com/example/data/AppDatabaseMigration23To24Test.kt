package com.example.data

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 1 schema migration verification (Roadmap Phase 1 — v23 → v24):
 *
 *  1. Creates an in-memory v23 database (pre-bus_ties, pre-paralleling columns).
 *  2. Seeds rows that must survive the migration untouched.
 *  3. Runs [MIGRATION_23_24] (non-destructive — no table rebuilds, no row rewrites).
 *  4. Asserts the `bus_ties` table + its indices exist, the new nullable columns
 *     exist on `generator_settings` / `sld_node_positions`, and every pre-seeded
 *     row is preserved with NULL in the new columns.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigration23To24Test {

    private fun createV23Database(): SupportSQLiteDatabase {
        val openHelper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(ApplicationProvider.getApplicationContext())
                .name(null) // null name = in-memory SQLite DB
                .callback(object : SupportSQLiteOpenHelper.Callback(23) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        // generator_settings @ v23 (no parallelingGroupId / syncBusId yet)
                        db.execSQL(
                            """
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
                            """.trimIndent()
                        )

                        // sld_node_positions @ v23 (no portOverrides yet)
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS `sld_node_positions` (
                                `projectId` TEXT NOT NULL DEFAULT 'DEFAULT_PROJECT',
                                `nodeId` TEXT NOT NULL,
                                `x` REAL NOT NULL,
                                `y` REAL NOT NULL,
                                PRIMARY KEY(`projectId`, `nodeId`)
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            "CREATE INDEX IF NOT EXISTS `index_sld_node_positions_projectId` " +
                                "ON `sld_node_positions` (`projectId`)"
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
        )
        return openHelper.writableDatabase
    }

    private fun tableColumns(db: SupportSQLiteDatabase, table: String): Set<String> {
        val columns = mutableSetOf<String>()
        val cursor = db.query("PRAGMA table_info(`$table`)")
        cursor.use {
            while (it.moveToNext()) {
                columns.add(it.getString(it.getColumnIndexOrThrow("name")))
            }
        }
        return columns
    }

    @Test
    fun migration23To24_createsBusTiesTableAndIndices() {
        val db = createV23Database()

        MIGRATION_23_24.migrate(db)

        val busTieColumns = tableColumns(db, "bus_ties")
        assertEquals(
            setOf(
                "projectId", "id", "busTieId", "name", "sourceBoardId", "targetBoardId",
                "interlockScheme", "normalStatus", "breakerRatingA"
            ),
            busTieColumns
        )

        // Composite primary key must be exactly (projectId, id)
        val pkCursor = db.query("PRAGMA table_info(`bus_ties`)")
        val pkColumns = mutableListOf<Pair<Int, String>>()
        pkCursor.use {
            while (it.moveToNext()) {
                val pk = it.getInt(it.getColumnIndexOrThrow("pk"))
                if (pk > 0) {
                    pkColumns.add(pk to it.getString(it.getColumnIndexOrThrow("name")))
                }
            }
        }
        assertEquals(listOf(1 to "projectId", 2 to "id"), pkColumns.sortedBy { it.first })

        // Strict-relational board reference indices (Roadmap Phase 1)
        val masterCursor = db.query(
            "SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'bus_ties'"
        )
        val indexNames = mutableSetOf<String>()
        masterCursor.use {
            while (it.moveToNext()) indexNames.add(it.getString(0))
        }
        assertTrue("index_bus_ties_source must exist", "index_bus_ties_source" in indexNames)
        assertTrue("index_bus_ties_target must exist", "index_bus_ties_target" in indexNames)

        // The fresh table must be usable per the entity contract
        db.execSQL(
            "INSERT INTO `bus_ties` (`projectId`, `id`, `busTieId`, `name`, `sourceBoardId`, " +
                "`targetBoardId`, `interlockScheme`, `normalStatus`, `breakerRatingA`) " +
                "VALUES ('PROJ-MIG', 1, 'TIE-01', 'Bus Tie 1', 101, 102, 'TWO_OUT_OF_THREE', 'OPEN', 1600.0)"
        )
        val tieCursor = db.query("SELECT * FROM `bus_ties` WHERE `projectId` = 'PROJ-MIG' AND `id` = 1")
        assertTrue("Inserted bus tie row must be readable", tieCursor.moveToFirst())
        assertEquals("TIE-01", tieCursor.getString(tieCursor.getColumnIndexOrThrow("busTieId")))
        assertEquals(101L, tieCursor.getLong(tieCursor.getColumnIndexOrThrow("sourceBoardId")))
        assertEquals(102L, tieCursor.getLong(tieCursor.getColumnIndexOrThrow("targetBoardId")))
        assertEquals(1600.0, tieCursor.getDouble(tieCursor.getColumnIndexOrThrow("breakerRatingA")), 0.01)
        tieCursor.close()

        db.close()
    }

    @Test
    fun migration23To24_addsGeneratorParallelingColumns_withoutTouchingRows() {
        val db = createV23Database()
        db.execSQL(
            "INSERT INTO `generator_settings` (`projectId`, `id`, `generatorId`, `generatorName`, " +
                "`additionalEssentialLoadKW`, `largestMotorKW`, `motorStartMethod`, `motorPf`, " +
                "`generatorPf`, `demandFactor`, `diversityFactor`, `stepLoadPct`, " +
                "`oversizingFactor`, `enableManualOverride`, `manualSizeKVA`) " +
                "VALUES ('PROJ-MIG', 1, 'GEN-01', 'Generator 1', 150.0, 45.0, 'DOL', 0.85, 0.8, " +
                "0.8, 1.0, 50.0, 1.0, 0, 500.0)"
        )

        MIGRATION_23_24.migrate(db)

        val columns = tableColumns(db, "generator_settings")
        assertTrue("parallelingGroupId column must exist", "parallelingGroupId" in columns)
        assertTrue("syncBusId column must exist", "syncBusId" in columns)

        val cursor = db.query("SELECT * FROM `generator_settings` WHERE `projectId` = 'PROJ-MIG' AND `id` = 1")
        assertTrue("Pre-existing generator row must survive the migration", cursor.moveToFirst())
        assertEquals("GEN-01", cursor.getString(cursor.getColumnIndexOrThrow("generatorId")))
        assertEquals("Generator 1", cursor.getString(cursor.getColumnIndexOrThrow("generatorName")))
        assertEquals(150.0, cursor.getDouble(cursor.getColumnIndexOrThrow("additionalEssentialLoadKW")), 0.01)
        assertEquals(500.0, cursor.getDouble(cursor.getColumnIndexOrThrow("manualSizeKVA")), 0.01)
        assertTrue(
            "parallelingGroupId must default to NULL for pre-existing rows",
            cursor.isNull(cursor.getColumnIndexOrThrow("parallelingGroupId"))
        )
        assertTrue(
            "syncBusId must default to NULL for pre-existing rows",
            cursor.isNull(cursor.getColumnIndexOrThrow("syncBusId"))
        )
        cursor.close()

        db.close()
    }

    @Test
    fun migration23To24_addsPortOverridesColumn_withoutTouchingRows() {
        val db = createV23Database()
        db.execSQL(
            "INSERT INTO `sld_node_positions` (`projectId`, `nodeId`, `x`, `y`) " +
                "VALUES ('PROJ-MIG', 'circ_1', 250.0, 150.0)"
        )

        MIGRATION_23_24.migrate(db)

        val columns = tableColumns(db, "sld_node_positions")
        assertTrue("portOverrides column must exist", "portOverrides" in columns)

        val cursor = db.query(
            "SELECT * FROM `sld_node_positions` WHERE `projectId` = 'PROJ-MIG' AND `nodeId` = 'circ_1'"
        )
        assertNotNull(cursor)
        assertTrue("Pre-existing SLD position row must survive the migration", cursor!!.moveToFirst())
        assertEquals(250.0f, cursor.getFloat(cursor.getColumnIndexOrThrow("x")), 0.001f)
        assertEquals(150.0f, cursor.getFloat(cursor.getColumnIndexOrThrow("y")), 0.001f)
        assertTrue(
            "portOverrides must default to NULL for pre-existing rows",
            cursor.isNull(cursor.getColumnIndexOrThrow("portOverrides"))
        )
        cursor.close()

        db.close()
    }
}
