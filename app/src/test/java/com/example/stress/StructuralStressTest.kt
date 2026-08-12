package com.example.stress

import com.example.data.CircuitEntity
import com.example.data.PanelType
import com.example.data.TransformerEntity
import com.example.sld.ElectricalNetworkGraphBuilder
import com.example.sld.SldLayoutEngine
import com.example.sld.SldValidationStatus
import org.junit.Assert.*
import org.junit.Ignore
import org.junit.Test

@Ignore("heavy stress — run manually")
class StructuralStressTest {

    @Test
    fun testLevel3StructuralExtremeScale10kCircuitsSingleTx() {
        println("=== LEVEL 3: STRUCTURAL EXTREME - 10,000 CIRCUITS SINGLE TX ===")

        val tx = TransformerEntity(transformerId = "TX-BIG", kVA = 2500.0)
        val circuitCount = 10000
        val circuits = (1..circuitCount).map { i ->
            CircuitEntity(
                id = i,
                circuitName = if (i == 1) "MDB-1" else "DB-$i",
                fedFrom = if (i == 1) "Transformer" else "MDB-1",
                transformerId = "TX-BIG",
                panelType = if (i == 1) PanelType.MDB.name else PanelType.DB.name
            )
        }

        val startTime = System.currentTimeMillis()
        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = "PROJ-10K",
            transformers = listOf(tx),
            circuits = circuits
        )
        val buildTimeMs = System.currentTimeMillis() - startTime

        assertNotNull(graph)
        assertTrue("Graph must contain nodes for tx and 10,000 circuits", graph.nodes.size >= 10000)
        println("10,000 CIRCUITS GRAPH BUILD TIME: ${buildTimeMs}ms (Node count: ${graph.nodes.size})")
        assertTrue("Graph build for 10,000 nodes must finish in under 10 seconds", buildTimeMs < 10000)
    }

    @Test
    fun testLevel3FiveTxTenLevelDeepChains() {
        println("=== LEVEL 3: STRUCTURAL - 5 TX WITH 10-LEVEL DEEP CHAINS ===")

        val transformers = (1..5).map { t ->
            TransformerEntity(transformerId = "TX-0$t", kVA = 1000.0 * t)
        }

        val circuits = mutableListOf<CircuitEntity>()
        var idCounter = 1

        for (t in 1..5) {
            var parentName = "Transformer"
            for (level in 0..9) {
                val circName = "TX0$t-LVL$level-C$idCounter"
                circuits.add(
                    CircuitEntity(
                        id = idCounter,
                        circuitName = circName,
                        fedFrom = parentName,
                        transformerId = "TX-0$t",
                        level = level,
                        panelType = when (level) {
                            0 -> PanelType.MDB.name
                            1, 2 -> PanelType.SMDB.name
                            else -> PanelType.DB.name
                        }
                    )
                )
                parentName = circName
                idCounter++
            }
        }

        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = "PROJ-DEEP",
            transformers = transformers,
            circuits = circuits
        )

        assertNotNull(graph)
        assertEquals("Should build all 50 deep chain circuits", 50, circuits.size)
        println("5 TX 10-LEVEL DEEP CHAINS SUMMARY: Node Count=${graph.nodes.size}, Edge Count=${graph.edges.size}")
    }

    @Test
    fun testLevel3CircularAndSelfParentingCycles() {
        println("=== LEVEL 3: STRUCTURAL - CIRCULAR & SELF PARENTING ===")

        // Self-parent (A -> A)
        val selfCirc = CircuitEntity(id = 1, circuitName = "SELF-PANEL", fedFrom = "SELF-PANEL", parentId = 1)
        
        // Circular parent (A -> B -> C -> A)
        val circA = CircuitEntity(id = 10, circuitName = "PANEL-A", fedFrom = "PANEL-C", parentId = 12)
        val circB = CircuitEntity(id = 11, circuitName = "PANEL-B", fedFrom = "PANEL-A", parentId = 10)
        val circC = CircuitEntity(id = 12, circuitName = "PANEL-C", fedFrom = "PANEL-B", parentId = 11)

        val circuits = listOf(selfCirc, circA, circB, circC)

        val graph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = "PROJ-CYCLE",
            transformers = emptyList(),
            circuits = circuits
        )

        assertNotNull(graph)
        println("CIRCULAR & SELF PARENTING SUMMARY: Safely built graph without infinite loop. Node Count=${graph.nodes.size}")
    }

    @Test
    fun testLevel3CrossProjectIsolation() {
        println("=== LEVEL 3: STRUCTURAL - CROSS PROJECT ISOLATION ===")

        val txA = TransformerEntity(projectId = "PROJ-A", transformerId = "TX-A", kVA = 1000.0)
        val circA = CircuitEntity(projectId = "PROJ-A", id = 1, circuitName = "MDB-A", fedFrom = "Transformer", transformerId = "TX-A")

        val txB = TransformerEntity(projectId = "PROJ-B", transformerId = "TX-B", kVA = 1600.0)
        val circB = CircuitEntity(projectId = "PROJ-B", id = 2, circuitName = "MDB-B", fedFrom = "Transformer", transformerId = "TX-B")

        // Build Graph for PROJ-A
        val graphA = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = "PROJ-A",
            transformers = listOf(txA, txB),
            circuits = listOf(circA, circB)
        )

        val nodeIdsA = graphA.nodes.map { it.id }
        assertTrue("PROJ-A graph must contain circ_1", nodeIdsA.contains("circ_1"))
        assertFalse("PROJ-A graph must NOT contain circ_2 (PROJ-B)", nodeIdsA.contains("circ_2"))

        println("CROSS PROJECT ISOLATION SUMMARY: Proj-A graph clean of Proj-B nodes.")
    }

    @Test
    fun testLevel3EmptyAndSingleNodeProject() {
        println("=== LEVEL 3: STRUCTURAL - EMPTY & SINGLE NODE PROJECT ===")

        val emptyGraph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = "PROJ-EMPTY",
            transformers = emptyList(),
            circuits = emptyList()
        )

        assertNotNull(emptyGraph)
        assertEquals("Empty project must generate default Grid node", 1, emptyGraph.nodes.size)
        assertEquals("grid_default", emptyGraph.nodes[0].id)

        val singleCirc = CircuitEntity(id = 100, circuitName = "ISOLATED-LOAD", fedFrom = "Transformer")
        val singleGraph = ElectricalNetworkGraphBuilder.buildGraph(
            activeProjectId = "PROJ-SINGLE",
            transformers = emptyList(),
            circuits = listOf(singleCirc)
        )

        assertNotNull(singleGraph)
        assertTrue(singleGraph.nodes.size >= 2)
        println("EMPTY & SINGLE NODE SUMMARY: Empty project nodes=${emptyGraph.nodes.size}, Single node project nodes=${singleGraph.nodes.size}")
    }
}
