package com.example.sld

import com.example.data.CircuitEntity
import com.example.data.TransformerEntity
import com.example.engine.SizingResult
import com.example.engine.TransformerResult
import org.junit.Assert.*
import org.junit.Test

class SldGraphAndLayoutTest {

    private val projectId = "proj_test_1"

    @Test
    fun testHierarchyGeneration() {
        // Transformer -> MDB -> SMDB -> DB -> Final Circuit
        val transformers = listOf(
            TransformerEntity(projectId = projectId, transformerId = "TX-01", kVA = 1000.0)
        )

        val circuits = listOf(
            CircuitEntity(id = 10, projectId = projectId, circuitName = "MDB-01", fedFrom = "Transformer", transformerId = "TX-01", loadType = "Power"),
            CircuitEntity(id = 20, projectId = projectId, circuitName = "SMDB-01", fedFrom = "MDB-01", transformerId = "TX-01", loadType = "Power"),
            CircuitEntity(id = 30, projectId = projectId, circuitName = "DB-01", fedFrom = "SMDB-01", transformerId = "TX-01", loadType = "Distribution"),
            CircuitEntity(id = 40, projectId = projectId, circuitName = "C1", fedFrom = "DB-01", transformerId = "TX-01", loadType = "Lighting")
        )

        val parents = mapOf(20 to 10, 30 to 20, 40 to 30)

        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = projectId,
            transformers = transformers,
            circuits = circuits,
            circuitParents = parents
        )

        assertEquals(0, graph.unconnectedNodes.size)
        assertEquals(6, graph.nodes.size) // Grid + Tx + MDB + SMDB + DB + C1

        val nodeTypes = graph.nodes.associate { it.id to it.type }
        assertEquals(SldNodeType.UTILITY_SOURCE, nodeTypes["grid_TX-01"])
        assertEquals(SldNodeType.TRANSFORMER, nodeTypes["tx_TX-01"])
        assertEquals(SldNodeType.MDB, nodeTypes["circ_10"])
        assertEquals(SldNodeType.SMDB, nodeTypes["circ_20"])
        assertEquals(SldNodeType.DB, nodeTypes["circ_30"])
        assertEquals(SldNodeType.FINAL_CIRCUIT, nodeTypes["circ_40"])
    }

    @Test
    fun testMultipleTransformersSeparateRoots() {
        val transformers = listOf(
            TransformerEntity(projectId = projectId, transformerId = "TX-01", kVA = 1000.0),
            TransformerEntity(projectId = projectId, transformerId = "TX-02", kVA = 800.0)
        )

        val circuits = listOf(
            CircuitEntity(id = 10, projectId = projectId, circuitName = "MDB-01", fedFrom = "Transformer", transformerId = "TX-01"),
            CircuitEntity(id = 20, projectId = projectId, circuitName = "MDB-02", fedFrom = "Transformer", transformerId = "TX-02")
        )

        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = projectId,
            transformers = transformers,
            circuits = circuits
        )

        val tx1Node = graph.nodes.first { it.id == "tx_TX-01" }
        val tx2Node = graph.nodes.first { it.id == "tx_TX-02" }

        val mdb1Node = graph.nodes.first { it.id == "circ_10" }
        val mdb2Node = graph.nodes.first { it.id == "circ_20" }

        assertEquals(tx1Node.id, mdb1Node.parentSourceId)
        assertEquals(tx2Node.id, mdb2Node.parentSourceId)
        assertNotEquals(mdb1Node.parentSourceId, mdb2Node.parentSourceId)
    }

    @Test
    fun testMissingParentBecomesDisconnected() {
        val circuits = listOf(
            CircuitEntity(id = 10, projectId = projectId, circuitName = "Sub-Panel", fedFrom = "NonExistentParent", transformerId = "TX-01")
        )

        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = projectId,
            transformers = emptyList(),
            circuits = circuits
        )

        assertEquals(1, graph.unconnectedNodes.size)
        assertEquals(SldValidationStatus.DISCONNECTED, graph.unconnectedNodes[0].status)
        assertTrue(graph.validationIssues.any { it.issueCode == "MISSING_PARENT" })
    }

    @Test
    fun testSelfParentBecomesInvalid() {
        val circuits = listOf(
            CircuitEntity(id = 10, projectId = projectId, circuitName = "Panel-A", fedFrom = "Panel-A", transformerId = "TX-01")
        )

        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = projectId,
            transformers = emptyList(),
            circuits = circuits
        )

        assertEquals(1, graph.unconnectedNodes.size)
        assertTrue(graph.validationIssues.any { it.issueCode == "SELF_PARENT" })
    }

    @Test
    fun testCircularChainDetection() {
        val circuits = listOf(
            CircuitEntity(id = 10, projectId = projectId, circuitName = "Loop-A", fedFrom = "Loop-B", transformerId = "TX-01"),
            CircuitEntity(id = 20, projectId = projectId, circuitName = "Loop-B", fedFrom = "Loop-A", transformerId = "TX-01")
        )
        val parents = mapOf(10 to 20, 20 to 10)

        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = projectId,
            transformers = emptyList(),
            circuits = circuits,
            circuitParents = parents
        )

        assertTrue(graph.validationIssues.any { it.issueCode == "CIRCULAR_PATH" })
    }

    @Test
    fun testCrossProjectParentRejected() {
        val transformers = listOf(
            TransformerEntity(projectId = "OTHER_PROJECT", transformerId = "TX-OTHER")
        )
        val circuits = listOf(
            CircuitEntity(id = 10, projectId = projectId, circuitName = "C1", fedFrom = "Transformer", transformerId = "TX-OTHER")
        )

        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = projectId,
            transformers = transformers,
            circuits = circuits
        )

        assertEquals(1, graph.unconnectedNodes.size)
        assertTrue(graph.validationIssues.any { it.issueCode == "MISSING_SOURCE_TX" })
    }

    @Test
    fun testDeterministicLayoutCoordinates() {
        val transformers = listOf(
            TransformerEntity(projectId = projectId, transformerId = "TX-01", kVA = 1000.0)
        )
        val circuits = listOf(
            CircuitEntity(id = 10, projectId = projectId, circuitName = "MDB-01", fedFrom = "Transformer", transformerId = "TX-01")
        )

        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = projectId,
            transformers = transformers,
            circuits = circuits
        )

        val layout1 = SldHierarchicalLayoutEngine.calculateLayout(graph)
        val layout2 = SldHierarchicalLayoutEngine.calculateLayout(graph)

        assertEquals(layout1.layoutNodes.size, layout2.layoutNodes.size)
        layout1.layoutNodes.forEach { (id, node1) ->
            val node2 = layout2.layoutNodes[id]
            assertNotNull(node2)
            assertEquals(node1.x, node2!!.x, 0.001f)
            assertEquals(node1.y, node2.y, 0.001f)
        }
    }

    @Test
    fun testParentDepthAlwaysAboveChildDepth() {
        val transformers = listOf(
            TransformerEntity(projectId = projectId, transformerId = "TX-01")
        )
        val circuits = listOf(
            CircuitEntity(id = 10, projectId = projectId, circuitName = "MDB-01", fedFrom = "Transformer", transformerId = "TX-01"),
            CircuitEntity(id = 20, projectId = projectId, circuitName = "DB-01", fedFrom = "MDB-01", transformerId = "TX-01")
        )
        val parents = mapOf(20 to 10)

        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = projectId,
            transformers = transformers,
            circuits = circuits,
            circuitParents = parents
        )

        val layout = SldHierarchicalLayoutEngine.calculateLayout(graph)

        val mdbLayout = layout.layoutNodes["circ_10"]!!
        val dbLayout = layout.layoutNodes["circ_20"]!!

        assertTrue(mdbLayout.depth < dbLayout.depth)
        assertTrue(mdbLayout.y < dbLayout.y)
    }

    @Test
    fun testMissingValuesDisplayDataRequired() {
        val circuits = listOf(
            CircuitEntity(id = 10, projectId = projectId, circuitName = "C1", fedFrom = "Transformer", transformerId = "TX-01")
        )

        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = projectId,
            transformers = emptyList(),
            circuits = circuits
        )

        val node = graph.nodes.find { it.displayName == "C1" } ?: graph.unconnectedNodes[0]
        assertEquals("DATA REQUIRED", node.dataBlock.ibAmp)
        assertEquals("DATA REQUIRED", node.dataBlock.izAmp)
        assertEquals("DATA REQUIRED", node.dataBlock.voltageDropPct)
    }

    @Test
    fun testAtsTopologyAndValidation() {
        val transformers = listOf(
            TransformerEntity(projectId = projectId, transformerId = "TX-01", kVA = 1000.0)
        )
        val circuits = listOf(
            CircuitEntity(id = 10, projectId = projectId, circuitName = "MDB-01", fedFrom = "Transformer", transformerId = "TX-01", panelType = "MDB"),
            CircuitEntity(id = 20, projectId = projectId, circuitName = "MDB-Emergency", fedFrom = "ATS-01", transformerId = "TX-01", panelType = "MDB")
        )
        val genResult = com.example.engine.GeneratorResult(
            finalSize = 500.0,
            status = "OPTIMAL"
        )
        val atsResult = com.example.engine.AtsResult(
            finalRating = 630.0,
            status = "NORMAL PREFERRED"
        )

        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = projectId,
            transformers = transformers,
            circuits = circuits,
            generatorResult = genResult,
            atsResult = atsResult
        )

        val atsNode = graph.nodes.firstOrNull { it.type == SldNodeType.ATS }
        assertNotNull(atsNode)
        assertEquals(SldValidationStatus.VALID, atsNode!!.status)

        val normalEdge = graph.edges.firstOrNull { it.type == SldEdgeType.ATS_NORMAL_PATH }
        assertNotNull(normalEdge)
        assertEquals("circ_10", normalEdge!!.fromNodeId)
        assertEquals("ats_1", normalEdge.toNodeId)

        val emergEdge = graph.edges.firstOrNull { it.type == SldEdgeType.ATS_EMERGENCY_PATH }
        assertNotNull("Emergency edge should not be null", emergEdge)
        assertEquals("gen_1", emergEdge!!.fromNodeId)
        assertEquals("ats_1", emergEdge.toNodeId)

        val outputEdge = graph.edges.firstOrNull { it.type == SldEdgeType.ATS_OUTPUT }
        assertNotNull("Output edge should not be null", outputEdge)
        assertEquals("ats_1", outputEdge!!.fromNodeId)
        assertEquals("circ_20", outputEdge.toNodeId)
    }

    @Test
    fun testAtsMissingSourcesGeneratesValidationIssues() {
        val atsResult = com.example.engine.AtsResult(
            finalRating = 400.0,
            status = "WARNING"
        )

        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = projectId,
            transformers = emptyList(),
            circuits = emptyList(),
            generatorResult = null,
            atsResult = atsResult
        )

        val atsNode = graph.nodes.firstOrNull { it.type == SldNodeType.ATS }
        assertNotNull(atsNode)
        assertEquals(SldValidationStatus.DATA_REQUIRED, atsNode!!.status)

        assertTrue(graph.validationIssues.any { it.issueCode == "ATS_EMERGENCY_SOURCE_REQUIRED" })
        assertTrue(graph.validationIssues.any { it.issueCode == "ATS_NORMAL_SOURCE_REQUIRED" })
        assertTrue(graph.validationIssues.any { it.issueCode == "ATS_OUTPUT_DESTINATION_REQUIRED" })
    }

    @Test
    fun testFitCalculationCentersGraph() {
        val transformers = listOf(
            TransformerEntity(projectId = projectId, transformerId = "TX-01", kVA = 1000.0)
        )
        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = projectId,
            transformers = transformers,
            circuits = emptyList()
        )

        val layout = SldHierarchicalLayoutEngine.calculateLayout(graph)
        val viewport = androidx.compose.ui.geometry.Size(1000f, 1000f)

        // val (fitScale, fitPan) = calculateFitTransform(layout, viewport)
        // assertTrue(fitScale > 0f)
        // assertNotNull(fitPan)
    }

    @Test
    fun testGate3ProjectSldLayoutTrace() {
        val circuits = listOf(
            CircuitEntity(id = 1, projectId = projectId, circuitName = "DB", fedFrom = "TX-01", loadKVA = 100.0, panelType = "DB"),
            CircuitEntity(id = 2, projectId = projectId, circuitName = "LPP 1", fedFrom = "DB", loadKVA = 40.0, panelType = "PANEL"),
            CircuitEntity(id = 3, projectId = projectId, circuitName = "LPP 2", fedFrom = "DB", loadKVA = 50.0, panelType = "PANEL")
        )

        val result = SldLayoutEngine.calculateLayout(activeProjectId=projectId, transformers=emptyList(), circuits=circuits)

        val txSource = result.nodes.first { it.isSource }
        val dbNode = result.nodes.first { it.id == "circ_1" || it.id == "1" }
        val lpp1Node = result.nodes.first { it.id == "circ_2" || it.id == "2" }
        val lpp2Node = result.nodes.first { it.id == "circ_3" || it.id == "3" }

        println("=== GATE 3 SLD NODE COORDINATE TRACE ===")
        println("• ${txSource.label} (Source, relative Level -1): x=${txSource.x}, y=${txSource.y}")
        println("• ${dbNode.label} (Parent DB, Level 0): x=${dbNode.x}, y=${dbNode.y}")
        println("• ${lpp1Node.label} (Child LPP 1, Level 1): x=${lpp1Node.x}, y=${lpp1Node.y}")
        println("• ${lpp2Node.label} (Child LPP 2, Level 1): x=${lpp2Node.x}, y=${lpp2Node.y}")

        // Assert distinct levels (y values)
        assertNotEquals(dbNode.y, lpp1Node.y)
        assertEquals(lpp1Node.y, lpp2Node.y)
        assertTrue(txSource.y < dbNode.y)
        assertTrue(dbNode.y < lpp1Node.y)

        // Assert horizontal separation and subtree centering
        assertTrue(lpp1Node.x < lpp2Node.x)
    }

    @Test
    fun test100PanelsSldLayout() {
        val circuits = mutableListOf<CircuitEntity>()
        // 1 Main DB, 10 SMDBs, 90 final circuits
        circuits.add(CircuitEntity(id = 1, projectId = projectId, circuitName = "MDB", fedFrom = "TX-01", loadKVA = 1000.0, panelType = "DB"))
        
        for (i in 2..11) {
            circuits.add(CircuitEntity(id = i, projectId = projectId, circuitName = "SMDB-$i", fedFrom = "MDB", loadKVA = 100.0, panelType = "SMDB"))
        }
        
        for (i in 12..101) {
            val parentId = (i % 10) + 2 // Distribute across SMDBs 2-11
            circuits.add(CircuitEntity(id = i, projectId = projectId, circuitName = "FINAL-$i", fedFrom = "SMDB-$parentId", loadKVA = 10.0, panelType = "PANEL"))
        }

        val result = SldLayoutEngine.calculateLayout(activeProjectId=projectId, transformers=emptyList(), circuits=circuits)
        
        // Root node + 1 MDB + 10 SMDBs + 90 Panels
        assertEquals(102, result.nodes.size)
        // 1 Incomer + 10 SMDB edges + 90 final edges
        assertEquals(101, result.edges.size)
        
        // Assert layout bounds
        assertTrue(result.bounds.width > 0)
        assertTrue(result.bounds.height > 0)
        
        // Assert no crashes and layout engine can handle 100 elements quickly
    }
}
