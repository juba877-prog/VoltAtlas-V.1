package com.example

import androidx.compose.ui.geometry.Rect
import com.example.data.CircuitEntity
import com.example.data.TransformerEntity
import com.example.sld.*
import org.junit.Assert.*
import org.junit.Test

class SldDxfExporterTest {

    @Test
    fun testDxfHeaderAndLayers() {
        val engineResult = SldEngineResult(
            nodes = emptyList(),
            edges = emptyList(),
            bounds = Rect(0f, 0f, 1000f, 800f)
        )

        val dxf = SldDxfExporter.generateDxf(engineResult, "TEST_PROJECT")

        assertTrue("Contains SECTION HEADER", dxf.contains("SECTION\n2\nHEADER"))
        assertTrue("Contains AC1009 pure R12 version", dxf.contains("\$ACADVER\n1\nAC1009"))
        assertTrue("Contains \$INSBASE", dxf.contains("\$INSBASE"))
        assertTrue("Contains \$EXTMIN", dxf.contains("\$EXTMIN"))
        assertTrue("Contains \$EXTMAX", dxf.contains("\$EXTMAX"))
        assertFalse("Does NOT contain \$INSUNITS invalid group code 7", dxf.contains("\$INSUNITS"))

        assertTrue("Contains LAYER table", dxf.contains("TABLE\n2\nLAYER"))
        assertTrue("Contains SLD_NODES layer", dxf.contains("SLD_NODES"))
        assertTrue("Contains SLD_BUS layer", dxf.contains("SLD_BUS"))
        assertTrue("Contains SLD_WIRES layer", dxf.contains("SLD_WIRES"))
        assertTrue("Contains SLD_SYMBOLS layer", dxf.contains("SLD_SYMBOLS"))
        assertTrue("Contains SLD_TEXT layer", dxf.contains("SLD_TEXT"))
        assertTrue("Contains SLD_TIERS layer", dxf.contains("SLD_TIERS"))

        assertTrue("Contains ENTITIES section", dxf.contains("SECTION\n2\nENTITIES"))
        assertTrue("Contains EOF tag", dxf.contains("0\nEOF\n"))
    }

    @Test
    fun testDxfGeometrySerializationAndYFlip() {
        val testNode = PositionedSldNode(
            id = "NODE_MDB",
            label = "MDB",
            panelType = "MDB",
            ratingText = "400 A",
            statusColorHex = "#10B981",
            x = 100f,
            y = 200f,
            width = 200f,
            height = 30f,
            isSource = false
        )

        val testTxNode = PositionedSldNode(
            id = "NODE_TX",
            label = "TX-01",
            panelType = "TRANSFORMER",
            ratingText = "1000 kVA",
            statusColorHex = "#10B981",
            x = 100f,
            y = 50f,
            width = 140f,
            height = 70f,
            isSource = true
        )

        val testGenNode = PositionedSldNode(
            id = "NODE_GEN",
            label = "GEN-01",
            panelType = "GENERATOR",
            ratingText = "500 kVA",
            statusColorHex = "#10B981",
            x = 300f,
            y = 50f,
            width = 140f,
            height = 70f,
            isSource = true
        )

        val testEdge = PositionedSldEdge(
            fromId = "NODE_TX",
            toId = "NODE_MDB",
            label = "4x(1x240) Cu/XLPE (45m)",
            pathPoints = listOf(Pair(170f, 85f), Pair(170f, 200f))
        )

        val engineResult = SldEngineResult(
            nodes = listOf(testTxNode, testGenNode, testNode),
            edges = listOf(testEdge),
            bounds = Rect(0f, 0f, 1000f, 800f)
        )

        val dxf = SldDxfExporter.generateDxf(engineResult, "GEOMETRY_TEST")

        // 1. Busbar rectangle check on SLD_BUS
        assertTrue("Busbar lines on SLD_BUS", dxf.contains("LINE\n8\nSLD_BUS"))

        // 2. Transformer & Generator symbol circles on SLD_SYMBOLS
        assertTrue("Circles for Transformer/Generator on SLD_SYMBOLS", dxf.contains("CIRCLE\n8\nSLD_SYMBOLS"))

        // 3. Feeder wire line on SLD_WIRES
        assertTrue("Feeder line on SLD_WIRES", dxf.contains("LINE\n8\nSLD_WIRES"))

        // 4. Text entities on SLD_TEXT
        assertTrue("Text label MDB on SLD_TEXT", dxf.contains("TEXT\n8\nSLD_TEXT"))
        assertTrue("MDB label text present", dxf.contains("1\nMDB"))
        assertTrue("TX-01 label text present", dxf.contains("1\nTX-01"))
        assertTrue("GEN-01 label text present", dxf.contains("1\nGEN-01"))

        // 5. Verify Y-Flip: y_screen = 200 -> y_dxf = -200.000
        assertTrue("Y-Flip coordinate -200.000 present", dxf.contains("-200.000"))
        assertTrue("Y-Flip coordinate -50.000 present", dxf.contains("-50.000"))
    }

    @Test
    fun testDxfFullSampleProjectNoCrash() {
        val transformers = listOf(
            TransformerEntity(projectId = "P1", transformerId = "TX-01", transformerName = "Main TX", kVA = 1000.0)
        )
        val circuits = listOf(
            CircuitEntity(id = 1, projectId = "P1", circuitName = "MDB Feeder", loadKVA = 250.0, panelType = "MDB", length_m = 30.0),
            CircuitEntity(id = 2, projectId = "P1", circuitName = "SMDB 1", loadKVA = 100.0, panelType = "SMDB", parentId = 1, length_m = 45.0),
            CircuitEntity(id = 3, projectId = "P1", circuitName = "DB Lighting", loadKVA = 20.0, panelType = "DB", parentId = 2, length_m = 20.0)
        )

        val layoutResult = SldLayoutEngine.calculateLayout(
            activeProjectId = "P1",
            transformers = transformers,
            circuits = circuits
        )

        val dxf = SldDxfExporter.generateDxf(layoutResult, "P1")

        assertNotNull(dxf)
        assertTrue(dxf.length > 500)
        assertTrue(dxf.startsWith("0\nSECTION"))
        assertTrue(dxf.endsWith("0\nEOF\n"))
    }
}
