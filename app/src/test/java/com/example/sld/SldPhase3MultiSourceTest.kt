package com.example.sld

import com.example.data.AtsEntity
import com.example.data.CircuitEntity
import com.example.data.GeneratorEntity
import com.example.data.TransformerEntity
import com.example.data.UpsEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SldPhase3MultiSourceTest {

    private val testProjectId = "SLD_PHASE3_TEST"

    // =========================================================================
    // 1. MULTI-SOURCE MAPPING TESTS
    // =========================================================================

    @Test
    fun testMultipleGeneratorsProduceDistinctNodes() {
        val generators = listOf(
            GeneratorEntity(projectId = testProjectId, generatorId = "GEN-01", generatorName = "Standby Gen 1", manualSizeKVA = 500.0),
            GeneratorEntity(projectId = testProjectId, generatorId = "GEN-02", generatorName = "Standby Gen 2", manualSizeKVA = 750.0),
            GeneratorEntity(projectId = testProjectId, generatorId = "GEN-03", generatorName = "Standby Gen 3", manualSizeKVA = 1000.0)
        )
        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = testProjectId,
            transformers = emptyList(),
            circuits = emptyList(),
            generators = generators
        )

        val genNodes = graph.nodes.filter { it.type == SldNodeType.GENERATOR }
        assertEquals("Must produce exactly 3 distinct generator nodes", 3, genNodes.size)
        assertEquals("gen_GEN-01", genNodes[0].id)
        assertEquals("gen_GEN-02", genNodes[1].id)
        assertEquals("gen_GEN-03", genNodes[2].id)
    }

    @Test
    fun testMultipleAtsProduceDistinctNodesWithExplicitLinks() {
        val transformers = listOf(
            TransformerEntity(projectId = testProjectId, transformerId = "TX-01", kVA = 1600.0)
        )
        val generators = listOf(
            GeneratorEntity(projectId = testProjectId, generatorId = "GEN-01", generatorName = "Gen 1", manualSizeKVA = 800.0),
            GeneratorEntity(projectId = testProjectId, generatorId = "GEN-02", generatorName = "Gen 2", manualSizeKVA = 800.0)
        )
        val atsList = listOf(
            AtsEntity(projectId = testProjectId, atsId = "ATS-01", atsName = "ATS Normal/Emergency 1", normalSourceId = "TX-01", emergencySourceId = "GEN-01"),
            AtsEntity(projectId = testProjectId, atsId = "ATS-02", atsName = "ATS Normal/Emergency 2", normalSourceId = "TX-01", emergencySourceId = "GEN-02")
        )
        val circuits = listOf(
            CircuitEntity(id = 101, projectId = testProjectId, circuitName = "MDB-Essential-1", assignedAtsId = "ATS-01"),
            CircuitEntity(id = 102, projectId = testProjectId, circuitName = "MDB-Essential-2", assignedAtsId = "ATS-02")
        )

        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = testProjectId,
            transformers = transformers,
            circuits = circuits,
            generators = generators,
            atsList = atsList
        )

        val atsNodes = graph.nodes.filter { it.type == SldNodeType.ATS }
        assertEquals("Must produce exactly 2 distinct ATS nodes", 2, atsNodes.size)

        // ATS 1 Connections
        val ats1Normal = graph.edges.find { it.fromNodeId == "tx_TX-01" && it.toNodeId == "ats_ATS-01" }
        val ats1Emerg = graph.edges.find { it.fromNodeId == "gen_GEN-01" && it.toNodeId == "ats_ATS-01" }
        val ats1Out = graph.edges.find { it.fromNodeId == "ats_ATS-01" && it.toNodeId == "circ_101" }
        assertNotNull("ATS-1 normal source edge must exist", ats1Normal)
        assertNotNull("ATS-1 emergency source edge must exist", ats1Emerg)
        assertNotNull("ATS-1 output edge must exist", ats1Out)
        assertEquals(SldEdgeType.ATS_NORMAL_PATH, ats1Normal?.type)
        assertEquals(SldEdgeType.ATS_EMERGENCY_PATH, ats1Emerg?.type)
        assertEquals(SldEdgeType.ATS_OUTPUT, ats1Out?.type)

        // ATS 2 Connections
        val ats2Normal = graph.edges.find { it.fromNodeId == "tx_TX-01" && it.toNodeId == "ats_ATS-02" }
        val ats2Emerg = graph.edges.find { it.fromNodeId == "gen_GEN-02" && it.toNodeId == "ats_ATS-02" }
        val ats2Out = graph.edges.find { it.fromNodeId == "ats_ATS-02" && it.toNodeId == "circ_102" }
        assertNotNull("ATS-2 normal source edge must exist", ats2Normal)
        assertNotNull("ATS-2 emergency source edge must exist", ats2Emerg)
        assertNotNull("ATS-2 output edge must exist", ats2Out)
        assertEquals(SldEdgeType.ATS_NORMAL_PATH, ats2Normal?.type)
        assertEquals(SldEdgeType.ATS_EMERGENCY_PATH, ats2Emerg?.type)
        assertEquals(SldEdgeType.ATS_OUTPUT, ats2Out?.type)
    }

    @Test
    fun testMultipleUpsProduceDistinctNodes() {
        val upsList = listOf(
            UpsEntity(projectId = testProjectId, upsId = "UPS-01", upsName = "DataCenter UPS 1", manualSizeKVA = 120.0),
            UpsEntity(projectId = testProjectId, upsId = "UPS-02", upsName = "Medical UPS 2", manualSizeKVA = 60.0)
        )
        val circuits = listOf(
            CircuitEntity(id = 201, projectId = testProjectId, circuitName = "Server Rack Feeder", assignedUpsId = "UPS-01"),
            CircuitEntity(id = 202, projectId = testProjectId, circuitName = "ICU Panel Feeder", assignedUpsId = "UPS-02")
        )

        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = testProjectId,
            transformers = emptyList(),
            circuits = circuits,
            upsList = upsList
        )

        val upsNodes = graph.nodes.filter { it.type == SldNodeType.UPS }
        assertEquals("Must produce exactly 2 distinct UPS nodes", 2, upsNodes.size)
        assertEquals("ups_UPS-01", upsNodes[0].id)
        assertEquals("ups_UPS-02", upsNodes[1].id)

        val ups1Out = graph.edges.find { it.fromNodeId == "ups_UPS-01" && it.toNodeId == "circ_201" }
        val ups2Out = graph.edges.find { it.fromNodeId == "ups_UPS-02" && it.toNodeId == "circ_202" }
        assertNotNull("UPS-01 output edge must exist", ups1Out)
        assertNotNull("UPS-02 output edge must exist", ups2Out)
        assertEquals(SldEdgeType.UPS_OUTPUT, ups1Out?.type)
        assertEquals(SldEdgeType.UPS_OUTPUT, ups2Out?.type)
    }

    @Test
    fun testSameNamedGeneratorsNeverDeduplicatedOrCrossConnected() {
        val generators = listOf(
            GeneratorEntity(projectId = testProjectId, generatorId = "GEN-A", generatorName = "Standby Generator", manualSizeKVA = 500.0),
            GeneratorEntity(projectId = testProjectId, generatorId = "GEN-B", generatorName = "Standby Generator", manualSizeKVA = 500.0)
        )
        val atsList = listOf(
            AtsEntity(projectId = testProjectId, atsId = "ATS-01", atsName = "ATS 1", emergencySourceId = "GEN-A")
        )
        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = testProjectId,
            transformers = emptyList(),
            circuits = emptyList(),
            generators = generators,
            atsList = atsList
        )

        val genNodes = graph.nodes.filter { it.type == SldNodeType.GENERATOR }
        assertEquals("Both same-named generators must remain distinct", 2, genNodes.size)

        val atsEmergEdge = graph.edges.find { it.toNodeId == "ats_ATS-01" && it.type == SldEdgeType.ATS_EMERGENCY_PATH }
        assertEquals("ATS must connect only to explicit ID GEN-A", "gen_GEN-A", atsEmergEdge?.fromNodeId)
    }

    // =========================================================================
    // 2. EXPLICIT RELATIONSHIP & UNRESOLVED STATE TESTS
    // =========================================================================

    @Test
    fun testAtsWithMissingEmergencySourceRendersUnresolvedStub() {
        val transformers = listOf(
            TransformerEntity(projectId = testProjectId, transformerId = "TX-01", kVA = 1000.0)
        )
        val atsList = listOf(
            AtsEntity(projectId = testProjectId, atsId = "ATS-01", atsName = "ATS Incomplete", normalSourceId = "TX-01", emergencySourceId = null)
        )
        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = testProjectId,
            transformers = transformers,
            circuits = emptyList(),
            atsList = atsList
        )

        val atsNode = graph.nodes.find { it.id == "ats_ATS-01" }
        assertNotNull("ATS node must exist", atsNode)
        assertEquals("ATS with missing emergency source must report DATA_REQUIRED", SldValidationStatus.DATA_REQUIRED, atsNode?.status)

        val unresolvedEdge = graph.edges.find { it.toNodeId == "ats_ATS-01" && it.type == SldEdgeType.UNRESOLVED_PATH }
        assertNotNull("Unresolved stub edge must be generated for missing emergency input", unresolvedEdge)
        assertTrue("Stub edge must indicate emergency missing", unresolvedEdge?.fromNodeId?.contains("emerg") == true)
        assertEquals("NO EMERGENCY SOURCE", unresolvedEdge?.label)

        val issue = graph.validationIssues.find { it.affectedNodeId == "ats_ATS-01" && it.issueCode == "ATS_EMERGENCY_SOURCE_REQUIRED" }
        assertNotNull("Validation issue for missing emergency source must be logged", issue)
    }

    @Test
    fun testAtsWithMissingNormalSourceRendersUnresolvedStub() {
        val generators = listOf(
            GeneratorEntity(projectId = testProjectId, generatorId = "GEN-01", generatorName = "Gen 1", manualSizeKVA = 500.0)
        )
        val atsList = listOf(
            AtsEntity(projectId = testProjectId, atsId = "ATS-01", atsName = "ATS Incomplete", normalSourceId = null, emergencySourceId = "GEN-01")
        )
        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = testProjectId,
            transformers = emptyList(),
            circuits = emptyList(),
            generators = generators,
            atsList = atsList
        )

        val atsNode = graph.nodes.find { it.id == "ats_ATS-01" }
        assertNotNull("ATS node must exist", atsNode)

        val unresolvedEdge = graph.edges.find { it.toNodeId == "ats_ATS-01" && it.type == SldEdgeType.UNRESOLVED_PATH }
        assertNotNull("Unresolved stub edge must be generated for missing normal input", unresolvedEdge)
        assertTrue("Stub edge must indicate normal missing", unresolvedEdge?.fromNodeId?.contains("normal") == true)
    }

    @Test
    fun testStandaloneGeneratorRendersUnresolvedOutputStub() {
        val generators = listOf(
            GeneratorEntity(projectId = testProjectId, generatorId = "GEN-ORPHAN", generatorName = "Unconnected Gen", manualSizeKVA = 250.0)
        )
        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = testProjectId,
            transformers = emptyList(),
            circuits = emptyList(),
            generators = generators
        )

        val genNode = graph.nodes.find { it.id == "gen_GEN-ORPHAN" }
        assertNotNull("Standalone generator node must exist", genNode)

        val unassignedEdge = graph.edges.find { it.fromNodeId == "gen_GEN-ORPHAN" && it.type == SldEdgeType.UNRESOLVED_PATH }
        assertNotNull("Unresolved output stub edge must be generated for standalone generator", unassignedEdge)
        assertEquals("UNASSIGNED GENERATOR OUTPUT", unassignedEdge?.label)
    }

    // =========================================================================
    // 3. LAYOUT & DETERMINISM TESTS
    // =========================================================================

    @Test
    fun testMultiSourceLayoutDoesNotOverlap() {
        val transformers = listOf(
            TransformerEntity(projectId = testProjectId, transformerId = "TX-01", kVA = 1000.0)
        )
        val generators = listOf(
            GeneratorEntity(projectId = testProjectId, generatorId = "GEN-01", generatorName = "Gen 1", manualSizeKVA = 500.0),
            GeneratorEntity(projectId = testProjectId, generatorId = "GEN-02", generatorName = "Gen 2", manualSizeKVA = 500.0)
        )
        val atsList = listOf(
            AtsEntity(projectId = testProjectId, atsId = "ATS-01", atsName = "ATS 1", normalSourceId = "TX-01", emergencySourceId = "GEN-01"),
            AtsEntity(projectId = testProjectId, atsId = "ATS-02", atsName = "ATS 2", normalSourceId = "TX-01", emergencySourceId = "GEN-02")
        )
        val upsList = listOf(
            UpsEntity(projectId = testProjectId, upsId = "UPS-01", upsName = "UPS 1", manualSizeKVA = 100.0)
        )
        val circuits = listOf(
            CircuitEntity(id = 1, projectId = testProjectId, circuitName = "MDB-1", assignedAtsId = "ATS-01"),
            CircuitEntity(id = 2, projectId = testProjectId, circuitName = "MDB-2", assignedAtsId = "ATS-02"),
            CircuitEntity(id = 3, projectId = testProjectId, circuitName = "Critical-DB", assignedUpsId = "UPS-01")
        )

        val result = SldLayoutEngine.calculateLayout(
            activeProjectId = testProjectId,
            transformers = transformers,
            circuits = circuits,
            generators = generators,
            atsList = atsList,
            upsList = upsList
        )

        // Check for node boundary collisions
        for (i in result.nodes.indices) {
            for (j in (i + 1) until result.nodes.size) {
                val nodeA = result.nodes[i]
                val nodeB = result.nodes[j]

                // Same tier horizontal overlap check
                if (Math.abs(nodeA.y - nodeB.y) < 30f) {
                    val overlap = (nodeA.x < nodeB.x + nodeB.width) && (nodeA.x + nodeA.width > nodeB.x)
                    assertFalse("Nodes ${nodeA.id} and ${nodeB.id} must not overlap horizontally on same tier (y=${nodeA.y})", overlap)
                }
            }
        }

        // Sources must remain upstream (y <= downstream y)
        val txNode = result.nodes.find { it.id == "tx_TX-01" }
        val mdb1Node = result.nodes.find { it.id == "circ_1" }
        assertNotNull(txNode)
        assertNotNull(mdb1Node)
        assertTrue("Transformer must be above MDB-1", (txNode?.y ?: 0f) < (mdb1Node?.y ?: 0f))
    }

    @Test
    fun testMultiSourceLayoutDeterminism() {
        val transformers = listOf(
            TransformerEntity(projectId = testProjectId, transformerId = "TX-01", kVA = 1000.0)
        )
        val generators = listOf(
            GeneratorEntity(projectId = testProjectId, generatorId = "GEN-01", generatorName = "Gen 1", manualSizeKVA = 500.0)
        )
        val atsList = listOf(
            AtsEntity(projectId = testProjectId, atsId = "ATS-01", atsName = "ATS 1", normalSourceId = "TX-01", emergencySourceId = "GEN-01")
        )
        val circuits = listOf(
            CircuitEntity(id = 1, projectId = testProjectId, circuitName = "MDB-Main", assignedAtsId = "ATS-01")
        )

        val run1 = SldLayoutEngine.calculateLayout(
            activeProjectId = testProjectId,
            transformers = transformers,
            circuits = circuits,
            generators = generators,
            atsList = atsList
        )
        val run2 = SldLayoutEngine.calculateLayout(
            activeProjectId = testProjectId,
            transformers = transformers,
            circuits = circuits,
            generators = generators,
            atsList = atsList
        )

        assertEquals("Node counts must match", run1.nodes.size, run2.nodes.size)
        assertEquals("Edge counts must match", run1.edges.size, run2.edges.size)
        run1.nodes.forEachIndexed { idx, node1 ->
            val node2 = run2.nodes[idx]
            assertEquals("Node IDs must match", node1.id, node2.id)
            assertEquals("Node X must match", node1.x, node2.x, 0.001f)
            assertEquals("Node Y must match", node1.y, node2.y, 0.001f)
        }
    }
}
