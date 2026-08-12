package com.example.sld

import com.example.data.CircuitEntity
import org.junit.Test
import org.junit.Assert.assertTrue
import kotlin.math.abs

class DynamicSizingTest {
    @Test
    fun testDynamicSizing() {
        val circuits = listOf(
            CircuitEntity(id = 1, projectId = "p1", circuitName = "MDB-Main", fedFrom = "Transformer", panelType = "MDB", loadKVA = 500.0, atsIncluded = true),
            CircuitEntity(id = 2, projectId = "p1", circuitName = "SMDB-North-Wing-Floor-1", fedFrom = "MDB-Main", panelType = "SMDB", loadKVA = 100.0),
            CircuitEntity(id = 3, projectId = "p1", circuitName = "SMDB-South", fedFrom = "MDB-Main", panelType = "SMDB", loadKVA = 80.0),
            CircuitEntity(id = 4, projectId = "p1", circuitName = "DB-A", fedFrom = "SMDB-North-Wing-Floor-1", panelType = "DB", loadKVA = 20.0),
            CircuitEntity(id = 5, projectId = "p1", circuitName = "DB-B", fedFrom = "SMDB-North-Wing-Floor-1", panelType = "DB", loadKVA = 25.0),
            CircuitEntity(id = 6, projectId = "p1", circuitName = "EXTREMELY-LONG-PANEL-NAME-THAT-WILL-WRAP-FOR-SURE", fedFrom = "SMDB-South", panelType = "DB", loadKVA = 15.0)
        )

        val result = SldLayoutEngine.calculateLayout(activeProjectId="proj_1", transformers=emptyList(), circuits=circuits)
        
        println("--- DYNAMIC SIZING TEST ---")
        println(String.format("%-25s | %-5s | %-5s | %-6s | %-6s | %-6s | %-6s", "LABEL", "W", "H", "X", "Y", "R", "CX"))
        
        val nodeMap = result.nodes.associateBy { it.id }
        
        result.nodes.sortedBy { it.y }.forEach { n ->
            val r = n.x + n.width
            val cx = n.x + n.width / 2f
            println(String.format("%-25s | %5.0f | %5.0f | %6.0f | %6.0f | %6.0f | %6.0f", n.label.take(25), n.width, n.height, n.x, n.y, r, cx))
        }

        println("\n--- PARENT CENTERING ASSERTIONS ---")
        val parents = circuits.filter { c -> circuits.any { it.parentId == c.id } }
        parents.forEach { parent ->
            val pNode = nodeMap[parent.id.toString()]!!
            val childrenNodes = circuits.filter { it.parentId == parent.id }.map { nodeMap[it.id.toString()]!! }
            val firstChild = childrenNodes.minByOrNull { it.x }!!
            val lastChild = childrenNodes.maxByOrNull { it.x }!!
            
            val pCenter = pNode.x + pNode.width / 2f
            val expectedCenter = ((firstChild.x + firstChild.width / 2f) + (lastChild.x + lastChild.width / 2f)) / 2f
            
            val pass = abs(pCenter - expectedCenter) < 1f
            val passStr = if (pass) "PASS" else "FAIL"
            println("${pNode.label.take(15)} : Center=${pCenter}, Expected=${expectedCenter} -> $passStr")
            assertTrue("Parent ${pNode.label} not centered!", pass)
        }

        println("\n--- CONVERGENCE EDGES (MDB-Main) ---")
        val mdbEdges = result.edges.filter { it.toId == "1" && it.fromId.contains("SOURCE") }
        mdbEdges.forEach { edge ->
            println("${edge.label} (From ${edge.fromId}):")
            edge.pathPoints.forEach { pt ->
                println("  -> (X=${pt.first}, Y=${pt.second})")
            }
        }
    }
}
