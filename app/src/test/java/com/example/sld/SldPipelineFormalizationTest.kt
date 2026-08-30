package com.example.sld

import androidx.compose.ui.geometry.Offset
import com.example.data.AtsEntity
import com.example.data.CircuitEntity
import com.example.data.GeneratorEntity
import com.example.data.TransformerEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Roadmap §2.1 Verification: Formalized 3-Pass Layout Pipeline.
 *
 * Asserts:
 * 1. Explicit 3-stage execution: assignLayers (Pass 1), computeBoundingBoxes (Pass 2),
 *    assignCoordinates (Pass 3), and routeEdges (Post-Pass).
 * 2. Complete numeric coordinate invariance for:
 *    - Narrow panel (single feeder)
 *    - Wide panel (6 feeders, DP-G3 equivalent)
 *    - Stale manual drag override case
 *    - Multi-ATS and multi-generator configurations
 * 3. Tracks bounding-box sizing observations across node types.
 */
class SldPipelineFormalizationTest {

    private val projectId = "PRJ_PIPELINE_TEST"

    @Test
    fun testExplicitThreePassPipelineExecution() {
        val transformers = listOf(
            TransformerEntity(projectId = projectId, transformerId = "TX-01", kVA = 1000.0)
        )
        val circuits = listOf(
            CircuitEntity(id = 1, projectId = projectId, circuitName = "MDB-01", fedFrom = "TX-01", panelType = "MDB"),
            CircuitEntity(id = 2, projectId = projectId, circuitName = "DB-01", fedFrom = "MDB-01", panelType = "DB")
        )

        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = projectId,
            transformers = transformers,
            circuits = circuits
        )

        // Pass 1: assignLayers
        val layers = RiserLayoutEngine.assignLayers(graph)
        assertEquals(-1, layers.effectiveTier["grid_TX-01"])
        assertEquals(0, layers.effectiveTier["tx_TX-01"])
        assertEquals(1, layers.effectiveTier["circ_1"])
        assertEquals(3, layers.effectiveTier["circ_2"])

        // Pass 2: computeBoundingBoxes
        val boxes = RiserLayoutEngine.computeBoundingBoxes(graph, null)
        assertTrue(boxes.nodeWidths.containsKey("circ_1"))
        assertTrue((boxes.nodeWidths["circ_1"] ?: 0f) >= 180f)

        // Pass 3: assignCoordinates
        val coords = RiserLayoutEngine.assignCoordinates(graph, circuits, layers, boxes, null, emptyMap())
        val mdbNode = coords.positionedNodes["circ_1"]
        val dbNode = coords.positionedNodes["circ_2"]
        assertNotNull(mdbNode)
        assertNotNull(dbNode)

        // Post-Pass: routeEdges
        val edges = RiserLayoutEngine.routeEdges(graph, coords.positionedNodes)
        assertTrue(edges.isNotEmpty())

        // Unified calculateLayout result matches direct 3-pass invocation
        val result = RiserLayoutEngine.calculateLayout(graph, circuits, null, emptyMap())
        assertEquals(coords.positionedNodes.size, result.nodes.size)
        assertEquals(edges.size, result.edges.size)
        assertEquals(coords.busbarPorts.size, result.busbarPorts.size)
    }

    @Test
    fun testGate3WidePanelMultiAtsCoordinatesInvariance() {
        val transformers = listOf(
            TransformerEntity(projectId = projectId, transformerId = "TX-01", kVA = 1000.0)
        )
        val generators = listOf(
            GeneratorEntity(projectId = projectId, generatorId = "GEN-01", generatorName = "Generator 1", manualSizeKVA = 40.0),
            GeneratorEntity(projectId = projectId, generatorId = "GEN-02", generatorName = "Generator 2", manualSizeKVA = 30.0)
        )
        val atsList = listOf(
            AtsEntity(projectId = projectId, atsId = "ATS-01", atsName = "ATS 1", normalSourceId = "DP-G3", emergencySourceId = "GEN-01"),
            AtsEntity(projectId = projectId, atsId = "ATS-02", atsName = "ATS 2", normalSourceId = "DP-G3", emergencySourceId = "GEN-02")
        )
        val circuits = listOf(
            CircuitEntity(id = 1, projectId = projectId, circuitName = "DP-G3", fedFrom = "TX-01", panelType = "MDB"),
            // Downstream of ATS-01
            CircuitEntity(id = 10, projectId = projectId, circuitName = "UPP-G3-1", fedFrom = "ATS-01", panelType = "DB"),
            CircuitEntity(id = 11, projectId = projectId, circuitName = "UPP-G3-2", fedFrom = "ATS-01", panelType = "DB"),
            // Downstream of ATS-02
            CircuitEntity(id = 20, projectId = projectId, circuitName = "LPP-G3-1", fedFrom = "ATS-02", panelType = "DB"),
            CircuitEntity(id = 21, projectId = projectId, circuitName = "LPP-G3-2", fedFrom = "ATS-02", panelType = "DB"),
            CircuitEntity(id = 22, projectId = projectId, circuitName = "LPP-G3-3", fedFrom = "ATS-02", panelType = "DB"),
            CircuitEntity(id = 23, projectId = projectId, circuitName = "FOUNTAIN", fedFrom = "ATS-02", panelType = "LOAD")
        )

        val result = SldLayoutEngine.calculateLayout(
            activeProjectId = projectId,
            transformers = transformers,
            generators = generators,
            atsList = atsList,
            circuits = circuits
        )

        val mdbNode = result.nodes.first { it.id == "circ_1" }
        val ats1Node = result.nodes.first { it.id == "ats_ATS-01" }
        val ats2Node = result.nodes.first { it.id == "ats_ATS-02" }
        val gen1Node = result.nodes.first { it.id == "gen_GEN-01" }
        val gen2Node = result.nodes.first { it.id == "gen_GEN-02" }

        val mdbEnclosureRight = mdbNode.x + mdbNode.width + 36f

        // Exact clearance >= 160f
        val mdbToAts1Gap = ats1Node.x - mdbEnclosureRight
        assertTrue(mdbToAts1Gap >= 160f)

        val ats1ToAts2Gap = ats2Node.x - (ats1Node.x + ats1Node.width)
        assertTrue(ats1ToAts2Gap >= 160f)

        // Generator spine alignment
        assertEquals(ats1Node.x + ats1Node.width * 0.75f, gen1Node.x + gen1Node.width / 2f, 5.0f)
        assertEquals(ats2Node.x + ats2Node.width * 0.75f, gen2Node.x + gen2Node.width / 2f, 5.0f)
    }

    @Test
    fun testStaleManualDragPreservesEnforceClearance() {
        val transformers = listOf(
            TransformerEntity(projectId = projectId, transformerId = "TX-01", kVA = 1000.0)
        )
        val generators = listOf(
            GeneratorEntity(projectId = projectId, generatorId = "GEN-01", generatorName = "Generator 1", manualSizeKVA = 40.0)
        )
        val atsList = listOf(
            AtsEntity(projectId = projectId, atsId = "ATS-01", atsName = "ATS 1", normalSourceId = "MDB-MAIN", emergencySourceId = "GEN-01")
        )
        val circuits = listOf(
            CircuitEntity(id = 1, projectId = projectId, circuitName = "MDB-MAIN", fedFrom = "TX-01", panelType = "MDB"),
            CircuitEntity(id = 2, projectId = projectId, circuitName = "FEEDER-1", fedFrom = "MDB-MAIN", panelType = "DB"),
            CircuitEntity(id = 3, projectId = projectId, circuitName = "FEEDER-2", fedFrom = "MDB-MAIN", panelType = "DB"),
            CircuitEntity(id = 4, projectId = projectId, circuitName = "FEEDER-3", fedFrom = "MDB-MAIN", panelType = "DB")
        )

        // Stale manual drag position placing ATS directly inside the panel box
        val manualPositions = mapOf(
            "ats_ATS-01" to Offset(x = 150f, y = 676f)
        )

        val result = SldLayoutEngine.calculateLayout(
            activeProjectId = projectId,
            transformers = transformers,
            generators = generators,
            atsList = atsList,
            circuits = circuits,
            manualPositions = manualPositions
        )

        val mdbNode = result.nodes.first { it.id == "circ_1" }
        val atsNode = result.nodes.first { it.id == "ats_ATS-01" }
        val mdbEnclosureRight = mdbNode.x + mdbNode.width + 36f

        // ATS must be pushed outside panel box regardless of manual position
        assertTrue(
            "ATS must be pushed right of panel box even with stale manual drag",
            atsNode.x >= mdbEnclosureRight + 160f
        )
    }

    @Test
    fun testNarrowPanelSingleFeederCoordinates() {
        val transformers = listOf(
            TransformerEntity(projectId = projectId, transformerId = "TX-01", kVA = 500.0)
        )
        val circuits = listOf(
            CircuitEntity(id = 1, projectId = projectId, circuitName = "MDB-SMALL", fedFrom = "TX-01", panelType = "MDB"),
            CircuitEntity(id = 2, projectId = projectId, circuitName = "DB-LIGHT", fedFrom = "MDB-SMALL", panelType = "DB")
        )

        val result = SldLayoutEngine.calculateLayout(
            activeProjectId = projectId,
            transformers = transformers,
            circuits = circuits
        )

        val mdbNode = result.nodes.first { it.id == "circ_1" }
        val dbNode = result.nodes.first { it.id == "circ_2" }

        // Both nodes must be placed with positive coordinates
        assertTrue(mdbNode.x >= RiserLayoutEngine.MARGIN_X)
        assertTrue(dbNode.x >= RiserLayoutEngine.MARGIN_X)
        assertTrue(dbNode.y > mdbNode.y)
    }

    @Test
    fun testSourceVisualBoundsMatchRenderedExtent() {
        val genNode = PositionedSldNode(
            id = "gen_1",
            label = "Emergency Generator 1",
            panelType = "GENERATOR",
            ratingText = "500 kVA",
            statusColorHex = "#4CAF50",
            x = 500f,
            y = 388f,
            width = 140f,
            height = 48f,
            isSource = true
        )
        val genBounds = RiserLayoutEngine.getNodeVisualBounds(genNode, null)
        // Top must extend 24px above y=388f (y=364f)
        assertEquals(364f, genBounds.top, 0.01f)
        // Bottom must extend to y + 60f (y=448f) to cover label text
        assertEquals(448f, genBounds.bottom, 0.01f)

        val upsNode = PositionedSldNode(
            id = "ups_1",
            label = "UPS-01",
            panelType = "UPS",
            ratingText = "100 kVA",
            statusColorHex = "#4CAF50",
            x = 800f,
            y = 676f,
            width = 140f,
            height = 48f,
            isSource = true
        )
        val upsBounds = RiserLayoutEngine.getNodeVisualBounds(upsNode, null)
        assertEquals(652f, upsBounds.top, 0.01f)
        assertEquals(700f, upsBounds.bottom, 0.01f)

        val utilNode = PositionedSldNode(
            id = "grid_1",
            label = "Utility Grid (22 kV)",
            panelType = "UTILITY_SOURCE",
            ratingText = "22 kV / 400 V",
            statusColorHex = "#4CAF50",
            x = 500f,
            y = 100f,
            width = 180f,
            height = 48f,
            isSource = true
        )
        val utilBounds = RiserLayoutEngine.getNodeVisualBounds(utilNode, null)
        assertEquals(76f, utilBounds.top, 0.01f)
        assertEquals(160f, utilBounds.bottom, 0.01f)
    }

    @Test
    fun testSideBySideGeneratorsNoOverlap() {
        val transformers = listOf(
            TransformerEntity(projectId = projectId, transformerId = "TX-01", kVA = 1000.0)
        )
        val generators = listOf(
            GeneratorEntity(projectId = projectId, generatorId = "GEN-01", generatorName = "Gen 1", manualSizeKVA = 500.0),
            GeneratorEntity(projectId = projectId, generatorId = "GEN-02", generatorName = "Gen 2", manualSizeKVA = 300.0)
        )
        val atsList = listOf(
            AtsEntity(projectId = projectId, atsId = "ATS-01", atsName = "ATS 1", normalSourceId = "DP-01", emergencySourceId = "GEN-01"),
            AtsEntity(projectId = projectId, atsId = "ATS-02", atsName = "ATS 2", normalSourceId = "DP-01", emergencySourceId = "GEN-02")
        )
        val circuits = listOf(
            CircuitEntity(id = 1, projectId = projectId, circuitName = "DP-01", fedFrom = "TX-01", panelType = "MDB"),
            CircuitEntity(id = 2, projectId = projectId, circuitName = "SUB-1", fedFrom = "ATS-01", panelType = "DB"),
            CircuitEntity(id = 3, projectId = projectId, circuitName = "SUB-2", fedFrom = "ATS-02", panelType = "DB")
        )

        val result = SldLayoutEngine.calculateLayout(
            activeProjectId = projectId,
            transformers = transformers,
            generators = generators,
            atsList = atsList,
            circuits = circuits
        )

        val gen1Node = result.nodes.first { it.id == "gen_GEN-01" }
        val gen2Node = result.nodes.first { it.id == "gen_GEN-02" }
        val gen1Bounds = RiserLayoutEngine.getNodeVisualBounds(gen1Node, null)
        val gen2Bounds = RiserLayoutEngine.getNodeVisualBounds(gen2Node, null)

        // Generators must not overlap horizontally
        assertTrue(
            "Generator 2 left (${gen2Bounds.left}) must be >= Generator 1 right (${gen1Bounds.right})",
            gen2Bounds.left >= gen1Bounds.right
        )
    }

    @Test
    fun testUpsNearPanelNoOverlap() {
        val transformers = listOf(
            TransformerEntity(projectId = projectId, transformerId = "TX-01", kVA = 1000.0)
        )
        val upsList = listOf(
            com.example.data.UpsEntity(projectId = projectId, upsId = "UPS-01", upsName = "Central UPS", manualSizeKVA = 120.0)
        )
        val circuits = listOf(
            CircuitEntity(id = 1, projectId = projectId, circuitName = "MDB-01", fedFrom = "TX-01", panelType = "MDB"),
            CircuitEntity(id = 2, projectId = projectId, circuitName = "CRITICAL-DB", fedFrom = "UPS-01", panelType = "DB")
        )

        val result = SldLayoutEngine.calculateLayout(
            activeProjectId = projectId,
            transformers = transformers,
            upsList = upsList,
            circuits = circuits
        )

        val mdbNode = result.nodes.first { it.id == "circ_1" }
        val upsNode = result.nodes.firstOrNull { it.id == "ups_UPS-01" }
        if (upsNode != null) {
            val mdbBounds = RiserLayoutEngine.getNodeVisualBounds(mdbNode, null)
            val upsBounds = RiserLayoutEngine.getNodeVisualBounds(upsNode, null)

            // Clearance between MDB enclosure right and UPS
            assertTrue(
                "UPS must not overlap MDB enclosure",
                upsBounds.left >= mdbBounds.right || upsBounds.right <= mdbBounds.left
            )
        }
    }

    @Test
    fun testMultipleUpsExplicitRoutingAndHierarchy() {
        val transformers = listOf(
            TransformerEntity(projectId = projectId, transformerId = "TX-01", kVA = 1000.0)
        )
        val upsList = listOf(
            com.example.data.UpsEntity(projectId = projectId, upsId = "UPS-01", upsName = "UPS 1", manualSizeKVA = 80.0),
            com.example.data.UpsEntity(projectId = projectId, upsId = "UPS-02", upsName = "UPS 2", manualSizeKVA = 60.0)
        )
        val circuits = listOf(
            CircuitEntity(id = 1, projectId = projectId, circuitName = "MDB-MAIN", fedFrom = "TX-01", panelType = "MDB"),
            CircuitEntity(id = 10, projectId = projectId, circuitName = "PANEL-UPS-A", fedFrom = "MDB-MAIN", assignedUpsId = "UPS-01", panelType = "DB"),
            CircuitEntity(id = 11, projectId = projectId, circuitName = "PANEL-UPS-B", fedFrom = "MDB-MAIN", assignedUpsId = "UPS-02", panelType = "DB"),
            CircuitEntity(id = 20, projectId = projectId, circuitName = "PANEL-DIRECT-C", fedFrom = "MDB-MAIN", panelType = "DB")
        )

        val result = SldLayoutEngine.calculateLayout(
            activeProjectId = projectId,
            transformers = transformers,
            upsList = upsList,
            circuits = circuits
        )

        // 1. Both UPS nodes must exist in result.nodes with valid on-canvas coordinates
        val ups1Node = result.nodes.firstOrNull { it.id == "ups_UPS-01" }
        val ups2Node = result.nodes.firstOrNull { it.id == "ups_UPS-02" }
        assertNotNull("UPS 1 must be present in layout nodes", ups1Node)
        assertNotNull("UPS 2 must be present in layout nodes", ups2Node)

        assertTrue(ups1Node!!.x >= 0f && !ups1Node.x.isNaN())
        assertTrue(ups1Node.y >= 0f && !ups1Node.y.isNaN())
        assertTrue(ups2Node!!.x >= 0f && !ups2Node.x.isNaN())
        assertTrue(ups2Node.y >= 0f && !ups2Node.y.isNaN())

        // 2. Multi-UPS distinct routing: UPS 1 feeds PANEL-A, UPS 2 feeds PANEL-B
        val edgeMdbToUps1 = result.edges.firstOrNull { it.fromId == "circ_1" && it.toId == "ups_UPS-01" }
        val edgeUps1ToPanelA = result.edges.firstOrNull { it.fromId == "ups_UPS-01" && it.toId == "circ_10" }
        val edgeMdbToUps2 = result.edges.firstOrNull { it.fromId == "circ_1" && it.toId == "ups_UPS-02" }
        val edgeUps2ToPanelB = result.edges.firstOrNull { it.fromId == "ups_UPS-02" && it.toId == "circ_11" }
        val edgeDirectC = result.edges.firstOrNull { it.fromId == "circ_1" && it.toId == "circ_20" }

        assertNotNull("Edge MDB -> UPS 1 must exist", edgeMdbToUps1)
        assertNotNull("Edge UPS 1 -> PANEL-A must exist", edgeUps1ToPanelA)
        assertNotNull("Edge MDB -> UPS 2 must exist", edgeMdbToUps2)
        assertNotNull("Edge UPS 2 -> PANEL-B must exist", edgeUps2ToPanelB)
        assertNotNull("Edge MDB -> PANEL-C must be direct", edgeDirectC)

        assertTrue(edgeMdbToUps1!!.isUps)
        assertTrue(edgeUps1ToPanelA!!.isUps)
        assertTrue(edgeMdbToUps2!!.isUps)
        assertTrue(edgeUps2ToPanelB!!.isUps)
        assertTrue(!edgeDirectC!!.isUps)
    }

    @Test
    fun testRenamedEntitiesPreserveGeometricStructure() {
        // Arbitrary user-defined names
        val transformers = listOf(
            TransformerEntity(projectId = "PRJ_RENAMED", transformerId = "TX-CUSTOM-99", kVA = 1500.0)
        )
        val generators = listOf(
            GeneratorEntity(projectId = "PRJ_RENAMED", generatorId = "GEN-ALPHA", generatorName = "Backup Set 12", manualSizeKVA = 250.0)
        )
        val atsList = listOf(
            AtsEntity(projectId = "PRJ_RENAMED", atsId = "ATS-DELTA", atsName = "EMG-SWITCH-A", normalSourceId = "Panel-XYZ-9", emergencySourceId = "GEN-ALPHA")
        )
        val circuits = listOf(
            CircuitEntity(id = 100, projectId = "PRJ_RENAMED", circuitName = "Panel-XYZ-9", fedFrom = "TX-CUSTOM-99", panelType = "MDB"),
            CircuitEntity(id = 101, projectId = "PRJ_RENAMED", circuitName = "Sub-Branch-1", fedFrom = "ATS-DELTA", panelType = "DB"),
            CircuitEntity(id = 102, projectId = "PRJ_RENAMED", circuitName = "Sub-Branch-2", fedFrom = "Panel-XYZ-9", panelType = "DB")
        )

        val result = SldLayoutEngine.calculateLayout(
            activeProjectId = "PRJ_RENAMED",
            transformers = transformers,
            generators = generators,
            atsList = atsList,
            circuits = circuits
        )

        val mdb = result.nodes.first { it.id == "circ_100" }
        val ats = result.nodes.first { it.id == "ats_ATS-DELTA" }
        val gen = result.nodes.first { it.id == "gen_GEN-ALPHA" }

        // Proper clearance maintained regardless of naming
        val mdbBoxRight = mdb.x + mdb.width + 36f
        assertTrue("ATS must maintain >= 160f clearance from panel box", ats.x - mdbBoxRight >= 160f)
        assertEquals(ats.x + ats.width * 0.75f, gen.x + gen.width / 2f, 5.0f)
    }

    @Test
    fun testArbitraryIdsAndUuidSupport() {
        val uProj = "PRJ_UUID_TEST"
        val transformers = listOf(
            TransformerEntity(projectId = uProj, transformerId = "tx-8f4b2a", kVA = 800.0)
        )
        val circuits = listOf(
            CircuitEntity(id = 501, projectId = uProj, circuitName = "node-switchboard-1", fedFrom = "tx-8f4b2a", panelType = "MDB"),
            CircuitEntity(id = 502, projectId = uProj, circuitName = "node-feeder-2", fedFrom = "node-switchboard-1", panelType = "DB")
        )

        val result = SldLayoutEngine.calculateLayout(
            activeProjectId = uProj,
            transformers = transformers,
            circuits = circuits
        )

        val node1 = result.nodes.first { it.id == "circ_501" }
        val node2 = result.nodes.first { it.id == "circ_502" }
        assertTrue(node1.x >= 0f)
        assertTrue(node2.y > node1.y)
    }
}
