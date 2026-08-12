package com.example.sld
import org.junit.Test
import com.example.data.*
import com.example.engine.*
import java.io.File

class DebugGraphTest {
    @Test
    fun testTwoTransformers() {
        val circuits = listOf(
            CircuitEntity(id = 1, circuitName = "MDB-01", panelType = "MDB", fedFrom = "Transformer", transformerId = "TX-01", loadKVA = 100.0, phases = "3", length_m = 40.0),
            CircuitEntity(id = 2, circuitName = "MDB-02", panelType = "MDB", fedFrom = "Transformer", transformerId = "TX-02", loadKVA = 100.0, phases = "3", length_m = 40.0)
        )
        val transformers = listOf(
            TransformerEntity(projectId = "p1", transformerId = "TX-01", transformerName = "Transformer 1"),
            TransformerEntity(projectId = "p1", transformerId = "TX-02", transformerName = "Transformer 2")
        )
        val atsEntity = AtsEntity(projectId = "p1", id = 1, outputBoardId = 1)
        
        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = "p1",
            circuits = circuits,
            transformers = transformers,
            sizingResults = emptyMap(),
            shortCircuitResults = emptyMap(),
            transformerResults = emptyMap(),
            generatorResult = null,
            atsResult = null,
            atsEntity = atsEntity
        )
        
        val engineResult = RiserLayoutEngine.calculateLayout(graph, circuits, null)

        File("test_output5.txt").writeText(
            "Nodes: " + engineResult.nodes.map { "${it.id} at (${it.x}, ${it.y})" } + "\n" +
            "Edges: " + engineResult.edges.map { "${it.fromId} -> ${it.toId}" } + "\n" +
            "ValidationIssues: " + engineResult.validationIssues.map { it.issueCode }
        )
    }
}
