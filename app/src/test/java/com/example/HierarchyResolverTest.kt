package com.example

import com.example.data.CircuitEntity
import com.example.data.PanelType
import com.example.data.SourceType
import com.example.hierarchy.HierarchyResolver
import org.junit.Assert.*
import org.junit.Test

class HierarchyResolverTest {

    @Test
    fun testComputeLevelsMultiTierChain() {
        val mdb = CircuitEntity(id = 1, circuitName = "MDB", parentId = null, panelType = PanelType.MDB.name)
        val smdb = CircuitEntity(id = 2, circuitName = "SMDB-1", parentId = 1, panelType = PanelType.SMDB.name)
        val db = CircuitEntity(id = 3, circuitName = "DB-1A", parentId = 2, panelType = PanelType.DB.name)

        val resolved = HierarchyResolver.computeLevels(listOf(mdb, smdb, db))
        val map = resolved.associateBy { it.id }

        assertEquals(0, map[1]?.level)
        assertEquals(1, map[2]?.level)
        assertEquals(2, map[3]?.level)
    }

    @Test
    fun testBuildTreeStructure() {
        val mdb = CircuitEntity(id = 1, circuitName = "MDB", parentId = null)
        val smdb1 = CircuitEntity(id = 2, circuitName = "SMDB-1", parentId = 1)
        val smdb2 = CircuitEntity(id = 3, circuitName = "SMDB-2", parentId = 1)

        val forest = HierarchyResolver.buildTree(listOf(mdb, smdb1, smdb2))
        assertEquals(1, forest.size)
        assertEquals("MDB", forest[0].board.circuitName)
        assertEquals(2, forest[0].children.size)
    }

    @Test
    fun testCycleDetectionBlocked() {
        val boardA = CircuitEntity(id = 1, circuitName = "Board A", parentId = 2)
        val boardB = CircuitEntity(id = 2, circuitName = "Board B", parentId = 1)

        val result = HierarchyResolver.validate(listOf(boardA, boardB))
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("Circular hierarchy dependency") })
    }

    @Test
    fun testSelfParentingBlocked() {
        val boardA = CircuitEntity(id = 1, circuitName = "Board A", parentId = 1)

        val result = HierarchyResolver.validate(listOf(boardA))
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("cannot be its own parent") })
    }

    @Test
    fun testBackfillParentsMatchingFedFrom() {
        val mdb = CircuitEntity(id = 10, circuitName = "MDB-MAIN", fedFrom = "Transformer")
        val db1 = CircuitEntity(id = 20, circuitName = "DB-1", fedFrom = "MDB-MAIN")

        val backfilled = HierarchyResolver.backfillParents(listOf(mdb, db1))
        val map = backfilled.associateBy { it.id }

        assertNull(map[10]?.parentId)
        assertEquals(SourceType.TRANSFORMER.name, map[10]?.sourceType)

        assertEquals(10, map[20]?.parentId)
        assertEquals(SourceType.PANEL.name, map[20]?.sourceType)
    }
}
