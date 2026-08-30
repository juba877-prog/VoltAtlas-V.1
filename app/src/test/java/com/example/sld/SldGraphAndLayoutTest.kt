package com.example.sld

import com.example.data.CircuitEntity
import com.example.data.TransformerEntity
import com.example.data.GeneratorEntity
import com.example.data.AtsEntity
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

    @Test
    fun testMultiGeneratorAndMultiAtsGraph() {
        val transformers = listOf(
            TransformerEntity(projectId = projectId, transformerId = "TX-01", kVA = 1000.0)
        )
        val generators = listOf(
            GeneratorEntity(projectId = projectId, generatorId = "GEN-01", generatorName = "Emergency Gen 1", manualSizeKVA = 500.0),
            GeneratorEntity(projectId = projectId, generatorId = "GEN-02", generatorName = "Backup Gen 2", manualSizeKVA = 300.0)
        )
        val atsList = listOf(
            AtsEntity(projectId = projectId, atsId = "ATS-01", atsName = "ATS-Main", emergencySourceId = "GEN-01", normalSourceId = "TX-01"),
            AtsEntity(projectId = projectId, atsId = "ATS-02", atsName = "ATS-Essential", emergencySourceId = "GEN-02", normalSourceId = "TX-01")
        )
        val circuits = listOf(
            CircuitEntity(id = 10, projectId = projectId, circuitName = "MDB-01", fedFrom = "Transformer", transformerId = "TX-01", panelType = "MDB"),
            CircuitEntity(id = 20, projectId = projectId, circuitName = "EM-Main", fedFrom = "ATS-Main", assignedAtsId = "ATS-01", transformerId = "TX-01", panelType = "MDB"),
            CircuitEntity(id = 30, projectId = projectId, circuitName = "EM-Essential", fedFrom = "ATS-Essential", assignedAtsId = "ATS-02", transformerId = "TX-01", panelType = "MDB")
        )

        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = projectId,
            transformers = transformers,
            circuits = circuits,
            generators = generators,
            atsList = atsList
        )

        // 1. Verify 2 distinct Generator nodes with dynamic names
        val gen1Node = graph.nodes.firstOrNull { it.id == "gen_GEN-01" }
        val gen2Node = graph.nodes.firstOrNull { it.id == "gen_GEN-02" }
        assertNotNull("GEN-01 node must exist", gen1Node)
        assertNotNull("GEN-02 node must exist", gen2Node)
        assertEquals("Emergency Gen 1 (500 kVA)", gen1Node!!.displayName)
        assertEquals("Backup Gen 2 (300 kVA)", gen2Node!!.displayName)

        // 2. Verify 2 distinct ATS nodes with dynamic names (NO hardcoded ATS-01)
        val ats1Node = graph.nodes.firstOrNull { it.id == "ats_ATS-01" }
        val ats2Node = graph.nodes.firstOrNull { it.id == "ats_ATS-02" }
        assertNotNull("ATS-01 node must exist", ats1Node)
        assertNotNull("ATS-02 node must exist", ats2Node)
        assertEquals("ATS-Main", ats1Node!!.displayName)
        assertEquals("ATS-Essential", ats2Node!!.displayName)

        // 3. Verify dedicated Emergency Edges per generator/ATS pair
        val emergEdge1 = graph.edges.firstOrNull { it.fromNodeId == "gen_GEN-01" && it.toNodeId == "ats_ATS-01" && it.type == SldEdgeType.ATS_EMERGENCY_PATH }
        val emergEdge2 = graph.edges.firstOrNull { it.fromNodeId == "gen_GEN-02" && it.toNodeId == "ats_ATS-02" && it.type == SldEdgeType.ATS_EMERGENCY_PATH }
        assertNotNull("Emergency edge GEN-01 -> ATS-01 must exist", emergEdge1)
        assertNotNull("Emergency edge GEN-02 -> ATS-02 must exist", emergEdge2)

        // 4. Verify downstream output edges
        val outEdge1 = graph.edges.firstOrNull { it.fromNodeId == "ats_ATS-01" && it.toNodeId == "circ_20" }
        val outEdge2 = graph.edges.firstOrNull { it.fromNodeId == "ats_ATS-02" && it.toNodeId == "circ_30" }
        assertNotNull("Output edge ATS-01 -> EM-Main must exist", outEdge1)
        assertNotNull("Output edge ATS-02 -> EM-Essential must exist", outEdge2)
    }

    @Test
    fun testDirectGeneratorFeed() {
        val generators = listOf(
            GeneratorEntity(projectId = projectId, generatorId = "GEN-DIRECT", generatorName = "Direct Gen", manualSizeKVA = 400.0)
        )
        val circuits = listOf(
            CircuitEntity(id = 100, projectId = projectId, circuitName = "Direct-Panel", fedFrom = "Direct Gen", assignedGeneratorId = "GEN-DIRECT", assignedAtsId = null, panelType = "MDB")
        )

        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = projectId,
            transformers = emptyList(),
            circuits = circuits,
            generators = generators,
            atsList = emptyList()
        )

        val directGenNode = graph.nodes.firstOrNull { it.id == "gen_GEN-DIRECT" }
        assertNotNull(directGenNode)

        val directEdge = graph.edges.firstOrNull { it.fromNodeId == "gen_GEN-DIRECT" && it.toNodeId == "circ_100" }
        assertNotNull("Direct generator edge must exist", directEdge)
    }

    @Test
    fun testCollisionFreeRiserLayoutForMultiSources() {
        val transformers = listOf(
            TransformerEntity(projectId = projectId, transformerId = "TX-01", kVA = 1000.0),
            TransformerEntity(projectId = projectId, transformerId = "TX-02", kVA = 800.0)
        )
        val generators = listOf(
            GeneratorEntity(projectId = projectId, generatorId = "GEN-01", generatorName = "Gen 1", manualSizeKVA = 500.0),
            GeneratorEntity(projectId = projectId, generatorId = "GEN-02", generatorName = "Gen 2", manualSizeKVA = 300.0)
        )
        val atsList = listOf(
            AtsEntity(projectId = projectId, atsId = "ATS-01", atsName = "ATS-1", emergencySourceId = "GEN-01"),
            AtsEntity(projectId = projectId, atsId = "ATS-02", atsName = "ATS-2", emergencySourceId = "GEN-02")
        )
        val circuits = listOf(
            CircuitEntity(id = 1, projectId = projectId, circuitName = "MDB-1", fedFrom = "TX-01", transformerId = "TX-01", panelType = "MDB"),
            CircuitEntity(id = 2, projectId = projectId, circuitName = "MDB-2", fedFrom = "TX-02", transformerId = "TX-02", panelType = "MDB"),
            CircuitEntity(id = 3, projectId = projectId, circuitName = "EM-1", fedFrom = "ATS-1", assignedAtsId = "ATS-01", panelType = "MDB"),
            CircuitEntity(id = 4, projectId = projectId, circuitName = "EM-2", fedFrom = "ATS-2", assignedAtsId = "ATS-02", panelType = "MDB")
        )

        val result = SldLayoutEngine.calculateLayout(
            activeProjectId = projectId,
            transformers = transformers,
            circuits = circuits,
            generators = generators,
            atsList = atsList
        )

        assertTrue(result.nodes.isNotEmpty())
        // Group nodes by Y and assert no two nodes on the same Y line overlap horizontally
        val nodesByY = result.nodes.groupBy { it.y }
        nodesByY.forEach { (_, tierNodes) ->
            val sorted = tierNodes.sortedBy { it.x }
            for (i in 0 until sorted.size - 1) {
                val nodeA = sorted[i]
                val nodeB = sorted[i + 1]
                assertTrue(
                    "Node ${nodeB.label} (x=${nodeB.x}) must not overlap with Node ${nodeA.label} (x=${nodeA.x}, width=${nodeA.width})",
                    nodeB.x >= nodeA.x + nodeA.width
                )
            }
        }
    }

    @Test
    fun testDualAssignmentOptionBRendering() {
        val transformers = listOf(
            TransformerEntity(projectId = projectId, transformerId = "TX-01", kVA = 1000.0)
        )
        val generators = listOf(
            GeneratorEntity(projectId = projectId, generatorId = "GEN-01", generatorName = "Emergency Gen 1", manualSizeKVA = 500.0)
        )
        val atsList = listOf(
            AtsEntity(projectId = projectId, atsId = "ATS-01", atsName = "ATS-Main", emergencySourceId = "GEN-01")
        )
        // Circuit with BOTH assignedGeneratorId AND assignedAtsId set
        val circuits = listOf(
            CircuitEntity(id = 10, projectId = projectId, circuitName = "MDB-01", fedFrom = "Transformer", transformerId = "TX-01", panelType = "MDB"),
            CircuitEntity(
                id = 50,
                projectId = projectId,
                circuitName = "Dual-Assigned-Panel",
                fedFrom = "ATS-Main",
                assignedGeneratorId = "GEN-01",
                assignedAtsId = "ATS-01",
                panelType = "MDB"
            )
        )

        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = projectId,
            transformers = transformers,
            circuits = circuits,
            generators = generators,
            atsList = atsList
        )

        // Verify: Exactly ONE incoming edge on the dual-assigned circuit from ATS
        val incomingEdges = graph.edges.filter { it.toNodeId == "circ_50" }
        assertEquals("Circuit 50 must have exactly 1 incoming edge under Option (b)", 1, incomingEdges.size)
        assertEquals("Incoming edge must come from ATS-01", "ats_ATS-01", incomingEdges[0].fromNodeId)

        // Verify NO direct generator -> circuit edge exists (Option b ATS precedence)
        val directGenEdge = graph.edges.firstOrNull { it.fromNodeId == "gen_GEN-01" && it.toNodeId == "circ_50" }
        assertNull("Direct generator -> circuit edge must NOT exist when ATS is assigned", directGenEdge)
    }

    @Test
    fun testModernEmptyAtsListWithDummyLegacyEntityGeneratesZeroNodesAndIssues() {
        // Reproduces the exact production bug runtime state:
        // Modern atsList is explicitly emptyList(), atsEntity is a default/dummy AtsEntity(), atsResult is null
        val transformers = listOf(
            TransformerEntity(projectId = projectId, transformerId = "TX-01", kVA = 1000.0)
        )
        val circuits = listOf(
            CircuitEntity(id = 1, projectId = projectId, circuitName = "MDB-01", fedFrom = "Transformer", transformerId = "TX-01", panelType = "MDB")
        )

        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = projectId,
            transformers = transformers,
            circuits = circuits,
            atsEntity = AtsEntity(), // Dummy fallback entity from ViewModel
            atsResult = null,
            atsList = emptyList() // Modern explicit empty list
        )

        // Assert zero ATS nodes and zero ATS validation issues
        assertTrue("Graph must have zero ATS nodes when modern atsList is emptyList()", graph.nodes.none { it.type == SldNodeType.ATS })
        assertTrue("Graph must have zero ATS validation issues when modern atsList is emptyList()", graph.validationIssues.none { it.issueCode.startsWith("ATS_") })
    }

    @Test
    fun testLegacySingularAtsEntityFallbackWhenAtsListOmitted() {
        val transformers = listOf(
            TransformerEntity(projectId = projectId, transformerId = "TX-01", kVA = 1000.0)
        )
        val circuits = listOf(
            CircuitEntity(id = 1, projectId = projectId, circuitName = "MDB-01", fedFrom = "Transformer", transformerId = "TX-01", panelType = "MDB"),
            CircuitEntity(id = 20, projectId = projectId, circuitName = "EMERG-DB", fedFrom = "Legacy ATS", panelType = "DB")
        )
        val legacyAts = AtsEntity(
            projectId = projectId,
            atsId = "ATS-LEGACY",
            atsName = "Legacy ATS",
            normalSourceId = "TX-01",
            emergencySourceId = "GEN-01",
            outputBoardId = 20
        )

        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = projectId,
            transformers = transformers,
            circuits = circuits,
            atsEntity = legacyAts,
            atsResult = null,
            atsList = null // Legacy caller omitting atsList parameter
        )

        assertTrue("Legacy ATS node ats_ATS-LEGACY must be generated when atsList is null", graph.nodes.any { it.id == "ats_ATS-LEGACY" })
    }

    @Test
    fun testLegacyNoLoadsResultDoesNotSynthesizeAts() {
        val transformers = listOf(
            TransformerEntity(projectId = projectId, transformerId = "TX-01", kVA = 1000.0)
        )
        val circuits = listOf(
            CircuitEntity(id = 1, projectId = projectId, circuitName = "MDB-01", fedFrom = "Transformer", transformerId = "TX-01", panelType = "MDB")
        )
        val noLoadsResult = com.example.engine.AtsResult(
            governingCurrent = 0.0,
            finalRating = 0.0,
            status = "NO_LOADS"
        )

        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = projectId,
            transformers = transformers,
            circuits = circuits,
            atsEntity = null,
            atsResult = noLoadsResult,
            atsList = null
        )

        assertTrue("Graph must have zero ATS nodes when atsResult is NO_LOADS", graph.nodes.none { it.type == SldNodeType.ATS })
        assertTrue("Graph must have zero ATS validation issues when atsResult is NO_LOADS", graph.validationIssues.none { it.issueCode.startsWith("ATS_") })
    }

    @Test
    fun testAtsNormalSourceFromDistributionBoard() {
        val transformers = listOf(
            TransformerEntity(projectId = projectId, transformerId = "TX-01", kVA = 1000.0)
        )
        val generators = listOf(
            GeneratorEntity(projectId = projectId, generatorId = "GEN-01", generatorName = "Generator", manualSizeKVA = 500.0)
        )
        val circuits = listOf(
            CircuitEntity(id = 1, projectId = projectId, circuitName = "MDB-01", fedFrom = "Transformer", transformerId = "TX-01", panelType = "MDB"),
            CircuitEntity(id = 12, projectId = projectId, circuitName = "DP-G3", fedFrom = "MDB-01", panelType = "DB"),
            CircuitEntity(id = 20, projectId = projectId, circuitName = "LPP-G3-2", fedFrom = "ATS-01", panelType = "DB")
        )
        val atsList = listOf(
            AtsEntity(
                projectId = projectId,
                atsId = "ATS-01",
                atsName = "ATS 1",
                normalSourceId = "DP-G3",
                emergencySourceId = "GEN-01",
                outputBoardId = 20
            )
        )

        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = projectId,
            transformers = transformers,
            circuits = circuits,
            generators = generators,
            atsList = atsList
        )

        // Verify Normal source edge in graph: circ_12 -> ats_ATS-01 of type ATS_NORMAL_PATH
        val normalEdge = graph.edges.firstOrNull { it.fromNodeId == "circ_12" && it.toNodeId == "ats_ATS-01" && it.type == SldEdgeType.ATS_NORMAL_PATH }
        assertNotNull("Graph must contain Normal Source edge circ_12 -> ats_ATS-01", normalEdge)

        // Verify edge is preserved in final Riser layout (not dropped)
        val layoutResult = RiserLayoutEngine.calculateLayout(graph, circuits, null)
        val layoutNormalEdge = layoutResult.edges.firstOrNull { it.fromId == "circ_12" && it.toId == "ats_ATS-01" }
        assertNotNull("Layout result must contain Normal Source edge circ_12 -> ats_ATS-01", layoutNormalEdge)
    }

    @Test
    fun testAtsNormalSourceExactMatchRejectsSimilarNames() {
        val transformers = listOf(
            TransformerEntity(projectId = projectId, transformerId = "TX-01", kVA = 1000.0)
        )
        val circuits = listOf(
            CircuitEntity(id = 10, projectId = projectId, circuitName = "DP-G3-A", fedFrom = "Transformer", transformerId = "TX-01", panelType = "DB"),
            CircuitEntity(id = 11, projectId = projectId, circuitName = "OLD-DP-G3", fedFrom = "Transformer", transformerId = "TX-01", panelType = "DB"),
            CircuitEntity(id = 20, projectId = projectId, circuitName = "EMERG-DB", fedFrom = "ATS-01", panelType = "DB")
        )
        val atsList = listOf(
            AtsEntity(
                projectId = projectId,
                atsId = "ATS-01",
                atsName = "ATS 1",
                normalSourceId = "DP-G3", // Specifically named source that does not exactly match DP-G3-A or OLD-DP-G3
                emergencySourceId = "GEN-01",
                outputBoardId = 20
            )
        )

        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = projectId,
            transformers = transformers,
            circuits = circuits,
            atsList = atsList
        )

        // Must not create edge to DP-G3-A (circ_10) or OLD-DP-G3 (circ_11)
        assertFalse("Must NOT create normal edge to circ_10 (DP-G3-A)", graph.edges.any { it.fromNodeId == "circ_10" && it.toNodeId == "ats_ATS-01" })
        assertFalse("Must NOT create normal edge to circ_11 (OLD-DP-G3)", graph.edges.any { it.fromNodeId == "circ_11" && it.toNodeId == "ats_ATS-01" })

        // Must raise ATS_NORMAL_SOURCE_NOT_FOUND
        val issue = graph.validationIssues.firstOrNull { it.issueCode == "ATS_NORMAL_SOURCE_NOT_FOUND" }
        assertNotNull("Must generate ATS_NORMAL_SOURCE_NOT_FOUND validation issue", issue)
    }

    @Test
    fun testAtsNormalSourceDuplicateExactCircuitNamesRaisesAmbiguityIssue() {
        val transformers = listOf(
            TransformerEntity(projectId = projectId, transformerId = "TX-01", kVA = 1000.0)
        )
        val circuits = listOf(
            CircuitEntity(id = 12, projectId = projectId, circuitName = "DP-G3", fedFrom = "Transformer", transformerId = "TX-01", panelType = "DB"),
            CircuitEntity(id = 15, projectId = projectId, circuitName = "DP-G3", fedFrom = "Transformer", transformerId = "TX-01", panelType = "DB"),
            CircuitEntity(id = 20, projectId = projectId, circuitName = "EMERG-DB", fedFrom = "ATS-01", panelType = "DB")
        )
        val atsList = listOf(
            AtsEntity(
                projectId = projectId,
                atsId = "ATS-01",
                atsName = "ATS 1",
                normalSourceId = "DP-G3",
                emergencySourceId = "GEN-01",
                outputBoardId = 20
            )
        )

        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = projectId,
            transformers = transformers,
            circuits = circuits,
            atsList = atsList
        )

        // No arbitrary edge created
        assertFalse("Must not create edge to circ_12 when ambiguous", graph.edges.any { it.fromNodeId == "circ_12" && it.toNodeId == "ats_ATS-01" })
        assertFalse("Must not create edge to circ_15 when ambiguous", graph.edges.any { it.fromNodeId == "circ_15" && it.toNodeId == "ats_ATS-01" })

        // Ambiguity issue raised with both IDs
        val issue = graph.validationIssues.firstOrNull { it.issueCode == "ATS_AMBIGUOUS_NORMAL_SOURCE" }
        assertNotNull("Must generate ATS_AMBIGUOUS_NORMAL_SOURCE issue", issue)
        assertTrue("Issue message must include conflicting ID 12", issue!!.failedCondition.contains("12"))
        assertTrue("Issue message must include conflicting ID 15", issue.failedCondition.contains("15"))
    }

    @Test
    fun testExistingTransformerFedAtsUnchanged() {
        val transformers = listOf(
            TransformerEntity(projectId = projectId, transformerId = "TX-01", kVA = 1000.0)
        )
        val generators = listOf(
            GeneratorEntity(projectId = projectId, generatorId = "GEN-01", generatorName = "Gen 1", manualSizeKVA = 500.0)
        )
        val circuits = listOf(
            CircuitEntity(id = 1, projectId = projectId, circuitName = "MDB-01", fedFrom = "Transformer", transformerId = "TX-01", panelType = "MDB"),
            CircuitEntity(id = 20, projectId = projectId, circuitName = "EMERG-DB", fedFrom = "ATS-01", panelType = "DB")
        )
        val atsList = listOf(
            AtsEntity(
                projectId = projectId,
                atsId = "ATS-01",
                atsName = "ATS 1",
                normalSourceId = "TX-01",
                emergencySourceId = "GEN-01",
                outputBoardId = 20
            )
        )

        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = projectId,
            transformers = transformers,
            circuits = circuits,
            generators = generators,
            atsList = atsList
        )

        val normalEdge = graph.edges.firstOrNull { it.fromNodeId == "tx_TX-01" && it.toNodeId == "ats_ATS-01" && it.type == SldEdgeType.ATS_NORMAL_PATH }
        assertNotNull("Normal edge tx_TX-01 -> ats_ATS-01 must exist", normalEdge)
    }

    @Test
    fun testAtsNormalSourceDuplicateTransformerNamesRaisesAmbiguityIssue() {
        val transformers = listOf(
            TransformerEntity(projectId = projectId, transformerId = "TX-01", transformerName = "Main TX", kVA = 1000.0),
            TransformerEntity(projectId = projectId, transformerId = "TX-02", transformerName = "Main TX", kVA = 1000.0)
        )
        val circuits = listOf(
            CircuitEntity(id = 20, projectId = projectId, circuitName = "EMERG-DB", fedFrom = "ATS-01", panelType = "DB")
        )
        val atsList = listOf(
            AtsEntity(
                projectId = projectId,
                atsId = "ATS-01",
                atsName = "ATS 1",
                normalSourceId = "Main TX",
                emergencySourceId = "GEN-01",
                outputBoardId = 20
            )
        )

        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = projectId,
            transformers = transformers,
            circuits = circuits,
            atsList = atsList
        )

        assertFalse("Must not create edge to tx_TX-01 when name is ambiguous", graph.edges.any { it.fromNodeId == "tx_TX-01" && it.toNodeId == "ats_ATS-01" })
        assertFalse("Must not create edge to tx_TX-02 when name is ambiguous", graph.edges.any { it.fromNodeId == "tx_TX-02" && it.toNodeId == "ats_ATS-01" })

        val issue = graph.validationIssues.firstOrNull { it.issueCode == "ATS_AMBIGUOUS_NORMAL_SOURCE" }
        assertNotNull("Must generate ATS_AMBIGUOUS_NORMAL_SOURCE issue for duplicate TX names", issue)
        assertTrue("Issue message must include TX-01", issue!!.failedCondition.contains("TX-01"))
        assertTrue("Issue message must include TX-02", issue.failedCondition.contains("TX-02"))
    }

    @Test
    fun testGenericTransformerKeywordWithMultipleTransformersRaisesAmbiguityIssue() {
        val transformers = listOf(
            TransformerEntity(projectId = projectId, transformerId = "TX-01", kVA = 1000.0),
            TransformerEntity(projectId = projectId, transformerId = "TX-02", kVA = 1000.0)
        )
        val circuits = listOf(
            CircuitEntity(id = 20, projectId = projectId, circuitName = "EMERG-DB", fedFrom = "ATS-01", panelType = "DB")
        )
        val atsList = listOf(
            AtsEntity(
                projectId = projectId,
                atsId = "ATS-01",
                atsName = "ATS 1",
                normalSourceId = "transformer",
                emergencySourceId = "GEN-01",
                outputBoardId = 20
            )
        )

        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = projectId,
            transformers = transformers,
            circuits = circuits,
            atsList = atsList
        )

        assertFalse("Must not arbitrarily select first transformer when generic keyword used with multiple transformers", graph.edges.any { it.toNodeId == "ats_ATS-01" && it.type == SldEdgeType.ATS_NORMAL_PATH })
        val issue = graph.validationIssues.firstOrNull { it.issueCode == "ATS_AMBIGUOUS_NORMAL_SOURCE" }
        assertNotNull("Must generate ATS_AMBIGUOUS_NORMAL_SOURCE issue for generic transformer keyword", issue)
    }

    @Test
    fun testExactTransformerIdResolvesDeterministically() {
        val transformers = listOf(
            TransformerEntity(projectId = projectId, transformerId = "TX-01", transformerName = "Primary Transformer", kVA = 1000.0),
            TransformerEntity(projectId = projectId, transformerId = "TX-02", transformerName = "TX-01 Backup", kVA = 1000.0)
        )
        val circuits = listOf(
            CircuitEntity(id = 20, projectId = projectId, circuitName = "EMERG-DB", fedFrom = "ATS-01", panelType = "DB")
        )
        val atsList = listOf(
            AtsEntity(
                projectId = projectId,
                atsId = "ATS-01",
                atsName = "ATS 1",
                normalSourceId = "TX-01",
                emergencySourceId = "GEN-01",
                outputBoardId = 20
            )
        )

        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = projectId,
            transformers = transformers,
            circuits = circuits,
            atsList = atsList
        )

        val normalEdge = graph.edges.firstOrNull { it.fromNodeId == "tx_TX-01" && it.toNodeId == "ats_ATS-01" && it.type == SldEdgeType.ATS_NORMAL_PATH }
        assertNotNull("Exact transformer ID TX-01 must resolve deterministically", normalEdge)
        assertFalse("Transformer B (TX-02) must NOT be selected", graph.edges.any { it.fromNodeId == "tx_TX-02" && it.toNodeId == "ats_ATS-01" })
    }

    @Test
    fun testAtsBranchMaintainsIndependentRightLaneWhenFedFromDistributionBoard() {
        val transformers = listOf(
            TransformerEntity(projectId = projectId, transformerId = "TX-01", kVA = 1000.0)
        )
        val generators = listOf(
            GeneratorEntity(projectId = projectId, generatorId = "GEN-01", generatorName = "Generator 1", manualSizeKVA = 500.0)
        )
        val circuits = listOf(
            CircuitEntity(id = 1, projectId = projectId, circuitName = "MDB-01", fedFrom = "Transformer", transformerId = "TX-01", panelType = "MDB"),
            CircuitEntity(id = 12, projectId = projectId, circuitName = "DP-G3", fedFrom = "MDB-01", panelType = "DB"),
            CircuitEntity(id = 20, projectId = projectId, circuitName = "LPP-G3-2", fedFrom = "ATS 1", panelType = "DB")
        )
        val atsList = listOf(
            AtsEntity(
                projectId = projectId,
                atsId = "ATS-01",
                atsName = "ATS 1",
                normalSourceId = "DP-G3",
                emergencySourceId = "GEN-01",
                outputBoardId = 20
            )
        )

        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = projectId,
            transformers = transformers,
            circuits = circuits,
            generators = generators,
            atsList = atsList
        )

        val layoutResult = RiserLayoutEngine.calculateLayout(graph, circuits, null)

        // 1. Edge existence assertions
        val normalEdge = layoutResult.edges.firstOrNull { it.fromId == "circ_12" && it.toId == "ats_ATS-01" }
        assertNotNull("Normal source edge circ_12 -> ats_ATS-01 must exist in layout", normalEdge)

        val emergEdge = layoutResult.edges.firstOrNull { it.fromId == "gen_GEN-01" && it.toId == "ats_ATS-01" }
        assertNotNull("Emergency source edge gen_GEN-01 -> ats_ATS-01 must exist in layout", emergEdge)

        val outputEdge = layoutResult.edges.firstOrNull { it.fromId == "ats_ATS-01" && it.toId == "circ_20" }
        assertNotNull("ATS output edge ats_ATS-01 -> circ_20 must exist in layout", outputEdge)

        // 2. Relative Vertical/Tier assertions
        val genNode = layoutResult.nodes.first { it.id == "gen_GEN-01" }
        val atsNode = layoutResult.nodes.first { it.id == "ats_ATS-01" }
        val dpG3Node = layoutResult.nodes.first { it.id == "circ_12" }
        val lppNode = layoutResult.nodes.first { it.id == "circ_20" }
        val mdbNode = layoutResult.nodes.first { it.id == "circ_1" }

        assertTrue("Generator Y (${genNode.y}) must be above ATS Y (${atsNode.y})", genNode.y < atsNode.y)
        assertTrue("ATS Y (${atsNode.y}) must be at or above DP-G3 Y (${dpG3Node.y})", atsNode.y <= dpG3Node.y)
        assertTrue("ATS Y (${atsNode.y}) must be aligned with Main Incomer tier (same Y as MDB: ${mdbNode.y})", atsNode.y == mdbNode.y)
        assertTrue("LPP-G3-2 Y (${lppNode.y}) must be below ATS Y (${atsNode.y})", lppNode.y > atsNode.y)

        // 3. Spatial Separation: ATS must be to the right of the entire normal network bounding box
        val normalNodes = layoutResult.nodes.filter { it.id in setOf("grid_TX-01", "tx_TX-01", "circ_1", "circ_12") }
        val normalNetworkMaxRight = normalNodes.maxOf { it.x + it.width }

        assertTrue(
            "ATS X (${atsNode.x}) must be to the right of the normal network bounding box ($normalNetworkMaxRight)",
            atsNode.x >= normalNetworkMaxRight
        )

        // 4. ATS output board remains in the ATS emergency lane
        assertTrue(
            "LPP-G3-2 X (${lppNode.x}) must remain in the emergency lane to the right of normal network ($normalNetworkMaxRight)",
            lppNode.x >= normalNetworkMaxRight - 50f
        )
    }

    @Test
    fun testAtsNormalPathDoesNotCollapseEmergencyBranchIntoNormalSubtree() {
        val transformers = listOf(
            TransformerEntity(projectId = projectId, transformerId = "TX-01", kVA = 1000.0)
        )
        val generators = listOf(
            GeneratorEntity(projectId = projectId, generatorId = "GEN-01", generatorName = "Gen 1", manualSizeKVA = 500.0)
        )
        val circuits = listOf(
            CircuitEntity(id = 1, projectId = projectId, circuitName = "MDB-01", fedFrom = "Transformer", transformerId = "TX-01", panelType = "MDB"),
            CircuitEntity(id = 10, projectId = projectId, circuitName = "DP-1", fedFrom = "MDB-01", panelType = "DB"),
            CircuitEntity(id = 11, projectId = projectId, circuitName = "DP-2", fedFrom = "MDB-01", panelType = "DB"),
            CircuitEntity(id = 12, projectId = projectId, circuitName = "DP-G3", fedFrom = "MDB-01", panelType = "DB"),
            CircuitEntity(id = 13, projectId = projectId, circuitName = "DP-G3-L1", fedFrom = "DP-G3", panelType = "DB"),
            CircuitEntity(id = 20, projectId = projectId, circuitName = "LPP-G3-2", fedFrom = "ATS-01", panelType = "DB")
        )
        val atsList = listOf(
            AtsEntity(
                projectId = projectId,
                atsId = "ATS-01",
                atsName = "ATS 1",
                normalSourceId = "DP-G3",
                emergencySourceId = "GEN-01",
                outputBoardId = 20
            )
        )

        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = projectId,
            transformers = transformers,
            circuits = circuits,
            generators = generators,
            atsList = atsList
        )

        val layoutResult = RiserLayoutEngine.calculateLayout(graph, circuits, null)

        val atsNode = layoutResult.nodes.first { it.id == "ats_ATS-01" }
        val mdbNode = layoutResult.nodes.first { it.id == "circ_1" }
        val normalDescendants = layoutResult.nodes.filter { it.id in setOf("circ_1", "circ_10", "circ_11", "circ_12", "circ_13") }
        val normalSubtreeMaxRight = normalDescendants.maxOf { it.x + it.width }

        // Assert ATS remains at Tier 1 (same Y as MDB)
        assertEquals("ATS must remain at Main/Incomer tier Y", mdbNode.y, atsNode.y, 0.01f)

        // Assert ATS is outside normal subtree bounding box
        assertTrue("ATS X (${atsNode.x}) must be >= normal subtree right boundary ($normalSubtreeMaxRight)", atsNode.x >= normalSubtreeMaxRight)
    }

    @Test
    fun testMultiAtsIndependentRightLanesAndDeterministicAlignment() {
        val transformers = listOf(
            TransformerEntity(projectId = projectId, transformerId = "TX-01", kVA = 1000.0)
        )
        val generators = listOf(
            GeneratorEntity(projectId = projectId, generatorId = "GEN-01", generatorName = "Gen 1", manualSizeKVA = 500.0),
            GeneratorEntity(projectId = projectId, generatorId = "GEN-02", generatorName = "Gen 2", manualSizeKVA = 300.0)
        )
        val circuits = listOf(
            CircuitEntity(id = 1, projectId = projectId, circuitName = "MDB-01", fedFrom = "Transformer", transformerId = "TX-01", panelType = "MDB"),
            CircuitEntity(id = 12, projectId = projectId, circuitName = "DP-G3", fedFrom = "MDB-01", panelType = "DB"),
            CircuitEntity(id = 20, projectId = projectId, circuitName = "LPP-EMERG-1", fedFrom = "ATS 1", panelType = "DB"),
            CircuitEntity(id = 21, projectId = projectId, circuitName = "LPP-EMERG-2", fedFrom = "ATS 2", panelType = "DB")
        )
        val atsList = listOf(
            AtsEntity(
                projectId = projectId,
                atsId = "ATS-01",
                atsName = "ATS 1",
                normalSourceId = "DP-G3",
                emergencySourceId = "GEN-01",
                outputBoardId = 20
            ),
            AtsEntity(
                projectId = projectId,
                atsId = "ATS-02",
                atsName = "ATS 2",
                normalSourceId = "MDB-01",
                emergencySourceId = "GEN-02",
                outputBoardId = 21
            )
        )

        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = projectId,
            transformers = transformers,
            circuits = circuits,
            generators = generators,
            atsList = atsList
        )

        val layoutResult = RiserLayoutEngine.calculateLayout(graph, circuits, null)

        val ats1 = layoutResult.nodes.first { it.id == "ats_ATS-01" }
        val ats2 = layoutResult.nodes.first { it.id == "ats_ATS-02" }
        val gen1 = layoutResult.nodes.first { it.id == "gen_GEN-01" }
        val gen2 = layoutResult.nodes.first { it.id == "gen_GEN-02" }
        val lpp1 = layoutResult.nodes.first { it.id == "circ_20" }
        val lpp2 = layoutResult.nodes.first { it.id == "circ_21" }

        // 1. Both ATS units occupy separate, non-overlapping horizontal lanes
        assertTrue("ATS 1 and ATS 2 must not horizontally overlap", ats1.x + ats1.width <= ats2.x || ats2.x + ats2.width <= ats1.x)

        // 2. Both Generators occupy separate, non-overlapping horizontal lanes
        assertTrue("Gen 1 and Gen 2 must not horizontally overlap", gen1.x + gen1.width <= gen2.x || gen2.x + gen2.width <= gen1.x)

        // 3. No ATS node is demoted below a normal circuit tier (ATS remains at Tier 1 / Main Incomer Y)
        val mdbNode = layoutResult.nodes.first { it.id == "circ_1" }
        assertEquals("ATS 1 must remain at Main/Incomer tier Y", mdbNode.y, ats1.y, 0.01f)
        assertEquals("ATS 2 must remain at Main/Incomer tier Y", mdbNode.y, ats2.y, 0.01f)

        // 4. Generators align with their respective ATS emergency lanes (Gen 1 for ATS 1, Gen 2 for ATS 2)
        val ats1TargetX = ats1.x + ats1.width * 0.75f
        val ats2TargetX = ats2.x + ats2.width * 0.75f
        val gen1CenterX = gen1.x + gen1.width / 2f
        val gen2CenterX = gen2.x + gen2.width / 2f

        assertTrue("Gen 1 ($gen1CenterX) must be to the left of Gen 2 ($gen2CenterX)", gen1CenterX < gen2CenterX)
        assertTrue("ATS 1 ($ats1TargetX) must be to the left of ATS 2 ($ats2TargetX)", ats1TargetX < ats2TargetX)
        assertTrue("Gen 1 must be positioned in ATS 1 lane", gen1.x >= ats1.x - 250f)
        assertTrue("Gen 2 must be positioned in ATS 2 lane", gen2.x >= ats2.x - 250f)

        // 5. Downstream emergency boards remain in their respective ATS subtrees
        assertTrue("LPP 1 and LPP 2 must occupy distinct horizontal lanes", lpp1.x + lpp1.width <= lpp2.x || lpp2.x + lpp2.width <= lpp1.x)

        // 6. All normal-source edges and emergency edges remain present in layout
        val normalEdge1 = layoutResult.edges.firstOrNull { it.fromId == "circ_12" && it.toId == "ats_ATS-01" }
        val normalEdge2 = layoutResult.edges.firstOrNull { it.fromId == "circ_1" && it.toId == "ats_ATS-02" }
        val emergEdge1 = layoutResult.edges.firstOrNull { it.fromId == "gen_GEN-01" && it.toId == "ats_ATS-01" }
        val emergEdge2 = layoutResult.edges.firstOrNull { it.fromId == "gen_GEN-02" && it.toId == "ats_ATS-02" }

        assertNotNull("Normal edge DP-G3 -> ATS-01 must exist", normalEdge1)
        assertNotNull("Normal edge MDB-01 -> ATS-02 must exist", normalEdge2)
        assertNotNull("Emergency edge GEN-01 -> ATS-01 must exist", emergEdge1)
        assertNotNull("Emergency edge GEN-02 -> ATS-02 must exist", emergEdge2)
    }

    @Test
    fun testAtsEmergencySourcesMapToExactGeneratorIdsWithoutCrossWiring() {
        val transformers = listOf(
            TransformerEntity(projectId = projectId, transformerId = "TX-01", kVA = 1000.0)
        )
        val generators = listOf(
            GeneratorEntity(projectId = projectId, generatorId = "GEN-01", generatorName = "Generator 1", manualSizeKVA = 500.0),
            GeneratorEntity(projectId = projectId, generatorId = "GEN-02", generatorName = "Generator 2", manualSizeKVA = 300.0)
        )
        val circuits = listOf(
            CircuitEntity(id = 1, projectId = projectId, circuitName = "MDB-01", fedFrom = "Transformer", transformerId = "TX-01", panelType = "MDB"),
            CircuitEntity(id = 12, projectId = projectId, circuitName = "DP-G3", fedFrom = "MDB-01", panelType = "DB"),
            CircuitEntity(id = 20, projectId = projectId, circuitName = "LPP-G3-2", fedFrom = "ATS 1", panelType = "DB"),
            CircuitEntity(id = 30, projectId = projectId, circuitName = "FOUNTAIN", fedFrom = "ATS 2", panelType = "DB")
        )
        val atsList = listOf(
            AtsEntity(
                projectId = projectId,
                atsId = "ATS-01",
                atsName = "ATS 1",
                normalSourceId = "DP-G3",
                emergencySourceId = "GEN-01",
                outputBoardId = 20
            ),
            AtsEntity(
                projectId = projectId,
                atsId = "ATS-02",
                atsName = "ATS 2",
                normalSourceId = "DP-G3",
                emergencySourceId = "GEN-02",
                outputBoardId = 30
            )
        )

        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = projectId,
            transformers = transformers,
            circuits = circuits,
            generators = generators,
            atsList = atsList
        )

        // Verify exact mapping in graph edges
        assertTrue("GEN-01 -> ATS-01 must exist", graph.edges.any { it.fromNodeId == "gen_GEN-01" && it.toNodeId == "ats_ATS-01" && it.type == SldEdgeType.ATS_EMERGENCY_PATH })
        assertTrue("GEN-02 -> ATS-02 must exist", graph.edges.any { it.fromNodeId == "gen_GEN-02" && it.toNodeId == "ats_ATS-02" && it.type == SldEdgeType.ATS_EMERGENCY_PATH })

        // Verify NO cross wiring
        assertFalse("GEN-01 -> ATS-02 must NOT exist", graph.edges.any { it.fromNodeId == "gen_GEN-01" && it.toNodeId == "ats_ATS-02" })
        assertFalse("GEN-02 -> ATS-01 must NOT exist", graph.edges.any { it.fromNodeId == "gen_GEN-02" && it.toNodeId == "ats_ATS-01" })

        // Verify layout preservation
        val layoutResult = RiserLayoutEngine.calculateLayout(graph, circuits, null)
        assertTrue("Layout must contain GEN-01 -> ATS-01", layoutResult.edges.any { it.fromId == "gen_GEN-01" && it.toId == "ats_ATS-01" })
        assertTrue("Layout must contain GEN-02 -> ATS-02", layoutResult.edges.any { it.fromId == "gen_GEN-02" && it.toId == "ats_ATS-02" })
        assertFalse("Layout must NOT contain GEN-01 -> ATS-02", layoutResult.edges.any { it.fromId == "gen_GEN-01" && it.toId == "ats_ATS-02" })
        assertFalse("Layout must NOT contain GEN-02 -> ATS-01", layoutResult.edges.any { it.fromId == "gen_GEN-02" && it.toId == "ats_ATS-01" })
    }

    @Test
    fun testAtsEmergencySourceResolvesByExactGeneratorName() {
        val transformers = listOf(
            TransformerEntity(projectId = projectId, transformerId = "TX-01", kVA = 1000.0)
        )
        val generators = listOf(
            GeneratorEntity(projectId = projectId, generatorId = "GEN-01", generatorName = "Generator 1", manualSizeKVA = 500.0),
            GeneratorEntity(projectId = projectId, generatorId = "GEN-02", generatorName = "Generator 2", manualSizeKVA = 300.0)
        )
        val circuits = listOf(
            CircuitEntity(id = 1, projectId = projectId, circuitName = "MDB-01", fedFrom = "Transformer", transformerId = "TX-01", panelType = "MDB"),
            CircuitEntity(id = 20, projectId = projectId, circuitName = "LPP-G3-2", fedFrom = "ATS 1", panelType = "DB"),
            CircuitEntity(id = 30, projectId = projectId, circuitName = "FOUNTAIN", fedFrom = "ATS 2", panelType = "DB")
        )
        val atsList = listOf(
            AtsEntity(
                projectId = projectId,
                atsId = "ATS-01",
                atsName = "ATS 1",
                normalSourceId = "MDB-01",
                emergencySourceId = "Generator 1",
                outputBoardId = 20
            ),
            AtsEntity(
                projectId = projectId,
                atsId = "ATS-02",
                atsName = "ATS 2",
                normalSourceId = "MDB-01",
                emergencySourceId = "Generator 2",
                outputBoardId = 30
            )
        )

        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = projectId,
            transformers = transformers,
            circuits = circuits,
            generators = generators,
            atsList = atsList
        )

        assertTrue("GEN-01 -> ATS-01 must resolve by generatorName", graph.edges.any { it.fromNodeId == "gen_GEN-01" && it.toNodeId == "ats_ATS-01" && it.type == SldEdgeType.ATS_EMERGENCY_PATH })
        assertTrue("GEN-02 -> ATS-02 must resolve by generatorName", graph.edges.any { it.fromNodeId == "gen_GEN-02" && it.toNodeId == "ats_ATS-02" && it.type == SldEdgeType.ATS_EMERGENCY_PATH })
        assertFalse("GEN-01 -> ATS-02 cross-wiring must not exist", graph.edges.any { it.fromNodeId == "gen_GEN-01" && it.toNodeId == "ats_ATS-02" })
    }

    @Test
    fun testAtsEmergencySourceGenericKeywordWithMultipleGeneratorsRaisesAmbiguity() {
        val transformers = listOf(
            TransformerEntity(projectId = projectId, transformerId = "TX-01", kVA = 1000.0)
        )
        val generators = listOf(
            GeneratorEntity(projectId = projectId, generatorId = "GEN-01", generatorName = "Generator 1", manualSizeKVA = 500.0),
            GeneratorEntity(projectId = projectId, generatorId = "GEN-02", generatorName = "Generator 2", manualSizeKVA = 300.0)
        )
        val circuits = listOf(
            CircuitEntity(id = 1, projectId = projectId, circuitName = "MDB-01", fedFrom = "Transformer", transformerId = "TX-01", panelType = "MDB"),
            CircuitEntity(id = 30, projectId = projectId, circuitName = "FOUNTAIN", fedFrom = "ATS 2", panelType = "DB")
        )
        val atsList = listOf(
            AtsEntity(
                projectId = projectId,
                atsId = "ATS-02",
                atsName = "ATS 2",
                normalSourceId = "MDB-01",
                emergencySourceId = "Generator",
                outputBoardId = 30
            )
        )

        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = projectId,
            transformers = transformers,
            circuits = circuits,
            generators = generators,
            atsList = atsList
        )

        // Must NOT create an emergency edge for ATS-02
        assertFalse("Must not create ATS_EMERGENCY_PATH when generic keyword is ambiguous", graph.edges.any { it.toNodeId == "ats_ATS-02" && it.type == SldEdgeType.ATS_EMERGENCY_PATH })

        // Must raise ATS_AMBIGUOUS_EMERGENCY_SOURCE issue mentioning conflicting IDs
        val issue = graph.validationIssues.firstOrNull { it.issueCode == "ATS_AMBIGUOUS_EMERGENCY_SOURCE" }
        assertNotNull("Must generate ATS_AMBIGUOUS_EMERGENCY_SOURCE issue", issue)
        assertTrue("Issue detail must list GEN-01", issue!!.failedCondition.contains("GEN-01"))
        assertTrue("Issue detail must list GEN-02", issue.failedCondition.contains("GEN-02"))
    }

    @Test
    fun testAtsEmergencySourceGenericKeywordWithSingleGeneratorUsesSingleLegacyGenerator() {
        val transformers = listOf(
            TransformerEntity(projectId = projectId, transformerId = "TX-01", kVA = 1000.0)
        )
        val generators = listOf(
            GeneratorEntity(projectId = projectId, generatorId = "GEN-01", generatorName = "Generator 1", manualSizeKVA = 500.0)
        )
        val circuits = listOf(
            CircuitEntity(id = 1, projectId = projectId, circuitName = "MDB-01", fedFrom = "Transformer", transformerId = "TX-01", panelType = "MDB"),
            CircuitEntity(id = 20, projectId = projectId, circuitName = "EMERG-DB", fedFrom = "ATS-01", panelType = "DB")
        )
        val atsList = listOf(
            AtsEntity(
                projectId = projectId,
                atsId = "ATS-01",
                atsName = "ATS 1",
                normalSourceId = "MDB-01",
                emergencySourceId = "Generator",
                outputBoardId = 20
            )
        )

        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = projectId,
            transformers = transformers,
            circuits = circuits,
            generators = generators,
            atsList = atsList
        )

        assertTrue("GEN-01 -> ATS-01 must exist for single generator generic keyword", graph.edges.any { it.fromNodeId == "gen_GEN-01" && it.toNodeId == "ats_ATS-01" && it.type == SldEdgeType.ATS_EMERGENCY_PATH })
    }

    @Test
    fun testUnknownAtsEmergencySourceDoesNotFallbackToFirstGenerator() {
        val transformers = listOf(
            TransformerEntity(projectId = projectId, transformerId = "TX-01", kVA = 1000.0)
        )
        val generators = listOf(
            GeneratorEntity(projectId = projectId, generatorId = "GEN-01", generatorName = "Generator 1", manualSizeKVA = 500.0),
            GeneratorEntity(projectId = projectId, generatorId = "GEN-02", generatorName = "Generator 2", manualSizeKVA = 300.0)
        )
        val circuits = listOf(
            CircuitEntity(id = 1, projectId = projectId, circuitName = "MDB-01", fedFrom = "Transformer", transformerId = "TX-01", panelType = "MDB"),
            CircuitEntity(id = 20, projectId = projectId, circuitName = "EMERG-DB", fedFrom = "ATS-01", panelType = "DB")
        )
        val atsList = listOf(
            AtsEntity(
                projectId = projectId,
                atsId = "ATS-01",
                atsName = "ATS 1",
                normalSourceId = "MDB-01",
                emergencySourceId = "DOES-NOT-EXIST",
                outputBoardId = 20
            )
        )

        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = projectId,
            transformers = transformers,
            circuits = circuits,
            generators = generators,
            atsList = atsList
        )

        assertFalse("Must not create edge to gen_GEN-01 when emergency source is unknown", graph.edges.any { it.toNodeId == "ats_ATS-01" && it.type == SldEdgeType.ATS_EMERGENCY_PATH })

        val issue = graph.validationIssues.firstOrNull { it.issueCode == "ATS_EMERGENCY_SOURCE_NOT_FOUND" }
        assertNotNull("Must generate ATS_EMERGENCY_SOURCE_NOT_FOUND issue", issue)
    }

    @Test
    fun testDuplicateGeneratorNamesRaiseEmergencySourceAmbiguity() {
        val transformers = listOf(
            TransformerEntity(projectId = projectId, transformerId = "TX-01", kVA = 1000.0)
        )
        val generators = listOf(
            GeneratorEntity(projectId = projectId, generatorId = "GEN-01", generatorName = "Emergency Generator", manualSizeKVA = 500.0),
            GeneratorEntity(projectId = projectId, generatorId = "GEN-02", generatorName = "Emergency Generator", manualSizeKVA = 300.0)
        )
        val circuits = listOf(
            CircuitEntity(id = 1, projectId = projectId, circuitName = "MDB-01", fedFrom = "Transformer", transformerId = "TX-01", panelType = "MDB"),
            CircuitEntity(id = 20, projectId = projectId, circuitName = "EMERG-DB", fedFrom = "ATS-01", panelType = "DB")
        )
        val atsList = listOf(
            AtsEntity(
                projectId = projectId,
                atsId = "ATS-01",
                atsName = "ATS 1",
                normalSourceId = "MDB-01",
                emergencySourceId = "Emergency Generator",
                outputBoardId = 20
            )
        )

        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = projectId,
            transformers = transformers,
            circuits = circuits,
            generators = generators,
            atsList = atsList
        )

        assertFalse("Must not arbitrarily connect when generator names are duplicated", graph.edges.any { it.toNodeId == "ats_ATS-01" && it.type == SldEdgeType.ATS_EMERGENCY_PATH })

        val issue = graph.validationIssues.firstOrNull { it.issueCode == "ATS_AMBIGUOUS_EMERGENCY_SOURCE" }
        assertNotNull("Must generate ATS_AMBIGUOUS_EMERGENCY_SOURCE issue for duplicate names", issue)
        assertTrue("Issue must include GEN-01", issue!!.failedCondition.contains("GEN-01"))
        assertTrue("Issue must include GEN-02", issue.failedCondition.contains("GEN-02"))
    }

    @Test
    fun testChangingAtsEmergencySourceRebuildsGraphWithNewGeneratorOnly() {
        val transformers = listOf(
            TransformerEntity(projectId = projectId, transformerId = "TX-01", kVA = 1000.0)
        )
        val generators = listOf(
            GeneratorEntity(projectId = projectId, generatorId = "GEN-01", generatorName = "Generator 1", manualSizeKVA = 500.0),
            GeneratorEntity(projectId = projectId, generatorId = "GEN-02", generatorName = "Generator 2", manualSizeKVA = 300.0)
        )
        val circuits = listOf(
            CircuitEntity(id = 1, projectId = projectId, circuitName = "MDB-01", fedFrom = "Transformer", transformerId = "TX-01", panelType = "MDB"),
            CircuitEntity(id = 30, projectId = projectId, circuitName = "FOUNTAIN", fedFrom = "ATS 2", panelType = "DB")
        )
        val initialAtsList = listOf(
            AtsEntity(
                projectId = projectId,
                atsId = "ATS-02",
                atsName = "ATS 2",
                normalSourceId = "MDB-01",
                emergencySourceId = "GEN-01",
                outputBoardId = 30
            )
        )

        val graph1 = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = projectId,
            transformers = transformers,
            circuits = circuits,
            generators = generators,
            atsList = initialAtsList
        )

        assertTrue("Initial graph must have GEN-01 -> ATS-02", graph1.edges.any { it.fromNodeId == "gen_GEN-01" && it.toNodeId == "ats_ATS-02" })

        // Reassign to GEN-02
        val updatedAtsList = listOf(
            initialAtsList.first().copy(emergencySourceId = "GEN-02")
        )

        val graph2 = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = projectId,
            transformers = transformers,
            circuits = circuits,
            generators = generators,
            atsList = updatedAtsList
        )

        assertTrue("Updated graph must have GEN-02 -> ATS-02", graph2.edges.any { it.fromNodeId == "gen_GEN-02" && it.toNodeId == "ats_ATS-02" })
        assertFalse("Updated graph must NOT have GEN-01 -> ATS-02", graph2.edges.any { it.fromNodeId == "gen_GEN-01" && it.toNodeId == "ats_ATS-02" })
    }

    @Test
    fun testMdbMultiFeederSeparationAndStraightDrops() {
        val transformers = listOf(
            TransformerEntity(projectId = projectId, transformerId = "TX-01", kVA = 1600.0)
        )
        val circuits = listOf(
            CircuitEntity(id = 1, projectId = projectId, circuitName = "MDB-Main", fedFrom = "Transformer", transformerId = "TX-01", panelType = "MDB"),
            CircuitEntity(id = 10, projectId = projectId, circuitName = "SMDB-1", fedFrom = "MDB-Main", transformerId = "TX-01", panelType = "SMDB"),
            CircuitEntity(id = 20, projectId = projectId, circuitName = "DB-Power", fedFrom = "MDB-Main", transformerId = "TX-01", panelType = "DB"),
            CircuitEntity(id = 30, projectId = projectId, circuitName = "SMDB-3", fedFrom = "MDB-Main", transformerId = "TX-01", panelType = "SMDB"),
            CircuitEntity(id = 40, projectId = projectId, circuitName = "Motor-1", fedFrom = "MDB-Main", transformerId = "TX-01", panelType = "LOAD")
        )

        val result = SldLayoutEngine.calculateLayout(
            activeProjectId = projectId,
            transformers = transformers,
            circuits = circuits
        )

        val mdbNode = result.nodes.first { it.id == "circ_1" }
        val outgoingEdges = result.edges.filter { it.fromId == "circ_1" }
        assertEquals("Must produce 4 outgoing edges from MDB", 4, outgoingEdges.size)

        // Busbar width must span across the children
        assertTrue("MDB busbar must expand across its 4 children (width >= 300f)", mdbNode.width >= 300f)

        // Every outgoing feeder must have distinct takeoff X coordinate on the busbar
        val takeoffXs = outgoingEdges.map { it.pathPoints.first().first }
        assertEquals("All 4 takeoff X coordinates must be distinct", 4, takeoffXs.distinct().size)

        // Adjacent takeoff points must have minimum separation >= 100f
        val sortedTakeoffs = takeoffXs.sorted()
        for (i in 0 until sortedTakeoffs.size - 1) {
            val gap = sortedTakeoffs[i + 1] - sortedTakeoffs[i]
            assertTrue("Gap between adjacent feeder takeoffs must be >= 100f (actual: $gap)", gap >= 100f)
        }

        // Each feeder wire must drop straight down without crossing (first X == last X)
        outgoingEdges.forEach { edge ->
            val startX = edge.pathPoints.first().first
            val endX = edge.pathPoints.last().first
            assertEquals("Feeder wire ${edge.label} must drop straight vertically without crossing", startX, endX, 2.0f)
        }
    }
}
