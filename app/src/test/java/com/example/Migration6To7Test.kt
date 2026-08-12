package com.example

import com.example.data.AppDatabase
import com.example.data.CircuitEntity
import com.example.data.MIGRATION_6_7
import com.example.data.PanelType
import com.example.data.SourceType
import org.junit.Assert.*
import org.junit.Test

class Migration6To7Test {

    @Test
    fun testCircuitEntityHierarchyDefaults() {
        val circuit = CircuitEntity(id = 1, circuitName = "Test Circuit")
        assertNull(circuit.panelType)
        assertNull(circuit.parentId)
        assertNull(circuit.level)
        assertNull(circuit.sourceType)
    }

    @Test
    fun testCircuitEntityHierarchyValues() {
        val circuit = CircuitEntity(
            id = 5,
            circuitName = "SMDB-1",
            panelType = PanelType.SMDB.name,
            parentId = 1,
            level = 1,
            sourceType = SourceType.PANEL.name
        )
        assertEquals("SMDB", circuit.panelType)
        assertEquals(1, circuit.parentId)
        assertEquals(1, circuit.level)
        assertEquals("PANEL", circuit.sourceType)
    }
}
