package com.example

import com.example.data.CircuitEntity
import com.example.data.PanelType
import com.example.sld.SldLayoutEngine
import org.junit.Assert.*
import org.junit.Test
import android.graphics.Rect

class SldLayoutEngineTest {

    @Test
    fun testSubtreeCenteringAndLevelSpacing() {
        val mdb = CircuitEntity(id = 1, circuitName = "MDB", parentId = null, level = 0, panelType = PanelType.MDB.name)
        val db1 = CircuitEntity(id = 2, circuitName = "DB-1", fedFrom = "MDB", parentId = 1, level = 1, panelType = PanelType.DB.name)
        val db2 = CircuitEntity(id = 3, circuitName = "DB-2", fedFrom = "MDB", parentId = 1, level = 1, panelType = PanelType.DB.name)

        val result = SldLayoutEngine.calculateLayout(activeProjectId="proj_1", transformers=emptyList(), circuits=listOf(mdb, db1, db2))

        val nodeMap = result.nodes.associateBy { it.id }
        val mdbNode = nodeMap["circ_1"] ?: nodeMap["1"]
        val db1Node = nodeMap["circ_2"] ?: nodeMap["2"]
        val db2Node = nodeMap["circ_3"] ?: nodeMap["3"]

        assertNotNull(mdbNode)
        assertNotNull(db1Node)
        assertNotNull(db2Node)

        // Horizontal separation assertion: children are ordered left-to-right
        assertTrue(db1Node!!.x < db2Node!!.x)

        // Vertical spacing assertion
        assertTrue(db1Node!!.y > mdbNode!!.y)
        assertEquals(db1Node.y, db2Node!!.y, 0.01f)
    }

    @Test
    fun testCalculatedBounds() {
        val mdb = CircuitEntity(id = 1, circuitName = "MDB", parentId = null, level = 0)
        val result = SldLayoutEngine.calculateLayout(activeProjectId="proj_1", transformers=emptyList(), circuits=listOf(mdb))

        assertTrue(result.bounds.width > 0)
        assertTrue(result.bounds.height > 0)
    }

    @Test
    fun testMultiTransformerSldLayout() {
        val tx1 = com.example.data.TransformerEntity(transformerId = "TX-01", transformerName = "Transformer 1", kVA = 1000.0)
        val tx2 = com.example.data.TransformerEntity(transformerId = "TX-02", transformerName = "Transformer 2", kVA = 1600.0)

        val mdb1 = CircuitEntity(id = 1, circuitName = "MDB-1", fedFrom = "Transformer", transformerId = "TX-01", panelType = PanelType.MDB.name)
        val mdb2 = CircuitEntity(id = 2, circuitName = "MDB-2", fedFrom = "Transformer", transformerId = "TX-02", panelType = PanelType.MDB.name)
        val db1 = CircuitEntity(id = 3, circuitName = "DB-1", fedFrom = "MDB-1", parentId = 1, panelType = PanelType.DB.name)
        val db2 = CircuitEntity(id = 4, circuitName = "DB-2", fedFrom = "MDB-2", parentId = 2, panelType = PanelType.DB.name)

        val result = SldLayoutEngine.calculateLayout(
            activeProjectId = "proj_1",
            transformers = listOf(tx1, tx2),
            circuits = listOf(mdb1, mdb2, db1, db2)
        )

        assertNotNull(result)
        assertTrue("Nodes must contain all sources and panels", result.nodes.size >= 6)
        assertTrue("Calculated bounds width must be positive", result.bounds.width > 0)
        assertTrue("Calculated bounds height must be positive", result.bounds.height > 0)
    }

    @Test
    fun testManualPositionOverridesAndEdgePathReRouting() {
        val mdb = CircuitEntity(id = 1, circuitName = "MDB", parentId = null, level = 0, panelType = PanelType.MDB.name)
        val db1 = CircuitEntity(id = 2, circuitName = "DB-1", fedFrom = "MDB", parentId = 1, level = 1, panelType = PanelType.DB.name)

        val manualPos = mapOf("circ_2" to androidx.compose.ui.geometry.Offset(500f, 600f))
        val result = SldLayoutEngine.calculateLayout(
            activeProjectId = "proj_1",
            transformers = emptyList(),
            circuits = listOf(mdb, db1),
            manualPositions = manualPos
        )

        val nodeMap = result.nodes.associateBy { it.id }
        val db1Node = nodeMap["circ_2"]
        assertNotNull(db1Node)
        assertEquals(500f, db1Node!!.x, 0.01f)
        assertEquals(600f, db1Node.y, 0.01f)

        // Connected edge should have updated path points matching manual position
        val edge = result.edges.firstOrNull { it.toId == "circ_2" }
        assertNotNull(edge)
        val lastPoint = edge!!.pathPoints.last()
        assertEquals(500f + db1Node.width / 2f, lastPoint.first, 1.0f)
    }
}
