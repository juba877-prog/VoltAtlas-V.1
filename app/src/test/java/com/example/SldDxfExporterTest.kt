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
        assertTrue(
            "Contains R12-valid \$DWGCODEPAGE ANSI_1252 (replaces invalid AC1009 \$INSUNITS)",
            dxf.contains("\$DWGCODEPAGE\n3\nANSI_1252")
        )
        assertFalse("AC1009 header must not contain AC1015+ \$INSUNITS", dxf.contains("\$INSUNITS"))

        assertTrue("Contains LAYER table", dxf.contains("TABLE\n2\nLAYER"))
        assertTrue("Contains MANDATORY layer 0", dxf.contains("2\n0\n"))
        assertTrue("Contains STYLE table with STANDARD text style", dxf.contains("TABLE\n2\nSTYLE") && dxf.contains("2\nSTANDARD\n"))
        assertTrue("Contains E-PANEL layer", dxf.contains("2\nE-PANEL\n"))
        assertTrue("Contains E-NORMAL layer", dxf.contains("2\nE-NORMAL\n"))
        assertTrue("Contains E-EMERG layer", dxf.contains("2\nE-EMERG\n"))
        assertTrue("Contains E-SYMBOLS layer", dxf.contains("2\nE-SYMBOLS\n"))
        assertTrue("Contains E-TEXT-TITLE layer", dxf.contains("2\nE-TEXT-TITLE\n"))
        assertTrue("Contains E-TEXT-DATA layer", dxf.contains("2\nE-TEXT-DATA\n"))
        assertTrue("Contains E-BORDER layer", dxf.contains("2\nE-BORDER\n"))

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

        // 1. Busbar check on E-PANEL
        assertTrue("Busbar lines on E-PANEL", dxf.contains("8\nE-PANEL"))

        // 2. Transformer & Generator symbol circles on E-TX / E-GEN
        assertTrue("Circles for Transformer on E-TX", dxf.contains("CIRCLE\n8\nE-TX"))
        assertTrue("Circles for Generator on E-GEN", dxf.contains("CIRCLE\n8\nE-GEN"))

        // 3. Feeder wire line on E-NORMAL
        assertTrue("Feeder line on E-NORMAL", dxf.contains("LINE\n8\nE-NORMAL"))

        // 4. Text entities on E-TEXT-TITLE / E-TEXT-DATA
        assertTrue("Text label on E-TEXT-TITLE", dxf.contains("8\nE-TEXT-TITLE"))
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
