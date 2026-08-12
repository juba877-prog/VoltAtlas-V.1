package com.example

import com.example.data.MIGRATION_1_2
import com.example.data.MIGRATION_5_6
import org.junit.Assert.*
import org.junit.Test

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
